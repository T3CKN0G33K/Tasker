package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.background
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

@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
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
    transactions: List<BudgetTransaction>,
    bills: List<BillItem>,
    monthlyLimit: Double,
    onUpdateTransactions: (List<BudgetTransaction>) -> Unit,
    onUpdateBills: (List<BillItem>) -> Unit,
    onUpdateMonthlyLimit: (Double) -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var showAddBillDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var showEditBudgetDialog by remember { mutableStateOf(false) }
    var billToEdit by remember { mutableStateOf<BillItem?>(null) }

    val totalSpent = remember(transactions) { transactions.sumOf { abs(it.amount) } }
    val totalBills = remember(bills) { bills.filter { it.isPaid }.sumOf { it.amount } }
    val remainingBalance = monthlyLimit - (totalSpent + totalBills)
    val progress = if (monthlyLimit > 0) ((totalSpent + totalBills) / monthlyLimit).coerceIn(0.0, 1.0).toFloat() else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Budget & Bills",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showAddTransactionDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Expense", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Overview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditBudgetDialog = true },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Remaining Safe-to-Spend", color = Color(0xFF8E8E93), fontSize = 14.sp)
                        Icon(Icons.Default.Edit, contentDescription = "Edit Limit", tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", remainingBalance)}",
                        color = Color.White,
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
                        color = if (progress >= 0.9f) Color(0xFFFF453A) else Color(0xFF0A84FF),
                        trackColor = Color(0xFF3A3A3C),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Spent + Bills: $${String.format(Locale.US, "%.2f", totalSpent + totalBills)}", color = Color(0xFF8E8E93), fontSize = 12.sp)
                        Text("Total Bills: $${String.format(Locale.US, "%.2f", monthlyLimit)}", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    }
                }
            }
        }

        // Bill Tracker Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monthly Bills", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddBillDialog = true }) {
                    Text("+ Add Bill", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (bills.isEmpty()) {
            item {
                Text("No monthly bills logged.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            // Monthly Bills List (Clickable to Edit Amount / Due Date)
            items(bills, key = { "bill_" + it.id }) { bill ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { billToEdit = bill },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(bill.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.Edit, contentDescription = "Edit Amount", tint = Color(0xFF0A84FF), modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(bill.dueDate, color = Color(0xFF8E8E93), fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$${String.format(Locale.US, "%.2f", bill.amount)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = bill.isPaid,
                                onCheckedChange = { checked ->
                                    val updatedBill = bill.copy(isPaid = checked)
                                    val updatedList = bills.map { if (it.id == bill.id) updatedBill else it }
                                    onUpdateBills(updatedList)
                                    
                                    currentUser.householdId?.let { hid ->
                                        val billDoc = db.collection("households").document(hid).collection("bills").document(bill.id)
                                        billDoc.set(
                                            hashMapOf(
                                                "name" to bill.name,
                                                "amount" to bill.amount,
                                                "dueDate" to bill.dueDate,
                                                "isPaid" to checked
                                            )
                                        )
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF34C759)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val updatedList = bills.filter { it.id != bill.id }
                                    onUpdateBills(updatedList)
                                    currentUser.householdId?.let { hid ->
                                        db.collection("households").document(hid)
                                            .collection("bills").document(bill.id)
                                            .delete()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Bill", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Recent Transactions Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Recent Transactions", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (transactions.isEmpty()) {
            item {
                Text("No recent transactions found.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            // Recent Transactions List (Prefix Key to guarantee uniqueness across LazyColumn)
            items(transactions, key = { "tx_" + it.id }) { tx ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(tx.account, color = Color(0xFF8E8E93), fontSize = 12.sp)
                            Text(tx.date, color = Color.DarkGray, fontSize = 10.sp)
                        }
                        Text(
                            text = if (tx.amount < 0) "-$${String.format(Locale.US, "%.2f", abs(tx.amount))}" else "+$${String.format(Locale.US, "%.2f", tx.amount)}",
                            color = if (tx.amount < 0) Color(0xFFFF453A) else Color(0xFF34C759),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val updatedList = transactions.filter { it.id != tx.id }
                                onUpdateTransactions(updatedList)
                                currentUser.householdId?.let { hid ->
                                    db.collection("households").document(hid)
                                        .collection("transactions").document(tx.id)
                                        .delete()
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Transaction", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for floating taskbar
        }
    }

    if (showAddBillDialog) {
        AddBillDialog(
            onDismiss = { showAddBillDialog = false },
            onAdd = { name, amount, dueDate ->
                val newBill = BillItem(UUID.randomUUID().toString(), name, amount, dueDate, false)
                val updatedList = bills + newBill
                onUpdateBills(updatedList)
                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("bills").document(newBill.id)
                    val billMap = hashMapOf(
                        "name" to name,
                        "amount" to amount,
                        "dueDate" to dueDate,
                        "isPaid" to false
                    )
                    docRef.set(billMap)
                }
                showAddBillDialog = false
            }
        )
    }

    // Edit Bill Dialog (For modifying fluctuating bill amounts like Insurance or Rent a Center)
    billToEdit?.let { bill ->
        EditBillDialog(
            bill = bill,
            onDismiss = { billToEdit = null },
            onSave = { updatedBill ->
                val updatedList = bills.map { if (it.id == bill.id) updatedBill else it }
                onUpdateBills(updatedList)
                currentUser.householdId?.let { hid ->
                    val billDoc = db.collection("households").document(hid).collection("bills").document(bill.id)
                    billDoc.set(
                        hashMapOf(
                            "name" to updatedBill.name,
                            "amount" to updatedBill.amount,
                            "dueDate" to updatedBill.dueDate,
                            "isPaid" to updatedBill.isPaid
                        )
                    )
                }
                billToEdit = null
            },
            onDelete = {
                val updatedList = bills.filter { it.id != bill.id }
                onUpdateBills(updatedList)
                currentUser.householdId?.let { hid ->
                    db.collection("households").document(hid)
                        .collection("bills").document(bill.id)
                        .delete()
                }
                billToEdit = null
            }
        )
    }

    if (showAddTransactionDialog) {
        AddTransactionDialog(
            onDismiss = { showAddTransactionDialog = false },
            onAdd = { title, amount, date ->
                val newTx = BudgetTransaction(UUID.randomUUID().toString(), title, -abs(amount), date, "Manual Entry")
                val updatedList = listOf(newTx) + transactions
                onUpdateTransactions(updatedList)
                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("transactions").document(newTx.id)
                    val transactionData = hashMapOf(
                        "title" to title,
                        "amount" to -abs(amount),
                        "date" to date,
                        "account" to "Manual Entry"
                    )
                    docRef.set(transactionData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Expense added!", Toast.LENGTH_SHORT).show()
                        }
                }
                showAddTransactionDialog = false
            }
        )
    }

    if (showEditBudgetDialog) {
        EditBudgetDialog(
            currentLimit = monthlyLimit,
            onDismiss = { showEditBudgetDialog = false },
            onSave = { newLimit ->
                onUpdateMonthlyLimit(newLimit)
                currentUser.householdId?.let { hid ->
                    db.collection("households").document(hid)
                        .set(hashMapOf("budgetLimit" to newLimit), SetOptions.merge())
                        .addOnSuccessListener {
                            Toast.makeText(context, "Budget limit updated!", Toast.LENGTH_SHORT).show()
                        }
                }
                showEditBudgetDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBillDialog(
    bill: BillItem,
    onDismiss: () -> Unit,
    onSave: (BillItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(bill.name) }
    var amountText by remember { mutableStateOf(bill.amount.toString()) }
    var dueDateText by remember { mutableStateOf(bill.dueDate) }
    var isPaid by remember { mutableStateOf(bill.isPaid) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Edit Bill Details", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Bill Amount ($)", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                // Interactive Calendar Date Picker Selector Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2C2C2E))
                        .clickable { showDatePicker = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Due Date: $dueDateText", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Due Date", tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paid Status", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull() ?: bill.amount
                    if (name.isNotBlank() && parsedAmount > 0) {
                        onSave(bill.copy(name = name.trim(), amount = parsedAmount, dueDate = dueDateText, isPaid = isPaid))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val utcFormat = SimpleDateFormat("d", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val dayNum = utcFormat.format(Date(millis))
                            val suffix = when (dayNum.toIntOrNull() ?: 1) {
                                1, 21, 31 -> "st"
                                2, 22 -> "nd"
                                3, 23 -> "rd"
                                else -> "th"
                            }
                            dueDateText = "Due ${dayNum}$suffix"
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = Color.Gray) }
            },
            colors = DatePickerDefaults.colors(containerColor = Color(0xFF1C1C1E))
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF1C1C1E),
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color.Gray,
                    subheadContentColor = Color.White,
                    dayContentColor = Color.White,
                    selectedDayContainerColor = Color(0xFF0A84FF),
                    selectedDayContentColor = Color.White
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBillDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, amount: Double, dueDate: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDateText by remember { mutableStateOf("Due 1st") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Add Monthly Bill", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bill Name (e.g. Electric / Rent)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($)", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                // Interactive Calendar Date Picker Selector Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2C2C2E))
                        .clickable { showDatePicker = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Due Date: $dueDateText", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Due Date", tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && parsedAmount > 0) {
                        onAdd(name.trim(), parsedAmount, dueDateText.ifBlank { "Due 1st" })
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
            ) {
                Text("Add Bill", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // FIX: Force TimeZone to UTC to prevent day-before offset shifts!
                            val utcFormat = SimpleDateFormat("d", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            val dayNum = utcFormat.format(Date(millis))
                            val suffix = when (dayNum.toIntOrNull() ?: 1) {
                                1, 21, 31 -> "st"
                                2, 22 -> "nd"
                                3, 23 -> "rd"
                                else -> "th"
                            }
                            dueDateText = "Due ${dayNum}$suffix"
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = Color.Gray) }
            },
            colors = DatePickerDefaults.colors(containerColor = Color(0xFF1C1C1E))
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF1C1C1E),
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color.Gray,
                    subheadContentColor = Color.White,
                    dayContentColor = Color.White,
                    selectedDayContainerColor = Color(0xFF0A84FF),
                    selectedDayContentColor = Color.White
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, amount: Double, date: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Log New Expense", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Expense Title (e.g. Groceries)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($)", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                // Interactive Calendar Date Picker Selector Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2C2C2E))
                        .clickable { showDatePicker = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Date: $dateText", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = Color(0xFF0A84FF), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && parsedAmount > 0) {
                        onAdd(title.trim(), parsedAmount, dateText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
            ) {
                Text("Add Expense", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // FIX: Force TimeZone to UTC to prevent day-before offset shifts!
                            val utcFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }
                            dateText = utcFormat.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = Color.Gray) }
            },
            colors = DatePickerDefaults.colors(containerColor = Color(0xFF1C1C1E))
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF1C1C1E),
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color.Gray,
                    subheadContentColor = Color.White,
                    dayContentColor = Color.White,
                    selectedDayContainerColor = Color(0xFF0A84FF),
                    selectedDayContentColor = Color.White
                )
            )
        }
    }
}

@Composable
fun EditBudgetDialog(
    currentLimit: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var limitText by remember { mutableStateOf(currentLimit.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Set Monthly Budget Limit", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Monthly Budget ($)", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = limitText.toDoubleOrNull()
                    if (parsed != null && parsed > 0) {
                        onSave(parsed)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
            ) {
                Text("Save Limit", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}
