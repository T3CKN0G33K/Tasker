import { initializeApp } from "firebase-admin/app";
import { DocumentData, DocumentReference, FieldValue, getFirestore } from "firebase-admin/firestore";
import { defineSecret } from "firebase-functions/params";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import {
  Configuration,
  CountryCode,
  PlaidApi,
  PlaidEnvironments,
  Products,
} from "plaid";

initializeApp();

const db = getFirestore();
const plaidClientId = defineSecret("PLAID_CLIENT_ID");
const plaidSecret = defineSecret("PLAID_SECRET");
const functionsRegion = "us-central1";

function getPlaidClient(): PlaidApi {
  const clientId = plaidClientId.value();
  const secret = plaidSecret.value();
  if (!clientId || !secret) {
    throw new HttpsError("failed-precondition", "Plaid Sandbox credentials are not configured on the server.");
  }
  return new PlaidApi(new Configuration({
    basePath: PlaidEnvironments.sandbox,
    baseOptions: {
      headers: {
        "PLAID-CLIENT-ID": clientId,
        "PLAID-SECRET": secret,
      },
    },
  }));
}

function requireUid(request: { auth?: { uid: string } | null }): string {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in before connecting a financial account.");
  return uid;
}

function requiredString(value: unknown, field: string): string {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new HttpsError("invalid-argument", `${field} is required.`);
  }
  return value.trim();
}

function serializeAccount(account: Awaited<ReturnType<PlaidApi["accountsGet"]>>["data"]["accounts"][number]) {
  return {
    accountId: account.account_id,
    name: account.name,
    officialName: account.official_name ?? null,
    type: account.type,
    subtype: account.subtype ?? null,
    mask: account.mask ?? null,
    currentBalance: account.balances.current ?? null,
    availableBalance: account.balances.available ?? null,
    isoCurrencyCode: account.balances.iso_currency_code ?? null,
    updatedAt: FieldValue.serverTimestamp(),
  };
}

function serializeTransaction(transaction: {
  transaction_id: string;
  account_id: string;
  name: string;
  merchant_name?: string | null;
  amount: number;
  date: string;
  authorized_date?: string | null;
  pending: boolean;
  iso_currency_code?: string | null;
  personal_finance_category?: { primary?: string; detailed?: string } | null;
}) {
  return {
    transactionId: transaction.transaction_id,
    accountId: transaction.account_id,
    name: transaction.name,
    merchantName: transaction.merchant_name ?? null,
    amount: transaction.amount,
    date: transaction.date,
    authorizedDate: transaction.authorized_date ?? null,
    pending: transaction.pending,
    isoCurrencyCode: transaction.iso_currency_code ?? null,
    category: transaction.personal_finance_category?.primary ?? null,
    detailedCategory: transaction.personal_finance_category?.detailed ?? null,
  };
}

async function syncItem(itemRef: DocumentReference, item: DocumentData) {
  const client = getPlaidClient();
  const accessToken = item.accessToken as string;
  const uid = item.userId as string;
  const itemId = item.itemId as string;

  const accountsResponse = await client.accountsGet({ access_token: accessToken });
  const accountCollection = db.collection("users").doc(uid).collection("plaidAccounts");
  const accountBatch = db.batch();
  for (const account of accountsResponse.data.accounts) {
    accountBatch.set(accountCollection.doc(account.account_id), serializeAccount(account), { merge: true });
  }
  await accountBatch.commit();

  const transactionsCollection = db.collection("users").doc(uid).collection("plaidTransactions");
  const writer = db.bulkWriter();
  let cursor = (item.cursor as string | undefined) ?? undefined;
  let nextCursor = cursor;
  let hasMore = true;
  let addedCount = 0;
  let modifiedCount = 0;
  let removedCount = 0;

  while (hasMore) {
    const response = await client.transactionsSync({
      access_token: accessToken,
      ...(nextCursor ? { cursor: nextCursor } : {}),
    });
    const data = response.data;
    for (const transaction of data.added) {
      writer.set(transactionsCollection.doc(transaction.transaction_id), serializeTransaction(transaction), { merge: true });
      addedCount++;
    }
    for (const transaction of data.modified) {
      writer.set(transactionsCollection.doc(transaction.transaction_id), serializeTransaction(transaction), { merge: true });
      modifiedCount++;
    }
    for (const removed of data.removed) {
      writer.delete(transactionsCollection.doc(removed.transaction_id));
      removedCount++;
    }
    nextCursor = data.next_cursor;
    hasMore = data.has_more;
  }

  await writer.close();
  await itemRef.set({
    cursor: nextCursor ?? null,
    lastSyncedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  return { itemId, addedCount, modifiedCount, removedCount };
}

async function syncUserItems(uid: string) {
  const privateItems = await db.collection("plaidPrivateItems").where("userId", "==", uid).get();
  const results = [];
  for (const itemDoc of privateItems.docs) {
    results.push(await syncItem(itemDoc.ref, itemDoc.data()));
  }
  return results;
}

export const createPlaidLinkToken = onCall(
  { region: functionsRegion, secrets: [plaidClientId, plaidSecret] },
  async (request) => {
    const uid = requireUid(request);
    try {
      const response = await getPlaidClient().linkTokenCreate({
        user: { client_user_id: uid },
        client_name: "Tasker Household Budget",
        products: [Products.Transactions],
        country_codes: [CountryCode.Us],
        language: "en",
        android_package_name: "com.example.tasker",
      });
      return { linkToken: response.data.link_token };
    } catch (error) {
      console.error("Plaid link-token creation failed", error);
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("internal", "Could not start Plaid Link. Check the server's Plaid Sandbox configuration.");
    }
  },
);

export const exchangePlaidPublicToken = onCall(
  { region: functionsRegion, secrets: [plaidClientId, plaidSecret] },
  async (request) => {
    const uid = requireUid(request);
    const publicToken = requiredString(request.data?.publicToken, "publicToken");
    const institutionName = typeof request.data?.institutionName === "string"
      ? request.data.institutionName.slice(0, 120)
      : "Connected institution";

    try {
      const exchange = await getPlaidClient().itemPublicTokenExchange({ public_token: publicToken });
      const itemId = exchange.data.item_id;
      await db.collection("plaidPrivateItems").doc(itemId).set({
        itemId,
        userId: uid,
        accessToken: exchange.data.access_token,
        cursor: null,
        institutionName,
        createdAt: FieldValue.serverTimestamp(),
      });
      await syncItem(db.collection("plaidPrivateItems").doc(itemId), {
        itemId,
        userId: uid,
        accessToken: exchange.data.access_token,
        cursor: null,
      });
      return { linked: true, institutionName };
    } catch (error) {
      console.error("Plaid public-token exchange failed", error);
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("internal", "Plaid linked the institution, but the account data could not be synced yet.");
    }
  },
);

export const syncPlaidTransactions = onCall(
  { region: functionsRegion, secrets: [plaidClientId, plaidSecret] },
  async (request) => {
    const uid = requireUid(request);
    try {
      const results = await syncUserItems(uid);
      return { syncedItems: results.length, results };
    } catch (error) {
      console.error("Plaid transaction sync failed", error);
      if (error instanceof HttpsError) throw error;
      throw new HttpsError("internal", "Could not refresh Plaid data. Try again later.");
    }
  },
);

export const getPlaidSnapshot = onCall(
  { region: functionsRegion },
  async (request) => {
    const uid = requireUid(request);
    const [privateItems, accounts, transactions] = await Promise.all([
      db.collection("plaidPrivateItems").where("userId", "==", uid).get(),
      db.collection("users").doc(uid).collection("plaidAccounts").get(),
      db.collection("users").doc(uid).collection("plaidTransactions")
        .orderBy("date", "desc").limit(100).get(),
    ]);
    return {
      connected: !privateItems.empty,
      institutions: privateItems.docs.map((doc) => ({ name: doc.get("institutionName") ?? "Connected institution" })),
      accounts: accounts.docs.map((doc) => doc.data()),
      transactions: transactions.docs.map((doc) => doc.data()),
    };
  },
);
