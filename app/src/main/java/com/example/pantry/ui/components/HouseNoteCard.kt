package com.example.pantry.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. Theme enum mirroring the SVG color gradients & borders
enum class BoardCardTone(
    val topColor: Color,
    val bottomColor: Color,
    val borderColor: Color,
    val accentTagColor: Color
) {
    AMBER(
        topColor = Color(0xFF78350F).copy(alpha = 0.45f),
        bottomColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        borderColor = Color(0xFFFBBF24).copy(alpha = 0.40f),
        accentTagColor = Color(0xFFFBBF24)
    ),
    CYAN(
        topColor = Color(0xFF0369A1).copy(alpha = 0.45f),
        bottomColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        borderColor = Color(0xFF38BDF8).copy(alpha = 0.35f),
        accentTagColor = Color(0xFF38BDF8)
    ),
    EMERALD(
        topColor = Color(0xFF065F46).copy(alpha = 0.45f),
        bottomColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        borderColor = Color(0xFF34D399).copy(alpha = 0.35f),
        accentTagColor = Color(0xFF34D399)
    ),
    VIOLET(
        topColor = Color(0xFF4C1D95).copy(alpha = 0.45f),
        bottomColor = Color(0xFF0F172A).copy(alpha = 0.85f),
        borderColor = Color(0xFFA78BFA).copy(alpha = 0.35f),
        accentTagColor = Color(0xFFA78BFA)
    ),
    SLATE(
        topColor = Color(0xFF1E293B).copy(alpha = 0.80f),
        bottomColor = Color(0xFF0F172A).copy(alpha = 0.90f),
        borderColor = Color(0xFF334155).copy(alpha = 0.50f),
        accentTagColor = Color(0xFF94A3B8)
    )
}

@Composable
fun HouseNoteCard(
    tag: String,                      // e.g. "🐾 PET ROUTINE & CARE" or "📌 MAINTENANCE & EMERGENCY"
    timestamp: String,                // e.g. "Today", "Oct 01"
    title: String,                    // e.g. "Buddy & Clifford Feeding Routine"
    body: String,                     // Main instruction or content text
    subtext: String? = null,          // Optional highlighted note/code at bottom
    tone: BoardCardTone = BoardCardTone.CYAN,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appTheme = com.example.pantry.ui.theme.LocalAppColors.current
    val bottomBg = if (appTheme.isSeasonal) appTheme.cardSurface else tone.bottomColor
    val cardStroke = if (appTheme.isSeasonal) appTheme.cardBorder else tone.borderColor

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, cardStroke)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(tone.topColor, bottomBg)
                    )
                )
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row: Category Badge + Timestamp / Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = tone.accentTagColor,
                        letterSpacing = 0.6.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timestamp,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                        if (onDelete != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "🗑",
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { onDelete() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Note Title
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Note Main Content
                Text(
                    text = body,
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 17.sp
                )

                // Optional Subtext/Code line (e.g. Master Code or 7 PM reminder)
                if (!subtext.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtext,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tone.accentTagColor
                    )
                }
            }
        }
    }
}
