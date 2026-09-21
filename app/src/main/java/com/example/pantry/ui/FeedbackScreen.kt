package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

@Composable
fun FeedbackScreen(
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc")
) {
    var message by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Bug Report") }
    var isSubmitted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val feedbackTypes = listOf("Bug Report", "Feature Request", "Other")

    val context = LocalContext.current
    val db = Firebase.firestore

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Text(
            text = "Feedback",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        // Topic Selector (iOS Segmented Control Style)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Topic", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                feedbackTypes.forEach { type ->
                    val isSelected = selectedType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF007AFF) else Color(0xFF1C1C1E))
                            .clickable { selectedType = type },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.replace(" ", "\n"),
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Multiline Message Input
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Message", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = message,
                onValueChange = { 
                    message = it
                    isSubmitted = false
                },
                placeholder = { Text("Tell us what's on your mind...", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1C1C1E),
                    unfocusedContainerColor = Color(0xFF1C1C1E),
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF007AFF)
                )
            )
        }

        if (isSubmitted) {
            Text(
                text = "Thank you! Your feedback has been submitted.",
                color = Color(0xFF34C759),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Large Submit Button
        Button(
            onClick = {
                if (message.isNotBlank()) {
                    isLoading = true
                    val hid = currentUser.householdId
                    if (hid != null) {
                        val docRef = db.collection("households").document(hid).collection("feedbacks").document()
                        val feedbackData = hashMapOf(
                            "senderUid" to currentUser.uid,
                            "senderName" to currentUser.name,
                            "senderEmail" to currentUser.email,
                            "topic" to selectedType,
                            "message" to message.trim(),
                            "timestamp" to System.currentTimeMillis()
                        )
                        docRef.set(feedbackData)
                            .addOnSuccessListener {
                                isLoading = false
                                isSubmitted = true
                                message = ""
                                selectedType = "Bug Report"
                            }
                            .addOnFailureListener { e ->
                                isLoading = false
                                Toast.makeText(context, "Failed to send feedback: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        isLoading = false
                        isSubmitted = true
                        message = ""
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Send Feedback", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
