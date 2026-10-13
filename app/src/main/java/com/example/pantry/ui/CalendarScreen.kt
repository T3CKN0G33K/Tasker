package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liquidglass.ChromaticDispersionPresets
import com.example.liquidglass.LiquidGlassListItem
import com.example.pantry.notifications.CalendarAlarmScheduler
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

fun parseEventDateTimeToEpoch(dateStr: String, timeStr: String): Long {
    return runCatching {
        val cleanTime = parsePunchTimeSafely(timeStr)
        val dateTimeStr = "${dateStr.trim()} $cleanTime"
        val sdf = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.US)
        sdf.parse(dateTimeStr)?.time ?: 0L
    }.getOrDefault(0L)
}

data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val date: String = "", // Format YYYY-MM-DD e.g. "2026-09-24"
    val time: String = "10:00 AM",
    val notes: String = "",
    val category: String = "Appointment"
)

val sampleCalendarEvents = listOf(
    CalendarEvent("e1", "Dentist Appointment", "2026-09-24", "10:00 AM", "Checkup & Cleaning", "Medical"),
    CalendarEvent("e2", "Grocery Restock Day", "2026-09-24", "04:30 PM", "Buy cat food & paper towels", "Household"),
    CalendarEvent("e3", "Car Maintenance", "2026-09-28", "09:00 AM", "Oil Change & Tire Rotation", "Personal")
)

@Composable
fun CalendarScreen(
    currentUser: UserProfile = UserProfile("123", "Thomas Barton", "thomas@example.com", Role.OWNER, "house_abc"),
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var calendarViewMode by remember { mutableStateOf("WEEK") } // "WEEK" vs "MONTH"

    // Calendar Date Calculation State
    val calendar = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) } // 0..11
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.US) }
    val monthTitle = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        monthFormat.format(cal.time)
    }

    val selectedDateString = remember(currentYear, currentMonth, selectedDay) {
        String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, selectedDay)
    }

    // Real-time Firestore Sync for Calendar Events
    LaunchedEffect(currentUser.householdId) {
        currentUser.householdId?.let { hid ->
            db.collection("households").document(hid).collection("events")
                .addSnapshotListener { snapshot, _ ->
                    isLoading = false
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { doc ->
                            CalendarEvent(
                                id = doc.id,
                                title = doc.getString("title") ?: "Event",
                                date = doc.getString("date") ?: selectedDateString,
                                time = doc.getString("time") ?: "10:00 AM",
                                notes = doc.getString("notes") ?: "",
                                category = doc.getString("category") ?: "Appointment"
                            )
                        }
                        events = items
                    } else if (events.isEmpty()) {
                        events = sampleCalendarEvents
                    }
                }
        } ?: run {
            isLoading = false
            if (events.isEmpty()) events = sampleCalendarEvents
        }
    }

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

    val selectedDayEvents = remember(events, selectedDateString) {
        events.filter { it.date == selectedDateString }
    }

    // Upcoming Focus Event Calculation
    val upcomingEvent = remember(events, selectedDateString) {
        events.filter { it.date >= selectedDateString }.minByOrNull { it.date + it.time }
    }

    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val firstDayOfWeekOffset = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sunday
    }

    // Week Ribbon Days Calculation (7 Days around selected day)
    val weekDays = remember(currentYear, currentMonth, selectedDay) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, selectedDay)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        cal.add(Calendar.DAY_OF_MONTH, -dayOfWeek) // Move to Sunday

        val days = mutableListOf<Triple<Int, Int, Int>>() // Year, Month, Day
        for (i in 0..6) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)
            days.add(Triple(y, m, d))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        days
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 64.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row with Circular Floating Glass "+" Icon
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calendar",
                        color = textMain,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { showAddEventDialog = true },
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
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Event",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Month Navigation Bar
            item {
                GlassCard(isDarkMode = isDarkMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (currentMonth == 0) {
                                currentMonth = 11
                                currentYear -= 1
                            } else {
                                currentMonth -= 1
                            }
                            selectedDay = 1
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = textMain)
                        }

                        Text(
                            text = monthTitle,
                            color = textMain,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    calendarViewMode = if (calendarViewMode == "WEEK") "MONTH" else "WEEK"
                                }
                            ) {
                                Text(
                                    text = if (calendarViewMode == "WEEK") "Month View" else "Week View",
                                    color = activeAccentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(onClick = {
                                if (currentMonth == 11) {
                                    currentMonth = 0
                                    currentYear += 1
                                } else {
                                    currentMonth += 1
                                }
                                selectedDay = 1
                            }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = textMain)
                            }
                        }
                    }
                }
            }

            // Interactive Week Ribbon vs Full Month Grid
            item {
                GlassCard(isDarkMode = isDarkMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (calendarViewMode == "WEEK") {
                            // Week Ribbon View (7 Days)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                                weekDays.forEachIndexed { idx, (y, m, d) ->
                                    val isSelected = y == currentYear && m == currentMonth && d == selectedDay
                                    val dayStr = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                    val hasEvent = events.any { it.date == dayStr }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) activeAccentColor else Color.Transparent)
                                            .clickable {
                                                currentYear = y
                                                currentMonth = m
                                                selectedDay = d
                                            }
                                            .padding(vertical = 8.dp, horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = daysOfWeek[idx],
                                            color = if (isSelected) Color.White else textSub,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = d.toString(),
                                            color = if (isSelected) Color.White else textMain,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (hasEvent) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else activeAccentColor)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Full Month Grid View
                            val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                daysOfWeek.forEach { dayName ->
                                    Text(
                                        text = dayName,
                                        color = textSub,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Calendar Days Cells
                            val totalSlots = daysInMonth + firstDayOfWeekOffset
                            val rows = (totalSlots + 6) / 7

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (r in 0 until rows) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        for (c in 0..6) {
                                            val dayNum = (r * 7 + c) - firstDayOfWeekOffset + 1
                                            if (dayNum in 1..daysInMonth) {
                                                val isSelected = dayNum == selectedDay
                                                val dayStr = String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, dayNum)
                                                val hasEvent = events.any { it.date == dayStr }

                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) activeAccentColor else Color.Transparent)
                                                        .clickable { selectedDay = dayNum },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text(
                                                            text = dayNum.toString(),
                                                            color = if (isSelected) Color.White else textMain,
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (hasEvent) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(4.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (isSelected) Color.White else activeAccentColor)
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top Hero Card: Upcoming Highlight Event
            upcomingEvent?.let { focusEvent ->
                item {
                    GlassCard(isDarkMode = isDarkMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(activeAccentColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "UPCOMING EVENT",
                                        color = activeAccentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(activeAccentColor.copy(alpha = 0.20f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(focusEvent.category, color = activeAccentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                text = focusEvent.title,
                                color = textMain,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = textSub, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(focusEvent.date, color = textSub, fontSize = 12.sp)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = textSub, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(focusEvent.time, color = textSub, fontSize = 12.sp)
                                }
                            }

                            if (focusEvent.notes.isNotBlank()) {
                                Text(
                                    text = focusEvent.notes,
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Events List Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Events for $selectedDateString",
                        color = textMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${selectedDayEvents.size} Scheduled",
                        color = activeAccentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Selected Day Events List
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = activeAccentColor)
                    }
                }
            } else if (selectedDayEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = textSub, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No events scheduled for this day.", color = textSub, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(selectedDayEvents, key = { it.id }) { eventItem ->
                    val badgeColor = when (eventItem.category.lowercase(Locale.US)) {
                        "medical" -> Color(0xFF34C759)
                        "household" -> Color(0xFFFF9500)
                        "personal" -> Color(0xFFAF52DE)
                        else -> activeAccentColor
                    }

                    LiquidGlassListItem(
                        title = eventItem.title,
                        subtitle = "${eventItem.time} • ${eventItem.category}${if (eventItem.notes.isNotBlank()) " • ${eventItem.notes}" else ""}",
                        preset = ChromaticDispersionPresets.Standard,
                        isDarkMode = isDarkMode,
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeColor.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (eventItem.category.lowercase(Locale.US)) {
                                        "medical" -> Icons.Default.MedicalServices
                                        "household" -> Icons.Default.Star
                                        "personal" -> Icons.Default.Person
                                        else -> Icons.Default.Event
                                    },
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    events = events.filter { it.id != eventItem.id }
                                    currentUser.householdId?.let { hid ->
                                        db.collection("households").document(hid)
                                            .collection("events").document(eventItem.id)
                                            .delete()
                                    }
                                    CalendarAlarmScheduler.cancelReminder(context, eventItem.id)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Event",
                                    tint = Color.White.copy(alpha = 0.60f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    // Schedule Event Dialog
    if (showAddEventDialog) {
        AddEventCenteredDialog(
            initialDate = selectedDateString,
            themePreference = currentUser.themePreference,
            onDismiss = { showAddEventDialog = false },
            onAdd = { newEvent ->
                events = events + newEvent
                currentUser.householdId?.let { hid ->
                    val docRef = db.collection("households").document(hid).collection("events").document(newEvent.id)
                    val data = hashMapOf(
                        "title" to newEvent.title,
                        "date" to newEvent.date,
                        "time" to newEvent.time,
                        "notes" to newEvent.notes,
                        "category" to newEvent.category
                    )
                    docRef.set(data, SetOptions.merge())
                }

                // Schedule exact alarm via AlarmManager
                val reminderMillis = parseEventDateTimeToEpoch(newEvent.date, newEvent.time)
                CalendarAlarmScheduler.scheduleReminder(
                    context = context,
                    eventId = newEvent.id,
                    eventTitle = newEvent.title,
                    eventTime = newEvent.time,
                    eventNotes = newEvent.notes,
                    reminderEpochMillis = reminderMillis
                )

                showAddEventDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventCenteredDialog(
    initialDate: String,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onAdd: (CalendarEvent) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(initialDate) }
    var timeText by remember { mutableStateOf("10:00 AM") }
    var category by remember { mutableStateOf("Appointment") }
    var notes by remember { mutableStateOf("") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val categories = listOf("Appointment", "Medical", "Household", "Personal")

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    val initialMillis = remember(initialDate) {
        runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(initialDate)?.time
        }.getOrNull() ?: System.currentTimeMillis()
    }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    val parsedTime = remember(timeText) {
        runCatching {
            val clean = parsePunchTimeSafely(timeText)
            val isPm = clean.contains("PM", ignoreCase = true)
            val parts = clean.replace("AM", "").replace("PM", "").trim().split(":")
            var h = parts[0].toInt()
            val m = if (parts.size > 1) parts[1].toInt() else 0
            if (isPm && h < 12) h += 12
            if (!isPm && h == 12) h = 0
            Pair(h, m)
        }.getOrDefault(Pair(10, 0))
    }

    val timePickerState = rememberTimePickerState(
        initialHour = parsedTime.first,
        initialMinute = parsedTime.second,
        is24Hour = false
    )

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
                            text = "Schedule Event",
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

                    val inputColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.10f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
                        focusedBorderColor = activeAccentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Event Title (e.g. Dentist, Flight)", color = Color.White.copy(alpha = 0.60f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = inputColors
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = dateText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Date", color = Color.White.copy(alpha = 0.70f)) },
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = activeAccentColor, modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showDatePicker = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = timeText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Time", color = Color.White.copy(alpha = 0.70f)) },
                                trailingIcon = {
                                    IconButton(onClick = { showTimePicker = true }) {
                                        Icon(Icons.Default.Schedule, contentDescription = "Select Time", tint = activeAccentColor, modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showTimePicker = true },
                                shape = RoundedCornerShape(14.dp),
                                colors = inputColors
                            )
                        }
                    }

                    Text("Category", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) activeAccentColor else Color.Transparent)
                                        .clickable { category = cat },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Notes / Description", color = Color.White.copy(alpha = 0.60f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = inputColors
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    GlassHeroButton(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Please enter an event title.", Toast.LENGTH_SHORT).show()
                                return@GlassHeroButton
                            }
                            val newEvent = CalendarEvent(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                date = dateText.trim().ifBlank { initialDate },
                                time = timeText.trim().ifBlank { "10:00 AM" },
                                notes = notes.trim(),
                                category = category.trim().ifBlank { "Appointment" }
                            )
                            onAdd(newEvent)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Schedule Event", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Material 3 DatePickerDialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        dateText = formatter.format(Date(millis))
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Material 3 TimePicker Dialog
    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF161B22)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            val hour = timePickerState.hour
                            val minute = timePickerState.minute
                            val isPm = hour >= 12
                            val displayHour = when {
                                hour == 0 -> 12
                                hour > 12 -> hour - 12
                                else -> hour
                            }
                            timeText = String.format(Locale.US, "%02d:%02d %s", displayHour, minute, if (isPm) "PM" else "AM")
                            showTimePicker = false
                        }) {
                            Text("OK", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
