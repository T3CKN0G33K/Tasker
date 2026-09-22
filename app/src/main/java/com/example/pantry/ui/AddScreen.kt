package com.example.pantry.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

@Composable
fun GlassStepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPlus: Boolean,
    modifier: Modifier = Modifier
) {
    val buttonBg = if (isPlus) {
        Color(0xFF007AFF).copy(alpha = 0.40f) // Translucent blue tint for (+)
    } else {
        Color.White.copy(alpha = 0.15f) // Translucent white/grey tint for (-)
    }

    val buttonBorder = Brush.verticalGradient(
        colors = if (isPlus) {
            listOf(
                Color.White.copy(alpha = 0.70f),
                Color(0xFF007AFF).copy(alpha = 0.35f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.50f),
                Color.White.copy(alpha = 0.15f)
            )
        }
    )

    // Pure Compose Glass Button: 100% surface clickability with zero AndroidView touch interception!
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(buttonBg)
            .border(
                width = 1.dp,
                brush = buttonBorder,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 24.dp)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun GlassHeroButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDarkMode: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val buttonBg = Color(0xFF007AFF).copy(alpha = 0.35f)
    val buttonBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.65f),
            Color(0xFF007AFF).copy(alpha = 0.30f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(buttonBg)
            .border(
                width = 1.5.dp,
                brush = buttonBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun AddScreen(
    isDarkMode: Boolean = true,
    onAddItem: (PantryItem) -> Unit = {}
) {
    var itemName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Dry Goods") }
    var priceText by remember { mutableStateOf("") }
    var quantity by remember { mutableIntStateOf(1) }
    var lowStockThreshold by remember { mutableIntStateOf(1) }
    var isSaved by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val categories = listOf("Dry Goods", "Beverages", "Pet Care", "Household")

    val textMain = if (isDarkMode) Color.White else Color.Black

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
                        Toast.makeText(context, "Scanned: $rawValue. Looking up product...", Toast.LENGTH_SHORT).show()
                        
                        lookupBarcodeProduct(
                            barcode = rawValue,
                            onSuccess = { name, category ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    isScanning = false
                                    itemName = name
                                    selectedCategory = category
                                    if (name.isNotBlank()) {
                                        Toast.makeText(context, "Product found: $name", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Barcode scanned. Enter item name.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onError = { _ ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    isScanning = false
                                    Toast.makeText(context, "Scanned barcode: $rawValue", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
                .addOnCanceledListener {
                    isScanning = false
                }
                .addOnFailureListener { e ->
                    isScanning = false
                    Toast.makeText(context, "Scanner error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            isScanning = false
            Toast.makeText(context, "Scanner initialization failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Unblock Background -> Color.Transparent for edge-to-edge ambient gradient!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 140.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Add Item",
            color = textMain,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        // 1. Walmart Barcode Scanner Hero Glass Button
        GlassHeroButton(
            onClick = { startBarcodeScan() },
            enabled = !isScanning,
            isDarkMode = isDarkMode
        ) {
            if (isScanning) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Looking up scanned product...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Scan Walmart / Grocery Barcode", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 2. Item Name Input (Frosted Glass Input Shell)
        OutlinedTextField(
            value = itemName,
            onValueChange = { 
                itemName = it
                isSaved = false
            },
            placeholder = { Text("Item Name", color = Color.White.copy(alpha = 0.60f)) },
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isDarkMode) listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.10f)) else listOf(Color.White, Color.White.copy(alpha = 0.50f))
                    ),
                    shape = RoundedCornerShape(14.dp)
                ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f),
                unfocusedContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f),
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF007AFF)
            )
        )

        // 3. Item Unit Price Input (Frosted Glass Input Shell)
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            placeholder = { Text("Unit Price ($ e.g. 6.98)", color = Color.White.copy(alpha = 0.60f)) },
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = if (isDarkMode) listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.10f)) else listOf(Color.White, Color.White.copy(alpha = 0.50f))
                    ),
                    shape = RoundedCornerShape(14.dp)
                ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f),
                unfocusedContainerColor = if (isDarkMode) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.70f),
                focusedBorderColor = Color(0xFF007AFF),
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFF007AFF)
            )
        )

        // 4. Category Selector (Upgraded to GlassCard)
        GlassCard(isDarkMode = isDarkMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Category", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        val chipBg = if (isSelected) Color(0xFF007AFF) else if (isDarkMode) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.60f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(chipBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF007AFF) else Color.White.copy(alpha = 0.20f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCategory = category },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 5. Quantity Control Stepper (Upgraded with 100% full-surface responsive Glass Stepper Buttons!)
        GlassCard(isDarkMode = isDarkMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quantity", 
                    color = Color.White, 
                    fontSize = 18.sp, 
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassStepperButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Decrease Quantity",
                        onClick = { if (quantity > 1) quantity-- },
                        isPlus = false
                    )

                    Text(
                        text = quantity.toString(), 
                        color = Color.White, 
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 32.dp)
                    )

                    GlassStepperButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Increase Quantity",
                        onClick = { quantity++ },
                        isPlus = true
                    )
                }
            }
        }

        // 6. Low Stock Threshold Stepper (Upgraded with 100% full-surface responsive Glass Stepper Buttons!)
        GlassCard(isDarkMode = isDarkMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Low Stock Threshold", 
                        color = Color.White, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Triggers restock alert when quantity ≤ this number", 
                        color = Color.White.copy(alpha = 0.65f), 
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassStepperButton(
                        icon = Icons.Default.Remove,
                        contentDescription = "Decrease Threshold",
                        onClick = { if (lowStockThreshold > 0) lowStockThreshold-- },
                        isPlus = false
                    )

                    Text(
                        text = lowStockThreshold.toString(), 
                        color = Color.White, 
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 32.dp)
                    )

                    GlassStepperButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Increase Threshold",
                        onClick = { lowStockThreshold++ },
                        isPlus = true
                    )
                }
            }
        }

        if (isSaved) {
            Text(
                text = "Item added to inventory!",
                color = Color(0xFF34C759),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 7. Save to Pantry Hero Glass Button
        GlassHeroButton(
            onClick = {
                if (itemName.isNotBlank()) {
                    val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                    val newItem = PantryItem(
                        id = UUID.randomUUID().toString(),
                        name = itemName.trim(),
                        category = selectedCategory,
                        quantity = quantity.toDouble(),
                        unit = "Units",
                        lowStockThreshold = lowStockThreshold.toDouble(),
                        price = parsedPrice
                    )
                    onAddItem(newItem)
                    itemName = ""
                    priceText = ""
                    quantity = 1
                    lowStockThreshold = 1
                    isSaved = true
                }
            },
            isDarkMode = isDarkMode
        ) {
            Text("Save to Pantry", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun lookupBarcodeProduct(
    barcode: String,
    onSuccess: (name: String, category: String) -> Unit,
    onError: (String) -> Unit
) {
    val cleanUpc = barcode.filter { it.isDigit() }
    val client = OkHttpClient()

    // Stage 1: Query UPC Item DB API (Designed for US Walmart/Groceries)
    val upcDbUrl = "https://api.upcitemdb.com/prod/trial/lookup?upc=$cleanUpc"
    val request1 = Request.Builder()
        .url(upcDbUrl)
        .header("User-Agent", "TaskerPantryManager/1.0")
        .build()

    client.newCall(request1).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            tryOpenFoodFacts(client, cleanUpc, onSuccess, onError)
        }

        override fun onResponse(call: Call, response: Response) {
            val bodyStr = response.body?.string() ?: ""
            try {
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val firstItem = items.getJSONObject(0)
                    val title = firstItem.optString("title")
                    val categoriesStr = firstItem.optString("category").lowercase()
                    if (title.isNotBlank()) {
                        val inferredCategory = when {
                            categoriesStr.contains("beverage") || categoriesStr.contains("drink") || categoriesStr.contains("juice") || categoriesStr.contains("milk") || categoriesStr.contains("soda") || categoriesStr.contains("water") -> "Beverages"
                            categoriesStr.contains("pet") || categoriesStr.contains("dog") || categoriesStr.contains("cat") -> "Pet Care"
                            categoriesStr.contains("household") || categoriesStr.contains("cleaning") || categoriesStr.contains("paper") -> "Household"
                            else -> "Dry Goods"
                        }
                        onSuccess(title, inferredCategory)
                        return
                    }
                }
            } catch (_: Exception) {}
            tryOpenFoodFacts(client, cleanUpc, onSuccess, onError)
        }
    })
}

private fun tryOpenFoodFacts(
    client: OkHttpClient,
    upc: String,
    onSuccess: (name: String, category: String) -> Unit,
    onError: (String) -> Unit
) {
    val offUrl = "https://world.openfoodfacts.org/api/v2/product/$upc.json"
    val request = Request.Builder()
        .url(offUrl)
        .header("User-Agent", "TaskerPantryManager/1.0")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onSuccess("", "Dry Goods")
        }

        override fun onResponse(call: Call, response: Response) {
            val bodyStr = response.body?.string() ?: ""
            try {
                val json = JSONObject(bodyStr)
                val status = json.optInt("status", 0)
                if (status == 1) {
                    val product = json.optJSONObject("product")
                    val name = product?.optString("product_name")?.ifBlank { null }
                        ?: product?.optString("product_name_en")?.ifBlank { null }
                        ?: product?.optString("brands")?.ifBlank { null }

                    val categoriesStr = product?.optString("categories")?.lowercase() ?: ""
                    val inferredCategory = when {
                        categoriesStr.contains("beverage") || categoriesStr.contains("drink") || categoriesStr.contains("juice") || categoriesStr.contains("milk") || categoriesStr.contains("soda") || categoriesStr.contains("water") -> "Beverages"
                        categoriesStr.contains("pet") || categoriesStr.contains("dog") || categoriesStr.contains("cat") -> "Pet Care"
                        categoriesStr.contains("household") || categoriesStr.contains("cleaning") || categoriesStr.contains("paper") -> "Household"
                        else -> "Dry Goods"
                    }

                    if (!name.isNullOrBlank()) {
                        onSuccess(name, inferredCategory)
                        return
                    }
                }
            } catch (_: Exception) {}

            onSuccess("", "Dry Goods")
        }
    })
}
