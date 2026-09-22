package com.example.pantry.ui

import android.content.Context
import android.content.SharedPreferences

data class TaskbarGlassSettings(
    val glassAlpha: Float = 0.0f,               // 0.0 = 100% crystal clear!
    val blurAmount: Float = 0.05f,              // Fine Gaussian blur
    val bevelWidthPx: Float = 24f,              // Bevel edge width
    val refractionHeightPx: Float = 36f,        // Refraction height
    val dispersionStrength: Float = 0.08f,      // Chromatic dispersion
    val edgeHighlightOpacity: Float = 100f,     // Specular rim opacity
    val isClearMaterial: Boolean = true,        // Clear vs Regular material
    val activeBadgeAlpha: Float = 0.25f         // Active tab badge glass alpha
)

object UserPreferences {
    private const val PREF_NAME = "pantry_user_prefs"
    private const val KEY_UID = "uid"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_ROLE = "role"
    private const val KEY_HOUSEHOLD_ID = "household_id"
    private const val KEY_CAN_MANAGE = "can_manage_household"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_THEME_PREFERENCE = "theme_preference"

    private const val KEY_GLASS_ALPHA = "glass_alpha"
    private const val KEY_GLASS_BLUR = "glass_blur"
    private const val KEY_GLASS_BEVEL = "glass_bevel"
    private const val KEY_GLASS_REFRACT = "glass_refract"
    private const val KEY_GLASS_DISPERSION = "glass_dispersion"
    private const val KEY_GLASS_EDGE_OPACITY = "glass_edge_opacity"
    private const val KEY_GLASS_IS_CLEAR = "glass_is_clear"
    private const val KEY_GLASS_BADGE_ALPHA = "glass_badge_alpha"

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
            putString(KEY_THEME_PREFERENCE, user.themePreference)
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
        val themePref = prefs.getString(KEY_THEME_PREFERENCE, "DEFAULT") ?: "DEFAULT"

        return UserProfile(uid, name, email, role, householdId, canManage, themePref)
    }

    fun saveDarkMode(context: Context, isDark: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DARK_MODE, isDark).apply()
    }

    fun isDarkMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DARK_MODE, true)
    }

    fun saveGlassSettings(context: Context, settings: TaskbarGlassSettings) {
        getPrefs(context).edit().apply {
            putFloat(KEY_GLASS_ALPHA, settings.glassAlpha)
            putFloat(KEY_GLASS_BLUR, settings.blurAmount)
            putFloat(KEY_GLASS_BEVEL, settings.bevelWidthPx)
            putFloat(KEY_GLASS_REFRACT, settings.refractionHeightPx)
            putFloat(KEY_GLASS_DISPERSION, settings.dispersionStrength)
            putFloat(KEY_GLASS_EDGE_OPACITY, settings.edgeHighlightOpacity)
            putBoolean(KEY_GLASS_IS_CLEAR, settings.isClearMaterial)
            putFloat(KEY_GLASS_BADGE_ALPHA, settings.activeBadgeAlpha)
            apply()
        }
    }

    fun getGlassSettings(context: Context): TaskbarGlassSettings {
        val prefs = getPrefs(context)
        return TaskbarGlassSettings(
            glassAlpha = prefs.getFloat(KEY_GLASS_ALPHA, 0.0f),
            blurAmount = prefs.getFloat(KEY_GLASS_BLUR, 0.05f),
            bevelWidthPx = prefs.getFloat(KEY_GLASS_BEVEL, 24f),
            refractionHeightPx = prefs.getFloat(KEY_GLASS_REFRACT, 36f),
            dispersionStrength = prefs.getFloat(KEY_GLASS_DISPERSION, 0.08f),
            edgeHighlightOpacity = prefs.getFloat(KEY_GLASS_EDGE_OPACITY, 100f),
            isClearMaterial = prefs.getBoolean(KEY_GLASS_IS_CLEAR, true),
            activeBadgeAlpha = prefs.getFloat(KEY_GLASS_BADGE_ALPHA, 0.25f)
        )
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
