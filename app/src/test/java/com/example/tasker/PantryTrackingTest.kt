package com.example.tasker

import com.example.pantry.ui.PackageCompletion
import com.example.pantry.ui.PantryEventType
import com.example.pantry.ui.PantryItem
import com.example.pantry.ui.PantryStockStatus
import com.example.pantry.ui.PantryTrackingMode
import com.example.pantry.ui.addPurchasedPackages
import com.example.pantry.ui.calculateDailyConsumption
import com.example.pantry.ui.correctInventory
import com.example.pantry.ui.effectiveDailyConsumption
import com.example.pantry.ui.estimateAt
import com.example.pantry.ui.finishCurrentPackage
import com.example.pantry.ui.isRunningLow
import com.example.pantry.ui.openNewPackage
import com.example.pantry.ui.packagesToRestockAt
import com.example.pantry.ui.pantryItemFromMap
import com.example.pantry.ui.pantryInventoryEventFromMap
import com.example.pantry.ui.recordApproximateUse
import com.example.pantry.ui.resetLearnedEstimate
import com.example.pantry.ui.suggestedTrackingMode
import com.example.pantry.ui.stockStatusAt
import com.example.pantry.ui.toFirestoreMap
import com.example.pantry.ui.usageConfigurationEvent
import com.example.pantry.ui.PantryTransition
import com.example.pantry.ui.withStableEventIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryTrackingTest {
    @Test fun legacyQuantityIsPreservedWithoutInventingPackageState() {
        val legacy = PantryItem(id = "old", quantity = 0.25, packageSize = 1.0)
        val restored = pantryItemFromMap(legacy.id, legacy.toFirestoreMap())
        assertEquals(0.25, restored.quantity, 0.0)
        assertEquals(PantryTrackingMode.LEGACY.name, restored.trackingMode)
        assertNull(restored.estimateAt(NOW).daysRemaining)
        assertNull(restored.estimateAt(NOW).currentPackageRemaining)
        assertFalse(restored.currentPackageLearningEligible)
    }

    @Test fun inventoryStatusesDistinguishSetupLowAndEstimatedEmpty() {
        val legacy = PantryItem(quantity = 1.25)
        assertEquals(PantryStockStatus.SETUP_NEEDED, legacy.stockStatusAt(NOW))
        assertEquals(1.25, legacy.quantity, 0.0)

        val count = PantryItem(trackingMode = PantryTrackingMode.COUNT.name, trackedCount = 0.0)
        assertEquals(PantryStockStatus.OUT_OF_STOCK, count.stockStatusAt(NOW))

        val predictive = consumption(10.0, 10.0, 2.0, lead = 7.0)
        assertEquals(PantryStockStatus.LOW, predictive.stockStatusAt(NOW + 3 * DAY))
        assertEquals(PantryStockStatus.ESTIMATED_EMPTY, predictive.stockStatusAt(NOW + 5 * DAY))
    }

    @Test fun dogFoodUnitConversionAndFiveDayEstimateAreCorrect() {
        val daily = calculateDailyConsumption(4.0, 4.0, "oz", "lb")!!
        assertEquals(1.0, daily, 0.0001)
        val ouncesDaily = calculateDailyConsumption(4.0, 4.0, "oz", "oz")!!
        val food = consumption(packageSize = 256.0, current = 256.0, rate = ouncesDaily, unit = "oz")
        val afterFiveDays = food.estimateAt(NOW + 5 * DAY)
        assertEquals(176.0, afterFiveDays.currentPackageRemaining!!, 0.0001)
        assertEquals(176.0, afterFiveDays.totalRemaining!!, 0.0001)
    }

    @Test fun weeklyActivityNormalizesToDailyRate() {
        val daily = calculateDailyConsumption(14.0, 1.0, "pod", "pod", 1.0, "WEEK")
        assertEquals(2.0, daily!!, 0.0001)
    }

    @Test fun packageDurationCalculationUsesConfiguredRateAndElapsedTime() {
        val estimate = consumption(256.0, 256.0, 16.0, unit = "oz").estimateAt(NOW + 5 * DAY)
        assertEquals(11.0, estimate.daysRemaining!!, 0.0001)
        assertEquals(NOW + 16 * DAY, estimate.depletionAtMillis)
    }

    @Test fun estimateDoesNotWriteOrSubtractFromPersistedState() {
        val original = consumption(100.0, 80.0, 5.0)
        original.estimateAt(NOW + 2 * DAY)
        assertEquals(80.0, original.currentPackageRemaining, 0.0)
        assertEquals(NOW, original.lastStockUpdateAt)
    }

    @Test fun elapsedUseFlowsThroughUnopenedBackupsWithoutCreatingPackageEvents() {
        val item = consumption(16.0, 4.0, 1.0, backups = 1)
        val afterTenDays = item.estimateAt(NOW + 10 * DAY)
        assertEquals(10.0, afterTenDays.totalRemaining!!, 0.0)
        assertEquals(10.0, afterTenDays.currentPackageRemaining!!, 0.0)
        assertEquals(0, afterTenDays.unopenedPackages)
        assertEquals(1, item.unopenedPackages) // estimate is virtual and does not write a predicted opening.
        assertEquals(NOW, item.lastStockUpdateAt)
    }

    @Test fun confirmingAnEstimatedBackupOpenDoesNotResetItToAFullPackageOrTrainIt() {
        val item = consumption(16.0, 4.0, 1.0, backups = 1)
        val confirmation = item.openNewPackage(NOW + 10 * DAY, "confirm-open")!!
        assertEquals(10.0, confirmation.item.currentPackageRemaining, 0.0)
        assertEquals(0, confirmation.item.unopenedPackages)
        assertFalse(confirmation.item.currentPackageLearningEligible)
        assertEquals("false", confirmation.events.single().metadata["learningEligible"])
    }

    @Test fun partialPackageAndBackupsContributeToTotalSupply() {
        val item = consumption(16.0, 8.0, 1.0, backups = 2)
        val estimate = item.estimateAt(NOW)
        assertEquals(40.0, estimate.totalRemaining!!, 0.0)
        assertEquals(40.0, estimate.daysRemaining!!, 0.0)
        assertEquals(2, estimate.unopenedPackages)
    }

    @Test fun invalidLegacyInventoryNumbersAreClampedForEstimatesAndRestockMath() {
        val counted = PantryItem(
            trackingMode = PantryTrackingMode.COUNT.name,
            trackedCount = Double.NaN,
            targetQuantity = Double.POSITIVE_INFINITY,
            packageSize = Double.POSITIVE_INFINITY
        )
        assertEquals(0.0, counted.estimateAt(NOW).totalRemaining!!, 0.0)
        assertTrue(counted.packagesToRestockAt(NOW) >= 0)
        val packaged = PantryItem(
            trackingMode = PantryTrackingMode.PACKAGE.name,
            packageSize = 10.0,
            currentPackageRemaining = Double.NaN,
            additionalOpenedRemaining = Double.POSITIVE_INFINITY,
            unopenedPackages = -2
        )
        assertEquals(0.0, packaged.estimateAt(NOW).totalRemaining!!, 0.0)
        assertFalse(packaged.estimateAt(NOW).totalRemaining!!.isNaN())
    }

    @Test fun openingNewPackagePreservesPreviousOpenedRemainderAndConsumesOneBackup() {
        val item = consumption(16.0, 4.0, 1.0, backups = 1)
        val transition = item.openNewPackage(NOW + DAY, "open-1")!!
        assertEquals(16.0, transition.item.currentPackageRemaining, 0.0)
        assertEquals(3.0, transition.item.additionalOpenedRemaining, 0.0)
        assertEquals(0, transition.item.unopenedPackages)
        assertEquals("open-1", transition.events.single().id)
        assertEquals(PantryEventType.PACKAGE_OPENED, transition.events.single().type)
    }

    @Test fun purchaseAndConsumptionEventsPersistAcrossModelRoundTrip() {
        val purchaseDate = NOW - DAY
        val bought = consumption(16.0, 8.0, 1.0).addPurchasedPackages(2, NOW, "buy-1", purchaseTotal = 15.0, purchaseDateMillis = purchaseDate)
        assertEquals(2, bought.item.unopenedPackages)
        assertEquals(PantryEventType.PURCHASE_RECORDED, bought.events.single().type)
        assertEquals(purchaseDate, bought.events.single().occurredAt)
        assertEquals("15.0", bought.events.single().metadata["purchaseTotal"])
        assertEquals(7.5, bought.item.price, 0.0)
        assertEquals(listOf(7.5), bought.item.purchasePriceHistory)
        val restored = pantryItemFromMap("x", bought.item.toFirestoreMap())
        assertEquals(2, restored.unopenedPackages)
        assertEquals(listOf(7.5), restored.purchasePriceHistory)
        val used = restored.recordApproximateUse(0.25, NOW, "use-1")
        assertEquals(4.0, used.events.single().quantity, 0.0)
        assertEquals(PantryEventType.CONSUMPTION_RECORDED, used.events.single().type)
        assertEquals(4.0, used.item.currentPackageRemaining, 0.0)
        val restoredEvent = pantryInventoryEventFromMap(used.events.single().toFirestoreMap())!!
        assertEquals(used.events.single(), restoredEvent)
    }

    @Test fun duplicatePurchaseEventDoesNotIncreaseInventoryOrPriceHistoryTwice() {
        val original = consumption(16.0, 8.0, 1.0)
        val first = original.addPurchasedPackages(2, NOW, "same-purchase", purchaseTotal = 20.0)
        val duplicate = first.item.addPurchasedPackages(2, NOW, "same-purchase", purchaseTotal = 20.0)
        assertEquals(2, first.item.unopenedPackages)
        assertEquals(first.item, duplicate.item)
        assertTrue(duplicate.events.isEmpty())
        assertEquals(listOf(10.0), duplicate.item.purchasePriceHistory)
    }

    @Test fun repeatedSameSecondInventoryActionUsesOneStableEventId() {
        val original = consumption(16.0, 16.0, 1.0)
        val firstRaw = original.recordApproximateUse(0.25, NOW, "random-a")
        val first = PantryTransition(firstRaw.item, firstRaw.events).withStableEventIds(original)!!
        val secondRaw = original.recordApproximateUse(0.25, NOW + 500, "random-b")
        val duplicate = PantryTransition(secondRaw.item, secondRaw.events).withStableEventIds(first.item)
        assertEquals(1, first.events.size)
        assertEquals(null, duplicate)
        assertTrue(first.events.single().id in first.item.processedEventIds)
    }

    @Test fun confirmedPackageFinishCreatesEligibleLearningEvidenceOnlyForKnownFullOpen() {
        val opened = consumption(16.0, 16.0, 1.0).copy(
            currentPackageId = "package-1", currentPackageLearningEligible = true, lastPackageOpenedAt = NOW
        )
        val finished = opened.finishCurrentPackage(NOW + 18 * DAY, "finish-1")
        assertEquals(1, finished.item.completedPackageRecords.size)
        assertTrue(finished.item.completedPackageRecords.single().verifiedFullStart)
        assertEquals("unit", finished.item.completedPackageRecords.single().unit)
        assertEquals(18.0, finished.item.completedPackageRecords.single().durationDays, 0.0001)
        assertEquals(0.0, finished.item.currentPackageRemaining, 0.0)
        assertEquals(PantryEventType.PACKAGE_FINISHED, finished.events.single().type)
        val uncertain = opened.copy(currentPackageLearningEligible = false).finishCurrentPackage(NOW + 18 * DAY)
        assertTrue(uncertain.item.completedPackageRecords.isEmpty())
    }

    @Test fun learningUsesConfirmedCyclesGraduallyAndKeepsBaselineWhenEvidenceIsAbsent() {
        val baseline = consumption(16.0, 16.0, 1.0)
        assertEquals(1.0, baseline.effectiveDailyConsumption()!!, 0.0)
        val history = listOf(18.0, 17.0, 19.0).mapIndexed { index, days ->
            PackageCompletion(days, 16.0, NOW + index * DAY, 16.0, "p$index", true, "unit")
        }
        val learned = baseline.copy(completedPackageRecords = history).effectiveDailyConsumption()!!
        val predictedDuration = 16.0 / learned
        assertTrue(predictedDuration in 16.0..20.0)
        assertNotEquals(0.0, learned, 0.0)
    }

    @Test fun outlierInfluenceIsBounded() {
        val records = listOf(16.0, 16.0, 0.1).mapIndexed { index, days ->
            PackageCompletion(days, 16.0, NOW + index * DAY, 16.0, "p$index", true, "unit")
        }
        val learned = consumption(16.0, 16.0, 1.0).copy(completedPackageRecords = records).effectiveDailyConsumption()!!
        assertTrue(learned in 0.5..1.5)
    }

    @Test fun usageChangeAppliesProspectivelyAndKeepsHistory() {
        val history = listOf(PackageCompletion(18.0, 100.0, NOW, 100.0, "old", true, "unit"))
        val beforeChange = consumption(100.0, 100.0, 5.0).copy(completedPackageRecords = history)
        val changeAt = NOW + 3 * DAY
        val checkpoint = beforeChange.estimateAt(changeAt).currentPackageRemaining!!
        val oldRate = beforeChange.effectiveDailyConsumption()!!
        assertEquals(100.0 - oldRate * 3.0, checkpoint, 0.0001)
        val changed = beforeChange.copy(
            currentPackageRemaining = checkpoint, dailyConsumption = 2.0, lastStockUpdateAt = changeAt,
            completedPackageRecords = history
        )
        val newBlendedRate = changed.effectiveDailyConsumption()!!
        assertEquals(checkpoint - newBlendedRate * 2.0, changed.estimateAt(changeAt + 2 * DAY).currentPackageRemaining!!, 0.0001)
        val event = beforeChange.usageConfigurationEvent(changed, changeAt, "cfg-1")
        assertEquals(1, event.item.completedPackageRecords.size)
        assertEquals(PantryEventType.USAGE_CONFIGURATION_CHANGED, event.events.single().type)
        assertEquals(changeAt.toString(), event.events.single().metadata["effectiveAt"])
    }

    @Test fun oldOrIncompatibleUnitObservationsAreNotMixedWithCurrentUsageUnits() {
        val item = consumption(16.0, 16.0, 1.0, unit = "oz").copy(
            completedPackageRecords = listOf(
                PackageCompletion(8.0, 16.0, NOW, 16.0, "old-unknown", true),
                PackageCompletion(8.0, 16.0, NOW + DAY, 16.0, "bad-unit", true, "cups")
            )
        )
        assertEquals(1.0, item.effectiveDailyConsumption()!!, 0.0)
    }

    @Test fun usageConfigurationChangeDisqualifiesCurrentCycleFromLearning() {
        val original = consumption(16.0, 16.0, 1.0).copy(
            currentPackageId = "p", currentPackageLearningEligible = true, lastPackageOpenedAt = NOW
        )
        val configured = original.usageConfigurationEvent(original.copy(dailyConsumption = 2.0), NOW + DAY, "rate-change")
        assertFalse(configured.item.currentPackageLearningEligible)
        val finish = configured.item.finishCurrentPackage(NOW + 18 * DAY, "finish-after-change")
        assertTrue(finish.item.completedPackageRecords.isEmpty())
    }

    @Test fun finishAfterPredictedBackupBoundaryDoesNotTrainAsOnePackage() {
        val item = consumption(16.0, 16.0, 1.0, backups = 1).copy(
            currentPackageId = "p", currentPackageLearningEligible = true, lastPackageOpenedAt = NOW
        )
        val finish = item.finishCurrentPackage(NOW + 18 * DAY, "finish-late")
        assertTrue(finish.item.completedPackageRecords.isEmpty())
        assertEquals("false", finish.events.single().metadata["usedForLearning"])
    }

    @Test fun inventoryCorrectionsClampNegativeAmountsAndRecordCorrection() {
        val correction = consumption(10.0, 5.0, 1.0).correctInventory(-3.0, -1.0, -2, NOW, eventId = "correct-1")
        assertEquals(0.0, correction.item.currentPackageRemaining, 0.0)
        assertEquals(0.0, correction.item.additionalOpenedRemaining, 0.0)
        assertEquals(0, correction.item.unopenedPackages)
        assertEquals(PantryEventType.INVENTORY_CORRECTED, correction.events.single().type)
    }

    @Test fun zeroInvalidAndUnknownUseRatesDoNotEnablePrediction() {
        assertNull(calculateDailyConsumption(0.0, 4.0, "oz", "oz"))
        assertNull(calculateDailyConsumption(Double.NaN, 4.0, "oz", "oz"))
        assertNull(calculateDailyConsumption(4.0, 1.0, "scoop", "lb"))
        assertNull(consumption(10.0, 10.0, 0.0).estimateAt(NOW).daysRemaining)
    }

    @Test fun restockWarningUsesTotalSupplyIncludingBackupsAndLeadTime() {
        val item = consumption(16.0, 4.0, 1.0, backups = 1, lead = 5.0)
        assertFalse(item.isRunningLow(NOW)) // 20 days including backup.
        assertTrue(item.isRunningLow(NOW + 15 * DAY)) // 5 days total supply remain.
    }

    @Test fun duplicateEventIdIsIgnoredAndDoesNotSubtractTwice() {
        val item = consumption(16.0, 16.0, 1.0)
        val first = item.recordApproximateUse(0.25, NOW, "same-id")
        val duplicate = first.item.recordApproximateUse(0.25, NOW, "same-id")
        assertEquals(12.0, first.item.currentPackageRemaining, 0.0)
        assertEquals(first.item, duplicate.item)
        assertTrue(duplicate.events.isEmpty())
    }

    @Test fun resetLearningLeavesInventoryAndPurchaseHistoryIntact() {
        val item = consumption(16.0, 8.0, 1.0, backups = 2).copy(
            completedPackageRecords = listOf(PackageCompletion(18.0, 16.0, NOW, 16.0, "p", true, "unit"))
        )
        val reset = item.resetLearnedEstimate(NOW, "reset-1")
        assertTrue(reset.item.completedPackageRecords.isEmpty())
        assertEquals(item.unopenedPackages, reset.item.unopenedPackages)
        assertEquals(PantryEventType.USAGE_CONFIGURATION_CHANGED, reset.events.single().type)
        assertEquals("user_learning_reset", reset.events.single().source)
    }

    @Test fun suggestedModesCoverConsumptionActivityApproximateAndCount() {
        assertEquals(PantryTrackingMode.CONSUMPTION, PantryItem(name = "Dog Food").suggestedTrackingMode())
        assertEquals(PantryTrackingMode.ACTIVITY, PantryItem(name = "Laundry detergent").suggestedTrackingMode())
        assertEquals(PantryTrackingMode.PACKAGE, PantryItem(name = "Bleach").suggestedTrackingMode())
        assertEquals(PantryTrackingMode.COUNT, PantryItem(name = "Sponges").suggestedTrackingMode())
    }

    private fun consumption(
        packageSize: Double,
        current: Double,
        rate: Double,
        unit: String = "unit",
        backups: Int = 0,
        lead: Double = 7.0
    ) = PantryItem(
        id = "product-1", trackingMode = PantryTrackingMode.CONSUMPTION.name,
        packageSize = packageSize, currentPackageRemaining = current,
        dailyConsumption = rate, lastStockUpdateAt = NOW, unopenedPackages = backups,
        restockLeadDays = lead, unit = unit
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY = 86_400_000L
    }
}
