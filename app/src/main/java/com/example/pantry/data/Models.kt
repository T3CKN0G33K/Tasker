package com.example.pantry.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

@IgnoreExtraProperties
data class Household(
    var id: String = "",
    var ownerId: String = "",
    var inviteCode: String = "",
    var memberIds: List<String> = emptyList()
) {
    fun isUserOwner(userId: String): Boolean = userId == ownerId
}

@IgnoreExtraProperties
data class HouseholdItem(
    var id: String = "",
    var name: String = "",
    var currentQuantity: Int = 0,
    var alertThreshold: Int = 0,
    @get:PropertyName("isStaple") @set:PropertyName("isStaple") var isStaple: Boolean = true,
    var status: String = "STOCKED", // "STOCKED", "LOW", "EMPTY"
    var barcode: String = "",
    var packageSize: String = ""
)

@IgnoreExtraProperties
data class BillItem(
    var id: String = "",
    var name: String = "",
    var amount: Double = 0.0,
    @get:PropertyName("isPaid") @set:PropertyName("isPaid") var isPaid: Boolean = false,
    var dueDate: String = "",
    @get:PropertyName("dueInCurrentCycle") @set:PropertyName("dueInCurrentCycle") var dueInCurrentCycle: Boolean = true
)

@IgnoreExtraProperties
data class BudgetLedger(
    var paycheckTotal: Double = 1250.0,
    var pendingBillsTotal: Double = 0.0,
    var payCadence: String = "Bi-Weekly (Home Depot)",
    var nextPaydayText: String = "Wed 8:30 PM",
    var daysUntilPayday: Int = 4
) {
    val safeToSpend: Double
        get() = paycheckTotal - pendingBillsTotal
}

@IgnoreExtraProperties
data class FeedbackItem(
    var id: String = "",
    var authorRole: String = "Member", // "Owner" or "Member"
    var type: String = "FEATURE", // "BUG" or "FEATURE"
    var title: String = "",
    var description: String = "",
    var status: String = "SUBMITTED", // "SUBMITTED", "IN_REVIEW", "RESOLVED"
    var timestampText: String = "Just Now"
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId var id: String = "",
    var authorId: String = "",
    var authorRole: String = "Member",
    var type: String = "BUG", // e.g., "BUG" or "FEATURE"
    var title: String = "",
    var description: String = "",
    var status: String = "WAITING", // Strict States: WAITING, CONNECTED, CLOSED
    @ServerTimestamp var createdAt: Date? = null,
    var connectedAt: Date? = null,
    var closedAt: Date? = null
)

@IgnoreExtraProperties
data class TicketMessage(
    @DocumentId var id: String = "",
    var senderId: String = "",
    var senderRole: String = "", // "Owner" or "Member"
    var text: String = "",
    @ServerTimestamp var timestamp: Date? = null
)
