import { env, fetchMock } from 'cloudflare:test';
import { beforeAll, afterEach, describe, expect, it } from 'vitest';
import migration from '../migrations/0001_plaid.sql?raw';
import disconnectMigration from '../migrations/0002_disconnect.sql?raw';
import worker, { route, snapshot, syncItem, type Env } from '../src/index';
import { encryptToken } from '../src/security';
const bindings = env as unknown as Env;
beforeAll(async () => {
  await bindings.DB.exec(migration.replace(/\n/g,' '));
  await bindings.DB.exec(disconnectMigration.replace(/\n/g,' '));
  fetchMock.activate(); fetchMock.disableNetConnect();
});
afterEach(() => {fetchMock.assertNoPendingInterceptors();});
async function item(uid = 'alice', id = 'item') {
  await bindings.DB.prepare('INSERT INTO items(item_id,uid,encrypted_token,institution_name,cursor) VALUES(?,?,?,?,?)')
    .bind(id, uid, await encryptToken('access-sandbox-test', bindings.TOKEN_ENCRYPTION_KEY, `${uid}:${id}`), 'Test fixture','old').run();
}
function upstream(path: string, result: object, status = 200) {
  fetchMock.get('https://sandbox.plaid.com').intercept({path,method:'POST'}).reply(status,JSON.stringify(result));
}
function configured(): Env { return {...bindings, PLAID_CLIENT_ID:'fixture', PLAID_SECRET:'fixture'}; }
describe('backend isolation and sync', () => {
  it('requires authentication before any database or Plaid access', async () => {
    const result = await worker.fetch(new Request('https://example/v1/snapshot',{method:'POST'}),bindings);
    expect(result.status).toBe(401);
  });
  it('cannot snapshot or sync another user\'s Item', async () => {
    await item();
    expect(await snapshot(bindings,'bob')).toEqual({connected:false,syncItemIds:[],disconnectItemIds:[],pendingDeletionCount:0,institutions:[],accounts:[],transactions:[]});
    await expect(syncItem(configured(),'bob','item')).rejects.toMatchObject({status:409});
    const alice = await snapshot(bindings,'alice');
    expect(JSON.stringify(alice)).not.toContain('encrypted_token');
    expect(JSON.stringify(alice)).not.toContain('access-sandbox');
  });
  it('rejects non-Sandbox public tokens before upstream calls', async () => {
    const request = new Request('https://example/v1/exchange',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({publicToken:'public-production-test'})});
    await expect(route(request,configured(),'alice')).rejects.toMatchObject({code:'sandbox_only'});
  });
  it('restarts a mutated pagination window and atomically applies removals and the final cursor', async () => {
    await item();
    await bindings.DB.prepare('INSERT INTO transactions VALUES(?,?,?,?,?)').bind('alice','item','removed','2026-01-01','{}').run();
    upstream('/accounts/get',{accounts:[]});
    const transaction = {transaction_id:'one',account_id:'a',name:'Fixture only',amount:1,date:'2026-01-02',pending:true};
    upstream('/transactions/sync',{added:[transaction],modified:[],removed:[],next_cursor:'page1',has_more:true});
    upstream('/transactions/sync',{error_code:'TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION'},400);
    upstream('/transactions/sync',{added:[],modified:[{...transaction,pending:false}],removed:[{transaction_id:'removed'}],next_cursor:'final',has_more:false});
    await syncItem(configured(),'alice','item');
    const result = await snapshot(bindings,'alice');
    expect(result.transactions).toHaveLength(1);
    expect(result.transactions[0].pending).toBe(false);
    expect(await bindings.DB.prepare('SELECT cursor,lock_id FROM items WHERE item_id = ?').bind('item').first()).toEqual({cursor:'final',lock_id:null});
  });
  it('does not advance cursor or write partial transactions after an upstream failure', async () => {
    await item();
    upstream('/accounts/get',{accounts:[]});
    upstream('/transactions/sync',{added:[{transaction_id:'one',account_id:'a',name:'Fixture only',amount:1,date:'2026-01-02',pending:false}],modified:[],removed:[],next_cursor:'partial',has_more:true});
    upstream('/transactions/sync',{error_code:'ITEM_LOGIN_REQUIRED'},400);
    await expect(syncItem(configured(),'alice','item')).rejects.toThrow();
    expect((await snapshot(bindings,'alice')).transactions).toHaveLength(0);
    expect(await bindings.DB.prepare('SELECT cursor,lock_id FROM items WHERE item_id = ?').bind('item').first()).toEqual({cursor:'old',lock_id:null});
  });
  it('encrypts exchanged tokens and exposes only owner-scoped metadata', async () => {
    upstream('/item/public_token/exchange',{item_id:'new-item',access_token:'access-sandbox-fixture'});
    const request = new Request('https://example/v1/exchange',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({publicToken:'public-sandbox-fixture',institutionName:'Fixture'})});
    expect(await route(request,configured(),'alice')).toMatchObject({linked:true,itemId:'new-item'});
    const stored = await bindings.DB.prepare('SELECT encrypted_token FROM items WHERE uid = ?').bind('alice').first<{encrypted_token:string}>();
    expect(stored?.encrypted_token).toMatch(/^v1\./);
    expect(stored?.encrypted_token).not.toContain('access-sandbox');
    expect((await snapshot(bindings,'bob')).connected).toBe(false);
    expect(JSON.stringify(await snapshot(bindings,'alice'))).not.toContain('encrypted_token');
  });
  it('rolls back the entire commit if a D1 write fails', async () => {
    await item();
    await bindings.DB.exec("CREATE TRIGGER reject_transaction BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'test write failure'); END;");
    upstream('/accounts/get',{accounts:[{account_id:'a',name:'Fixture',type:'depository',balances:{current:null,available:null,iso_currency_code:'USD'}}]});
    upstream('/transactions/sync',{added:[{transaction_id:'one',account_id:'a',name:'Fixture',amount:1,date:'2026-01-02',pending:false}],modified:[],removed:[],next_cursor:'final',has_more:false});
    await expect(syncItem(configured(),'alice','item')).rejects.toThrow();
    const result = await snapshot(bindings,'alice');
    expect(result.accounts).toHaveLength(0);
    expect(result.transactions).toHaveLength(0);
    expect(await bindings.DB.prepare('SELECT cursor FROM items').first()).toEqual({cursor:'old'});
  });
  it('blocks concurrent sync and leaves the other request\'s lock intact', async () => {
    await item();
    await bindings.DB.prepare("UPDATE items SET lock_id='other', lock_until=unixepoch()+180").run();
    await expect(syncItem(configured(),'alice','item')).rejects.toMatchObject({status:409});
    expect((await bindings.DB.prepare('SELECT lock_id FROM items').first()).lock_id).toBe('other');
  });
});
