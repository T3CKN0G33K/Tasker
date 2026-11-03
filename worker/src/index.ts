import { ApiError, decryptToken, encryptToken, verifyFirebaseToken } from './security';

export interface Env {
  DB: D1Database;
  FIREBASE_PROJECT_ID: string;
  ALLOWED_FIREBASE_UIDS: string;
  PLAID_CLIENT_ID: string;
  PLAID_SECRET: string;
  TOKEN_ENCRYPTION_KEY: string;
}
interface Item {
  item_id: string; uid: string; encrypted_token: string; institution_name: string;
  cursor: string | null; last_synced_at: number | null; deletion_pending: number;
}
// Explicit projection: raw Plaid objects are never persisted or returned.
interface Account {
  account_id: string; name: string; official_name?: string | null;
  type: string; subtype?: string | null; mask?: string | null;
  balances: { current: number | null; available: number | null; iso_currency_code: string | null };
}
interface Transaction {
  transaction_id: string; account_id: string; name: string; merchant_name?: string | null;
  amount: number; date: string; authorized_date?: string | null; pending: boolean;
  iso_currency_code?: string | null;
  personal_finance_category?: { primary: string; detailed: string } | null;
}
interface SyncResponse {
  added: Transaction[]; modified: Transaction[]; removed: {transaction_id: string}[];
  next_cursor: string; has_more: boolean;
}
export function accountData(a: Account) {
  return { accountId: a.account_id, name: a.name, officialName: a.official_name ?? null,
    type: a.type, subtype: a.subtype ?? null, mask: a.mask ?? null,
    currentBalance: a.balances.current, availableBalance: a.balances.available,
    isoCurrencyCode: a.balances.iso_currency_code, updatedAt: new Date().toISOString() };
}
export function transactionData(t: Transaction) {
  return { transactionId: t.transaction_id, accountId: t.account_id, name: t.name,
    merchantName: t.merchant_name ?? null, amount: t.amount, date: t.date,
    authorizedDate: t.authorized_date ?? null, pending: t.pending,
    isoCurrencyCode: t.iso_currency_code ?? null,
    category: t.personal_finance_category?.primary ?? null,
    detailedCategory: t.personal_finance_category?.detailed ?? null };
}
function required(value: unknown, max = 512): string {
  if (typeof value !== 'string' || !value.trim() || value.length > max) throw new ApiError(400, 'invalid_request');
  return value.trim();
}
async function body(request: Request): Promise<Record<string, unknown>> {
  if (!request.headers.get('content-type')?.startsWith('application/json')) throw new ApiError(415, 'invalid_request');
  const reader = request.body?.getReader();
  if (!reader) throw new ApiError(400, 'invalid_request');
  let text = ''; let size = 0; const decoder = new TextDecoder();
  while (true) {
    const chunk = await reader.read(); if (chunk.done) break;
    size += chunk.value.byteLength;
    if (size > 4096) { await reader.cancel(); throw new ApiError(413, 'invalid_request'); }
    text += decoder.decode(chunk.value, {stream: true});
  }
  try {
    const data = JSON.parse(text + decoder.decode());
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error();
    return data;
  } catch { throw new ApiError(400, 'invalid_request'); }
}
class PlaidError extends ApiError {
  constructor(public plaidCode: string) { super(502, 'plaid_unavailable'); }
}
async function plaid<T>(env: Env, path: string, data: object): Promise<T> {
  if (!env.PLAID_CLIENT_ID || !env.PLAID_SECRET) throw new ApiError(503, 'configuration_required');
  // Hardcoded Sandbox: configuration cannot select a live environment.
  const response = await fetch(`https://sandbox.plaid.com${path}`, {
    method: 'POST', headers: {'Content-Type': 'application/json', 'Plaid-Version': '2020-09-14'},
    body: JSON.stringify({...data, client_id: env.PLAID_CLIENT_ID, secret: env.PLAID_SECRET}),
    signal: AbortSignal.timeout(12000), redirect: 'manual',
  });
  if (!response.ok) {
    const error = await response.json() as { error_code?: string };
    // Never log the response, exception, request body, tokens, or headers.
    throw new PlaidError(error.error_code ?? 'UNKNOWN');
  }
  return response.json() as Promise<T>;
}

export async function snapshot(env: Env, uid: string) {
  const results = await env.DB.batch([
    env.DB.prepare('SELECT item_id, institution_name, last_synced_at, deletion_pending FROM items WHERE uid = ?').bind(uid),
    env.DB.prepare('SELECT data FROM accounts WHERE uid = ?').bind(uid),
    env.DB.prepare('SELECT data FROM transactions WHERE uid = ? ORDER BY date DESC, record_id LIMIT 100').bind(uid),
  ]);
  const items = results[0].results as unknown as Item[];
  return { connected: items.length > 0, syncItemIds: items.filter(i => !i.deletion_pending).map(i => i.item_id),
    disconnectItemIds: items.map(i => i.item_id), pendingDeletionCount: items.filter(i => i.deletion_pending).length,
    institutions: items.map(i => ({name: i.institution_name, lastSyncedAt: i.last_synced_at})),
    accounts: results[1].results.map(row => JSON.parse((row as {data: string}).data)),
    transactions: results[2].results.map(row => JSON.parse((row as {data: string}).data)) };
}

export async function syncItem(env: Env, uid: string, itemId: string) {
  const lock = crypto.randomUUID();
  const item = await env.DB.prepare(`UPDATE items SET lock_id = ?, lock_until = unixepoch() + 180
    WHERE uid = ? AND item_id = ? AND deletion_pending = 0 AND lock_until < unixepoch() RETURNING *`).bind(lock, uid, itemId).first<Item>();
  if (!item) throw new ApiError(409, 'sync_busy_or_item_unavailable');
  try {
    const accessToken = await decryptToken(item.encrypted_token, env.TOKEN_ENCRYPTION_KEY, `${uid}:${itemId}`);
    const accounts = await plaid<{accounts: Account[]}>(env, '/accounts/get', {access_token: accessToken});
    let cursor = item.cursor; let complete = false;
    const changes = new Map<string, ReturnType<typeof transactionData>>();
    const removed = new Set<string>();
    // Keep bounded work below free-tier subrequest limits. No partial cursor commits.
    for (let attempt = 0; attempt < 3 && !complete; attempt++) {
      cursor = item.cursor; changes.clear(); removed.clear();
      try {
        for (let page = 0; page < 4; page++) {
          const data = await plaid<SyncResponse>(env, '/transactions/sync', {
            access_token: accessToken, count: 100, ...(cursor ? {cursor} : {}),
          });
          for (const t of [...data.added, ...data.modified]) {
            changes.set(t.transaction_id, transactionData(t)); removed.delete(t.transaction_id);
          }
          for (const t of data.removed) { changes.delete(t.transaction_id); removed.add(t.transaction_id); }
          cursor = data.next_cursor;
          if (!data.has_more) { complete = true; break; }
        }
        if (!complete) throw new ApiError(422, 'sync_window_too_large');
      } catch (error) {
        if (!(error instanceof PlaidError) || error.plaidCode !== 'TRANSACTIONS_SYNC_MUTATION_DURING_PAGINATION' || attempt === 2) throw error;
      }
    }
    const accountJson = JSON.stringify(accounts.accounts.map(accountData));
    const transactionJson = JSON.stringify([...changes.values()]);
    if (new TextEncoder().encode(accountJson + transactionJson).length > 1_500_000) throw new ApiError(422, 'sync_window_too_large');
    const guard = 'EXISTS (SELECT 1 FROM items WHERE uid = ? AND item_id = ? AND lock_id = ? AND deletion_pending = 0)';
    // D1 batch is transactional: data changes and cursor advance succeed together.
    const result = await env.DB.batch([
      env.DB.prepare(`DELETE FROM accounts WHERE uid = ? AND item_id = ? AND ${guard}`).bind(uid, itemId, uid, itemId, lock),
      env.DB.prepare(`INSERT INTO accounts(uid, item_id, record_id, data)
        SELECT ?, ?, json_extract(value, '$.accountId'), value FROM json_each(?) WHERE ${guard}`)
        .bind(uid, itemId, accountJson, uid, itemId, lock),
      env.DB.prepare(`INSERT INTO transactions(uid, item_id, record_id, date, data)
        SELECT ?, ?, json_extract(value, '$.transactionId'), json_extract(value, '$.date'), value
        FROM json_each(?) WHERE ${guard}
        ON CONFLICT(uid, item_id, record_id) DO UPDATE SET date = excluded.date, data = excluded.data`)
        .bind(uid, itemId, transactionJson, uid, itemId, lock),
      env.DB.prepare(`DELETE FROM transactions WHERE uid = ? AND item_id = ?
        AND record_id IN (SELECT value FROM json_each(?)) AND ${guard}`)
        .bind(uid, itemId, JSON.stringify([...removed]), uid, itemId, lock),
      env.DB.prepare(`UPDATE items SET cursor = ?, last_synced_at = unixepoch(), lock_id = NULL, lock_until = 0
        WHERE uid = ? AND item_id = ? AND lock_id = ? AND deletion_pending = 0`)
        .bind(cursor, uid, itemId, lock),
    ]);
    if (!result[4].meta.changes) throw new ApiError(409, 'sync_busy_or_item_unavailable');
    return {synced: true};
  } finally {
    await env.DB.prepare('UPDATE items SET lock_id = NULL, lock_until = 0 WHERE uid = ? AND item_id = ? AND lock_id = ?')
      .bind(uid, itemId, lock).run();
  }
}

// Disconnect is not a sync: it permanently fences out in-flight sync writers.
// Only the authenticated owner's Item can be locked, revoked or deleted.
export async function disconnectItem(env: Env, uid: string, itemId: string) {
  const lock = crypto.randomUUID();
  const guard = 'EXISTS (SELECT 1 FROM items WHERE uid = ? AND item_id = ? AND lock_id = ? AND deletion_pending = 1)';
  const begun = await env.DB.batch([
    env.DB.prepare(`UPDATE items SET deletion_pending = 1, cursor = NULL, last_synced_at = NULL,
      institution_name = 'Disconnect pending',
      lock_id = ?, lock_until = unixepoch() + 180
      WHERE uid = ? AND item_id = ? AND (deletion_pending = 0 OR lock_until < unixepoch()) RETURNING *`)
      .bind(lock, uid, itemId),
    env.DB.prepare(`DELETE FROM accounts WHERE uid = ? AND item_id = ? AND ${guard}`)
      .bind(uid, itemId, uid, itemId, lock),
    env.DB.prepare(`DELETE FROM transactions WHERE uid = ? AND item_id = ? AND ${guard}`)
      .bind(uid, itemId, uid, itemId, lock),
  ]);
  const item = begun[0].results[0] as unknown as Item | undefined;
  if (!item) {
    const existing = await env.DB.prepare('SELECT item_id FROM items WHERE uid = ? AND item_id = ?').bind(uid, itemId).first();
    if (existing) throw new ApiError(409, 'disconnect_busy');
    // Same response for absent and other-user IDs: idempotent and no ownership disclosure.
    return {deleted: true};
  }
  try {
    try {
      const token = await decryptToken(item.encrypted_token, env.TOKEN_ENCRYPTION_KEY, `${uid}:${itemId}`);
      if (!token.startsWith('access-sandbox-')) throw new ApiError(400, 'sandbox_only');
      await plaid(env, '/item/remove', {access_token: token});
    } catch (error) {
      // A prior attempt may have revoked remotely before its local commit failed.
      // Only ITEM_NOT_FOUND confirms it is already removed; arbitrary auth/API errors do not.
      if (!(error instanceof PlaidError) || error.plaidCode !== 'ITEM_NOT_FOUND') {
        throw new ApiError(502, 'disconnect_pending');
      }
    }
    const finished = await env.DB.batch([
      env.DB.prepare(`DELETE FROM accounts WHERE uid = ? AND item_id = ? AND ${guard}`)
        .bind(uid, itemId, uid, itemId, lock),
      env.DB.prepare(`DELETE FROM transactions WHERE uid = ? AND item_id = ? AND ${guard}`)
        .bind(uid, itemId, uid, itemId, lock),
      env.DB.prepare('DELETE FROM items WHERE uid = ? AND item_id = ? AND lock_id = ? AND deletion_pending = 1')
        .bind(uid, itemId, lock),
    ]);
    if (!finished[2].meta.changes) throw new ApiError(409, 'disconnect_busy');
    return {deleted: true};
  } finally {
    // Failed attempts remain pending and cannot sync; release only our own lease for retry.
    await env.DB.prepare('UPDATE items SET lock_id = NULL, lock_until = 0 WHERE uid = ? AND item_id = ? AND lock_id = ?')
      .bind(uid, itemId, lock).run();
  }
}

export async function route(request: Request, env: Env, uid: string) {
  const data = await body(request);
  switch (new URL(request.url).pathname) {
    case '/v1/link-token': {
      const result = await plaid<{link_token: string}>(env, '/link/token/create', {
        user: {client_user_id: uid}, client_name: 'Tasker Household Budget (Sandbox)',
        products: ['transactions'], transactions: {days_requested: 30}, country_codes: ['US'], language: 'en', android_package_name: 'com.example.tasker',
      });
      return {linkToken: result.link_token};
    }
    case '/v1/exchange': {
      // Validate encryption configuration before consuming a one-use public token.
      await encryptToken('', env.TOKEN_ENCRYPTION_KEY, uid);
      const token = required(data.publicToken);
      if (!token.startsWith('public-sandbox-')) throw new ApiError(400, 'sandbox_only');
      const result = await plaid<{item_id: string; access_token: string}>(env, '/item/public_token/exchange', {public_token: token});
      if (!result.access_token.startsWith('access-sandbox-')) throw new ApiError(502, 'sandbox_only');
      const encrypted = await encryptToken(result.access_token, env.TOKEN_ENCRYPTION_KEY, `${uid}:${result.item_id}`);
      const institution = typeof data.institutionName === 'string' ? data.institutionName.slice(0,120) : 'Sandbox institution';
      // Never reassign an existing Item from another UID or overwrite its sync cursor.
      const saved = await env.DB.prepare(`INSERT INTO items(item_id, uid, encrypted_token, institution_name)
        VALUES (?, ?, ?, ?) ON CONFLICT(item_id) DO NOTHING RETURNING item_id`)
        .bind(result.item_id, uid, encrypted, institution).first();
      if (!saved) throw new ApiError(409, 'item_already_linked');
      return {linked: true, institutionName: institution, itemId: result.item_id};
    }
    case '/v1/disconnect': return disconnectItem(env, uid, required(data.itemId));
    case '/v1/sync': return syncItem(env, uid, required(data.itemId));
    case '/v1/snapshot': return snapshot(env, uid);
    default: throw new ApiError(404, 'not_found');
  }
}
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      if (request.method !== 'POST') throw new ApiError(405, 'method_not_allowed');
      const token = request.headers.get('Authorization')?.match(/^Bearer ([^\s]+)$/)?.[1];
      if (!token) throw new ApiError(401, 'unauthenticated');
      const uid = await verifyFirebaseToken(token, env.FIREBASE_PROJECT_ID);
      if (!env.ALLOWED_FIREBASE_UIDS.split(',').map(s => s.trim()).filter(Boolean).includes(uid)) throw new ApiError(403, 'user_not_allowed');
      const limit = await env.DB.prepare(`INSERT INTO request_limits(uid, window, count)
        VALUES (?, unixepoch()/60, 1) ON CONFLICT(uid) DO UPDATE SET
        count = CASE WHEN window = unixepoch()/60 THEN count + 1 ELSE 1 END,
        window = unixepoch()/60 RETURNING count`).bind(uid).first<{count: number}>();
      if (!limit || limit.count > 30) throw new ApiError(429, 'rate_limited');
      return reply(await route(request, env, uid));
    } catch (error) {
      // Deliberately no exception logging: upstream errors may contain financial credentials.
      const safe = error instanceof ApiError ? error : new ApiError(503, 'temporarily_unavailable');
      return reply({error: safe.code}, safe.status);
    }
  },
};
function reply(data: unknown, status = 200) {
  return Response.json(data, {status, headers: {'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff'}});
}
