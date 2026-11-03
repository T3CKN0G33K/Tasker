package com.example.pantry.data

import com.example.pantry.ui.BoardNoteItem
import com.example.pantry.ui.PantryItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val currentUserId: String
        get() = auth.currentUser?.uid.orEmpty()

    // Base Household Collection Path
    private fun householdRef(householdId: String) =
        firestore.collection("households").document(householdId)

    // ---------------------------------------------------------
    // 1. PANTRY (Real-Time Live Inventory)
    // ---------------------------------------------------------
    fun observePantryItems(householdId: String): Flow<List<PantryItem>> = callbackFlow {
        val listener = householdRef(householdId).collection("pantry")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PantryItem::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(items)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updatePantryStock(householdId: String, itemId: String, newQuantity: Double) {
        householdRef(householdId).collection("pantry").document(itemId)
            .update("quantity", newQuantity).await()
    }

    // ---------------------------------------------------------
    // 2. HOUSE BOARD (Strict Real-Time Notes & Pinned Notices)
    // ---------------------------------------------------------
    fun observeHouseNotes(householdId: String): Flow<List<BoardNoteItem>> = callbackFlow {
        val listener = householdRef(householdId).collection("notes")
            .orderBy("isPinned", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val notes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(BoardNoteItem::class.java)?.copy(id = doc.id)
                }.orEmpty()
                trySend(notes)
            }
        awaitClose { listener.remove() }
    }

    suspend fun deleteHouseNote(householdId: String, noteId: String) {
        householdRef(householdId).collection("notes").document(noteId).delete().await()
    }

    // ---------------------------------------------------------
    // 3. BUDGET (Monthly Allowance & Interactive Bills Checklist)
    // ---------------------------------------------------------
    fun observeMonthlyBudget(householdId: String, cycleKey: String): Flow<BudgetCycleData> = callbackFlow {
        val listener = householdRef(householdId).collection("budgets").document(cycleKey)
            .addSnapshotListener { snapshot, _ ->
                val data = snapshot?.toObject(BudgetCycleData::class.java) 
                    ?: BudgetCycleData(cycleId = cycleKey, allowance = 0.0, bills = emptyList())
                trySend(data)
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleBillPaid(householdId: String, cycleKey: String, billId: String, isPaid: Boolean) {
        val docRef = householdRef(householdId).collection("budgets").document(cycleKey)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentData = snapshot.toObject(BudgetCycleData::class.java)
            if (currentData != null) {
                val updatedBills = currentData.bills.map { bill ->
                    if (bill.id == billId) bill.copy(isPaid = isPaid) else bill
                }
                transaction.update(docRef, "bills", updatedBills)
            }
        }.await()
    }

    // ---------------------------------------------------------
    // 4. USER PROFILE & PHOTO STORAGE
    // ---------------------------------------------------------
    fun observeUserProfile(userId: String = currentUserId): Flow<UserProfileData> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, _ ->
                val profile = snapshot?.toObject(UserProfileData::class.java)
                    ?: UserProfileData(uid = userId, displayName = "User", role = "Member")
                trySend(profile)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateUserProfile(userId: String = currentUserId, name: String, photoUrl: String?) {
        val updates = mutableMapOf<String, Any>("displayName" to name)
        photoUrl?.let { updates["photoUrl"] = it }
        firestore.collection("users").document(userId).update(updates).await()
    }
}
