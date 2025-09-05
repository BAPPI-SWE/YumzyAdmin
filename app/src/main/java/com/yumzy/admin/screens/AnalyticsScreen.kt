package com.yumzy.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- DATA BLUEPRINTS ---
data class RiderDailyStats(
    val riderId: String,
    val riderName: String,
    val totalAssigned: Int,
    val acceptedCount: Int,
    val deliveredCount: Int
)

data class RestaurantDailyStats(
    val restaurantId: String,
    val restaurantName: String,
    val totalOrders: Int
)

data class AnalyticsOrder(
    val riderId: String? = null,
    val restaurantId: String? = null,
    val orderStatus: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var riderStats by remember { mutableStateOf<List<RiderDailyStats>>(emptyList()) }
    var restaurantStats by remember { mutableStateOf<List<RestaurantDailyStats>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 for Riders, 1 for Restaurants

    LaunchedEffect(selectedDate) {
        isLoading = true
        val db = Firebase.firestore

        // Step 1: Get all riders AND all restaurants
        val ridersMap = try {
            db.collection("riders").get().await().documents.associate { doc ->
                doc.id to (doc.getString("name") ?: "Unknown Rider")
            }
        } catch (e: Exception) { emptyMap() }

        val restaurantsMap = try {
            db.collection("restaurants").get().await().documents.associate { doc ->
                doc.id to (doc.getString("name") ?: "Unknown Restaurant")
            }
        } catch (e: Exception) { emptyMap() }

        // Step 2: Calculate date range
        val zoneId = ZoneId.systemDefault()
        val startOfDay = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startTimestamp = Timestamp(startOfDay / 1000, 0)
        val endTimestamp = Timestamp(endOfDay / 1000, 0)

        // Step 3: Fetch orders for that day
        val ordersForDay = try {
            db.collection("orders")
                .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
                .whereLessThan("createdAt", endTimestamp)
                .get().await().documents.mapNotNull { it.toObject(AnalyticsOrder::class.java) }
        } catch (e: Exception) { emptyList() }

        // Step 4: Process Rider Stats
        val riderOrders = ordersForDay.filter { it.riderId != null }.groupBy { it.riderId!! }
        riderStats = ridersMap.map { (riderId, riderName) ->
            val orders = riderOrders[riderId] ?: emptyList()
            RiderDailyStats(
                riderId = riderId,
                riderName = riderName,
                totalAssigned = orders.size,
                acceptedCount = orders.count { it.orderStatus !in listOf("Pending", "Rejected", "Cancelled") },
                deliveredCount = orders.count { it.orderStatus == "Delivered" }
            )
        }.sortedByDescending { it.totalAssigned }

        // Step 5: Process Restaurant Stats
        val restaurantOrders = ordersForDay.filter { it.restaurantId != null }.groupBy { it.restaurantId!! }
        restaurantStats = restaurantsMap.map { (restaurantId, restaurantName) ->
            RestaurantDailyStats(
                restaurantId = restaurantId,
                restaurantName = restaurantName,
                totalOrders = restaurantOrders[restaurantId]?.size ?: 0
            )
        }.sortedByDescending { it.totalOrders }

        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Analytics") },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            val formatter = DateTimeFormatter.ofPattern("dd MMMM, yyyy")
            Text(
                "Showing stats for: ${selectedDate.format(formatter)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )

            // Tab Row for switching between Riders and Restaurants
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Rider Stats") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Restaurant Stats") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                // Show content based on the selected tab
                when (selectedTabIndex) {
                    0 -> RiderStatsContent(riderStats)
                    1 -> RestaurantStatsContent(restaurantStats)
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) { DatePicker(state = datePickerState) }
        }
    }
}

@Composable
fun RiderStatsContent(stats: List<RiderDailyStats>) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No rider data for this day.") }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stats) { riderStat ->
                RiderStatCard(stat = riderStat)
            }
        }
    }
}

@Composable
fun RestaurantStatsContent(stats: List<RestaurantDailyStats>) {
    if (stats.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No restaurant data for this day.") }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stats) { restaurantStat ->
                RestaurantStatCard(stat = restaurantStat)
            }
        }
    }
}

@Composable
fun RiderStatCard(stat: RiderDailyStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stat.riderName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Divider()
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem(label = "Assigned", value = stat.totalAssigned.toString())
                StatItem(label = "Accepted", value = stat.acceptedCount.toString())
                StatItem(label = "Delivered", value = stat.deliveredCount.toString())
            }
        }
    }
}

@Composable
fun RestaurantStatCard(stat: RestaurantDailyStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stat.restaurantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatItem(label = "Total Orders", value = stat.totalOrders.toString())
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}