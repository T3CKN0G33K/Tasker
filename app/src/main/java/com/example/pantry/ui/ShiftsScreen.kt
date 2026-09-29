package com.example.pantry.ui

import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PunchClock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liquidglass.ChromaticDispersionPresets
import com.example.liquidglass.LiquidGlassListItem
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

fun parseShiftDateMillis(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    val trimmed = dateStr.trim()
    return runCatching {
        // Try YYYY-MM-DD
        val sdfIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdfIso.parse(trimmed)?.time
    }.getOrNull() ?: runCatching {
        // Try EEE, MMM dd, yyyy
        val sdfFull = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US)
        sdfFull.parse(trimmed)?.time
    }.getOrNull() ?: runCatching {
        // Try EEE, MMM dd
        val sdfShort = SimpleDateFormat("EEE, MMM dd", Locale.US)
        val parsed = sdfShort.parse(trimmed)
        if (parsed != null) {
            val cal = Calendar.getInstance().apply {
                val currentYear = get(Calendar.YEAR)
                time = parsed
                set(Calendar.YEAR, currentYear)
            }
            cal.timeInMillis
        } else 0L
    }.getOrDefault(0L)
}

fun parsePunchTimeSafely(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return "09:00 PM"
    return runCatching {
        val trimmed = timeStr.trim().uppercase(Locale.US)
        if (trimmed.contains("AM") || trimmed.contains("PM")) {
            trimmed
        } else {
            val parts = trimmed.split(":")
            var hour = parts[0].toInt()
            val min = if (parts.size > 1) parts[1].toInt() else 0
            val isPm = hour >= 12
            if (hour > 12) hour -= 12
            if (hour == 0) hour = 12
            String.format(Locale.US, "%02d:%02d %s", hour, min, if (isPm) "PM" else "AM")
        }
    }.getOrDefault("09:00 PM")
}

data class ShiftPunch(
    val type: String = "IN", // "IN", "MEAL_OUT", "MEAL_IN", "OUT"
    val time: String = "09:00 PM"
)

data class WorkShiftDay(
    val id: String = "",
    val dateLabel: String = "2026-09-22",
    val punches: List<ShiftPunch> = listOf(
        ShiftPunch("IN", "09:01 PM"),
        ShiftPunch("MEAL_OUT", "01:00 AM"),
        ShiftPunch("MEAL_IN", "01:30 AM"),
        ShiftPunch("OUT", "05:24 AM")
    ),
    val regularHours: Double = 8.0,
    val overtimeHours: Double = 0.1,
    val hourlyRate: Double = 20.16,
    val source: String = "Personal observation",
    val status: String = "UNVERIFIED",
    val notes: String? = null
) {
    val totalHours: Double get() = regularHours + overtimeHours
    val grossEarnings: Double get() = (regularHours * hourlyRate) + (overtimeHours * hourlyRate * 1.5)
}

data class PersonalPunchRecord(
    val id: String = "",
    val shiftId: String? = null,
    val type: String = "IN", // "IN", "OUT", "MEAL_OUT", "MEAL_IN"
    val dateLabel: String = "2026-09-24",
    val timeLabel: String = "09:00 PM",
    val source: String = "Personal observation",
    val notes: String? = null,
    val status: String = "UNVERIFIED",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val typeLabel: String get() = when (type.uppercase()) {
        "IN" -> "Clock In"
        "OUT" -> "Clock Out"
        "MEAL_OUT" -> "Meal Start"
        "MEAL_IN" -> "Meal End"
        else -> type
    }
}

data class OfficialTimecardRecord(
    val id: String = "",
    val periodStart: String = "2026-09-21",
    val periodEnd: String = "2026-10-04",
    val officialRegularHours: Double? = 80.0,
    val officialOvertimeHours: Double? = 0.4,
    val officialPaidHours: Double? = 80.4,
    val officialGrossPay: Double? = 1620.86,
    val notes: String? = null,
    val status: String = "Needs review",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ArchivedPayCycle(
    val id: String = "",
    val periodRange: String,
    val totalHours: Double,
    val grossPay: Double,
    val netTakeHome: Double,
    val archivedAt: Long = System.currentTimeMillis()
)

val defaultWorkShifts = listOf(
    WorkShiftDay("ws1", "2026-09-24", listOf(ShiftPunch("IN", "09:00 PM"), ShiftPunch("MEAL_OUT", "01:00 AM"), ShiftPunch("MEAL_IN", "01:30 AM"), ShiftPunch("OUT", "05:30 AM")), 8.0, 0.0, 20.16, "Personal observation", "UNVERIFIED"),
    WorkShiftDay("ws2", "2026-09-23", listOf(ShiftPunch("IN", "08:50 PM"), ShiftPunch("MEAL_OUT", "01:00 AM"), ShiftPunch("MEAL_IN", "01:30 AM"), ShiftPunch("OUT", "05:20 AM")), 8.0, 0.0, 20.16, "Personal observation", "UNVERIFIED"),
    WorkShiftDay("ws3", "2026-09-22", listOf(ShiftPunch("IN", "09:01 PM"), ShiftPunch("MEAL_OUT", "01:00 AM"), ShiftPunch("MEAL_IN", "01:30 AM"), ShiftPunch("OUT", "05:24 AM")), 8.0, 0.1, 20.16, "Personal observation", "UNVERIFIED"),
    WorkShiftDay("ws4", "2026-09-21", listOf(ShiftPunch("IN", "09:00 PM"), ShiftPunch("MEAL_OUT", "01:00 AM"), ShiftPunch("MEAL_IN", "01:30 AM"), ShiftPunch("OUT", "05:30 AM")), 8.0, 0.0, 20.16, "Personal observation", "UNVERIFIED"),
    WorkShiftDay("ws5", "2026-09-20", emptyList(), 0.0, 0.0, 20.16, "Personal observation", "UNVERIFIED")
)

val defaultPersonalPunches = listOf(
    PersonalPunchRecord("p1", "ws1", "IN", "2026-09-24", "09:00 PM", "Personal observation", "On-time arrival"),
    PersonalPunchRecord("p2", "ws1", "MEAL_OUT", "2026-09-25", "01:00 AM", "Personal observation"),
    PersonalPunchRecord("p3", "ws1", "MEAL_IN", "2026-09-25", "01:30 AM", "Personal observation"),
    PersonalPunchRecord("p4", "ws1", "OUT", "2026-09-25", "05:30 AM", "Personal observation")
)

val defaultOfficialTimecards = listOf(
    OfficialTimecardRecord(
        id = "ot1",
        periodStart = "2026-09-21",
        periodEnd = "2026-10-04",
        officialRegularHours = 80.0,
        officialOvertimeHours = 0.4,
        officialPaidHours = 80.4,
        officialGrossPay = 1620.86,
        notes = "Recorded from Workforce pay stub",
        status = "Needs review"
    )
)

@Composable
fun ShiftsScreen(
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT"
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current

    var shifts by remember { mutableStateOf<List<WorkShiftDay>>(emptyList()) }
    var personalPunches by remember { mutableStateOf<List<PersonalPunchRecord>>(emptyList()) }
    var officialTimecards by remember { mutableStateOf<List<OfficialTimecardRecord>>(emptyList()) }
    var archivedPeriods by remember { mutableStateOf<List<ArchivedPayCycle>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var isRecordingActive by remember { mutableStateOf(false) }
    var activeDialogStep by remember { mutableIntStateOf(0) } // 0 = None, 1 = Selection Dialog, 2 = Add/Edit Full Shift, 3 = Add/Edit Punch, 4 = Official Timecard
    var editingShift by remember { mutableStateOf<WorkShiftDay?>(null) }
    var editingPunch by remember { mutableStateOf<PersonalPunchRecord?>(null) }
    var editingOfficialTimecard by remember { mutableStateOf<OfficialTimecardRecord?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    val activePeriodRange = "Sep 21 – Oct 04, 2026"

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

    // Real-time Firestore Sync for Work Shifts, Personal Punches, Official Timecards & Archived Pay Cycles
    LaunchedEffect(currentUser.householdId, isPreview) {
        if (isPreview) {
            isLoading = false
            shifts = defaultWorkShifts.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
            personalPunches = defaultPersonalPunches.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
            officialTimecards = defaultOfficialTimecards.sortedByDescending { parseShiftDateMillis(it.periodStart) }
            return@LaunchedEffect
        }

        try {
            val db = Firebase.firestore
            currentUser.householdId?.let { hid ->
                db.collection("households").document(hid).collection("work_shifts")
                    .addSnapshotListener { snapshot, _ ->
                        isLoading = false
                        if (snapshot != null && !snapshot.isEmpty) {
                            val items = snapshot.documents.mapNotNull { doc ->
                                val punchesList = (doc.get("punches") as? List<Map<String, Any>>)?.map { p ->
                                    ShiftPunch(type = p["type"] as? String ?: "IN", time = p["time"] as? String ?: "")
                                } ?: listOf(ShiftPunch("IN", "09:00 PM"), ShiftPunch("OUT", "05:30 AM"))

                                WorkShiftDay(
                                    id = doc.id,
                                    dateLabel = doc.getString("dateLabel") ?: "Today",
                                    punches = punchesList,
                                    regularHours = doc.getDouble("regularHours") ?: 8.0,
                                    overtimeHours = doc.getDouble("overtimeHours") ?: 0.0,
                                    hourlyRate = doc.getDouble("hourlyRate") ?: 20.16,
                                    source = doc.getString("source") ?: "Personal observation",
                                    status = doc.getString("status") ?: "UNVERIFIED",
                                    notes = doc.getString("notes")
                                )
                            }
                            shifts = items.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                        } else if (shifts.isEmpty()) {
                            shifts = defaultWorkShifts.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                        }
                    }

                db.collection("households").document(hid).collection("work_punches")
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && !snapshot.isEmpty) {
                            val items = snapshot.documents.mapNotNull { doc ->
                                PersonalPunchRecord(
                                    id = doc.id,
                                    shiftId = doc.getString("shiftId"),
                                    type = doc.getString("type") ?: "IN",
                                    dateLabel = doc.getString("dateLabel") ?: "Today",
                                    timeLabel = doc.getString("timeLabel") ?: "09:00 PM",
                                    source = doc.getString("source") ?: "Personal observation",
                                    notes = doc.getString("notes"),
                                    status = doc.getString("status") ?: "UNVERIFIED",
                                    createdAt = doc.getLong("createdAt") ?: 0L,
                                    updatedAt = doc.getLong("updatedAt") ?: 0L
                                )
                            }
                            personalPunches = items.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                        } else if (personalPunches.isEmpty()) {
                            personalPunches = defaultPersonalPunches.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                        }
                    }

                db.collection("households").document(hid).collection("official_timecards")
                    .addSnapshotListener { snapshot, _ ->
                        if (snapshot != null && !snapshot.isEmpty) {
                            val items = snapshot.documents.mapNotNull { doc ->
                                OfficialTimecardRecord(
                                    id = doc.id,
                                    periodStart = doc.getString("periodStart") ?: "2026-09-21",
                                    periodEnd = doc.getString("periodEnd") ?: "2026-10-04",
                                    officialRegularHours = doc.getDouble("officialRegularHours"),
                                    officialOvertimeHours = doc.getDouble("officialOvertimeHours"),
                                    officialPaidHours = doc.getDouble("officialPaidHours"),
                                    officialGrossPay = doc.getDouble("officialGrossPay"),
                                    notes = doc.getString("notes"),
                                    status = doc.getString("status") ?: "Needs review",
                                    createdAt = doc.getLong("createdAt") ?: 0L,
                                    updatedAt = doc.getLong("updatedAt") ?: 0L
                                )
                            }
                            officialTimecards = items.sortedByDescending { parseShiftDateMillis(it.periodStart) }
                        } else if (officialTimecards.isEmpty()) {
                            officialTimecards = defaultOfficialTimecards.sortedByDescending { parseShiftDateMillis(it.periodStart) }
                        }
                    }

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
            } ?: run {
                isLoading = false
                if (shifts.isEmpty()) shifts = defaultWorkShifts.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                if (personalPunches.isEmpty()) personalPunches = defaultPersonalPunches.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                if (officialTimecards.isEmpty()) officialTimecards = defaultOfficialTimecards.sortedByDescending { parseShiftDateMillis(it.periodStart) }
            }
        } catch (_: Exception) {
            isLoading = false
            if (shifts.isEmpty()) shifts = defaultWorkShifts.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
            if (personalPunches.isEmpty()) personalPunches = defaultPersonalPunches.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
            if (officialTimecards.isEmpty()) officialTimecards = defaultOfficialTimecards.sortedByDescending { parseShiftDateMillis(it.periodStart) }
        }
    }

    // Payroll Calculations (Personal Estimate)
    val totalHours = remember(shifts) { shifts.sumOf { it.totalHours } }

    val grossPaycheck = remember(shifts) {
        shifts.sumOf { it.grossEarnings }
    }

    val oasdiTax = grossPaycheck * 0.062       // 6.2%
    val medicareTax = grossPaycheck * 0.0145    // 1.45%
    val federalTax = grossPaycheck * 0.085      // ~8.5%
    val maStateTax = grossPaycheck * 0.050      // 5.0%
    val fixedBenefits = 51.42                   // Homer Fund, STD/LTD

    val totalDeductions = oasdiTax + medicareTax + federalTax + maStateTax + fixedBenefits
    val netTakeHomePay = (grossPaycheck - totalDeductions).coerceAtLeast(0.0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 64.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Work Shifts",
                        color = textMain,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            try {
                                showHistoryDialog = true
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught crash on History icon", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .border(
                                width = 0.5.dp,
                                color = Color.White.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 1. Top Hero: "Active Shift & Punch Telemetry" (GlassCard)
            item {
                GlassCard(isDarkMode = isDarkMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isRecordingActive) Color(0xFF34C759) else Color(0xFFFF9500))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRecordingActive) "RECORDING ACTIVE" else "PERSONAL RECORD PAUSED",
                                    color = if (isRecordingActive) Color(0xFF34C759) else Color(0xFFFF9500),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Personal record for comparison", color = textSub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Column {
                            Text("Next Scheduled Shift", color = textSub, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "Thu, Sep 24 • 9:00 PM – 5:30 AM (8.0 hrs)",
                                color = textMain,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Last Personal Punch: Wed, Sep 23 at 8:50 PM",
                                color = Color.White.copy(alpha = 0.60f),
                                fontSize = 12.sp
                            )
                        }

                        // Primary Action Area with Subdued Disclosure
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GlassHeroButton(
                                onClick = {
                                    try {
                                        Log.d("SHIFTS_DEBUG", "Record Work Time clicked: opening WorkTimeSelectionDialog")
                                        isRecordingActive = !isRecordingActive
                                        activeDialogStep = 1 // Open WorkTimeSelectionDialog
                                    } catch (e: Exception) {
                                        Log.e("ShiftCrash", "Caught click crash on Record Work Time", e)
                                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.PunchClock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record Work Time", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }

                            Text(
                                text = "Personal record only — official punches are managed in Workforce.",
                                color = textSub,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 2. Time Off & Pay Period Balances (Horizontal Glass Pill Strip)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF34C759).copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Sick Balance", color = textSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("04:53 hrs", color = textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    GlassCard(
                        isDarkMode = isDarkMode,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(activeAccentColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.BeachAccess, contentDescription = null, tint = activeAccentColor, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Vacation", color = textSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("40:00 hrs", color = textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. Interactive Pay Period & Timecards Header
            item {
                GlassCard(isDarkMode = isDarkMode) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Personal Pay Estimate", color = textSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(activePeriodRange, color = textMain, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${String.format(Locale.US, "%.1f", totalHours)} / 80.0 hrs",
                                color = activeAccentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Progress Bar
                        LinearProgressIndicator(
                            progress = { (totalHours / 80.0).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = activeAccentColor,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.5.dp)

                        // Net Pay Preview
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Est. Take-Home Pay (Unverified)", color = textSub, fontSize = 11.sp)
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", netTakeHomePay)}",
                                    color = Color(0xFF34C759),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Est. Gross Pay", color = textSub, fontSize = 11.sp)
                                Text("$${String.format(Locale.US, "%.2f", grossPaycheck)}", color = textMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 4. Official Timecard Comparison Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Official Timecard Comparison",
                        color = textMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            try {
                                Log.d("SHIFTS_DEBUG", "Official '+' icon clicked: Opening EnterOfficialTimecardDialog")
                                editingOfficialTimecard = null
                                activeDialogStep = 4 // Open EnterOfficialTimecardDialog
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught click crash on Official '+' button", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Official Entry",
                            tint = activeAccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (officialTimecards.isEmpty()) {
                item {
                    Text("No official timecard values recorded for comparison.", color = textSub, fontSize = 14.sp)
                }
            } else {
                items(officialTimecards, key = { "ot_" + it.id }) { otRecord ->
                    val otPaid = otRecord.officialPaidHours
                    val hoursDiff = otPaid?.let { it - totalHours }

                    val badgeColor = when (otRecord.status) {
                        "Matches entered official values" -> Color(0xFF34C759)
                        "Needs review" -> Color(0xFFFF9500)
                        else -> Color.Gray
                    }

                    val subtitleStr = runCatching {
                        "Official Paid: ${if (otPaid != null) String.format(Locale.US, "%.1f hrs", otPaid) else "N/A"}${if (hoursDiff != null) " (${String.format(Locale.US, "%+.1f hrs diff", hoursDiff)})" else ""}"
                    }.getOrDefault("Official Paid: N/A")

                    LiquidGlassListItem(
                        title = "${otRecord.periodStart} – ${otRecord.periodEnd}",
                        subtitle = subtitleStr,
                        preset = ChromaticDispersionPresets.Standard,
                        isDarkMode = isDarkMode,
                        onClick = {
                            try {
                                Log.d("SHIFTS_DEBUG", "Editing Official Timecard: ${otRecord.id}")
                                editingOfficialTimecard = otRecord
                                activeDialogStep = 4
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught click crash on Official Timecard Comparison card", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PunchClock,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeColor.copy(alpha = 0.20f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(otRecord.status, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Edit Record",
                                    tint = textSub,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }

            item {
                Text(
                    text = "Personal Shift Summaries",
                    color = textMain,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = activeAccentColor)
                    }
                }
            } else {
                items(shifts, key = { "ws_" + it.id }) { shift ->
                    val isZeroHours = shift.totalHours <= 0.0

                    val subtitleStr = runCatching {
                        if (isZeroHours) "Off Day / Scheduled Rest" else "${String.format(Locale.US, "%.1f", shift.regularHours)} hrs Reg${if (shift.overtimeHours > 0) " • ${String.format(Locale.US, "%.1f", shift.overtimeHours)} hrs OT" else ""} (${shift.source})"
                    }.getOrDefault("Scheduled Shift")

                    val formattedEarnings = runCatching {
                        "$${String.format(Locale.US, "%.2f", shift.grossEarnings)}"
                    }.getOrDefault("$0.00")

                    LiquidGlassListItem(
                        title = shift.dateLabel,
                        subtitle = subtitleStr,
                        preset = ChromaticDispersionPresets.Standard,
                        isDarkMode = isDarkMode,
                        onClick = {
                            try {
                                val shiftId = shift.id
                                if (shiftId.isNotBlank()) {
                                    Log.d("SHIFTS_DEBUG", "Personal Shift Summary item clicked: $shiftId")
                                    editingShift = shift
                                    activeDialogStep = 2 // Open AddFullShiftDialog for editing
                                }
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught click crash on Personal Shift Summaries item", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isZeroHours) Color.White.copy(alpha = 0.08f) else activeAccentColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = if (isZeroHours) textSub else activeAccentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", shift.totalHours)} hrs",
                                    color = if (isZeroHours) textSub else textMain,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = formattedEarnings,
                                    color = if (isZeroHours) Color.Transparent else Color(0xFF34C759),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    )
                }
            }

            // Individual Personal Punches Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Individual Personal Punches",
                        color = textMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            try {
                                editingPunch = null
                                activeDialogStep = 3 // Open AddOrCorrectPunchDialog
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught click crash on Punch '+' button", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Punch",
                            tint = activeAccentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (personalPunches.isEmpty()) {
                item {
                    Text("No individual punch records logged.", color = textSub, fontSize = 14.sp)
                }
            } else {
                items(personalPunches, key = { "p_" + it.id }) { punch ->
                    LiquidGlassListItem(
                        title = "${punch.typeLabel} • ${punch.dateLabel} at ${punch.timeLabel}",
                        subtitle = "Source: ${punch.source}${punch.notes?.let { " • $it" } ?: ""}",
                        preset = ChromaticDispersionPresets.Standard,
                        isDarkMode = isDarkMode,
                        onClick = {
                            try {
                                Log.d("SHIFTS_DEBUG", "Editing Punch: ${punch.id}")
                                editingPunch = punch
                                activeDialogStep = 3
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught click crash on Punch item", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(activeAccentColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PunchClock,
                                    contentDescription = null,
                                    tint = activeAccentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(punch.status, color = textSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Edit Record",
                                    tint = textSub,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    // Consolidated Single Dialog Window Host
    if (activeDialogStep > 0) {
        Dialog(
            onDismissRequest = {
                activeDialogStep = 0
                editingShift = null
                editingPunch = null
                editingOfficialTimecard = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (activeDialogStep) {
                        1 -> WorkTimeSelectionDialog(
                            themePreference = currentUser.themePreference,
                            onDismiss = { activeDialogStep = 0 },
                            onSelectOption = { optionIndex ->
                                try {
                                    Log.d("SHIFTS_DEBUG", "Selected Option $optionIndex in WorkTimeSelectionDialog")
                                    editingShift = null
                                    editingPunch = null
                                    editingOfficialTimecard = null
                                    activeDialogStep = optionIndex + 2
                                } catch (e: Exception) {
                                    Log.e("ShiftCrash", "Caught click crash on Option $optionIndex", e)
                                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                        2 -> AddFullShiftDialog(
                            currentUser = currentUser,
                            editingShift = editingShift,
                            themePreference = currentUser.themePreference,
                            onDismiss = {
                                editingShift = null
                                activeDialogStep = 0
                            },
                            onSaveSuccess = { newShift ->
                                shifts = (listOf(newShift) + shifts.filter { it.id != newShift.id }).sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                                editingShift = null
                                activeDialogStep = 0
                            },
                            onDeleteSuccess = { deletedShiftId ->
                                shifts = shifts.filter { it.id != deletedShiftId }.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                                editingShift = null
                                activeDialogStep = 0
                            }
                        )
                        3 -> AddOrCorrectPunchDialog(
                            currentUser = currentUser,
                            editingPunch = editingPunch,
                            existingShifts = shifts,
                            themePreference = currentUser.themePreference,
                            onDismiss = {
                                editingPunch = null
                                activeDialogStep = 0
                            },
                            onSaveSuccess = { updatedPunch ->
                                personalPunches = (listOf(updatedPunch) + personalPunches.filter { it.id != updatedPunch.id }).sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                                editingPunch = null
                                activeDialogStep = 0
                            },
                            onDeleteSuccess = { deletedPunchId ->
                                personalPunches = personalPunches.filter { it.id != deletedPunchId }.sortedByDescending { parseShiftDateMillis(it.dateLabel) }
                                editingPunch = null
                                activeDialogStep = 0
                            }
                        )
                        4 -> EnterOfficialTimecardDialog(
                            currentUser = currentUser,
                            editingRecord = editingOfficialTimecard,
                            personalTotalHours = totalHours,
                            personalGrossPay = grossPaycheck,
                            themePreference = currentUser.themePreference,
                            onDismiss = {
                                editingOfficialTimecard = null
                                activeDialogStep = 0
                            },
                            onSaveSuccess = { updatedRecord ->
                                officialTimecards = (listOf(updatedRecord) + officialTimecards.filter { it.id != updatedRecord.id }).sortedByDescending { parseShiftDateMillis(it.periodStart) }
                                editingOfficialTimecard = null
                                activeDialogStep = 0
                            },
                            onDeleteSuccess = { deletedId ->
                                officialTimecards = officialTimecards.filter { it.id != deletedId }.sortedByDescending { parseShiftDateMillis(it.periodStart) }
                                editingOfficialTimecard = null
                                activeDialogStep = 0
                            }
                        )
                    }
                }
            }
        }
    }

    // Archived Pay Cycles History Dialog
    if (showHistoryDialog) {
        ArchivedPayHistoryDialog(
            archivedPeriods = archivedPeriods,
            themePreference = currentUser.themePreference,
            onDismiss = { showHistoryDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// DIALOG 1: Selection Modal for Personal Time Recording Workflows
// ---------------------------------------------------------------------------

@Composable
fun WorkTimeSelectionDialog(
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSelectOption: (Int) -> Unit
) {
    val context = LocalContext.current
    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record Work Time",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Selection Action Cards
            val options = listOf(
                "Add Full Shift" to "Record start, end, optional meal break, rate, and notes.",
                "Add or Correct a Punch" to "Record one clock-in, meal, or clock-out timestamp.",
                "Enter Official Timecard Values" to "Manually record values from the official app for personal comparison."
            )

            options.forEachIndexed { index, (title, subtitle) ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f),
                                    Color.White.copy(alpha = 0.08f)
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            try {
                                onSelectOption(index)
                            } catch (e: Exception) {
                                Log.e("ShiftCrash", "Caught click crash on Record Work Time Option $index", e)
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = activeAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Personal record only — this does not affect your employer timecard.",
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DIALOG 2: Add Full Shift Form Dialog
// ---------------------------------------------------------------------------

data class CalculatedShiftSummary(
    val spanHours: Double,
    val mealHours: Double,
    val workedHours: Double,
    val estimatedGrossPay: Double?,
    val isOvernight: Boolean,
    val errorMessage: String?
)

fun parseTimeToMinutes(timeStr: String?): Int? {
    if (timeStr.isNullOrBlank()) return null
    return try {
        val trimmed = timeStr.trim().uppercase(Locale.US)
        val isPm = trimmed.contains("PM")
        val isAm = trimmed.contains("AM")
        val clean = trimmed.replace("PM", "").replace("AM", "").trim()
        val parts = clean.split(":")
        var hour = parts[0].trim().toInt()
        val minute = if (parts.size > 1) parts[1].trim().toInt() else 0
        if (isPm && hour < 12) hour += 12
        if (isAm && hour == 12) hour = 0
        hour * 60 + minute
    } catch (_: Exception) {
        null
    }
}

fun calculateShiftSummary(
    clockInStr: String,
    clockOutStr: String,
    mealStartStr: String,
    mealEndStr: String,
    wageStr: String
): CalculatedShiftSummary {
    val startMin = parseTimeToMinutes(clockInStr)
    val endMin = parseTimeToMinutes(clockOutStr)

    if (startMin == null || endMin == null) {
        return CalculatedShiftSummary(0.0, 0.0, 0.0, null, false, "Please enter valid clock-in and clock-out times.")
    }

    val isOvernight = endMin <= startMin
    val endSpanMinutes = if (isOvernight) (endMin + 24 * 60 - startMin) else (endMin - startMin)
    val spanHours = (endSpanMinutes / 60.0).coerceAtLeast(0.0)

    val mStartMin = parseTimeToMinutes(mealStartStr)
    val mEndMin = parseTimeToMinutes(mealEndStr)

    val hasMealStart = !mealStartStr.isBlank()
    val hasMealEnd = !mealEndStr.isBlank()

    if (hasMealStart != hasMealEnd) {
        return CalculatedShiftSummary(spanHours, 0.0, spanHours, null, isOvernight, "Both meal start and meal end are required if recording a meal break.")
    }

    var mealHours = 0.0
    if (hasMealStart && mStartMin != null && mEndMin != null) {
        val mStartOffset = if (mStartMin >= startMin) mStartMin - startMin else mStartMin + 24 * 60 - startMin
        val mEndOffset = if (mEndMin >= startMin) mEndMin - startMin else mEndMin + 24 * 60 - startMin

        if (mEndOffset <= mStartOffset) {
            return CalculatedShiftSummary(spanHours, 0.0, spanHours, null, isOvernight, "Meal end must be after meal start within the shift window.")
        }
        if (mStartOffset < 0 || mEndOffset > endSpanMinutes) {
            return CalculatedShiftSummary(spanHours, 0.0, spanHours, null, isOvernight, "Meal break must fall within the shift window.")
        }

        mealHours = (mEndOffset - mStartOffset) / 60.0
    }

    val workedHours = (spanHours - mealHours).coerceAtLeast(0.0)
    val wage = wageStr.toDoubleOrNull()
    val estimatedGrossPay = wage?.let { (workedHours.coerceAtMost(8.0) * it) + ((workedHours - 8.0).coerceAtLeast(0.0) * it * 1.5) }

    return CalculatedShiftSummary(
        spanHours = spanHours,
        mealHours = mealHours,
        workedHours = workedHours,
        estimatedGrossPay = estimatedGrossPay,
        isOvernight = isOvernight,
        errorMessage = null
    )
}

@Composable
fun AddFullShiftDialog(
    currentUser: UserProfile,
    editingShift: WorkShiftDay? = null,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSaveSuccess: (WorkShiftDay) -> Unit,
    onDeleteSuccess: (String) -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current

    var dateStr by remember { mutableStateOf(editingShift?.dateLabel ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var clockIn by remember { mutableStateOf(parsePunchTimeSafely(editingShift?.punches?.firstOrNull { it.type == "IN" }?.time ?: "09:00 PM")) }
    var clockOut by remember { mutableStateOf(parsePunchTimeSafely(editingShift?.punches?.lastOrNull { it.type == "OUT" }?.time ?: "05:30 AM")) }
    var mealStart by remember { mutableStateOf(parsePunchTimeSafely(editingShift?.punches?.firstOrNull { it.type == "MEAL_OUT" }?.time ?: "01:00 AM")) }
    var mealEnd by remember { mutableStateOf(parsePunchTimeSafely(editingShift?.punches?.firstOrNull { it.type == "MEAL_IN" }?.time ?: "01:30 AM")) }
    var hourlyWageText by remember { mutableStateOf(editingShift?.hourlyRate?.toString() ?: "20.16") }
    var notesText by remember { mutableStateOf(editingShift?.notes ?: "") }
    var selectedSource by remember { mutableStateOf(editingShift?.source ?: "Personal observation") }

    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val sources = listOf("Personal observation", "Official QR punch observed", "Corrected estimate")

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    val summary = remember(clockIn, clockOut, mealStart, mealEnd, hourlyWageText) {
        calculateShiftSummary(clockIn, clockOut, mealStart, mealEnd, hourlyWageText)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.94f)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingShift != null) "Edit Full Shift (Personal)" else "Add Full Shift (Personal)",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    enabled = !isSaving && !isDeleting,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
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
                value = dateStr,
                onValueChange = { dateStr = it },
                label = { Text("Shift Date (YYYY-MM-DD)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = clockIn,
                    onValueChange = { clockIn = it },
                    label = { Text("Clock In", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = clockOut,
                    onValueChange = { clockOut = it },
                    label = { Text("Clock Out", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = mealStart,
                    onValueChange = { mealStart = it },
                    label = { Text("Meal Start (Optional)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = mealEnd,
                    onValueChange = { mealEnd = it },
                    label = { Text("Meal End (Optional)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = hourlyWageText,
                    onValueChange = { hourlyWageText = it },
                    label = { Text("Hourly Wage ($)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (Optional)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            // Source Selector
            Text("Source", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sources.forEach { src ->
                        val isSelected = selectedSource == src
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) activeAccentColor else Color.Transparent)
                                .clickable { selectedSource = src },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = src,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Live Calculated Summary Card & Overnight Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Summary", color = Color.White.copy(alpha = 0.70f), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        if (summary.isOvernight) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF007AFF).copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("+1 Day Overnight", color = Color(0xFF007AFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Span: ${String.format(Locale.US, "%.1f", summary.spanHours)} hrs • Meal: ${String.format(Locale.US, "%.1f", summary.mealHours)} hrs",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Worked: ${String.format(Locale.US, "%.1f", summary.workedHours)} hrs",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (summary.estimatedGrossPay != null) {
                        Text(
                            text = "Estimated Gross: $${String.format(Locale.US, "%.2f", summary.estimatedGrossPay)} (Estimated)",
                            color = Color(0xFF34C759),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Field Level Validation Error
            if (summary.errorMessage != null) {
                Text(
                    text = summary.errorMessage,
                    color = Color(0xFFFF3B30),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (saveErrorMessage != null) {
                Text(
                    text = saveErrorMessage!!,
                    color = Color(0xFFFF3B30),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingShift != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isSaving && !isDeleting,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete Shift", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GlassHeroButton(
                    onClick = {
                        try {
                            if (summary.errorMessage != null || isSaving) return@GlassHeroButton
                            isSaving = true
                            saveErrorMessage = null

                            val rate = hourlyWageText.toDoubleOrNull() ?: 20.16
                            val regHours = summary.workedHours.coerceAtMost(8.0)
                            val otHours = (summary.workedHours - 8.0).coerceAtLeast(0.0)

                            val punches = listOf(
                                ShiftPunch("IN", clockIn.trim()),
                                if (mealStart.isNotBlank()) ShiftPunch("MEAL_OUT", mealStart.trim()) else null,
                                if (mealEnd.isNotBlank()) ShiftPunch("MEAL_IN", mealEnd.trim()) else null,
                                ShiftPunch("OUT", clockOut.trim())
                            ).filterNotNull()

                            val docId = editingShift?.id ?: UUID.randomUUID().toString()
                            val newShift = WorkShiftDay(
                                id = docId,
                                dateLabel = dateStr.trim(),
                                punches = punches,
                                regularHours = regHours,
                                overtimeHours = otHours,
                                hourlyRate = rate,
                                source = selectedSource,
                                status = "UNVERIFIED",
                                notes = notesText.trim().ifBlank { null }
                            )

                            if (isPreview) {
                                isSaving = false
                                onSaveSuccess(newShift)
                                return@GlassHeroButton
                            }

                            val db = Firebase.firestore
                            currentUser.householdId?.let { hid ->
                                val docRef = db.collection("households").document(hid).collection("work_shifts").document(docId)
                                val shiftData = hashMapOf(
                                    "id" to docId,
                                    "dateLabel" to newShift.dateLabel,
                                    "shiftStart" to clockIn.trim(),
                                    "shiftEnd" to clockOut.trim(),
                                    "mealStart" to mealStart.trim().ifBlank { null },
                                    "mealEnd" to mealEnd.trim().ifBlank { null },
                                    "regularHours" to regHours,
                                    "overtimeHours" to otHours,
                                    "hourlyRate" to rate,
                                    "source" to selectedSource,
                                    "status" to "UNVERIFIED",
                                    "notes" to notesText.trim().ifBlank { null },
                                    "createdAt" to (editingShift?.id?.let { System.currentTimeMillis() } ?: System.currentTimeMillis()),
                                    "updatedAt" to System.currentTimeMillis(),
                                    "createdUserId" to currentUser.uid,
                                    "householdId" to hid,
                                    "punches" to punches.map { mapOf("type" to it.type, "time" to it.time) }
                                )

                                docRef.set(shiftData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        isSaving = false
                                        onSaveSuccess(newShift)
                                    }
                                    .addOnFailureListener { e ->
                                        isSaving = false
                                        saveErrorMessage = "Save failed: ${e.message ?: "Unknown error"}. Values preserved."
                                    }
                            } ?: run {
                                isSaving = false
                                onSaveSuccess(newShift)
                            }
                        } catch (e: Exception) {
                            isSaving = false
                            Log.e("ShiftCrash", "Caught save crash on Save Personal Shift", e)
                            Toast.makeText(context, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Personal Shift", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "Personal record only — this does not affect your employer timecard.",
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Delete Confirmation Dialog for Shift
    if (showDeleteConfirm && editingShift != null) {
        ConfirmDeleteShiftDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                showDeleteConfirm = false
                isDeleting = true

                if (isPreview) {
                    isDeleting = false
                    onDeleteSuccess(editingShift.id)
                    return@ConfirmDeleteShiftDialog
                }

                try {
                    val db = Firebase.firestore
                    currentUser.householdId?.let { hid ->
                        db.collection("households").document(hid)
                            .collection("work_shifts").document(editingShift.id)
                            .delete()
                            .addOnSuccessListener {
                                isDeleting = false
                                onDeleteSuccess(editingShift.id)
                            }
                            .addOnFailureListener { e ->
                                isDeleting = false
                                saveErrorMessage = "Delete failed: ${e.message ?: "Unknown error"}"
                            }
                    } ?: run {
                        isDeleting = false
                        onDeleteSuccess(editingShift.id)
                    }
                } catch (e: Exception) {
                    isDeleting = false
                    saveErrorMessage = "Delete failed: ${e.message ?: "Unknown error"}"
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// CONFIRM DELETE SHIFT DIALOG
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDeleteShiftDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
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
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161B22).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Delete Personal Shift?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Are you sure you want to delete this personal shift record? This action cannot be undone.",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirmDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DIALOG 3: Add or Correct a Punch Form Dialog
// ---------------------------------------------------------------------------

@Composable
fun AddOrCorrectPunchDialog(
    currentUser: UserProfile,
    editingPunch: PersonalPunchRecord? = null,
    existingShifts: List<WorkShiftDay> = emptyList(),
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSaveSuccess: (PersonalPunchRecord) -> Unit,
    onDeleteSuccess: (String) -> Unit
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current

    val types = listOf(
        "IN" to "Clock In",
        "MEAL_OUT" to "Meal Start",
        "MEAL_IN" to "Meal End",
        "OUT" to "Clock Out"
    )

    val sources = listOf(
        "Personal observation",
        "Official QR punch observed",
        "Official timecard value",
        "Corrected estimate"
    )

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    var selectedType by remember { mutableStateOf(editingPunch?.type ?: "IN") }
    var dateLabel by remember { mutableStateOf(editingPunch?.dateLabel ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var timeLabel by remember { mutableStateOf(parsePunchTimeSafely(editingPunch?.timeLabel)) }
    var selectedShiftId by remember { mutableStateOf(editingPunch?.shiftId ?: "") }
    var selectedSource by remember { mutableStateOf(editingPunch?.source ?: "Personal observation") }
    var notesText by remember { mutableStateOf(editingPunch?.notes ?: "") }

    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.94f)
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
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingPunch != null) "Edit Personal Punch" else "Add or Correct a Punch",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    enabled = !isSaving && !isDeleting,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
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

            // 1. Punch Type Selector
            Text("Punch Type", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.forEach { (typeCode, label) ->
                        val isSelected = selectedType == typeCode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) activeAccentColor else Color.Transparent)
                                .clickable { selectedType = typeCode },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 2. Date & Time Inputs
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = { dateLabel = it },
                    label = { Text("Date (YYYY-MM-DD)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it },
                    label = { Text("Time (e.g. 09:00 PM)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            // 3. Optional Link to Personal Shift
            Text("Link to Personal Shift (Optional)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val shiftOptions = listOf("" to "Standalone") + existingShifts.take(3).map { it.id to it.dateLabel }
                    shiftOptions.forEach { (shiftId, label) ->
                        val isSelected = selectedShiftId == shiftId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) activeAccentColor else Color.Transparent)
                                .clickable { selectedShiftId = shiftId },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 4. Source Selector
            Text("Source", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sources.take(2).forEach { src ->
                            val isSelected = selectedSource == src
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeAccentColor else Color.Transparent)
                                    .clickable { selectedSource = src },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = src,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        sources.drop(2).forEach { src ->
                            val isSelected = selectedSource == src
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeAccentColor else Color.Transparent)
                                    .clickable { selectedSource = src },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = src,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 5. Optional Note
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                placeholder = { Text("Notes (e.g. Corrected clock-in time)", color = Color.White.copy(alpha = 0.60f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            if (saveErrorMessage != null) {
                Text(
                    text = saveErrorMessage!!,
                    color = Color(0xFFFF3B30),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingPunch != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isSaving && !isDeleting,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete Punch", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GlassHeroButton(
                    onClick = {
                        try {
                            if (dateLabel.isBlank() || timeLabel.isBlank() || isSaving) return@GlassHeroButton
                            isSaving = true
                            saveErrorMessage = null

                            val safeTime = parsePunchTimeSafely(timeLabel)
                            val docId = editingPunch?.id ?: UUID.randomUUID().toString()
                            val newPunch = PersonalPunchRecord(
                                id = docId,
                                shiftId = selectedShiftId.ifBlank { null },
                                type = selectedType,
                                dateLabel = dateLabel.trim(),
                                timeLabel = safeTime,
                                source = selectedSource,
                                notes = notesText.trim().ifBlank { null },
                                status = "UNVERIFIED",
                                createdAt = editingPunch?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )

                            if (isPreview) {
                                isSaving = false
                                onSaveSuccess(newPunch)
                                return@GlassHeroButton
                            }

                            val db = Firebase.firestore
                            currentUser.householdId?.let { hid ->
                                val docRef = db.collection("households").document(hid).collection("work_punches").document(docId)
                                val punchData = hashMapOf(
                                    "id" to docId,
                                    "shiftId" to newPunch.shiftId,
                                    "type" to newPunch.type,
                                    "dateLabel" to newPunch.dateLabel,
                                    "timeLabel" to newPunch.timeLabel,
                                    "source" to newPunch.source,
                                    "notes" to newPunch.notes,
                                    "status" to "UNVERIFIED",
                                    "createdAt" to newPunch.createdAt,
                                    "updatedAt" to newPunch.updatedAt,
                                    "createdUserId" to currentUser.uid,
                                    "householdId" to hid
                                )

                                docRef.set(punchData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        isSaving = false
                                        onSaveSuccess(newPunch)
                                    }
                                    .addOnFailureListener { e ->
                                        isSaving = false
                                        saveErrorMessage = "Save failed: ${e.message ?: "Unknown error"}. Values preserved."
                                    }
                            } ?: run {
                                isSaving = false
                                onSaveSuccess(newPunch)
                            }
                        } catch (e: Exception) {
                            isSaving = false
                            Log.e("ShiftCrash", "Caught save crash on Save Personal Punch", e)
                            Toast.makeText(context, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Personal Punch", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(
                text = "Personal record only — this does not affect your employer timecard.",
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && editingPunch != null) {
        ConfirmDeletePunchDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                showDeleteConfirm = false
                isDeleting = true

                if (isPreview) {
                    isDeleting = false
                    onDeleteSuccess(editingPunch.id)
                    return@ConfirmDeletePunchDialog
                }

                try {
                    val db = Firebase.firestore
                    currentUser.householdId?.let { hid ->
                        db.collection("households").document(hid)
                            .collection("work_punches").document(editingPunch.id)
                            .delete()
                            .addOnSuccessListener {
                                isDeleting = false
                                onDeleteSuccess(editingPunch.id)
                            }
                            .addOnFailureListener { e ->
                                isDeleting = false
                                saveErrorMessage = "Delete failed: ${e.message ?: "Unknown error"}"
                            }
                    } ?: run {
                        isDeleting = false
                        onDeleteSuccess(editingPunch.id)
                    }
                } catch (e: Exception) {
                    isDeleting = false
                    saveErrorMessage = "Delete failed: ${e.message ?: "Unknown error"}"
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// CONFIRM DELETE DIALOG: Explicit Confirmation Modal
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDeletePunchDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
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
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161B22).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Delete Personal Punch?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Are you sure you want to delete this personal punch record? This action cannot be undone.",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirmDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DIALOG 4: Enter Official Timecard Values Dialog
// ---------------------------------------------------------------------------

@Composable
fun EnterOfficialTimecardDialog(
    currentUser: UserProfile,
    editingRecord: OfficialTimecardRecord? = null,
    personalTotalHours: Double = 0.0,
    personalGrossPay: Double = 0.0,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSaveSuccess: (OfficialTimecardRecord) -> Unit,
    onDeleteSuccess: (String) -> Unit
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    var periodStart by remember { mutableStateOf(editingRecord?.periodStart ?: "2026-09-21") }
    var periodEnd by remember { mutableStateOf(editingRecord?.periodEnd ?: "2026-10-04") }
    var regHoursText by remember { mutableStateOf(editingRecord?.officialRegularHours?.toString() ?: "80.0") }
    var otHoursText by remember { mutableStateOf(editingRecord?.officialOvertimeHours?.toString() ?: "0.4") }
    var paidHoursText by remember { mutableStateOf(editingRecord?.officialPaidHours?.toString() ?: "80.4") }
    var grossPayText by remember { mutableStateOf(editingRecord?.officialGrossPay?.toString() ?: "1620.86") }
    var notesText by remember { mutableStateOf(editingRecord?.notes ?: "") }

    var isSaving by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Live Comparison Calculation (Guarded against NullPointerException / NumberFormatException)
    val officialPaidVal = runCatching { paidHoursText.toDoubleOrNull() }.getOrNull()
    val officialGrossVal = runCatching { grossPayText.toDoubleOrNull() }.getOrNull()

    val hoursDiff = officialPaidVal?.let { it - personalTotalHours }
    val dollarDiff = officialGrossVal?.let { it - personalGrossPay }

    val comparisonStatus = when {
        officialPaidVal == null && officialGrossVal == null -> "Unverified"
        (hoursDiff != null && abs(hoursDiff) <= 0.01) &&
                (dollarDiff == null || abs(dollarDiff) <= 0.01) -> "Matches entered official values"
        else -> "Needs review"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.94f)
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
                    text = if (editingRecord != null) "Edit Official Timecard Values" else "Enter Official Timecard Values",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onDismiss,
                    enabled = !isSaving && !isDeleting,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = periodStart,
                    onValueChange = { periodStart = it },
                    label = { Text("Period Start", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = periodEnd,
                    onValueChange = { periodEnd = it },
                    label = { Text("Period End", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = regHoursText,
                    onValueChange = { regHoursText = it },
                    label = { Text("Official Reg Hrs", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
                OutlinedTextField(
                    value = otHoursText,
                    onValueChange = { otHoursText = it },
                    label = { Text("Official OT Hrs", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = paidHoursText,
                    onValueChange = { paidHoursText = it },
                    label = { Text("Official Paid Hrs", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
                OutlinedTextField(
                    value = grossPayText,
                    onValueChange = { grossPayText = it },
                    label = { Text("Official Gross ($)", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
            }

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                placeholder = { Text("Notes (e.g. Reference pay stub values)", color = Color.White.copy(alpha = 0.60f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            // Comparison Presentation Card with Dense Dark Plate
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Comparison Summary", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        val badgeColor = when (comparisonStatus) {
                            "Matches entered official values" -> Color(0xFF34C759)
                            "Needs review" -> Color(0xFFFF9500)
                            else -> Color.Gray
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(comparisonStatus, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Personal Recorded: ${String.format(Locale.US, "%.1f", personalTotalHours)} hrs",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Official Paid: ${if (officialPaidVal != null) String.format(Locale.US, "%.1f hrs", officialPaidVal) else "Not entered"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Est. Personal Gross: $${String.format(Locale.US, "%.2f", personalGrossPay)}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Official Gross: ${if (officialGrossVal != null) String.format(Locale.US, "$%.2f", officialGrossVal) else "Not entered"}",
                            color = Color(0xFF34C759),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (hoursDiff != null || dollarDiff != null) {
                        val hoursDiffStr = if (hoursDiff != null) {
                            val prefix = if (hoursDiff >= 0) "+" else ""
                            "$prefix${String.format(Locale.US, "%.1f hrs", hoursDiff)}"
                        } else "N/A"

                        val dollarDiffStr = if (dollarDiff != null) {
                            val prefix = if (dollarDiff >= 0) "+$" else "-$"
                            "$prefix${String.format(Locale.US, "%.2f", abs(dollarDiff))}"
                        } else "N/A"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Hours Diff: $hoursDiffStr",
                                color = if (hoursDiff != null && abs(hoursDiff) > 0.01) Color(0xFFFF9500) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Dollar Diff: $dollarDiffStr",
                                color = if (dollarDiff != null && abs(dollarDiff) > 0.01) Color(0xFFFF9500) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (saveErrorMessage != null) {
                Text(
                    text = saveErrorMessage!!,
                    color = Color(0xFFFF3B30),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Explanatory Disclosure
            Text(
                text = "Differences can result from rounding, meal rules, scheduling, or processing. Verify details in the official system.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingRecord != null) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !isSaving && !isDeleting,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Delete Record", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GlassHeroButton(
                    onClick = {
                        try {
                            if (periodStart.isBlank() || periodEnd.isBlank() || isSaving) return@GlassHeroButton
                            isSaving = true
                            saveErrorMessage = null

                            val docId = editingRecord?.id ?: UUID.randomUUID().toString()
                            val newRecord = OfficialTimecardRecord(
                                id = docId,
                                periodStart = periodStart.trim(),
                                periodEnd = periodEnd.trim(),
                                officialRegularHours = regHoursText.toDoubleOrNull(),
                                officialOvertimeHours = otHoursText.toDoubleOrNull(),
                                officialPaidHours = paidHoursText.toDoubleOrNull(),
                                officialGrossPay = grossPayText.toDoubleOrNull(),
                                notes = notesText.trim().ifBlank { null },
                                status = comparisonStatus,
                                createdAt = editingRecord?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )

                            if (isPreview) {
                                isSaving = false
                                onSaveSuccess(newRecord)
                                return@GlassHeroButton
                            }

                            val db = Firebase.firestore
                            currentUser.householdId?.let { hid ->
                                val docRef = db.collection("households").document(hid).collection("official_timecards").document(docId)
                                val cardData = hashMapOf(
                                    "id" to docId,
                                    "periodStart" to newRecord.periodStart,
                                    "periodEnd" to newRecord.periodEnd,
                                    "officialRegularHours" to newRecord.officialRegularHours,
                                    "officialOvertimeHours" to newRecord.officialOvertimeHours,
                                    "officialPaidHours" to newRecord.officialPaidHours,
                                    "officialGrossPay" to newRecord.officialGrossPay,
                                    "notes" to newRecord.notes,
                                    "status" to newRecord.status,
                                    "createdAt" to newRecord.createdAt,
                                    "updatedAt" to newRecord.updatedAt,
                                    "createdUserId" to currentUser.uid,
                                    "householdId" to hid
                                )

                                docRef.set(cardData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        isSaving = false
                                        onSaveSuccess(newRecord)
                                    }
                                    .addOnFailureListener { e ->
                                        isSaving = false
                                        saveErrorMessage = "Save failed: ${e.message ?: "Unknown error"}. Values preserved."
                                    }
                            } ?: run {
                                isSaving = false
                                onSaveSuccess(newRecord)
                            }
                        } catch (e: Exception) {
                            isSaving = false
                            Log.e("ShiftCrash", "Caught save crash on Save Official Timecard", e)
                            Toast.makeText(context, "Error saving: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save Official Timecard", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm && editingRecord != null) {
        ConfirmDeleteTimecardDialog(
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                showDeleteConfirm = false
                isDeleting = true

                if (isPreview) {
                    isDeleting = false
                    onDeleteSuccess(editingRecord.id)
                    return@ConfirmDeleteTimecardDialog
                }

                try {
                    val db = Firebase.firestore
                    currentUser.householdId?.let { hid ->
                        db.collection("households").document(hid)
                            .collection("official_timecards").document(editingRecord.id)
                            .delete()
                            .addOnSuccessListener {
                                isDeleting = false
                                onDeleteSuccess(editingRecord.id)
                            }
                            .addOnFailureListener { e ->
                                isDeleting = false
                                saveErrorMessage = "Delete failed: ${e.message ?: "Unknown error"}"
                            }
                    } ?: run {
                        isDeleting = false
                        onDeleteSuccess(editingRecord.id)
                    }
                } catch (e: Exception) {
                    isDeleting = false
                    saveErrorMessage = "Delete failed: ${e.message ?: "Unknown error"}"
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// CONFIRM DELETE TIMECARD DIALOG
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDeleteTimecardDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
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
                    .fillMaxWidth(0.85f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF161B22).copy(alpha = 0.96f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Delete Official Timecard Record?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Are you sure you want to delete this recorded official timecard entry? This action cannot be undone.",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirmDelete,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30))
                        ) {
                            Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DIALOG 5: Archived Pay Cycles History Dialog
// ---------------------------------------------------------------------------

@Composable
fun ArchivedPayHistoryDialog(
    archivedPeriods: List<ArchivedPayCycle>,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit
) {
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
                            text = "Archived Pay Cycles",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (archivedPeriods.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No archived pay periods yet.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(archivedPeriods, key = { it.id }) { cycle ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(cycle.periodRange, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${String.format(Locale.US, "%.1f", cycle.totalHours)} total hrs • Gross $${String.format(Locale.US, "%.2f", cycle.grossPay)}", color = Color.White.copy(alpha = 0.60f), fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "$${String.format(Locale.US, "%.2f", cycle.netTakeHome)}",
                                                color = Color(0xFF34C759),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text("Net Take-Home", color = Color(0xFF34C759), fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B132B)
@Composable
fun ShiftsScreenPreview() {
    MaterialTheme {
        ShiftsScreen(
            currentUser = UserProfile("preview_user", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_preview"),
            isDarkMode = true
        )
    }
}
