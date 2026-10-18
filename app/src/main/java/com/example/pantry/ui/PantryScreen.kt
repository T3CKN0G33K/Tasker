package com.example.pantry.ui

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class PantryItem(
    val id: String = "",
    val name: String = "",
    val category: String = "Household",
    val quantity: Double = 1.0,
    val unit: String = "units",
    val lowStockThreshold: Double = 1.0,
    val price: Double = 0.0,
    val targetQuantity: Double = 2.0,
    val emoji: String? = null
) {
    val minThreshold: Double get() = lowStockThreshold
    val unitPrice: Double get() = price
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

    val categoryList = remember(items) {
        (listOf("All", "Household", "Pet Care", "Cleaning", "Dry Goods", "Beverages") + items.map { it.category }).distinct()
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

    // Dynamic Header Math (Zero Hardcoding)
    val lowStockItems = remember(items) { items.filter { it.quantity <= it.lowStockThreshold } }
    val restockTotal = remember(lowStockItems) {
        lowStockItems.sumOf { (maxOf(it.targetQuantity, it.lowStockThreshold) - it.quantity) * it.price }
    }
    val isAllStocked = lowStockItems.isEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.pantry.ui.theme.LocalAppColors.current.background)
    ) {
        LazyColumn(
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
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                                    .border(1.2.dp, Color(0xFF334155), CircleShape)
                                    .clickable { isSearchExpanded = !isSearchExpanded },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFFF8FAFC),
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
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Search Pantry...", color = Color(0xFF64748B), fontSize = 15.sp)
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
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Hero Restock Card (pantry.svg)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .svgCardContainer(cornerRadius = 20.dp)
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
                                // Cart Icon Badge
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (!isAllStocked) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f))
                                        .border(1.dp, if (!isAllStocked) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = if (!isAllStocked) Color(0xFFF87171) else Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Low Stock Restock Estimate",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = String.format(Locale.US, "$%.2f", restockTotal),
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-0.5).sp
                                    )
                                }
                            }

                            // Alert Pill Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (!isAllStocked) Color(0xFF7F1D1D).copy(alpha = 0.7f) else Color(0xFF064E3B).copy(alpha = 0.7f))
                                    .border(1.dp, if (!isAllStocked) Color(0xFFF87171).copy(alpha = 0.4f) else Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(11.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (!isAllStocked) "${lowStockItems.size} Low" else "All Stocked",
                                    color = if (!isAllStocked) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
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
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id }) { item ->
                    PantryItemCard(
                        item = item,
                        onQuantityChange = { newQty ->
                            onItemUpdate(item.copy(quantity = newQty))
                        },
                        onEdit = { itemToEdit = item }
                    )
                }
            }
        }

        // Single Circular + FAB Pinned at bottom = 96.dp, end = 24.dp
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
                .clickable { showAddItemDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Item",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Add Item Dialog
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
fun PantryItemCard(
    item: PantryItem,
    onQuantityChange: (Double) -> Unit,
    onEdit: () -> Unit
) {
    val isLowStock = item.quantity <= item.lowStockThreshold
    val formattedQty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)

    val appColors = com.example.pantry.ui.theme.LocalAppColors.current
    val cardBg = if (isLowStock) Color(0xFF2D1518).copy(alpha = 0.85f) else appColors.cardSurface
    val cardBorder = if (isLowStock) Color(0xFFF87171).copy(alpha = 0.4f) else appColors.cardBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit)
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
                // Item Icon Badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isLowStock) Color(0xFF7F1D1D).copy(alpha = 0.5f) else appColors.primaryAccent.copy(alpha = 0.2f))
                        .border(1.dp, if (isLowStock) Color(0xFFF87171).copy(alpha = 0.3f) else appColors.cardBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.resolveEmoji(),
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.category.uppercase()} • $${String.format(Locale.US, "%.2f", item.price)} / 1 ${item.unit.singularize()}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isLowStock) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF7F1D1D).copy(alpha = 0.8f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LOW STOCK",
                                color = Color(0xFFFCA5A5),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Stepper Pill Control (Right Side) - Fixed 104.dp x 44.dp
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(appColors.cardSurface)
                    .border(1.2.dp, appColors.cardBorder, RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(32.dp)
                            .clickable {
                                val decrement = if (item.unit.lowercase().contains("bag") || item.unit.lowercase().contains("box")) 0.25 else 1.0
                                onQuantityChange(maxOf(0.0, item.quantity - decrement))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            color = Color(0xFF94A3B8),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formattedQty,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = item.unit.ifBlank { "units" },
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(32.dp)
                            .clickable {
                                val increment = if (item.unit.lowercase().contains("bag") || item.unit.lowercase().contains("box")) 0.25 else 1.0
                                onQuantityChange(item.quantity + increment)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            color = Color(0xFF94A3B8),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
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
    val categories = listOf("Dry Goods", "Beverages", "Pet Care", "Household", "Cleaning")

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
                                    .clickable { selectedEmoji = emo },
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
                    val parsedQty = quantityText.toDoubleOrNull() ?: 1.0
                    val threshold = lowStockThresholdText.toDoubleOrNull() ?: 1.0

                    val newItem = PantryItem(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        category = category,
                        quantity = parsedQty,
                        unit = unit.ifBlank { "units" },
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
    var selectedEmoji by remember { mutableStateOf(item.emoji ?: item.resolveEmoji()) }

    LiquidGlassDialog(
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
                Text(
                    text = "Edit Pantry Item",
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
                label = { Text("Unit Price ($ e.g. 6.98)", color = Color.White.copy(alpha = 0.70f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = inputColors
            )

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

                        val updatedItem = item.copy(
                            name = name.trim(),
                            category = category,
                            quantity = parsedQty,
                            unit = unit,
                            lowStockThreshold = threshold,
                            price = parsedPrice,
                            emoji = selectedEmoji
                        )
                        onSave(updatedItem)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    Text("Save Changes", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
