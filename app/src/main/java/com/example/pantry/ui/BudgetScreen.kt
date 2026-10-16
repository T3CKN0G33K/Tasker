package com.example.pantry.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liquidglass.ChromaticDispersionPresets
import com.example.liquidglass.LiquidGlassListItem
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

enum class BudgetSubTab(val label: String) {
    BILLS("Bi-Weekly Bills"),
    LEDGER("Bank Ledger")
}

enum class TransactionType { INCOME, EXPENSE, BILL }

data class LedgerEntry(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.EXPENSE,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String = "General"
) {
    val signedAmount: Double
        get() = when (type) {
            TransactionType.INCOME -> amount
            TransactionType.EXPENSE, TransactionType.BILL -> -abs(amount)
        }
}

data class BillItem(
    val id: String = "",
    val name: String = "",
    val amount: Double = 0.0,
    val dueDate: String = "Due 1st",
    val paidPeriodIds: List<String> = emptyList()
) {
    fun isPaidInCycle(cycleId: String): Boolean = paidPeriodIds.contains(cycleId)
}

val defaultBillsList = listOf(
    BillItem("b1", "Rent", 397.00, "Due 1st"),
    BillItem("b2", "Disney Plus", 35.99, "Due 1st"),
    BillItem("b3", "Xfinity", 80.94, "Due 4th"),
    BillItem("b4", "Google ONE", 21.24, "Due 8th"),
    BillItem("b5", "Safty Insurance", 124.71, "Due 10th"),
    BillItem("b6", "YouTube", 13.99, "Due 10th"),
    BillItem("b7", "Spotify", 12.99, "Due 16th"),
    BillItem("b8", "National Grid", 138.00, "Due 16th"),
    BillItem("b9", "AT&T", 263.00, "Due 25th"),
    BillItem("b10", "Rent a Center", 336.74, "Due 17th"),
    BillItem("b11", "Rent a Center", 380.06, "Due 19th")
)

val defaultLedgerEntries = listOf(
    LedgerEntry("l1", "Bi-Weekly Paycheck Deposit", 1850.00, TransactionType.INCOME, System.currentTimeMillis() - 864000000L, "Income"),
    LedgerEntry("l2", "Pantry Restock Groceries", 40.95, TransactionType.EXPENSE, System.currentTimeMillis() - 432000000L, "Groceries"),
    LedgerEntry("l3", "Google ONE Subscription", 21.24, TransactionType.BILL, System.currentTimeMillis() - 216000000L, "Bills")
)

private fun parseDueDateNumber(dueDate: String): Int {
    if (dueDate.contains("/")) {
        val parts = dueDate.split("/")
        if (parts.size >= 2) {
            val day = parts[1].toIntOrNull() ?: 1
            return day + 31
        }
    }
    val digits = dueDate.filter { it.isDigit() }
    return digits.toIntOrNull() ?: 99
}

fun resolveBillEmoji(name: String, category: String = ""): String {
    val lower = name.lowercase(Locale.US)
    return when {
        lower.contains("rent") || lower.contains("apartment") -> "🏠"
        lower.contains("disney") || lower.contains("netflix") || lower.contains("hulu") || lower.contains("youtube") || lower.contains("tv") || lower.contains("spotify") -> "📺"
        lower.contains("xfinity") || lower.contains("internet") || lower.contains("wifi") || lower.contains("at&t") || lower.contains("phone") -> "🌐"
        lower.contains("google") || lower.contains("cloud") || lower.contains("apple") || lower.contains("storage") -> "☁️"
        lower.contains("insurance") || lower.contains("safty") || lower.contains("safety") -> "🛡️"
        lower.contains("electric") || lower.contains("power") || lower.contains("energy") || lower.contains("grid") -> "⚡"
        lower.contains("water") || lower.contains("trash") -> "💧"
        lower.contains("car") || lower.contains("auto") -> "🚗"
        else -> "📄"
    }
}

@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    backdrop: Backdrop? = null,
    isDarkMode: Boolean = true,
    ledgerEntries: List<LedgerEntry> = emptyList(),
    bills: List<BillItem> = emptyList(),
    onUpdateLedger: (List<LedgerEntry>) -> Unit = {},
    onUpdateBills: (List<BillItem>) -> Unit = {}
) {
    BudgetScreenContent(
        modifier = modifier,
        currentUser = currentUser,
        backdrop = backdrop,
        isDarkMode = isDarkMode,
        ledgerEntries = ledgerEntries,
        bills = bills,
        onUpdateLedger = onUpdateLedger,
        onUpdateBills = onUpdateBills
    )
}

@Composable
fun BudgetScreenContent(
    modifier: Modifier = Modifier,
    currentUser: UserProfile,
    backdrop: Backdrop? = null,
    isDarkMode: Boolean = true,
    ledgerEntries: List<LedgerEntry>,
    bills: List<BillItem>,
    onUpdateLedger: (List<LedgerEntry>) -> Unit,
    onUpdateBills: (List<BillItem>) -> Unit
) {
    val currentBackdrop = backdrop ?: rememberLayerBackdrop()
    val context = LocalContext.current
    val db = Firebase.firestore

    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedSubTab by remember { mutableStateOf(BudgetSubTab.BILLS) }
    var showAddBillDialog by remember { mutableStateOf(false) }
    var showAddDepositDialog by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var editingBill by remember { mutableStateOf<BillItem?>(null) }

    val activeAccentColor = if (currentUser.themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    // Document Picker for Ephemeral Statement Ingestion
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            importStatementInMemory(
                context = context,
                uri = selectedUri,
                currentUser = currentUser,
                db = db,
                existingLedger = ledgerEntries,
                onUpdateLedger = onUpdateLedger
            )
        }
    }

    val currentPayCycle = remember { PayCycleUtils.getCurrentPayCycle() }

    val currentBankBalance = remember(ledgerEntries) {
        ledgerEntries.sumOf { it.signedAmount }
    }

    val totalIncome = remember(ledgerEntries) {
        ledgerEntries.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    val totalExpenses = remember(ledgerEntries) {
        ledgerEntries.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.BILL }.sumOf { abs(it.amount) }
    }

    val totalPaidInCycle = remember(bills, currentPayCycle.cycleId) {
        bills.filter { it.isPaidInCycle(currentPayCycle.cycleId) }.sumOf { it.amount }
    }

    val unpaidBillsInCycle = remember(bills, currentPayCycle.cycleId) {
        bills.filter { !it.isPaidInCycle(currentPayCycle.cycleId) }
    }

    val totalUnpaidBillsInCycle = remember(unpaidBillsInCycle) {
        unpaidBillsInCycle.sumOf { it.amount }
    }

    val safeToSpend = currentBankBalance - totalUnpaidBillsInCycle

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

    val sortedBills = remember(bills) {
        bills.sortedBy { parseDueDateNumber(it.dueDate) }
    }

    val sortedLedger = remember(ledgerEntries) {
        ledgerEntries.sortedByDescending { it.timestamp }
    }

    val categoryList = listOf("All", "Bills", "Savings", "Auto", "Daily", "Groceries")

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Scrollable Budget Content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 140.dp // Clearance for floating action buttons & taskbar
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budget",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White
                    )
                }
            }

            // Executive Summary Glass Card (Balance & Cash Flow)
            item {
                BudgetSummaryGlassCard(
                    backdrop = currentBackdrop,
                    currentBankBalance = currentBankBalance,
                    safeToSpend = safeToSpend,
                    totalIncome = totalIncome,
                    totalExpenses = totalExpenses,
                    activeAccentColor = activeAccentColor,
                    textSub = textSub
                )
            }

            // SubTab & Quick Category Breakdown / Filter Strip
            item {
                BudgetCategoryStrip(
                    selectedSubTab = selectedSubTab,
                    onSelectSubTab = { selectedSubTab = it },
                    selectedCategory = selectedCategoryFilter,
                    onSelectCategory = { selectedCategoryFilter = it },
                    categories = categoryList,
                    activeAccentColor = activeAccentColor
                )
            }

            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedSubTab == BudgetSubTab.BILLS) "Recurring Household Bills" else "Transaction History",
                        color = textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (selectedSubTab == BudgetSubTab.BILLS)
                            "${sortedBills.count { it.isPaidInCycle(currentPayCycle.cycleId) }}/${sortedBills.size} Paid"
                        else
                            "${sortedLedger.size} Logged",
                        color = activeAccentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Transaction & Expense Cards
            if (selectedSubTab == BudgetSubTab.BILLS) {
                val filteredBills = if (selectedCategoryFilter == "All") sortedBills else sortedBills.filter {
                    it.name.contains(selectedCategoryFilter, ignoreCase = true)
                }
                items(filteredBills, key = { "bill_" + it.id }) { bill ->
                    val isPaidInCurrentCycle = bill.isPaidInCycle(currentPayCycle.cycleId)
                    BillGlassCard(
                        bill = bill,
                        currentPayCycle = currentPayCycle,
                        backdrop = currentBackdrop,
                        isDarkMode = isDarkMode,
                        textMain = textMain,
                        onEdit = {
                            editingBill = bill
                            showAddBillDialog = true
                        },
                        onTogglePaid = { isChecked ->
                            val updatedPaidCycleIds = if (isChecked) {
                                (bill.paidPeriodIds + currentPayCycle.cycleId).distinct()
                            } else {
                                bill.paidPeriodIds.filter { it != currentPayCycle.cycleId }
                            }

                            val updatedBill = bill.copy(paidPeriodIds = updatedPaidCycleIds)
                            val newBillsList = bills.filter { it.id != bill.id } + updatedBill
                            onUpdateBills(newBillsList)

                            val hid = currentUser.householdId
                            if (!hid.isNullOrBlank()) {
                                db.collection("households").document(hid)
                                    .collection("bills").document(bill.id)
                                    .set(
                                        hashMapOf(
                                            "id" to bill.id,
                                            "name" to bill.name,
                                            "amount" to bill.amount,
                                            "dueDate" to bill.dueDate,
                                            "paidPeriodIds" to updatedPaidCycleIds
                                        ),
                                        SetOptions.merge()
                                    )
                            }
                        }
                    )
                }
            } else {
                val filteredLedger = if (selectedCategoryFilter == "All") sortedLedger else sortedLedger.filter {
                    it.category.equals(selectedCategoryFilter, ignoreCase = true) || it.title.contains(selectedCategoryFilter, ignoreCase = true)
                }
                items(filteredLedger, key = { "tx_" + it.id }) { entry ->
                    TransactionGlassCard(
                        entry = entry,
                        backdrop = currentBackdrop,
                        isDarkMode = isDarkMode,
                        textMain = textMain,
                        onDelete = {
                            val newLedger = ledgerEntries.filter { it.id != entry.id }
                            onUpdateLedger(newLedger)

                            val hid = currentUser.householdId
                            if (!hid.isNullOrBlank()) {
                                db.collection("households").document(hid)
                                    .collection("transactions").document(entry.id)
                                    .delete()
                            }
                        }
                    )
                }
            }
        }

        // Floating Action Buttons (Filter/Analytics & Add Transaction)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Analytics / Filter Glass Button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .drawBackdrop(
                        backdrop = currentBackdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(10.dp.toPx(), 14.dp.toPx())
                        },
                        highlight = { Highlight(width = 1.dp, alpha = 0.45f) },
                        shadow = { Shadow(radius = 12.dp, alpha = 0.25f) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }
                    )
                    .clickable { showAddDepositDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Analytics",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Add Transaction FAB (Accent Blue Glass)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .drawBackdrop(
                        backdrop = currentBackdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(12.dp.toPx(), 16.dp.toPx())
                        },
                        highlight = { Highlight(width = 1.2.dp, alpha = 0.6f) },
                        shadow = { Shadow(radius = 16.dp, alpha = 0.35f) },
                        onDrawSurface = { drawRect(Color(0xFF007AFF).copy(alpha = 0.85f)) }
                    )
                    .clickable {
                        if (selectedSubTab == BudgetSubTab.BILLS) showAddBillDialog = true
                        else showAddExpenseDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Transaction",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    // Dialogs
    if (showAddBillDialog) {
        AddBillDialog(
            currentUser = currentUser,
            editingBill = editingBill,
            backdrop = currentBackdrop,
            themePreference = currentUser.themePreference,
            onDismiss = {
                showAddBillDialog = false
                editingBill = null
            },
            onSaveSuccess = { savedBill ->
                val newBills = bills.filter { it.id != savedBill.id } + savedBill
                onUpdateBills(newBills)
                showAddBillDialog = false
                editingBill = null
            },
            onDeleteSuccess = { deletedId ->
                val newBills = bills.filter { it.id != deletedId }
                onUpdateBills(newBills)
                showAddBillDialog = false
                editingBill = null
            }
        )
    }

    if (showAddDepositDialog) {
        AddDepositDialog(
            currentUser = currentUser,
            backdrop = currentBackdrop,
            themePreference = currentUser.themePreference,
            onDismiss = { showAddDepositDialog = false },
            onAddSuccess = { newEntry ->
                val newLedger = ledgerEntries + newEntry
                onUpdateLedger(newLedger)
                showAddDepositDialog = false
            }
        )
    }

    if (showAddExpenseDialog) {
        AddExpenseDialog(
            currentUser = currentUser,
            backdrop = currentBackdrop,
            themePreference = currentUser.themePreference,
            onDismiss = { showAddExpenseDialog = false },
            onAddSuccess = { newEntry ->
                val newLedger = ledgerEntries + newEntry
                onUpdateLedger(newLedger)
                showAddExpenseDialog = false
            }
        )
    }
}

@Composable
fun BudgetSummaryGlassCard(
    backdrop: Backdrop,
    currentBankBalance: Double,
    safeToSpend: Double,
    totalIncome: Double,
    totalExpenses: Double,
    activeAccentColor: Color,
    textSub: Color
) {
    val cardShape = remember { RoundedCornerShape(24.dp) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cardShape },
                effects = {
                    colorControls(
                        brightness = 0f,
                        saturation = 1.3f
                    )
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 36.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight(width = 1.dp, alpha = 0.35f) },
                shadow = { Shadow(radius = 16.dp, alpha = 0.25f, offset = DpOffset(0.dp, 6.dp)) },
                onDrawSurface = {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.12f),
                                Color.White.copy(alpha = 0.04f)
                            )
                        )
                    )
                }
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Top row: Inset balance pill (size = 44.dp) + "Total Balance" label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(activeAccentColor.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = activeAccentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text("Total Bank Balance", color = textSub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = String.format(Locale.US, "$%.2f", currentBankBalance),
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Safe to spend pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(activeAccentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Safe $${String.format(Locale.US, "%.0f", safeToSpend)}",
                        color = activeAccentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.5.dp)

            // Bottom row: Two mini inset pill widgets for monthly Income & Expenses
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Income Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF34C759).copy(alpha = 0.15f))
                        .border(0.5.dp, Color(0xFF34C759).copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                        Column {
                            Text("Income", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text("+$${String.format(Locale.US, "%.2f", totalIncome)}", color = Color(0xFF34C759), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Expenses Pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFF3B30).copy(alpha = 0.15f))
                        .border(0.5.dp, Color(0xFFFF3B30).copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(16.dp))
                        Column {
                            Text("Expenses", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            Text("-$${String.format(Locale.US, "%.2f", totalExpenses)}", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetCategoryStrip(
    selectedSubTab: BudgetSubTab,
    onSelectSubTab: (BudgetSubTab) -> Unit,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    categories: List<String>,
    activeAccentColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Sub-Tab Switcher
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BudgetSubTab.entries.forEach { tab ->
                    val isSelected = selectedSubTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) activeAccentColor else Color.Transparent)
                            .clickable { onSelectSubTab(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Category Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) activeAccentColor else Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 0.5.dp,
                            color = if (isSelected) activeAccentColor else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onSelectCategory(cat) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun BillGlassCard(
    bill: BillItem,
    currentPayCycle: PayCycleInfo,
    backdrop: Backdrop,
    isDarkMode: Boolean = true,
    textMain: Color,
    onEdit: () -> Unit,
    onTogglePaid: (Boolean) -> Unit
) {
    val cardShape = remember { RoundedCornerShape(28.dp) }
    val isPaid = bill.isPaidInCycle(currentPayCycle.cycleId)
    val containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cardShape },
                effects = {
                    colorControls(
                        brightness = if (isDarkMode) 0f else 0.15f,
                        saturation = 1.3f
                    )
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 36.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight(width = 1.2.dp, alpha = 0.45f) },
                shadow = { Shadow(radius = 16.dp, alpha = 0.25f, offset = DpOffset(0.dp, 6.dp)) },
                innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.20f) },
                onDrawSurface = { drawRect(containerColor) }
            )
            .clickable(onClick = onEdit)
            .padding(14.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.60f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f
                )
            )
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
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx())
                                    lens(
                                        refractionHeight = 8.dp.toPx(),
                                        refractionAmount = 12.dp.toPx(),
                                        chromaticAberration = true
                                    )
                                },
                                highlight = { Highlight(width = 1.dp, alpha = 0.5f) },
                                shadow = { Shadow(radius = 8.dp, alpha = 0.2f) },
                                innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.25f) },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.16f)) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resolveBillEmoji(bill.name),
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bill.name,
                            color = textMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${bill.dueDate} • ${if (isPaid) "Paid for Cycle" else "Unpaid"}",
                            color = if (isPaid) Color(0xFF34C759) else Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(percent = 50) },
                                effects = {
                                    lens(refractionHeight = 8.dp.toPx(), refractionAmount = 12.dp.toPx())
                                },
                                highlight = { Highlight(width = 1.dp, alpha = 0.4f) },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) }
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "$%.2f", bill.amount),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (isPaid) {
                                    Modifier.background(Color(0xFF34C759))
                                } else {
                                    Modifier
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(
                                            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                }
                            )
                            .clickable { onTogglePaid(!isPaid) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPaid) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Paid",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionGlassCard(
    entry: LedgerEntry,
    backdrop: Backdrop,
    isDarkMode: Boolean = true,
    textMain: Color,
    onDelete: () -> Unit
) {
    val cardShape = remember { RoundedCornerShape(28.dp) }
    val isIncome = entry.type == TransactionType.INCOME
    val amountColor = if (isIncome) Color(0xFF34C759) else Color.White

    val emoji = when {
        entry.category.contains("auto", ignoreCase = true) || entry.title.contains("gas", ignoreCase = true) || entry.title.contains("car", ignoreCase = true) -> "🚗"
        entry.category.contains("groceries", ignoreCase = true) || entry.title.contains("food", ignoreCase = true) -> "🛒"
        entry.category.contains("bills", ignoreCase = true) || entry.category.contains("rent", ignoreCase = true) || entry.title.contains("rent", ignoreCase = true) -> "🏠"
        entry.category.contains("income", ignoreCase = true) || entry.type == TransactionType.INCOME -> "💵"
        entry.category.contains("tech", ignoreCase = true) || entry.title.contains("google", ignoreCase = true) || entry.title.contains("disney", ignoreCase = true) -> "📱"
        else -> "💳"
    }

    val formattedDate = remember(entry.timestamp) {
        val sdf = SimpleDateFormat("MMM dd • hh:mm a", Locale.US)
        sdf.format(Date(entry.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cardShape },
                effects = {
                    colorControls(
                        brightness = if (isDarkMode) 0f else 0.15f,
                        saturation = 1.3f
                    )
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 36.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                highlight = { Highlight(width = 1.2.dp, alpha = 0.45f) },
                shadow = { Shadow(radius = 16.dp, alpha = 0.25f, offset = DpOffset(0.dp, 6.dp)) },
                innerShadow = { InnerShadow(radius = 8.dp, alpha = 0.20f) },
                onDrawSurface = {
                    val containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f)
                    drawRect(containerColor)
                }
            )
            .padding(14.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.60f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f
                )
            )
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
                    // Inset Circular Emoji Badge (Left)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx())
                                    lens(
                                        refractionHeight = 8.dp.toPx(),
                                        refractionAmount = 12.dp.toPx(),
                                        chromaticAberration = true
                                    )
                                },
                                highlight = { Highlight(width = 1.dp, alpha = 0.5f) },
                                shadow = { Shadow(radius = 8.dp, alpha = 0.2f) },
                                innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.25f) },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.16f)) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            color = textMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${entry.category} • $formattedDate",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 13.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Amount Pill (Right)
                    Box(
                        modifier = Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(percent = 50) },
                                effects = {
                                    lens(refractionHeight = 8.dp.toPx(), refractionAmount = 12.dp.toPx())
                                },
                                highlight = { Highlight(width = 1.dp, alpha = 0.4f) },
                                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) }
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%s$%.2f", if (isIncome) "+" else "-", abs(entry.amount)),
                            color = amountColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Transaction",
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// Dialogs stay below
@Composable
fun AddBillDialog(
    currentUser: UserProfile,
    editingBill: BillItem? = null,
    backdrop: Backdrop? = null,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSaveSuccess: (BillItem) -> Unit,
    onDeleteSuccess: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var name by remember { mutableStateOf(editingBill?.name ?: "") }
    var amountText by remember { mutableStateOf(editingBill?.amount?.toString() ?: "") }
    var dueDate by remember { mutableStateOf(editingBill?.dueDate ?: "Due 1st") }
    var isSaving by remember { mutableStateOf(false) }

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        themePreference = themePreference
    ) { _ ->
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
                Text(
                    text = if (editingBill != null) "Edit Household Bill" else "Add Household Bill",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(18.dp))
                }
            }

            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                focusedBorderColor = activeAccentColor,
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Bill Description / Payee", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount ($)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                label = { Text("Due Date (e.g. Due 1st or 10/16)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingBill != null) {
                    TextButton(
                        onClick = {
                            val hid = currentUser.householdId
                            if (!hid.isNullOrBlank()) {
                                db.collection("households").document(hid)
                                    .collection("bills").document(editingBill.id)
                                    .delete()
                            }
                            onDeleteSuccess(editingBill.id)
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GlassHeroButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (name.isBlank() || amount == null || amount <= 0.0 || isSaving) return@GlassHeroButton
                        isSaving = true

                        val docId = editingBill?.id ?: UUID.randomUUID().toString()
                        val newBill = BillItem(
                            id = docId,
                            name = name.trim(),
                            amount = amount,
                            dueDate = dueDate.trim().ifBlank { "Due 1st" },
                            paidPeriodIds = editingBill?.paidPeriodIds ?: emptyList()
                        )

                        val hid = currentUser.householdId
                        if (!hid.isNullOrBlank()) {
                            db.collection("households").document(hid)
                                .collection("bills").document(docId)
                                .set(
                                    hashMapOf(
                                        "id" to docId,
                                        "name" to newBill.name,
                                        "amount" to newBill.amount,
                                        "dueDate" to newBill.dueDate,
                                        "paidPeriodIds" to newBill.paidPeriodIds
                                    ),
                                    SetOptions.merge()
                                )
                                .addOnSuccessListener {
                                    isSaving = false
                                    onSaveSuccess(newBill)
                                }
                        } else {
                            isSaving = false
                            onSaveSuccess(newBill)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save Bill", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddDepositDialog(
    currentUser: UserProfile,
    backdrop: Backdrop? = null,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onAddSuccess: (LedgerEntry) -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var title by remember { mutableStateOf("Bi-Weekly Paycheck Deposit") }
    var amountText by remember { mutableStateOf("1850.00") }
    var category by remember { mutableStateOf("Paycheck") }
    var isSaving by remember { mutableStateOf(false) }

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        themePreference = themePreference
    ) { _ ->
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
                Text(
                    text = "Add Deposit / Paycheck",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(18.dp))
                }
            }

            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                focusedBorderColor = Color(0xFF34C759),
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Deposit Description", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount ($)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (e.g. Paycheck, Bonus)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            Spacer(modifier = Modifier.height(4.dp))

            GlassHeroButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (title.isBlank() || amount == null || amount <= 0.0 || isSaving) return@GlassHeroButton
                    isSaving = true

                    val docId = UUID.randomUUID().toString()
                    val newEntry = LedgerEntry(
                        id = docId,
                        title = title.trim(),
                        amount = abs(amount),
                        type = TransactionType.INCOME,
                        timestamp = System.currentTimeMillis(),
                        category = category.trim().ifBlank { "Income" }
                    )

                    val hid = currentUser.householdId
                    if (!hid.isNullOrBlank()) {
                        db.collection("households").document(hid)
                            .collection("transactions").document(docId)
                            .set(
                                hashMapOf(
                                    "id" to docId,
                                    "title" to newEntry.title,
                                    "amount" to newEntry.amount,
                                    "type" to newEntry.type.name,
                                    "timestamp" to newEntry.timestamp,
                                    "category" to newEntry.category
                                )
                            )
                            .addOnSuccessListener {
                                isSaving = false
                                Toast.makeText(context, "Deposit added to bank balance!", Toast.LENGTH_SHORT).show()
                                onAddSuccess(newEntry)
                            }
                    } else {
                        isSaving = false
                        onAddSuccess(newEntry)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Log Deposit & Add to Balance", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    currentUser: UserProfile,
    backdrop: Backdrop? = null,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onAddSuccess: (LedgerEntry) -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Expense") }
    var isSaving by remember { mutableStateOf(false) }

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
        themePreference = themePreference
    ) { _ ->
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
                Text(
                    text = "Add Expense",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.70f), modifier = Modifier.size(18.dp))
                }
            }

            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                focusedBorderColor = Color(0xFFFF3B30),
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Expense Description", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount ($)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category (e.g. Groceries, Gas)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            Spacer(modifier = Modifier.height(4.dp))

            GlassHeroButton(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (title.isBlank() || amount == null || amount <= 0.0 || isSaving) return@GlassHeroButton
                    isSaving = true

                    val docId = UUID.randomUUID().toString()
                    val newEntry = LedgerEntry(
                        id = docId,
                        title = title.trim(),
                        amount = abs(amount),
                        type = TransactionType.EXPENSE,
                        timestamp = System.currentTimeMillis(),
                        category = category.trim().ifBlank { "Expense" }
                    )

                    val hid = currentUser.householdId
                    if (!hid.isNullOrBlank()) {
                        db.collection("households").document(hid)
                            .collection("transactions").document(docId)
                            .set(
                                hashMapOf(
                                    "id" to docId,
                                    "title" to newEntry.title,
                                    "amount" to newEntry.amount,
                                    "type" to newEntry.type.name,
                                    "timestamp" to newEntry.timestamp,
                                    "category" to newEntry.category
                                )
                            )
                            .addOnSuccessListener {
                                isSaving = false
                                Toast.makeText(context, "Expense logged!", Toast.LENGTH_SHORT).show()
                                onAddSuccess(newEntry)
                            }
                    } else {
                        isSaving = false
                        onAddSuccess(newEntry)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Log Expense & Deduct Balance", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun importStatementInMemory(
    context: Context,
    uri: Uri,
    currentUser: UserProfile,
    db: FirebaseFirestore,
    existingLedger: List<LedgerEntry>,
    onUpdateLedger: (List<LedgerEntry>) -> Unit
) {
    try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""

        val rawText = when {
            mimeType.contains("pdf") || uri.path?.endsWith(".pdf", ignoreCase = true) == true -> {
                PDFBoxResourceLoader.init(context.applicationContext)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    PDDocument.load(inputStream).use { pdDocument ->
                        PDFTextStripper().getText(pdDocument)
                    }
                } ?: ""
            }
            else -> {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: ""
            }
        }

        if (rawText.isBlank()) {
            Toast.makeText(context, "No text extracted from statement.", Toast.LENGTH_SHORT).show()
            return
        }

        val parsedEntries = mutableListOf<LedgerEntry>()
        val lines = rawText.lines()
        val regex = Regex("""(?i)(income|deposit|credit|expense|debit|payment|bill)?[\s,]*([A-Za-z0-9\s\-_&']+?)[\s,]+(\$?\d+(?:\.\d{2})?)""")

        for (line in lines) {
            val match = regex.find(line.trim())
            if (match != null) {
                val keyword = match.groupValues[1].uppercase()
                val titleStr = match.groupValues[2].trim()
                val amountVal = match.groupValues[3].replace("$", "").toDoubleOrNull() ?: continue

                if (titleStr.isNotBlank() && amountVal > 0.0) {
                    val txType = when {
                        keyword.contains("INCOME") || keyword.contains("DEPOSIT") || keyword.contains("CREDIT") -> TransactionType.INCOME
                        keyword.contains("BILL") -> TransactionType.BILL
                        else -> TransactionType.EXPENSE
                    }

                    parsedEntries.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            title = titleStr,
                            amount = amountVal,
                            type = txType,
                            timestamp = System.currentTimeMillis(),
                            category = if (txType == TransactionType.INCOME) "Income" else "Imported"
                        )
                    )
                }
            }
        }

        if (parsedEntries.isNotEmpty()) {
            val newLedger = existingLedger + parsedEntries
            onUpdateLedger(newLedger)

            val hid = currentUser.householdId
            if (!hid.isNullOrBlank()) {
                val batch = db.batch()
                parsedEntries.forEach { entry ->
                    val docRef = db.collection("households").document(hid).collection("transactions").document(entry.id)
                    batch.set(
                        docRef,
                        hashMapOf(
                            "id" to entry.id,
                            "title" to entry.title,
                            "amount" to entry.amount,
                            "type" to entry.type.name,
                            "timestamp" to entry.timestamp,
                            "category" to entry.category
                        )
                    )
                }
                batch.commit()
            }

            Toast.makeText(context, "Imported ${parsedEntries.size} entries into bank ledger!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "No valid statement transaction lines detected.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to parse statement: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
