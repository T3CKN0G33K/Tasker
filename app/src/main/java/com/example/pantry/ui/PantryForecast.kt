package com.example.pantry.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

enum class RestockUrgency { NEEDS_ATTENTION, UPCOMING }

data class RestockRecommendation(
    val id: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val packageSize: Double,
    val unit: String,
    val urgency: RestockUrgency,
    val suggestedDate: LocalDate,
    val depletionDate: LocalDate?,
    val estimatedCost: Double?,
    val priceSource: String?,
    val reason: String,
    val confidence: String?
)

data class HouseholdSpendingForecast(
    val days: Int,
    val total: Double,
    val pricedProducts: Int,
    val unpricedProducts: Int
)

/** Pure, deterministic restock and cost forecast calculations. */
object PantryForecastEngine {
    fun recommendations(items: List<PantryItem>, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): List<RestockRecommendation> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return items.mapNotNull { item -> recommendation(item, today, nowMillis, zoneId) }
            .sortedWith(compareBy<RestockRecommendation>({ it.urgency.ordinal }, { it.suggestedDate }, { it.productName.lowercase() }))
    }

    private fun recommendation(item: PantryItem, today: LocalDate, nowMillis: Long, zoneId: ZoneId): RestockRecommendation? {
        val mode = PantryTrackingMode.fromStored(item.trackingMode)
        if (mode == PantryTrackingMode.LEGACY) return null
        val estimate = item.estimateAt(nowMillis)
        val available = estimate.totalRemaining?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val unitSize = item.packageSize.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val rate = estimate.effectiveDailyConsumption?.takeIf { it.isFinite() && it > 0.0 }
        val depletionDate = estimate.depletionAtMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        val leadDays = item.restockLeadDays.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val target = item.targetQuantity.takeIf { it.isFinite() && it > 0.0 } ?: unitSize

        val purchaseDate: LocalDate
        val urgency: RestockUrgency
        val reason: String
        val needAtPurchase: Double
        if (rate != null && depletionDate != null) {
            purchaseDate = depletionDate.minusDays(ceil(leadDays).toLong()).let { if (it.isBefore(today)) today else it }
            urgency = if (purchaseDate <= today.plusDays(7)) RestockUrgency.NEEDS_ATTENTION else RestockUrgency.UPCOMING
            val daysUntilPurchase = kotlin.math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(today, purchaseDate)).toDouble()
            val remainingAtPurchase = (available - rate * daysUntilPurchase).coerceAtLeast(0.0)
            needAtPurchase = (target - remainingAtPurchase).coerceAtLeast(0.0)
            reason = if (depletionDate.isBefore(today.plusDays(1))) "Stock is estimated to be depleted; restock timing is approximate."
            else "Estimated to run out ${depletionDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}; includes ${item.unopenedPackages} unopened backup package(s)."
        } else {
            // Count and approximate-package products have no reliable time rate. Only surface a
            // confirmed count/threshold shortage for review; never invent a future date.
            val threshold = item.lowStockThreshold.coerceAtLeast(0.0)
            if (available > threshold) return null
            purchaseDate = today
            urgency = RestockUrgency.NEEDS_ATTENTION
            needAtPurchase = (target - available).coerceAtLeast(unitSize)
            reason = "Manual stock is at or below its restock threshold; review the quantity."
        }
        val quantity = ceil(needAtPurchase / unitSize).toInt().coerceAtLeast(1)
        if (quantity <= 0 || item.name.isBlank()) return null
        val (price, priceSource) = item.forecastUnitPrice()
        return RestockRecommendation(
            id = "restock-${item.id}", productId = item.id, productName = item.name,
            quantity = quantity, packageSize = unitSize, unit = item.unit, urgency = urgency,
            suggestedDate = purchaseDate, depletionDate = depletionDate,
            estimatedCost = price?.times(quantity), priceSource = priceSource, reason = reason,
            confidence = estimate.confidence.takeIf { rate != null }
        )
    }

    fun spendingForecast(recommendations: List<RestockRecommendation>, today: LocalDate, days: Int): HouseholdSpendingForecast {
        require(days in setOf(7, 14, 30))
        val end = today.plusDays(days.toLong())
        val due = recommendations.filter { !it.suggestedDate.isBefore(today) && !it.suggestedDate.isAfter(end) }
        return HouseholdSpendingForecast(
            days = days,
            total = due.sumOf { it.estimatedCost ?: 0.0 },
            pricedProducts = due.count { it.estimatedCost != null },
            unpricedProducts = due.count { it.estimatedCost == null }
        )
    }
}
