package com.example.pantry.data

import com.example.tasker.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.tasks.await

data class PlaidAccountSummary(
    val accountId: String,
    val name: String,
    val mask: String?,
    val type: String,
    val subtype: String?,
    val currentBalance: Double?,
    val availableBalance: Double?,
    val currency: String?
)

data class PlaidTransactionSummary(
    val transactionId: String,
    val accountId: String,
    val name: String,
    val merchantName: String?,
    val amount: Double,
    val date: String,
    val pending: Boolean,
    val currency: String?,
    val category: String?
)

data class PlaidSnapshot(
    val connected: Boolean,
    val institutions: List<String>,
    val accounts: List<PlaidAccountSummary>,
    val transactions: List<PlaidTransactionSummary>,
    val pendingDeletionCount: Int = 0
)

object PlaidFinanceRepository {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private class BackendError(val status: Int, val code: String) : Exception()

    private suspend fun call(path: String, body: JSONObject = JSONObject()): Map<*, *> {
        val origin = BuildConfig.PLAID_WORKER_URL
        check(origin.isNotBlank()) { "Plaid Sandbox backend has not been configured yet." }
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw BackendError(401, "unauthenticated")
        suspend fun send(forceRefresh: Boolean): Map<*, *> {
            val token = user.getIdToken(forceRefresh).await().token
                ?: throw BackendError(401, "unauthenticated")
            return withContext(Dispatchers.IO) {
                val request = Request.Builder().url("$origin/v1/$path")
                    .header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    // Never include raw responses, tokens, or request headers in exceptions.
                    val payload = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    if (!response.isSuccessful) throw BackendError(response.code, payload?.optString("error").orEmpty())
                    checkNotNull(payload) { "Plaid backend returned an invalid response." }
                    jsonMap(payload)
                }
            }
        }
        val result = try { send(false) } catch (error: BackendError) {
            if (error.status == 401) send(true) else throw error
        }
        check(FirebaseAuth.getInstance().currentUser?.uid == user.uid) { "Your signed-in account changed. Please refresh." }
        return result
    }

    private fun jsonMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence().associateWith { key ->
        jsonValue(value.get(key))
    }
    private fun jsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> jsonMap(value)
        is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
        else -> value
    }

    suspend fun createLinkToken(): String = call("link-token")["linkToken"] as? String
        ?: error("Plaid did not return a Link token.")

    suspend fun exchangePublicToken(publicToken: String, institutionName: String?) {
        val result = call("exchange", JSONObject().put("publicToken", publicToken)
            .put("institutionName", institutionName ?: "Sandbox institution"))
        check(result["linked"] == true) { "Plaid did not confirm the connection." }
    }

    suspend fun syncTransactions() {
        val itemIds = call("snapshot")["syncItemIds"] as? List<*> ?: emptyList<Any>()
        itemIds.filterIsInstance<String>().forEach { call("sync", JSONObject().put("itemId", it)) }
    }

    suspend fun disconnectAll() {
        val owner = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw BackendError(401, "unauthenticated")
        val before = call("snapshot")
        val ids = (before["disconnectItemIds"] as? List<*>).orEmpty().filterIsInstance<String>()
        var failure: Exception? = null
        for (id in ids) {
            if (FirebaseAuth.getInstance().currentUser?.uid != owner) throw BackendError(401, "unauthenticated")
            try {
                call("disconnect", JSONObject().put("itemId", id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Continue cleaning other owned connections; pending ones remain retryable.
                if (failure == null) failure = error
            }
        }
        if (FirebaseAuth.getInstance().currentUser?.uid != owner) throw BackendError(401, "unauthenticated")
        val after = call("snapshot")
        if (after["connected"] != false) throw failure ?: BackendError(409, "disconnect_pending")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun getSnapshot(): PlaidSnapshot {
        val data = call("snapshot")
        val institutions = (data["institutions"] as? List<Map<String, Any?>>).orEmpty()
            .mapNotNull { it["name"] as? String }
        val accounts = (data["accounts"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { row ->
            val accountId = row["accountId"] as? String ?: return@mapNotNull null
            PlaidAccountSummary(
                accountId = accountId,
                name = row["name"] as? String ?: "Account",
                mask = row["mask"] as? String,
                type = row["type"] as? String ?: "",
                subtype = row["subtype"] as? String,
                currentBalance = (row["currentBalance"] as? Number)?.toDouble(),
                availableBalance = (row["availableBalance"] as? Number)?.toDouble(),
                currency = row["isoCurrencyCode"] as? String
            )
        }
        val transactions = (data["transactions"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { row ->
            val transactionId = row["transactionId"] as? String ?: return@mapNotNull null
            PlaidTransactionSummary(
                transactionId = transactionId,
                accountId = row["accountId"] as? String ?: "",
                name = row["name"] as? String ?: "Transaction",
                merchantName = row["merchantName"] as? String,
                amount = (row["amount"] as? Number)?.toDouble() ?: 0.0,
                date = row["date"] as? String ?: "",
                pending = row["pending"] as? Boolean ?: false,
                currency = row["isoCurrencyCode"] as? String,
                category = row["category"] as? String
            )
        }
        return PlaidSnapshot(
            connected = data["connected"] as? Boolean ?: false,
            institutions = institutions,
            accounts = accounts,
            transactions = transactions,
            pendingDeletionCount = (data["pendingDeletionCount"] as? Number)?.toInt() ?: 0
        )
    }

    fun userFacingError(error: Throwable): String = when {
        error is BackendError -> when (error.code) {
            "unauthenticated" -> "Sign in again before linking a Sandbox account."
            "user_not_allowed" -> "This account is not enabled for Plaid Sandbox yet."
            "configuration_required" -> "The Plaid Sandbox backend needs its secure configuration."
            "sync_busy_or_item_unavailable" -> "A refresh is already running or this account is unavailable. Try again shortly."
            "sync_window_too_large" -> "This transaction history exceeds the current sync limit. Contact the app owner."
            "disconnect_pending" -> "Disconnect is still pending. Retry Disconnect & delete to finish removing saved data and revoking access."
            "disconnect_busy" -> "A disconnect is already running. Wait briefly, then retry Disconnect & delete."
            "rate_limited" -> "Too many requests. Wait a minute and refresh again."
            else -> "Plaid Sandbox is temporarily unavailable. Try refreshing again."
        }
        BuildConfig.PLAID_WORKER_URL.isBlank() -> "Plaid Sandbox backend has not been configured yet."
        else -> "Could not load Plaid Sandbox data. Check your connection and try again."
    }
}
