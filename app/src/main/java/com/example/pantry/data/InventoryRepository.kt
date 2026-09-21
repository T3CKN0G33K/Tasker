package com.example.pantry.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.pantry.service.MyFirebaseMessagingService
import com.example.tasker.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ScannedProductInfo(
    val name: String,
    val quantity: Int = 1,
    val packageSize: String = ""
)

object RealtimeSyncBridge {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Long-lived SSE stream
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val activeStreams = ConcurrentHashMap<String, Job>()

    fun startListening(
        householdId: String,
        onInventorySync: (List<HouseholdItem>) -> Unit,
        onBillsSync: (List<BillItem>) -> Unit,
        onPaycheckSync: (Double) -> Unit,
        onFeedbackSync: (List<FeedbackItem>) -> Unit,
        onTicketsSync: (List<SupportTicket>) -> Unit = {},
        onMessagesSync: (String, List<TicketMessage>) -> Unit = { _, _ -> }
    ) {
        if (householdId.isEmpty()) return
        if (activeStreams.containsKey(householdId)) return

        val job = CoroutineScope(Dispatchers.IO).launch {
            val url = "https://ntfy.sh/pantry_sync_$householdId/json"
            val request = Request.Builder().url(url).build()

            while (isActive) {
                try {
                    val response = client.newCall(request).execute()
                    val source = response.body?.source() ?: break
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue
                        try {
                            val json = JSONObject(line)
                            val message = json.optString("message", "")
                            if (message.isNotEmpty()) {
                                val payload = JSONObject(message)
                                when (payload.optString("type")) {
                                    "INVENTORY_SYNC" -> {
                                        val array = payload.optJSONArray("items")
                                        if (array != null) {
                                            val list = mutableListOf<HouseholdItem>()
                                            for (i in 0 until array.length()) {
                                                val obj = array.getJSONObject(i)
                                                list.add(
                                                    HouseholdItem(
                                                        id = obj.optString("id"),
                                                        name = obj.optString("name"),
                                                        currentQuantity = obj.optInt("currentQuantity"),
                                                        alertThreshold = obj.optInt("alertThreshold"),
                                                        isStaple = obj.optBoolean("isStaple", true),
                                                        status = obj.optString("status", "STOCKED"),
                                                        barcode = obj.optString("barcode"),
                                                        packageSize = obj.optString("packageSize")
                                                    )
                                                )
                                            }
                                            withContext(Dispatchers.Main) {
                                                onInventorySync(list)
                                            }
                                        }
                                    }
                                    "BILLS_SYNC" -> {
                                        val array = payload.optJSONArray("bills")
                                        if (array != null) {
                                            val list = mutableListOf<BillItem>()
                                            for (i in 0 until array.length()) {
                                                val obj = array.getJSONObject(i)
                                                list.add(
                                                    BillItem(
                                                        id = obj.optString("id"),
                                                        name = obj.optString("name"),
                                                        amount = obj.optDouble("amount"),
                                                        isPaid = obj.optBoolean("isPaid"),
                                                        dueDate = obj.optString("dueDate"),
                                                        dueInCurrentCycle = obj.optBoolean("dueInCurrentCycle", true)
                                                    )
                                                )
                                            }
                                            withContext(Dispatchers.Main) {
                                                onBillsSync(list)
                                            }
                                        }
                                    }
                                    "PAYCHECK_SYNC" -> {
                                        val amount = payload.optDouble("paycheckTotal", 1250.0)
                                        withContext(Dispatchers.Main) {
                                            onPaycheckSync(amount)
                                        }
                                    }
                                    "FEEDBACK_SYNC" -> {
                                        val array = payload.optJSONArray("feedback")
                                        if (array != null) {
                                            val list = mutableListOf<FeedbackItem>()
                                            for (i in 0 until array.length()) {
                                                val obj = array.getJSONObject(i)
                                                list.add(
                                                    FeedbackItem(
                                                        id = obj.optString("id"),
                                                        authorRole = obj.optString("authorRole", "Member"),
                                                        type = obj.optString("type", "FEATURE"),
                                                        title = obj.optString("title"),
                                                        description = obj.optString("description"),
                                                        status = obj.optString("status", "SUBMITTED"),
                                                        timestampText = obj.optString("timestampText", "Just Now")
                                                    )
                                                )
                                            }
                                            withContext(Dispatchers.Main) {
                                                onFeedbackSync(list)
                                            }
                                        }
                                    }
                                    "TICKETS_SYNC" -> {
                                        val array = payload.optJSONArray("tickets")
                                        if (array != null) {
                                            val list = mutableListOf<SupportTicket>()
                                            for (i in 0 until array.length()) {
                                                val obj = array.getJSONObject(i)
                                                val timeMs = obj.optLong("createdAtMs", System.currentTimeMillis())
                                                list.add(
                                                    SupportTicket(
                                                        id = obj.optString("id"),
                                                        authorId = obj.optString("authorId"),
                                                        authorRole = obj.optString("authorRole", "Member"),
                                                        type = obj.optString("type", "BUG"),
                                                        title = obj.optString("title"),
                                                        description = obj.optString("description"),
                                                        status = obj.optString("status", "WAITING"),
                                                        createdAt = Date(timeMs)
                                                    )
                                                )
                                            }
                                            withContext(Dispatchers.Main) {
                                                onTicketsSync(list)
                                            }
                                        }
                                    }
                                    "MESSAGES_SYNC" -> {
                                        val ticketId = payload.optString("ticketId")
                                        val array = payload.optJSONArray("messages")
                                        if (ticketId.isNotEmpty() && array != null) {
                                            val list = mutableListOf<TicketMessage>()
                                            for (i in 0 until array.length()) {
                                                val obj = array.getJSONObject(i)
                                                val timeMs = obj.optLong("timestampMs", System.currentTimeMillis())
                                                list.add(
                                                    TicketMessage(
                                                        id = obj.optString("id"),
                                                        senderId = obj.optString("senderId"),
                                                        senderRole = obj.optString("senderRole"),
                                                        text = obj.optString("text"),
                                                        timestamp = Date(timeMs)
                                                    )
                                                )
                                            }
                                            withContext(Dispatchers.Main) {
                                                onMessagesSync(ticketId, list)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("RealtimeSync", "Error parsing SSE event line", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RealtimeSync", "SSE connection error, reconnecting in 3s...", e)
                    delay(3000)
                }
            }
        }
        activeStreams[householdId] = job
    }

    fun publishInventory(householdId: String, items: List<HouseholdItem>) {
        if (householdId.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val array = JSONArray()
                items.forEach { item ->
                    val obj = JSONObject()
                    obj.put("id", item.id)
                    obj.put("name", item.name)
                    obj.put("currentQuantity", item.currentQuantity)
                    obj.put("alertThreshold", item.alertThreshold)
                    obj.put("isStaple", item.isStaple)
                    obj.put("status", item.status)
                    obj.put("barcode", item.barcode)
                    obj.put("packageSize", item.packageSize)
                    array.put(obj)
                }
                val payload = JSONObject()
                payload.put("type", "INVENTORY_SYNC")
                payload.put("items", array)

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/pantry_sync_$householdId")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Error publishing inventory sync", e)
            }
        }
    }

    fun publishBills(householdId: String, bills: List<BillItem>) {
        if (householdId.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val array = JSONArray()
                bills.forEach { bill ->
                    val obj = JSONObject()
                    obj.put("id", bill.id)
                    obj.put("name", bill.name)
                    obj.put("amount", bill.amount)
                    obj.put("isPaid", bill.isPaid)
                    obj.put("dueDate", bill.dueDate)
                    obj.put("dueInCurrentCycle", bill.dueInCurrentCycle)
                    array.put(obj)
                }
                val payload = JSONObject()
                payload.put("type", "BILLS_SYNC")
                payload.put("bills", array)

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/pantry_sync_$householdId")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Error publishing bills sync", e)
            }
        }
    }

    fun publishPaycheck(householdId: String, paycheckAmount: Double) {
        if (householdId.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val payload = JSONObject()
                payload.put("type", "PAYCHECK_SYNC")
                payload.put("paycheckTotal", paycheckAmount)

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/pantry_sync_$householdId")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Error publishing paycheck sync", e)
            }
        }
    }

    fun publishFeedback(householdId: String, feedbackList: List<FeedbackItem>) {
        if (householdId.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val array = JSONArray()
                feedbackList.forEach { fb ->
                    val obj = JSONObject()
                    obj.put("id", fb.id)
                    obj.put("authorRole", fb.authorRole)
                    obj.put("type", fb.type)
                    obj.put("title", fb.title)
                    obj.put("description", fb.description)
                    obj.put("status", fb.status)
                    obj.put("timestampText", fb.timestampText)
                    array.put(obj)
                }
                val payload = JSONObject()
                payload.put("type", "FEEDBACK_SYNC")
                payload.put("feedback", array)

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/pantry_sync_$householdId")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Error publishing feedback sync", e)
            }
        }
    }

    fun publishTickets(householdId: String, tickets: List<SupportTicket>) {
        if (householdId.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val array = JSONArray()
                tickets.forEach { ticket ->
                    val obj = JSONObject()
                    obj.put("id", ticket.id)
                    obj.put("authorId", ticket.authorId)
                    obj.put("authorRole", ticket.authorRole)
                    obj.put("type", ticket.type)
                    obj.put("title", ticket.title)
                    obj.put("description", ticket.description)
                    obj.put("status", ticket.status)
                    obj.put("createdAtMs", ticket.createdAt?.time ?: System.currentTimeMillis())
                    array.put(obj)
                }
                val payload = JSONObject()
                payload.put("type", "TICKETS_SYNC")
                payload.put("tickets", array)

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/pantry_sync_$householdId")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Error publishing tickets sync", e)
            }
        }
    }

    fun publishMessages(householdId: String, ticketId: String, messages: List<TicketMessage>) {
        if (householdId.isEmpty() || ticketId.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val array = JSONArray()
                messages.forEach { msg ->
                    val obj = JSONObject()
                    obj.put("id", msg.id)
                    obj.put("senderId", msg.senderId)
                    obj.put("senderRole", msg.senderRole)
                    obj.put("text", msg.text)
                    obj.put("timestampMs", msg.timestamp?.time ?: System.currentTimeMillis())
                    array.put(obj)
                }
                val payload = JSONObject()
                payload.put("type", "MESSAGES_SYNC")
                payload.put("ticketId", ticketId)
                payload.put("messages", array)

                val body = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://ntfy.sh/pantry_sync_$householdId")
                    .post(body)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e("RealtimeSync", "Error publishing messages sync", e)
            }
        }
    }
}

class InventoryRepository {

    private val db: FirebaseFirestore? = run {
        try {
            Firebase.firestore
        } catch (e: Exception) {
            try {
                FirebaseFirestore.getInstance()
            } catch (e2: Exception) {
                null
            }
        }
    }

    private val prefs: SharedPreferences?
        get() {
            val ctx = MainActivity.appContext ?: MyFirebaseMessagingService.instance
            return ctx?.getSharedPreferences("pantry_prefs", Context.MODE_PRIVATE)
        }

    // Shared Code -> Household ID Registry
    private val inviteCodeToHouseholdMap = ConcurrentHashMap<String, Household>()

    // Per-Household Reactive Memory SSOT Maps
    private val itemsMap = ConcurrentHashMap<String, MutableStateFlow<List<HouseholdItem>>>()
    private val billsMap = ConcurrentHashMap<String, MutableStateFlow<List<BillItem>>>()
    private val paycheckMap = ConcurrentHashMap<String, MutableStateFlow<Double>>()
    private val feedbackMap = ConcurrentHashMap<String, MutableStateFlow<List<FeedbackItem>>>()
    private val ticketsMap = ConcurrentHashMap<String, MutableStateFlow<List<SupportTicket>>>()
    private val messagesMap = ConcurrentHashMap<String, MutableStateFlow<List<TicketMessage>>>()

    private fun defaultItemsList(): List<HouseholdItem> {
        return listOf(
            HouseholdItem(id = "1", name = "Whole Milk", currentQuantity = 1, alertThreshold = 2, status = "LOW"),
            HouseholdItem(id = "2", name = "Free Range Eggs", currentQuantity = 12, alertThreshold = 4, status = "STOCKED"),
            HouseholdItem(id = "3", name = "Sourdough Bread", currentQuantity = 0, alertThreshold = 1, status = "EMPTY"),
            HouseholdItem(id = "4", name = "Organic Avocados", currentQuantity = 5, alertThreshold = 2, status = "STOCKED"),
            HouseholdItem(id = "5", name = "Greek Yogurt", currentQuantity = 2, alertThreshold = 3, status = "LOW"),
            HouseholdItem(id = "6", name = "Oat Milk", currentQuantity = 4, alertThreshold = 1, status = "STOCKED")
        )
    }

    private fun defaultBillsList(): List<BillItem> {
        return listOf(
            BillItem(id = "b1", name = "Electric Bill", amount = 135.0, isPaid = false, dueDate = "This Wed", dueInCurrentCycle = true),
            BillItem(id = "b2", name = "Cell Phone Plan", amount = 75.0, isPaid = false, dueDate = "This Fri", dueInCurrentCycle = true),
            BillItem(id = "b3", name = "Car Insurance", amount = 160.0, isPaid = true, dueDate = "Paid", dueInCurrentCycle = true),
            BillItem(id = "b4", name = "Credit Card Min", amount = 95.0, isPaid = false, dueDate = "Next Cycle", dueInCurrentCycle = false)
        )
    }

    private fun defaultFeedbackList(): List<FeedbackItem> {
        return listOf(
            FeedbackItem(
                id = "f1",
                authorRole = "Member",
                type = "FEATURE",
                title = "Add Barcode Auto-Scan for Receipts",
                description = "It would be super helpful if we could scan grocery receipts to auto-add items to the pantry!",
                status = "IN_REVIEW",
                timestampText = "Yesterday"
            )
        )
    }

    private fun defaultTicketsList(): List<SupportTicket> {
        return listOf(
            SupportTicket(
                id = "t1",
                authorId = "user_member_1",
                authorRole = "Member",
                type = "BUG",
                title = "Quantity counter skipped from 2 to 0",
                description = "When tapping decrease button fast, it skipped quantity 1.",
                status = "CONNECTED",
                createdAt = Date(System.currentTimeMillis() - 3600000)
            )
        )
    }

    private fun defaultMessagesList(ticketId: String): List<TicketMessage> {
        return listOf(
            TicketMessage(
                id = "m1",
                senderId = "user_member_1",
                senderRole = "Member",
                text = "When tapping decrease button fast, it skipped quantity 1.",
                timestamp = Date(System.currentTimeMillis() - 3600000)
            ),
            TicketMessage(
                id = "m2",
                senderId = "user_owner_1",
                senderRole = "Owner",
                text = "Thanks for reporting! I connected to this ticket and fixed the race condition.",
                timestamp = Date(System.currentTimeMillis() - 1800000)
            )
        )
    }

    private fun getItemsFlowFor(householdId: String): MutableStateFlow<List<HouseholdItem>> {
        val id = householdId.ifEmpty { "default_household" }
        return itemsMap.getOrPut(id) {
            MutableStateFlow(defaultItemsList())
        }
    }

    private fun getBillsFlowFor(householdId: String): MutableStateFlow<List<BillItem>> {
        val id = householdId.ifEmpty { "default_household" }
        return billsMap.getOrPut(id) {
            MutableStateFlow(defaultBillsList())
        }
    }

    private fun getPaycheckFlowFor(householdId: String): MutableStateFlow<Double> {
        val id = householdId.ifEmpty { "default_household" }
        return paycheckMap.getOrPut(id) {
            MutableStateFlow(1250.0)
        }
    }

    private fun getFeedbackFlowFor(householdId: String): MutableStateFlow<List<FeedbackItem>> {
        val id = householdId.ifEmpty { "default_household" }
        return feedbackMap.getOrPut(id) {
            MutableStateFlow(defaultFeedbackList())
        }
    }

    private fun getTicketsFlowFor(householdId: String): MutableStateFlow<List<SupportTicket>> {
        val id = householdId.ifEmpty { "default_household" }
        return ticketsMap.getOrPut(id) {
            MutableStateFlow(defaultTicketsList())
        }
    }

    private fun getMessagesFlowFor(householdId: String, ticketId: String): MutableStateFlow<List<TicketMessage>> {
        val key = "${householdId.ifEmpty { "default_household" }}_$ticketId"
        return messagesMap.getOrPut(key) {
            MutableStateFlow(defaultMessagesList(ticketId))
        }
    }

    fun getSavedHouseholdId(): String? {
        val id = prefs?.getString("householdId", null) ?: prefs?.getString("household_id", null)
        return if (id == "default_household" || id.isNullOrEmpty()) "default_household" else id
    }

    fun getSavedIsOwner(): Boolean {
        return prefs?.getBoolean("isOwner", false) ?: prefs?.getBoolean("is_owner", false) ?: false
    }

    fun saveHouseholdState(householdId: String, isOwner: Boolean) {
        prefs?.edit()
            ?.putString("householdId", householdId)
            ?.putString("household_id", householdId)
            ?.putBoolean("isOwner", isOwner)
            ?.putBoolean("is_owner", isOwner)
            ?.apply()
    }

    fun getCurrentUserId(): String {
        return try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser
            if (user != null) {
                user.uid
            } else {
                val savedUid = prefs?.getString("user_uid", null)
                if (savedUid != null) {
                    savedUid
                } else {
                    val newUid = UUID.randomUUID().toString()
                    prefs?.edit()?.putString("user_uid", newUid)?.apply()
                    newUid
                }
            }
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun createHousehold(userId: String): Household {
        val inviteCode = generateInviteCode()
        val householdId = "HH_$inviteCode"
        val household = Household(
            id = householdId,
            ownerId = userId,
            inviteCode = inviteCode,
            memberIds = listOf(userId)
        )

        inviteCodeToHouseholdMap[inviteCode] = household
        saveHouseholdState(householdId, isOwner = true)

        val initialItems = defaultItemsList()
        val initialBills = defaultBillsList()
        val initialFeedback = defaultFeedbackList()
        val initialTickets = defaultTicketsList()
        getItemsFlowFor(householdId).value = initialItems
        getBillsFlowFor(householdId).value = initialBills
        getPaycheckFlowFor(householdId).value = 1250.0
        getFeedbackFlowFor(householdId).value = initialFeedback
        getTicketsFlowFor(householdId).value = initialTickets

        // Start listening to real-time HTTP SSE bridge
        ensureRealtimeSyncListening(householdId)

        // Broadcast initial state
        RealtimeSyncBridge.publishInventory(householdId, initialItems)
        RealtimeSyncBridge.publishBills(householdId, initialBills)
        RealtimeSyncBridge.publishPaycheck(householdId, 1250.0)
        RealtimeSyncBridge.publishFeedback(householdId, initialFeedback)
        RealtimeSyncBridge.publishTickets(householdId, initialTickets)

        val database = db
        if (database != null) {
            try {
                database.collection("households")
                    .document(householdId)
                    .set(household, SetOptions.merge())

                initialItems.forEach { item ->
                    database.collection("households")
                        .document(householdId)
                        .collection("inventory")
                        .document(item.id)
                        .set(item, SetOptions.merge())
                }

                initialBills.forEach { bill ->
                    database.collection("households")
                        .document(householdId)
                        .collection("bills")
                        .document(bill.id)
                        .set(bill, SetOptions.merge())
                }
            } catch (e: Exception) {
                Log.e("InventoryRepo", "Error seeding remote household $householdId: ${e.message}", e)
            }
        }
        return household
    }

    suspend fun joinHouseholdByCode(userId: String, code: String): Result<Household> {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.length != 6) {
            return Result.failure(Exception("Invite code must be 6 characters"))
        }

        val householdId = "HH_$cleanCode"

        val household = Household(
            id = householdId,
            ownerId = "remote_owner",
            inviteCode = cleanCode,
            memberIds = listOf("remote_owner", userId)
        )
        inviteCodeToHouseholdMap[cleanCode] = household
        saveHouseholdState(householdId, isOwner = false)

        ensureRealtimeSyncListening(householdId)

        val database = db
        if (database != null) {
            try {
                val docRef = database.collection("households").document(householdId)
                val snapshot = docRef.get().await()

                if (snapshot.exists()) {
                    val ownerId = snapshot.getString("ownerId") ?: ""
                    val isOwner = ownerId == userId

                    docRef.update("memberIds", FieldValue.arrayUnion(userId))

                    val remoteHousehold = Household(
                        id = householdId,
                        ownerId = ownerId,
                        inviteCode = cleanCode,
                        memberIds = (snapshot.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: listOf(userId)
                    )

                    saveHouseholdState(householdId, isOwner = isOwner)
                    return Result.success(remoteHousehold)
                }
            } catch (e: Exception) {
                Log.e("InventoryRepo", "Error joining remote household $householdId: ${e.message}", e)
            }
        }

        return Result.success(household)
    }

    private fun ensureRealtimeSyncListening(householdId: String) {
        if (householdId.isEmpty()) return
        RealtimeSyncBridge.startListening(
            householdId = householdId,
            onInventorySync = { items ->
                getItemsFlowFor(householdId).value = items
            },
            onBillsSync = { bills ->
                getBillsFlowFor(householdId).value = bills
            },
            onPaycheckSync = { amount ->
                getPaycheckFlowFor(householdId).value = amount
                getBillsFlowFor(householdId).update { ArrayList(it) }
            },
            onFeedbackSync = { feedback ->
                getFeedbackFlowFor(householdId).value = feedback
            },
            onTicketsSync = { tickets ->
                getTicketsFlowFor(householdId).value = tickets
            },
            onMessagesSync = { ticketId, messages ->
                getMessagesFlowFor(householdId, ticketId).value = messages
            }
        )
    }

    private fun calculateNextWed830PMPayday(): Pair<String, Int> {
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_WEEK) // Wed = 4
        var daysUntilWed = (Calendar.WEDNESDAY - today + 7) % 7
        if (daysUntilWed == 0) {
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val min = now.get(Calendar.MINUTE)
            if (hour > 20 || (hour == 20 && min >= 30)) {
                daysUntilWed = 14
            }
        }
        val days = if (daysUntilWed == 0) 14 else daysUntilWed
        val text = when (days) {
            14 -> "Arrives Tonight at 8:30 PM!"
            1 -> "Tomorrow 8:30 PM"
            else -> "Wed 8:30 PM ($days days)"
        }
        return Pair(text, days)
    }

    fun getInventoryFlow(householdId: String): Flow<List<HouseholdItem>> = callbackFlow {
        if (householdId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        ensureRealtimeSyncListening(householdId)

        val targetFlow = getItemsFlowFor(householdId)

        trySend(targetFlow.value)

        val job = CoroutineScope(Dispatchers.Default).launch {
            targetFlow.collect { items ->
                trySend(items)
            }
        }

        val database = db
        val listener = if (database != null) {
            database.collection("households")
                .document(householdId)
                .collection("inventory")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val remoteItems = snapshot.documents.mapNotNull { doc ->
                            try {
                                val item = doc.toObject(HouseholdItem::class.java)
                                item?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (remoteItems.isNotEmpty()) {
                            targetFlow.value = remoteItems
                            trySend(remoteItems)
                        }
                    }
                }
        } else null

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    fun getBillsFlow(householdId: String): Flow<List<BillItem>> = callbackFlow {
        if (householdId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        ensureRealtimeSyncListening(householdId)

        val targetFlow = getBillsFlowFor(householdId)

        trySend(targetFlow.value)

        val job = CoroutineScope(Dispatchers.Default).launch {
            targetFlow.collect { bills ->
                trySend(bills)
            }
        }

        val database = db
        val listener = if (database != null) {
            database.collection("households")
                .document(householdId)
                .collection("bills")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val remoteBills = snapshot.documents.mapNotNull { doc ->
                            try {
                                val bill = doc.toObject(BillItem::class.java)
                                bill?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (remoteBills.isNotEmpty()) {
                            targetFlow.value = remoteBills
                            trySend(remoteBills)
                        }
                    }
                }
        } else null

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    fun getLedgerFlow(householdId: String): Flow<BudgetLedger> = callbackFlow {
        if (householdId.isEmpty()) {
            trySend(BudgetLedger())
            awaitClose { }
            return@callbackFlow
        }

        ensureRealtimeSyncListening(householdId)

        val (paydayText, daysRemaining) = calculateNextWed830PMPayday()
        val billsTargetFlow = getBillsFlowFor(householdId)
        val paycheckTargetFlow = getPaycheckFlowFor(householdId)

        val job = CoroutineScope(Dispatchers.Default).launch {
            billsTargetFlow.collect { bills ->
                val pendingTotal = bills.filter { !it.isPaid && it.dueInCurrentCycle }.sumOf { it.amount }
                trySend(
                    BudgetLedger(
                        paycheckTotal = paycheckTargetFlow.value,
                        pendingBillsTotal = pendingTotal,
                        nextPaydayText = paydayText,
                        daysUntilPayday = daysRemaining
                    )
                )
            }
        }

        val database = db
        val listener = if (database != null) {
            database.collection("households")
                .document(householdId)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null && snapshot.exists()) {
                        val paycheck = snapshot.getDouble("paycheckTotal")
                        if (paycheck != null) {
                            paycheckTargetFlow.value = paycheck
                            billsTargetFlow.update { ArrayList(it) }
                        }
                    }
                }
        } else null

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    fun getFeedbackFlow(householdId: String): Flow<List<FeedbackItem>> = callbackFlow {
        if (householdId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        ensureRealtimeSyncListening(householdId)

        val targetFlow = getFeedbackFlowFor(householdId)

        trySend(targetFlow.value)

        val job = CoroutineScope(Dispatchers.Default).launch {
            targetFlow.collect { list ->
                trySend(list)
            }
        }

        val database = db
        val listener = if (database != null) {
            database.collection("households")
                .document(householdId)
                .collection("feedback")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val remoteFeedback = snapshot.documents.mapNotNull { doc ->
                            try {
                                val fb = doc.toObject(FeedbackItem::class.java)
                                fb?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (remoteFeedback.isNotEmpty()) {
                            targetFlow.value = remoteFeedback
                            trySend(remoteFeedback)
                        }
                    }
                }
        } else null

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    fun getTicketsFlow(householdId: String): Flow<List<SupportTicket>> = callbackFlow {
        if (householdId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        ensureRealtimeSyncListening(householdId)

        val targetFlow = getTicketsFlowFor(householdId)

        trySend(targetFlow.value)

        val job = CoroutineScope(Dispatchers.Default).launch {
            targetFlow.collect { list ->
                trySend(list)
            }
        }

        val database = db
        val listener = if (database != null) {
            database.collection("households")
                .document(householdId)
                .collection("tickets")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val remoteTickets = snapshot.documents.mapNotNull { doc ->
                            try {
                                val ticket = doc.toObject(SupportTicket::class.java)
                                ticket?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (remoteTickets.isNotEmpty()) {
                            targetFlow.value = remoteTickets
                            trySend(remoteTickets)
                        }
                    }
                }
        } else null

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    fun getTicketMessagesFlow(householdId: String, ticketId: String): Flow<List<TicketMessage>> = callbackFlow {
        if (householdId.isEmpty() || ticketId.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        ensureRealtimeSyncListening(householdId)

        val targetFlow = getMessagesFlowFor(householdId, ticketId)

        trySend(targetFlow.value)

        val job = CoroutineScope(Dispatchers.Default).launch {
            targetFlow.collect { list ->
                trySend(list)
            }
        }

        val database = db
        val listener = if (database != null) {
            database.collection("households")
                .document(householdId)
                .collection("tickets")
                .document(ticketId)
                .collection("messages")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        val remoteMessages = snapshot.documents.mapNotNull { doc ->
                            try {
                                val msg = doc.toObject(TicketMessage::class.java)
                                msg?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (remoteMessages.isNotEmpty()) {
                            targetFlow.value = remoteMessages
                            trySend(remoteMessages)
                        }
                    }
                }
        } else null

        awaitClose {
            job.cancel()
            listener?.remove()
        }
    }

    suspend fun addItem(householdId: String, item: HouseholdItem) {
        val itemId = if (item.id.isBlank()) UUID.randomUUID().toString() else item.id
        val status = when {
            item.currentQuantity <= 0 -> "EMPTY"
            item.currentQuantity <= item.alertThreshold -> "LOW"
            else -> "STOCKED"
        }
        val newItem = item.copy(id = itemId, status = status)

        val updatedList = getItemsFlowFor(householdId).updateAndGet { list -> list + newItem }

        RealtimeSyncBridge.publishInventory(householdId, updatedList)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("inventory")
                    .document(newItem.id)
                    .set(newItem, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote add error: ${e.message}", e)
            }
        }
    }

    suspend fun editItem(householdId: String, updatedItem: HouseholdItem) {
        val status = when {
            updatedItem.currentQuantity <= 0 -> "EMPTY"
            updatedItem.currentQuantity <= updatedItem.alertThreshold -> "LOW"
            else -> "STOCKED"
        }
        val finalItem = updatedItem.copy(status = status)

        val updatedList = getItemsFlowFor(householdId).updateAndGet { list ->
            list.map { if (it.id == finalItem.id) finalItem else it }
        }

        RealtimeSyncBridge.publishInventory(householdId, updatedList)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("inventory")
                    .document(finalItem.id)
                    .set(finalItem, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote edit error: ${e.message}", e)
            }
        }
    }

    suspend fun deleteItem(householdId: String, itemId: String) {
        val updatedList = getItemsFlowFor(householdId).updateAndGet { list -> list.filter { it.id != itemId } }

        RealtimeSyncBridge.publishInventory(householdId, updatedList)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("inventory")
                    .document(itemId)
                    .delete()
            } catch (e: Exception) {
                Log.e("Firestore", "Remote delete error: ${e.message}", e)
            }
        }
    }

    suspend fun updateItemQuantity(householdId: String, itemId: String, newQuantity: Int) {
        val updatedList = getItemsFlowFor(householdId).updateAndGet { list ->
            list.map { item ->
                if (item.id == itemId) {
                    val status = when {
                        newQuantity <= 0 -> "EMPTY"
                        newQuantity <= item.alertThreshold -> "LOW"
                        else -> "STOCKED"
                    }
                    val isLow = newQuantity <= item.alertThreshold || status == "LOW" || status == "EMPTY"
                    val wasStocked = item.currentQuantity > item.alertThreshold
                    if (isLow && wasStocked) {
                        triggerRestockAlert(item.name, newQuantity)
                    }
                    item.copy(currentQuantity = newQuantity, status = status)
                } else {
                    item
                }
            }
        }

        RealtimeSyncBridge.publishInventory(householdId, updatedList)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                val target = updatedList.find { it.id == itemId } ?: HouseholdItem(id = itemId, currentQuantity = newQuantity)
                database.collection("households")
                    .document(householdId)
                    .collection("inventory")
                    .document(itemId)
                    .set(target, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote quantity update error: ${e.message}", e)
            }
        }
    }

    suspend fun addBill(householdId: String, bill: BillItem) {
        val billId = if (bill.id.isBlank()) UUID.randomUUID().toString() else bill.id
        val newBill = bill.copy(id = billId)

        val updatedBills = getBillsFlowFor(householdId).updateAndGet { list -> list + newBill }

        RealtimeSyncBridge.publishBills(householdId, updatedBills)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("bills")
                    .document(newBill.id)
                    .set(newBill, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote add bill error: ${e.message}", e)
            }
        }
    }

    suspend fun toggleBillPaid(householdId: String, billId: String) {
        val updatedBills = getBillsFlowFor(householdId).updateAndGet { list ->
            list.map {
                if (it.id == billId) it.copy(isPaid = !it.isPaid)
                else it
            }
        }

        RealtimeSyncBridge.publishBills(householdId, updatedBills)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                val target = updatedBills.find { it.id == billId } ?: BillItem(id = billId)
                database.collection("households")
                    .document(householdId)
                    .collection("bills")
                    .document(billId)
                    .set(target, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote toggle bill paid error: ${e.message}", e)
            }
        }
    }

    suspend fun toggleBillCycle(householdId: String, billId: String) {
        val updatedBills = getBillsFlowFor(householdId).updateAndGet { list ->
            list.map {
                if (it.id == billId) it.copy(dueInCurrentCycle = !it.dueInCurrentCycle)
                else it
            }
        }

        RealtimeSyncBridge.publishBills(householdId, updatedBills)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                val target = updatedBills.find { it.id == billId } ?: BillItem(id = billId)
                database.collection("households")
                    .document(householdId)
                    .collection("bills")
                    .document(billId)
                    .set(target, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote toggle bill cycle error: ${e.message}", e)
            }
        }
    }

    suspend fun deleteBill(householdId: String, billId: String) {
        val updatedBills = getBillsFlowFor(householdId).updateAndGet { list -> list.filter { it.id != billId } }

        RealtimeSyncBridge.publishBills(householdId, updatedBills)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("bills")
                    .document(billId)
                    .delete()
            } catch (e: Exception) {
                Log.e("Firestore", "Remote delete bill error: ${e.message}", e)
            }
        }
    }

    suspend fun updatePaycheck(householdId: String, paycheckAmount: Double) {
        getPaycheckFlowFor(householdId).value = paycheckAmount
        getBillsFlowFor(householdId).update { ArrayList(it) }

        RealtimeSyncBridge.publishPaycheck(householdId, paycheckAmount)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .set(mapOf("paycheckTotal" to paycheckAmount), SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote paycheck update error: ${e.message}", e)
            }
        }
    }

    suspend fun submitFeedback(householdId: String, feedback: FeedbackItem) {
        val fbId = if (feedback.id.isBlank()) UUID.randomUUID().toString() else feedback.id
        val finalFb = feedback.copy(id = fbId)

        val updatedList = getFeedbackFlowFor(householdId).updateAndGet { list -> list + finalFb }

        RealtimeSyncBridge.publishFeedback(householdId, updatedList)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("feedback")
                    .document(finalFb.id)
                    .set(finalFb, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote feedback submit error: ${e.message}", e)
            }
        }
    }

    suspend fun createSupportTicket(householdId: String, ticket: SupportTicket): SupportTicket {
        val tId = if (ticket.id.isBlank()) UUID.randomUUID().toString() else ticket.id
        val finalTicket = ticket.copy(
            id = tId,
            status = "WAITING",
            createdAt = Date()
        )

        val updatedTickets = getTicketsFlowFor(householdId).updateAndGet { list -> list + finalTicket }

        RealtimeSyncBridge.publishTickets(householdId, updatedTickets)

        // Seed initial description as m1 message
        if (finalTicket.description.isNotBlank()) {
            val initialMsg = TicketMessage(
                id = UUID.randomUUID().toString(),
                senderId = finalTicket.authorId,
                senderRole = finalTicket.authorRole,
                text = finalTicket.description,
                timestamp = Date()
            )
            val updatedMessages = getMessagesFlowFor(householdId, finalTicket.id).updateAndGet { list -> list + initialMsg }
            RealtimeSyncBridge.publishMessages(householdId, finalTicket.id, updatedMessages)
        }

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("tickets")
                    .document(finalTicket.id)
                    .set(finalTicket, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote ticket create error: ${e.message}", e)
            }
        }
        return finalTicket
    }

    suspend fun sendMessageToTicket(householdId: String, ticketId: String, message: TicketMessage) {
        val mId = if (message.id.isBlank()) UUID.randomUUID().toString() else message.id
        val finalMsg = message.copy(id = mId, timestamp = Date())

        val updatedMessages = getMessagesFlowFor(householdId, ticketId).updateAndGet { list -> list + finalMsg }
        RealtimeSyncBridge.publishMessages(householdId, ticketId, updatedMessages)

        // Auto-connect WAITING ticket if Owner responds
        val currentTickets = getTicketsFlowFor(householdId).value
        val targetTicket = currentTickets.find { it.id == ticketId }
        if (targetTicket != null && targetTicket.status == "WAITING") {
            updateTicketStatus(householdId, ticketId, "CONNECTED")
        }

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                database.collection("households")
                    .document(householdId)
                    .collection("tickets")
                    .document(ticketId)
                    .collection("messages")
                    .document(finalMsg.id)
                    .set(finalMsg, SetOptions.merge())
            } catch (e: Exception) {
                Log.e("Firestore", "Remote message send error: ${e.message}", e)
            }
        }
    }

    suspend fun sendMessage(householdId: String, ticketId: String, message: TicketMessage) {
        sendMessageToTicket(householdId, ticketId, message)
    }

    suspend fun updateTicketStatus(householdId: String, ticketId: String, newStatus: String) {
        val updatedTickets = getTicketsFlowFor(householdId).updateAndGet { list ->
            list.map { t ->
                if (t.id == ticketId) {
                    val connectedTime = if (newStatus == "CONNECTED") Date() else t.connectedAt
                    val closedTime = if (newStatus == "CLOSED") Date() else t.closedAt
                    t.copy(
                        status = newStatus,
                        connectedAt = connectedTime,
                        closedAt = closedTime
                    )
                } else t
            }
        }

        RealtimeSyncBridge.publishTickets(householdId, updatedTickets)

        val database = db
        if (database != null && householdId.isNotEmpty()) {
            try {
                val target = updatedTickets.find { it.id == ticketId }
                if (target != null) {
                    database.collection("households")
                        .document(householdId)
                        .collection("tickets")
                        .document(ticketId)
                        .set(target, SetOptions.merge())
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Remote ticket status update error: ${e.message}", e)
            }
        }
    }

    suspend fun fetchProductByBarcode(barcode: String): ScannedProductInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "TaskerPantryApp - Android - Version 1.0")

            if (connection.responseCode == 200) {
                val stream = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(stream)
                if (json.optInt("status") == 1) {
                    val product = json.optJSONObject("product")
                    if (product != null) {
                        val name = product.optString("product_name_en").ifEmpty {
                            product.optString("product_name").ifEmpty { "Scanned Product ($barcode)" }
                        }
                        val qtyStr = product.optString("quantity", "")
                        return@withContext ScannedProductInfo(
                            name = name,
                            quantity = 1,
                            packageSize = qtyStr
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("BarcodeLookup", "Error fetching barcode $barcode", e)
            null
        }
    }

    private fun triggerRestockAlert(itemName: String, quantity: Int) {
        Log.d("FCM", "Would send FCM to other household members: $itemName is LOW (Quantity: $quantity)")
        try {
            val context = MyFirebaseMessagingService.instance
            if (context != null) {
                MyFirebaseMessagingService.showLocalNotification(
                    context,
                    "Restock Alert!",
                    "$itemName is running low ($quantity left)"
                )
            }
        } catch (e: Exception) {
            Log.e("FCM", "Failed to trigger local notification", e)
        }
    }
}
