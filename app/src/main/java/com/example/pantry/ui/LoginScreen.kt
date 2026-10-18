package com.example.pantry.ui

import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

@Composable
fun GlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: () -> Unit = {},
    contentType: ContentType? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF38BDF8),
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .svgCardContainer(
                    cornerRadius = 14.dp,
                    backgroundColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                    borderColor = Color(0xFF38BDF8).copy(alpha = 0.25f)
                )
                .then(
                    if (contentType != null) {
                        Modifier.semantics { this.contentType = contentType }
                    } else Modifier
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF64748B),
                    fontSize = 15.sp
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onImeAction() },
                    onNext = { onImeAction() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onAuthRouting: (UserProfile, AppState) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }
    val auth = Firebase.auth
    val db = Firebase.firestore

    fun performLogin() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            Toast.makeText(context, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6) {
            Toast.makeText(context, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }

        keyboardController?.hide()
        isLoading = true

        auth.signInWithEmailAndPassword(trimmedEmail, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    autofillManager?.commit()
                    val uid = task.result?.user?.uid ?: ""
                    handleUserAuthSuccess(uid, trimmedEmail, db, context) { profile, targetState ->
                        isLoading = false
                        onAuthRouting(profile, targetState)
                    }
                } else {
                    isLoading = false
                    val msg = task.exception?.localizedMessage ?: "Authentication failed."
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Color(0xFF090D16))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header Row: Back Button & Title
            Spacer(modifier = Modifier.height(44.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .svgCardContainer(
                            cornerRadius = 18.dp,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            borderColor = Color.White.copy(alpha = 0.2f)
                        )
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Welcome Back",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Form Card Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .svgCardContainer(cornerRadius = 28.dp)
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    GlassInputField(
                        value = email,
                        onValueChange = { email = it },
                        label = "EMAIL ADDRESS",
                        placeholder = "name@example.com",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        contentType = ContentType.EmailAddress
                    )

                    GlassInputField(
                        value = password,
                        onValueChange = { password = it },
                        label = "PASSWORD",
                        placeholder = "••••••••",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        onImeAction = { performLogin() },
                        contentType = ContentType.Password
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF007AFF))
                            .clickable(enabled = !isLoading) { performLogin() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "SIGN IN",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        Text(
                            text = "  OR  ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .svgCardContainer(
                                cornerRadius = 14.dp,
                                backgroundColor = Color.White.copy(alpha = 0.06f),
                                borderColor = Color.White.copy(alpha = 0.12f)
                            )
                            .clickable {
                                Toast.makeText(context, "Google Sign-In is managed via Credential Manager", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue with Google",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}