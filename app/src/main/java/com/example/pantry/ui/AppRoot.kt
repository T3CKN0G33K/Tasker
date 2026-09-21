package com.example.pantry.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import java.util.UUID

enum class AppState { LOADING, AUTH, ONBOARDING, DASHBOARD }

@Composable
fun AppRoot() {
    var currentAppState by remember { mutableStateOf(AppState.LOADING) }
    var activeUser by remember { mutableStateOf<UserProfile?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth

    // Auto-check for OTA app updates on startup
    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdates(context) { updateInfo ->
            pendingUpdate = updateInfo
        }
    }

    // On App Startup: Load local preferences first, then sync with Firebase Auth & Firestore in real-time
    LaunchedEffect(Unit) {
        val savedProfile = UserPreferences.getUser(context)
        val firebaseUser = auth.currentUser

        if (savedProfile != null && !savedProfile.householdId.isNullOrBlank() && firebaseUser != null) {
            // Instant load from device storage!
            activeUser = savedProfile
            currentAppState = AppState.DASHBOARD

            // Real-time listener on users/{uid} to sync roles, permissions, and household IDs instantly across versions
            db.collection("users").document(firebaseUser.uid)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val roleStr = doc.getString("role") ?: savedProfile.role.name
                        val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
                        val householdId = doc.getString("householdId") ?: savedProfile.householdId
                        val canManage = doc.getBoolean("canManageHousehold") ?: savedProfile.canManageHousehold

                        val syncedProfile = savedProfile.copy(role = role, householdId = householdId, canManageHousehold = canManage)
                        activeUser = syncedProfile
                        UserPreferences.saveUser(context, syncedProfile)
                    }
                }
        } else if (firebaseUser != null) {
            db.collection("users").document(firebaseUser.uid)
                .addSnapshotListener { doc, _ ->
                    val uid = firebaseUser.uid
                    val email = firebaseUser.email ?: ""
                    val name = doc?.getString("name") ?: email.substringBefore("@").ifBlank { "User" }
                    val roleStr = doc?.getString("role") ?: "OWNER"
                    val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
                    val householdId = doc?.getString("householdId") ?: savedProfile?.householdId
                    val canManage = doc?.getBoolean("canManageHousehold") ?: false

                    if (!householdId.isNullOrBlank()) {
                        val profile = UserProfile(uid, name, email, role, householdId, canManage)
                        activeUser = profile
                        UserPreferences.saveUser(context, profile)
                        currentAppState = AppState.DASHBOARD
                    } else {
                        // Check collectionGroup("members") for membership recovery
                        db.collectionGroup("members").whereEqualTo("email", email).get()
                            .addOnSuccessListener { memberDocs ->
                                val foundDoc = memberDocs.documents.firstOrNull()
                                val recoveredHid = foundDoc?.getString("householdId")
                                val recoveredRoleStr = foundDoc?.getString("role") ?: "MEMBER"
                                val recoveredRole = try { Role.valueOf(recoveredRoleStr) } catch (_: Exception) { Role.MEMBER }
                                val recoveredCanManage = foundDoc?.getBoolean("canManageHousehold") ?: false

                                val profile = UserProfile(uid, name, email, if (recoveredHid != null) recoveredRole else role, recoveredHid, recoveredCanManage)
                                activeUser = profile
                                UserPreferences.saveUser(context, profile)
                                currentAppState = if (recoveredHid.isNullOrBlank()) AppState.ONBOARDING else AppState.DASHBOARD
                            }
                            .addOnFailureListener {
                                val profile = UserProfile(uid, name, email, role, null, false)
                                activeUser = profile
                                UserPreferences.saveUser(context, profile)
                                currentAppState = AppState.ONBOARDING
                            }
                    }
                }
        } else {
            currentAppState = AppState.AUTH
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                }
                AppState.AUTH -> AuthScreen(
                    onLoginSuccess = { user -> 
                        activeUser = user
                        UserPreferences.saveUser(context, user)
                        currentAppState = if (user.householdId.isNullOrBlank()) AppState.ONBOARDING else AppState.DASHBOARD
                    }
                )
                AppState.ONBOARDING -> OnboardingScreen(
                    user = activeUser!!,
                    onHouseholdJoined = { updatedUser ->
                        activeUser = updatedUser
                        UserPreferences.saveUser(context, updatedUser)
                        currentAppState = AppState.DASHBOARD
                    }
                )
                AppState.DASHBOARD -> DashboardScreen(
                    currentUser = activeUser ?: UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
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

@Composable
fun AppUpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = Color(0xFF1C1C1E),
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
fun AuthScreen(onLoginSuccess: (UserProfile) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
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
                                val user = task.result?.user
                                val uid = user?.uid ?: UUID.randomUUID().toString()
                                val name = trimmedEmail.substringBefore("@")
                                val profile = UserProfile(uid, name, trimmedEmail, Role.OWNER, null, false)

                                // Create user document in Firestore
                                val userMap = hashMapOf(
                                    "uid" to uid,
                                    "name" to name,
                                    "email" to trimmedEmail,
                                    "role" to "OWNER",
                                    "householdId" to null,
                                    "canManageHousehold" to false
                                )
                                db.collection("users").document(uid).set(userMap)
                                    .addOnSuccessListener {
                                        isLoading = false
                                        onLoginSuccess(profile)
                                    }
                                    .addOnFailureListener {
                                        isLoading = false
                                        onLoginSuccess(profile)
                                    }
                            } else {
                                isLoading = false
                                Toast.makeText(context, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    auth.signInWithEmailAndPassword(trimmedEmail, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                val uid = user?.uid ?: ""

                                // Fetch user profile from Firestore
                                db.collection("users").document(uid).get()
                                    .addOnSuccessListener { doc ->
                                        val name = doc?.getString("name") ?: trimmedEmail.substringBefore("@")
                                        val roleStr = doc?.getString("role") ?: "OWNER"
                                        val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.OWNER }
                                        val householdId = doc?.getString("householdId")
                                        val canManage = doc?.getBoolean("canManageHousehold") ?: false

                                        if (!householdId.isNullOrBlank()) {
                                            isLoading = false
                                            onLoginSuccess(UserProfile(uid, name, trimmedEmail, role, householdId, canManage))
                                        } else {
                                            // Fallback check: Search collectionGroup("members") for membership recovery
                                            db.collectionGroup("members").whereEqualTo("email", trimmedEmail).get()
                                                .addOnSuccessListener { memberDocs ->
                                                    isLoading = false
                                                    val foundDoc = memberDocs.documents.firstOrNull()
                                                    val recoveredHid = foundDoc?.getString("householdId")
                                                    val recoveredRoleStr = foundDoc?.getString("role") ?: "MEMBER"
                                                    val recoveredRole = try { Role.valueOf(recoveredRoleStr) } catch (_: Exception) { Role.MEMBER }
                                                    val recoveredCanManage = foundDoc?.getBoolean("canManageHousehold") ?: false

                                                    val profile = UserProfile(uid, name, trimmedEmail, if (recoveredHid != null) recoveredRole else role, recoveredHid, recoveredCanManage)
                                                    if (recoveredHid != null) {
                                                        db.collection("users").document(uid).set(
                                                            hashMapOf(
                                                                "uid" to uid,
                                                                "name" to name,
                                                                "email" to trimmedEmail,
                                                                "role" to recoveredRole.name,
                                                                "householdId" to recoveredHid,
                                                                "canManageHousehold" to recoveredCanManage
                                                            )
                                                        )
                                                    }
                                                    onLoginSuccess(profile)
                                                }
                                                .addOnFailureListener {
                                                    isLoading = false
                                                    val profile = UserProfile(uid, name, trimmedEmail, role, null, false)
                                                    onLoginSuccess(profile)
                                                }
                                        }
                                    }
                                    .addOnFailureListener {
                                        isLoading = false
                                        val profile = UserProfile(uid, trimmedEmail.substringBefore("@"), trimmedEmail, Role.OWNER, null, false)
                                        onLoginSuccess(profile)
                                    }
                            } else {
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
fun OnboardingScreen(user: UserProfile, onHouseholdJoined: (UserProfile) -> Unit) {
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
            "canManageHousehold" to updatedUser.canManageHousehold
        )

        // 1. Save to users/{uid}
        db.collection("users").document(updatedUser.uid).set(memberMap)

        // 2. Save to households/{householdId}/members/{uid}
        updatedUser.householdId?.let { hid ->
            db.collection("households").document(hid)
                .collection("members").document(updatedUser.uid)
                .set(memberMap)
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
            text = "Create a new pantry or join an existing one using an invite link.", 
            color = Color.Gray, 
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
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
                        Text("I Have an Invite Link", color = Color(0xFF007AFF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = inviteLink,
                        onValueChange = { inviteLink = it },
                        placeholder = { Text("Paste link here...", color = Color.Gray) },
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
                                val linkTrimmed = inviteLink.trim()
                                if (linkTrimmed.startsWith("app://pantry/join/")) {
                                    val extractedId = linkTrimmed.substringAfterLast("/")
                                    if (extractedId.length > 5) { 
                                        val updatedUser = user.copy(role = Role.MEMBER, householdId = extractedId)
                                        saveHouseholdToUser(updatedUser)
                                    } else {
                                        Toast.makeText(context, "Invalid household ID.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Please paste a valid invite link.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                        ) {
                            Text("Join", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
