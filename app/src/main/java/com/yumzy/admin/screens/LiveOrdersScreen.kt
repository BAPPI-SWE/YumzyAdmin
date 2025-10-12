package com.yumzy.admin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

// Enhanced order data class with all details
data class AdminOrder(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val userSubLocation: String = "",
    val riderName: String? = null,
    val orderStatus: String = "",
    val totalPrice: Double = 0.0,
    val createdAt: Timestamp = Timestamp.now(),
    val items: List<OrderItemDetail> = emptyList()
)

data class OrderItemDetail(
    val name: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveOrdersScreen() {
    var allOrders by remember { mutableStateOf<List<AdminOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Filter states
    var selectedStatuses by remember { mutableStateOf(setOf<String>()) }
    var selectedSubLocations by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDateFilter by remember { mutableStateOf(false) }

    // Date range filter
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }

    // Available filter options
    val allStatuses = listOf("Pending", "Accepted", "Preparing", "On the way", "Delivered", "Rejected", "Cancelled")
    val allSubLocations = remember { mutableStateOf<List<String>>(emptyList()) }

    // Real-time listener
    LaunchedEffect(key1 = Unit) {
        Firebase.firestore.collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(200) // Increased limit for better filtering
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoading = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val orders = snapshot.documents.mapNotNull { doc ->
                        try {
                            val itemsData = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                            val orderItems = itemsData.map { itemMap ->
                                OrderItemDetail(
                                    name = itemMap["itemName"] as? String
                                        ?: itemMap["name"] as? String
                                        ?: "Unknown Item",
                                    quantity = (itemMap["quantity"] as? Number)?.toInt() ?: 0,
                                    price = (itemMap["itemPrice"] as? Number)?.toDouble()
                                        ?: (itemMap["price"] as? Number)?.toDouble()
                                        ?: 0.0
                                )
                            }

                            AdminOrder(
                                id = doc.id,
                                restaurantName = doc.getString("restaurantName") ?: "",
                                userName = doc.getString("userName") ?: "",
                                userSubLocation = doc.getString("userSubLocation") ?: "N/A",
                                riderName = doc.getString("riderName"),
                                orderStatus = doc.getString("orderStatus") ?: "",
                                totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                                createdAt = doc.getTimestamp("createdAt") ?: Timestamp.now(),
                                items = orderItems
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    allOrders = orders

                    // Extract unique sub-locations
                    allSubLocations.value = orders.map { it.userSubLocation }
                        .filter { it.isNotBlank() && it != "N/A" }
                        .distinct()
                        .sorted()
                }
                isLoading = false
            }
    }

    // Apply filters
    val filteredOrders = remember(allOrders, selectedStatuses, selectedSubLocations, searchQuery, startDate, endDate) {
        allOrders.filter { order ->
            // Status filter
            val statusMatch = selectedStatuses.isEmpty() || selectedStatuses.contains(order.orderStatus)

            // Sub-location filter
            val locationMatch = selectedSubLocations.isEmpty() || selectedSubLocations.contains(order.userSubLocation)

            // Search filter (search in item names)
            val searchMatch = if (searchQuery.isBlank()) {
                true
            } else {
                order.items.any { item ->
                    item.name.contains(searchQuery, ignoreCase = true)
                } || order.restaurantName.contains(searchQuery, ignoreCase = true)
                        || order.userName.contains(searchQuery, ignoreCase = true)
            }

            // Date range filter
            val dateMatch = if (startDate == null && endDate == null) {
                true
            } else {
                val orderDate = Instant.ofEpochMilli(order.createdAt.toDate().time)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                val afterStart = startDate?.let { orderDate.isAfter(it.minusDays(1)) } ?: true
                val beforeEnd = endDate?.let { orderDate.isBefore(it.plusDays(1)) } ?: true

                afterStart && beforeEnd
            }

            statusMatch && locationMatch && searchMatch && dateMatch
        }
    }

    val activeFilterCount = selectedStatuses.size + selectedSubLocations.size +
            (if (startDate != null || endDate != null) 1 else 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Live Order Feed")
                        Text(
                            "${filteredOrders.size} orders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    // Date filter button
                    IconButton(onClick = { showDateFilter = true }) {
                        Badge(
                            containerColor = if (startDate != null || endDate != null)
                                MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = "Date Filter")
                        }
                    }

                    // Filter button with badge
                    IconButton(onClick = { showFilterDialog = true }) {
                        if (activeFilterCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text("$activeFilterCount")
                            }
                        }
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
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
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by food name, restaurant, or customer...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Active status filters chips
            if (selectedStatuses.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedStatuses.toList()) { status ->
                        FilterChip(
                            selected = true,
                            onClick = { selectedStatuses = selectedStatuses - status },
                            label = { Text(status) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // Active location filters chips
            if (selectedSubLocations.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedSubLocations.toList()) { location ->
                        FilterChip(
                            selected = true,
                            onClick = { selectedSubLocations = selectedSubLocations - location },
                            label = { Text(location) },
                            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            // Date range display
            if (startDate != null || endDate != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Date: ${startDate ?: "Start"} to ${endDate ?: "End"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = {
                                startDate = null
                                endDate = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear dates",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Clear all filters button
            if (activeFilterCount > 0 || searchQuery.isNotEmpty()) {
                TextButton(
                    onClick = {
                        selectedStatuses = setOf()
                        selectedSubLocations = setOf()
                        searchQuery = ""
                        startDate = null
                        endDate = null
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear all", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear all filters")
                }
            }

            Divider()

            // Orders list
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredOrders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "No results",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No orders found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredOrders) { order ->
                        EnhancedOrderCard(order = order)
                    }
                }
            }
        }
    }

    // Filter dialog
    if (showFilterDialog) {
        FilterDialog(
            allStatuses = allStatuses,
            selectedStatuses = selectedStatuses,
            onStatusesChanged = { selectedStatuses = it },
            allSubLocations = allSubLocations.value,
            selectedSubLocations = selectedSubLocations,
            onSubLocationsChanged = { selectedSubLocations = it },
            onDismiss = { showFilterDialog = false }
        )
    }

    // Date filter dialog
    if (showDateFilter) {
        DateRangeFilterDialog(
            startDate = startDate,
            endDate = endDate,
            onStartDateSelected = { startDate = it },
            onEndDateSelected = { endDate = it },
            onDismiss = { showDateFilter = false }
        )
    }
}

@Composable
fun EnhancedOrderCard(order: AdminOrder) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val statusColor = when (order.orderStatus) {
        "Pending" -> Color(0xFF757575)
        "Accepted", "Preparing" -> Color(0xFF0D47A1)
        "On the way" -> Color(0xFFE65100)
        "Delivered" -> Color(0xFF1B5E20)
        "Rejected", "Cancelled" -> Color(0xFFD32F2F)
        else -> Color.Black
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header: Restaurant and Price
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        order.restaurantName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        sdf.format(order.createdAt.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Text(
                    "৳${order.totalPrice}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Order Items
            Text(
                "Items:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${item.quantity}x ${item.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "৳${item.price * item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            // Customer and Location Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoRow(label = "Customer:", value = order.userName)
                    InfoRow(label = "Location:", value = order.userSubLocation)
                    InfoRow(label = "Rider:", value = order.riderName ?: "Not Assigned")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Status Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = order.orderStatus,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    allStatuses: List<String>,
    selectedStatuses: Set<String>,
    onStatusesChanged: (Set<String>) -> Unit,
    allSubLocations: List<String>,
    selectedSubLocations: Set<String>,
    onSubLocationsChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Filters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status filters
                    item {
                        Text(
                            "Order Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    items(allStatuses) { status ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStatusesChanged(
                                        if (selectedStatuses.contains(status)) {
                                            selectedStatuses - status
                                        } else {
                                            selectedStatuses + status
                                        }
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedStatuses.contains(status),
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(status)
                        }
                    }

                    // Sub-location filters
                    if (allSubLocations.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Sub-Location",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        items(allSubLocations) { location ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSubLocationsChanged(
                                            if (selectedSubLocations.contains(location)) {
                                                selectedSubLocations - location
                                            } else {
                                                selectedSubLocations + location
                                            }
                                        )
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedSubLocations.contains(location),
                                    onCheckedChange = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(location)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        onStatusesChanged(setOf())
                        onSubLocationsChanged(setOf())
                    }) {
                        Text("Clear All")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterDialog(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onStartDateSelected: (LocalDate?) -> Unit,
    onEndDateSelected: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Date Range Filter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(startDate?.toString() ?: "Select Start Date")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(endDate?.toString() ?: "Select End Date")
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        onStartDateSelected(null)
                        onEndDateSelected(null)
                    }) {
                        Text("Clear")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(onClick = onDismiss) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onStartDateSelected(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        onEndDateSelected(
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        )
                    }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}