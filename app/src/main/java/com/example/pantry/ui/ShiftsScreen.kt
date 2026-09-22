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
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var currentShifts by remember { mutableStateOf<List<SplitShiftEntry>>(emptyList()) }
    var archivedPeriods by remember { mutableStateOf<List<ArchivedPayCycle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var showLogShiftDialog by remember { mutableStateOf(false) }
    var showArchivedHistoryDialog by remember { mutableStateOf(false) }

    val activePeriodRange = "Aug 10, 2026 - Aug 23, 2026"

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

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

    // Unblock Background -> Color.Transparent for edge-to-edge ambient theme gradient!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 64.dp, bottom = 140.dp, start = 20.dp, end = 20.dp),
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
                        color = textMain,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )

                    GlassStepperButton(
                        icon = Icons.Default.History,
                        contentDescription = "History",
                        onClick = { showArchivedHistoryDialog = true },
                        isPlus = false
                    )
                }
            }

            // Home Depot Pay Period & Net Pay Engine Card (Upgraded to GlassCard)
            item {
                GlassCard(isDarkMode = isDarkMode) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active 14-Day Pay Period", color = textSub, fontSize = 13.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF007AFF).copy(alpha = 0.25f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(activePeriodRange, color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Calculated Net Take-Home Pay", color = textSub, fontSize = 12.sp)
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", netTakeHomePay)}",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(color = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA), thickness = 0.5.dp)

                        // Payroll Breakdown Grid
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gross Pay (${String.format(Locale.US, "%.1f", totalShiftHours)} hrs @ $20.16/hr):", color = textSub, fontSize = 12.sp)
                                Text("$${String.format(Locale.US, "%.2f", grossPaycheck)}", color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("OASDI (6.2%) + Medicare (1.45%):", color = textSub, fontSize = 12.sp)
                                Text("-$${String.format(Locale.US, "%.2f", oasdiTax + medicareTax)}", color = Color(0xFFFF453A), fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Federal (~8.5%) + MA State (5.0%):", color = textSub, fontSize = 12.sp)
                                Text("-$${String.format(Locale.US, "%.2f", federalTax + maStateTax)}", color = Color(0xFFFF453A), fontSize = 12.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fixed Deductions (Power, STD/LTD, Homer):", color = textSub, fontSize = 12.sp)
                                Text("-$${String.format(Locale.US, "%.2f", fixedBenefits)}", color = Color(0xFFFF453A), fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassHeroButton(
                                onClick = { showLogShiftDialog = true },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Log Split Shift", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            GlassHeroButton(
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
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
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
                    color = textMain,
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
                    Text("No split shifts logged for this pay period.", color = textSub, fontSize = 14.sp)
                }
            } else {
                items(currentShifts, key = { "shift_" + it.id }) { shift ->
                    GlassCard(isDarkMode = isDarkMode) {
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
                                    .background(if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE5E5EA)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(shift.date, color = textMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("In1: ${shift.clockIn1} ➔ Out1: ${shift.clockOut1}", color = textSub, fontSize = 11.sp)
                                if (shift.clockIn2.isNotBlank() && shift.clockOut2.isNotBlank()) {
                                    Text("In2: ${shift.clockIn2} ➔ Out2: ${shift.clockOut2}", color = textSub, fontSize = 11.sp)
                                }
                                Text("${String.format(Locale.US, "%.1f", shift.totalHours)} Total Hours", color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", shift.grossEarnings)}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                TextButton(
                                    onClick = {
                                        currentUser.householdId?.let { hid ->
                                            db.collection("households").document(hid)
                                                .collection("split_shifts").document(shift.id)
                                                .delete()
                                        }
                                        currentShifts = currentShifts.filter { it.id != shift.id }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Delete", color = Color(0xFFFF3B30), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showLogShiftDialog) {
        LogSplitShiftDialog(
            onDismiss = { showLogShiftDialog = false },
            onLog = { newShift ->
                currentShifts = listOf(newShift) + currentShifts

                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("split_shifts").document()
                    val data = hashMapOf(
                        "date" to newShift.date,
                        "clockIn1" to newShift.clockIn1,
                        "clockOut1" to newShift.clockOut1,
                        "clockIn2" to newShift.clockIn2,
                        "clockOut2" to newShift.clockOut2,
                        "hourlyRate" to newShift.hourlyRate
                    )
                    docRef.set(data)
                }
                showLogShiftDialog = false
            }
        )
    }

    if (showArchivedHistoryDialog) {
        ArchivedPayHistoryDialog(
            archivedPeriods = archivedPeriods,
            onDismiss = { showArchivedHistoryDialog = false }
        )
    }
}

@Composable
fun LogSplitShiftDialog(
    onDismiss: () -> Unit,
    onLog: (SplitShiftEntry) -> Unit
) {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    var dateStr by remember { mutableStateOf(sdf.format(Date())) }

    var clockIn1 by remember { mutableStateOf("20:50") }
    var clockOut1 by remember { mutableStateOf("01:00") }
    var clockIn2 by remember { mutableStateOf("01:30") }
    var clockOut2 by remember { mutableStateOf("05:20") }
    var rateText by remember { mutableStateOf("20.16") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Log Home Depot Split Shift", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = { dateStr = it },
                    label = { Text("Shift Date", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )

                Text("LEG 1 (FIRST HALF)", color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clockIn1,
                        onValueChange = { clockIn1 = it },
                        label = { Text("Clock In 1 (20:50)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = clockOut1,
                        onValueChange = { clockOut1 = it },
                        label = { Text("Clock Out 1 (01:00)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                Text("LEG 2 (SECOND HALF)", color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = clockIn2,
                        onValueChange = { clockIn2 = it },
                        label = { Text("Clock In 2 (01:30)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = clockOut2,
                        onValueChange = { clockOut2 = it },
                        label = { Text("Clock Out 2 (05:20)", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }

                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Hourly Rate ($)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = rateText.toDoubleOrNull() ?: 20.16
                    if (clockIn1.isBlank() || clockOut1.isBlank()) {
                        Toast.makeText(context, "Please enter at least Leg 1 clock in/out times.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val newEntry = SplitShiftEntry(
                        id = UUID.randomUUID().toString(),
                        date = dateStr.trim(),
                        clockIn1 = clockIn1.trim(),
                        clockOut1 = clockOut1.trim(),
                        clockIn2 = clockIn2.trim(),
                        clockOut2 = clockOut2.trim(),
                        hourlyRate = rate
                    )
                    onLog(newEntry)
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
}

@Composable
fun ArchivedPayHistoryDialog(
    archivedPeriods: List<ArchivedPayCycle>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Archived Pay Periods", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            if (archivedPeriods.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No archived pay periods yet.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(archivedPeriods, key = { it.id }) { cycle ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2C2C2E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(cycle.periodRange, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${String.format(Locale.US, "%.1f", cycle.totalHours)} total hours • Gross: $${String.format(Locale.US, "%.2f", cycle.grossPay)}", color = Color.Gray, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", cycle.netTakeHome)}",
                                        color = Color(0xFF34C759),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text("Net Pay", color = Color(0xFF34C759), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) }
        }
    )
}
