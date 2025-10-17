package com.yumzy.admin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// Data classes for order analytics
data class ItemOrderStats(
    val itemName: String,
    val pendingCount: Int,
    val acceptedCount: Int,
    val deliveredCount: Int
)

data class MiniRestaurantOrder(
    val items: List<Map<String, Any>> = emptyList(),
    val orderStatus: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantAnalyticsScreen(
    miniResId: String,
    miniResName: String,
    navController: NavController
) {
    var itemStats by remember { mutableStateOf<List<ItemOrderStats>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var startTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var endTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun fetchOrderStats() {
        isLoading = true
        coroutineScope.launch {
            try {
                val db = Firebase.firestore
                var query = db.collection("orders")
                    .whereEqualTo("restaurantId", "yumzy_store")

                // Apply date filter
                if (selectedDate != null) {
                    val zoneId = ZoneId.systemDefault()
                    val startOfDay = selectedDate!!.atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val endOfDay = selectedDate!!.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                    val startTimestamp = Timestamp(startOfDay / 1000, 0)
                    val endTimestamp = Timestamp(endOfDay / 1000, 0)

                    query = query
                        .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
                        .whereLessThan("createdAt", endTimestamp)
                }

                val snapshot = query.get().await()
                val orders = snapshot.documents.mapNotNull { doc ->
                    try {
                        MiniRestaurantOrder(
                            items = doc.get("items") as? List<Map<String, Any>> ?: emptyList(),
                            orderStatus = doc.getString("orderStatus") ?: "",
                            createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Filter orders by time and status
                val filteredOrders = orders.filter { order ->
                    // Time filter
                    val timeMatch = if (startTime != null || endTime != null) {
                        val orderDateTime = order.createdAt.toDate()
                        val calendar = Calendar.getInstance().apply { time = orderDateTime }
                        val orderHour = calendar.get(Calendar.HOUR_OF_DAY)
                        val orderMinute = calendar.get(Calendar.MINUTE)
                        val orderTimeInMinutes = orderHour * 60 + orderMinute

                        val startTimeInMinutes = startTime?.let { it.first * 60 + it.second } ?: 0
                        val endTimeInMinutes = endTime?.let { it.first * 60 + it.second } ?: (23 * 60 + 59)

                        orderTimeInMinutes >= startTimeInMinutes && orderTimeInMinutes <= endTimeInMinutes
                    } else true

                    // Status filter
                    val statusMatch = selectedStatus == null || order.orderStatus == selectedStatus

                    timeMatch && statusMatch
                }

                // Count items by status
                val itemCountMap = mutableMapOf<String, MutableMap<String, Int>>()

                filteredOrders.forEach { order ->
                    order.items.forEach { itemMap ->
                        val itemName = itemMap["itemName"] as? String
                            ?: itemMap["name"] as? String
                            ?: "Unknown Item"

                        // --- FIX: Check the miniResName directly on the item ---
                        // This is far more reliable than matching by name.
                        val itemMiniResName = itemMap["miniResName"] as? String ?: ""
                        if (itemMiniResName == miniResName) {
                            val quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0

                            if (!itemCountMap.containsKey(itemName)) {
                                itemCountMap[itemName] = mutableMapOf(
                                    "Pending" to 0,
                                    "Accepted" to 0,
                                    "Preparing" to 0,
                                    "On the way" to 0,
                                    "Delivered" to 0
                                )
                            }

                            val status = order.orderStatus
                            when (status) {
                                "Pending" -> itemCountMap[itemName]!!["Pending"] =
                                    itemCountMap[itemName]!!["Pending"]!! + quantity
                                "Delivered" -> itemCountMap[itemName]!!["Delivered"] =
                                    itemCountMap[itemName]!!["Delivered"]!! + quantity
                                "Accepted", "Preparing", "On the way" ->
                                    itemCountMap[itemName]!!["Accepted"] =
                                        itemCountMap[itemName]!!["Accepted"]!! + quantity
                            }
                        }
                    }
                }

                itemStats = itemCountMap.map { (itemName, counts) ->
                    ItemOrderStats(
                        itemName = itemName,
                        pendingCount = counts["Pending"] ?: 0,
                        acceptedCount = (counts["Accepted"] ?: 0) +
                                (counts["Preparing"] ?: 0) +
                                (counts["On the way"] ?: 0),
                        deliveredCount = counts["Delivered"] ?: 0
                    )
                }.sortedByDescending { it.pendingCount + it.acceptedCount + it.deliveredCount }

            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedDate, startTime, endTime, selectedStatus) {
        fetchOrderStats()
    }

    val totalPending = itemStats.sumOf { it.pendingCount }
    val totalAccepted = itemStats.sumOf { it.acceptedCount }
    val totalDelivered = itemStats.sumOf { it.deliveredCount }
    val totalItemsSold = totalPending + totalAccepted + totalDelivered

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(miniResName)
                        Text(
                            "Order Analytics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filters Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Date Filter Button
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        selectedDate?.format(DateTimeFormatter.ofPattern("dd MMM, yyyy"))
                            ?: "Select Date (Optional)"
                    )
                }

                // Time Range
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            startTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" }
                                ?: "Start Time",
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { showEndTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            endTime?.let { "${it.first}:${it.second.toString().padStart(2, '0')}" }
                                ?: "End Time",
                            fontSize = 12.sp
                        )
                    }
                }

                // Status Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { selectedStatus = null },
                        label = { Text("All") }
                    )
                    FilterChip(
                        selected = selectedStatus == "Pending",
                        onClick = { selectedStatus = if (selectedStatus == "Pending") null else "Pending" },
                        label = { Text("Pending") }
                    )
                    FilterChip(
                        selected = selectedStatus == "Accepted",
                        onClick = { selectedStatus = if (selectedStatus == "Accepted") null else "Accepted" },
                        label = { Text("Accepted") }
                    )
                    FilterChip(
                        selected = selectedStatus == "Delivered",
                        onClick = { selectedStatus = if (selectedStatus == "Delivered") null else "Delivered" },
                        label = { Text("Delivered") }
                    )
                }

                // Clear Filters
                if (selectedDate != null || startTime != null || endTime != null || selectedStatus != null) {
                    TextButton(
                        onClick = {
                            selectedDate = null
                            startTime = null
                            endTime = null
                            selectedStatus = null
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Clear All Filters")
                    }
                }
            }

            HorizontalDivider()

            // Summary Stats
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AnalyticsStatBadge("Total Items", totalItemsSold, Color.Gray)
                    AnalyticsStatBadge("Pending", totalPending, Color(0xFFFF9800))
                    AnalyticsStatBadge("Accepted", totalAccepted, Color(0xFF2196F3))
                    AnalyticsStatBadge("Delivered", totalDelivered, Color(0xFF4CAF50))
                }
            }

            HorizontalDivider()

            // Items List
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (itemStats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inventory,
                            contentDescription = "No data",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No matching orders found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Item Breakdown (${itemStats.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    items(itemStats) { stat ->
                        AnalyticsItemStatsCard(stat)
                    }
                }
            }
        }
    }

    // Date Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Start Time Picker
    if (showStartTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = startTime?.first ?: 0,
            initialMinute = startTime?.second ?: 0,
            is24Hour = true
        )
        AnalyticsTimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startTime = Pair(timePickerState.hour, timePickerState.minute)
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    // End Time Picker
    if (showEndTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = endTime?.first ?: 23,
            initialMinute = endTime?.second ?: 59,
            is24Hour = true
        )
        AnalyticsTimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endTime = Pair(timePickerState.hour, timePickerState.minute)
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") }
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
fun AnalyticsStatBadge(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun AnalyticsItemStatsCard(stat: ItemOrderStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stat.itemName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsOrderCountBadge(
                    label = "Pending",
                    count = stat.pendingCount,
                    color = Color(0xFFFF9800)
                )
                AnalyticsOrderCountBadge(
                    label = "Accepted",
                    count = stat.acceptedCount,
                    color = Color(0xFF2196F3)
                )
                AnalyticsOrderCountBadge(
                    label = "Delivered",
                    count = stat.deliveredCount,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun AnalyticsOrderCountBadge(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
fun AnalyticsTimePickerDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}