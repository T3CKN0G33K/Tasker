package com.example.pantry.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun parsePunchTimeSafely(timeStr: String): String = timeStr.trim()

fun parseEventDateTimeToEpoch(dateStr: String, timeStr: String): Long {
    return runCatching {
        val cleanTime = parsePunchTimeSafely(timeStr)
            .substringBefore("–")
            .substringBefore("-")
            .trim()
        val dateTimeStr = "${dateStr.trim()} $cleanTime"
        val sdf = SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.US).apply { isLenient = false }
        sdf.parse(dateTimeStr)?.time ?: 0L
    }.getOrDefault(0L)
}

private fun cancelEventReminders(context: android.content.Context, event: CalendarEvent) {
    CalendarAlarmScheduler.cancelReminder(context, event.id)
    val today = LocalDate.now()
    repeat(181) { offset -> CalendarAlarmScheduler.cancelReminder(context, "${event.id}@${today.plusDays(offset.toLong())}") }
}

private fun scheduleEventReminder(context: android.content.Context, event: CalendarEvent) {
    // Replace any alarms created by older versions or older event settings.
    cancelEventReminders(context, event)
    if (event.reminderMinutesBefore < 0 || event.appointmentStatus == "ATTENDED") return
    val today = LocalDate.now()
    repeat(181) { offset ->
        val occurrenceDate = today.plusDays(offset.toLong())
        if (eventOccursOn(event, occurrenceDate)) {
            val occurrence = event.copy(
                id = if (event.repeatRule == "NONE") event.id else "${event.id}@$occurrenceDate",
                date = occurrenceDate.toString(),
                repeatRule = "NONE"
            )
            val startTime = parseEventDateTimeToEpoch(occurrence.date, occurrence.time)
            if (startTime > 0L) {
                CalendarAlarmScheduler.scheduleReminder(
                    context = context,
                    eventId = occurrence.id,
                    eventTitle = occurrence.title,
                    eventTime = occurrence.time,
                    eventNotes = occurrence.notes,
                    reminderEpochMillis = startTime - occurrence.reminderMinutesBefore * 60_000L
                )
            }
        }
    }
}

data class AppointmentHistoryEntry(
    val action: String = "",
    val date: String = "",
    val time: String = "",
    val recordedAt: Long = 0L
)

data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val date: String = "", // Format YYYY-MM-DD e.g. "2026-10-16"
    val time: String = "10:00 AM",
    val notes: String = "",
    val category: String = "Appointment",
    val reminderMinutesBefore: Int = 60,
    val repeatRule: String = "NONE",
    val repeatUntil: String = "",
    val occurrenceDate: String? = null,
    val isForecast: Boolean = false,
    val appointmentStatus: String = "SCHEDULED",
    val appointmentHistory: List<AppointmentHistoryEntry> = emptyList()
)

private fun eventOccursOn(event: CalendarEvent, targetDate: LocalDate): Boolean {
    val start = runCatching { LocalDate.parse(event.date) }.getOrNull() ?: return false
    if (targetDate.isBefore(start)) return false
    val repeatEnd = event.repeatUntil.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (repeatEnd != null && targetDate.isAfter(repeatEnd)) return false
    val deltaDays = ChronoUnit.DAYS.between(start, targetDate)
    return when (event.repeatRule) {
        "DAILY" -> true
        "WEEKLY" -> deltaDays % 7L == 0L
        "MONTHLY" -> {
            val months = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(targetDate))
            months >= 0 && targetDate.dayOfMonth == minOf(start.dayOfMonth, YearMonth.from(targetDate).lengthOfMonth())
        }
        "YEARLY" -> targetDate.monthValue == start.monthValue &&
            targetDate.dayOfMonth == minOf(start.dayOfMonth, YearMonth.from(targetDate).lengthOfMonth())
        else -> targetDate == start
    }
}

private fun expandEvents(events: List<CalendarEvent>, from: String, through: String): List<CalendarEvent> {
    val start = runCatching { LocalDate.parse(from) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalDate.parse(through) }.getOrNull() ?: return emptyList()
    val output = mutableListOf<CalendarEvent>()
    events.forEach { event ->
        var day = start
        var added = 0
        while (!day.isAfter(end) && added < 400) {
            if (eventOccursOn(event, day)) {
                output += event.copy(occurrenceDate = day.toString())
                added++
            }
            day = day.plusDays(1)
        }
    }
    return output
}

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
    currentUser: UserProfile,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    forecastEvents: List<CalendarEvent> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()

    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    val allEvents = remember(events, forecastEvents) { (events + forecastEvents).distinctBy { it.id } }
    var isLoading by remember { mutableStateOf(true) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var eventToDelete by remember { mutableStateOf<CalendarEvent?>(null) }
    var isSavingEvent by remember { mutableStateOf(false) }
    var calendarViewMode by remember { mutableStateOf("AGENDA") }

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
                                title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                                date = doc.getString("date")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                                time = doc.getString("time") ?: "",
                                notes = doc.getString("notes") ?: "",
                                category = doc.getString("category") ?: "Appointment",
                                // Older events had a reminder toggle but didn't persist its value.
                                reminderMinutesBefore = doc.getLong("reminderMinutesBefore")?.toInt() ?: 60,
                                repeatRule = doc.getString("repeatRule") ?: "NONE",
                                repeatUntil = doc.getString("repeatUntil") ?: "",
                                appointmentStatus = doc.getString("appointmentStatus") ?: "SCHEDULED",
                                appointmentHistory = (doc.get("appointmentHistory") as? List<*>)
                                    .orEmpty()
                                    .mapNotNull { raw ->
                                        val entry = raw as? Map<*, *> ?: return@mapNotNull null
                                        AppointmentHistoryEntry(
                                            action = entry["action"] as? String ?: "",
                                            date = entry["date"] as? String ?: "",
                                            time = entry["time"] as? String ?: "",
                                            recordedAt = (entry["recordedAt"] as? Number)?.toLong() ?: 0L
                                        )
                                    }
                            )
                        }
                        events = items
                        items.forEach { scheduleEventReminder(context, it) }
                    }
                }
        } ?: run {
            isLoading = false
        }
    }

    // Scope-aware Event Filtering Logic
    val displayedEvents = remember(allEvents, selectedDateString, calendarViewMode, weekStartString, weekEndString, monthStartString, monthEndString) {
        val range = when (calendarViewMode) {
            "WEEK" -> weekStartString to weekEndString
            "MONTH" -> monthStartString to monthEndString
            "DAY" -> selectedDateString to selectedDateString
            else -> selectedDateString to LocalDate.parse(selectedDateString).plusMonths(12).toString()
        }
        expandEvents(allEvents, range.first, range.second)
            .sortedWith(compareBy({ it.occurrenceDate ?: it.date }, { parsePunchTimeSafely(it.time) }))
    }
    val calendarGridEvents = remember(allEvents, calendarViewMode, weekStartString, weekEndString, monthStartString, monthEndString) {
        val range = if (calendarViewMode == "WEEK") weekStartString to weekEndString else monthStartString to monthEndString
        expandEvents(allEvents, range.first, range.second)
    }
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current

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
            // Calendar title and a quick return to today's date.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calendar",
                        color = appColors.textPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )

                    TextButton(onClick = {
                        val today = Calendar.getInstance()
                        currentYear = today.get(Calendar.YEAR)
                        currentMonth = today.get(Calendar.MONTH)
                        selectedDay = today.get(Calendar.DAY_OF_MONTH)
                    }) {
                        Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Today")
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("AGENDA" to "Agenda", "DAY" to "Day", "WEEK" to "Week", "MONTH" to "Month")) { (key, label) ->
                        FilterChip(
                            selected = calendarViewMode == key,
                            onClick = { calendarViewMode = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (calendarViewMode == "DAY" || calendarViewMode == "AGENDA") {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = {
                            val moved = Calendar.getInstance().apply {
                                set(currentYear, currentMonth, selectedDay)
                                add(Calendar.DAY_OF_MONTH, -1)
                            }
                            currentYear = moved.get(Calendar.YEAR)
                            currentMonth = moved.get(Calendar.MONTH)
                            selectedDay = moved.get(Calendar.DAY_OF_MONTH)
                        }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day") }
                        Text(
                            text = LocalDate.parse(selectedDateString).format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.US)),
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = {
                            val moved = Calendar.getInstance().apply {
                                set(currentYear, currentMonth, selectedDay)
                                add(Calendar.DAY_OF_MONTH, 1)
                            }
                            currentYear = moved.get(Calendar.YEAR)
                            currentMonth = moved.get(Calendar.MONTH)
                            selectedDay = moved.get(Calendar.DAY_OF_MONTH)
                        }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next day") }
                    }
                }
            }

            // 2. Main Calendar Container Card
            if (calendarViewMode == "MONTH" || calendarViewMode == "WEEK") item {
                MainCalendarCard(
                    currentYear = currentYear,
                    currentMonth = currentMonth,
                    selectedDay = selectedDay,
                    monthTitle = monthTitle,
                    calendarViewMode = calendarViewMode,
                    daysInMonth = daysInMonth,
                    firstDayOfWeekOffset = firstDayOfWeekOffset,
                    weekDays = weekDays,
                    events = calendarGridEvents,
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

            // The selected date and events remain visible in every view.
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
                            color = appColors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (calendarViewMode) {
                                "WEEK" -> "WEEK AGENDA"
                                "MONTH" -> "MONTH AGENDA"
                                "DAY" -> "DAY AGENDA"
                                else -> "UPCOMING EVENTS"
                            },
                            color = appColors.textSecondary,
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
                            text = "${displayedEvents.size} events",
                            color = appColors.primaryAccent,
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
                            Icon(Icons.Default.Event, contentDescription = null, tint = appColors.textSecondary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No events scheduled for this period.", color = appColors.textSecondary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(displayedEvents, key = { it.id }) { eventItem ->
                    AgendaEventCard(
                        eventItem = eventItem,
                        onOpen = {
                            if (!eventItem.isForecast) {
                                editingEvent = eventItem
                                showAddEventDialog = true
                            }
                        },
                        onDelete = { if (!eventItem.isForecast) eventToDelete = eventItem }
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
        .clickable { editingEvent = null; showAddEventDialog = true },
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
            initialEvent = editingEvent,
            isSaving = isSavingEvent,
            themePreference = currentUser.themePreference,
            onDismiss = { if (!isSavingEvent) { showAddEventDialog = false; editingEvent = null } },
            onAdd = { newEvent ->
                val hid = currentUser.householdId
                if (hid.isNullOrBlank() || isSavingEvent) return@AddEventCenteredDialog
                isSavingEvent = true
                scope.launch {
                    try {
                        val data = hashMapOf(
                        "id" to newEvent.id,
                        "title" to newEvent.title,
                        "date" to newEvent.date,
                        "time" to newEvent.time,
                        "notes" to newEvent.notes,
                        "category" to newEvent.category,
                        "reminderMinutesBefore" to newEvent.reminderMinutesBefore,
                        "repeatRule" to newEvent.repeatRule,
                        "repeatUntil" to newEvent.repeatUntil,
                        "appointmentStatus" to newEvent.appointmentStatus,
                        "appointmentHistory" to newEvent.appointmentHistory.map { entry ->
                            mapOf(
                                "action" to entry.action,
                                "date" to entry.date,
                                "time" to entry.time,
                                "recordedAt" to entry.recordedAt
                            )
                        }
                        )
                        val eventRef = db.collection("households").document(hid)
                            .collection("events").document(newEvent.id)
                        val saveTask = eventRef.set(data, SetOptions.merge())
                        scheduleEventReminder(context, newEvent)
                        showAddEventDialog = false
                        editingEvent = null
                        Toast.makeText(context, "Event update queued on this device; it will sync when Firestore reconnects.", Toast.LENGTH_LONG).show()
                        saveTask.addOnSuccessListener {
                            Toast.makeText(context, "Calendar update synced.", Toast.LENGTH_SHORT).show()
                        }.addOnFailureListener { error ->
                            Toast.makeText(context, "Calendar update couldn't sync: ${error.localizedMessage ?: "check your connection"}", Toast.LENGTH_LONG).show()
                        }
                    } catch (error: Exception) {
                        val message = if (error is kotlinx.coroutines.TimeoutCancellationException) {
                            "Saving took too long. Check your connection and try again."
                        } else {
                            "Couldn't save event: ${error.localizedMessage ?: "try again"}"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    } finally {
                        isSavingEvent = false
                    }
                }
            }
        )
    }

    eventToDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { eventToDelete = null },
            title = { Text("Delete event?", color = Color.White) },
            text = {
                Text(
                    if (event.repeatRule == "NONE") "${event.title} will be removed from the household calendar."
                    else "${event.title} and its repeating schedule will be removed from the household calendar.",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val hid = currentUser.householdId ?: return@TextButton
                    eventToDelete = null
                    scope.launch {
                        try {
                            db.collection("households").document(hid).collection("events").document(event.id).delete().await()
                            db.collection("households").document(hid).collection("calendar_events").document(event.id).delete().await()
                            cancelEventReminders(context, event)
                        } catch (error: Exception) {
                            Toast.makeText(context, "Couldn't delete event: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { eventToDelete = null }) { Text("Cancel") } },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
fun UpcomingHeroCard(focusEvent: CalendarEvent) {
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
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
                color = appColors.textPrimary,
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
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = focusEvent.date,
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = appColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = focusEvent.time,
                        color = appColors.textSecondary,
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
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
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
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month", tint = appColors.textPrimary)
                }

                Text(
                    text = monthTitle,
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onMonthNext) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = appColors.textPrimary)
                }
            }

            // Day-of-Week Labels Header (7 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { idx, label ->
                    val color = if (idx == 0 || idx == 6) Color(0xFFEF4444) else appColors.textSecondary
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

            HorizontalDivider(color = appColors.cardBorder, thickness = 0.7.dp)

            if (calendarViewMode == "WEEK") {
                // Single Horizontal 7-Day Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDays.forEach { (y, m, d) ->
                        val isSelected = y == currentYear && m == currentMonth && d == selectedDay
                        val dayStr = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                        val dayEvents = events.filter { (it.occurrenceDate ?: it.date) == dayStr }

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
                                    color = if (isSelected) Color.White else appColors.textPrimary,
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
                                    val dayEvents = events.filter { (it.occurrenceDate ?: it.date) == dayStr }

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
                                                color = if (isSelected) Color.White else appColors.textPrimary,
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
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    val accentColor = getCategoryAccentColor(eventItem.category)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(18.dp)
            .clickable(onClick = onOpen)
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
                        color = appColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${eventItem.occurrenceDate ?: eventItem.date} • ${eventItem.time}${if (eventItem.notes.isNotBlank()) " • ${eventItem.notes}" else ""}",
                            color = appColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if ((eventItem.category.equals("Appointment", ignoreCase = true) || eventItem.category.equals("Medical", ignoreCase = true)) &&
                        eventItem.appointmentStatus != "SCHEDULED"
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (eventItem.appointmentStatus == "ATTENDED") "Attended" else "Rescheduled",
                            color = if (eventItem.appointmentStatus == "ATTENDED") Color(0xFF34D399) else Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
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

                if (!eventItem.isForecast) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Event",
                            tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Text("ESTIMATE", color = accentColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddEventCenteredDialog(
    initialDate: String,
    initialEvent: CalendarEvent? = null,
    isSaving: Boolean = false,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onAdd: (CalendarEvent) -> Unit
) {
    val context = LocalContext.current
    val initialTimes = initialEvent?.time.orEmpty().split("–")
    val draftEventId = remember(initialEvent?.id) { initialEvent?.id ?: UUID.randomUUID().toString() }
    var title by remember(initialEvent?.id) { mutableStateOf(initialEvent?.title.orEmpty()) }
    var dateText by remember(initialEvent?.id) { mutableStateOf(initialEvent?.date ?: initialDate) }
    var startTimeText by remember(initialEvent?.id) { mutableStateOf(initialTimes.firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: "5:00 PM") }
    var endTimeText by remember(initialEvent?.id) { mutableStateOf(initialTimes.getOrNull(1)?.trim().orEmpty()) }
    var category by remember(initialEvent?.id) { mutableStateOf(initialEvent?.category ?: "Restock") }
    var appointmentStatus by remember(initialEvent?.id) {
        mutableStateOf(initialEvent?.appointmentStatus ?: "SCHEDULED")
    }
    var locationDetails by remember(initialEvent?.id) { mutableStateOf(initialEvent?.notes.orEmpty()) }
    var reminderMinutesBefore by remember(initialEvent?.id) { mutableIntStateOf(initialEvent?.reminderMinutesBefore ?: 60) }
    var repeatRule by remember(initialEvent?.id) { mutableStateOf(initialEvent?.repeatRule ?: "NONE") }
    var repeatUntil by remember(initialEvent?.id) { mutableStateOf(initialEvent?.repeatUntil.orEmpty()) }
    var showRepeatOptions by remember { mutableStateOf(false) }
    var showReminderOptions by remember { mutableStateOf(false) }
    val reminderOptions = listOf(
        -1 to "No reminder",
        0 to "At event time",
        5 to "5 minutes before",
        10 to "10 minutes before",
        15 to "15 minutes before",
        30 to "30 minutes before",
        60 to "1 hour before",
        1440 to "1 day before"
    )

    val categories = listOf(
        "Restock" to "🛒",
        "Shift" to "⏰",
        "Upkeep" to "🚗",
        "Chore" to "🧼",
        "Appointment" to "🩺"
    )
    val editingAppointment = initialEvent != null &&
        (initialEvent.category.equals("Appointment", ignoreCase = true) || initialEvent.category.equals("Medical", ignoreCase = true))

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
                        text = if (initialEvent == null) "Create event" else "Edit event",
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

            if (initialEvent?.repeatRule?.let { it != "NONE" } == true) {
                Text("Changes apply to the repeating event series.", color = Color(0xFF94A3B8), fontSize = 12.sp)
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

            if (editingAppointment) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "APPOINTMENT STATUS",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(
                            "SCHEDULED" to "Scheduled",
                            "ATTENDED" to "Attended",
                            "RESCHEDULED" to "Reschedule"
                        )) { (status, label) ->
                            FilterChip(
                                selected = appointmentStatus == status,
                                onClick = { appointmentStatus = status },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        text = when (appointmentStatus) {
                            "ATTENDED" -> "Mark that you went. This appointment will stay in your calendar history."
                            "RESCHEDULED" -> "Choose the new date and time below. The previous appointment time will be kept in its history."
                            else -> "This appointment is still scheduled."
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    initialEvent.appointmentHistory.lastOrNull()?.let { previous ->
                        val previousAction = if (previous.action == "RESCHEDULED") "Previously rescheduled from" else "Previously marked attended for"
                        Text("$previousAction ${previous.date} · ${previous.time}", color = Color(0xFF94A3B8), fontSize = 11.sp)
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
                onValueChange = {},
                label = { Text("DATE", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                trailingIcon = { Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF38BDF8)) },
                modifier = Modifier.fillMaxWidth().clickable {
                    val date = runCatching { LocalDate.parse(dateText) }.getOrDefault(LocalDate.now())
                    DatePickerDialog(context, { _, year, month, day ->
                        dateText = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                readOnly = true,
                colors = inputColors
            )

            Box {
                OutlinedButton(onClick = { showRepeatOptions = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Repeat: ${mapOf("NONE" to "Does not repeat", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly")[repeatRule]}")
                }
                DropdownMenu(expanded = showRepeatOptions, onDismissRequest = { showRepeatOptions = false }) {
                    listOf("NONE" to "Does not repeat", "DAILY" to "Daily", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly").forEach { (rule, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { repeatRule = rule; showRepeatOptions = false })
                    }
                }
            }
            if (repeatRule != "NONE") {
                OutlinedTextField(
                    value = repeatUntil,
                    onValueChange = { repeatUntil = it },
                    label = { Text("REPEAT UNTIL (OPTIONAL)") },
                    placeholder = { Text("YYYY-MM-DD or leave blank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = inputColors
                )
            }

            // Start & End Time Split Row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = startTimeText,
                    onValueChange = {},
                    label = { Text("START TIME", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f).clickable {
                        val time = runCatching { SimpleDateFormat("h:mm a", Locale.US).parse(startTimeText) }.getOrNull()
                        val cal = Calendar.getInstance().apply { if (time != null) this.time = time }
                        TimePickerDialog(context, { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                            startTimeText = SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    readOnly = true,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = endTimeText,
                    onValueChange = {},
                    label = { Text("END TIME", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f).clickable {
                        val time = runCatching { SimpleDateFormat("h:mm a", Locale.US).parse(endTimeText) }.getOrNull()
                        val cal = Calendar.getInstance().apply { if (time != null) this.time = time }
                        TimePickerDialog(context, { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                            endTimeText = SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    readOnly = true,
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

            // Configurable event notification, matching the reminder choices people expect.
            Box {
                OutlinedButton(
                    onClick = { showReminderOptions = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("Reminder", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Text(
                            reminderOptions.firstOrNull { it.first == reminderMinutesBefore }?.second ?: "1 hour before",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text("▾", color = Color(0xFF38BDF8))
                }
                DropdownMenu(
                    expanded = showReminderOptions,
                    onDismissRequest = { showReminderOptions = false }
                ) {
                    reminderOptions.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                reminderMinutesBefore = minutes
                                showReminderOptions = false
                            }
                        )
                    }
                }
            }

            // Save Primary Button
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Add an event title.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val eventDate = runCatching { LocalDate.parse(dateText.trim()) }.getOrNull()
                    if (eventDate == null) {
                        Toast.makeText(context, "Choose a valid event date.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val endDate = repeatUntil.trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (repeatUntil.isNotBlank() && (endDate == null || endDate.isBefore(eventDate))) {
                        Toast.makeText(context, "Choose a repeat end date on or after the event date.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val timeFormatted = if (endTimeText.isNotBlank()) "$startTimeText – $endTimeText" else startTimeText
                    val previousTime = initialEvent?.time.orEmpty()
                    val dateOrTimeChanged = initialEvent != null &&
                        (dateText.trim() != initialEvent.date || timeFormatted != previousTime)
                    if (editingAppointment && appointmentStatus == "RESCHEDULED" && !dateOrTimeChanged) {
                        Toast.makeText(context, "Choose the new appointment date or time before saving the reschedule.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val history = initialEvent?.appointmentHistory.orEmpty().toMutableList()
                    if (editingAppointment) {
                        val appointment = checkNotNull(initialEvent)
                        val newlyAttended = appointmentStatus == "ATTENDED" && appointment.appointmentStatus != "ATTENDED"
                        val newlyRescheduled = appointmentStatus == "RESCHEDULED" && dateOrTimeChanged
                        if (newlyAttended) {
                            history += AppointmentHistoryEntry(
                                action = "ATTENDED",
                                date = appointment.date,
                                time = appointment.time,
                                recordedAt = System.currentTimeMillis()
                            )
                        } else if (newlyRescheduled) {
                            history += AppointmentHistoryEntry(
                                action = "RESCHEDULED",
                                date = appointment.date,
                                time = appointment.time,
                                recordedAt = System.currentTimeMillis()
                            )
                        }
                    }
                    val newEvent = CalendarEvent(
                        id = draftEventId,
                        title = title.trim(),
                        date = dateText.trim(),
                        time = timeFormatted,
                        notes = locationDetails.trim(),
                        category = category,
                        reminderMinutesBefore = reminderMinutesBefore,
                        repeatRule = repeatRule,
                        repeatUntil = if (repeatRule == "NONE") "" else repeatUntil.trim(),
                        appointmentStatus = if (editingAppointment) appointmentStatus else "SCHEDULED",
                        appointmentHistory = history.takeLast(40)
                    )
                    onAdd(newEvent)
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text(if (isSaving) "SAVING…" else if (initialEvent == null) "SAVE EVENT" else "SAVE CHANGES", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
