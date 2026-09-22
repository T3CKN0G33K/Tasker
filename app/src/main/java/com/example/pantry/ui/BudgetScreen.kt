package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

enum class BudgetSubTab(val label: String) {
    BILLS("Monthly Bills"),
    TRANSACTIONS("Recent Transactions")
}

data class BillItem(
    val id: String = "",
    val name: String,
    val amount: Double,
    val dueDate: String,
    val isPaid: Boolean
)

data class BudgetTransaction(
    val id: String = "",
    val title: String,
    val amount: Double,
    val date: String,
    val account: String
)

val defaultBillsList = listOf(
    BillItem("b1", "Rent", 397.00, "Due 1st", false),
    BillItem("b2", "Disney Plus", 35.99, "Due 1st", false),
    BillItem("b3", "Xfinity", 80.94, "Due 4th", false),
    BillItem("b4", "Google ONE", 21.24, "Due 8th", true),
    BillItem("b5", "Safty Insurance", 124.71, "Due 10th", true),
    BillItem("b6", "YouTube", 13.99, "Due 10th", true),
    BillItem("b7", "Spotify", 12.99, "Due 16th", true),
    BillItem("b8", "National Grid", 138.00, "Due 16th", true),
    BillItem("b9", "AT&T", 263.00, "Due 25th", false),
    BillItem("b10", "Rent a Center", 336.74, "Due 17th", false),
    BillItem("b11", "Rent a Center", 380.06, "Due 19th", false)
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
    transactions: List<BudgetTransaction> = emptyList(),
    bills: List<BillItem> = emptyList(),
    monthlyLimit: Double = 1804.66,
    onUpdateTransactions: (List<BudgetTransaction>) -> Unit = {},
    onUpdateBills: (List<BillItem>) -> Unit = {},
    onUpdateMonthlyLimit: (Double) -> Unit = {}
) {
    BudgetScreenContent(
        modifier = modifier,
        currentUser = currentUser,
        isDarkMode = isDarkMode,
        transactions = transactions,
        bills = bills.ifEmpty { defaultBillsList },
        monthlyLimit = monthlyLimit,
        onUpdateTransactions = onUpdateTransactions,
        onUpdateBills = onUpdateBills,
        onUpdateMonthlyLimit = onUpdateMonthlyLimit
    )
}

@Composable
fun BudgetScreenContent(
    modifier: Modifier = Modifier,
    currentUser: UserProfile,
    isDarkMode: Boolean = true,
    transactions: List<BudgetTransaction>,
    bills: List<BillItem>,
    monthlyLimit: Double,
    onUpdateTransactions: (List<BudgetTransaction>) -> Unit,
    onUpdateBills: (List<BillItem>) -> Unit,
    onUpdateMonthlyLimit: (Double) -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var selectedSubTab by remember { mutableStateOf(BudgetSubTab.BILLS) }

    var showAddBillDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showEditBudgetDialog by remember { mutableStateOf(false) }
    var billToEdit by remember { mutableStateOf<BillItem?>(null) }

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

    val totalMonthlyBills = remember(bills) { bills.sumOf { it.amount } }
    val effectiveLimit = if (monthlyLimit <= 0.0) totalMonthlyBills else monthlyLimit
    val totalSpent = remember(transactions) { transactions.sumOf { abs(it.amount) } }
    val totalPaidBills = remember(bills) { bills.filter { it.isPaid }.sumOf { it.amount } }
    val remainingBalance = effectiveLimit - (totalSpent + totalPaidBills)
    val progress = if (effectiveLimit > 0) ((totalSpent + totalPaidBills) / effectiveLimit).coerceIn(0.0, 1.0).toFloat() else 0f

    // Chronological due date sorting (1st to 30th)
    val sortedBills = remember(bills) {
        bills.sortedBy { parseDueDateNumber(it.dueDate) }
    }

    // Unblock Background -> Color.Transparent for full-screen edge-to-edge ambient theme gradient!
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 64.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Budget & Bills",
                    color = textMain,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                // Glass Hero Button for Add Bill / Add Expense
                GlassHeroButton(
                    onClick = {
                        if (selectedSubTab == BudgetSubTab.BILLS) showAddBillDialog = true
                        else showAddTransactionDialog = true
                    },
                    modifier = Modifier.width(130.dp).height(44.dp)
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

        // Overview Card (Upgraded to GlassCard)
        item {
            GlassCard(
                isDarkMode = isDarkMode,
                modifier = Modifier.clickable { showEditBudgetDialog = true }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Remaining Safe-to-Spend", color = textSub, fontSize = 14.sp)
                        Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", remainingBalance)}",
                        color = textMain,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = if (progress >= 0.9f) Color(0xFFFF3B30) else Color(0xFF007AFF),
                        trackColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Spent + Paid: $${String.format(Locale.US, "%.2f", totalSpent + totalPaidBills)}", color = textSub, fontSize = 12.sp)
                        Text("Total Monthly Bills: $${String.format(Locale.US, "%.2f", totalMonthlyBills)}", color = textSub, fontSize = 12.sp)
                    }
                }
            }
        }

        // Segmented Control Sub-Tab Pill Row (Upgraded to GlassCard)
        item {
            GlassCard(isDarkMode = isDarkMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    BudgetSubTab.entries.forEach { subTab ->
                        val isSelected = selectedSubTab == subTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF007AFF) else Color.Transparent)
                                .clickable { selectedSubTab = subTab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = subTab.label,
                                color = if (isSelected) Color.White else textSub,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // List Content Section
        if (selectedSubTab == BudgetSubTab.BILLS) {
            items(sortedBills, key = { it.id.ifBlank { it.name + it.dueDate } }) { bill ->
                GlassCard(
                    isDarkMode = isDarkMode,
                    modifier = Modifier.clickable { billToEdit = bill }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = bill.isPaid,
                                onCheckedChange = { isChecked ->
                                    val updated = bills.map { if (it.id == bill.id || (it.name == bill.name && it.dueDate == bill.dueDate)) it.copy(isPaid = isChecked) else it }
                                    onUpdateBills(updated)

                                    currentUser.householdId?.let { hid ->
                                        db.collection("households").document(hid)
                                            .collection("bills").document(bill.id)
                                            .update("isPaid", isChecked)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF34C759),
                                    uncheckedColor = textSub
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(bill.name, color = textMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(bill.dueDate, color = textSub, fontSize = 12.sp)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", bill.amount)}",
                                color = textMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (bill.isPaid) Color(0xFF34C759).copy(alpha = 0.25f) else Color(0xFFFF9500).copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (bill.isPaid) "PAID" else "UNPAID",
                                    color = if (bill.isPaid) Color(0xFF34C759) else Color(0xFFFF9500),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(transactions, key = { it.id.ifBlank { it.title + it.date } }) { tx ->
                GlassCard(isDarkMode = isDarkMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.title, color = textMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${tx.date} • ${tx.account}", color = textSub, fontSize = 12.sp)
                        }

                        Text(
                            text = "-$${String.format(Locale.US, "%.2f", abs(tx.amount))}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showAddBillDialog) {
        AddBillDialog(
            currentUser = currentUser,
            onDismiss = { showAddBillDialog = false },
            onAdd = { newBill ->
                val updated = bills + newBill
                onUpdateBills(updated)

                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("bills").document()
                    val data = hashMapOf(
                        "name" to newBill.name,
                        "amount" to newBill.amount,
                        "dueDate" to newBill.dueDate,
                        "isPaid" to newBill.isPaid
                    )
                    docRef.set(data, SetOptions.merge())
                }
                showAddBillDialog = false
            }
        )
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            currentUser = currentUser,
            onDismiss = { showAddTransactionDialog = false },
            onAdd = { newTx ->
                val updated = transactions + newTx
                onUpdateTransactions(updated)

                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("transactions").document()
                    val data = hashMapOf(
                        "title" to newTx.title,
                        "amount" to newTx.amount,
                        "date" to newTx.date,
                        "account" to newTx.account
                    )
                    docRef.set(data, SetOptions.merge())
                }
                showAddTransactionDialog = false
            }
        )
    }

    if (showEditBudgetDialog) {
        EditBudgetLimitDialog(
            currentLimit = monthlyLimit,
            themePreference = currentUser.themePreference,
            onDismiss = { showEditBudgetDialog = false },
            onSave = { newLimit ->
                onUpdateMonthlyLimit(newLimit)
                currentUser.householdId?.let { hid ->
                    db.collection("households").document(hid)
                        .set(hashMapOf("budgetLimit" to newLimit), SetOptions.merge())
                }
                showEditBudgetDialog = false
            }
        )
    }

    billToEdit?.let { bill ->
        EditBillDialog(
            bill = bill,
            themePreference = currentUser.themePreference,
            onDismiss = { billToEdit = null },
            onSave = { updatedBill ->
                val updated = bills.map { if (it.id == bill.id) updatedBill else it }
                onUpdateBills(updated)

                currentUser.householdId?.let { hid ->
                    db.collection("households").document(hid)
                        .collection("bills").document(bill.id)
                        .set(
                            hashMapOf(
                                "name" to updatedBill.name,
                                "amount" to updatedBill.amount,
                                "dueDate" to updatedBill.dueDate,
                                "isPaid" to updatedBill.isPaid
                            ),
                            SetOptions.merge()
                        )
                }
                billToEdit = null
            },
            onDelete = {
                val updated = bills.filter { it.id != bill.id }
                onUpdateBills(updated)

                currentUser.householdId?.let { hid ->
                    db.collection("households").document(hid)
                        .collection("bills").document(bill.id)
                        .delete()
                }
                billToEdit = null
            }
        )
    }
}

@Composable
fun AddBillDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onAdd: (BillItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("Due 1st") }

    val context = LocalContext.current

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        themePreference = currentUser.themePreference,
        title = { Text("Add Recurring Monthly Bill", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill Name (e.g. Electric, Rent)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($ e.g. 138.00)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (e.g. Due 16th)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (name.isBlank() || amount == null || amount <= 0) {
                        Toast.makeText(context, "Please enter valid bill name and amount.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val newBill = BillItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        amount = amount,
                        dueDate = dueDate.trim().ifBlank { "Due 1st" },
                        isPaid = false
                    )
                    onAdd(newBill)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Add Bill", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun AddTransactionDialog(
    currentUser: UserProfile,
    onDismiss: () -> Unit,
    onAdd: (BudgetTransaction) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("Checking") }

    val context = LocalContext.current

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        themePreference = currentUser.themePreference,
        title = { Text("Log Out-of-Pocket Expense", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title (e.g. Groceries)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($ e.g. 45.50)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text("Account (e.g. Debit Card)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (title.isBlank() || amount == null || amount <= 0) {
                        Toast.makeText(context, "Please enter valid title and amount.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
                    val newTx = BudgetTransaction(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        amount = amount,
                        date = sdf.format(Date()),
                        account = account.trim().ifBlank { "Checking" }
                    )
                    onAdd(newTx)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Log Expense", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun EditBudgetLimitDialog(
    currentLimit: Double,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var limitText by remember { mutableStateOf(if (currentLimit > 0) currentLimit.toString() else "") }
    val context = LocalContext.current

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        themePreference = themePreference,
        title = { Text("Edit Monthly Budget Cap", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set monthly spending cap. Remaining safe-to-spend balance calculates from this limit.", color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Cap ($)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newLimit = limitText.toDoubleOrNull()
                    if (newLimit == null || newLimit < 0) {
                        Toast.makeText(context, "Please enter a valid dollar amount.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(newLimit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Save Cap", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun EditBillDialog(
    bill: BillItem,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSave: (BillItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(bill.name) }
    var amountText by remember { mutableStateOf(bill.amount.toString()) }
    var dueDate by remember { mutableStateOf(bill.dueDate) }
    var isPaid by remember { mutableStateOf(bill.isPaid) }

    val context = LocalContext.current

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        themePreference = themePreference,
        title = { Text("Edit Monthly Bill", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill Name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                OutlinedTextField(
                    value = dueDate,
                    onValueChange = { dueDate = it },
                    label = { Text("Due Date (e.g. Due 16th)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mark as Paid", color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: bill.amount
                    if (name.isBlank() || amount <= 0) {
                        Toast.makeText(context, "Please enter valid bill name and amount.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSave(bill.copy(name = name.trim(), amount = amount, dueDate = dueDate.trim(), isPaid = isPaid))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) {
                    Text("Delete Bill", color = Color(0xFFFF3B30))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    )
}
