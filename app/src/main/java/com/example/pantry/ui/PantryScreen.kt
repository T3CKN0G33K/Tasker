package com.example.pantry.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.Calendar
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

data class PantryItem(
    val id: String = "",
    val name: String = "",
    val category: String = "Household",
    val quantity: Double = 1.0,
    val unit: String = "units",
    val lowStockThreshold: Double = 1.0,
    val price: Double = 0.0,
    val targetQuantity: Double = 2.0,
    val packageSize: Double = 1.0,
    val quantityStep: Double = 0.25,
    val emoji: String? = null,
    val trackingMode: String = PantryTrackingMode.LEGACY.name,
    val trackedCount: Double? = null,
    val currentPackageRemaining: Double = 0.0,
    val additionalOpenedRemaining: Double = 0.0,
    val unopenedPackages: Int = 0,
    val dailyConsumption: Double = 0.0,
    val usesPerDay: Double = 0.0,
    val amountPerUse: Double = 0.0,
    val useUnit: String = "",
    val customStockUnitsPerUseUnit: Double? = null,
    val usesPerPeriod: Double = 0.0,
    val usagePeriod: String = "DAY",
    val restockLeadDays: Double = 7.0,
    val lastStockUpdateAt: Long = 0L,
    val lastPackageOpenedAt: Long = 0L,
    val currentPackageId: String? = null,
    val currentPackageLearningEligible: Boolean = false,
    val processedEventIds: List<String> = emptyList(),
    val completedPackageRecords: List<PackageCompletion> = emptyList(),
    val purchasePriceHistory: List<Double> = emptyList()
) {
    val minThreshold: Double get() = lowStockThreshold
    val unitPrice: Double get() = price
}

private fun PantryItem.packagesToRestock(): Int {
    val amountToTarget = (targetQuantity - quantity).coerceAtLeast(0.0)
    if (amountToTarget == 0.0) return 0
    val safePackageSize = packageSize.takeIf { it > 0.0 } ?: 1.0
    return ceil(amountToTarget / safePackageSize).toInt()
}

private fun formatPantryQuantity(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else String.format(Locale.US, "%.2f", quantity)

private fun PantryTrackingMode.displayLabel(): String = when (this) {
    PantryTrackingMode.COUNT -> "simple count"
    PantryTrackingMode.PACKAGE -> "package level"
    PantryTrackingMode.CONSUMPTION -> "usage estimate"
    PantryTrackingMode.ACTIVITY -> "activity estimate"
    PantryTrackingMode.LEGACY -> "not configured"
}

fun PantryItem.resolveEmoji(): String {
    if (!emoji.isNullOrBlank()) return emoji
    return when (category.lowercase()) {
        "pet care" -> if (name.contains("dog", ignoreCase = true) || name.contains("pedigree", ignoreCase = true) || name.contains("puppy", ignoreCase = true)) "🐶" else "🐱"
        "household" -> if (name.contains("toilet", ignoreCase = true) || name.contains("paper", ignoreCase = true)) "🧻" else "🧼"
        "cleaning" -> "🧹"
        "beverages" -> "☕"
        "dry goods" -> "🥫"
        else -> "📦"
    }
}

@Composable
fun PantryScreen(
    items: List<PantryItem> = emptyList(),
    isLoading: Boolean = false,
    loadError: String? = null,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    defaultQuantityStep: Double = 0.25,
    onItemsChange: (List<PantryItem>) -> Unit = {},
    onItemUpdate: (PantryItem) -> Unit = {},
    onInventoryEvent: (PantryItem, List<PantryInventoryEvent>) -> Unit = { updated, _ -> onItemUpdate(updated) },
    onItemDelete: (String) -> Unit = {},
    onAddItem: (PantryItem) -> Unit = {},
    onAddInventoryItem: (PantryItem, List<PantryInventoryEvent>) -> Unit = { item, _ -> onAddItem(item) },
    openItemId: String? = null,
    onItemOpenHandled: () -> Unit = {},
    onFullScreenDestinationChange: (Boolean) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var itemToDetail by remember { mutableStateOf<PantryItem?>(null) }
    var itemToEdit by remember { mutableStateOf<PantryItem?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    val pantryListState = rememberLazyListState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nowMillis = System.currentTimeMillis()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(itemToDetail?.id, itemToEdit?.id) {
        onFullScreenDestinationChange(itemToDetail != null || itemToEdit != null)
    }
    DisposableEffect(Unit) {
        onDispose { onFullScreenDestinationChange(false) }
    }
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current

    val categoryList = remember(items) {
        (listOf("All", "Household", "Pet Care", "Cleaning", "Dry Goods", "Beverages") + items.map { it.category }).distinct()
    }

    val filteredList = remember(items, searchQuery, selectedCategoryFilter, nowMillis) {
        items.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "All" || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            matchesSearch && matchesCategory
        }.sortedWith(compareBy<PantryItem>({
            when (it.stockStatusAt(nowMillis)) {
                PantryStockStatus.OUT_OF_STOCK, PantryStockStatus.LOW, PantryStockStatus.ESTIMATED_EMPTY -> 0
                PantryStockStatus.RESTOCK_SOON, PantryStockStatus.ESTIMATE_UNAVAILABLE -> 1
                PantryStockStatus.STOCKED -> 2
                PantryStockStatus.SETUP_NEEDED -> 3
            }
        }, { it.name.lowercase(Locale.US) }))
    }
    LaunchedEffect(items, itemToDetail?.id) {
        val selectedId = itemToDetail?.id
        if (selectedId != null) {
            itemToDetail = items.firstOrNull { it.id == selectedId }
        }
    }
    LaunchedEffect(items, itemToEdit?.id) {
        val selectedId = itemToEdit?.id ?: return@LaunchedEffect
        if (items.none { it.id == selectedId }) {
            itemToEdit = null
            itemToDetail = null
        }
    }
    BackHandler(enabled = itemToDetail != null || itemToEdit != null) {
        if (itemToEdit != null) itemToEdit = null else itemToDetail = null
    }
    LaunchedEffect(openItemId, items) {
        val requested = openItemId ?: return@LaunchedEffect
        items.firstOrNull { it.id == requested }?.let {
            itemToDetail = it
            onItemOpenHandled()
        }
    }

    val attentionCount = items.count {
        it.stockStatusAt(nowMillis) in setOf(PantryStockStatus.RESTOCK_SOON, PantryStockStatus.LOW, PantryStockStatus.OUT_OF_STOCK, PantryStockStatus.ESTIMATED_EMPTY)
    }
    val restockForecast = remember(items, nowMillis) { PantryForecastEngine.recommendations(items, nowMillis) }
    val forecastToday = remember(nowMillis) { java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    val next30DaySpend = remember(restockForecast, forecastToday) {
        PantryForecastEngine.spendingForecast(restockForecast, forecastToday, 30)
    }
    val lowOrOutProductIds = remember(items, nowMillis) {
        items.asSequence()
            .filter { it.stockStatusAt(nowMillis) in setOf(PantryStockStatus.LOW, PantryStockStatus.OUT_OF_STOCK, PantryStockStatus.ESTIMATED_EMPTY) }
            .map { it.id }
            .toSet()
    }
    val immediateRestockRecommendations = remember(restockForecast, lowOrOutProductIds) {
        restockForecast.filter { it.productId in lowOrOutProductIds }
    }
    val immediateRestockTotal = immediateRestockRecommendations.sumOf { it.estimatedCost ?: 0.0 }
    val immediateUnpricedProducts = lowOrOutProductIds.count { id ->
        immediateRestockRecommendations.none { it.productId == id && it.estimatedCost != null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        if (itemToEdit != null) {
            val item = itemToEdit!!
            PantryItemSettingsScreen(
                item = item,
                onDismiss = { itemToEdit = null },
                onSave = { updatedItem ->
                    onItemUpdate(updatedItem)
                    itemToEdit = null
                    itemToDetail = updatedItem
                },
                onEvent = { updatedItem, events ->
                    onInventoryEvent(updatedItem, events)
                    itemToDetail = updatedItem
                    itemToEdit = null
                    nowMillis = System.currentTimeMillis()
                },
                onDelete = {
                    onItemDelete(item.id)
                    itemToEdit = null
                    itemToDetail = null
                }
            )
        } else if (itemToDetail != null) {
            val item = itemToDetail!!
            PantryItemDetailScreen(
                item = item,
                nowMillis = nowMillis,
                onBack = { itemToDetail = null },
                onConfigure = { itemToEdit = item },
                onDelete = {
                    onItemDelete(item.id)
                    itemToDetail = null
                },
                onEvent = { updated, events ->
                    onInventoryEvent(updated, events)
                    itemToDetail = updated
                    nowMillis = System.currentTimeMillis()
                }
            )
        } else {
        LazyColumn(
            state = pantryListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Top App Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "My Pantry",
                            color = appColors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showAddItemDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = appColors.primaryAccent,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add item", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Search Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(appColors.cardSurface.copy(alpha = 0.9f))
                                    .border(1.2.dp, appColors.cardBorder, CircleShape)
                                    .clickable { isSearchExpanded = !isSearchExpanded },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = appColors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Search Input Bar
                    if (isSearchExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .svgCardContainer(cornerRadius = 24.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = appColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = appColors.textPrimary, fontSize = 15.sp),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Search Pantry...", color = appColors.textSecondary, fontSize = 15.sp)
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = appColors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Compact overview: stock count, attention count, and tracked method count.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(appColors.cardSurface)
                            .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OverviewMetric("Products", items.size.toString(), appColors.textPrimary)
                            OverviewMetric("Need attention", attentionCount.toString(), if (attentionCount > 0) Color(0xFFF4B942) else Color(0xFF42C983))
                            OverviewMetric("Tracked", items.count { PantryTrackingMode.fromStored(it.trackingMode) != PantryTrackingMode.LEGACY }.toString(), appColors.primaryAccent)
                        }
                    }

                    // Category Filter Pills Row
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categoryList) { category ->
                            val isSelected = selectedCategoryFilter == category
                            val appColors = com.example.pantry.ui.theme.LocalAppColors.current
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(if (isSelected) appColors.primaryAccent else appColors.cardSurface)
                                    .border(1.dp, if (isSelected) appColors.primaryAccent else appColors.cardBorder, RoundedCornerShape(17.dp))
                                    .clickable { selectedCategoryFilter = category }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.White else appColors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            if (restockForecast.isNotEmpty()) {
                item(key = "upcoming-household-needs") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(appColors.cardSurface)
                            .border(1.dp, appColors.cardBorder, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("UPCOMING HOUSEHOLD NEEDS", color = appColors.primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                        val urgentCount = restockForecast.count { it.urgency == RestockUrgency.NEEDS_ATTENTION }
                        Text("${restockForecast.size} product${if (restockForecast.size == 1) "" else "s"} may need replenishment${if (urgentCount > 0) " · $urgentCount need attention" else ""}", color = appColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (immediateRestockRecommendations.any { it.estimatedCost != null } || immediateUnpricedProducts > 0) {
                                "Restock now · low/out: ${String.format(Locale.US, "$%.2f", immediateRestockTotal)}${if (immediateUnpricedProducts > 0) " · $immediateUnpricedProducts price(s) unknown" else ""}"
                            } else "No products are currently low or out of stock",
                            color = appColors.textSecondary, fontSize = 12.sp
                        )
                        Text(
                            if (next30DaySpend.pricedProducts > 0) "30-day forecast (includes future needs): ${String.format(Locale.US, "$%.2f", next30DaySpend.total)}${if (next30DaySpend.unpricedProducts > 0) " · ${next30DaySpend.unpricedProducts} price(s) unknown" else ""}"
                            else "No saved prices for the upcoming recommendations",
                            color = appColors.textSecondary, fontSize = 12.sp
                        )
                        restockForecast.firstOrNull()?.let { Text("Next suggested purchase: ${it.productName} · ${it.suggestedDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US))}", color = appColors.textSecondary, fontSize = 12.sp) }
                        Text("Forecast only; it does not change your paid expenses.", color = appColors.textSecondary, fontSize = 10.sp)
                    }
                }
            }

            if (loadError != null && items.isNotEmpty()) {
                item(key = "pantry-sync-warning") {
                    Text("Showing saved inventory. Sync issue: $loadError", color = Color(0xFFF4B942), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                }
            }

            if (isLoading && items.isEmpty()) {
                item(key = "pantry-loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = appColors.primaryAccent)
                    }
                }
            } else if (loadError != null && items.isEmpty()) {
                item(key = "pantry-error") {
                    Text(loadError, color = Color(0xFFF4B942), fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center)
                }
            } else if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategoryFilter != "All") "No matching items found." else "Your Pantry is empty. Tap Add item to get started.",
                            color = appColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    PantryItemCard(
                        item = item,
                        nowMillis = nowMillis,
                        onOpenDetails = { itemToDetail = item }
                    )
                }
            }
        }

        }
    }

    // Add Item Dialog
    if (showAddItemDialog) {
        AddItemCenteredDialog(
            themePreference = themePreference,
            defaultQuantityStep = defaultQuantityStep,
            onDismiss = { showAddItemDialog = false },
            onSave = { newItem ->
                onAddItem(newItem)
                showAddItemDialog = false
            },
            onSaveWithEvents = { newItem, events ->
                onAddInventoryItem(newItem, events)
                showAddItemDialog = false
            }
        )
    }

}

@Composable
fun PantryItemCard(
    item: PantryItem,
    nowMillis: Long,
    onOpenDetails: () -> Unit
) {
    val mode = PantryTrackingMode.fromStored(item.trackingMode)
    val estimate = item.estimateAt(nowMillis)
    val status = item.stockStatusAt(nowMillis)
    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    val (statusText, statusColor) = when (status) {
        PantryStockStatus.STOCKED -> "Stocked" to Color(0xFF42C983)
        PantryStockStatus.RESTOCK_SOON -> "Restock soon" to Color(0xFFF4B942)
        PantryStockStatus.LOW -> "Low" to Color(0xFFEF6262)
        PantryStockStatus.OUT_OF_STOCK -> "Out of stock" to Color(0xFFEF6262)
        PantryStockStatus.ESTIMATED_EMPTY -> "Estimated empty" to Color(0xFFEF6262)
        PantryStockStatus.SETUP_NEEDED -> "Tracking setup needed" to appColors.textSecondary
        PantryStockStatus.ESTIMATE_UNAVAILABLE -> "Estimate unavailable" to Color(0xFFF4B942)
    }
    val detailText = when (mode) {
        PantryTrackingMode.LEGACY -> "Saved amount: ${formatLegacyAmount(item.quantity)} ${item.unit} · confirm stock to set up tracking"
        PantryTrackingMode.COUNT -> {
            val count = estimate.totalRemaining ?: 0.0
            "${formatPantryQuantity(count)} ${if (count == 1.0) item.unit.singularize() else item.unit} available · target ${formatPantryQuantity(item.targetQuantity)}"
        }
        PantryTrackingMode.PACKAGE -> "Open package about ${approximatePackagePercent(estimate.currentPackageRemaining, item.packageSize)}% · ${estimate.unopenedPackages} sealed"
        PantryTrackingMode.CONSUMPTION -> "${estimate.daysRemaining?.let { "About ${ceil(it).toInt().coerceAtLeast(0)} days of supply" } ?: "Configure use to estimate"} · ${estimate.unopenedPackages} unopened"
        PantryTrackingMode.ACTIVITY -> {
            val estimatedUses = if (item.usesPerDay > 0.0 && item.dailyConsumption > 0.0 && estimate.totalRemaining != null)
                ceil(estimate.totalRemaining / (item.dailyConsumption / item.usesPerDay)).toInt().coerceAtLeast(0)
            else null
            val activityLabel = when {
                item.name.contains("laundry", ignoreCase = true) -> "loads"
                item.name.contains("dishwasher", ignoreCase = true) -> "cycles"
                else -> "uses"
            }
            listOfNotNull(
                estimatedUses?.let { "About $it $activityLabel left" },
                estimate.daysRemaining?.let { "~${ceil(it).toInt().coerceAtLeast(0)} days" },
                "${estimate.unopenedPackages} unopened"
            ).joinToString(" · ")
        }
    }
    Card(
        onClick = onOpenDetails,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.cardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (status == PantryStockStatus.LOW || status == PantryStockStatus.OUT_OF_STOCK || status == PantryStockStatus.ESTIMATED_EMPTY) Color(0xFFEF6262).copy(alpha = 0.45f) else appColors.cardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(item.resolveEmoji(), fontSize = 23.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.name, color = appColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(statusText.uppercase(), color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(item.category, color = appColors.textSecondary, fontSize = 10.sp, maxLines = 1)
                Text(detailText, color = appColors.textSecondary, fontSize = 12.sp, maxLines = 2)
            }
            Text("Details ›", color = appColors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun approximatePackagePercent(remaining: Double?, size: Double): Int =
    if (remaining == null || size <= 0.0) 0 else ((remaining / size).coerceIn(0.0, 1.0) * 100).roundToInt()

private fun formatLegacyAmount(value: Double): String {
    if (!value.isFinite()) return "unknown"
    val whole = kotlin.math.floor(value).toInt()
    val fraction = value - whole
    val quarter = when {
        kotlin.math.abs(fraction - 0.25) < 0.03 -> "¼"
        kotlin.math.abs(fraction - 0.5) < 0.03 -> "½"
        kotlin.math.abs(fraction - 0.75) < 0.03 -> "¾"
        fraction < 0.03 -> ""
        else -> " ${String.format(Locale.US, "%.2f", fraction)}"
    }
    return when {
        whole == 0 && fraction < 0.03 -> "0"
        whole == 0 -> quarter.trimStart()
        fraction < 0.03 -> whole.toString()
        else -> "$whole$quarter"
    }
}

@Composable
private fun OverviewMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = com.example.pantry.ui.theme.LocalAppColors.current.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun PantryItemDetailScreen(
    item: PantryItem,
    nowMillis: Long,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    onEvent: (PantryItem, List<PantryInventoryEvent>) -> Unit
) {
    val context = LocalContext.current
    val colors = com.example.pantry.ui.theme.LocalAppColors.current
    val mode = PantryTrackingMode.fromStored(item.trackingMode)
    val estimate = item.estimateAt(nowMillis)
    val status = item.stockStatusAt(nowMillis)
    val statusPair = when (status) {
        PantryStockStatus.STOCKED -> "Stocked" to Color(0xFF42C983)
        PantryStockStatus.RESTOCK_SOON -> "Restock soon" to Color(0xFFF4B942)
        PantryStockStatus.LOW -> "Low" to Color(0xFFEF6262)
        PantryStockStatus.OUT_OF_STOCK -> "Out of stock" to Color(0xFFEF6262)
        PantryStockStatus.ESTIMATED_EMPTY -> "Estimated empty" to Color(0xFFEF6262)
        PantryStockStatus.SETUP_NEEDED -> "Tracking setup needed" to colors.textSecondary
        PantryStockStatus.ESTIMATE_UNAVAILABLE -> "Estimate unavailable" to Color(0xFFF4B942)
    }
    var showUsedSome by remember(item.id) { mutableStateOf(false) }
    var showPurchase by remember(item.id) { mutableStateOf(false) }
    var showCorrection by remember(item.id) { mutableStateOf(false) }
    var showOpenNew by remember(item.id) { mutableStateOf(false) }
    var showFinish by remember(item.id) { mutableStateOf(false) }
    var showDelete by remember(item.id) { mutableStateOf(false) }
    var purchaseCount by remember(item.id) { mutableStateOf("1") }
    var purchaseTotal by remember(item.id) { mutableStateOf("") }
    var purchaseDate by remember(item.id) { mutableLongStateOf(nowMillis) }
    var correctionCurrent by remember(item.id) { mutableStateOf(formatPantryQuantity(estimate.currentPackageRemaining ?: estimate.totalRemaining ?: item.quantity)) }
    var correctionOther by remember(item.id) { mutableStateOf(formatPantryQuantity(estimate.additionalOpenedRemaining)) }
    var correctionBackups by remember(item.id) { mutableStateOf(estimate.unopenedPackages.toString()) }
    var customUse by remember(item.id) { mutableStateOf("") }

    fun emit(transition: PantryTransition) {
        if (transition.events.isNotEmpty()) onEvent(transition.item, transition.events)
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Pantry", tint = colors.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(item.category, color = colors.textSecondary, fontSize = 12.sp)
            }
            TextButton(onClick = onConfigure) { Text("Settings", color = colors.primaryAccent) }
        }
        HorizontalDivider(color = colors.cardBorder)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.resolveEmoji(), fontSize = 28.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Inventory status", color = colors.textSecondary, fontSize = 12.sp)
                }
                Text(statusPair.first.uppercase(), color = statusPair.second, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            when (mode) {
                PantryTrackingMode.LEGACY -> Text(
                    "Saved amount: ${formatLegacyAmount(item.quantity)} ${item.unit}. Package state is unknown until you confirm it.",
                    color = colors.textSecondary, fontSize = 13.sp
                )
                PantryTrackingMode.COUNT -> Text(
                    "${formatPantryQuantity(estimate.totalRemaining ?: 0.0)} ${item.unit} available · restock target ${formatPantryQuantity(item.targetQuantity)}",
                    color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
                PantryTrackingMode.PACKAGE -> Text(
                    "Open package is about ${approximatePackagePercent(estimate.currentPackageRemaining, item.packageSize)}% full. ${estimate.unopenedPackages} unopened ${if (estimate.unopenedPackages == 1) "package" else "packages"}.",
                    color = colors.textPrimary, fontSize = 15.sp
                )
                PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
                    Text(
                        estimate.daysRemaining?.let { "About ${ceil(it).toInt().coerceAtLeast(0)} days of supply remain." }
                            ?: "Set a valid package size and usage rate to calculate an estimate.",
                        color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Current package: about ${approximatePackagePercent(estimate.currentPackageRemaining, item.packageSize)}% · ${estimate.unopenedPackages} unopened",
                        color = colors.textSecondary, fontSize = 12.sp
                    )
                    if (estimate.depletionAtMillis != null) {
                        Text("Estimated to run out around ${java.text.SimpleDateFormat("MMM d", Locale.getDefault()).format(java.util.Date(estimate.depletionAtMillis))}.", color = colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            HorizontalDivider(color = colors.cardBorder)
            Text("Usage insights", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (item.completedPackageRecords.any { it.verifiedFullStart }) {
                val typicalDays = item.completedPackageRecords.filter { it.verifiedFullStart }.takeLast(5).map { it.durationDays }.average()
                if (typicalDays.isFinite()) Text(
                    "Your household usually finishes this package in about ${typicalDays.roundToInt()} days. ${item.predictionConfidence()}.",
                    color = Color(0xFF7DD3FC), fontSize = 12.sp
                )
            } else if (mode == PantryTrackingMode.CONSUMPTION || mode == PantryTrackingMode.ACTIVITY) {
                Text("This is a starting estimate. It improves after you confirm full packages as finished.", color = colors.textSecondary, fontSize = 12.sp)
            }

            HorizontalDivider(color = colors.cardBorder)
            Text("Manage supply", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (mode == PantryTrackingMode.LEGACY) {
                Button(onClick = onConfigure, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Confirm stock and set up tracking") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showUsedSome = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (mode == PantryTrackingMode.COUNT) "Used one" else "Used some")
                    }
                    OutlinedButton(onClick = { showPurchase = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Bought more") }
                }
                if (mode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = {
                            if (estimate.unopenedPackages <= 0) Toast.makeText(context, "Buy another package before opening one.", Toast.LENGTH_SHORT).show()
                            else showOpenNew = true
                        }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Opened new") }
                        OutlinedButton(onClick = { showFinish = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Finished package") }
                    }
                }
                OutlinedButton(onClick = { showCorrection = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Correct stock estimate") }
            }

            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Tracking settings") }
            TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Delete product", color = Color(0xFFEF6262)) }
        }
    }

    if (showUsedSome) {
        if (mode == PantryTrackingMode.COUNT) {
            AlertDialog(
                onDismissRequest = { showUsedSome = false },
                title = { Text("Record one used") },
                text = { Text("This subtracts one ${item.unit.singularize()} from the confirmed count.") },
                confirmButton = { TextButton(onClick = { emit(item.recordApproximateUse(0.0, nowMillis)); showUsedSome = false }) { Text("Record") } },
                dismissButton = { TextButton(onClick = { showUsedSome = false }) { Text("Cancel") } }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showUsedSome = false },
                title = { Text("About how much was used?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("These are rough package estimates, not measured amounts.", color = Color.LightGray)
                        Row {
                            listOf(0.05 to "A little (~5%)", 0.15 to "Some (~15%)", 0.30 to "A lot (~30%)").forEach { (fraction, label) ->
                                TextButton(onClick = { emit(item.recordApproximateUse(fraction, nowMillis)); showUsedSome = false }) { Text(label, fontSize = 10.sp) }
                            }
                        }
                        OutlinedTextField(value = customUse, onValueChange = { customUse = it }, label = { Text("Custom amount (${item.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                },
                confirmButton = { TextButton(onClick = {
                    val amount = customUse.toDoubleOrNull()
                    if (amount != null && amount >= 0 && item.packageSize > 0) {
                        emit(item.recordApproximateUse(amount / item.packageSize, nowMillis))
                        customUse = ""
                        showUsedSome = false
                    }
                }) { Text("Record custom") } },
                dismissButton = { TextButton(onClick = { showUsedSome = false }) { Text("Cancel") } }
            )
        }
    }

    if (showOpenNew) {
        AlertDialog(
            onDismissRequest = { showOpenNew = false },
            title = { Text("Open a new package?") },
            text = { Text("This will record the package opening and preserve any remaining open supply.") },
            confirmButton = {
                TextButton(onClick = {
                    val transition = item.openNewPackage(System.currentTimeMillis())
                    if (transition == null) Toast.makeText(context, "No unopened package is available.", Toast.LENGTH_SHORT).show()
                    else emit(transition)
                    showOpenNew = false
                }) { Text("Open package") }
            },
            dismissButton = { TextButton(onClick = { showOpenNew = false }) { Text("Cancel") } }
        )
    }

    if (showPurchase) {
        AlertDialog(
            onDismissRequest = { showPurchase = false },
            title = { Text("Record a purchase") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = purchaseCount, onValueChange = { purchaseCount = it.filter(Char::isDigit).take(3) }, label = { Text("Packages bought") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = purchaseTotal, onValueChange = { purchaseTotal = it }, label = { Text("Total spent (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    TextButton(onClick = {
                        val calendar = Calendar.getInstance().apply { timeInMillis = purchaseDate }
                        DatePickerDialog(context, { _, year, month, day ->
                            calendar.set(year, month, day, 12, 0, 0)
                            purchaseDate = calendar.timeInMillis
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("Purchase date: ${java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(purchaseDate))}") }
                }
            },
            confirmButton = { TextButton(onClick = {
                val count = purchaseCount.toIntOrNull() ?: 0
                val total = purchaseTotal.toDoubleOrNull()
                if (count > 0 && (total == null || total.isFinite() && total >= 0)) {
                    emit(item.addPurchasedPackages(count, nowMillis, purchaseTotal = total, purchaseDateMillis = purchaseDate))
                    purchaseCount = "1"
                    purchaseTotal = ""
                    purchaseDate = nowMillis
                    showPurchase = false
                }
            }) { Text("Add purchase") } },
            dismissButton = { TextButton(onClick = { showPurchase = false }) { Text("Cancel") } }
        )
    }

    if (showCorrection) {
        AlertDialog(
            onDismissRequest = { showCorrection = false },
            title = { Text("Correct inventory") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mode == PantryTrackingMode.COUNT) {
                        OutlinedTextField(value = correctionCurrent, onValueChange = { correctionCurrent = it }, label = { Text("Confirmed count (${item.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    } else {
                        if (item.packageSize > 0.0) {
                            Text("Quick estimate for the current package", color = Color.LightGray, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.25 to "About ¼", 0.5 to "About ½", 0.75 to "About ¾").forEach { (fraction, label) ->
                                    TextButton(onClick = { correctionCurrent = formatPantryQuantity(item.packageSize * fraction) }) { Text(label) }
                                }
                            }
                        }
                        OutlinedTextField(value = correctionCurrent, onValueChange = { correctionCurrent = it }, label = { Text("Open package left (${item.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(value = correctionOther, onValueChange = { correctionOther = it }, label = { Text("Other opened amount (${item.unit})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(value = correctionBackups, onValueChange = { correctionBackups = it.filter(Char::isDigit) }, label = { Text("Unopened packages") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                val current = correctionCurrent.toDoubleOrNull()
                val other = correctionOther.toDoubleOrNull() ?: 0.0
                val backups = correctionBackups.toIntOrNull() ?: 0
                val valid = current != null && current.isFinite() && current >= 0 && other.isFinite() && other >= 0 && backups >= 0
                if (valid) {
                    emit(item.correctInventory(current!!, other, backups, nowMillis))
                    showCorrection = false
                } else Toast.makeText(context, "Enter non-negative inventory amounts.", Toast.LENGTH_SHORT).show()
            }) { Text("Save correction") } },
            dismissButton = { TextButton(onClick = { showCorrection = false }) { Text("Cancel") } }
        )
    }

    if (showFinish) {
        AlertDialog(
            onDismissRequest = { showFinish = false },
            title = { Text("Confirm package is finished?") },
            text = { Text("This saves a real completion event. Predicted empty dates are never treated as confirmed use.") },
            confirmButton = { TextButton(onClick = { emit(item.finishCurrentPackage(nowMillis)); showFinish = false }) { Text("Confirm finished") } },
            dismissButton = { TextButton(onClick = { showFinish = false }) { Text("Cancel") } }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete ${item.name}?") },
            text = { Text("This removes this product from the household inventory. The app does not provide an undo.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFEF6262)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun String.singularize(): String {
    val lower = this.trim().lowercase(Locale.US)
    return when {
        lower.endsWith("rolls") -> "roll"
        lower.endsWith("bags") -> "bag"
        lower.endsWith("boxes") -> "box"
        lower.endsWith("units") -> "unit"
        lower.endsWith("s") && lower.length > 1 -> lower.dropLast(1)
        else -> lower.ifBlank { "unit" }
    }
}

@Composable
fun AddItemCenteredDialog(
    themePreference: String = "DEFAULT",
    defaultQuantityStep: Double = 0.25,
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onSaveWithEvents: (PantryItem, List<PantryInventoryEvent>) -> Unit = { item, _ -> onSave(item) }
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Dry Goods") }
    var unit by remember { mutableStateOf("Units") }
    var quantityText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }
    var lowStockThresholdText by remember { mutableStateOf("1") }
    var targetQuantityText by remember { mutableStateOf("2") }
    var packageSizeText by remember { mutableStateOf("1") }
    var currentPackageText by remember { mutableStateOf("") }
    var backupPackagesText by remember { mutableStateOf("0") }
    var usageCountText by remember { mutableStateOf("1") }
    var amountPerUseText by remember { mutableStateOf("1") }
    var useUnitText by remember { mutableStateOf("") }
    var usagePeriod by remember { mutableStateOf("DAY") }
    var restockDaysText by remember { mutableStateOf("7") }
    var customConversionText by remember { mutableStateOf("") }
    var initialPackageIsFull by remember { mutableStateOf(false) }
    var trackingMode by remember { mutableStateOf(PantryTrackingMode.COUNT) }
    var trackingModeChosen by remember { mutableStateOf(false) }
    var quantityStepText by remember { mutableStateOf(formatPantryQuantity(defaultQuantityStep)) }
    var selectedEmoji by remember { mutableStateOf("📦") }
    var isScanning by remember { mutableStateOf(false) }
    var categoryChosen by remember { mutableStateOf(false) }
    var emojiChosen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val categories = listOf("Dry Goods", "Beverages", "Pet Care", "Household", "Cleaning")

    LaunchedEffect(name) {
        if (!trackingModeChosen && name.isNotBlank()) trackingMode = PantryItem(name = name).suggestedTrackingMode()
        if (!categoryChosen && name.isNotBlank()) {
            category = when {
                listOf("dog", "cat", "pet", "puppy", "litter").any { name.contains(it, true) } -> "Pet Care"
                listOf("bleach", "cleaner", "detergent", "sponge", "trash", "soap").any { name.contains(it, true) } -> "Cleaning"
                listOf("milk", "juice", "coffee", "water", "soda").any { name.contains(it, true) } -> "Beverages"
                else -> "Household"
            }
        }
    }
    LaunchedEffect(name, category) {
        if (!emojiChosen && name.isNotBlank()) selectedEmoji = PantryItem(name = name, category = category).resolveEmoji()
    }

    fun startBarcodeScan() {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_ALL_FORMATS
                )
                .enableAutoZoom()
                .build()

            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        isScanning = true
                        lookupBarcodeProduct(
                            barcode = rawValue,
                            onSuccess = { scannedName, scannedCategory ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    isScanning = false
                                    name = scannedName
                                    category = scannedCategory
                                    categoryChosen = true
                                }
                            },
                            onError = { _ ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    isScanning = false
                                }
                            }
                        )
                    }
                }
                .addOnCanceledListener {
                    isScanning = false
                }
                .addOnFailureListener {
                    isScanning = false
                }
        } catch (_: Exception) {
            isScanning = false
        }
    }

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        themePreference = themePreference
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Item to Pantry",
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
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Item Name", color = Color.White.copy(alpha = 0.70f)) },
                trailingIcon = {
                    IconButton(onClick = { startBarcodeScan() }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = Color(0xFF38BDF8))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Package price (optional)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Stock unit (e.g. lb, fl oz, bottle)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            Text("Category", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(selected = category == cat, onClick = { category = cat; categoryChosen = true }, label = { Text(cat) })
                }
            }

            Text("How should Pantry track this?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(
                    PantryTrackingMode.COUNT to "Simple count",
                    PantryTrackingMode.PACKAGE to "Approx. amount",
                    PantryTrackingMode.CONSUMPTION to "Usage over time",
                    PantryTrackingMode.ACTIVITY to "By activity"
                )) { (mode, label) ->
                    FilterChip(selected = trackingMode == mode, onClick = { trackingMode = mode; trackingModeChosen = true }, label = { Text(label) })
                }
            }
            val trackingHelp = when (trackingMode) {
                PantryTrackingMode.COUNT -> "Track individual items, such as rolls, sponges, or trash bags."
                PantryTrackingMode.PACKAGE -> "Track an approximate amount in use and any sealed packages."
                PantryTrackingMode.CONSUMPTION -> "Estimate remaining days from a known amount used per day or week."
                PantryTrackingMode.ACTIVITY -> "Estimate stock from repeated activities, such as loads of laundry."
                PantryTrackingMode.LEGACY -> "Choose a tracking method for new Pantry items."
            }
            Text(
                text = if (name.isNotBlank() && !trackingModeChosen) "Suggested: ${trackingMode.displayLabel()}. $trackingHelp" else trackingHelp,
                color = Color(0xFF7DD3FC),
                fontSize = 12.sp
            )

            if (trackingMode == PantryTrackingMode.COUNT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it.filter(Char::isDigit) },
                        label = { Text("Items available") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = inputColors
                    )
                    OutlinedTextField(
                        value = lowStockThresholdText,
                        onValueChange = { lowStockThresholdText = it },
                        label = { Text("Alert below") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors
                    )
                }
                if (trackingMode == PantryTrackingMode.COUNT) {
                    OutlinedTextField(value = packageSizeText, onValueChange = { packageSizeText = it }, label = { Text("Items in one purchase package") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = inputColors)
                    OutlinedTextField(value = targetQuantityText, onValueChange = { targetQuantityText = it }, label = { Text("Restock when fewer than this remain") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = inputColors)
                }
            }

            if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = packageSizeText, onValueChange = { packageSizeText = it }, label = { Text("Package size ($unit)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors)
                    OutlinedTextField(value = currentPackageText, onValueChange = { currentPackageText = it }, label = { Text("Amount in open package (0 if none)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors)
                }
                OutlinedTextField(value = backupPackagesText, onValueChange = { backupPackagesText = it.filter(Char::isDigit) }, label = { Text("Sealed packages available") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = inputColors)
                if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = usagePeriod == "DAY", onClick = { usagePeriod = "DAY" }, label = { Text("Per day") })
                        FilterChip(selected = usagePeriod == "WEEK", onClick = { usagePeriod = "WEEK" }, label = { Text("Per week") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = usageCountText, onValueChange = { usageCountText = it }, label = { Text(if (usagePeriod == "WEEK") "Uses per week" else "Uses per day") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors)
                        OutlinedTextField(value = amountPerUseText, onValueChange = { amountPerUseText = it }, label = { Text("Amount each use") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors)
                    }
                    OutlinedTextField(value = useUnitText, onValueChange = { useUnitText = it }, label = { Text("Unit per use (e.g. oz, ml, scoop)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors)
                    val dailyRate = calculateDailyConsumption(usageCountText.toDoubleOrNull() ?: 0.0, amountPerUseText.toDoubleOrNull() ?: 0.0, useUnitText, unit, customConversionText.toDoubleOrNull(), usagePeriod)
                    if (dailyRate == null && useUnitText.isNotBlank()) {
                        OutlinedTextField(value = customConversionText, onValueChange = { customConversionText = it }, label = { Text("Known $unit per ${useUnitText} (optional conversion)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors)
                    }
                    OutlinedTextField(value = restockDaysText, onValueChange = { restockDaysText = it }, label = { Text("Restock warning (days of supply)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors)
                    if (dailyRate != null) {
                        val current = currentPackageText.toDoubleOrNull() ?: 0.0
                        val backups = backupPackagesText.toIntOrNull() ?: 0
                        val days = (current + backups * (packageSizeText.toDoubleOrNull() ?: 0.0)) / dailyRate
                        Text("Based on this routine, your listed stock should last about ${ceil(days).toInt()} days.", color = Color(0xFF7DD3FC), fontSize = 12.sp)
                    } else Text("A known unit conversion is needed before this estimate can be shown.", color = Color(0xFFFBBF24), fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = initialPackageIsFull, onCheckedChange = { initialPackageIsFull = it })
                        Text("Open package is full and was opened today", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }

            val addSummary = when (trackingMode) {
                PantryTrackingMode.LEGACY -> "Choose a tracking method to add this product."
                PantryTrackingMode.COUNT -> "${quantityText.toIntOrNull() ?: 0} ${unit} tracked as individual items."
                else -> "${backupPackagesText.toIntOrNull() ?: 0} unopened packages plus about ${formatPantryQuantity(if (initialPackageIsFull) packageSizeText.toDoubleOrNull() ?: 0.0 else currentPackageText.toDoubleOrNull() ?: 0.0)} $unit in the open package."
            }
            Text("Review: $addSummary", color = Color(0xFF7DD3FC), fontSize = 12.sp)

            // Emoji Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(1.dp, Color.White.copy(alpha = 0.40f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selectedEmoji, fontSize = 26.sp, textAlign = TextAlign.Center)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Badge Emoji", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("🐶", "🐱", "🐾", "🧻", "🧼", "🧹", "🥫", "🍞", "🥛", "☕", "📦")) { emo ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedEmoji == emo) Color(0xFF007AFF) else Color.White.copy(alpha = 0.08f))
                                    .clickable { selectedEmoji = emo; emojiChosen = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emo, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                    val parsedQty = quantityText.toDoubleOrNull() ?: 0.0
                    val threshold = lowStockThresholdText.toDoubleOrNull() ?: 1.0
                    val target = targetQuantityText.toDoubleOrNull() ?: 2.0
                    val packageSize = packageSizeText.toDoubleOrNull() ?: 1.0
                    val quantityStep = quantityStepText.toDoubleOrNull() ?: defaultQuantityStep
                    val count = quantityText.toIntOrNull()
                    val currentAmount = if (initialPackageIsFull) packageSize else currentPackageText.toDoubleOrNull() ?: 0.0
                    val backups = backupPackagesText.toIntOrNull()
                    val uses = usageCountText.toDoubleOrNull()
                    val perUse = amountPerUseText.toDoubleOrNull()
                    val dailyRate = calculateDailyConsumption(uses ?: 0.0, perUse ?: 0.0, useUnitText.trim(), unit.trim(), customConversionText.toDoubleOrNull(), usagePeriod)
                    val restockDays = restockDaysText.toDoubleOrNull()
                    val invalidTracking = when (trackingMode) {
                        PantryTrackingMode.LEGACY -> !parsedQty.isFinite() || parsedQty < 0 || !threshold.isFinite() || threshold < 0
                        PantryTrackingMode.COUNT -> count == null || count < 0 || !packageSize.isFinite() || packageSize < 1 || packageSize % 1.0 != 0.0 || !threshold.isFinite() || threshold < 0 || !target.isFinite() || target < threshold
                        PantryTrackingMode.PACKAGE -> !packageSize.isFinite() || packageSize <= 0 || !currentAmount.isFinite() || currentAmount < 0 || currentAmount > packageSize || backups == null || backups < 0
                        PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> !packageSize.isFinite() || packageSize <= 0 || !currentAmount.isFinite() || currentAmount < 0 || currentAmount > packageSize || backups == null || backups < 0 || dailyRate == null || restockDays == null || !restockDays.isFinite() || restockDays < 0
                    }
                    val validPrice = priceText.isBlank() || (priceText.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)
                    if (unit.isBlank() || !parsedPrice.isFinite() || parsedPrice < 0 || !validPrice || !quantityStep.isFinite() || quantityStep <= 0 || !target.isFinite() || target < 0 || !threshold.isFinite() || threshold < 0 || invalidTracking) {
                        Toast.makeText(context, "Check the item name, stock amount, package size, usage conversion, and restock target.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val now = System.currentTimeMillis()
                    val packageId = UUID.randomUUID().toString().takeIf { currentAmount > 0 && trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY) }
                    val learnedStartKnown = initialPackageIsFull && trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)
                    val newItem = PantryItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        category = category,
                        quantity = when (trackingMode) {
                            PantryTrackingMode.LEGACY -> parsedQty
                            PantryTrackingMode.COUNT -> count!!.toDouble()
                            else -> currentAmount + (backups ?: 0) * packageSize
                        },
                        unit = unit.trim(),
                        lowStockThreshold = threshold,
                        price = parsedPrice,
                        targetQuantity = target,
                        packageSize = packageSize,
                        quantityStep = quantityStep,
                        emoji = selectedEmoji,
                        trackingMode = trackingMode.name,
                        trackedCount = if (trackingMode == PantryTrackingMode.COUNT) count!!.toDouble() else null,
                        currentPackageRemaining = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) currentAmount else 0.0,
                        unopenedPackages = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) backups ?: 0 else 0,
                        dailyConsumption = dailyRate ?: 0.0,
                        usesPerDay = if (usagePeriod == "WEEK") (uses ?: 0.0) / 7.0 else uses ?: 0.0,
                        usesPerPeriod = uses ?: 0.0,
                        usagePeriod = usagePeriod,
                        amountPerUse = perUse ?: 0.0,
                        useUnit = useUnitText.trim(),
                        customStockUnitsPerUseUnit = customConversionText.toDoubleOrNull(),
                        restockLeadDays = restockDaysText.toDoubleOrNull() ?: 7.0,
                        lastStockUpdateAt = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) now else 0L,
                        lastPackageOpenedAt = if (learnedStartKnown) now else 0L,
                        currentPackageId = packageId,
                        currentPackageLearningEligible = learnedStartKnown
                    )
                    if (packageId != null) {
                        val eventId = UUID.randomUUID().toString()
                        val event = PantryEventType.PACKAGE_OPENED.event(
                            eventId, newItem, now, currentAmount, unit.trim(), "initial_inventory_setup", packageId,
                            mapOf(
                                "startingQuantityKnown" to learnedStartKnown.toString(),
                                "learningEligible" to learnedStartKnown.toString(),
                                "unopenedPackages" to newItem.unopenedPackages.toString()
                            )
                        )
                        onSaveWithEvents(newItem.markEvent(eventId), listOf(event))
                    } else {
                        val eventId = UUID.randomUUID().toString()
                        val startingAmount = when (trackingMode) {
                            PantryTrackingMode.COUNT -> newItem.trackedCount ?: 0.0
                            else -> newItem.quantity
                        }
                        val event = PantryEventType.INVENTORY_CORRECTED.event(
                            eventId,
                            newItem,
                            now,
                            startingAmount,
                            unit.trim(),
                            "initial_inventory_setup",
                            metadata = mapOf(
                                "trackingMode" to trackingMode.name,
                                "unopenedPackages" to newItem.unopenedPackages.toString()
                            )
                        )
                        onSaveWithEvents(newItem.markEvent(eventId), listOf(event))
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save to Pantry", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PantryItemSettingsScreen(
    item: PantryItem,
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onEvent: (PantryItem, List<PantryInventoryEvent>) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var category by remember { mutableStateOf(item.category) }
    var unit by remember { mutableStateOf(item.unit) }
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var priceText by remember { mutableStateOf(if (item.price > 0) item.price.toString() else "") }
    var lowStockThresholdText by remember { mutableStateOf(item.lowStockThreshold.toString()) }
    var targetQuantityText by remember { mutableStateOf(item.targetQuantity.toString()) }
    var packageSizeText by remember {
        mutableStateOf(if (PantryTrackingMode.fromStored(item.trackingMode) == PantryTrackingMode.LEGACY) "" else item.packageSize.toString())
    }
    var quantityStepText by remember { mutableStateOf(item.quantityStep.toString()) }
    var selectedEmoji by remember { mutableStateOf(item.emoji ?: item.resolveEmoji()) }
    var trackingMode by remember {
        mutableStateOf(
            PantryTrackingMode.fromStored(item.trackingMode).let { storedMode ->
                if (storedMode == PantryTrackingMode.LEGACY) item.suggestedTrackingMode() else storedMode
            }
        )
    }
    var trackedCountText by remember { mutableStateOf(item.trackedCount?.let(::formatPantryQuantity).orEmpty()) }
    val estimateWhenOpened = remember(item) { item.estimateAt(System.currentTimeMillis()) }
    var currentPackageText by remember { mutableStateOf(if (item.trackingMode == PantryTrackingMode.LEGACY.name) "" else formatPantryQuantity(estimateWhenOpened.currentPackageRemaining ?: item.currentPackageRemaining)) }
    var additionalOpenedText by remember { mutableStateOf(if (item.trackingMode == PantryTrackingMode.LEGACY.name) "0" else formatPantryQuantity(estimateWhenOpened.additionalOpenedRemaining)) }
    var unopenedPackagesText by remember { mutableStateOf(if (item.trackingMode == PantryTrackingMode.LEGACY.name) "" else estimateWhenOpened.unopenedPackages.toString()) }
    var usesPerDayText by remember { mutableStateOf(if (item.usesPerPeriod > 0) item.usesPerPeriod.toString() else if (item.dailyConsumption > 0) "1" else "") }
    var usagePeriod by remember { mutableStateOf(item.usagePeriod.ifBlank { "DAY" }) }
    var amountPerUseText by remember { mutableStateOf(if (item.amountPerUse > 0) item.amountPerUse.toString() else if (item.dailyConsumption > 0) item.dailyConsumption.toString() else "") }
    var useUnitText by remember { mutableStateOf(item.useUnit.ifBlank { if (item.dailyConsumption > 0) item.unit else "" }) }
    var customConversionText by remember { mutableStateOf(item.customStockUnitsPerUseUnit?.toString().orEmpty()) }
    var restockLeadDaysText by remember { mutableStateOf(item.restockLeadDays.toString()) }
    var daysPackageOpenText by remember {
        mutableStateOf(if (item.lastPackageOpenedAt > 0L) ((System.currentTimeMillis() - item.lastPackageOpenedAt).coerceAtLeast(0L) / 86_400_000.0).toInt().toString() else "0")
    }
    var showFinishConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(com.example.pantry.ui.theme.LocalAppColors.current.background).statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tracking settings",
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
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Item Name", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            Text("Category", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val editCategories = listOf("Dry Goods", "Beverages", "Pet Care", "Household", "Cleaning")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                editCategories.forEach { cat ->
                    val isSelected = category == cat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.08f))
                            .clickable { category = cat },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Cost per package ($)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Stock unit (e.g. lb, fl oz, bottle)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true, colors = inputColors
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (trackingMode == PantryTrackingMode.LEGACY) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Saved legacy amount", color = Color.White.copy(alpha = 0.70f)) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors
                    )
                }
                OutlinedTextField(
                    value = lowStockThresholdText,
                    onValueChange = { lowStockThresholdText = it },
                    label = { Text("Low Stock Alert", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = targetQuantityText,
                    onValueChange = { targetQuantityText = it },
                    label = { Text("Restock target", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors
                )
            }
            OutlinedTextField(
                value = quantityStepText,
                onValueChange = { quantityStepText = it },
                label = { Text("Adjust quantity by ($unit)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = inputColors
            )

            Text("Inventory tracking", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (item.trackingMode == PantryTrackingMode.LEGACY.name) {
                Text(
                    "Your saved amount (${formatPantryQuantity(item.quantity)} ${item.unit}) stays untouched. Choose a method and enter a confirmed starting amount below to enable estimates.",
                    color = Color(0xFFFBBF24), fontSize = 12.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Suggested: ${item.suggestedTrackingMode().displayLabel()}", color = Color.LightGray, fontSize = 11.sp)
                    TextButton(onClick = { trackingMode = item.suggestedTrackingMode() }) { Text("Use suggestion") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    PantryTrackingMode.COUNT to "Simple count",
                    PantryTrackingMode.PACKAGE to "Package level",
                    PantryTrackingMode.CONSUMPTION to "Time based",
                    PantryTrackingMode.ACTIVITY to "Activity based"
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = trackingMode == mode,
                        onClick = { trackingMode = mode },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }

            when (trackingMode) {
                PantryTrackingMode.LEGACY -> Text("Tracking is not configured. The existing quantity remains available as legacy data.", color = Color.LightGray, fontSize = 12.sp)
                PantryTrackingMode.COUNT -> {
                    OutlinedTextField(
                        value = trackedCountText,
                        onValueChange = { trackedCountText = it.filter(Char::isDigit) },
                        label = { Text("Confirmed number on hand (${unit.ifBlank { "items" }})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors
                    )
                    OutlinedTextField(
                        value = packageSizeText,
                        onValueChange = { packageSizeText = it },
                        label = { Text("Number of $unit in one purchase package") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors
                    )
                    Text("Use for rolls, sponges, trash bags, and other individually counted supplies.", color = Color.LightGray, fontSize = 11.sp)
                }
                PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = packageSizeText,
                            onValueChange = { packageSizeText = it },
                            label = { Text("One package ($unit)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true, colors = inputColors
                        )
                        OutlinedTextField(
                            value = currentPackageText,
                            onValueChange = { currentPackageText = it },
                            label = { Text("Open package left") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true, colors = inputColors
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = additionalOpenedText,
                            onValueChange = { additionalOpenedText = it },
                            label = { Text("Other opened amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f), singleLine = true, colors = inputColors
                        )
                        OutlinedTextField(
                            value = unopenedPackagesText,
                            onValueChange = { unopenedPackagesText = it.filter(Char::isDigit) },
                            label = { Text("Unopened packages") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f), singleLine = true, colors = inputColors
                        )
                    }
                    if (trackingMode == PantryTrackingMode.CONSUMPTION || trackingMode == PantryTrackingMode.ACTIVITY) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("DAY" to "Per day", "WEEK" to "Per week").forEach { (period, label) ->
                                FilterChip(selected = usagePeriod == period, onClick = { usagePeriod = period }, label = { Text(label) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = usesPerDayText,
                                onValueChange = { usesPerDayText = it },
                                label = { Text(if (usagePeriod == "WEEK") "Uses per week" else "Uses per day") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f), singleLine = true, colors = inputColors
                            )
                            OutlinedTextField(
                                value = amountPerUseText,
                                onValueChange = { amountPerUseText = it },
                                label = { Text("Amount per use") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f), singleLine = true, colors = inputColors
                            )
                        }
                        OutlinedTextField(
                            value = useUnitText,
                            onValueChange = { useUnitText = it },
                            label = { Text("Unit per use (e.g. oz or scoop)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors
                        )
                        val calculatedDailyUse = calculateDailyConsumption(
                            usesPerDayText.toDoubleOrNull() ?: 0.0,
                            amountPerUseText.toDoubleOrNull() ?: 0.0,
                            useUnitText,
                            unit,
                            customConversionText.toDoubleOrNull(),
                            usagePeriod
                        )
                        if (calculatedDailyUse == null && usesPerDayText.isNotBlank() && amountPerUseText.isNotBlank() && useUnitText.isNotBlank()) {
                            OutlinedTextField(
                                value = customConversionText,
                                onValueChange = { customConversionText = it },
                                label = { Text("Known $unit per $useUnitText (product-specific)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors
                            )
                        }
                        Text(
                            calculatedDailyUse?.let { "Calculated use: ${formatPantryQuantity(it)} $unit/day" }
                                ?: "For weekly use, enter weekly activity frequency. Unknown serving units need a measured product-specific conversion; no scoop weight is assumed.",
                            color = if (calculatedDailyUse == null) Color(0xFFFBBF24) else Color.LightGray,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = daysPackageOpenText,
                            onValueChange = { daysPackageOpenText = it.filter(Char::isDigit) },
                            label = { Text("Days current package has been open") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors
                        )
                        OutlinedTextField(
                            value = restockLeadDaysText,
                            onValueChange = { restockLeadDaysText = it },
                            label = { Text("Restock when this many days remain") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), singleLine = true, colors = inputColors
                        )
                        Text(
                            "Enter use in the same unit as package size. For scoops/cups, use a known product-specific weight conversion; scoop sizes do not have a universal weight.",
                            color = Color.LightGray, fontSize = 11.sp
                        )
                        Text(
                            "${item.completedPackageRecords.size} confirmed package completions · ${item.predictionConfidence()}",
                            color = Color(0xFF7DD3FC), fontSize = 11.sp
                        )
                        val learnedRate = item.effectiveDailyConsumption()
                        if (learnedRate != null) {
                            Text("Current blended estimate: ${formatPantryQuantity(learnedRate)} ${unit}/day", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (trackingMode == PantryTrackingMode.PACKAGE || trackingMode == PantryTrackingMode.CONSUMPTION || trackingMode == PantryTrackingMode.ACTIVITY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val now = System.currentTimeMillis()
                        val updated = item.openNewPackage(now)
                        if (updated == null) {
                            Toast.makeText(context, "Add a purchased unopened package first.", Toast.LENGTH_SHORT).show()
                        } else {
                            onEvent(updated.item, updated.events)
                            Toast.makeText(context, "Opened. Any previous open amount was preserved.", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Opened New") }
                    OutlinedButton(onClick = { showFinishConfirmation = true }) { Text("Finished Package") }
                }
                if (item.completedPackageRecords.isNotEmpty()) {
                    TextButton(onClick = {
                        val reset = item.resetLearnedEstimate(System.currentTimeMillis())
                        onEvent(reset.item, reset.events)
                    }) {
                        Text("Reset learned history")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Delete Item", color = Color(0xFFFF3B30), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val parsedPrice = priceText.toDoubleOrNull() ?: item.price
                        val parsedQty = quantityText.toDoubleOrNull() ?: item.quantity
                        val threshold = lowStockThresholdText.toDoubleOrNull() ?: item.lowStockThreshold
                        val target = targetQuantityText.toDoubleOrNull() ?: item.targetQuantity
                        val packageSize = packageSizeText.toDoubleOrNull()
                        val quantityStep = quantityStepText.toDoubleOrNull() ?: item.quantityStep
                        val trackedCount = trackedCountText.toDoubleOrNull()
                        val currentPackage = currentPackageText.toDoubleOrNull()
                        val otherOpened = additionalOpenedText.toDoubleOrNull() ?: 0.0
                        val unopened = unopenedPackagesText.toIntOrNull()
                        val usesPerDay = usesPerDayText.toDoubleOrNull()
                        val amountPerUse = amountPerUseText.toDoubleOrNull()
                        val useUnit = useUnitText.trim()
                        val customConversion = customConversionText.toDoubleOrNull()
                        val dailyUse = calculateDailyConsumption(
                            usesPerDay ?: 0.0,
                            amountPerUse ?: 0.0,
                            useUnit,
                            unit,
                            customConversion,
                            usagePeriod
                        )
                        val usesPerDayNormalized = if (usagePeriod == "WEEK") (usesPerDay ?: 0.0) / 7.0 else usesPerDay ?: 0.0
                        val restockDays = restockLeadDaysText.toDoubleOrNull() ?: item.restockLeadDays
                        val daysOpen = daysPackageOpenText.toIntOrNull() ?: 0
                        val stockSetupInvalid = when (trackingMode) {
                            PantryTrackingMode.LEGACY -> false
                            PantryTrackingMode.COUNT -> trackedCount == null || !trackedCount.isFinite() || trackedCount < 0 || trackedCount % 1.0 != 0.0 || packageSize == null || !packageSize.isFinite() || packageSize < 1.0 || packageSize % 1.0 != 0.0
                            PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY ->
                                packageSize == null || !packageSize.isFinite() || packageSize <= 0 || currentPackage == null || !currentPackage.isFinite() || currentPackage < 0 ||
                                    currentPackage > packageSize || !otherOpened.isFinite() || otherOpened < 0 || unopened == null || unopened < 0 ||
                                    (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY) && (dailyUse == null || dailyUse <= 0 || usesPerDay == null || amountPerUse == null || useUnit.isBlank() || !restockDays.isFinite() || restockDays < 0))
                        }
                        if (name.isBlank() || unit.isBlank() || !parsedQty.isFinite() || parsedQty < 0 || !parsedPrice.isFinite() || parsedPrice < 0 || !threshold.isFinite() || threshold < 0 || !target.isFinite() || target < threshold || !quantityStep.isFinite() || quantityStep <= 0 || stockSetupInvalid) {
                            Toast.makeText(context, "Check the stock setup and enter a confirmed starting amount.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val now = System.currentTimeMillis()
                        val updatedItem = item.copy(
                            name = name.trim(),
                            category = category,
                            quantity = parsedQty,
                            unit = unit.trim(),
                            lowStockThreshold = threshold,
                            price = parsedPrice,
                            targetQuantity = target,
                            packageSize = packageSize ?: item.packageSize,
                            quantityStep = quantityStep,
                            emoji = selectedEmoji,
                            trackingMode = trackingMode.name,
                            trackedCount = if (trackingMode == PantryTrackingMode.COUNT) trackedCount else item.trackedCount,
                            currentPackageRemaining = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) currentPackage!! else item.currentPackageRemaining,
                            additionalOpenedRemaining = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) otherOpened else item.additionalOpenedRemaining,
                            unopenedPackages = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) unopened!! else item.unopenedPackages,
                            dailyConsumption = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) dailyUse!! else item.dailyConsumption,
                            usesPerDay = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) usesPerDayNormalized else item.usesPerDay,
                            usesPerPeriod = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) usesPerDay!! else item.usesPerPeriod,
                            usagePeriod = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) usagePeriod else item.usagePeriod,
                            amountPerUse = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) amountPerUse!! else item.amountPerUse,
                            useUnit = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) useUnit else item.useUnit,
                            customStockUnitsPerUseUnit = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) customConversion else item.customStockUnitsPerUseUnit,
                            restockLeadDays = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) restockDays else item.restockLeadDays,
                            lastStockUpdateAt = if (trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) now else item.lastStockUpdateAt,
                            lastPackageOpenedAt = if (trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY) && currentPackage!! > 0) now - daysOpen * 86_400_000L else item.lastPackageOpenedAt,
                            currentPackageLearningEligible = if (item.trackingMode == PantryTrackingMode.LEGACY.name && trackingMode in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) false else item.currentPackageLearningEligible,
                            currentPackageId = if (
                                item.trackingMode == PantryTrackingMode.LEGACY.name &&
                                trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY) &&
                                (currentPackage ?: 0.0) > 0.0
                            ) UUID.randomUUID().toString() else item.currentPackageId
                        )
                        val previousMode = PantryTrackingMode.fromStored(item.trackingMode)
                        val packageMode = trackingMode in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)
                        val inventoryChanged = when (trackingMode) {
                            PantryTrackingMode.LEGACY -> false
                            PantryTrackingMode.COUNT -> previousMode != trackingMode || item.trackedCount != updatedItem.trackedCount
                            PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
                                val previousEstimate = item.estimateAt(now)
                                previousMode != trackingMode ||
                                    kotlin.math.abs((previousEstimate.currentPackageRemaining ?: 0.0) - updatedItem.currentPackageRemaining) > 0.0001 ||
                                    kotlin.math.abs(previousEstimate.additionalOpenedRemaining - updatedItem.additionalOpenedRemaining) > 0.0001 ||
                                    previousEstimate.unopenedPackages != updatedItem.unopenedPackages
                            }
                        }
                        val configurationChanged = previousMode != trackingMode ||
                            (trackingMode != PantryTrackingMode.LEGACY && (
                                item.unit != updatedItem.unit || item.packageSize != updatedItem.packageSize ||
                                    item.dailyConsumption != updatedItem.dailyConsumption || item.usesPerPeriod != updatedItem.usesPerPeriod ||
                                    item.usagePeriod != updatedItem.usagePeriod || item.amountPerUse != updatedItem.amountPerUse ||
                                    item.useUnit != updatedItem.useUnit || item.customStockUnitsPerUseUnit != updatedItem.customStockUnitsPerUseUnit ||
                                    item.restockLeadDays != updatedItem.restockLeadDays
                                ))

                        val events = mutableListOf<PantryInventoryEvent>()
                        var eventAppliedItem = updatedItem.copy(processedEventIds = item.processedEventIds)
                        if (inventoryChanged) {
                            val correction = updatedItem.correctInventory(
                                currentAmount = if (trackingMode == PantryTrackingMode.COUNT) updatedItem.trackedCount ?: 0.0 else updatedItem.currentPackageRemaining,
                                otherOpenedAmount = updatedItem.additionalOpenedRemaining,
                                backups = updatedItem.unopenedPackages,
                                nowMillis = now,
                                effectivePackageSize = updatedItem.packageSize,
                                eventId = UUID.randomUUID().toString()
                            )
                            events += correction.events
                            eventAppliedItem = correction.item
                        }
                        if (configurationChanged) {
                            val config = item.usageConfigurationEvent(
                                eventAppliedItem,
                                now,
                                UUID.randomUUID().toString()
                            )
                            events += config.events
                            eventAppliedItem = config.item
                        }
                        if (previousMode == PantryTrackingMode.LEGACY && packageMode && updatedItem.currentPackageRemaining > 0.0) {
                            val eventId = UUID.randomUUID().toString()
                            val occurredAt = updatedItem.lastPackageOpenedAt.takeIf { it > 0L } ?: now
                            val openedEvent = PantryEventType.PACKAGE_OPENED.event(
                                eventId,
                                item,
                                occurredAt,
                                updatedItem.currentPackageRemaining,
                                updatedItem.unit,
                                "tracking_setup_state_confirmed",
                                updatedItem.currentPackageId,
                                mapOf("fullStartingAmountKnown" to "false", "learningEligible" to "false")
                            )
                            events += openedEvent
                            eventAppliedItem = eventAppliedItem.markEvent(eventId)
                        }
                        if (events.isNotEmpty()) onEvent(eventAppliedItem, events) else onSave(updatedItem)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    Text("Save Changes", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showFinishConfirmation) {
            AlertDialog(
                onDismissRequest = { showFinishConfirmation = false },
                title = { Text("Finish this package?", color = Color.White) },
                text = { Text("This records a confirmed package completion. Predicted empty dates are never saved as completed use.", color = Color.LightGray) },
                confirmButton = {
                    TextButton(onClick = {
                        val completed = item.finishCurrentPackage(System.currentTimeMillis())
                        if (!item.currentPackageLearningEligible || item.lastPackageOpenedAt <= 0L) {
                            Toast.makeText(context, "Package finish was recorded but won't train the estimate because its full starting amount wasn't confirmed.", Toast.LENGTH_LONG).show()
                        }
                        showFinishConfirmation = false
                        onEvent(completed.item, completed.events)
                    }) { Text("Confirm finished") }
                },
                dismissButton = { TextButton(onClick = { showFinishConfirmation = false }) { Text("Cancel") } },
                containerColor = Color(0xFF101B33), titleContentColor = Color.White, textContentColor = Color.LightGray
            )
        }
    }
}
