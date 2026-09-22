package com.example.pantry.ui

enum class Role { OWNER, ADMIN, MEMBER }

data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val role: Role,
    val householdId: String? = null,
    val canManageHousehold: Boolean = false,
    val themePreference: String = "DEFAULT" // "DEFAULT", "CYAN", "PURPLE"
)

data class FeedbackItem(
    val id: String,
    val senderName: String,
    val senderEmail: String,
    val topic: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
