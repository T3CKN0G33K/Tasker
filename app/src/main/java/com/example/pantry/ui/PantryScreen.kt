package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.liquidglass.ChromaticDispersionPresets
import com.example.liquidglass.LiquidGlassListItem
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

import androidx.compose.foundation.lazy.LazyRow

data class PantryItem(
    val id: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val lowStockThreshold: Double = 1.0,
    val price: Double = 0.0,
    val emoji: String? = null
)

fun PantryItem.resolveEmoji(): String {
    if (!emoji.isNullOrBlank()) return emoji
    return when (category.lowercase()) {
        "pet care" -> if (name.contains("dog", ignoreCase = true) || name.contains("pedigree", ignoreCase = true) || name.contains("puppy", ignoreCase = true)) "🐶" else "🐱"
        "household" -> if (name.contains("toilet", ignoreCase = true) || name.contains("paper", ignoreCase = true)) "🧻" else "🧼"
        "beverages" -> "☕"
        else -> "📦"
    }
}

val samplePantryData = listOf(
    PantryItem("1", "Cat Litter (Arm & Hammer)", "Pet Care", 0.25, "Bag", 0.5, 6.98),
    PantryItem("2", "Buddy's Dry Food", "Pet Care", 1.0, "Bag", 1.0, 24.99),
    PantryItem("3", "Energy Drinks", "Beverages", 8.0, "Cans", 2.0, 2.50),
    PantryItem("4", "Mac & Cheese", "Dry Goods", 1.0, "Boxes", 2.0, 1.25),
    PantryItem("5", "Paper Towels", "Household", 6.0, "Rolls", 2.0, 8.99)
)

@Composable
fun PantryScreen(
    items: List<PantryItem> = samplePantryData,
    backdrop: Backdrop? = null,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    onItemsChange: (List<PantryItem>) -> Unit = {},
    onItemUpdate: (PantryItem) -> Unit = {},
    onItemDelete: (String) -> Unit = {},
    onAddItem: (PantryItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var itemToEdit by remember { mutableStateOf<PantryItem?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.LightGray else Color(0xFF6C6C70)

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    val categoryList = remember(items) {
        listOf("All") + items.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    val filteredList = remember(items, searchQuery, selectedCategoryFilter) {
        items.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "All" || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            matchesSearch && matchesCategory
        }
    }

    val lowStockItems = remember(items) { items.filter { it.quantity <= it.lowStockThreshold } }
    val totalRestockEstimate = remember(lowStockItems) {
        lowStockItems.sumOf { item ->
            val neededPacks = ceil((item.lowStockThreshold - item.quantity + 1.0).coerceAtLeast(1.0))
            neededPacks * item.price
        }
    }

    val restockBackdrop = backdrop ?: rememberLayerBackdrop()
    val fabBackdrop = backdrop ?: rememberLayerBackdrop()
    val cardShape = remember { RoundedCornerShape(20.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // LAYER 1: Scrolling Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header Row - Large Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Pantry",
                            color = textMain,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }

                    // Search Input Pill when expanded
                    AnimatedContent(
                        targetState = isSearchExpanded,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "SearchPillTransition"
                    ) { expanded ->
                        if (expanded) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.75f))
                                    .border(
                                        width = 0.5.dp,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.12f))
                                        ),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = textSub,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textMain, fontSize = 16.sp),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text("Search Pantry...", color = textSub, fontSize = 16.sp)
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
                                            tint = textSub,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Executive Summary Widget: Low Stock Restock Estimate
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = restockBackdrop,
                                shape = { cardShape },
                                effects = {
                                    vibrancy()
                                    blur(12.dp.toPx())
                                    lens(refractionHeight = 6.dp.toPx(), refractionAmount = 10.dp.toPx())
                                },
                                highlight = { Highlight(width = 1.dp, alpha = 0.25f) },
                                onDrawSurface = {
                                    drawRect(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.12f),
                                                Color.White.copy(alpha = 0.04f)
                                            )
                                        )
                                    )
                                }
                            )
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30).copy(alpha = 0.18f)
                                            else Color(0xFF34C759).copy(alpha = 0.18f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF34C759),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Low Stock Restock Estimate",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", totalRestockEstimate)}",
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30).copy(alpha = 0.15f)
                                        else Color(0xFF34C759).copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (lowStockItems.isNotEmpty()) "${lowStockItems.size} Low" else "All Stocked",
                                    color = if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF30D158),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryList.take(5).forEach { category ->
                            val isSelected = selectedCategoryFilter == category
                            val chipBg = if (isSelected) activeAccentColor else Color.White.copy(alpha = 0.08f)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(chipBg)
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isSelected) activeAccentColor else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedCategoryFilter = category }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank() || selectedCategoryFilter != "All") "No matching items found." else "Your Pantry is empty! Tap '+' to add items.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = textSub
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    PantryItemCard(
                        item = item,
                        backdrop = backdrop,
                        isDarkMode = isDarkMode,
                        themePreference = themePreference,
                        onQuantityChange = { newQty ->
                            onItemUpdate(item.copy(quantity = newQty))
                        },
                        onEdit = { itemToEdit = item }
                    )
                }
            }
        }

        // LAYER 2: Floating Bottom Action Overlay (Search & Add Buttons)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp), // Floats just above 64dp taskbar
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Search Glass Button
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .drawBackdrop(
                        backdrop = fabBackdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(10.dp.toPx(), 14.dp.toPx())
                        },
                        highlight = { Highlight(width = 1.dp, alpha = 0.45f) },
                        shadow = { Shadow(radius = 12.dp, alpha = 0.25f) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) }
                    )
                    .clickable { isSearchExpanded = !isSearchExpanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Add Item Glass Button (Primary Accent)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .drawBackdrop(
                        backdrop = fabBackdrop,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(12.dp.toPx(), 16.dp.toPx())
                        },
                        highlight = { Highlight(width = 1.2.dp, alpha = 0.6f) },
                        shadow = { Shadow(radius = 16.dp, alpha = 0.35f) },
                        onDrawSurface = {
                            drawRect(Color(0xFF007AFF).copy(alpha = 0.85f))
                        }
                    )
                    .clickable { showAddItemDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Item",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    // Add Item Centered Glass Popup Dialog
    if (showAddItemDialog) {
        AddItemCenteredDialog(
            themePreference = themePreference,
            backdrop = backdrop,
            onDismiss = { showAddItemDialog = false },
            onSave = { newItem ->
                onAddItem(newItem)
                showAddItemDialog = false
            }
        )
    }

    // Edit Item Glass Popup Dialog
    itemToEdit?.let { item ->
        EditItemDialog(
            item = item,
            themePreference = themePreference,
            backdrop = backdrop,
            onDismiss = { itemToEdit = null },
            onSave = { updatedItem ->
                onItemUpdate(updatedItem)
                itemToEdit = null
            },
            onDelete = {
                onItemDelete(item.id)
                itemToEdit = null
            }
        )
    }
}

@Composable
fun AddItemCenteredDialog(
    themePreference: String = "DEFAULT",
    backdrop: Backdrop? = null,
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Dry Goods") }
    var unit by remember { mutableStateOf("Units") }
    var quantityText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }
    var lowStockThresholdText by remember { mutableStateOf("1") }
    var selectedEmoji by remember { mutableStateOf("📦") }
    var isScanning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val categories = listOf("Dry Goods", "Beverages", "Pet Care", "Household")
    val presetEmojis = listOf("🐶", "🐱", "🐾", "🧻", "🧼", "🧹", "🥫", "🍞", "🥛", "☕", "📦")

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
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
        backdrop = backdrop,
        themePreference = themePreference
    ) { _ ->
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
                focusedBorderColor = activeAccentColor,
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
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = activeAccentColor)
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
                label = { Text("Unit Price ($ e.g. 6.98)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            Text("Category", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = category == cat
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) activeAccentColor else Color.White.copy(alpha = 0.08f))
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

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity", color = Color.White.copy(alpha = 0.70f)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
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

            // Emoji Selector Preview Badge & Preset Row
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
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(presetEmojis) { em ->
                            val isSelected = selectedEmoji == em
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) activeAccentColor else Color.White.copy(alpha = 0.10f))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.20f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedEmoji = em },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = em, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            GlassHeroButton(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "Please enter an item name.", Toast.LENGTH_SHORT).show()
                        return@GlassHeroButton
                    }
                    val parsedQty = quantityText.toDoubleOrNull() ?: 1.0
                    val threshold = lowStockThresholdText.toDoubleOrNull() ?: 1.0
                    val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                    val newItem = PantryItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        category = category,
                        quantity = parsedQty,
                        unit = unit.ifBlank { "Units" },
                        lowStockThreshold = threshold,
                        price = parsedPrice,
                        emoji = selectedEmoji
                    )
                    onSave(newItem)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save to Pantry", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PantryItemCard(
    item: PantryItem,
    backdrop: Backdrop? = null,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    onQuantityChange: (Double) -> Unit,
    onEdit: () -> Unit
) {
    val currentBackdrop = backdrop ?: rememberLayerBackdrop()
    val isLowStock = item.quantity <= item.lowStockThreshold
    val formattedQty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)
    val textMain = if (isDarkMode) Color.White else Color.Black

    val activeAccentColor = if (themePreference.equals("PURPLE", ignoreCase = true)) {
        Color(0xFFC084FC)
    } else {
        Color(0xFF007AFF)
    }

    val subtitleText = buildString {
        append(item.category.uppercase())
        if (isLowStock) {
            append(" • LOW STOCK")
        }
        if (item.price > 0) {
            append(" • $${String.format(Locale.US, "%.2f", item.price)} / ${item.unit.take(5)}")
        }
    }

    val containerColor = if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.45f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = currentBackdrop,
                shape = { Capsule() },
                effects = {
                    colorControls(
                        brightness = if (isDarkMode) 0f else 0.15f,
                        saturation = 1.3f
                    )
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 20.dp.toPx(),
                        refractionAmount = 36.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                highlight = {
                    Highlight(
                        width = 1.2.dp,
                        alpha = 0.45f
                    )
                },
                shadow = {
                    Shadow(
                        radius = 16.dp,
                        alpha = 0.25f,
                        offset = DpOffset(0.dp, 6.dp)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp,
                        alpha = 0.20f
                    )
                },
                onDrawSurface = { drawRect(containerColor) }
            )
            .clickable(onClick = onEdit)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                color = Color.White,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.60f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f
                )
            )
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
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .drawBackdrop(
                                backdrop = currentBackdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx())
                                    lens(
                                        refractionHeight = 8.dp.toPx(),
                                        refractionAmount = 12.dp.toPx(),
                                        chromaticAberration = true
                                    )
                                },
                                highlight = { Highlight(width = 1.dp, alpha = 0.5f) },
                                shadow = { Shadow(radius = 8.dp, alpha = 0.2f) },
                                innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.25f) },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = 0.16f))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.resolveEmoji(),
                            fontSize = 24.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = textMain,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitleText,
                            color = if (isLowStock) Color(0xFFFF3B30) else Color.White.copy(alpha = 0.70f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(Capsule())
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.50f),
                                    Color.White.copy(alpha = 0.12f)
                                )
                            ),
                            shape = Capsule()
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { if (item.quantity > 0) onQuantityChange((item.quantity - 0.25).coerceAtLeast(0.0)) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Quantity",
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = formattedQty,
                                color = textMain,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = item.unit.take(5),
                                color = Color.White.copy(alpha = 0.70f),
                                fontSize = 9.sp
                            )
                        }

                        IconButton(
                            onClick = { onQuantityChange(item.quantity + 1.0) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Quantity",
                                tint = Color.White.copy(alpha = 0.90f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditItemDialog(
    item: PantryItem,
    themePreference: String = "DEFAULT",
    backdrop: Backdrop? = null,
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var category by remember { mutableStateOf(item.category) }
    var unit by remember { mutableStateOf(item.unit) }
    var quantityText by remember { mutableStateOf(item.quantity.toString()) }
    var priceText by remember { mutableStateOf(if (item.price > 0) item.price.toString() else "") }
    var lowStockThresholdText by remember { mutableStateOf(item.lowStockThreshold.toString()) }
    var selectedEmoji by remember { mutableStateOf(item.emoji ?: item.resolveEmoji()) }

    val presetEmojis = listOf("🐶", "🐱", "🐾", "🧻", "🧼", "🧹", "🥫", "🍞", "🥛", "☕", "📦")

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        backdrop = backdrop,
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
                Text(
                    text = "Edit Item",
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
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Unit Price ($ e.g. 6.98)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it },
                label = { Text("Current Quantity (e.g. 0.25 for 1/4 Bag)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unit (e.g. Bag, Cans, Boxes)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = inputColors
            )

            OutlinedTextField(
                value = lowStockThresholdText,
                onValueChange = { lowStockThresholdText = it },
                label = { Text("Low Stock Threshold (e.g. 0.5 or 1.0)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

            // Emoji Selector Preview Badge & Preset Row
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
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(presetEmojis) { em ->
                            val isSelected = selectedEmoji == em
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.10f))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.20f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedEmoji = em },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = em, fontSize = 17.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Delete", color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.70f), fontSize = 14.sp)
                }

                GlassHeroButton(
                    onClick = {
                        val parsedQty = quantityText.toDoubleOrNull() ?: item.quantity
                        val threshold = lowStockThresholdText.toDoubleOrNull() ?: item.lowStockThreshold
                        val parsedPrice = priceText.toDoubleOrNull() ?: item.price
                        val updated = item.copy(
                            name = name.trim(),
                            category = category.trim(),
                            unit = unit.trim(),
                            quantity = parsedQty,
                            price = parsedPrice,
                            lowStockThreshold = threshold,
                            emoji = selectedEmoji
                        )
                        onSave(updated)
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
