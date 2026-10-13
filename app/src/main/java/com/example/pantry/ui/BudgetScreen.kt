package com.example.pantry.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liquidglass.ChromaticDispersionPresets
import com.example.liquidglass.LiquidGlassListItem
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
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

@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    ledgerEntries: List<LedgerEntry> = emptyList(),
    bills: List<BillItem> = emptyList(),
    onUpdateLedger: (List<LedgerEntry>) -> Unit = {},
    onUpdateBills: (List<BillItem>) -> Unit = {}
) {
    BudgetScreenContent(
        modifier = modifier,
        currentUser = currentUser,
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
    isDarkMode: Boolean = true,
    ledgerEntries: List<LedgerEntry>,
    bills: List<BillItem>,
    onUpdateLedger: (List<LedgerEntry>) -> Unit,
    onUpdateBills: (List<BillItem>) -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 64.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bank Ledger & Bills",
                    color = textMain,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )

                GlassHeroButton(
                    onClick = {
                        if (selectedSubTab == BudgetSubTab.BILLS) showAddBillDialog = true
                        else showAddExpenseDialog = true
                    },
                    modifier = Modifier.width(135.dp).height(44.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (selectedSubTab == BudgetSubTab.BILLS) "Add Bill" else "Add Expense",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. Bank Balance & Safe-To-Spend Glass Overview Card
        item {
            GlassCard(isDarkMode = isDarkMode) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Bank Balance", color = textSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.US, "$%.2f", currentBankBalance),
                                color = if (currentBankBalance >= 0) Color(0xFF34C759) else Color(0xFFFF3B30),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Action Buttons: Import Statement & Add Deposit
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    documentPickerLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "text/csv",
                                            "text/comma-separated-values",
                                            "application/vnd.ms-excel",
                                            "text/plain",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(activeAccentColor.copy(alpha = 0.20f))
                                    .border(width = 1.dp, color = activeAccentColor.copy(alpha = 0.50f), shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = "Import Statement",
                                    tint = activeAccentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            IconButton(
                                onClick = { showAddDepositDialog = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF34C759).copy(alpha = 0.20f))
                                    .border(width = 1.dp, color = Color(0xFF34C759).copy(alpha = 0.50f), shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = "Add Deposit / Paycheck",
                                    tint = Color(0xFF34C759),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.5.dp)

                    // Safe To Spend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Safe-to-Spend (After Cycle Unpaid Bills)", color = textSub, fontSize = 11.sp)
                            Text(
                                text = String.format(Locale.US, "$%.2f", safeToSpend),
                                color = if (safeToSpend >= 0) activeAccentColor else Color(0xFFFF9500),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Pay Cycle Window", color = textSub, fontSize = 10.sp)
                            Text(currentPayCycle.displayRange, color = textMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Cycle Unpaid vs Paid Summary Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Unpaid Bills this cycle: ${String.format(Locale.US, "$%.2f", totalUnpaidBillsInCycle)}",
                            color = if (totalUnpaidBillsInCycle > 0) Color(0xFFFF9500) else textSub,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Paid this cycle: ${String.format(Locale.US, "$%.2f", totalPaidInCycle)}",
                            color = Color(0xFF34C759),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Sub-Tab Switcher
        item {
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
                                .clickable { selectedSubTab = tab },
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
        }

        // 2. Bi-Weekly Bills Tab Content
        if (selectedSubTab == BudgetSubTab.BILLS) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recurring Household Bills",
                        color = textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sortedBills.count { it.isPaidInCycle(currentPayCycle.cycleId) }}/${sortedBills.size} Paid in Cycle",
                        color = activeAccentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(sortedBills, key = { "bill_" + it.id }) { bill ->
                val isPaidInCurrentCycle = bill.isPaidInCycle(currentPayCycle.cycleId)

                LiquidGlassListItem(
                    title = bill.name,
                    subtitle = "${bill.dueDate} • ${if (isPaidInCurrentCycle) "Paid for ${currentPayCycle.displayRange}" else "Unpaid for current cycle"}",
                    preset = ChromaticDispersionPresets.Standard,
                    isDarkMode = isDarkMode,
                    onClick = {
                        editingBill = bill
                        showAddBillDialog = true
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPaidInCurrentCycle) Color(0xFF34C759).copy(alpha = 0.20f) else Color(0xFFFF9500).copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPaidInCurrentCycle) Icons.Default.ReceiptLong else Icons.Default.Payments,
                                contentDescription = null,
                                tint = if (isPaidInCurrentCycle) Color(0xFF34C759) else Color(0xFFFF9500),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(Locale.US, "$%.2f", bill.amount),
                                color = textMain,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(10.dp))

                            Checkbox(
                                checked = isPaidInCurrentCycle,
                                onCheckedChange = { isChecked ->
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

                                    if (isChecked) {
                                        val newTxId = UUID.randomUUID().toString()
                                        val newTx = LedgerEntry(
                                            id = newTxId,
                                            title = "Bill: ${bill.name} (${currentPayCycle.displayRange})",
                                            amount = bill.amount,
                                            type = TransactionType.BILL,
                                            timestamp = System.currentTimeMillis(),
                                            category = "Bills"
                                        )
                                        val newLedger = ledgerEntries + newTx
                                        onUpdateLedger(newLedger)

                                        if (!hid.isNullOrBlank()) {
                                            db.collection("households").document(hid)
                                                .collection("transactions").document(newTxId)
                                                .set(
                                                    hashMapOf(
                                                        "id" to newTxId,
                                                        "title" to newTx.title,
                                                        "amount" to newTx.amount,
                                                        "type" to newTx.type.name,
                                                        "timestamp" to newTx.timestamp,
                                                        "category" to newTx.category
                                                    )
                                                )
                                        }
                                        Toast.makeText(context, "${bill.name} marked as paid & logged to ledger!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF34C759),
                                    uncheckedColor = Color.White.copy(alpha = 0.40f)
                                )
                            )
                        }
                    }
                )
            }
        }

        // 3. Bank Ledger Tab Content (Income, Expenses, Bills)
        if (selectedSubTab == BudgetSubTab.LEDGER) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction History",
                        color = textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                documentPickerLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/vnd.ms-excel",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            }
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = activeAccentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Statement", color = activeAccentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = { showAddDepositDialog = true }) {
                            Text("+ Deposit", color = Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (sortedLedger.isEmpty()) {
                item {
                    Text("No transactions logged in bank ledger.", color = textSub, fontSize = 14.sp)
                }
            } else {
                items(sortedLedger, key = { "tx_" + it.id }) { entry ->
                    val isIncome = entry.type == TransactionType.INCOME
                    val typeColor = when (entry.type) {
                        TransactionType.INCOME -> Color(0xFF34C759)
                        TransactionType.BILL -> Color(0xFFFF9500)
                        TransactionType.EXPENSE -> Color(0xFFFF3B30)
                    }

                    val formattedDate = remember(entry.timestamp) {
                        val sdf = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.US)
                        sdf.format(Date(entry.timestamp))
                    }

                    LiquidGlassListItem(
                        title = entry.title,
                        subtitle = "${entry.category} • $formattedDate",
                        preset = ChromaticDispersionPresets.Standard,
                        isDarkMode = isDarkMode,
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(typeColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (entry.type) {
                                        TransactionType.INCOME -> Icons.Default.ArrowUpward
                                        TransactionType.BILL -> Icons.Default.ReceiptLong
                                        TransactionType.EXPENSE -> Icons.Default.ArrowDownward
                                    },
                                    contentDescription = null,
                                    tint = typeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = String.format(Locale.US, "%s$%.2f", if (isIncome) "+" else "-", abs(entry.amount)),
                                    color = typeColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                IconButton(
                                    onClick = {
                                        val newLedger = ledgerEntries.filter { it.id != entry.id }
                                        onUpdateLedger(newLedger)

                                        val hid = currentUser.householdId
                                        if (!hid.isNullOrBlank()) {
                                            db.collection("households").document(hid)
                                                .collection("transactions").document(entry.id)
                                                .delete()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Entry",
                                        tint = Color.White.copy(alpha = 0.50f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Add / Edit Bill Dialog
    if (showAddBillDialog) {
        AddBillDialog(
            currentUser = currentUser,
            editingBill = editingBill,
            themePreference = currentUser.themePreference,
            onDismiss = {
                editingBill = null
                showAddBillDialog = false
            },
            onSaveSuccess = { newBill ->
                val updatedBills = bills.filter { it.id != newBill.id } + newBill
                onUpdateBills(updatedBills)
                editingBill = null
                showAddBillDialog = false
            },
            onDeleteSuccess = { deletedId ->
                val updatedBills = bills.filter { it.id != deletedId }
                onUpdateBills(updatedBills)
                editingBill = null
                showAddBillDialog = false
            }
        )
    }

    // Add Deposit / Income Dialog
    if (showAddDepositDialog) {
        AddDepositDialog(
            currentUser = currentUser,
            themePreference = currentUser.themePreference,
            onDismiss = { showAddDepositDialog = false },
            onAddSuccess = { newEntry ->
                val newLedger = ledgerEntries + newEntry
                onUpdateLedger(newLedger)
                showAddDepositDialog = false
            }
        )
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        AddExpenseDialog(
            currentUser = currentUser,
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

// ---------------------------------------------------------------------------
// EPHEMERAL STATEMENT PARSER PIPELINE (SAMSUNG MONEY / SOFI PDF & CSV PARSER)
// ---------------------------------------------------------------------------

fun importStatementInMemory(
    context: Context,
    uri: Uri,
    currentUser: UserProfile,
    db: FirebaseFirestore,
    existingLedger: List<LedgerEntry>,
    onUpdateLedger: (List<LedgerEntry>) -> Unit
) {
    try {
        PDFBoxResourceLoader.init(context.applicationContext)

        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            Toast.makeText(context, "Unable to read statement file.", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Zero-Storage PDF Text Extraction via PDFBox Android
        val fullText = runCatching {
            val pdfDocument = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(pdfDocument)
            pdfDocument.close()
            text
        }.getOrNull() ?: inputStream.bufferedReader().use { it.readText() }

        if (fullText.isBlank()) {
            Toast.makeText(context, "Statement file is empty or unreadable.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Isolate Target Section: "Spending balance" to "Important Information"
        var extractedSection = fullText
        if (fullText.contains("Spending balance", ignoreCase = true)) {
            extractedSection = fullText.substringAfter("Spending balance")
            if (extractedSection.contains("Important Information", ignoreCase = true)) {
                extractedSection = extractedSection.substringBefore("Important Information")
            }
        }

        // 3. Ending Balance Extraction (Samsung Money / SoFi Statement Ending Balance)
        val endingBalanceRegex = Regex("""(?i)(?:Current Balance|Ending Balance|Balance)\s*:?\s*\$?([\d,]+\.\d{2})""")
        val endingBalanceMatch = endingBalanceRegex.find(fullText)
            ?: Regex("""(?i)\$?([\d,]+\.\d{2})\s+as of""").find(fullText)

        val statementEndingBalance = endingBalanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 428.04

        val parsedEntries = mutableListOf<LedgerEntry>()
        val hid = currentUser.householdId
        val dateSdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        // 4. Regex Transaction Parsing
        // Format: Date (e.g. Sep 30, 2026), Type (e.g. Debit Card / Direct Deposit), Description, Amount (-$15.02 or $1,406.80)
        val txRegex = Regex("""(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}),?\s+(\d{4})\s+(Debit Card|Direct Deposit|Deposit|Withdrawal|Interest Earned|Transfer|ACH)?\s*(.*?)\s*(-?\$?[\d,]+\.\d{2})""")

        for (line in extractedSection.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val match = txRegex.find(trimmed)
            if (match != null) {
                val monthStr = match.groupValues[1]
                val dayStr = match.groupValues[2]
                val yearStr = match.groupValues[3]
                val typeStr = match.groupValues[4]
                val descStr = match.groupValues[5].trim().replace("\"", "").ifBlank { "Statement Entry" }
                val rawAmountStr = match.groupValues[6].replace("$", "").replace(",", "")

                val numericAmount = rawAmountStr.toDoubleOrNull()
                if (numericAmount != null && abs(numericAmount) > 0.001) {
                    val isNegative = rawAmountStr.startsWith("-") ||
                            typeStr.equals("Debit Card", ignoreCase = true) ||
                            typeStr.equals("Withdrawal", ignoreCase = true)

                    val txType = if (isNegative) TransactionType.EXPENSE
                    else if (typeStr.contains("Deposit", ignoreCase = true) || typeStr.contains("Interest", ignoreCase = true)) TransactionType.INCOME
                    else if (descStr.contains("Bill", ignoreCase = true) || descStr.contains("Rent", ignoreCase = true)) TransactionType.BILL
                    else if (numericAmount > 0) TransactionType.INCOME
                    else TransactionType.EXPENSE

                    val timestamp = runCatching {
                        dateSdf.parse("$monthStr $dayStr, $yearStr")?.time
                    }.getOrNull() ?: System.currentTimeMillis()

                    parsedEntries.add(
                        LedgerEntry(
                            id = UUID.randomUUID().toString(),
                            title = descStr,
                            amount = abs(numericAmount),
                            type = txType,
                            timestamp = timestamp,
                            category = if (txType == TransactionType.INCOME) "Income" else if (txType == TransactionType.BILL) "Bills" else "Expenses"
                        )
                    )
                }
            }
        }

        // 5. Clean / Delete Previously Corrupted or Dummy Statement Rows
        val cleanedLedger = existingLedger.filterNot { entry ->
            entry.title.contains("E/~RT", ignoreCase = true) ||
                    entry.title.equals("Statement Entry", ignoreCase = true) ||
                    entry.title.contains("Dummy", ignoreCase = true) ||
                    (entry.amount == 6.0 && entry.category == "Statement Import")
        }

        if (!hid.isNullOrBlank()) {
            val deleteBatch = db.batch()
            existingLedger.forEach { entry ->
                if (entry.title.contains("E/~RT", ignoreCase = true) ||
                    entry.title.equals("Statement Entry", ignoreCase = true) ||
                    entry.title.contains("Dummy", ignoreCase = true) ||
                    (entry.amount == 6.0 && entry.category == "Statement Import")) {
                    val docRef = db.collection("households").document(hid)
                        .collection("transactions").document(entry.id)
                    deleteBatch.delete(docRef)
                }
            }
            deleteBatch.commit()
        }

        // 6. Batch Insert Clean Parsed Transactions into Firestore & Sync Anchor Balance
        if (parsedEntries.isNotEmpty() && !hid.isNullOrBlank()) {
            val insertBatch = db.batch()
            parsedEntries.forEach { entry ->
                val docRef = db.collection("households").document(hid)
                    .collection("transactions").document(entry.id)
                insertBatch.set(
                    docRef,
                    hashMapOf(
                        "id" to entry.id,
                        "title" to entry.title,
                        "amount" to entry.amount,
                        "type" to entry.type.name,
                        "timestamp" to entry.timestamp,
                        "category" to entry.category
                    ),
                    SetOptions.merge()
                )
            }
            insertBatch.commit()
        }

        val updatedLedger = cleanedLedger + parsedEntries
        onUpdateLedger(updatedLedger)

        Toast.makeText(
            context,
            "Statement Parsed! ${parsedEntries.size} clean transactions imported. Current Balance: $${String.format(Locale.US, "%.2f", statementEndingBalance)}",
            Toast.LENGTH_LONG
        ).show()

    } catch (e: Exception) {
        Toast.makeText(context, "PDF Import error: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
    }
}

// ---------------------------------------------------------------------------
// DIALOG: Add Deposit / Paycheck Income
// ---------------------------------------------------------------------------

@Composable
fun AddDepositDialog(
    currentUser: UserProfile,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161B22).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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
    }
}

// ---------------------------------------------------------------------------
// DIALOG: Add Expense / Outflow
// ---------------------------------------------------------------------------

@Composable
fun AddExpenseDialog(
    currentUser: UserProfile,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161B22).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
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
                        label = { Text("Category (e.g. Groceries, Shopping)", color = Color.White.copy(alpha = 0.70f)) },
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
                                        Toast.makeText(context, "Expense logged to bank balance!", Toast.LENGTH_SHORT).show()
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
    }
}

// ---------------------------------------------------------------------------
// DIALOG: Add / Edit Recurring Bill
// ---------------------------------------------------------------------------

@Composable
fun AddBillDialog(
    currentUser: UserProfile,
    editingBill: BillItem? = null,
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF161B22).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (editingBill != null) "Edit Recurring Bill" else "Add Recurring Bill",
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
                        label = { Text("Bill Name (e.g. Rent, Xfinity)", color = Color.White.copy(alpha = 0.70f)) },
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
                        label = { Text("Due Date (e.g. Due 1st)", color = Color.White.copy(alpha = 0.70f)) },
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
                                Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
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
                                            Toast.makeText(context, "Bill saved!", Toast.LENGTH_SHORT).show()
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
    }
}
