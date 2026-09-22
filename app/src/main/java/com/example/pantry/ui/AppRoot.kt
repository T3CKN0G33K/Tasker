package com.example.pantry.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

enum class AppState { LOADING, AUTH, ONBOARDING, DASHBOARD }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var currentAppState by remember { mutableStateOf(AppState.LOADING) }
    var activeUser by remember { mutableStateOf<UserProfile?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDarkMode by remember { mutableStateOf(UserPreferences.isDarkMode(context)) }

    val db = Firebase.firestore
    val auth = Firebase.auth

    // Dynamic System Status Bar Appearance (Prevents washed out status bar text in Light Mode!)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkMode
                controller.isAppearanceLightNavigationBars = !isDarkMode
            }
        }
    }

    // Auto-check for OTA app updates on startup
    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdates(context) { updateInfo ->
            pendingUpdate = updateInfo
        }
    }

    // On App Startup: Check local preferences and sync remote Firestore profile for auth & household routing
    LaunchedEffect(Unit) {
        val savedProfile = UserPreferences.getUser(context)
        val firebaseUser = auth.currentUser

        Log.d("AUTH_DEBUG", "App Startup -> savedProfile: ${savedProfile?.householdId}, firebaseUser: ${firebaseUser?.uid}")

        if (savedProfile != null && !savedProfile.householdId.isNullOrBlank() && firebaseUser != null) {
            // Instant load from local storage
            activeUser = savedProfile
            currentAppState = AppState.DASHBOARD

            // Sync user profile & householdId to Firestore users/{uid} root document
            val userSyncMap = hashMapOf<String, Any?>(
                "uid" to firebaseUser.uid,
                "name" to savedProfile.name,
                "email" to savedProfile.email,
                "role" to savedProfile.role.name,
                "householdId" to savedProfile.householdId,
                "canManageHousehold" to savedProfile.canManageHousehold,
                "themePreference" to savedProfile.themePreference
            )
            db.collection("users").document(firebaseUser.uid).set(userSyncMap, SetOptions.merge())

            // Real-time listener on users/{uid} to sync roles, permissions, theme, and household IDs
            db.collection("users").document(firebaseUser.uid)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val roleStr = doc.getString("role") ?: savedProfile.role.name
                        val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
                        val householdId = doc.getString("householdId") ?: savedProfile.householdId
                        val canManage = doc.getBoolean("canManageHousehold") ?: savedProfile.canManageHousehold
                        val themePref = doc.getString("themePreference") ?: activeUser?.themePreference ?: savedProfile.themePreference

                        val syncedProfile = savedProfile.copy(
                            role = role, 
                            householdId = householdId, 
                            canManageHousehold = canManage,
                            themePreference = themePref
                        )
                        activeUser = syncedProfile
                        UserPreferences.saveUser(context, syncedProfile)
                    }
                }
        } else if (firebaseUser != null) {
            // Async Navigation Guard: Keep AppState.LOADING active while Firestore fetch is in-flight!
            currentAppState = AppState.LOADING
            handleUserAuthSuccess(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                db = db,
                context = context
            ) { profile, targetState ->
                activeUser = profile
                currentAppState = targetState
            }
        } else {
            currentAppState = AppState.AUTH
        }
    }

    val rootBg = if (isDarkMode) Color.Black else Color(0xFFF2F2F7)

    Box(modifier = Modifier.fillMaxSize().background(rootBg)) {
        AnimatedContent(
            targetState = currentAppState,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "RootRouting"
        ) { state ->
            when (state) {
                AppState.LOADING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Color(0xFF007AFF))
                            Text("Connecting to Household...", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
                AppState.AUTH -> AuthScreen(
                    onAuthRouting = { profile, targetState ->
                        activeUser = profile
                        currentAppState = targetState
                    }
                )
                AppState.ONBOARDING -> OnboardingScreen(
                    user = activeUser ?: UserProfile("", "", "", Role.OWNER, null, false),
                    onHouseholdJoined = { updatedUser ->
                        activeUser = updatedUser
                        UserPreferences.saveUser(context, updatedUser)
                        currentAppState = AppState.DASHBOARD
                    },
                    onSignOut = {
                        auth.signOut()
                        UserPreferences.clear(context)
                        activeUser = null
                        currentAppState = AppState.AUTH
                    }
                )
                AppState.DASHBOARD -> DashboardScreen(
                    currentUser = activeUser ?: UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { newDark ->
                        isDarkMode = newDark
                        UserPreferences.saveDarkMode(context, newDark)
                    },
                    onProfileUpdated = { updatedUser ->
                        activeUser = updatedUser
                        UserPreferences.saveUser(context, updatedUser)
                    },
                    onSignOut = {
                        auth.signOut()
                        UserPreferences.clear(context)
                        activeUser = null
                        currentAppState = AppState.AUTH
                    }
                )
            }
        }
    }

    pendingUpdate?.let { updateInfo ->
        AppUpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { pendingUpdate = null }
        )
    }
}

// Resilient 5-Stage Fail-Safe Recovery Pipeline with AUTH_DEBUG logging & Async Navigation Guard
fun handleUserAuthSuccess(
    uid: String,
    email: String,
    db: FirebaseFirestore,
    context: Context,
    retryCount: Int = 0,
    onResult: (UserProfile, AppState) -> Unit
) {
    val rawEmail = email.trim()
    val lowerEmail = rawEmail.lowercase(Locale.US)
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    Log.d("AUTH_DEBUG", "Step 1: Initiating handleUserAuthSuccess for uid='$uid', email='$rawEmail', retryCount=$retryCount")

    fun completeRouting(householdId: String, role: Role, name: String, canManage: Boolean, themePref: String = "DEFAULT") {
        Log.d("AUTH_DEBUG", "Complete Routing -> Auto-linking householdId='$householdId' into users/$uid. Target: DASHBOARD")
        val profile = UserProfile(uid, name, rawEmail, role, householdId, canManage, themePref)
        UserPreferences.saveUser(context, profile)

        // Automatically link persistent householdId field at users/{userId}
        db.collection("users").document(uid).set(
            hashMapOf(
                "uid" to uid,
                "name" to name,
                "email" to rawEmail,
                "role" to role.name,
                "householdId" to householdId,
                "canManageHousehold" to canManage,
                "themePreference" to themePref
            ),
            SetOptions.merge()
        )

        onResult(profile, AppState.DASHBOARD)
    }

    fun retryOrFail() {
        if (retryCount < 3) {
            Log.w("AUTH_DEBUG", "Household check inconclusive. Scheduling retry ${retryCount + 1}/3 after 1000ms delay.")
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                handleUserAuthSuccess(uid, email, db, context, retryCount + 1, onResult)
            }
        } else {
            Log.w("AUTH_DEBUG", "All 5 recovery stages inconclusive for $uid / $rawEmail. Navigating to ONBOARDING.")
            val profile = UserProfile(uid, rawEmail.substringBefore("@"), rawEmail, Role.OWNER, null, false)
            UserPreferences.saveUser(context, profile)
            onResult(profile, AppState.ONBOARDING)
        }
    }

    // Refresh Auth ID Token before issuing Firestore queries
    currentUser?.getIdToken(false)?.addOnCompleteListener { tokenTask ->
        if (tokenTask.exception != null) {
            Log.e("AUTH_DEBUG", "Auth Token Refresh Exception: ${tokenTask.exception?.message}", tokenTask.exception)
        }

        // STAGE 1: Fetch users/{userId} directly
        Log.d("AUTH_DEBUG", "STAGE 1: Fetching users/$uid from Firestore.")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val snapshotExists = userDoc != null && userDoc.exists()
                Log.d("AUTH_DEBUG", "STAGE 1 Result -> users/$uid snapshotExists=$snapshotExists, data=${userDoc?.data}")

                val hid = userDoc?.getString("householdId")
                val themePref = userDoc?.getString("themePreference") ?: "DEFAULT"
                if (snapshotExists && !hid.isNullOrBlank()) {
                    val name = userDoc.getString("name") ?: rawEmail.substringBefore("@").ifBlank { "User" }
                    val roleStr = userDoc.getString("role") ?: "OWNER"
                    val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
                    val canManage = userDoc.getBoolean("canManageHousehold") ?: (role == Role.OWNER || role == Role.ADMIN)
                    
                    completeRouting(hid, role, name, canManage, themePref)
                    return@addOnSuccessListener
                }

                // STAGE 2: Search users collection by email
                Log.d("AUTH_DEBUG", "STAGE 2: users/$uid lacks householdId. Searching users collection by email='$rawEmail'.")
                db.collection("users").whereIn("email", listOf(rawEmail, lowerEmail).distinct()).get()
                    .addOnSuccessListener { emailUsers ->
                        Log.d("AUTH_DEBUG", "STAGE 2 Result -> users matching email count=${emailUsers.size()}")
                        val emailDoc = emailUsers.documents.firstOrNull { !it.getString("householdId").isNullOrBlank() }
                        val emailHid = emailDoc?.getString("householdId")

                        if (!emailHid.isNullOrBlank()) {
                            val name = emailDoc?.getString("name") ?: rawEmail.substringBefore("@").ifBlank { "User" }
                            val roleStr = emailDoc?.getString("role") ?: "OWNER"
                            val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
                            val canManage = emailDoc?.getBoolean("canManageHousehold") ?: (role == Role.OWNER || role == Role.ADMIN)
                            val tPref = emailDoc?.getString("themePreference") ?: "DEFAULT"
                            completeRouting(emailHid, role, name, canManage, tPref)
                            return@addOnSuccessListener
                        }

                        // STAGE 3: Search households collection where ownerId == uid
                        Log.d("AUTH_DEBUG", "STAGE 3: Searching households collection where ownerId=='$uid'.")
                        db.collection("households").whereEqualTo("ownerId", uid).get()
                            .addOnSuccessListener { ownerDocs ->
                                Log.d("AUTH_DEBUG", "STAGE 3 Result -> households where ownerId=='$uid' count=${ownerDocs.size()}")
                                val ownerDoc = ownerDocs.documents.firstOrNull()
                                val ownerHid = ownerDoc?.id?.ifBlank { ownerDoc.getString("id") ?: "" } ?: ""

                                if (ownerHid.isNotBlank()) {
                                    completeRouting(ownerHid, Role.OWNER, rawEmail.substringBefore("@").ifBlank { "User" }, true)
                                    return@addOnSuccessListener
                                }

                                // STAGE 4: Search households collection where memberIds contains uid
                                Log.d("AUTH_DEBUG", "STAGE 4: Searching households collection where memberIds contains '$uid'.")
                                db.collection("households").whereArrayContains("memberIds", uid).get()
                                    .addOnSuccessListener { memberDocs ->
                                        Log.d("AUTH_DEBUG", "STAGE 4 Result -> households where memberIds contains '$uid' count=${memberDocs.size()}")
                                        val memberDoc = memberDocs.documents.firstOrNull()
                                        val memberHid = memberDoc?.id?.ifBlank { memberDoc.getString("id") ?: "" } ?: ""
                                        if (memberHid.isNotBlank()) {
                                            val ownerId = memberDoc?.getString("ownerId") ?: ""
                                            val isOwner = ownerId == uid
                                            completeRouting(memberHid, if (isOwner) Role.OWNER else Role.MEMBER, rawEmail.substringBefore("@").ifBlank { "User" }, isOwner)
                                            return@addOnSuccessListener
                                        }

                                        // STAGE 5: Search collectionGroup("members") by UID or email
                                        Log.d("AUTH_DEBUG", "STAGE 5: Searching collectionGroup('members') by uid='$uid'.")
                                        db.collectionGroup("members").whereEqualTo("uid", uid).get()
                                            .addOnSuccessListener { memberGroupUidDocs ->
                                                Log.d("AUTH_DEBUG", "STAGE 5a Result -> collectionGroup('members') where uid=='$uid' count=${memberGroupUidDocs.size()}")
                                                val memberGroupDoc = memberGroupUidDocs.documents.firstOrNull { !it.getString("householdId").isNullOrBlank() }
                                                val recoveredHid = memberGroupDoc?.getString("householdId")
                                                if (!recoveredHid.isNullOrBlank()) {
                                                    val name = memberGroupDoc.getString("name") ?: rawEmail.substringBefore("@").ifBlank { "User" }
                                                    val roleStr = memberGroupDoc.getString("role") ?: "MEMBER"
                                                    val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.MEMBER }
                                                    val canManage = memberGroupDoc.getBoolean("canManageHousehold") ?: false
                                                    val tPref = memberGroupDoc.getString("themePreference") ?: "DEFAULT"
                                                    completeRouting(recoveredHid, role, name, canManage, tPref)
                                                    return@addOnSuccessListener
                                                }

                                                db.collectionGroup("members").whereIn("email", listOf(rawEmail, lowerEmail).distinct()).get()
                                                    .addOnSuccessListener { memberGroupEmailDocs ->
                                                        Log.d("AUTH_DEBUG", "STAGE 5b Result -> collectionGroup('members') where email in $rawEmail count=${memberGroupEmailDocs.size()}")
                                                        val emailMemberDoc = memberGroupEmailDocs.documents.firstOrNull { !it.getString("householdId").isNullOrBlank() }
                                                        val recoveredEmailHid = emailMemberDoc?.getString("householdId")
                                                        if (!recoveredEmailHid.isNullOrBlank()) {
                                                            val name = emailMemberDoc.getString("name") ?: rawEmail.substringBefore("@").ifBlank { "User" }
                                                            val roleStr = emailMemberDoc.getString("role") ?: "MEMBER"
                                                            val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.MEMBER }
                                                            val canManage = emailMemberDoc.getBoolean("canManageHousehold") ?: false
                                                            val tPref = emailMemberDoc.getString("themePreference") ?: "DEFAULT"
                                                            completeRouting(recoveredEmailHid, role, name, canManage, tPref)
                                                        } else {
                                                            retryOrFail()
                                                        }
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e("AUTH_DEBUG", "STAGE 5b Exception: ${e.message}", e)
                                                        retryOrFail()
                                                    }
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("AUTH_DEBUG", "STAGE 5a Exception: ${e.message}", e)
                                                retryOrFail()
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("AUTH_DEBUG", "STAGE 4 Exception: ${e.message}", e)
                                        retryOrFail()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("AUTH_DEBUG", "STAGE 3 Exception: ${e.message}", e)
                                retryOrFail()
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e("AUTH_DEBUG", "STAGE 2 Exception: ${e.message}", e)
                        retryOrFail()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("AUTH_DEBUG", "STAGE 1 Exception on users/$uid: ${e.message}", e)
                retryOrFail()
            }
    } ?: run {
        retryOrFail()
    }
}

@Composable
fun AppUpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    LiquidGlassDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("New Update Available", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Version ${updateInfo.latestVersionName}", color = Color(0xFF007AFF), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = updateInfo.releaseNotes,
                    color = Color.LightGray,
                    fontSize = 14.sp
                )

                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = Color(0xFF007AFF),
                            trackColor = Color(0xFF2C2C2E)
                        )
                        Text(
                            text = "Downloading update... ${(downloadProgress * 100).toInt()}%",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isDownloading = true
                    AppUpdateManager.downloadAndInstallApk(
                        context = context,
                        downloadUrl = updateInfo.apkUrl,
                        onProgress = { progress -> downloadProgress = progress },
                        onComplete = { isDownloading = false },
                        onError = { errorMsg ->
                            isDownloading = false
                            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                            // Fail-safe: Open URL in system browser
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                },
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Update Now", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (!isDownloading) {
                Row {
                    TextButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.apkUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }) {
                        Text("Browser Download", color = Color(0xFF007AFF))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) {
                        Text("Later", color = Color.Gray)
                    }
                }
            }
        }
    )
}

@Composable
fun AuthScreen(onAuthRouting: (UserProfile, AppState) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }
    val auth = Firebase.auth
    val db = Firebase.firestore

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isSignUp) "Create Account" else "Welcome Back", 
            color = Color.White, 
            fontSize = 34.sp, 
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enter your email and password to continue.", 
            color = Color.Gray, 
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.EmailAddress },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1C1C1E),
                unfocusedContainerColor = Color(0xFF1C1C1E),
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1C1C1E),
                unfocusedContainerColor = Color(0xFF1C1C1E),
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { 
                val trimmedEmail = email.trim()
                if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
                    Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (password.length < 6) {
                    Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                if (isSignUp) {
                    auth.createUserWithEmailAndPassword(trimmedEmail, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                autofillManager?.commit()
                                val user = task.result?.user
                                val uid = user?.uid ?: UUID.randomUUID().toString()
                                val name = trimmedEmail.substringBefore("@")
                                val profile = UserProfile(uid, name, trimmedEmail, Role.OWNER, null, false, "DEFAULT")

                                // Create user document in Firestore
                                val userMap = hashMapOf(
                                    "uid" to uid,
                                    "name" to name,
                                    "email" to trimmedEmail,
                                    "role" to "OWNER",
                                    "householdId" to null,
                                    "canManageHousehold" to false,
                                    "themePreference" to "DEFAULT"
                                )
                                db.collection("users").document(uid).set(userMap)
                                    .addOnSuccessListener {
                                        isLoading = false
                                        UserPreferences.saveUser(context, profile)
                                        onAuthRouting(profile, AppState.ONBOARDING)
                                    }
                                    .addOnFailureListener {
                                        isLoading = false
                                        UserPreferences.saveUser(context, profile)
                                        onAuthRouting(profile, AppState.ONBOARDING)
                                    }
                            } else {
                                autofillManager?.cancel()
                                isLoading = false
                                Toast.makeText(context, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    auth.signInWithEmailAndPassword(trimmedEmail, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                autofillManager?.commit()
                                val firebaseUser = task.result?.user
                                if (firebaseUser != null) {
                                    handleUserAuthSuccess(
                                        uid = firebaseUser.uid,
                                        email = trimmedEmail,
                                        db = db,
                                        context = context
                                    ) { profile, targetState ->
                                        isLoading = false
                                        onAuthRouting(profile, targetState)
                                    }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Login failed: User record null.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                autofillManager?.cancel()
                                isLoading = false
                                Toast.makeText(context, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(if (isSignUp) "Sign Up" else "Sign In", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { isSignUp = !isSignUp }, enabled = !isLoading) {
            Text(
                text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                color = Color(0xFF007AFF)
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    user: UserProfile, 
    onHouseholdJoined: (UserProfile) -> Unit,
    onSignOut: () -> Unit = {}
) {
    var showLinkInput by remember { mutableStateOf(false) }
    var inviteLink by remember { mutableStateOf("") }
    val context = LocalContext.current
    val db = Firebase.firestore

    fun saveHouseholdToUser(updatedUser: UserProfile) {
        UserPreferences.saveUser(context, updatedUser)

        val memberMap = hashMapOf(
            "uid" to updatedUser.uid,
            "name" to updatedUser.name,
            "email" to updatedUser.email,
            "role" to updatedUser.role.name,
            "householdId" to updatedUser.householdId,
            "canManageHousehold" to updatedUser.canManageHousehold,
            "themePreference" to updatedUser.themePreference
        )

        // 1. Save to users/{uid}
        db.collection("users").document(updatedUser.uid).set(memberMap, SetOptions.merge())

        // 2. Save to households/{householdId} root document
        val hid = updatedUser.householdId
        if (!hid.isNullOrBlank()) {
            val householdRootMap = hashMapOf(
                "id" to hid,
                "ownerId" to updatedUser.uid,
                "ownerEmail" to updatedUser.email,
                "memberIds" to listOf(updatedUser.uid),
                "memberEmails" to listOf(updatedUser.email)
            )
            db.collection("households").document(hid).set(householdRootMap, SetOptions.merge())

            // 3. Save to households/{householdId}/members/{uid}
            db.collection("households").document(hid)
                .collection("members").document(updatedUser.uid)
                .set(memberMap, SetOptions.merge())
        }

        onHouseholdJoined(updatedUser)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Join a Household", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Logged in as ${user.email}\nCreate a new household, join an existing one, or enter your Household ID.", 
            color = Color.Gray, 
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
        
        Spacer(modifier = Modifier.height(36.dp))
        
        AnimatedContent(targetState = showLinkInput, label = "OnboardingState") { isInputState ->
            if (!isInputState) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = { 
                            val newHouseholdId = UUID.randomUUID().toString()
                            val updatedUser = user.copy(role = Role.OWNER, householdId = newHouseholdId)
                            saveHouseholdToUser(updatedUser)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                    ) {
                        Text("Create New Household", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { showLinkInput = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1C1E))
                    ) {
                        Text("Enter Household ID / Invite Link", color = Color(0xFF007AFF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = inviteLink,
                        onValueChange = { inviteLink = it },
                        placeholder = { Text("Paste link or enter Household ID...", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1C1C1E),
                            unfocusedContainerColor = Color(0xFF1C1C1E),
                            focusedBorderColor = Color(0xFF007AFF),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { showLinkInput = false },
                            modifier = Modifier.weight(1f).height(56.dp)
                        ) {
                            Text("Cancel", color = Color.Gray, fontSize = 16.sp)
                        }
                        
                        Button(
                            onClick = {
                                val rawInput = inviteLink.trim()
                                val extractedId = when {
                                    rawInput.startsWith("app://pantry/join/") -> rawInput.substringAfterLast("/")
                                    else -> rawInput
                                }
                                if (extractedId.isNotBlank()) { 
                                    val updatedUser = user.copy(role = Role.MEMBER, householdId = extractedId)
                                    saveHouseholdToUser(updatedUser)
                                } else {
                                    Toast.makeText(context, "Please enter a valid Household ID or invite link.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                        ) {
                            Text("Connect", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Switch Account / Sign Out Button directly on Onboarding Screen
        TextButton(onClick = { onSignOut() }) {
            Text("Sign Out / Switch Account", color = Color(0xFFFF3B30), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
