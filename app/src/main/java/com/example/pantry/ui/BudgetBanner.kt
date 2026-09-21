package com.example.pantry.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantry.data.BudgetLedger

@Composable
fun BudgetBanner(
    ledger: BudgetLedger,
    isOwner: Boolean = true,
    onManageBudgetClick: () -> Unit = {},
    useLiquidGlass: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .clickable(enabled = isOwner, onClick = onManageBudgetClick),
        color = if (useLiquidGlass) Color(0x22FFFFFF) else Color(0xFF1C1C1E).copy(alpha = 0.95f),
        border = if (useLiquidGlass) BorderStroke(1.dp, Color(0x33FFFFFF)) else null,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SAFE TO SPEND THIS CYCLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Next Payday: ${ledger.nextPaydayText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF34C759),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isOwner) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2C2C2E),
                        modifier = Modifier.clickable(onClick = onManageBudgetClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Manage Budget",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Paycheck & Bills",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Text(
                text = "$${String.format("%.2f", ledger.safeToSpend)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Bi-Weekly Paycheck: $${String.format("%.2f", ledger.paycheckTotal)} | Bills Due This Cycle: $${String.format("%.2f", ledger.pendingBillsTotal)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E8E93),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
