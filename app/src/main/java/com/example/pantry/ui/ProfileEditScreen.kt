package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileEditScreen(
    currentUser: UserProfile,
    onBack: () -> Unit = {},
    onSaveChanges: (UserProfile) -> Unit = {}
) {
    var displayName by remember(currentUser.uid) { mutableStateOf(currentUser.name) }
    var currentPassword by remember(currentUser.uid) { mutableStateOf("") }
    var newPassword by remember(currentUser.uid) { mutableStateOf("") }
    var confirmPassword by remember(currentUser.uid) { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = Firebase.firestore
    val auth = Firebase.auth
    val colors = com.example.pantry.ui.theme.LocalAppColors.current
    val firebaseUser = auth.currentUser
    val canChangePassword = firebaseUser?.providerData?.any {
        it.providerId == EmailAuthProvider.PROVIDER_ID
    } == true
    val passwordChangeRequested = currentPassword.isNotBlank() || newPassword.isNotBlank() || confirmPassword.isNotBlank()
    val roleLabel = when {
        currentUser.role == Role.OWNER -> "Owner"
        currentUser.role == Role.ADMIN || currentUser.canManageHousehold -> "Admin"
        else -> "Member"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.cardSurface)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = colors.textPrimary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Edit profile", color = colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Manage your account details", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }

            item {
                ProfileSectionCard(colors = colors) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(colors.primaryAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                                color = colors.primaryAccent,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(displayName.ifBlank { "Your name" }, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(currentUser.email.ifBlank { "No email on file" }, color = colors.textSecondary, fontSize = 13.sp)
                        }
                        Surface(shape = RoundedCornerShape(50), color = colors.primaryAccent.copy(alpha = 0.10f)) {
                            Text(
                                text = roleLabel,
                                color = colors.primaryAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSectionHeading("PERSONAL INFORMATION", colors.textSecondary)
                    ProfileSectionCard(colors = colors) {
                        Text(
                            text = "Display name",
                            color = colors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = { Text("Enter your name") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            colors = profileTextFieldColors(colors)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "This name is shown to your household.",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSectionHeading("SIGN-IN", colors.textSecondary)
                    ProfileSectionCard(colors = colors) {
                        Text("Email address", color = colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(currentUser.email.ifBlank { "Not available" }, color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Email address changes aren't supported on this page.",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSectionHeading("PASSWORD", colors.textSecondary)
                    ProfileSectionCard(colors = colors) {
                        if (canChangePassword) {
                            Text(
                                "Leave these fields empty to keep your current password.",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            ProfilePasswordField("Current password", currentPassword, { currentPassword = it }, colors)
                            Spacer(Modifier.height(10.dp))
                            ProfilePasswordField("New password", newPassword, { newPassword = it }, colors)
                            Spacer(Modifier.height(10.dp))
                            ProfilePasswordField("Confirm new password", confirmPassword, { confirmPassword = it }, colors)
                            if (passwordChangeRequested && newPassword.isNotEmpty() && newPassword.length < 6) {
                                Spacer(Modifier.height(6.dp))
                                Text("Use at least 6 characters.", color = Color(0xFFDC2626), fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                "This account uses a sign-in provider that does not support password changes here.",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSectionHeading("HOUSEHOLD", colors.textSecondary)
                    ProfileSectionCard(colors = colors) {
                        ProfileReadOnlyRow("Role", roleLabel, colors)
                        HorizontalDivider(color = colors.cardBorder)
                        ProfileReadOnlyRow("Household ID", currentUser.householdId ?: "Not linked", colors)
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val cleanName = displayName.trim()
                        if (cleanName.isBlank()) {
                            Toast.makeText(context, "Enter a display name.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (passwordChangeRequested) {
                            if (!canChangePassword || currentPassword.isBlank() || newPassword.length < 6 || confirmPassword.isBlank()) {
                                Toast.makeText(context, "Enter your current password and a new password of at least 6 characters.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (newPassword != confirmPassword) {
                                Toast.makeText(context, "The new password and confirmation do not match.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }

                        isSaving = true
                        scope.launch {
                            try {
                                if (passwordChangeRequested) {
                                    val user = auth.currentUser ?: error("Please sign in again before changing your password.")
                                    val email = user.email ?: error("This account does not have a password sign-in.")
                                    user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
                                    user.updatePassword(newPassword).await()
                                }

                                val updatedProfile = currentUser.copy(name = cleanName)
                                val nameUpdate = mapOf("name" to cleanName)
                                db.collection("users").document(currentUser.uid)
                                    .set(nameUpdate, SetOptions.merge()).await()
                                val memberDirectorySynced = currentUser.householdId?.takeIf { it.isNotBlank() }?.let { householdId ->
                                    runCatching {
                                        db.collection("households").document(householdId)
                                            .collection("members").document(currentUser.uid)
                                            .set(nameUpdate, SetOptions.merge()).await()
                                    }.isSuccess
                                } ?: true

                                runCatching {
                                    auth.currentUser?.updateProfile(
                                        UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                                    )?.await()
                                }
                                onSaveChanges(updatedProfile)
                                Toast.makeText(
                                    context,
                                    when {
                                        !memberDirectorySynced -> "Profile saved; household member list couldn't be updated."
                                        passwordChangeRequested -> "Profile and password updated."
                                        else -> "Profile updated."
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (error: Exception) {
                                Toast.makeText(context, "Couldn't update profile: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryAccent,
                        contentColor = if (colors.primaryAccent.luminance() > 0.2f) Color(0xFF172033) else Color.White
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save changes", fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun ProfileSectionHeading(text: String, color: Color) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
}

@Composable
private fun ProfileSectionCard(colors: com.example.pantry.ui.theme.AppThemeColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.cardSurface,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun ProfileReadOnlyRow(label: String, value: String, colors: com.example.pantry.ui.theme.AppThemeColors) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = colors.textSecondary, fontSize = 12.sp)
        Text(value, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProfilePasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    colors: com.example.pantry.ui.theme.AppThemeColors
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = RoundedCornerShape(14.dp),
        colors = profileTextFieldColors(colors)
    )
}

@Composable
private fun profileTextFieldColors(colors: com.example.pantry.ui.theme.AppThemeColors) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.textPrimary,
    unfocusedTextColor = colors.textPrimary,
    focusedContainerColor = colors.background,
    unfocusedContainerColor = colors.background,
    focusedBorderColor = colors.primaryAccent,
    unfocusedBorderColor = colors.cardBorder,
    focusedLabelColor = colors.primaryAccent,
    unfocusedLabelColor = colors.textSecondary,
    cursorColor = colors.primaryAccent
)
