package com.example.pantry.ui

import kotlin.math.max
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class PantryTrackingMode {
    LEGACY,
    COUNT,
    PACKAGE,
    CONSUMPTION,
    ACTIVITY;

    companion object {
        fun fromStored(value: String?): PantryTrackingMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LEGACY
    }
}

fun PantryItem.suggestedTrackingMode(): PantryTrackingMode {
    val label = name.lowercase()
    return when {
        listOf("dog food", "cat food", "puppy food", "pet food", "puppy pad", "pee pad", "toilet paper").any(label::contains) -> PantryTrackingMode.CONSUMPTION
        listOf("laundry detergent", "dishwasher tablet", "dishwasher pod", "detergent pod", "trash bag").any(label::contains) -> PantryTrackingMode.ACTIVITY
        listOf("bleach", "cat litter", "litter", "dish soap", "shampoo", "cleaner", "spray", "detergent").any(label::contains) -> PantryTrackingMode.PACKAGE
        else -> PantryTrackingMode.COUNT
    }
}

data class PackageCompletion(
    val durationDays: Double,
    val packageSize: Double,
    val completedAt: Long,
    val openingQuantity: Double = 0.0,
    val packageInstanceId: String = "",
    val verifiedFullStart: Boolean = false,
    val unit: String = ""
)

enum class PantryEventType {
    PACKAGE_OPENED,
    PACKAGE_FINISHED,
    PURCHASE_RECORDED,
    CONSUMPTION_RECORDED,
    INVENTORY_CORRECTED,
    USAGE_CONFIGURATION_CHANGED
}

data class PantryInventoryEvent(
    val id: String,
    val productId: String,
    val type: PantryEventType,
    val occurredAt: Long,
    val quantity: Double,
    val unit: String,
    val source: String,
    val packageInstanceId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class PantryTransition(
    val item: PantryItem,
    val events: List<PantryInventoryEvent>
)

/** Maps repeated identical actions in the same second to one persisted event id. */
fun PantryTransition.withStableEventIds(existingItem: PantryItem? = null): PantryTransition? {
    if (events.isEmpty()) return this
    val stableEvents = events.map { event ->
        val signature = listOf(
            event.productId, event.type.name, (event.occurredAt / 1_000L).toString(),
            event.quantity.toString(), event.unit, event.source
        ).joinToString("|")
        event.copy(id = UUID.nameUUIDFromBytes(signature.toByteArray(StandardCharsets.UTF_8)).toString())
    }
    val stableIds = stableEvents.map { it.id }.toSet()
    if (existingItem != null && stableIds.any { it in existingItem.processedEventIds }) return null
    val idsByOriginal = events.zip(stableEvents).associate { (old, new) -> old.id to new.id }
    val normalizedItem = item.copy(
        processedEventIds = (item.processedEventIds.map { idsByOriginal[it] ?: it } + stableIds).distinct().takeLast(500)
    )
    return copy(item = normalizedItem, events = stableEvents)
}

data class PantryEstimate(
    val currentPackageRemaining: Double? = null,
    val additionalOpenedRemaining: Double = 0.0,
    val unopenedPackages: Int = 0,
    val totalRemaining: Double? = null,
    val daysRemaining: Double? = null,
    val depletionAtMillis: Long? = null,
    val currentPackageDaysRemaining: Double? = null,
    val effectiveDailyConsumption: Double? = null,
    val confidence: String = "Not configured"
)

enum class PantryStockStatus {
    STOCKED,
    RESTOCK_SOON,
    LOW,
    OUT_OF_STOCK,
    ESTIMATED_EMPTY,
    SETUP_NEEDED,
    ESTIMATE_UNAVAILABLE
}

fun unitConversionFactor(fromUnit: String, toUnit: String): Double? {
    val from = fromUnit.trim().lowercase().replace(" ", "")
    val to = toUnit.trim().lowercase().replace(" ", "")
    if (from.isBlank() || to.isBlank()) return null
    if (from == to || from.trimEnd('s') == to.trimEnd('s')) return 1.0

    val weightGrams = mapOf(
        "mg" to 0.001, "g" to 1.0, "gram" to 1.0, "grams" to 1.0,
        "kg" to 1000.0, "oz" to 28.349523125, "ounce" to 28.349523125, "ounces" to 28.349523125,
        "lb" to 453.59237, "lbs" to 453.59237, "pound" to 453.59237, "pounds" to 453.59237
    )
    val volumeMl = mapOf(
        "ml" to 1.0, "milliliter" to 1.0, "milliliters" to 1.0,
        "l" to 1000.0, "liter" to 1000.0, "liters" to 1000.0,
        "tsp" to 4.92892159375, "teaspoon" to 4.92892159375, "teaspoons" to 4.92892159375,
        "tbsp" to 14.78676478125, "tablespoon" to 14.78676478125, "tablespoons" to 14.78676478125,
        "cup" to 236.5882365, "cups" to 236.5882365,
        "pt" to 473.176473, "pint" to 473.176473, "pints" to 473.176473,
        "qt" to 946.352946, "quart" to 946.352946, "quarts" to 946.352946,
        "gal" to 3785.411784, "gallon" to 3785.411784, "gallons" to 3785.411784,
        "floz" to 29.5735295625, "fluidounce" to 29.5735295625, "fluidounces" to 29.5735295625
    )
    val fromWeight = weightGrams[from]
    val toWeight = weightGrams[to]
    if (fromWeight != null && toWeight != null) return fromWeight / toWeight
    val fromVolume = volumeMl[from]
    val toVolume = volumeMl[to]
    if (fromVolume != null && toVolume != null) return fromVolume / toVolume
    return null
}

fun calculateDailyConsumption(
    usesPerDay: Double,
    amountPerUse: Double,
    useUnit: String,
    stockUnit: String,
    customStockUnitsPerUseUnit: Double? = null,
    period: String = "DAY"
): Double? {
    if (!usesPerDay.isFinite() || !amountPerUse.isFinite() || usesPerDay <= 0.0 || amountPerUse <= 0.0) return null
    val factor = unitConversionFactor(useUnit, stockUnit)
        ?: customStockUnitsPerUseUnit?.takeIf { it.isFinite() && it > 0.0 }
        ?: return null
    val normalizedUsesPerDay = if (period.equals("WEEK", ignoreCase = true)) usesPerDay / 7.0 else usesPerDay
    return (normalizedUsesPerDay * amountPerUse * factor).takeIf { it.isFinite() && it > 0.0 }
}

private const val MILLIS_PER_DAY = 86_400_000.0

fun PantryItem.effectiveDailyConsumption(): Double? {
    if (PantryTrackingMode.fromStored(trackingMode) !in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY) || !dailyConsumption.isFinite() || dailyConsumption <= 0.0) return null
    val rates = completedPackageRecords
        .takeLast(8)
        .mapNotNull { record ->
            if (!record.verifiedFullStart || !record.durationDays.isFinite() || record.durationDays <= 0.0 ||
                record.openingQuantity <= 0.0 || !record.openingQuantity.isFinite()) return@mapNotNull null
            val factor = unitConversionFactor(record.unit, unit) ?: return@mapNotNull null
            (record.openingQuantity * factor / record.durationDays) to record.completedAt
        }
        .sortedBy { it.second }
    if (rates.isEmpty()) return dailyConsumption

    // Bound observations around the configured baseline and weight recent completions more.
    val recent = rates.takeLast(5)
    val weightedRate = recent.mapIndexed { index, (rate, _) ->
        val reliability = rate.coerceIn(dailyConsumption * 0.5, dailyConsumption * 1.5)
        reliability to (index + 1).toDouble()
    }
    val recentMean = weightedRate.sumOf { it.first * it.second } / weightedRate.sumOf { it.second }
    val boundedRecent = recentMean.coerceIn(dailyConsumption * 0.5, dailyConsumption * 1.5)
    val weight = (rates.size.toDouble() / (rates.size + 2.0)).coerceAtMost(0.8)
    return dailyConsumption * (1.0 - weight) + boundedRecent * weight
}

fun PantryItem.predictionConfidence(): String {
    val samples = completedPackageRecords.count { it.verifiedFullStart && it.openingQuantity > 0.0 && it.durationDays > 0.0 }
    return when {
        samples == 0 -> "Starting estimate"
        samples == 1 -> "Early estimate · 1 confirmed package"
        samples < 5 -> "Improving · $samples confirmed packages"
        else -> "Established · $samples confirmed packages"
    }
}

fun PantryItem.forecastUnitPrice(): Pair<Double?, String?> {
    val historical = purchasePriceHistory.filter { it.isFinite() && it > 0.0 }
    return when {
        historical.isNotEmpty() -> historical.average() to "Historical average"
        price.isFinite() && price > 0.0 -> price to "Saved package price"
        else -> null to null
    }
}

fun PantryItem.estimateAt(nowMillis: Long): PantryEstimate {
    val mode = PantryTrackingMode.fromStored(trackingMode)
    return when (mode) {
        PantryTrackingMode.LEGACY -> PantryEstimate()
        PantryTrackingMode.COUNT -> {
            val count = trackedCount?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
            PantryEstimate(totalRemaining = count, confidence = "Confirmed count")
        }
        PantryTrackingMode.PACKAGE -> {
            if (!packageSize.isFinite() || packageSize <= 0.0) return PantryEstimate()
            val current = (currentPackageRemaining.takeIf { it.isFinite() } ?: 0.0).coerceIn(0.0, packageSize)
            val overflow = (additionalOpenedRemaining.takeIf { it.isFinite() } ?: 0.0).coerceAtLeast(0.0)
            val unopened = unopenedPackages.coerceAtLeast(0)
            PantryEstimate(
                currentPackageRemaining = current,
                additionalOpenedRemaining = overflow,
                unopenedPackages = unopened,
                totalRemaining = current + overflow + unopened * packageSize,
                confidence = "Approximate package level"
            )
        }
        PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
            val rate = effectiveDailyConsumption() ?: return PantryEstimate()
            if (!packageSize.isFinite() || packageSize <= 0.0 || !rate.isFinite() || rate <= 0.0) return PantryEstimate()
            val elapsedDays = if (lastStockUpdateAt > 0L)
                max(0.0, (nowMillis - lastStockUpdateAt).toDouble() / MILLIS_PER_DAY)
            else 0.0
            val consumed = elapsedDays * rate
            val currentAtUpdate = currentPackageRemaining.takeIf { it.isFinite() }?.coerceIn(0.0, packageSize) ?: 0.0
            val overflowAtUpdate = additionalOpenedRemaining.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
            val unopenedAtUpdate = unopenedPackages.coerceAtLeast(0)
            val openAtUpdate = currentAtUpdate + overflowAtUpdate
            val totalAtUpdate = openAtUpdate + unopenedAtUpdate * packageSize
            val total = max(0.0, totalAtUpdate - consumed)
            val consumedPastOpen = max(0.0, consumed - openAtUpdate)
            val unopenedQuantity = max(0.0, unopenedAtUpdate * packageSize - consumedPastOpen)
            // This is a virtual estimate only. Crossing a predicted package boundary does not
            // mutate persisted state or create a PACKAGE_OPENED/PACKAGE_FINISHED event.
            val inferredUnopened = kotlin.math.floor((unopenedQuantity + 1e-9) / packageSize).toInt()
            val inferredCurrentFromBackup = max(0.0, unopenedQuantity - inferredUnopened * packageSize)
            val current = if (consumedPastOpen > 0.0) inferredCurrentFromBackup else max(0.0, currentAtUpdate - consumed)
            val overflow = if (consumedPastOpen > 0.0) 0.0 else max(0.0, overflowAtUpdate - max(0.0, consumed - currentAtUpdate))
            val unopened = if (consumedPastOpen > 0.0) inferredUnopened else unopenedAtUpdate
            val daysRemaining = total / rate
            PantryEstimate(
                currentPackageRemaining = current,
                additionalOpenedRemaining = overflow,
                unopenedPackages = unopened,
                totalRemaining = total,
                daysRemaining = daysRemaining,
                depletionAtMillis = nowMillis + (daysRemaining * MILLIS_PER_DAY).toLong(),
                currentPackageDaysRemaining = (current + overflow) / rate,
                effectiveDailyConsumption = rate,
                confidence = predictionConfidence()
            )
        }
    }
}

fun PantryItem.isRunningLow(nowMillis: Long): Boolean {
    val threshold = lowStockThreshold.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    return when (PantryTrackingMode.fromStored(trackingMode)) {
    PantryTrackingMode.LEGACY -> quantity.takeIf { it.isFinite() }?.coerceAtLeast(0.0)?.let { it <= threshold } ?: true
    PantryTrackingMode.COUNT -> (estimateAt(nowMillis).totalRemaining ?: 0.0) <= threshold
    PantryTrackingMode.PACKAGE -> (estimateAt(nowMillis).totalRemaining ?: 0.0) <= threshold
    PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> (estimateAt(nowMillis).daysRemaining ?: Double.POSITIVE_INFINITY) <= (restockLeadDays.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0) + 1e-9
    }
}

fun PantryItem.stockStatusAt(nowMillis: Long): PantryStockStatus {
    val mode = PantryTrackingMode.fromStored(trackingMode)
    val estimate = estimateAt(nowMillis)
    val threshold = lowStockThreshold.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val leadDays = restockLeadDays.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    return when (mode) {
        PantryTrackingMode.LEGACY -> if (quantity <= 0.0) PantryStockStatus.OUT_OF_STOCK else PantryStockStatus.SETUP_NEEDED
        PantryTrackingMode.COUNT -> when {
            (estimate.totalRemaining ?: 0.0) <= 0.0 -> PantryStockStatus.OUT_OF_STOCK
            (estimate.totalRemaining ?: 0.0) <= threshold -> PantryStockStatus.LOW
            (estimate.totalRemaining ?: 0.0) <= threshold * 1.5 -> PantryStockStatus.RESTOCK_SOON
            else -> PantryStockStatus.STOCKED
        }
        PantryTrackingMode.PACKAGE -> when {
            (estimate.totalRemaining ?: 0.0) <= 0.0 -> PantryStockStatus.OUT_OF_STOCK
            (estimate.totalRemaining ?: 0.0) <= threshold -> PantryStockStatus.LOW
            (estimate.totalRemaining ?: 0.0) <= threshold * 1.5 -> PantryStockStatus.RESTOCK_SOON
            else -> PantryStockStatus.STOCKED
        }
        PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
            val days = estimate.daysRemaining ?: return PantryStockStatus.ESTIMATE_UNAVAILABLE
            when {
                estimate.totalRemaining == null -> PantryStockStatus.ESTIMATE_UNAVAILABLE
                estimate.totalRemaining <= 0.0 -> PantryStockStatus.ESTIMATED_EMPTY
                days <= leadDays * 0.5 -> PantryStockStatus.LOW
                days <= leadDays -> PantryStockStatus.RESTOCK_SOON
                else -> PantryStockStatus.STOCKED
            }
        }
    }
}

fun PantryItem.packagesToRestockAt(nowMillis: Long): Int {
    val mode = PantryTrackingMode.fromStored(trackingMode)
    val safeTarget = targetQuantity.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    if (mode == PantryTrackingMode.COUNT) {
        val availableCount = trackedCount?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val missing = (safeTarget - availableCount).coerceAtLeast(0.0)
        val unitsPerPackage = packageSize.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return kotlin.math.ceil(missing / unitsPerPackage).toInt()
    }
    val available = when (mode) {
        PantryTrackingMode.LEGACY -> quantity.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> estimateAt(nowMillis).totalRemaining ?: quantity
        PantryTrackingMode.COUNT -> 0.0
    }
    val safeAvailable = available.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val missing = (safeTarget - safeAvailable).coerceAtLeast(0.0)
    val safePackageSize = packageSize.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    return kotlin.math.ceil(missing / safePackageSize).toInt()
}

fun PantryItem.markEvent(eventId: String): PantryItem =
    copy(processedEventIds = (processedEventIds + eventId).distinct().takeLast(500))

fun PantryEventType.event(id: String, item: PantryItem, at: Long, quantity: Double, eventUnit: String, source: String, packageId: String? = null, metadata: Map<String, String> = emptyMap()) =
    PantryInventoryEvent(id, item.id, this, at, quantity.coerceAtLeast(0.0), eventUnit, source, packageId, metadata)

fun PantryItem.recordApproximateUse(fractionOfPackage: Double, nowMillis: Long, eventId: String = UUID.randomUUID().toString()): PantryTransition {
    if (eventId in processedEventIds) return PantryTransition(this, emptyList())
    val mode = PantryTrackingMode.fromStored(trackingMode)
    var amountUsed = 0.0
    val updated = when (mode) {
        PantryTrackingMode.COUNT -> {
            val current = (trackedCount ?: 0.0).takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
            amountUsed = minOf(1.0, current)
            copy(trackedCount = (current - 1.0).coerceAtLeast(0.0))
        }
        PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
            val estimate = estimateAt(nowMillis)
            val current = estimate.currentPackageRemaining ?: return PantryTransition(this, emptyList())
            val fraction = (if (fractionOfPackage.isFinite()) fractionOfPackage else 0.0).coerceIn(0.0, 1.0)
            val requested = (packageSize * fraction).coerceAtLeast(0.0)
            val amount = minOf(requested, current + estimate.additionalOpenedRemaining)
            amountUsed = amount
            val usedFromCurrent = minOf(current, amount)
            copy(
                currentPackageRemaining = current - usedFromCurrent,
                additionalOpenedRemaining = (estimate.additionalOpenedRemaining - (amount - usedFromCurrent)).coerceAtLeast(0.0),
                unopenedPackages = estimate.unopenedPackages,
                lastStockUpdateAt = nowMillis
            )
        }
        PantryTrackingMode.LEGACY -> return PantryTransition(this, emptyList())
    }
    if (amountUsed <= 0.0) return PantryTransition(this, emptyList())
    val marked = updated.markEvent(eventId)
    val event = PantryEventType.CONSUMPTION_RECORDED.event(
        eventId, this, nowMillis, amountUsed, unit, "manual_approximation",
        currentPackageId, mapOf("fractionOfPackage" to (if (fractionOfPackage.isFinite()) fractionOfPackage else 0.0).coerceIn(0.0, 1.0).toString())
    )
    return PantryTransition(marked, listOf(event))
}

fun PantryItem.addPurchasedPackages(
    count: Int,
    nowMillis: Long,
    eventId: String = UUID.randomUUID().toString(),
    purchaseTotal: Double? = null,
    purchaseDateMillis: Long = nowMillis
): PantryTransition {
    if (eventId in processedEventIds || count <= 0) return PantryTransition(this, emptyList())
    val mode = PantryTrackingMode.fromStored(trackingMode)
    val safeCount = count.coerceAtLeast(0)
    val amount = when (mode) {
        PantryTrackingMode.COUNT -> safeCount * packageSize.coerceAtLeast(1.0)
        PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> safeCount.toDouble()
        PantryTrackingMode.LEGACY -> return PantryTransition(this, emptyList())
    }
    val updated = when (mode) {
        PantryTrackingMode.COUNT -> copy(trackedCount = (trackedCount?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0) + amount)
        PantryTrackingMode.PACKAGE -> copy(unopenedPackages = (unopenedPackages + safeCount).coerceAtLeast(0))
        PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> {
            val estimate = estimateAt(nowMillis)
            copy(
                currentPackageRemaining = estimate.currentPackageRemaining ?: currentPackageRemaining,
                additionalOpenedRemaining = estimate.additionalOpenedRemaining,
                unopenedPackages = estimate.unopenedPackages + safeCount,
                lastStockUpdateAt = nowMillis
            )
        }
        PantryTrackingMode.LEGACY -> return PantryTransition(this, emptyList())
    }.let { inventoryUpdated ->
        val actualUnitPrice = purchaseTotal
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?.div(safeCount)
        if (actualUnitPrice != null) inventoryUpdated.copy(
            price = actualUnitPrice,
            purchasePriceHistory = (inventoryUpdated.purchasePriceHistory + actualUnitPrice).takeLast(12)
        ) else inventoryUpdated
    }.markEvent(eventId)
    val metadata = buildMap {
        put("packageCount", safeCount.toString())
        put("unitPrice", updated.price.toString())
        put("purchaseDate", purchaseDateMillis.toString())
        if (purchaseTotal != null && purchaseTotal.isFinite() && purchaseTotal >= 0.0) put("purchaseTotal", purchaseTotal.toString())
    }
    val event = PantryEventType.PURCHASE_RECORDED.event(
        eventId, this, purchaseDateMillis, amount,
        if (mode == PantryTrackingMode.COUNT) unit else "packages", "user_purchase",
        metadata = metadata
    )
    return PantryTransition(updated, listOf(event))
}

fun PantryItem.openNewPackage(nowMillis: Long, eventId: String = UUID.randomUUID().toString()): PantryTransition? {
    if (eventId in processedEventIds) return PantryTransition(this, emptyList())
    if (PantryTrackingMode.fromStored(trackingMode) !in setOf(PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY)) return null
    val estimate = estimateAt(nowMillis)
    val estimatedBackupWasConsumed = PantryTrackingMode.fromStored(trackingMode) in setOf(PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY) &&
        estimate.unopenedPackages < unopenedPackages &&
        (estimate.currentPackageRemaining ?: 0.0) > 0.0
    if (estimatedBackupWasConsumed) {
        val packageId = UUID.randomUUID().toString()
        val confirmedRemaining = estimate.currentPackageRemaining ?: 0.0
        val updated = copy(
            currentPackageRemaining = confirmedRemaining,
            additionalOpenedRemaining = estimate.additionalOpenedRemaining,
            unopenedPackages = estimate.unopenedPackages,
            lastStockUpdateAt = nowMillis,
            lastPackageOpenedAt = nowMillis,
            currentPackageId = packageId,
            currentPackageLearningEligible = false
        ).markEvent(eventId)
        val event = PantryEventType.PACKAGE_OPENED.event(
            eventId, this, nowMillis, confirmedRemaining, unit, "confirmed_estimated_backup_open", packageId,
            mapOf("startingQuantityKnown" to "false", "learningEligible" to "false")
        )
        return PantryTransition(updated, listOf(event))
    }
    if (estimate.unopenedPackages <= 0 || packageSize <= 0.0) return null
    val currentLeft = estimate.currentPackageRemaining ?: 0.0
    val preservedOpen = estimate.additionalOpenedRemaining + currentLeft
    val packageId = UUID.randomUUID().toString()
    val updated = copy(
        currentPackageRemaining = packageSize,
        additionalOpenedRemaining = preservedOpen,
        unopenedPackages = (estimate.unopenedPackages - 1).coerceAtLeast(0),
        lastStockUpdateAt = nowMillis,
        lastPackageOpenedAt = nowMillis,
        currentPackageId = packageId,
        currentPackageLearningEligible = true
    ).markEvent(eventId)
    val event = PantryEventType.PACKAGE_OPENED.event(eventId, this, nowMillis, packageSize, unit, "user_action", packageId)
    return PantryTransition(updated, listOf(event))
}

fun PantryItem.finishCurrentPackage(nowMillis: Long, eventId: String = UUID.randomUUID().toString()): PantryTransition {
    if (eventId in processedEventIds) return PantryTransition(this, emptyList())
    val estimate = estimateAt(nowMillis)
    val openedAt = lastPackageOpenedAt
    val durationDays = if (openedAt > 0L && nowMillis > openedAt) (nowMillis - openedAt) / MILLIS_PER_DAY else 0.0
    // If elapsed-time estimation has virtually consumed an unopened backup, the actual
    // package currently being finished is uncertain until the user confirms an open.
    val crossedUnconfirmedPackageBoundary = estimate.unopenedPackages < unopenedPackages
    val verified = currentPackageLearningEligible && currentPackageId != null && durationDays > 0.0 &&
        durationDays.isFinite() && packageSize.isFinite() && packageSize > 0.0 && !crossedUnconfirmedPackageBoundary
    val records = if (verified) {
        (completedPackageRecords + PackageCompletion(durationDays, packageSize, nowMillis, packageSize, currentPackageId.orEmpty(), true, unit)).takeLast(40)
    } else completedPackageRecords
    val updated = copy(
        currentPackageRemaining = 0.0,
        additionalOpenedRemaining = estimate.additionalOpenedRemaining,
        unopenedPackages = estimate.unopenedPackages,
        lastStockUpdateAt = nowMillis,
        lastPackageOpenedAt = 0L,
        currentPackageId = null,
        currentPackageLearningEligible = false,
        completedPackageRecords = records
    ).markEvent(eventId)
    val event = PantryEventType.PACKAGE_FINISHED.event(
        eventId, this, nowMillis, packageSize.coerceAtLeast(0.0), unit,
        if (verified) "confirmed_full_cycle" else "confirmed_unverified_cycle", currentPackageId,
        mapOf("durationDays" to durationDays.toString(), "usedForLearning" to verified.toString())
    )
    return PantryTransition(updated, listOf(event))
}

fun PantryItem.correctInventory(
    currentAmount: Double,
    otherOpenedAmount: Double,
    backups: Int,
    nowMillis: Long,
    effectivePackageSize: Double = packageSize,
    eventId: String = UUID.randomUUID().toString()
): PantryTransition {
    if (eventId in processedEventIds) return PantryTransition(this, emptyList())
    val mode = PantryTrackingMode.fromStored(trackingMode)
    val safeCurrent = (if (currentAmount.isFinite()) currentAmount else 0.0).coerceAtLeast(0.0)
    val safeOther = (if (otherOpenedAmount.isFinite()) otherOpenedAmount else 0.0).coerceAtLeast(0.0)
    val safeBackups = backups.coerceAtLeast(0)
    val updated = when (mode) {
        PantryTrackingMode.COUNT -> copy(trackedCount = safeCurrent)
        PantryTrackingMode.PACKAGE, PantryTrackingMode.CONSUMPTION, PantryTrackingMode.ACTIVITY -> copy(
            currentPackageRemaining = safeCurrent.coerceAtMost(packageSize.coerceAtLeast(0.0)),
            additionalOpenedRemaining = safeOther,
            unopenedPackages = safeBackups,
            lastStockUpdateAt = nowMillis
        )
        PantryTrackingMode.LEGACY -> return PantryTransition(this, emptyList())
    }.markEvent(eventId)
    val eventAmount = if (mode == PantryTrackingMode.COUNT) safeCurrent else safeCurrent + safeOther + safeBackups * effectivePackageSize.coerceAtLeast(0.0)
    val event = PantryEventType.INVENTORY_CORRECTED.event(eventId, this, nowMillis, eventAmount, unit, "user_correction")
    return PantryTransition(updated, listOf(event))
}

fun PantryItem.usageConfigurationEvent(updatedItem: PantryItem, nowMillis: Long, eventId: String = UUID.randomUUID().toString()): PantryTransition {
    if (eventId in processedEventIds) return PantryTransition(this, emptyList())
    val usageChanged = dailyConsumption != updatedItem.dailyConsumption || usesPerPeriod != updatedItem.usesPerPeriod ||
        usagePeriod != updatedItem.usagePeriod || amountPerUse != updatedItem.amountPerUse || useUnit != updatedItem.useUnit ||
        customStockUnitsPerUseUnit != updatedItem.customStockUnitsPerUseUnit || packageSize != updatedItem.packageSize ||
        unit != updatedItem.unit || trackingMode != updatedItem.trackingMode
    val updated = updatedItem.copy(
        processedEventIds = processedEventIds,
        currentPackageLearningEligible = if (usageChanged) false else updatedItem.currentPackageLearningEligible
    ).markEvent(eventId)
    val event = PantryEventType.USAGE_CONFIGURATION_CHANGED.event(
        eventId, this, nowMillis, updatedItem.dailyConsumption, "${updatedItem.unit}/day", "user_configuration",
        metadata = mapOf(
            "effectiveAt" to nowMillis.toString(),
            "previousDailyRate" to dailyConsumption.toString(),
            "newDailyRate" to updatedItem.dailyConsumption.toString(),
            "trackingMode" to updatedItem.trackingMode
        )
    )
    return PantryTransition(updated, listOf(event))
}

fun PantryItem.resetLearnedEstimate(nowMillis: Long, eventId: String = UUID.randomUUID().toString()): PantryTransition {
    if (eventId in processedEventIds) return PantryTransition(this, emptyList())
    val updated = copy(completedPackageRecords = emptyList()).markEvent(eventId)
    val event = PantryEventType.USAGE_CONFIGURATION_CHANGED.event(
        eventId, this, nowMillis, dailyConsumption, "${unit}/day", "user_learning_reset",
        metadata = mapOf("resetLearning" to "true", "effectiveAt" to nowMillis.toString())
    )
    return PantryTransition(updated, listOf(event))
}

fun PantryInventoryEvent.toFirestoreMap(): Map<String, Any> = buildMap {
    put("id", id)
    put("productId", productId)
    put("type", type.name)
    put("occurredAt", occurredAt)
    put("quantity", quantity)
    put("unit", unit)
    put("source", source)
    packageInstanceId?.let { put("packageInstanceId", it) }
    put("metadata", metadata)
}

fun pantryInventoryEventFromMap(data: Map<String, Any?>): PantryInventoryEvent? {
    val eventId = data["id"] as? String ?: return null
    val productId = data["productId"] as? String ?: return null
    val type = runCatching { PantryEventType.valueOf(data["type"] as? String ?: return null) }.getOrNull() ?: return null
    val metadata = (data["metadata"] as? Map<*, *>)?.mapNotNull { (key, value) ->
        if (key is String && value is String) key to value else null
    }?.toMap().orEmpty()
    return PantryInventoryEvent(
        id = eventId,
        productId = productId,
        type = type,
        occurredAt = (data["occurredAt"] as? Number)?.toLong() ?: return null,
        quantity = ((data["quantity"] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0),
        unit = data["unit"] as? String ?: "",
        source = data["source"] as? String ?: "unknown",
        packageInstanceId = data["packageInstanceId"] as? String,
        metadata = metadata
    )
}

fun PantryItem.toFirestoreMap(): Map<String, Any?> = mapOf(
    "name" to name,
    "category" to category,
    "quantity" to quantity,
    "unit" to unit,
    "lowStockThreshold" to lowStockThreshold,
    "price" to price,
    "targetQuantity" to targetQuantity,
    "packageSize" to packageSize,
    "quantityStep" to quantityStep,
    "emoji" to emoji,
    "trackingMode" to trackingMode,
    "trackedCount" to trackedCount,
    "currentPackageRemaining" to currentPackageRemaining,
    "additionalOpenedRemaining" to additionalOpenedRemaining,
    "unopenedPackages" to unopenedPackages,
    "dailyConsumption" to dailyConsumption,
    "usesPerDay" to usesPerDay,
    "amountPerUse" to amountPerUse,
    "useUnit" to useUnit,
    "customStockUnitsPerUseUnit" to customStockUnitsPerUseUnit,
    "usesPerPeriod" to usesPerPeriod,
    "usagePeriod" to usagePeriod,
    "restockLeadDays" to restockLeadDays,
    "lastStockUpdateAt" to lastStockUpdateAt,
    "lastPackageOpenedAt" to lastPackageOpenedAt,
    "currentPackageId" to currentPackageId,
    "currentPackageLearningEligible" to currentPackageLearningEligible,
    "processedEventIds" to processedEventIds,
    "completedPackageRecords" to completedPackageRecords.map { record ->
        mapOf(
            "durationDays" to record.durationDays,
            "packageSize" to record.packageSize,
            "completedAt" to record.completedAt,
            "openingQuantity" to record.openingQuantity,
            "packageInstanceId" to record.packageInstanceId,
            "verifiedFullStart" to record.verifiedFullStart,
            "unit" to record.unit
        )
    },
    "purchasePriceHistory" to purchasePriceHistory
)

fun pantryItemFromMap(id: String, data: Map<String, Any?>, defaultQuantityStep: Double = 0.25): PantryItem {
    fun number(key: String, fallback: Double): Double = (data[key] as? Number)?.toDouble() ?: fallback
    val threshold = number("alertThreshold", number("lowStockThreshold", 1.0))
    val completed = (data["completedPackageRecords"] as? List<*>)?.mapNotNull { raw ->
        val record = raw as? Map<*, *> ?: return@mapNotNull null
        val duration = (record["durationDays"] as? Number)?.toDouble() ?: return@mapNotNull null
        val amount = (record["packageSize"] as? Number)?.toDouble() ?: return@mapNotNull null
        PackageCompletion(
            durationDays = duration,
            packageSize = amount,
            completedAt = (record["completedAt"] as? Number)?.toLong() ?: 0L,
            openingQuantity = (record["openingQuantity"] as? Number)?.toDouble() ?: 0.0,
            packageInstanceId = record["packageInstanceId"] as? String ?: "",
            verifiedFullStart = record["verifiedFullStart"] as? Boolean ?: false,
            unit = record["unit"] as? String ?: ""
        )
    }.orEmpty()
    val processedIds = (data["processedEventIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
    return PantryItem(
        id = id,
        name = data["name"] as? String ?: "",
        category = data["category"] as? String ?: "Household",
        quantity = number("currentQuantity", number("quantity", 0.0)),
        unit = data["unit"] as? String ?: "Units",
        lowStockThreshold = threshold,
        price = number("price", 0.0),
        targetQuantity = number("targetQuantity", maxOf(threshold, 2.0)),
        packageSize = number("packageSize", 1.0),
        quantityStep = number("quantityStep", defaultQuantityStep),
        emoji = data["emoji"] as? String,
        trackingMode = data["trackingMode"] as? String ?: PantryTrackingMode.LEGACY.name,
        trackedCount = (data["trackedCount"] as? Number)?.toDouble(),
        currentPackageRemaining = number("currentPackageRemaining", 0.0),
        additionalOpenedRemaining = number("additionalOpenedRemaining", 0.0),
        unopenedPackages = number("unopenedPackages", 0.0).toInt().coerceAtLeast(0),
        dailyConsumption = number("dailyConsumption", 0.0),
        usesPerDay = number("usesPerDay", 0.0),
        amountPerUse = number("amountPerUse", 0.0),
        useUnit = data["useUnit"] as? String ?: "",
        customStockUnitsPerUseUnit = (data["customStockUnitsPerUseUnit"] as? Number)?.toDouble(),
        usesPerPeriod = number("usesPerPeriod", number("usesPerDay", 0.0)),
        usagePeriod = data["usagePeriod"] as? String ?: "DAY",
        restockLeadDays = number("restockLeadDays", 7.0),
        lastStockUpdateAt = number("lastStockUpdateAt", 0.0).toLong(),
        lastPackageOpenedAt = number("lastPackageOpenedAt", 0.0).toLong(),
        currentPackageId = data["currentPackageId"] as? String,
        currentPackageLearningEligible = data["currentPackageLearningEligible"] as? Boolean ?: false,
        processedEventIds = processedIds,
        completedPackageRecords = completed,
        purchasePriceHistory = (data["purchasePriceHistory"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble()?.takeIf { price -> price.isFinite() && price > 0.0 } }.orEmpty().takeLast(12)
    )
}
