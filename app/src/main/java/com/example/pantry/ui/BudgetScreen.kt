package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pantry.data.BillItem
import com.example.pantry.ui.theme.themedCard
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

val defaultHouseholdBills = listOf(
    BillItem(id = "b1", name = "Rent", amount = 397.00, isPaid = false, dueDate = "Due 1st"),
    BillItem(id = "b2", name = "Disney Plus", amount = 35.99, isPaid = false, dueDate = "Due 1st"),
    BillItem(id = "b3", name = "Xfinity", amount = 80.94, isPaid = false, dueDate = "Due 4th"),
    BillItem(id = "b4", name = "Google ONE", amount = 21.24, isPaid = false, dueDate = "Due 8th"),
    BillItem(id = "b5", name = "Safety Insurance", amount = 124.71, isPaid = false, dueDate = "Due 10th"),
    BillItem(id = "b6", name = "YouTube", amount = 13.99, isPaid = false, dueDate = "Due 10th"),
    BillItem(id = "b7", name = "Spotify", amount = 12.99, isPaid = false, dueDate = "Due 16th"),
    BillItem(id = "b8", name = "National Grid", amount = 138.00, isPaid = false, dueDate = "Due 16th"),
    BillItem(id = "b9", name = "AT&T", amount = 263.00, isPaid = false, dueDate = "Due 25th")
)

fun parseDueDateDayNumber(dueDate: String): Int {
    val clean = dueDate.trim()
    if (clean.contains("/")) {
        val parts = clean.split("/")
        if (parts.size >= 2) {
            val day = parts[1].filter { it.isDigit() }.toIntOrNull()
            if (day != null) return day
        }
    }
    val regex = Regex("\\d+")
    val match = regex.find(clean)
    return match?.value?.toIntOrNull() ?: 99
}

fun resolveBillCategoryEmoji(name: String): String {
    val lower = name.lowercase(Locale.US)
    return when {
        lower.contains("rent") || lower.contains("apartment") -> "🏠"
        lower.contains("disney") || lower.contains("netflix") || lower.contains("hulu") || lower.contains("youtube") || lower.contains("spotify") || lower.contains("tv") -> "📺"
        lower.contains("xfinity") || lower.contains("internet") || lower.contains("wifi") || lower.contains("at&t") || lower.contains("phone") -> "🌐"
        lower.contains("google") || lower.contains("cloud") || lower.contains("apple") || lower.contains("storage") -> "☁️"
        lower.contains("insurance") || lower.contains("safty") || lower.contains("safety") -> "🛡️"
        lower.contains("grid") || lower.contains("electric") || lower.contains("power") || lower.contains("energy") -> "⚡"
        else -> "📄"
    }
}

data class BudgetCycleInfo(
    val key: String, // e.g. "2026-10"
    val label: String, // e.g. "October 2026"
    val isActive: Boolean,
    val totalPaid: Int = 0,
    val totalBills: Int = 0,
    val remainingBalance: Double = 0.0,
    val surplusDeficitText: String = ""
)

@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    initialBills: List<BillItem> = emptyList(),
    onUpdateBills: (List<BillItem>) -> Unit = {}
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var billsList by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var showCyclePickerSheet by remember { mutableStateOf(false) }

    // Dynamic Cycle Key & Mode State
    var selectedCycleKey by remember { mutableStateOf("2026-10") }
    val currentActiveCycleKey = "2026-10"
    val isReadOnlyArchive = selectedCycleKey != currentActiveCycleKey

    val selectedCycleLabel = remember(selectedCycleKey) {
        when (selectedCycleKey) {
            "2026-10" -> "October 2026"
            "2026-09" -> "September 2026"
            "2026-08" -> "August 2026"
            "2026-07" -> "July 2026"
            else -> "October 2026"
        }
    }

    // Dynamic Firestore Query Path Adaptation
    LaunchedEffect(currentUser.householdId, selectedCycleKey) {
        isLoading = true
        currentUser.householdId?.let { hid ->
            val queryRef = if (selectedCycleKey == currentActiveCycleKey) {
                db.collection("households").document(hid).collection("bills")
            } else {
                db.collection("households").document(hid).collection("budgets").document(selectedCycleKey).collection("bills")
            }

            queryRef.addSnapshotListener { snapshot, _ ->
                isLoading = false
                if (snapshot != null && !snapshot.isEmpty) {
                    val items = snapshot.documents.mapNotNull { doc ->
                        BillItem(
                            id = doc.id,
                            name = doc.getString("name") ?: doc.getString("title") ?: "Bill",
                            amount = doc.getDouble("amount") ?: 0.0,
                            isPaid = doc.getBoolean("isPaid") ?: (selectedCycleKey != currentActiveCycleKey),
                            dueDate = doc.getString("dueDate") ?: "Due Soon",
                            dueInCurrentCycle = doc.getBoolean("dueInCurrentCycle") ?: true
                        )
                    }
                    billsList = items
                } else if (selectedCycleKey == currentActiveCycleKey) {
                    billsList = defaultHouseholdBills
                } else {
                    // Seeded archived monthly data
                    billsList = when (selectedCycleKey) {
                        "2026-09" -> listOf(
                            BillItem("b1", "Rent", 397.00, true, "Due 1st"),
                            BillItem("b2", "Disney Plus", 35.99, true, "Due 1st"),
                            BillItem("b3", "Xfinity", 80.94, true, "Due 4th")
                        )
                        "2026-08" -> listOf(
                            BillItem("b1", "Rent", 397.00, true, "Due 1st"),
                            BillItem("b8", "National Grid", 138.00, true, "Due 16th")
                        )
                        else -> listOf(
                            BillItem("b1", "Rent", 397.00, true, "Due 1st")
                        )
                    }
                }
            }
        } ?: run {
            isLoading = false
            billsList = defaultHouseholdBills
        }
    }

    val activeBills = remember(billsList) {
        val list = if (billsList.isNotEmpty()) billsList else defaultHouseholdBills
        list.sortedBy { parseDueDateDayNumber(it.dueDate) }
    }

    // Dynamic Header Math
    val totalBillsAmount = remember(activeBills) { activeBills.sumOf { it.amount } }
    val paidAmount = remember(activeBills) { activeBills.filter { it.isPaid }.sumOf { it.amount } }
    val unpaidAmount = remember(activeBills) { activeBills.filter { !it.isPaid }.sumOf { it.amount } }
    val totalAllowance = 1250.0
    val remainingAllowance = (totalAllowance - unpaidAmount).coerceAtLeast(0.0)
    val progress = if (totalAllowance > 0) (remainingAllowance / totalAllowance).coerceIn(0.0, 1.0).toFloat() else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(com.example.pantry.ui.theme.LocalAppColors.current.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 140.dp // Clearance for floating taskbar & FAB
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Bar & Header Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budget",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )

                    // Top-Right Inset Action Capsule Pill (Wires to open Cycle Picker Sheet)
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                            .clickable { showCyclePickerSheet = true }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = selectedCycleLabel,
                                color = Color(0xFF38BDF8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "▼",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Read-Only Archive Banner if viewing archived month
            if (isReadOnlyArchive) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF78350F).copy(alpha = 0.35f))
                            .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                            Text(
                                text = "READ-ONLY ARCHIVE • $selectedCycleLabel",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Dynamic Hero Allowance Card
            item {
                DynamicAllowanceHeroCard(
                    remainingAllowance = remainingAllowance,
                    totalAllowance = totalAllowance,
                    paidAmount = paidAmount,
                    unpaidAmount = unpaidAmount,
                    progress = progress
                )
            }

            // 3. Live Household Bills Ledger Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isReadOnlyArchive) "ARCHIVED BILLS" else "HOUSEHOLD BILLS",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${activeBills.count { it.isPaid }}/${activeBills.size} Paid",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Live Bills Cards List
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                }
            } else if (activeBills.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No household bills found. Tap '+' to add.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(activeBills, key = { it.id }) { bill ->
                    LiveBillCard(
                        bill = bill,
                        isReadOnly = isReadOnlyArchive,
                        onTogglePaid = {
                            if (isReadOnlyArchive) {
                                Toast.makeText(context, "Archived budget cycle ($selectedCycleLabel) is read-only.", Toast.LENGTH_SHORT).show()
                                return@LiveBillCard
                            }
                            val newPaidState = !bill.isPaid
                            billsList = billsList.map { if (it.id == bill.id) it.copy(isPaid = newPaidState) else it }
                            currentUser.householdId?.let { hid ->
                                db.collection("households").document(hid)
                                    .collection("bills").document(bill.id)
                                    .update("isPaid", newPaidState)
                            }
                        },
                        onDelete = {
                            if (isReadOnlyArchive) {
                                Toast.makeText(context, "Archived budget cycle ($selectedCycleLabel) is read-only.", Toast.LENGTH_SHORT).show()
                                return@LiveBillCard
                            }
                            billsList = billsList.filter { it.id != bill.id }
                            currentUser.householdId?.let { hid ->
                                db.collection("households").document(hid)
                                    .collection("bills").document(bill.id)
                                    .delete()
                            }
                        }
                    )
                }
            }
        }

        // Exactly ONE Circular Blue + FAB Anchored at bottom = 96.dp, end = 24.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isReadOnlyArchive) listOf(Color(0xFF64748B), Color(0xFF475569)) else listOf(com.example.pantry.ui.theme.LocalAppColors.current.fabColor, com.example.pantry.ui.theme.LocalAppColors.current.primaryAccent)
                    )
                )
                .clickable {
                    if (isReadOnlyArchive) {
                        Toast.makeText(context, "Archived budget cycle ($selectedCycleLabel) is read-only.", Toast.LENGTH_SHORT).show()
                    } else {
                        showAddExpenseDialog = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Bill",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Cycle Picker Bottom Sheet Dialog (budget_cycle_picker.svg)
    if (showCyclePickerSheet) {
        BudgetCyclePickerSheetDialog(
            selectedCycleKey = selectedCycleKey,
            themePreference = currentUser.themePreference,
            onDismiss = { showCyclePickerSheet = false },
            onSelectCycle = { newCycleKey ->
                selectedCycleKey = newCycleKey
                showCyclePickerSheet = false
            }
        )
    }

    // Add Bill Sheet Dialog
    if (showAddExpenseDialog) {
        AddBillSheetDialog(
            themePreference = currentUser.themePreference,
            onDismiss = { showAddExpenseDialog = false },
            onAdd = { newBill ->
                billsList = listOf(newBill) + billsList
                currentUser.householdId?.let { hid ->
                    val data = hashMapOf(
                        "id" to newBill.id,
                        "name" to newBill.name,
                        "amount" to newBill.amount,
                        "isPaid" to newBill.isPaid,
                        "dueDate" to newBill.dueDate,
                        "dueInCurrentCycle" to newBill.dueInCurrentCycle
                    )
                    db.collection("households").document(hid)
                        .collection("bills").document(newBill.id)
                        .set(data, SetOptions.merge())
                }
                showAddExpenseDialog = false
            }
        )
    }
}

@Composable
fun DynamicAllowanceHeroCard(
    remainingAllowance: Double,
    totalAllowance: Double,
    paidAmount: Double,
    unpaidAmount: Double,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(24.dp)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Label
            Text(
                text = "Remaining Monthly Allowance",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Dynamic Balance Display
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "$%.2f", remainingAllowance),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format(Locale.US, "/ $%.2f", totalAllowance),
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Full-Width Smooth Dynamic Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981))
                )
            }

            // Bottom Stat Splits
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paid so far: $${String.format(Locale.US, "%.2f", paidAmount)}",
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Unpaid Bills: $${String.format(Locale.US, "%.2f", unpaidAmount)}",
                    color = Color(0xFFF87171),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun LiveBillCard(
    bill: BillItem,
    isReadOnly: Boolean = false,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit
) {
    val emoji = resolveBillCategoryEmoji(bill.name)
    val isPaid = bill.isPaid
    val appTheme = com.example.pantry.ui.theme.LocalAppColors.current

    val cardBorder = if (isPaid) Color(0xFF10B981).copy(alpha = 0.35f) else appTheme.cardBorder
    val iconBg = if (isPaid) Color(0xFF10B981).copy(alpha = 0.20f) else Color(0xFF7F1D1D).copy(alpha = 0.35f)
    val iconBorder = if (isPaid) Color(0xFF10B981).copy(alpha = 0.40f) else appTheme.cardBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(appTheme.cardSurface)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .clickable { onTogglePaid() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading 44.dp Icon Container
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg)
                        .border(1.dp, iconBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 20.sp, textAlign = TextAlign.Center)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bill.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${bill.dueDate} • ",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPaid) Color(0xFF10B981).copy(alpha = 0.20f) else Color(0xFF7F1D1D).copy(alpha = 0.40f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isPaid) "PAID" else "UNPAID",
                                color = if (isPaid) Color(0xFF10B981) else Color(0xFFF87171),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "-$%.2f", bill.amount),
                    color = if (isPaid) Color(0xFF10B981) else Color(0xFFF87171),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!isReadOnly) {
                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Bill",
                            tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetCyclePickerSheetDialog(
    selectedCycleKey: String,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSelectCycle: (String) -> Unit
) {
    val context = LocalContext.current

    val cycles = listOf(
        BudgetCycleInfo("2026-10", "October 2026", isActive = true),
        BudgetCycleInfo("2026-09", "September 2026", isActive = false, totalPaid = 3, totalBills = 3, remainingBalance = 142.10, surplusDeficitText = "+$142.10 Surplus saved"),
        BudgetCycleInfo("2026-08", "August 2026", isActive = false, totalPaid = 3, totalBills = 3, remainingBalance = 88.40, surplusDeficitText = "+$88.40 Surplus saved"),
        BudgetCycleInfo("2026-07", "July 2026", isActive = false, totalPaid = 3, totalBills = 3, remainingBalance = -18.25, surplusDeficitText = "-$18.25 Deficit")
    )

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        themePreference = themePreference
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Drag Handle Grabber
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFF475569))
                )
            }

            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Budget Cycle",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Switch between the live ledger and past monthly archives",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            // Year & Quick Jump Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("2026 ▼", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2563EB).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
                        .clickable { onSelectCycle("2026-10") }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("● Current Month", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Active Cycle Card (2026-10)
            val activeCycle = cycles.first()
            val isCurrentSelected = selectedCycleKey == activeCycle.key
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0369A1).copy(alpha = 0.35f), Color(0xFF0284C7).copy(alpha = 0.15f))
                        )
                    )
                    .border(1.5.dp, if (isCurrentSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(20.dp))
                    .clickable { onSelectCycle(activeCycle.key) }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Radio Button Indicator
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isCurrentSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                .border(2.dp, if (isCurrentSelected) Color(0xFF38BDF8) else Color(0xFF64748B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrentSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = activeCycle.label,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ACTIVE CYCLE • Live Ledger",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("$160.58", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Remaining", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            }

            // Section Label
            Text(
                text = "ARCHIVED BUDGETS (READ-ONLY)",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            // Archived Cycles List
            cycles.drop(1).forEach { cycle ->
                val isSelected = selectedCycleKey == cycle.key
                val surplusColor = if (cycle.remainingBalance >= 0) Color(0xFF34D399) else Color(0xFFF87171)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onSelectCycle(cycle.key) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Unselected Radio Button
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                    .border(1.5.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = cycle.label,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Archived • All bills paid (${cycle.totalPaid}/${cycle.totalBills})",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (cycle.remainingBalance >= 0) String.format(Locale.US, "+$%.2f", cycle.remainingBalance) else String.format(Locale.US, "-$%.2f", abs(cycle.remainingBalance)),
                                color = surplusColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (cycle.remainingBalance >= 0) "Surplus saved" else "Deficit",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Start New Billing Cycle Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .clickable {
                        Toast.makeText(context, "Current cycle October 2026 is active.", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Start New Billing Cycle Early",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddBillSheetDialog(
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onAdd: (BillItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDateText by remember { mutableStateOf("Due 1st") }
    var isPaid by remember { mutableStateOf(false) }

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        themePreference = themePreference
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Add Household Bill",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Adds recurring bill to live household ledger",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF334155), CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("BILL NAME", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                placeholder = { Text("e.g. Electric Bill", color = Color(0xFF64748B), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = inputColors
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("AMOUNT ($)", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )

                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("DUE DATE", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            // Already Paid Toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Mark as Already Paid", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Deducts immediately from allowance", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }
            }

            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || parsedAmount <= 0.0) return@Button

                    val newBill = BillItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        amount = parsedAmount,
                        isPaid = isPaid,
                        dueDate = dueDateText.trim(),
                        dueInCurrentCycle = true
                    )
                    onAdd(newBill)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("SAVE BILL", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }

            Text(
                text = "Discard",
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(vertical = 4.dp)
            )
        }
    }
}
