# Plaid Sandbox on Cloudflare Free

The Android app now uses a Cloudflare Worker over HTTPS, with Firebase ID tokens.
Firebase Auth and existing Firestore data stay on Spark. The previous `functions/`,
`.firebaserc`, and `firebase.json` are retained as historical local scaffolding;
**do not deploy Firebase Functions**. Those Functions require Blaze and the old
implementation logs raw upstream exceptions. No financial data is copied into
Firestore by the Worker, so no Firestore rule changes are needed for this migration.

## Local changes to review

- `worker/src/index.ts`: POST `/v1/link-token`, `/v1/exchange`, `/v1/sync`,
  `/v1/snapshot`, and `/v1/disconnect`. Native fetch calls to **sandbox.plaid.com only**; no Plaid
  server SDK/Node compatibility required in the Worker.
- `worker/src/security.ts`: Google JWKS signature verification with jose, RS256,
  Firebase issuer/audience, subject, expiration, issued-at and auth-time checks.
  No Firebase service-account credential is needed. Revocation/disabled-user
  checks are not implemented; signed tokens remain usable until expiry.
- `worker/migrations/0001_plaid.sql`: items, accounts, transactions and per-UID
  request-limit tables, plus indexes. All finance queries include verified UID.
  No credentials reach Android. Access tokens are AES-256-GCM encrypted, with
  random IV and UID+Item as authenticated data. Other financial data is stored
  as structured JSON in D1, accessible only through the Worker/account controls.
- `ALLOWED_FIREBASE_UIDS` starts empty: access is denied until the owner's chosen
  Firebase UIDs are configured. Household roles do not automatically share bank
  data. Each user sees their own connected accounts only.
- Per-Item lock with owner fencing, three total mutation attempts, and atomic
  D1 batch for account/transaction updates and cursor advance. Failed syncs do
  not advance the cursor. There are no webhooks, schedulers, ntfy calls, paid
  queues, Durable Objects, or analytics/logging services in this integration.
- Android repository uses Firebase ID tokens, retries an expired token once,
  disallows HTTP redirects, and never logs request bodies or tokens. Removed
  its Firebase Functions dependency; retained the existing Link SDK/UI.
- `plaidWorkerUrl` is a public HTTPS origin, validated in Gradle, not a secret.
  Until configured, the UI reports that the Sandbox backend is not configured.
  Sandbox wording replaces the previous promise of live bank data.

## Free tier is a requirement

Do not upgrade Firebase Spark or Cloudflare Workers Free. Do not add a payment
method, select Workers Paid, enable a paid Plaid plan, or buy a domain. Use the
included workers.dev hostname and D1 Free. Account-wide limits are shared with
other apps on the same Cloudflare account.

Current published limits (checked 2026-11-02):

- Workers Free: 100,000 requests/day, 10 ms CPU/request, 50 outbound subrequests.
- D1 Free: 5 million rows read/day, 100,000 rows written/day, 5 GB total storage.
  D1 also has per-database limits; stay below the Free database size limit.
- D1 Free rejects queries at quota exhaustion rather than charging paid overages.
- Plaid Sandbox is free and contains synthetic data, not real SoFi activity.

Three users do not guarantee performance within CPU limits. Initial Link requests
ask for 30 days of history; each sync is capped at four pages of 100 changes
(and a 1.5 MB aggregate payload), with one Item per request. A larger update
returns `sync_window_too_large`, leaving its original data/cursor unchanged.
If encountered, redesign sync into smaller staged requests; do not upgrade or
silently truncate history. This is a deliberately bounded Sandbox scaffold.
Snapshot returns the latest 100 transactions, and the existing UI shows five.
Refresh is manual; there is no periodic polling. Request rate is limited to 30
per authenticated allowed UID per minute. This is not an account-wide quota
reservation. Check real Worker CPU/error metrics after an approved Sandbox
deployment; local tests cannot certify the production 10 ms CPU limit.

Sources:
https://developers.cloudflare.com/workers/platform/limits/
https://developers.cloudflare.com/d1/platform/pricing/
https://firebase.google.com/docs/auth/admin/verify-id-tokens
https://developers.cloudflare.com/workers/configuration/secrets/

## Local verification (no cloud resources, secrets, or bank data)

Use Node 22+ and pnpm. From `worker/`:

```sh
pnpm install --frozen-lockfile
pnpm run check
pnpm test
pnpm run build
```

`build` is **wrangler deploy --dry-run**, which bundles locally without publishing.
Tests use disposable local D1 and mocked Plaid responses; no fake financial
records are inserted into the app, Firebase, or a remote Cloudflare database.
Android: `./gradlew :app:compileDebugKotlin`.

## Proposed external steps — NOT RUN; approval required

1. Confirm the Cloudflare account is on **Workers Free** and D1 Free is available.
   Stop if any screen requires a paid plan or payment details.
2. Review the above source and migration, then authenticate Wrangler:

   ```sh
   cd worker
   pnpm exec wrangler login
   pnpm exec wrangler d1 create tasker-plaid-sandbox
   ```

   This creates one remote database. Copy its non-secret `database_id` into
   `wrangler.jsonc`, replacing the all-zero placeholder. Set
   `ALLOWED_FIREBASE_UIDS` to the comma-separated UIDs of the permitted users
   from Firebase Console > Authentication > Users (not emails or passwords).
3. Review and approve applying the new D1 schema:

   ```sh
   pnpm exec wrangler d1 migrations apply tasker-plaid-sandbox --remote
   ```

4. Review and approve publishing one Worker:

   ```sh
   pnpm exec wrangler deploy
   ```

   It has an empty allowlist/absent secrets until configured and will fail closed.
   No Firebase deployments or rules changes are involved.
5. Enter credentials **yourself**, directly in Cloudflare Dashboard > Workers &
   Pages > tasker-plaid-sandbox > Settings > Variables and Secrets > Add > Secret:

   - `PLAID_CLIENT_ID`: Plaid Sandbox client ID.
   - `PLAID_SECRET`: Plaid Sandbox secret.
   - `TOKEN_ENCRYPTION_KEY`: a cryptographically random 32-byte value, base64
     encoded. Generate and save it directly in a trusted password manager, then
     paste only into the Cloudflare secret field. Do not print it in a terminal,
     post it in chat, write it in a source/.env file, or log it.

   Dashboard secret changes may create/deploy a Worker version. These actions
   are part of deployment approval, not authorization granted by this document.
   Do not use “Copy .env” from Plaid. Do not disclose any secret to Codex.
   Keep a secure backup of the encryption key; replacing it without migrating
   encrypted records makes existing tokens unreadable.
6. Set the public Worker origin for Android, for example:

   ```sh
   ./gradlew :app:assembleDebug -PplaidWorkerUrl=https://tasker-plaid-sandbox.YOUR-SUBDOMAIN.workers.dev
   ```

   Use the actual public origin with no trailing slash. This is the only new
   Android configuration value. Plaid secrets stay in Worker secrets.
7. Test with Plaid Sandbox credentials in Link only. Verify auth rejection,
   cross-user isolation, refresh and CPU/D1 metrics. Do not connect SoFi or use
   production tokens. No promises about free live Plaid access are made here.

## Still outside this Sandbox scaffold

Background webhooks, reconnect/update mode,
large-history staged sync and encryption-key rotation need implementation
before considering a real-bank rollout. Existing broad household Firestore
permissions and the old ntfy bridge still need their own review; this backend
never calls either. Do not feed imported finance records into that bridge.


## Disconnect/delete flow (local only; not deployed)

The Budget card has **Disconnect & delete my Sandbox data**, with a confirmation
explaining that it removes all Plaid connections belonging to the signed-in UID,
not other household users, Firebase login/profile, or manually entered budgets.
The action is disabled during Link/refresh and clears visible financial data as
soon as cleanup starts. The repository enumerates that UID's connections, calls
`POST /v1/disconnect` with `{ "itemId": "..." }` for each, continues with other
connections after a failure, and verifies a fresh snapshot before reporting success.
It never asks Android to provide an access token or authoritative UID.

`0002_disconnect.sql` adds `items.deletion_pending`. Both migrations must be
applied to a future D1 deployment. No migration has been applied remotely.
For each connection, the authenticated/allowlisted Worker:

1. In one D1 transaction, marks deletion pending, clears cursor/sync timestamp
   and institution name, purges its accounts/transactions, and replaces the sync
   lease. All statements filter by verified UID and Item. Sync excludes pending
   deletion Items; fencing guards prevent an old sync from re-inserting data.
2. Decrypts the token in memory and calls Sandbox `/item/remove`. Success revokes
   the Item. `ITEM_NOT_FOUND` is accepted as already removed, allowing retry after
   revocation succeeded but local deletion failed. Other API/auth/network errors
   do not count as revocation success.
3. In another atomic D1 batch, deletes any remaining account/transaction rows
   and the Item row containing encrypted token, cursor and metadata. Returns
   success only after that commit. No token, raw request or exception is logged.

Failure behavior is deliberate: after the initial local purge, an upstream error
leaves **only an encrypted token plus the UID/Item and deletion/lock metadata**
needed to retry revocation. The cursor and financial records are gone, normal
sync is blocked, and snapshots expose only a pending count/connection ID and
safe status. The app displays **Retry disconnect & delete**. A missing encryption
key or broken credentials require configuration repair before retry. It does not
throw away the only token needed to finish remote removal, or falsely report
completion. No automatic paid queue/scheduler is added: retry occurs through
the app. If the initial D1 purge fails, that transaction rolls back and revocation
is not attempted. Existing data then remains until a successful retry.

Requests for an absent or another user's Item return the same successful no-op
without revealing its existence or calling Plaid. Repeated cleanup is safe. A
pending deletion lease rejects competing cleanup until it finishes or expires;
a new request can recover an abandoned lease after 180 seconds. Connections
linked concurrently on another device can require another cleanup attempt.
The per-UID request-limit row remains for abuse prevention; it contains no Plaid
credentials or financial records.

Scope of deletion: live D1 tables and in-memory app state, not an assertion of
instant physical erasure from Cloudflare historical recovery storage, another
already-open device's memory, or Plaid's own legal retention. D1 Time Travel can
restore historical data within its seven-day Workers Free retention window. Do not restore a historical
database containing deleted tokens/data without reapplying cleanup before
serving it. Device-wide screenshot/backup erasure is not implemented here.

Plaid documents that `/item/remove` invalidates the access token and associated
processor/bank tokens; removing an Item **does not replenish Trial connections**.
This integration creates no Asset Reports or Audit Copies, which have separate
removal endpoints. None of this enables Trial or Production.

Sources:
https://plaid.com/docs/api/items/#itemremove
https://plaid.com/docs/errors/item/#item_not_found
https://developers.cloudflare.com/d1/reference/time-travel/

Verification includes local D1 integration tests for successful deletion, repeated
requests, two-user and same-user Item isolation, pending revocation/retry,
ITEM_NOT_FOUND, invalid API keys, atomic purge failure, final token-deletion
failure/retry, competing deletion leases, and an in-flight sync that cannot
resurrect deleted rows. Upstream calls are mocked; no real Item is removed.
