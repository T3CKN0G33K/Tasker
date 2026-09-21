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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
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
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SplitShiftEntry(
    val id: String = "",
    val date: String,
    val clockIn1: String,
    val clockOut1: String,
    val clockIn2: String = "",
    val clockOut2: String = "",
    val hourlyRate: Double = 20.16
) {
    private fun parseLegHours(cIn: String, cOut: String): Double {
        if (cIn.isBlank() || cOut.isBlank()) return 0.0
        return try {
            val inParts = cIn.split(":").map { it.toInt() }
            val outParts = cOut.split(":").map { it.toInt() }
            val startMinutes = inParts[0] * 60 + inParts[1]
            var endMinutes = outParts[0] * 60 + outParts[1]
            if (endMinutes <= startMinutes) {
                endMinutes += 24 * 60 // Overnight shift past midnight
            }
            ((endMinutes - startMinutes) / 60.0).coerceAtLeast(0.0)
        } catch (_: Exception) {
            0.0
        }
    }

    val totalHours: Double
        get() {
            val leg1 = parseLegHours(clockIn1, clockOut1)
            val leg2 = parseLegHours(clockIn2, clockOut2)
            val sum = leg1 + leg2
            return if (sum > 0) sum else 8.0
        }

    val grossEarnings: Double get() = totalHours * hourlyRate
}

data class ArchivedPayCycle(
    val id: String = "",
    val periodRange: String,
    val totalHours: Double,
    val grossPay: Double,
    val netTakeHome: Double,
    val archivedAt: Long = System.currentTimeMillis()
)

val defaultShiftsList = listOf(
    SplitShiftEntry("s1", "Sep 20, 2026", "20:50", "01:00", "01:30", "05:20", 20.16),
    SplitShiftEntry("s2", "Sep 19, 2026", "20:50", "01:00", "01:30", "05:20", 20.16),
    SplitShiftEntry("s3", "Sep 18, 2026", "20:50", "01:00", "01:30", "05:20", 20.16),
    SplitShiftEntry("s4", "Sep 17, 2026", "20:50", "01:00", "01:30", "05:20", 20.16)
)

@Composable
fun ShiftsScreen(
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc")
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var currentShifts by remember { mutableStateOf<List<SplitShiftEntry>>(emptyList()) }
    var archivedPeriods by remember { mutableStateOf<List<ArchivedPayCycle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showLogShiftDialog by remember { mutableStateOf(false) }
    var showArchivedHistoryDialog by remember { mutableStateOf(false) }

    val activePeriodRange = "Aug 10, 2026 - Aug 23, 2026"

    // Real-time Firestore Sync for Active Split Shifts & Archived Pay Periods
    LaunchedEffect(currentUser.householdId) {
        currentUser.householdId?.let { hid ->
            // 1. Listen to Active Shifts
            db.collection("households").document(hid).collection("split_shifts")
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    if (snapshot != null && !snapshot.isEmpty) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            SplitShiftEntry(
                                id = doc.id,
                                date = doc.getString("date") ?: "Today",
                                clockIn1 = doc.getString("clockIn1") ?: "20:50",
                                clockOut1 = doc.getString("clockOut1") ?: "01:00",
                                clockIn2 = doc.getString("clockIn2") ?: "01:30",
                                clockOut2 = doc.getString("clockOut2") ?: "05:20",
                                hourlyRate = doc.getDouble("hourlyRate") ?: 20.16
                            )
                        }
                        currentShifts = items
                    } else if (currentShifts.isEmpty()) {
                        currentShifts = defaultShiftsList
                    }
                }

            // 2. Listen to Archived Pay Periods
            db.collection("households").document(hid).collection("archived_pay_cycles")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val periods = snapshot.documents.mapNotNull { doc ->
                            ArchivedPayCycle(
                                id = doc.id,
                                periodRange = doc.getString("periodRange") ?: "Past Cycle",
                                totalHours = doc.getDouble("totalHours") ?: 0.0,
                                grossPay = doc.getDouble("grossPay") ?: 0.0,
                                netTakeHome = doc.getDouble("netTakeHome") ?: 0.0,
                                archivedAt = doc.getLong("archivedAt") ?: 0L
                            )
                        }
                        archivedPeriods = periods.sortedByDescending { it.archivedAt }
                    }
                }
        }
    }

    // Home Depot Net Pay Payroll Engine
    val totalShiftHours = remember(currentShifts) { currentShifts.sumOf { it.totalHours } }
    val regularHours = totalShiftHours.coerceAtMost(80.0)
    val overtimeHours = (totalShiftHours - 80.0).coerceAtLeast(0.0)

    val regularPay = regularHours * 20.16
    val overtimePay = overtimeHours * (20.16 * 1.5)
    val grossPaycheck = regularPay + overtimePay

    val oasdiTax = grossPaycheck * 0.062       // 6.2%
    val medicareTax = grossPaycheck * 0.0145    // 1.45%
    val federalTax = grossPaycheck * 0.085      // ~8.5%
    val maStateTax = grossPaycheck * 0.050      // 5.0%
    val fixedBenefits = 51.42                   // Purchase Power, STD/LTD, Roadside, Homer Fund

    val totalDeductions = oasdiTax + medicareTax + federalTax + maStateTax + fixedBenefits
    val netTakeHomePay = (grossPaycheck - totalDeductions).coerceAtLeast(0.0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 64.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Work Shifts",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { showArchivedHistoryDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1C1E))
                    ) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = Color(0xFF007AFF))
                    }
                }
            }

            // Home Depot Pay Period & Net Pay Engine Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active 14-Day Pay Period", color = Color.Gray, fontSize = 13.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF007AFF).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(activePeriodRange, color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Calculated Net Take-Home Pay", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", netTakeHomePay)}",
                            color = Color(0xFF34C759),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)

                        // Payroll Breakdown Grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gross Pay (${String.format(Locale.US, "%.1f", totalShiftHours)} hrs @ $20.16/hr):", color = Color.Gray, fontSize = 12.sp)
                                Text("$${String.format(Locale.US, "%.2f", grossPaycheck)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("OASDI (6.2%) + Medicare (1.45%):", color = Color.Gray, fontSize = 12.sp)
                                Text("-$${String.format(Locale.US, "%.2f", oasdiTax + medicareTax)}", color = Color(0xFFFF453A), fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Federal (~8.5%) + MA State (5.0%):", color = Color.Gray, fontSize = 12.sp)
                                Text("-$${String.format(Locale.US, "%.2f", federalTax + maStateTax)}", color = Color(0xFFFF453A), fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fixed Deductions (Power, STD/LTD, Homer):", color = Color.Gray, fontSize = 12.sp)
                                Text("-$${String.format(Locale.US, "%.2f", fixedBenefits)}", color = Color(0xFFFF453A), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showLogShiftDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Split Shift", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (currentShifts.isNotEmpty()) {
                                        currentUser.householdId?.let { hid ->
                                            val docRef = db.collection("households").document(hid).collection("archived_pay_cycles").document()
                                            docRef.set(
                                                hashMapOf(
                                                    "periodRange" to activePeriodRange,
                                                    "totalHours" to totalShiftHours,
                                                    "grossPay" to grossPaycheck,
                                                    "netTakeHome" to netTakeHomePay,
                                                    "archivedAt" to System.currentTimeMillis()
                                                )
                                            )

                                            currentShifts.forEach { shift ->
                                                db.collection("households").document(hid).collection("split_shifts").document(shift.id).delete()
                                            }
                                        }

                                        currentShifts = emptyList()
                                        Toast.makeText(context, "Pay Period Closed & Archived!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Archive Cycle", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Logged Split Shifts",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                }
            } else if (currentShifts.isEmpty()) {
                item {
                    Text("No split shifts logged for this pay period.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                items(currentShifts, key = { "shift_" + it.id }) { shift ->
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
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF2C2C2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(shift.date, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("In1: ${shift.clockIn1} ➔ Out1: ${shift.clockOut1}", color = Color.Gray, fontSize = 11.sp)
                                if (shift.clockIn2.isNotBlank() && shift.clockOut2.isNotBlank()) {
                                    Text("In2: ${shift.clockIn2} ➔ Out2: ${shift.clockOut2}", color = Color.Gray, fontSize = 11.sp)
                                }
                                Text("${String.format(Locale.US, "%.1f", shift.totalHours)} Total Hours", color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", shift.grossEarnings)}",
                                    color = Color(0xFF34C759),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("$20.16/hr", color = Color.DarkGray, fontSize = 10.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    currentShifts = currentShifts.filter { it.id != shift.id }
                                    currentUser.householdId?.let { hid ->
                                        db.collection("households").document(hid)
                                            .collection("split_shifts").document(shift.id)
                                            .delete()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Shift", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogShiftDialog) {
        LogSplitShiftWithCalendarDialog(
            onDismiss = { showLogShiftDialog = false },
            onLog = { selectedDateStr, cIn1, cOut1, cIn2, cOut2 ->
                val newShift = SplitShiftEntry(
                    id = UUID.randomUUID().toString(),
                    date = selectedDateStr,
                    clockIn1 = cIn1,
                    clockOut1 = cOut1,
                    clockIn2 = cIn2,
                    clockOut2 = cOut2,
                    hourlyRate = 20.16
                )
                currentShifts = listOf(newShift) + currentShifts

                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("split_shifts").document(newShift.id)
                    docRef.set(
                        hashMapOf(
                            "date" to selectedDateStr,
                            "clockIn1" to cIn1,
                            "clockOut1" to cOut1,
                            "clockIn2" to cIn2,
                            "clockOut2" to cOut2,
                            "hourlyRate" to 20.16
                        )
                    )
                }
                showLogShiftDialog = false
            }
        )
    }

    if (showArchivedHistoryDialog) {
        ArchivedCyclesDialog(
            cycles = archivedPeriods,
            onDismiss = { showArchivedHistoryDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSplitShiftWithCalendarDialog(
    onDismiss: () -> Unit,
    onLog: (date: String, cIn1: String, cOut1: String, cIn2: String, cOut2: String) -> Unit
) {
    var dateText by remember { mutableStateOf(SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())) }
    var clockIn1Text by remember { mutableStateOf("20:50") }
    var clockOut1Text by remember { mutableStateOf("01:00") }
    var clockIn2Text by remember { mutableStateOf("01:30") }
    var clockOut2Text by remember { mutableStateOf("05:20") }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Log Overnight Split Shift", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tap date below to pick shift date on calendar:", color = Color.Gray, fontSize = 12.sp)

                // Interactive Calendar Date Picker Field
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
                        Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clockIn1Text,
                        onValueChange = { clockIn1Text = it },
                        label = { Text("In 1 (e.g. 20:50)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = clockOut1Text,
                        onValueChange = { clockOut1Text = it },
                        label = { Text("Out 1 (e.g. 01:00)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clockIn2Text,
                        onValueChange = { clockIn2Text = it },
                        label = { Text("In 2 (e.g. 01:30)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = clockOut2Text,
                        onValueChange = { clockOut2Text = it },
                        label = { Text("Out 2 (e.g. 05:20)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (clockIn1Text.isNotBlank() && clockOut1Text.isNotBlank()) {
                        onLog(
                            dateText,
                            clockIn1Text.trim(),
                            clockOut1Text.trim(),
                            clockIn2Text.trim(),
                            clockOut2Text.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Save Shift", color = Color.White, fontWeight = FontWeight.Bold)
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
                    Text("OK", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
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
                    selectedDayContainerColor = Color(0xFF007AFF),
                    selectedDayContentColor = Color.White
                )
            )
        }
    }
}

@Composable
fun ArchivedCyclesDialog(
    cycles: List<ArchivedPayCycle>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Archived Pay Periods", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (cycles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No closed pay periods archived yet.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(cycles, key = { "cycle_" + it.id }) { cycle ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(cycle.periodRange, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${String.format(Locale.US, "%.1f", cycle.totalHours)} Hours", color = Color.Gray, fontSize = 12.sp)
                                        Text("Net: $${String.format(Locale.US, "%.2f", cycle.netTakeHome)}", color = Color(0xFF34C759), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF0A84FF), fontWeight = FontWeight.Bold) }
        }
    )
}
