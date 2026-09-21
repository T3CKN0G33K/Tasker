package com.example.pantry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
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
    onItemsChange: (List<PantryItem>) -> Unit = {},
    onItemUpdate: (PantryItem) -> Unit = {},
    onItemDelete: (String) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var itemToEdit by remember { mutableStateOf<PantryItem?>(null) }

    val screenBg = if (isDarkMode) Color.Black else Color(0xFFF2F2F7)
    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val textMain = if (isDarkMode) Color.White else Color.Black
    val textSub = if (isDarkMode) Color.Gray else Color(0xFF6C6C70)

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

    // Low Stock Restock Budget Estimate Calculation
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
            .background(screenBg)
    ) {
        // LAYER 1: Scrolling Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 110.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "My Pantry",
                        color = textMain,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    // Low Stock Restock Budget Estimate Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg)
                    ) {
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
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30).copy(alpha = 0.2f) else Color(0xFF34C759).copy(alpha = 0.2f)),
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
                                    Text("Low Stock Restock Estimate", color = textSub, fontSize = 12.sp)
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", totalRestockEstimate)}",
                                        color = textMain,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (lowStockItems.isNotEmpty()) Color(0xFFFF3B30).copy(alpha = 0.2f) else if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
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

                    // Category Filter Chips Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categoryList.take(5).forEach { category ->
                            val isSelected = selectedCategoryFilter == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) Color(0xFF007AFF) else cardBg)
                                    .clickable { selectedCategoryFilter = category }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = if (isSelected) Color.White else textSub,
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
                            text = if (searchQuery.isNotBlank() || selectedCategoryFilter != "All") "No matching items found." else "Your Pantry is empty! Tap 'Add' to restock.",
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
                        onQuantityChange = { newQty ->
                            onItemUpdate(item.copy(quantity = newQty))
                        },
                        onEdit = { itemToEdit = item }
                    )
                }
            }
        }

        // LAYER 2: Frosted Sticky Glass Search Header
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            NativeLiquidGlass(
                modifier = Modifier
                    .matchParentSize()
                    .background(if (isDarkMode) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.65f)),
                blurRadius = 30.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                isDarkTheme = isDarkMode
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDarkMode) Color(0xFF767680).copy(alpha = 0.24f) else Color(0xFF767680).copy(alpha = 0.12f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Search, contentDescription = "Search", tint = textSub, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = textMain, fontSize = 16.sp),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("Search Pantry", color = textSub, fontSize = 16.sp)
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Edit Modal
    itemToEdit?.let { item ->
        EditItemDialog(
            item = item,
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
    isDarkMode: Boolean = true,
    onQuantityChange: (Double) -> Unit,
    onEdit: () -> Unit
) {
    val isLowStock = item.quantity <= item.lowStockThreshold
    val formattedQty = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.US, "%.2f", item.quantity)

    val cardBg = if (isDarkMode) Color(0xFF1C1C1E) else Color.White
    val textMain = if (isDarkMode) Color.White else Color.Black
    val pillBg = if (isDarkMode) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable { onEdit() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category Tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF007AFF).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.category.uppercase(),
                            color = Color(0xFF007AFF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Low Stock Restock Warning Badge
                    if (isLowStock) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFF3B30).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "LOW STOCK",
                                color = Color(0xFFFF3B30),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(text = item.name, color = textMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                
                // Unit Price Tag
                if (item.price > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$${String.format(Locale.US, "%.2f", item.price)} / ${item.unit.take(5)}",
                        color = Color(0xFF34C759),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Stepper Quantity Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { if (item.quantity > 0) onQuantityChange((item.quantity - 0.25).coerceAtLeast(0.0)) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(pillBg, CircleShape)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = textMain, modifier = Modifier.size(16.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formattedQty,
                        color = if (isLowStock) Color(0xFFFF3B30) else textMain,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = item.unit, color = Color.Gray, fontSize = 10.sp)
                }

                IconButton(
                    onClick = { onQuantityChange(item.quantity + 1.0) },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF007AFF), CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Quick Fractional Fill Level Selector Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val fillLevels = listOf(
                "1/4 (25%)" to 0.25,
                "1/2 (50%)" to 0.50,
                "3/4 (75%)" to 0.75,
                "Full (1.0)" to 1.00
            )
            fillLevels.forEach { (label, value) ->
                val isCurrent = abs(item.quantity - value) < 0.05
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) Color(0xFF007AFF) else pillBg)
                        .clickable { onQuantityChange(value) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isCurrent) Color.White else if (isDarkMode) Color.Gray else Color(0xFF6C6C70),
                        fontSize = 10.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun EditItemDialog(
    item: PantryItem,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1C1E),
        title = { Text("Edit Item", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("Unit Price ($ e.g. 6.98)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Current Quantity (e.g. 0.25 for 1/4 Bag)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g. Bag, Cans, Boxes)", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = lowStockThresholdText,
                    onValueChange = { lowStockThresholdText = it },
                    label = { Text("Low Stock Threshold (e.g. 0.5 or 1.0)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
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
