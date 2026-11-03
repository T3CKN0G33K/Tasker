package com.example.tasker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.mutableStateOf
import com.example.pantry.ui.PantryItem
import com.example.pantry.ui.PantryTrackingMode
import com.example.pantry.ui.PantryScreen
import androidx.activity.ComponentActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class PantryScreenRenderTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun consumptionCardAndTrackingDetailsRender() {
        val now = System.currentTimeMillis()
        val item = PantryItem(
            id = "dog-food",
            name = "Dog Food",
            category = "Pet Care",
            unit = "lb",
            packageSize = 16.0,
            trackingMode = PantryTrackingMode.CONSUMPTION.name,
            currentPackageRemaining = 16.0,
            dailyConsumption = 1.0,
            lastStockUpdateAt = now
        )

        composeRule.setContent { PantryScreen(items = listOf(item)) }
        composeRule.onNodeWithText("About 16 days of supply", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Details ›").performClick()
        composeRule.onNodeWithText("Manage supply").assertIsDisplayed()
        composeRule.onNodeWithText("Tracking settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Inventory tracking").assertIsDisplayed()
        composeRule.onNodeWithText("Finished Package").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun productDetailsAreFullScreenAndBackRestoresPantryScroll() {
        val products = (0..24).map { index ->
            PantryItem(
                id = "product-$index", name = "Product $index", category = "Household", unit = "items",
                trackingMode = PantryTrackingMode.COUNT.name, trackedCount = 3.0, quantity = 3.0
            )
        }
        composeRule.setContent { PantryScreen(items = products) }
        repeat(8) { composeRule.onRoot().performTouchInput { swipeUp() } }
        composeRule.onNodeWithText("Product 24").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Inventory status").assertIsDisplayed()
        composeRule.onNodeWithText("Manage supply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Pantry").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Product 24").assertIsDisplayed()
    }

    @Test
    fun trackingSettingsAreASeparateDestinationAndBackReturnsToProduct() {
        val item = PantryItem(
            id = "dog-food", name = "Dog Food", category = "Pet Care", unit = "lb",
            packageSize = 16.0, trackingMode = PantryTrackingMode.CONSUMPTION.name,
            currentPackageRemaining = 16.0, dailyConsumption = 1.0,
            lastStockUpdateAt = System.currentTimeMillis()
        )
        val fullScreenStateChanges = mutableListOf<Boolean>()
        composeRule.setContent {
            PantryScreen(
                items = listOf(item),
                onFullScreenDestinationChange = { fullScreenStateChanges += it }
            )
        }
        composeRule.onNodeWithText("Details ›").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Inventory tracking").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(true, fullScreenStateChanges.last()) }

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("Manage supply").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Pantry").assertIsDisplayed()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText("Dog Food").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(false, fullScreenStateChanges.last()) }
    }

    @Test
    fun packageOpenPurchaseAndFinishActionsStillEmitInventoryEvents() {
        val itemState = mutableStateOf(
            PantryItem(
                id = "bleach", name = "Bleach", category = "Cleaning", unit = "bottles",
                packageSize = 1.0, trackingMode = PantryTrackingMode.PACKAGE.name,
                currentPackageRemaining = 0.0, unopenedPackages = 1,
                lastStockUpdateAt = System.currentTimeMillis()
            )
        )
        val emitted = mutableListOf<String>()
        composeRule.setContent {
            PantryScreen(items = listOf(itemState.value), onInventoryEvent = { updated, events ->
                itemState.value = updated
                emitted += events.map { it.type.name }
            })
        }

        composeRule.onNodeWithText("Details ›").performClick()
        composeRule.onNodeWithText("Opened new").performClick()
        composeRule.onNodeWithText("Open package").performClick()
        composeRule.runOnIdle {
            assertEquals(1.0, itemState.value.currentPackageRemaining, 0.0)
            assertEquals(0, itemState.value.unopenedPackages)
            assertEquals("PACKAGE_OPENED", emitted.last())
        }

        composeRule.onNodeWithText("Bought more").performClick()
        composeRule.onNodeWithText("Add purchase").performClick()
        composeRule.runOnIdle {
            assertEquals(1, itemState.value.unopenedPackages)
            assertEquals("PURCHASE_RECORDED", emitted.last())
        }

        composeRule.onNodeWithText("Finished package").performClick()
        composeRule.onNodeWithText("Confirm finished").performClick()
        composeRule.runOnIdle {
            assertEquals("PACKAGE_FINISHED", emitted.last())
        }
    }

    @Test
    fun countCardActionEmitsPersistentInventoryTransition() {
        val item = PantryItem(
            id = "rolls", name = "Paper Towels", category = "Household", unit = "rolls",
            trackingMode = PantryTrackingMode.COUNT.name, trackedCount = 3.0, quantity = 3.0,
            lowStockThreshold = 1.0
        )
        var updated: PantryItem? = null
        var emittedEvents = emptyList<com.example.pantry.ui.PantryInventoryEvent>()
        composeRule.setContent {
            PantryScreen(items = listOf(item), onInventoryEvent = { saved, events ->
                updated = saved
                emittedEvents = events
            })
        }

        composeRule.onNodeWithText("3 rolls available", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Details ›").performClick()
        composeRule.onNodeWithText("Used one").performClick()
        composeRule.onNodeWithText("Record", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(2.0, updated?.trackedCount ?: -1.0, 0.0)
            assertEquals("CONSUMPTION_RECORDED", emittedEvents.single().type.name)
        }
    }

    @Test
    fun legacyDecimalAmountRemainsReadableAndMarkedForSetup() {
        val legacy = PantryItem(id = "legacy", name = "Old Cleaner", quantity = 1.25, unit = "bottles")
        composeRule.setContent { PantryScreen(items = listOf(legacy)) }
        composeRule.onNodeWithText("Saved amount: 1¼ bottles", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("TRACKING SETUP NEEDED").assertIsDisplayed()
    }

    @Test
    fun legacyConfirmStockAndTrackerSaveCompletesWithoutLeavingPantry() {
        val legacy = PantryItem(id = "legacy-cleaner", name = "Bleach", category = "Cleaning", quantity = 1.25, unit = "bottles")
        val saved = mutableStateOf<PantryItem?>(null)
        val emittedEvents = mutableListOf<String>()
        composeRule.setContent {
            PantryScreen(
                items = listOf(legacy),
                onItemUpdate = { saved.value = it },
                onInventoryEvent = { updated, events ->
                    saved.value = updated
                    emittedEvents += events.map { it.type.name }
                }
            )
        }

        composeRule.onNodeWithText("Details ›").performClick()
        composeRule.onNodeWithText("Confirm stock and set up tracking").performClick()
        composeRule.onNodeWithText("Use suggestion").performClick()
        composeRule.onNodeWithText("One package (bottles)").performTextReplacement("1")
        composeRule.onNodeWithText("Open package left").performTextReplacement("0.5")
        composeRule.onNodeWithText("Unopened packages").performTextReplacement("1")
        composeRule.onNodeWithText("Save Changes").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(PantryTrackingMode.PACKAGE.name, saved.value?.trackingMode)
            assertEquals(0.5, saved.value?.currentPackageRemaining ?: -1.0, 0.0)
            assertEquals(1, saved.value?.unopenedPackages)
            assertEquals(true, "INVENTORY_CORRECTED" in emittedEvents)
            assertEquals(true, "PACKAGE_OPENED" in emittedEvents)
        }
        composeRule.onNodeWithText("Inventory status").assertIsDisplayed()
    }

    @Test
    fun legacySimpleCountSetupWithZeroAndOneSavesWithoutPackageStateCrash() {
        val legacy = PantryItem(
            id = "legacy-stain-remover",
            name = "1 Gal. Stain and Odor Remover",
            category = "Cleaning",
            quantity = 1.0,
            unit = "Units"
        )
        val saved = mutableStateOf<PantryItem?>(null)
        val emittedEvents = mutableListOf<String>()
        composeRule.setContent {
            PantryScreen(
                items = listOf(legacy),
                onItemUpdate = { saved.value = it },
                onInventoryEvent = { updated, events ->
                    saved.value = updated
                    emittedEvents += events.map { it.type.name }
                }
            )
        }

        composeRule.onNodeWithText("Details ›").performClick()
        composeRule.onNodeWithText("Confirm stock and set up tracking").performClick()
        composeRule.onNodeWithText("Confirmed number on hand (Units)").performTextReplacement("0")
        composeRule.onNodeWithText("Number of Units in one purchase package").performScrollTo().performTextReplacement("1")
        composeRule.onNodeWithText("Save Changes").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(PantryTrackingMode.COUNT.name, saved.value?.trackingMode)
            assertEquals(0.0, saved.value?.trackedCount ?: -1.0, 0.0)
            assertEquals(1.0, saved.value?.packageSize ?: -1.0, 0.0)
            assertEquals(true, "INVENTORY_CORRECTED" in emittedEvents)
            assertEquals(false, "PACKAGE_OPENED" in emittedEvents)
        }
        composeRule.onNodeWithText("Inventory status").assertIsDisplayed()
    }
}
