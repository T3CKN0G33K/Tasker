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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
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
import com.example.pantry.notifications.CalendarAlarmScheduler
import com.example.pantry.ui.theme.themedCard
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

fun parsePunchTimeSafely(timeStr: String): String = timeStr.trim()

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
    val date: String = "", // Format YYYY-MM-DD e.g. "2026-10-16"
    val time: String = "10:00 AM",
    val notes: String = "",
    val category: String = "Appointment"
)

fun getCategoryAccentColor(category: String): Color {
    return when (category.lowercase()) {
        "shift", "shifts", "restock" -> Color(0xFFFBBF24)
        "appointment", "medical" -> Color(0xFF38BDF8)
        "household", "maintenance", "personal", "diy", "upkeep", "chore" -> Color(0xFF10B981)
        else -> Color(0xFF38BDF8)
    }
}

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
    var calendarViewMode by remember { mutableStateOf("MONTH") } // "WEEK" vs "MONTH"

    // Calendar Date Calculation State
    val calendar = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) } // 0..11
    var selectedDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

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

    val monthStartString = remember(currentYear, currentMonth) {
        String.format(Locale.US, "%04d-%02d-01", currentYear, currentMonth + 1)
    }

    val daysInMonth = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    val monthEndString = remember(currentYear, currentMonth, daysInMonth) {
        String.format(Locale.US, "%04d-%02d-%02d", currentYear, currentMonth + 1, daysInMonth)
    }

    val firstDayOfWeekOffset = remember(currentYear, currentMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.get(Calendar.DAY_OF_WEEK) - 1 // 0 for Sunday
    }

    // Week Strip Calculation
    val weekDays = remember(currentYear, currentMonth, selectedDay) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, selectedDay)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        cal.add(Calendar.DAY_OF_MONTH, -dayOfWeek)

        val days = mutableListOf<Triple<Int, Int, Int>>()
        for (i in 0..6) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)
            days.add(Triple(y, m, d))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        days
    }

    val weekStartString = remember(weekDays) {
        val (y, m, d) = weekDays.first()
        String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
    }

    val weekEndString = remember(weekDays) {
        val (y, m, d) = weekDays.last()
        String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
    }

    // Real-time Firestore Sync for Calendar Events (Purged Demo Fallbacks)
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
                    }
                }
        } ?: run {
            isLoading = false
        }
    }

    // Scope-aware Event Filtering Logic
    val displayedEvents = remember(events, selectedDateString, calendarViewMode, weekStartString, weekEndString, monthStartString, monthEndString) {
        when (calendarViewMode) {
            "WEEK" -> events.filter { it.date in weekStartString..weekEndString }
            "MONTH" -> events.filter { it.date in monthStartString..monthEndString }
            else -> events.filter { it.date == selectedDateString }
        }.sortedWith(compareBy({ it.date }, { parsePunchTimeSafely(it.time) }))
    }

    // Upcoming Event Hero Card Calculation
    val upcomingEvent = remember(events, selectedDateString) {
        events.filter { it.date >= selectedDateString }
            .minByOrNull { it.date + " " + it.time }
    }

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
            // 1. Top Bar: Header Title & View Toggle Segmented Pill
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Schedule",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )

                    // Segmented Capsule Pill Toggle
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (calendarViewMode == "WEEK") Color(0xFF007AFF) else Color.Transparent)
                                .clickable { calendarViewMode = "WEEK" }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Week",
                                color = if (calendarViewMode == "WEEK") Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (calendarViewMode == "MONTH") Color(0xFF007AFF) else Color.Transparent)
                                .clickable { calendarViewMode = "MONTH" }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Month",
                                color = if (calendarViewMode == "MONTH") Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. Main Calendar Container Card
            item {
                MainCalendarCard(
                    currentYear = currentYear,
                    currentMonth = currentMonth,
                    selectedDay = selectedDay,
                    monthTitle = monthTitle,
                    calendarViewMode = calendarViewMode,
                    daysInMonth = daysInMonth,
                    firstDayOfWeekOffset = firstDayOfWeekOffset,
                    weekDays = weekDays,
                    events = events,
                    onMonthPrev = {
                        if (currentMonth == 0) {
                            currentMonth = 11
                            currentYear -= 1
                        } else {
                            currentMonth -= 1
                        }
                        selectedDay = 1
                    },
                    onMonthNext = {
                        if (currentMonth == 11) {
                            currentMonth = 0
                            currentYear += 1
                        } else {
                            currentMonth += 1
                        }
                        selectedDay = 1
                    },
                    onSelectDay = { y, m, d ->
                        currentYear = y
                        currentMonth = m
                        selectedDay = d
                    }
                )
            }

            // 3. Upcoming Event Hero Card
            upcomingEvent?.let { focusEvent ->
                item {
                    UpcomingHeroCard(focusEvent = focusEvent)
                }
            }

            // 4. Agenda Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = selectedDay.toString(),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (calendarViewMode == "WEEK") "WEEKLY AGENDA" else if (calendarViewMode == "MONTH") "MONTHLY AGENDA" else "EVENTS FOR $selectedDateString",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${displayedEvents.size} Scheduled",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. Scope-Aware Agenda Event Cards
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                }
            } else if (displayedEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No events scheduled for this period.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(displayedEvents, key = { it.id }) { eventItem ->
                    AgendaEventCard(
                        eventItem = eventItem,
                        onDelete = {
                            events = events.filter { it.id != eventItem.id }
                            currentUser.householdId?.let { hid ->
                                db.collection("households").document(hid)
                                    .collection("events").document(eventItem.id)
                                    .delete()
                                db.collection("households").document(hid)
                                    .collection("calendar_events").document(eventItem.id)
                                    .delete()
                            }
                            CalendarAlarmScheduler.cancelReminder(context, eventItem.id)
                        }
                    )
                }
            }
        }

        // Single Primary Blue + FAB Anchored at bottom = 96.dp, end = 24.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(com.example.pantry.ui.theme.LocalAppColors.current.fabColor, com.example.pantry.ui.theme.LocalAppColors.current.primaryAccent)
                    )
                )
                .clickable { showAddEventDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Event",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Schedule Event Sheet Dialog
    if (showAddEventDialog) {
        AddEventCenteredDialog(
            initialDate = selectedDateString,
            themePreference = currentUser.themePreference,
            onDismiss = { showAddEventDialog = false },
            onAdd = { newEvent ->
                events = events + newEvent
                currentUser.householdId?.let { hid ->
                    val data = hashMapOf(
                        "id" to newEvent.id,
                        "title" to newEvent.title,
                        "date" to newEvent.date,
                        "time" to newEvent.time,
                        "notes" to newEvent.notes,
                        "category" to newEvent.category
                    )
                    db.collection("households").document(hid).collection("events").document(newEvent.id).set(data, SetOptions.merge())
                    db.collection("households").document(hid).collection("calendar_events").document(newEvent.id).set(data, SetOptions.merge())
                }

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

@Composable
fun UpcomingHeroCard(focusEvent: CalendarEvent) {
    val accentColor = getCategoryAccentColor(focusEvent.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(20.dp)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UPCOMING EVENT",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.20f))
                        .border(0.5.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = focusEvent.category,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = focusEvent.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = focusEvent.date,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = focusEvent.time,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun MainCalendarCard(
    currentYear: Int,
    currentMonth: Int,
    selectedDay: Int,
    monthTitle: String,
    calendarViewMode: String,
    daysInMonth: Int,
    firstDayOfWeekOffset: Int,
    weekDays: List<Triple<Int, Int, Int>>,
    events: List<CalendarEvent>,
    onMonthPrev: () -> Unit,
    onMonthNext: () -> Unit,
    onSelectDay: (year: Int, month: Int, day: Int) -> Unit
) {
    val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(24.dp)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Month Header Bar with Navigation Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMonthPrev) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = Color.White)
                }

                Text(
                    text = monthTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onMonthNext) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = Color.White)
                }
            }

            // Day-of-Week Labels Header (7 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { idx, label ->
                    val color = if (idx == 0 || idx == 6) Color(0xFFEF4444) else Color(0xFF64748B)
                    Text(
                        text = label,
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f), thickness = 0.5.dp)

            if (calendarViewMode == "WEEK") {
                // Single Horizontal 7-Day Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDays.forEach { (y, m, d) ->
                        val isSelected = y == currentYear && m == currentMonth && d == selectedDay
                        val dayStr = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                        val dayEvents = events.filter { it.date == dayStr }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF007AFF) else Color.Transparent)
                                .clickable { onSelectDay(y, m, d) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = d.toString(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (dayEvents.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayEvents.take(3).forEach { evt ->
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else getCategoryAccentColor(evt.category))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Collapse Expander Drag Handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF334155))
                    )
                }
            } else {
                // 7-Column Month Grid View
                val totalSlots = daysInMonth + firstDayOfWeekOffset
                val rows = (totalSlots + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    val dayEvents = events.filter { it.date == dayStr }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) Color(0xFF007AFF) else Color.Transparent)
                                            .clickable { onSelectDay(currentYear, currentMonth, dayNum) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            if (dayEvents.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    dayEvents.take(3).forEach { evt ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSelected) Color.White else getCategoryAccentColor(evt.category))
                                                        )
                                                    }
                                                }
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

@Composable
fun AgendaEventCard(
    eventItem: CalendarEvent,
    onDelete: () -> Unit
) {
    val accentColor = getCategoryAccentColor(eventItem.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Accent Stripe (width = 4.dp, fill matching category)
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eventItem.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${eventItem.time}${if (eventItem.notes.isNotBlank()) " • ${eventItem.notes}" else ""}",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Category Tag Pill (height = 24.dp)
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.20f))
                        .border(0.5.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = eventItem.category,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Event",
                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddEventCenteredDialog(
    initialDate: String,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onAdd: (CalendarEvent) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(initialDate) }
    var startTimeText by remember { mutableStateOf("5:00 PM") }
    var endTimeText by remember { mutableStateOf("6:30 PM") }
    var category by remember { mutableStateOf("Restock") }
    var locationDetails by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(true) }

    val categories = listOf(
        "Restock" to "🛒",
        "Shift" to "⏰",
        "Upkeep" to "🚗",
        "Chore" to "🧼",
        "Appointment" to "🩺"
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
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Schedule Event",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add a shift, restock run, or chore to the agenda",
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

            // Category Pill Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "EVENT TYPE",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { (catName, emoji) ->
                        val isSelected = category == catName
                        val catColor = getCategoryAccentColor(catName)
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) catColor.copy(alpha = 0.25f) else Color(0xFF0F172A))
                                .border(1.dp, if (isSelected) catColor else Color(0xFF334155), RoundedCornerShape(16.dp))
                                .clickable { category = catName }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$emoji $catName",
                                color = if (isSelected) catColor else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        }
                    }
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

            // Title Field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("EVENT TITLE", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                placeholder = { Text("e.g. Restock Run: Pet Supplies", color = Color(0xFF64748B), fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = inputColors
            )

            // Date Picker Field
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text("DATE", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                trailingIcon = { Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF38BDF8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = inputColors
            )

            // Start & End Time Split Row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startTimeText,
                    onValueChange = { startTimeText = it },
                    label = { Text("START TIME", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = endTimeText,
                    onValueChange = { endTimeText = it },
                    label = { Text("END TIME", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            // Location / Details Field
            OutlinedTextField(
                value = locationDetails,
                onValueChange = { locationDetails = it },
                label = { Text("LOCATION / DETAILS", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                placeholder = { Text("Walmart • Cat food & puppy pads", color = Color(0xFF64748B), fontSize = 13.sp) },
                trailingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = inputColors
            )

            // Push Notification Reminder Toggle Row
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
                        Text("Push Notification Reminder", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Alert 1 hour before scheduled time", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7)
                        )
                    )
                }
            }

            // Save Primary Button
            Button(
                onClick = {
                    if (title.isBlank() || dateText.isBlank()) return@Button
                    val timeFormatted = if (endTimeText.isNotBlank()) "$startTimeText – $endTimeText" else startTimeText
                    val newEvent = CalendarEvent(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        date = dateText.trim(),
                        time = timeFormatted,
                        notes = locationDetails.trim(),
                        category = category
                    )
                    onAdd(newEvent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("SAVE TO CALENDAR", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
