package com.example.pantry.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tasker.R

@Composable
fun WelcomeScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(120.dp))

                // Logo Container (size = 100.dp)
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .svgCardContainer(cornerRadius = 28.dp)
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_staple_box_logo),
                        contentDescription = "Pantry Logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Title
                Text(
                    text = "PANTRY TRACKER",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = "Household Supplies & Restock Ledger",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Feature Pill Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("⚡ Offline First", "📊 Restock Est.", "🔥 Firebase").forEach { pillText ->
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .svgCardContainer(cornerRadius = 17.dp)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pillText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }

            // Bottom Docked Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Primary "GET STARTED"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF007AFF), Color(0xFF38BDF8))
                            )
                        )
                        .clickable { onNavigateToSignUp() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GET STARTED",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                // Secondary "I already have an account"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .svgCardContainer(
                            cornerRadius = 20.dp,
                            backgroundColor = Color.White.copy(alpha = 0.08f),
                            borderColor = Color.White.copy(alpha = 0.15f)
                        )
                        .clickable { onNavigateToLogin() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "I already have an account",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Footer
                Text(
                    text = "v1.28 • Local Ledger Build",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}