package com.example.pantry.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BarcodeLookup {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun lookupUpc(upc: String): String? = withContext(Dispatchers.IO) {
        val cleanUpc = upc.trim()
        if (cleanUpc.isEmpty()) return@withContext null

        // 1. Try UPCItemDB Trial API
        try {
            val request = Request.Builder()
                .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$cleanUpc")
                .header("User-Agent", "TaskerPantryApp/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val code = json.optString("code")
                    if (code == "OK") {
                        val items = json.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val firstItem = items.getJSONObject(0)
                            val title = firstItem.optString("title")
                            if (title.isNotBlank()) {
                                Log.d("BarcodeLookup", "UPCItemDB found: $title")
                                return@withContext title
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BarcodeLookup", "UPCItemDB error for $cleanUpc", e)
        }

        // 2. Fallback to Open Food Facts API
        try {
            val request = Request.Builder()
                .url("https://world.openfoodfacts.org/api/v0/product/$cleanUpc.json")
                .header("User-Agent", "TaskerPantryApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    if (json.optInt("status") == 1) {
                        val product = json.optJSONObject("product")
                        if (product != null) {
                            val title = product.optString("product_name_en").ifEmpty {
                                product.optString("product_name")
                            }
                            if (title.isNotBlank()) {
                                Log.d("BarcodeLookup", "OpenFoodFacts found: $title")
                                return@withContext title
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BarcodeLookup", "OpenFoodFacts error for $cleanUpc", e)
        }

        null
    }
}
