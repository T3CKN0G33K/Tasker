package com.example.pantry.ui

import android.content.Context
import android.content.SharedPreferences

object UserPreferences {
    private const val PREF_NAME = "pantry_user_prefs"
    private const val KEY_UID = "uid"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_ROLE = "role"
    private const val KEY_HOUSEHOLD_ID = "household_id"
    private const val KEY_CAN_MANAGE = "can_manage_household"
    private const val KEY_DARK_MODE = "dark_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveUser(context: Context, user: UserProfile) {
        getPrefs(context).edit().apply {
            putString(KEY_UID, user.uid)
            putString(KEY_NAME, user.name)
            putString(KEY_EMAIL, user.email)
            putString(KEY_ROLE, user.role.name)
            putString(KEY_HOUSEHOLD_ID, user.householdId)
            putBoolean(KEY_CAN_MANAGE, user.canManageHousehold)
            apply()
        }
    }

    fun getUser(context: Context): UserProfile? {
        val prefs = getPrefs(context)
        val uid = prefs.getString(KEY_UID, null) ?: return null
        val name = prefs.getString(KEY_NAME, "User") ?: "User"
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val roleStr = prefs.getString(KEY_ROLE, Role.OWNER.name) ?: Role.OWNER.name
        val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
        val householdId = prefs.getString(KEY_HOUSEHOLD_ID, null)
        val canManage = prefs.getBoolean(KEY_CAN_MANAGE, false)

        return UserProfile(uid, name, email, role, householdId, canManage)
    }

    fun saveDarkMode(context: Context, isDark: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }

    fun isDarkMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, true)
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
