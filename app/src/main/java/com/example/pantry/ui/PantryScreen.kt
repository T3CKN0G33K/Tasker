package com.example.pantry.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.liquidglass.ChromaticDispersionPresets
import com.example.liquidglass.LiquidGlassListItem
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
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

data class PantryItem(
    val id: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val lowStockThreshold: Double = 1.0,
    val price: Double = 0.0
)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // LAYER 1: Scrolling Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 72.dp, bottom = 140.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Pantry",
                            color = textMain,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { isSearchExpanded = !isSearchExpanded },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(
                                        width = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = { showAddItemDialog = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(activeAccentColor)
                                    .border(
                                        width = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.40f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Item",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

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

                    GlassCard(isDarkMode = isDarkMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30).copy(alpha = 0.25f) else Color(0xFF34C759).copy(alpha = 0.25f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF34C759),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Low Stock Restock Estimate", color = textSub, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", totalRestockEstimate)}",
                                        color = textMain,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30).copy(alpha = 0.25f) else Color(0xFF34C759).copy(alpha = 0.20f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (lowStockItems.isNotEmpty()) "${lowStockItems.size} Low" else "All Stocked",
                                    color = if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30) else Color(0xFF34C759),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

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

    // Add Item Centered Glass Popup Dialog
    if (showAddItemDialog) {
        AddItemCenteredDialog(
            themePreference = themePreference,
            onDismiss = { showAddItemDialog = false },
            onSave = { newItem ->
                onAddItem(newItem)
                showAddItemDialog = false
            }
        )
    }
}

    // Edit Item Dialog
    itemToEdit?.let { item ->
        EditItemDialog(
            item = item,
            themePreference = themePreference,
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
    onDismiss: () -> Unit,
    onSave: (PantryItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Dry Goods") }
    var unit by remember { mutableStateOf("Units") }
    var quantityText by remember { mutableStateOf("1") }
    var priceText by remember { mutableStateOf("") }
    var lowStockThresholdText by remember { mutableStateOf("1") }
    var isScanning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val categories = listOf("Dry Goods", "Beverages", "Pet Care", "Household")

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                isDarkMode = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22).copy(alpha = 0.94f))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title Row with Close "X" Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Item to Pantry",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.70f),
                                modifier = Modifier.size(18.dp)
                            )
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

                    // Item Name Input with Trailing Barcode Scanner Action Button
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Item Name", color = Color.White.copy(alpha = 0.60f)) },
                        trailingIcon = {
                            IconButton(
                                onClick = { startBarcodeScan() },
                                enabled = !isScanning
                            ) {
                                if (isScanning) {
                                    CircularProgressIndicator(
                                        color = activeAccentColor,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan Barcode",
                                        tint = activeAccentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = inputColors
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        placeholder = { Text("Unit Price ($ e.g. 6.98)", color = Color.White.copy(alpha = 0.60f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = inputColors
                    )

                    Text("Category", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = category == cat
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) activeAccentColor else Color.Transparent)
                                        .clickable { category = cat },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            label = { Text("Quantity", color = Color.White.copy(alpha = 0.70f)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = inputColors
                        )

                        OutlinedTextField(
                            value = lowStockThresholdText,
                            onValueChange = { lowStockThresholdText = it },
                            label = { Text("Low Stock Alert", color = Color.White.copy(alpha = 0.70f)) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = inputColors
                        )
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
                                price = parsedPrice
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
    }
}

@Composable
fun PantryItemCard(
    item: PantryItem,
    isDarkMode: Boolean = true,
    themePreference: String = "DEFAULT",
    onQuantityChange: (Double) -> Unit,
    onEdit: () -> Unit
) {
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

    LiquidGlassListItem(
        title = item.name,
        subtitle = subtitleText,
        preset = ChromaticDispersionPresets.Standard,
        isDarkMode = isDarkMode,
        onClick = onEdit,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isLowStock) Color(0xFFFF3B30).copy(alpha = 0.25f) else activeAccentColor.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLowStock) Icons.Default.ShoppingCart else Icons.Default.Search,
                    contentDescription = null,
                    tint = if (isLowStock) Color(0xFFFF3B30) else activeAccentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.10f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
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
                            tint = Color.White.copy(alpha = 0.85f),
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
                            color = Color.White.copy(alpha = 0.60f),
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
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun EditItemDialog(
    item: PantryItem,
    themePreference: String = "DEFAULT",
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

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        themePreference = themePreference,
        title = { Text("Edit Item", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val inputColors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedBorderColor = Color(0xFF007AFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.20f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color.White.copy(alpha = 0.70f)) },
                    colors = inputColors
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category", color = Color.White.copy(alpha = 0.70f)) },
                    colors = inputColors
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Unit Price ($ e.g. 6.98)", color = Color.White.copy(alpha = 0.70f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Current Quantity (e.g. 0.25 for 1/4 Bag)", color = Color.White.copy(alpha = 0.70f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g. Bag, Cans, Boxes)", color = Color.White.copy(alpha = 0.70f)) },
                    colors = inputColors
                )
                OutlinedTextField(
                    value = lowStockThresholdText,
                    onValueChange = { lowStockThresholdText = it },
                    label = { Text("Low Stock Threshold (e.g. 0.5 or 1.0)", color = Color.White.copy(alpha = 0.70f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = inputColors
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    val parsedQty = quantityText.toDoubleOrNull() ?: item.quantity
                    val threshold = lowStockThresholdText.toDoubleOrNull() ?: item.lowStockThreshold
                    val parsedPrice = priceText.toDoubleOrNull() ?: item.price
                    onSave(item.copy(name = name, category = category, unit = unit, quantity = parsedQty, price = parsedPrice, lowStockThreshold = threshold)) 
                }
            ) {
                Text("Save", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) {
                    Text("Delete", color = Color(0xFFFF3B30))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    )
}
