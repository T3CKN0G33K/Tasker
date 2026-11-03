package com.example.tasker

import com.example.pantry.ui.PantryForecastEngine
import com.example.pantry.ui.PantryItem
import com.example.pantry.ui.PantryTrackingMode
import com.example.pantry.ui.RestockUrgency
import com.example.pantry.ui.forecastUnitPrice
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryForecastTest {
    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 11, 3)
    private val now = today.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun restockDateIncludesBackupPackagesAndLeadTime() {
        val food = PantryItem(
            id = "food", name = "Dog food", trackingMode = PantryTrackingMode.CONSUMPTION.name,
            packageSize = 16.0, currentPackageRemaining = 5.0, dailyConsumption = 1.0,
            lastStockUpdateAt = now, unopenedPackages = 1, restockLeadDays = 5.0,
            targetQuantity = 2.0, price = 24.99
        )
        val rec = PantryForecastEngine.recommendations(listOf(food), now, zone).single()
        assertEquals(today.plusDays(16), rec.suggestedDate)
        assertEquals(today.plusDays(21), rec.depletionDate)
        assertEquals(1, rec.quantity)
        assertEquals(24.99, rec.estimatedCost!!, 0.001)
        assertEquals(RestockUrgency.UPCOMING, rec.urgency)
    }

    @Test fun manualLowStockHasNoFabricatedDateOrPrice() {
        val sponge = PantryItem(
            id = "sponges", name = "Sponges", trackingMode = PantryTrackingMode.COUNT.name,
            trackedCount = 1.0, lowStockThreshold = 1.0, targetQuantity = 3.0, packageSize = 2.0
        )
        val rec = PantryForecastEngine.recommendations(listOf(sponge), now, zone).single()
        assertEquals(today, rec.suggestedDate)
        assertNull(rec.depletionDate)
        assertEquals(1, rec.quantity)
        assertNull(rec.estimatedCost)
        assertTrue(rec.reason.contains("Manual stock"))
    }

    @Test fun forecastUsesActualPurchaseAverageWhenAvailableAndSavedPriceOtherwise() {
        val current = PantryItem(id = "x", price = 30.0, purchasePriceHistory = listOf(20.0, 22.0, 24.0))
        val (average, source) = current.forecastUnitPrice()
        assertEquals(22.0, average!!, 0.001)
        assertEquals("Historical average", source)
        val fallback = PantryItem(id = "y", price = 8.5).forecastUnitPrice()
        assertEquals(8.5, fallback.first!!, 0.001)
        assertEquals("Saved package price", fallback.second)
    }

    @Test fun legacyAndUnconfiguredProductsAreNotInventedIntoForecast() {
        val items = listOf(
            PantryItem(id = "legacy", name = "Old decimal", quantity = 0.25),
            PantryItem(id = "unknown", name = "Unknown rate", trackingMode = PantryTrackingMode.CONSUMPTION.name,
                currentPackageRemaining = 4.0, packageSize = 4.0, dailyConsumption = 0.0, lastStockUpdateAt = now)
        )
        assertTrue(PantryForecastEngine.recommendations(items, now, zone).isEmpty())
    }

    @Test fun sevenFourteenAndThirtyDayForecastsIncludeOnlyRecommendationsInWindow() {
        val near = recommendation("near", today.plusDays(5), 12.0)
        val mid = recommendation("mid", today.plusDays(10), null)
        val far = recommendation("far", today.plusDays(25), 30.0)
        val all = listOf(near, mid, far)
        val week = PantryForecastEngine.spendingForecast(all, today, 7)
        val twoWeeks = PantryForecastEngine.spendingForecast(all, today, 14)
        val month = PantryForecastEngine.spendingForecast(all, today, 30)
        assertEquals(12.0, week.total, 0.001)
        assertEquals(0, week.unpricedProducts)
        assertEquals(12.0, twoWeeks.total, 0.001)
        assertEquals(1, twoWeeks.unpricedProducts)
        assertEquals(42.0, month.total, 0.001)
        assertEquals(1, month.unpricedProducts)
        assertFalse(twoWeeks.total > month.total)
    }

    private fun recommendation(id: String, date: LocalDate, cost: Double?) =
        com.example.pantry.ui.RestockRecommendation(
            id = id, productId = id, productName = id, quantity = 1, packageSize = 1.0, unit = "package",
            urgency = RestockUrgency.UPCOMING, suggestedDate = date, depletionDate = date.plusDays(5),
            estimatedCost = cost, priceSource = null, reason = "test data", confidence = null
        )
}
