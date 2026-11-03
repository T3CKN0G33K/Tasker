package com.example.pantry.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
import com.example.pantry.data.BillItem
import com.example.pantry.data.PlaidFinanceRepository
import com.example.pantry.data.PlaidSnapshot
import com.plaid.link.Plaid
import com.plaid.link.OpenPlaidLink
import com.plaid.link.configuration.linkTokenConfiguration
import com.plaid.link.result.LinkExit
import com.plaid.link.result.LinkSuccess
import com.example.pantry.ui.theme.themedCard
import com.google.firebase.Firebase
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

fun parseDueDateDayNumber(dueDate: String): Int {
    val clean = dueDate.trim()
    if (clean.contains("/")) {
        val parts = clean.split("/")
        if (parts.size >= 2) {
            val day = parts[1].filter { it.isDigit() }.toIntOrNull()
            if (day != null) return day
        }
    }
    val regex = Regex("\\d+")
    val match = regex.find(clean)
    return match?.value?.toIntOrNull() ?: 99
}

fun resolveBillCategoryEmoji(name: String): String {
    val lower = name.lowercase(Locale.US)
    return when {
        lower.contains("rent") || lower.contains("apartment") -> "🏠"
        lower.contains("disney") || lower.contains("netflix") || lower.contains("hulu") || lower.contains("youtube") || lower.contains("spotify") || lower.contains("tv") -> "📺"
        lower.contains("xfinity") || lower.contains("internet") || lower.contains("wifi") || lower.contains("at&t") || lower.contains("phone") -> "🌐"
        lower.contains("google") || lower.contains("cloud") || lower.contains("apple") || lower.contains("storage") -> "☁️"
        lower.contains("insurance") || lower.contains("safty") || lower.contains("safety") -> "🛡️"
        lower.contains("grid") || lower.contains("electric") || lower.contains("power") || lower.contains("energy") -> "⚡"
        else -> "📄"
    }
}

data class BudgetCycleInfo(
    val key: String, // e.g. "2026-10"
    val label: String, // e.g. "October 2026"
    val isActive: Boolean
)

@Composable
private fun PantrySpendingForecastCard(
    recommendations: List<RestockRecommendation>,
    forecast: HouseholdSpendingForecast,
    selectedDays: Int,
    pendingIds: Set<String>,
    onSelectDays: (Int) -> Unit,
    onSnooze: (RestockRecommendation) -> Unit,
    onViewPantryItem: (String) -> Unit,
    onMarkPurchased: (RestockRecommendation) -> Unit
) {
    val colors = com.example.pantry.ui.theme.LocalAppColors.current
    var quantities by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.cardSurface)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(20.dp)).padding(if (expanded) 16.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("SUPPLY SPENDING FORECAST", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (forecast.pricedProducts > 0) String.format(Locale.US, "$%.2f", forecast.total) else "No prices available",
                        color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
                    )
                    Text("next $selectedDays days", color = colors.textSecondary, fontSize = 12.sp)
                }
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide" else "Details") }
        }

        if (expanded) {
            if (forecast.unpricedProducts > 0) Text("${forecast.unpricedProducts} suggested item(s) have no saved price and are excluded.", color = colors.textSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 14, 30).forEach { days ->
                    FilterChip(selected = selectedDays == days, onClick = { onSelectDays(days) }, label = { Text("${days}d") })
                }
            }
            if (recommendations.isEmpty()) {
                Text("No pantry purchases are currently forecast.", color = colors.textSecondary, fontSize = 13.sp)
            } else {
                recommendations.forEach { rec ->
                    val count = quantities[rec.id] ?: rec.quantity
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background.copy(alpha = 0.55f)).padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rec.productName, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${rec.urgency.label()} · ${rec.suggestedDate.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))} · $count package(s)", color = colors.textSecondary, fontSize = 12.sp)
                            }
                            Text(rec.estimatedCost?.let { String.format(Locale.US, "$%.2f", it / rec.quantity * count) } ?: "Price unknown", color = colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        Text(rec.reason, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                        rec.priceSource?.let { Text("Price: $it", color = colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp)) }
                        rec.confidence?.let { Text(it, color = colors.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp)) }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onViewPantryItem(rec.productId) }) { Text("View Pantry") }
                            TextButton(onClick = { quantities = quantities + (rec.id to (count - 1).coerceAtLeast(1)) }) { Text("−") }
                            TextButton(onClick = { quantities = quantities + (rec.id to (count + 1).coerceAtMost(99)) }) { Text("+") }
                            Spacer(Modifier.weight(1f))
                            if (rec.urgency == RestockUrgency.UPCOMING) TextButton(onClick = { onSnooze(rec) }) { Text("Snooze 7 days") }
                            Button(onClick = { onMarkPurchased(rec.copy(quantity = count)) }, enabled = rec.productId !in pendingIds) {
                                Text(if (rec.productId in pendingIds) "Saving…" else "Mark purchased")
                            }
                        }
                    }
                }
            }
            Text("Forecast only. This does not count as money spent in your Budget.", color = colors.textSecondary, fontSize = 11.sp)
        }
    }
}

private fun RestockUrgency.label(): String = when (this) {
    RestockUrgency.NEEDS_ATTENTION -> "Needs attention"
    RestockUrgency.UPCOMING -> "Upcoming"
}

@Composable
fun BudgetScreen(
    modifier: Modifier = Modifier,
    currentUser: UserProfile,
    isDarkMode: Boolean = true,
    initialBills: List<BillItem> = emptyList(),
    onUpdateBills: (List<BillItem>) -> Unit = {},
    pantryItems: List<PantryItem> = emptyList(),
    onRecordPantryPurchase: (PantryItem, Int) -> Unit = { _, _ -> },
    onViewPantryItem: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var billsList by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var billsLoadError by remember { mutableStateOf<String?>(null) }
    var billsListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var cycleListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var cycleListListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    var availableCycleKeys by remember { mutableStateOf(listOf(YearMonth.now().toString())) }
    var totalAllowance by remember { mutableDoubleStateOf(0.0) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var billPaymentTarget by remember { mutableStateOf<BillItem?>(null) }
    var billDeleteTarget by remember { mutableStateOf<BillItem?>(null) }
    var showCyclePickerSheet by remember { mutableStateOf(false) }
    var showAllowanceDialog by remember { mutableStateOf(false) }
    var isSavingAllowance by remember { mutableStateOf(false) }
    var allowanceInput by remember { mutableStateOf("") }
    var showPlaidSection by remember { mutableStateOf(false) }
    var isSavingNewBill by remember { mutableStateOf(false) }
    var savingBillIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var plaidSnapshot by remember(currentUser.uid) { mutableStateOf(PlaidSnapshot(false, emptyList(), emptyList(), emptyList())) }
    var isPlaidLoading by remember { mutableStateOf(false) }
    var isLinkingBank by remember { mutableStateOf(false) }
    var plaidError by remember(currentUser.uid) { mutableStateOf<String?>(null) }
    var showPlaidDeleteDialog by remember(currentUser.uid) { mutableStateOf(false) }
    var forecastDays by remember { mutableIntStateOf(30) }
    var purchasePendingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var locallySnoozedRestockIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var forecastNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()

    suspend fun loadPlaidSnapshot(syncFirst: Boolean = false) {
        isPlaidLoading = true
        plaidError = null
        try {
            // Load persisted connection status first, so a failed sync can be retried.
            plaidSnapshot = PlaidFinanceRepository.getSnapshot()
            if (syncFirst) {
                PlaidFinanceRepository.syncTransactions()
                plaidSnapshot = PlaidFinanceRepository.getSnapshot()
            }
        } catch (error: Exception) {
            plaidError = PlaidFinanceRepository.userFacingError(error)
        } finally {
            isPlaidLoading = false
        }
    }

    if (showPlaidDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showPlaidDeleteDialog = false },
            title = { Text("Disconnect and delete Sandbox data?") },
            text = { Text("This removes all Plaid Sandbox connections linked to your signed-in account and deletes their saved balances, transactions, and access tokens. Other household users and your manual budget are unaffected. If Plaid is unavailable, only the encrypted token needed to retry disconnect is kept until cleanup finishes.") },
            confirmButton = {
                TextButton(onClick = {
                    showPlaidDeleteDialog = false
                    scope.launch {
                        isPlaidLoading = true
                        plaidError = null
                        // Drop sensitive visible state immediately; never restore a cached snapshot.
                        plaidSnapshot = plaidSnapshot.copy(accounts = emptyList(), transactions = emptyList())
                        try {
                            PlaidFinanceRepository.disconnectAll()
                            plaidSnapshot = PlaidSnapshot(false, emptyList(), emptyList(), emptyList())
                            Toast.makeText(context, "Sandbox connections disconnected and stored data deleted.", Toast.LENGTH_LONG).show()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            plaidError = "Cleanup did not finish. " + PlaidFinanceRepository.userFacingError(error)
                            try {
                                plaidSnapshot = PlaidFinanceRepository.getSnapshot()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) { /* Keep cleared state and the retry action. */ }
                        } finally {
                            isPlaidLoading = false
                        }
                    }
                }) { Text("Disconnect & delete") }
            },
            dismissButton = { TextButton(onClick = { showPlaidDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    val plaidLinkLauncher = rememberLauncherForActivityResult(OpenPlaidLink()) { result ->
        when (result) {
            is LinkSuccess -> scope.launch {
                isPlaidLoading = true
                isLinkingBank = false
                plaidError = null
                try {
                    PlaidFinanceRepository.exchangePublicToken(
                        publicToken = result.publicToken,
                        institutionName = result.metadata.institution?.name
                    )
                    loadPlaidSnapshot(syncFirst = true)
                    if (plaidError == null) Toast.makeText(context, "Sandbox account linked and refreshed.", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    plaidError = PlaidFinanceRepository.userFacingError(error)
                } finally {
                    isPlaidLoading = false
                    isLinkingBank = false
                }
            }
            is LinkExit -> {
                isLinkingBank = false
                val linkError = result.error
                if (linkError != null) plaidError = linkError.displayMessage ?: "Plaid Link was closed with an error."
            }
        }
    }

    LaunchedEffect(currentUser.uid) {
        plaidSnapshot = PlaidSnapshot(false, emptyList(), emptyList(), emptyList())
        loadPlaidSnapshot()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            forecastNow = System.currentTimeMillis()
        }
    }

    fun startPlaidLink() {
        val activity = context as? ComponentActivity
        if (activity == null) {
            plaidError = "Plaid Link requires an Android activity."
            Toast.makeText(context, plaidError, Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            isLinkingBank = true
            plaidError = null
            Toast.makeText(context, "Preparing secure bank connection…", Toast.LENGTH_SHORT).show()
            try {
                val token = PlaidFinanceRepository.createLinkToken()
                val configuration = linkTokenConfiguration { this.token = token }
                val session = Plaid.createPlaidLinkSession(activity, configuration)
                plaidLinkLauncher.launch(session)
            } catch (error: Exception) {
                plaidError = PlaidFinanceRepository.userFacingError(error)
                Toast.makeText(context, plaidError, Toast.LENGTH_LONG).show()
                isLinkingBank = false
            }
        }
    }

    // Keep the active cycle aligned with the device's current calendar month.
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentMonth = YearMonth.now()
            delay(60_000)
        }
    }
    val currentActiveCycleKey = currentMonth.toString()
    var selectedCycleKey by remember { mutableStateOf(currentActiveCycleKey) }
    LaunchedEffect(currentActiveCycleKey) { selectedCycleKey = currentActiveCycleKey }
    val isReadOnlyArchive = selectedCycleKey != currentActiveCycleKey

    val selectedCycleLabel = remember(selectedCycleKey) {
        runCatching {
            YearMonth.parse(selectedCycleKey).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
        }.getOrDefault(selectedCycleKey)
    }

    // Every cycle has its own bill documents and paid state. When a new month
    // starts, carry forward the previous month's bill definitions with unpaid
    // status, while preserving that previous month's paid state as an archive.
    LaunchedEffect(currentUser.householdId, selectedCycleKey, currentActiveCycleKey) {
        val hid = currentUser.householdId
        if (hid == null) {
            billsList = emptyList()
            totalAllowance = 0.0
            billsLoadError = "No household is connected to this account."
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        billsLoadError = null
        val householdRef = db.collection("households").document(hid)
        cycleListListener?.remove()
        cycleListListener = householdRef.collection("budgets").addSnapshotListener { snapshot, _ ->
            val keys = snapshot?.documents.orEmpty().map { it.id }
                .filter { runCatching { YearMonth.parse(it) }.isSuccess }
            availableCycleKeys = (keys + currentActiveCycleKey).distinct().sortedDescending()
        }
        val cycleRef = householdRef.collection("budgets").document(selectedCycleKey)
        val cycleBillsRef = cycleRef.collection("bills")

        // Attach listeners first so cached bills remain visible if the device
        // cannot reach Firestore's server.
        billsListener?.remove()
        cycleListener?.remove()
        cycleListener = cycleRef.addSnapshotListener { snapshot, _ ->
            totalAllowance = snapshot?.getDouble("allowance") ?: 0.0
        }
        billsListener = cycleBillsRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                billsLoadError = error.localizedMessage ?: "Check the connection and try again."
                isLoading = false
                return@addSnapshotListener
            }
            billsLoadError = if (snapshot?.metadata?.isFromCache == true) {
                "Firestore is offline. Showing bills saved on this device; reconnect to load the full budget."
            } else null
            billsList = snapshot?.documents.orEmpty().map { doc ->
                BillItem(
                    id = doc.id,
                    name = doc.getString("name") ?: doc.getString("title") ?: "Bill",
                    amount = doc.getDouble("amount") ?: 0.0,
                    isPaid = doc.getBoolean("isPaid") ?: false,
                    dueDate = doc.getString("dueDate") ?: "Due Soon",
                    dueInCurrentCycle = doc.getBoolean("dueInCurrentCycle") ?: true,
                    repeatsMonthly = doc.getBoolean("repeatsMonthly") ?: true,
                    paidDate = doc.getString("paidDate"),
                    paidAmount = doc.getDouble("paidAmount"),
                    paidByUid = doc.getString("paidByUid"),
                    paidByName = doc.getString("paidByName")
                )
            }
            isLoading = false
        }

        try {
            if (selectedCycleKey == currentActiveCycleKey) {
                val currentCycle = cycleRef.get(Source.SERVER).await()
                val existingCycleBills = cycleBillsRef.get(Source.SERVER).await()
                val isInitialized = currentCycle.getBoolean("billsInitialized") == true
                if (!isInitialized) {
                    val previousCycleKey = YearMonth.parse(currentActiveCycleKey).minusMonths(1).toString()
                    val previousBillsRef = householdRef.collection("budgets").document(previousCycleKey).collection("bills")
                    val previousBills = if (existingCycleBills.isEmpty) previousBillsRef.get(Source.SERVER).await() else null
                    val legacyBills = if (existingCycleBills.isEmpty && previousBills?.isEmpty != false) {
                        householdRef.collection("bills").get(Source.SERVER).await()
                    } else previousBills

                    val batch = db.batch()
                    if (existingCycleBills.isEmpty && legacyBills != null && !legacyBills.isEmpty) {
                        if (previousBills?.isEmpty != false) {
                            batch.set(householdRef.collection("budgets").document(previousCycleKey), mapOf("cycleId" to previousCycleKey), SetOptions.merge())
                        }
                        legacyBills.documents.forEach { doc ->
                            val billData = doc.data?.toMutableMap() ?: mutableMapOf()
                            billData["id"] = doc.id
                            if (previousBills?.isEmpty != false) {
                                batch.set(previousBillsRef.document(doc.id), billData)
                            }
                            // Older bills predate the recurrence flag, so treat them as monthly.
                            if (doc.getBoolean("repeatsMonthly") != false) {
                                val newCycleData = billData.toMutableMap().apply {
                                    this["isPaid"] = false
                                    remove("paidDate")
                                    remove("paidAmount")
                                    remove("paidByUid")
                                    remove("paidByName")
                                    this["repeatsMonthly"] = true
                                }
                                batch.set(cycleBillsRef.document(doc.id), newCycleData)
                            }
                        }
                    }
                    batch.set(cycleRef, mapOf("cycleId" to selectedCycleKey, "billsInitialized" to true), SetOptions.merge())
                    withTimeout(15_000L) { batch.commit().await() }
                }
            }
        } catch (error: Exception) {
            billsLoadError = error.localizedMessage ?: "Check the connection and try again."
            isLoading = false
            Toast.makeText(context, "Couldn't load this budget cycle: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }
    DisposableEffect(selectedCycleKey, currentActiveCycleKey) {
        onDispose {
            billsListener?.remove()
            billsListener = null
            cycleListener?.remove()
            cycleListener = null
            cycleListListener?.remove()
            cycleListListener = null
        }
    }

    val activeBills = remember(billsList) { billsList.sortedBy { parseDueDateDayNumber(it.dueDate) } }
    val unpaidBills = remember(activeBills) { activeBills.filterNot { it.isPaid } }
    val paidBills = remember(activeBills) { activeBills.filter { it.isPaid } }
    val paidAmount = remember(paidBills) { paidBills.sumOf { it.paidAmount ?: it.amount } }
    val unpaidAmount = remember(unpaidBills) { unpaidBills.sumOf { it.amount } }
    val paidCount = paidBills.size
    val progress = if (activeBills.isNotEmpty()) paidCount.toFloat() / activeBills.size else 0f
    val restockRecommendations = remember(pantryItems, forecastNow) {
        PantryForecastEngine.recommendations(pantryItems, forecastNow)
    }
    val today = remember(forecastNow) { java.time.Instant.ofEpochMilli(forecastNow).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    val spendingForecast = remember(restockRecommendations, today, forecastDays) {
        PantryForecastEngine.spendingForecast(restockRecommendations, today, forecastDays)
    }
    val visibleRecommendations = restockRecommendations.filter { recommendation ->
        !recommendation.suggestedDate.isAfter(today.plusDays(30)) && (
        recommendation.urgency == RestockUrgency.NEEDS_ATTENTION ||
            (recommendation.productId !in locallySnoozedRestockIds && UserPreferences.getRestockSnoozeUntil(context, recommendation.productId) <= forecastNow))
    }
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
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
            // 1. Top Bar & Header Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budget",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = appColors.textPrimary
                    )

                    // Top-Right Inset Action Capsule Pill (Wires to open Cycle Picker Sheet)
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(appColors.cardSurface)
                            .border(1.dp, appColors.cardBorder, RoundedCornerShape(18.dp))
                            .clickable { showCyclePickerSheet = true }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = selectedCycleLabel,
                                color = appColors.primaryAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "▼",
                                color = appColors.primaryAccent,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Read-Only Archive Banner if viewing archived month
            if (isReadOnlyArchive) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF78350F).copy(alpha = 0.35f))
                            .border(1.dp, Color(0xFFFBBF24).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                            Text(
                                text = "READ-ONLY ARCHIVE • $selectedCycleLabel",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // A bill ledger first: show payment progress, not a bank connection prompt.
            item {
                MonthlyBillSummaryCard(
                    totalAllowance = totalAllowance,
                    paidAmount = paidAmount,
                    unpaidAmount = unpaidAmount,
                    paidCount = paidCount,
                    billCount = activeBills.size,
                    progress = progress,
                    isReadOnly = isReadOnlyArchive,
                    onEditBudget = {
                        allowanceInput = if (totalAllowance > 0) String.format(Locale.US, "%.2f", totalAllowance) else ""
                        showAllowanceDialog = true
                    }
                )
            }

            item(key = "pantry-spending-forecast") {
                PantrySpendingForecastCard(
                    recommendations = visibleRecommendations,
                    forecast = spendingForecast,
                    selectedDays = forecastDays,
                    pendingIds = purchasePendingIds,
                    onSelectDays = { forecastDays = it },
                    onSnooze = { recommendation ->
                        UserPreferences.snoozeRestock(context, recommendation.productId,
                            System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                        locallySnoozedRestockIds = locallySnoozedRestockIds + recommendation.productId
                    },
                    onViewPantryItem = onViewPantryItem,
                    onMarkPurchased = { recommendation ->
                        val item = pantryItems.firstOrNull { it.id == recommendation.productId } ?: return@PantrySpendingForecastCard
                        if (item.id in purchasePendingIds) return@PantrySpendingForecastCard
                        purchasePendingIds = purchasePendingIds + item.id
                        onRecordPantryPurchase(item, recommendation.quantity)
                        purchasePendingIds = purchasePendingIds - item.id
                    }
                )
            }

            // Bills that still need attention appear first.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isReadOnlyArchive) "BILLS TO PAY IN ARCHIVE" else "BILLS TO PAY",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${unpaidBills.size} left",
                            color = if (unpaidBills.isEmpty()) Color(0xFF10B981) else Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!isLoading && billsLoadError != null && activeBills.isNotEmpty()) {
                item(key = "budget-bills-sync-warning") {
                    Text(
                        "Showing saved bills. Sync issue: $billsLoadError",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF007AFF))
                    }
                }
            } else if (activeBills.isNotEmpty() && unpaidBills.isEmpty()) {
                item {
                    Text(
                        text = "All bills for $selectedCycleLabel are marked paid.",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (billsLoadError != null) {
                item(key = "budget-bills-load-error") {
                    Text(
                        "Couldn't load bills: $billsLoadError",
                        color = Color(0xFFFBBF24),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)
                    )
                }
            } else if (activeBills.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No household bills found. Tap '+' to add.", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(unpaidBills, key = { it.id }) { bill ->
                    LiveBillCard(
                        bill = bill,
                        isReadOnly = isReadOnlyArchive,
                        isSaving = bill.id in savingBillIds,
                        isOverdue = !isReadOnlyArchive &&
                            parseDueDateDayNumber(bill.dueDate) < LocalDate.now().dayOfMonth,
                        onTogglePaid = {
                            if (isReadOnlyArchive) {
                                Toast.makeText(context, "Archived budget cycle ($selectedCycleLabel) is read-only.", Toast.LENGTH_SHORT).show()
                                return@LiveBillCard
                            }
                            billPaymentTarget = bill
                        },
                        onDelete = {
                            if (isReadOnlyArchive) {
                                Toast.makeText(context, "Archived budget cycle ($selectedCycleLabel) is read-only.", Toast.LENGTH_SHORT).show()
                                return@LiveBillCard
                            }
                            billDeleteTarget = bill
                        }
                    )
                }
            }

            if (!isLoading && paidBills.isNotEmpty()) {
                item(key = "paid-bills-heading") {
                    PaidBillsSection(
                        bills = paidBills,
                        isReadOnly = isReadOnlyArchive,
                        savingBillIds = savingBillIds,
                        onUndoPaid = { bill ->
                            if (bill.id in savingBillIds) return@PaidBillsSection
                            savingBillIds = savingBillIds + bill.id
                            scope.launch {
                                try {
                                    currentUser.householdId?.let { hid ->
                                        db.collection("households").document(hid)
                                            .collection("budgets").document(selectedCycleKey)
                                            .collection("bills").document(bill.id)
                                            .update(
                                                "isPaid", false,
                                                "paidDate", com.google.firebase.firestore.FieldValue.delete(),
                                                "paidAmount", com.google.firebase.firestore.FieldValue.delete(),
                                                "paidByUid", com.google.firebase.firestore.FieldValue.delete(),
                                                "paidByName", com.google.firebase.firestore.FieldValue.delete()
                                            ).await()
                                            .also { Toast.makeText(context, "${bill.name} moved back to bills to pay.", Toast.LENGTH_SHORT).show() }
                                    }
                                } catch (error: Exception) {
                                    Toast.makeText(context, "Couldn't update payment: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                                } finally {
                                    savingBillIds = savingBillIds - bill.id
                                }
                            }
                        }
                    )
                }
            }

            item(key = "optional-bank-connection") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { showPlaidSection = !showPlaidSection },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showPlaidSection) "Hide optional bank connection" else "Optional bank connection")
                    }
                    if (showPlaidSection) {
                        PlaidAccountsCard(
                            snapshot = plaidSnapshot,
                            isLoading = isPlaidLoading,
                            isLinking = isLinkingBank,
                            errorMessage = plaidError,
                            onLinkAccount = ::startPlaidLink,
                            onRefresh = { scope.launch { loadPlaidSnapshot(syncFirst = true) } },
                            onDisconnect = { showPlaidDeleteDialog = true }
                        )
                    }
                }
            }
        }

        // Exactly ONE Circular Blue + FAB Anchored at bottom = 96.dp, end = 24.dp
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (isReadOnlyArchive) listOf(Color(0xFF64748B), Color(0xFF475569)) else listOf(com.example.pantry.ui.theme.LocalAppColors.current.fabColor, com.example.pantry.ui.theme.LocalAppColors.current.primaryAccent)
                    )
                )
                .clickable {
                    if (isReadOnlyArchive) {
                        Toast.makeText(context, "Archived budget cycle ($selectedCycleLabel) is read-only.", Toast.LENGTH_SHORT).show()
                    } else {
                        showAddExpenseDialog = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Bill",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Cycle Picker Bottom Sheet Dialog (budget_cycle_picker.svg)
    billDeleteTarget?.let { bill ->
        AlertDialog(
            onDismissRequest = { billDeleteTarget = null },
            title = { Text("Remove ${bill.name}?", color = Color.White) },
            text = { Text("This removes it from $selectedCycleLabel only. Other months stay unchanged.", color = Color(0xFFCBD5E1)) },
            confirmButton = {
                TextButton(onClick = {
                    val hid = currentUser.householdId
                    if (hid.isNullOrBlank()) {
                        Toast.makeText(context, "Connect a household before removing a bill.", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (bill.id in savingBillIds) return@TextButton
                    billDeleteTarget = null
                    savingBillIds = savingBillIds + bill.id
                    scope.launch {
                        try {
                            db.collection("households").document(hid)
                                .collection("budgets").document(selectedCycleKey)
                                .collection("bills").document(bill.id).delete().await()
                            Toast.makeText(context, "${bill.name} removed from this month.", Toast.LENGTH_SHORT).show()
                        } catch (error: Exception) {
                            Toast.makeText(context, "Couldn't remove bill: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                        } finally {
                            savingBillIds = savingBillIds - bill.id
                        }
                    }
                }) { Text("Remove bill") }
            },
            dismissButton = { TextButton(onClick = { billDeleteTarget = null }) { Text("Cancel") } },
            containerColor = Color(0xFF0F172A)
        )
    }

    billPaymentTarget?.let { bill ->
        var paidAmountInput by remember(bill.id) {
            mutableStateOf(String.format(Locale.US, "%.2f", bill.amount))
        }
        AlertDialog(
            onDismissRequest = { billPaymentTarget = null },
            title = { Text("Mark ${bill.name} paid?", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confirm the amount that actually came out of your account.", color = Color(0xFFCBD5E1))
                    OutlinedTextField(
                        value = paidAmountInput,
                        onValueChange = { paidAmountInput = it },
                        label = { Text("Amount paid") },
                        prefix = { Text("$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Text("Payment date will be saved as today.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = paidAmountInput.toDoubleOrNull()
                    if (amount == null || amount < 0.0) {
                        Toast.makeText(context, "Enter a valid payment amount.", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val hid = currentUser.householdId
                    if (hid.isNullOrBlank()) {
                        Toast.makeText(context, "Connect a household before saving a payment.", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (bill.id in savingBillIds) return@TextButton
                    billPaymentTarget = null
                    savingBillIds = savingBillIds + bill.id
                    scope.launch {
                        try {
                            db.collection("households").document(hid)
                                .collection("budgets").document(selectedCycleKey)
                                .collection("bills").document(bill.id)
                                .update(
                                    "isPaid", true,
                                    "paidDate", LocalDate.now().toString(),
                                    "paidAmount", amount,
                                    "paidByUid", currentUser.uid,
                                    "paidByName", currentUser.name
                                ).await()
                            Toast.makeText(context, "${bill.name} marked paid.", Toast.LENGTH_SHORT).show()
                        } catch (error: Exception) {
                            Toast.makeText(context, "Couldn't save payment: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                        } finally {
                            savingBillIds = savingBillIds - bill.id
                        }
                    }
                }) { Text("Save payment") }
            },
            dismissButton = { TextButton(onClick = { billPaymentTarget = null }) { Text("Cancel") } },
            containerColor = Color(0xFF0F172A)
        )
    }

    // Cycle Picker Bottom Sheet Dialog (budget_cycle_picker.svg)
    if (showCyclePickerSheet) {
        BudgetCyclePickerSheetDialog(
            selectedCycleKey = selectedCycleKey,
            currentCycleKey = currentActiveCycleKey,
            availableCycleKeys = availableCycleKeys,
            themePreference = currentUser.themePreference,
            onDismiss = { showCyclePickerSheet = false },
            onSelectCycle = { newCycleKey ->
                selectedCycleKey = newCycleKey
                showCyclePickerSheet = false
            }
        )
    }

    // Add Bill Sheet Dialog
    if (showAddExpenseDialog) {
        AddBillSheetDialog(
            themePreference = currentUser.themePreference,
            isSaving = isSavingNewBill,
            onDismiss = { showAddExpenseDialog = false },
            onAdd = { newBill ->
                val hid = currentUser.householdId
                if (hid.isNullOrBlank() || isSavingNewBill) return@AddBillSheetDialog
                isSavingNewBill = true
                scope.launch {
                    try {
                        val data = hashMapOf<String, Any>(
                            "id" to newBill.id,
                            "name" to newBill.name,
                            "amount" to newBill.amount,
                            "isPaid" to newBill.isPaid,
                            "dueDate" to newBill.dueDate,
                            "dueInCurrentCycle" to newBill.dueInCurrentCycle,
                            "repeatsMonthly" to newBill.repeatsMonthly
                        )
                        if (newBill.isPaid) {
                            data["paidDate"] = LocalDate.now().toString()
                            data["paidAmount"] = newBill.amount
                            data["paidByUid"] = currentUser.uid
                            data["paidByName"] = currentUser.name
                        }
                        db.collection("households").document(hid)
                            .collection("budgets").document(selectedCycleKey)
                            .collection("bills").document(newBill.id)
                            .set(data, SetOptions.merge()).await()
                        showAddExpenseDialog = false
                        Toast.makeText(context, "${newBill.name} added to $selectedCycleLabel.", Toast.LENGTH_SHORT).show()
                    } catch (error: Exception) {
                        Toast.makeText(context, "Couldn't save bill: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                    } finally {
                        isSavingNewBill = false
                    }
                }
            }
        )
    }

    if (showAllowanceDialog) {
        AlertDialog(
            onDismissRequest = { showAllowanceDialog = false },
            title = { Text("Monthly budget", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = allowanceInput,
                    onValueChange = { allowanceInput = it },
                    label = { Text("Amount") },
                    prefix = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = allowanceInput.toDoubleOrNull()
                    val hid = currentUser.householdId
                    if (amount == null || amount <= 0.0 || hid.isNullOrBlank() || isSavingAllowance) {
                        Toast.makeText(context, "Enter a valid amount and connect a household.", Toast.LENGTH_SHORT).show()
                    } else {
                        isSavingAllowance = true
                        scope.launch {
                            try {
                                db.collection("households").document(hid)
                                    .collection("budgets").document(selectedCycleKey)
                                    .set(mapOf("cycleId" to selectedCycleKey, "allowance" to amount), SetOptions.merge()).await()
                                showAllowanceDialog = false
                                Toast.makeText(context, "Monthly limit saved.", Toast.LENGTH_SHORT).show()
                            } catch (error: Exception) {
                                Toast.makeText(context, "Couldn't save limit: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
                            } finally {
                                isSavingAllowance = false
                            }
                        }
                    }
                }, enabled = !isSavingAllowance) { Text(if (isSavingAllowance) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAllowanceDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun PlaidAccountsCard(
    snapshot: PlaidSnapshot,
    isLoading: Boolean,
    isLinking: Boolean,
    errorMessage: String?,
    onLinkAccount: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(24.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("CONNECTED ACCOUNTS", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = snapshot.institutions.joinToString().ifBlank { "Sandbox accounts" },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (snapshot.connected) {
                TextButton(onClick = onRefresh, enabled = !isLoading && !isLinking && snapshot.pendingDeletionCount == 0) { Text("Refresh") }
            }
        }

        when {
            isLoading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            !snapshot.connected -> Text(
                "Link a Plaid Sandbox test account to preview balances and transactions. Real bank accounts are not enabled.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            snapshot.accounts.isEmpty() -> Text(
                "Your bank is linked. Account details are still loading.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        }

        snapshot.accounts.forEach { account ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(account.subtype, account.mask?.let { "••$it" }).joinToString(" • ").ifBlank { account.type },
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
                Text(
                    account.currentBalance?.let { String.format(Locale.US, "$%.2f", it) } ?: "Balance unavailable",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (snapshot.transactions.isNotEmpty()) {
            HorizontalDivider(color = Color(0xFF334155))
            Text("RECENT TRANSACTIONS", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            snapshot.transactions.take(5).forEach { transaction ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(transaction.merchantName ?: transaction.name, color = Color.White, fontSize = 13.sp)
                        Text(
                            listOfNotNull(transaction.date, transaction.category, if (transaction.pending) "Pending" else null).joinToString(" • "),
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                    val amountText = if (transaction.amount >= 0.0) {
                        String.format(Locale.US, "-$%.2f", transaction.amount)
                    } else {
                        String.format(Locale.US, "+$%.2f", abs(transaction.amount))
                    }
                    Text(amountText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (snapshot.connected && snapshot.accounts.isNotEmpty()) {
            Text(
                "No transactions are available from the latest sync yet.",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(errorMessage, color = Color(0xFFFCA5A5), fontSize = 12.sp)
        }

        if (snapshot.connected) {
            if (snapshot.pendingDeletionCount > 0) {
                Text("Disconnect pending. Retry to finish removing your connection and encrypted token.", color = Color(0xFFFCA5A5), fontSize = 12.sp)
            }
            OutlinedButton(onClick = onDisconnect, enabled = !isLoading && !isLinking, modifier = Modifier.fillMaxWidth()) {
                Text(if (snapshot.pendingDeletionCount > 0) "Retry disconnect & delete" else "Disconnect & delete my Sandbox data")
            }
        }

        if (!snapshot.connected) {
            Button(onClick = onLinkAccount, enabled = !isLinking && !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text(if (isLinking) "Starting secure connection…" else "Link a bank account")
            }
        }
    }
}

@Composable
fun MonthlyBillSummaryCard(
    totalAllowance: Double,
    paidAmount: Double,
    unpaidAmount: Double,
    paidCount: Int,
    billCount: Int,
    progress: Float,
    isReadOnly: Boolean,
    onEditBudget: () -> Unit
) {
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .themedCard(24.dp)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MONTHLY BILL PROGRESS",
                    color = appColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.weight(1f)
                )
                if (!isReadOnly) {
                    TextButton(onClick = onEditBudget) {
                        Text(if (totalAllowance > 0) "Edit limit" else "Add limit")
                    }
                }
            }

            Text(
                text = "$paidCount of $billCount bills paid",
                color = appColors.textPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(appColors.background)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF10B981))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryAmount(
                    label = "PAID",
                    amount = paidAmount,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                SummaryAmount(
                    label = "STILL TO PAY",
                    amount = unpaidAmount,
                    color = Color(0xFFF87171),
                    modifier = Modifier.weight(1f)
                )
            }

            if (totalAllowance > 0.0) {
                Text(
                    text = "Optional monthly limit: ${String.format(Locale.US, "$%.2f", totalAllowance)}",
                    color = appColors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, amount: Double, color: Color, modifier: Modifier = Modifier) {
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    Column(modifier = modifier) {
        Text(label, color = appColors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(String.format(Locale.US, "$%.2f", amount), color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LiveBillCard(
    bill: BillItem,
    isReadOnly: Boolean = false,
    isSaving: Boolean = false,
    isOverdue: Boolean = false,
    onTogglePaid: () -> Unit,
    onDelete: () -> Unit
) {
    val appTheme = com.example.pantry.ui.theme.LocalAppColors.current
    val accent = if (isOverdue) Color(0xFFF87171) else appTheme.primaryAccent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(appTheme.cardSurface)
            .border(1.dp, if (isOverdue) Color(0xFFF87171).copy(alpha = 0.45f) else appTheme.cardBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = resolveBillCategoryEmoji(bill.name), fontSize = 20.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bill.name, color = appTheme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isOverdue) "Overdue • ${bill.dueDate}" else bill.dueDate,
                    color = if (isOverdue) Color(0xFFF87171) else appTheme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            Text(
                text = String.format(Locale.US, "$%.2f", bill.amount),
                color = appTheme.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isReadOnly) {
                TextButton(onClick = onDelete, enabled = !isSaving) {
                    Text("Remove", color = Color(0xFF94A3B8))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onTogglePaid,
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Saving…" else "Mark paid")
                }
            } else {
                Text(
                    text = "Not marked paid",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PaidBillsSection(
    bills: List<BillItem>,
    isReadOnly: Boolean,
    savingBillIds: Set<String>,
    onUndoPaid: (BillItem) -> Unit
) {
    var expanded by remember(bills.size) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "${if (expanded) "Hide" else "Show"} ${bills.size} paid bill${if (bills.size == 1) "" else "s"} • ${String.format(Locale.US, "$%.2f", bills.sumOf { it.paidAmount ?: it.amount })}",
                color = Color(0xFF10B981)
            )
        }
        if (expanded) {
            bills.forEach { bill ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF064E3B).copy(alpha = 0.24f))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.24f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bill.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            val paidDetails = buildList {
                                bill.paidDate?.let { add("Paid $it") }
                                bill.paidByName?.takeIf(String::isNotBlank)?.let { add("by $it") }
                            }.joinToString(" ")
                            Text(paidDetails.ifBlank { "Paid this month" }, color = Color(0xFF86EFAC), fontSize = 12.sp)
                        }
                        Text(
                            String.format(Locale.US, "$%.2f", bill.paidAmount ?: bill.amount),
                            color = Color(0xFF86EFAC),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isReadOnly) {
                        TextButton(
                            onClick = { onUndoPaid(bill) },
                            enabled = bill.id !in savingBillIds,
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(if (bill.id in savingBillIds) "Saving…" else "Undo paid") }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetCyclePickerSheetDialog(
    selectedCycleKey: String,
    currentCycleKey: String,
    availableCycleKeys: List<String>,
    themePreference: String = "DEFAULT",
    onDismiss: () -> Unit,
    onSelectCycle: (String) -> Unit
) {
    val context = LocalContext.current

    val currentMonth = YearMonth.parse(currentCycleKey)
    val cycles = availableCycleKeys.map { key ->
        val month = YearMonth.parse(key)
        BudgetCycleInfo(
            key = key,
            label = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
            isActive = key == currentCycleKey
        )
    }
    val archivedCycles = cycles.filterNot { it.isActive }

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
            // Drag Handle Grabber
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color(0xFF475569))
                )
            }

            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Budget Cycle",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Switch between the live ledger and past monthly archives",
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

            // Year & Quick Jump Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${currentMonth.year} ▼", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2563EB).copy(alpha = 0.25f))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(16.dp))
                        .clickable { onSelectCycle(currentCycleKey) }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("● Current Month", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Active Cycle Card
            val activeCycle = cycles.first()
            val isCurrentSelected = selectedCycleKey == activeCycle.key
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0369A1).copy(alpha = 0.35f), Color(0xFF0284C7).copy(alpha = 0.15f))
                        )
                    )
                    .border(1.5.dp, if (isCurrentSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(20.dp))
                    .clickable { onSelectCycle(activeCycle.key) }
                    .padding(16.dp)
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
                        // Radio Button Indicator
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isCurrentSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                .border(2.dp, if (isCurrentSelected) Color(0xFF38BDF8) else Color(0xFF64748B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrentSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = activeCycle.label,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ACTIVE CYCLE • Live Ledger",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                }
            }

            // Section Label
            Text(
                text = "ARCHIVED BUDGETS (READ-ONLY)",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )

            // Archived Cycles List
            archivedCycles.forEach { cycle ->
                val isSelected = selectedCycleKey == cycle.key
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable { onSelectCycle(cycle.key) }
                        .padding(14.dp)
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
                            // Unselected Radio Button
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                    .border(1.5.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = cycle.label,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Archived monthly record",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Start New Billing Cycle Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.2.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .clickable {
                        Toast.makeText(
                            context,
                            "Current cycle ${currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))} is active.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Start New Billing Cycle Early",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddBillSheetDialog(
    themePreference: String = "DEFAULT",
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onAdd: (BillItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var dueDateText by remember { mutableStateOf("Due 1st") }
    var isPaid by remember { mutableStateOf(false) }
    var repeatsMonthly by remember { mutableStateOf(true) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Add Household Bill",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add it to this month’s bill list",
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

            val inputColors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.6f),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("BILL NAME", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                placeholder = { Text("e.g. Electric Bill", color = Color(0xFF64748B), fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = inputColors
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("AMOUNT ($)", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )

                OutlinedTextField(
                    value = dueDateText,
                    onValueChange = { dueDateText = it },
                    label = { Text("DUE DATE", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = inputColors
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.6f))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Repeat every month", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Turn off for a one-time bill", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Switch(checked = repeatsMonthly, onCheckedChange = { repeatsMonthly = it })
            }

            // Already Paid Toggle
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
                        Text("Mark as Already Paid", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Records today and the expected amount", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    Switch(
                        checked = isPaid,
                        onCheckedChange = { isPaid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }
            }

            Button(
                onClick = {
                    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
                    if (name.isBlank() || parsedAmount <= 0.0) return@Button

                    val newBill = BillItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        amount = parsedAmount,
                        isPaid = isPaid,
                        dueDate = dueDateText.trim(),
                        dueInCurrentCycle = true,
                        repeatsMonthly = repeatsMonthly,
                        paidDate = if (isPaid) LocalDate.now().toString() else null,
                        paidAmount = if (isPaid) parsedAmount else null
                    )
                    onAdd(newBill)
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text(if (isSaving) "SAVING…" else "ADD BILL", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
