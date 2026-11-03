import { env, fetchMock } from 'cloudflare:test';
import { beforeAll, afterEach, describe, expect, it, vi } from 'vitest';
import migration from '../migrations/0001_plaid.sql?raw';
import disconnectMigration from '../migrations/0002_disconnect.sql?raw';
import worker, { disconnectItem, route, snapshot, syncItem, type Env } from '../src/index';
import { decryptToken, encryptToken } from '../src/security';
const bindings = env as unknown as Env;
const configured = (): Env => ({...bindings, PLAID_CLIENT_ID:'fixture', PLAID_SECRET:'fixture'});
beforeAll(async () => {
  await bindings.DB.exec(migration.replace(/\n/g,' '));
  await bindings.DB.exec(disconnectMigration.replace(/\n/g,' '));
  fetchMock.activate(); fetchMock.disableNetConnect();
});
afterEach(() => {vi.restoreAllMocks(); fetchMock.assertNoPendingInterceptors();});
async function seed(uid = 'alice', id = 'item') {
  const token = await encryptToken(`access-sandbox-${id}`,bindings.TOKEN_ENCRYPTION_KEY,`${uid}:${id}`);
  await bindings.DB.batch([
    bindings.DB.prepare('INSERT INTO items(item_id,uid,encrypted_token,institution_name,cursor) VALUES(?,?,?,?,?)').bind(id,uid,token,'Fixture','saved-cursor'),
    bindings.DB.prepare('INSERT INTO accounts VALUES(?,?,?,?)').bind(uid,id,'account','{"accountId":"account"}'),
    bindings.DB.prepare('INSERT INTO transactions VALUES(?,?,?,?,?)').bind(uid,id,'transaction','2026-01-01','{"transactionId":"transaction"}'),
  ]);
}
function upstream(path: string, data: object, status = 200) {
  fetchMock.get('https://sandbox.plaid.com').intercept({path,method:'POST'}).reply(status,JSON.stringify(data));
}
async function rows(uid: string) {
  return Promise.all(['items','accounts','transactions'].map(table => bindings.DB.prepare(`SELECT * FROM ${table} WHERE uid = ? ORDER BY item_id`).bind(uid).all().then(r => r.results)));
}
describe('disconnect and delete', () => {
  it('requires authentication on the disconnect endpoint', async () => {
    expect((await worker.fetch(new Request('https://example/v1/disconnect',{method:'POST'}),bindings)).status).toBe(401);
  });
  it('revokes the exact owned token and deletes token, cursor, accounts and transactions', async () => {
    await seed();
    fetchMock.get('https://sandbox.plaid.com').intercept({path:'/item/remove',method:'POST',body: JSON.stringify({access_token:'access-sandbox-item',client_id:'fixture',secret:'fixture'})}).reply(200,'{"request_id":"fixture"}');
    const request = new Request('https://example/v1/disconnect',{method:'POST',headers:{'Content-Type':'application/json'},body:'{"itemId":"item"}'});
    expect(await route(request,configured(),'alice')).toEqual({deleted:true});
    expect(await rows('alice')).toEqual([[],[],[]]);
    expect((await snapshot(bindings,'alice')).connected).toBe(false);
  });
  it('does not touch another user or another connection owned by the same user', async () => {
    await seed(); await seed('alice','second'); await seed('bob','bob-item');
    const beforeBob = await rows('bob');
    upstream('/item/remove',{});
    await disconnectItem(configured(),'alice','item');
    expect(await rows('bob')).toEqual(beforeBob);
    expect((await rows('alice')).every(table => table.length === 1 && table[0].item_id === 'second')).toBe(true);
  });
  it('is idempotent and does not reveal or revoke another user\'s Item', async () => {
    await seed('bob','bob-item'); const before = await rows('bob');
    expect(await disconnectItem(configured(),'alice','bob-item')).toEqual({deleted:true});
    expect(await disconnectItem(configured(),'alice','missing')).toEqual({deleted:true});
    expect(await rows('bob')).toEqual(before);
    await seed(); upstream('/item/remove',{});
    await disconnectItem(configured(),'alice','item');
    expect(await disconnectItem(configured(),'alice','item')).toEqual({deleted:true});
  });
  it('treats confirmed ITEM_NOT_FOUND as already revoked', async () => {
    await seed(); upstream('/item/remove',{error_code:'ITEM_NOT_FOUND'},400);
    await disconnectItem(configured(),'alice','item');
    expect(await rows('alice')).toEqual([[],[],[]]);
  });
  it('purges financial data immediately on revocation failure and retains only a retryable encrypted token', async () => {
    await seed(); upstream('/item/remove',{error_code:'INTERNAL_SERVER_ERROR'},500);
    await expect(disconnectItem(configured(),'alice','item')).rejects.toMatchObject({code:'disconnect_pending'});
    const [items,accounts,transactions] = await rows('alice');
    expect(accounts).toEqual([]); expect(transactions).toEqual([]);
    expect(items[0]).toMatchObject({deletion_pending:1,cursor:null,last_synced_at:null,lock_id:null});
    expect(await decryptToken(items[0].encrypted_token as string,bindings.TOKEN_ENCRYPTION_KEY,'alice:item')).toBe('access-sandbox-item');
    expect(await snapshot(bindings,'alice')).toMatchObject({syncItemIds:[],disconnectItemIds:['item'],pendingDeletionCount:1,accounts:[],transactions:[]});
    await expect(syncItem(configured(),'alice','item')).rejects.toMatchObject({status:409});
    upstream('/item/remove',{});
    await disconnectItem(configured(),'alice','item');
    expect(await rows('alice')).toEqual([[],[],[]]);
  });
  it('does not mistake invalid credentials for confirmed revocation', async () => {
    await seed(); upstream('/item/remove',{error_code:'INVALID_API_KEYS'},400);
    await expect(disconnectItem(configured(),'alice','item')).rejects.toMatchObject({code:'disconnect_pending'});
    expect((await rows('alice'))[0]).toHaveLength(1);
  });
  it('rolls back an initial local purge failure before attempting revocation', async () => {
    await seed(); const before = await rows('alice');
    await bindings.DB.exec("CREATE TRIGGER reject_purge BEFORE DELETE ON accounts BEGIN SELECT RAISE(ABORT, 'fixture'); END;");
    await expect(disconnectItem(configured(),'alice','item')).rejects.toThrow();
    expect(await rows('alice')).toEqual(before);
  });
  it('can recover if revocation succeeds but deletion of the token fails', async () => {
    await seed();
    await bindings.DB.exec("CREATE TRIGGER reject_item_delete BEFORE DELETE ON items BEGIN SELECT RAISE(ABORT, 'fixture'); END;");
    upstream('/item/remove',{});
    await expect(disconnectItem(configured(),'alice','item')).rejects.toThrow();
    expect((await rows('alice'))[0][0]).toMatchObject({deletion_pending:1,cursor:null});
    await bindings.DB.exec('DROP TRIGGER reject_item_delete;');
    upstream('/item/remove',{error_code:'ITEM_NOT_FOUND'},400);
    await disconnectItem(configured(),'alice','item');
    expect(await rows('alice')).toEqual([[],[],[]]);
  });
  it('rejects competing disconnect requests while a deletion lease is held', async () => {
    await seed();
    await bindings.DB.prepare("UPDATE items SET deletion_pending=1, lock_id='other', lock_until=unixepoch()+180").run();
    await expect(disconnectItem(configured(),'alice','item')).rejects.toMatchObject({code:'disconnect_busy'});
    expect((await rows('alice'))[0][0].lock_id).toBe('other');
  });
  it('fences an in-flight sync so it cannot recreate deleted records', async () => {
    await seed();
    let release!: () => void; let started!: () => void;
    const gate = new Promise<void>(r => {release=r;}); const reached = new Promise<void>(r => {started=r;});
    const original = globalThis.fetch;
    vi.spyOn(globalThis,'fetch').mockImplementation(async (input, init) => {
      if (String(input).endsWith('/accounts/get')) {started(); await gate;}
      return original(input,init);
    });
    upstream('/accounts/get',{accounts:[{account_id:'new',name:'Fixture',type:'depository',balances:{current:null,available:null,iso_currency_code:'USD'}}]});
    upstream('/transactions/sync',{added:[{transaction_id:'new',account_id:'new',name:'Fixture',amount:1,date:'2026-01-01',pending:false}],modified:[],removed:[],next_cursor:'new-cursor',has_more:false});
    const syncing = syncItem(configured(),'alice','item');
    await reached;
    upstream('/item/remove',{});
    try {await disconnectItem(configured(),'alice','item');} finally {release();}
    await expect(syncing).rejects.toMatchObject({status:409});
    expect(await rows('alice')).toEqual([[],[],[]]);
  });
});
