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

// Blueprint for a Rider's daily stats
data class RiderDailyStats(
    val riderId: String,
    val riderName: String,
    val totalAssigned: Int,
    val acceptedCount: Int,
    val deliveredCount: Int
)

// Blueprint for a single Order document, only the fields we need
data class AnalyticsOrder(
    val riderId: String? = null,
    val orderStatus: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen() {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<List<RiderDailyStats>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // This block runs every time the 'selectedDate' changes.
    // It fetches new data from Firestore and calculates the stats.
    LaunchedEffect(selectedDate) {
        isLoading = true
        val db = Firebase.firestore

        // Step 1: Get all riders to know their names
        val ridersMap = try {
            db.collection("riders").get().await().documents.associate { doc ->
                doc.id to (doc.getString("name") ?: "Unknown Rider")
            }
        } catch (e: Exception) {
            emptyMap()
        }

        // Step 2: Calculate start and end of the selected day
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
        } catch (e: Exception) {
            emptyList()
        }

        // Step 4: Process the data
        val riderOrders = ordersForDay.filter { it.riderId != null }.groupBy { it.riderId!! }

        val calculatedStats = ridersMap.map { (riderId, riderName) ->
            val orders = riderOrders[riderId] ?: emptyList()
            RiderDailyStats(
                riderId = riderId,
                riderName = riderName,
                totalAssigned = orders.size,
                // An order is "accepted" if its status is not pending, rejected, or cancelled
                acceptedCount = orders.count { it.orderStatus !in listOf("Pending", "Rejected", "Cancelled") },
                deliveredCount = orders.count { it.orderStatus == "Delivered" }
            )
        }
        stats = calculatedStats.sortedByDescending { it.totalAssigned }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daily Rider Analytics") },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).padding(16.dp)) {
            val formatter = DateTimeFormatter.ofPattern("dd MMMM, yyyy")
            Text("Showing stats for: ${selectedDate.format(formatter)}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (stats.isEmpty()){
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No rider data for this day.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stats) { riderStat ->
                        RiderStatCard(stat = riderStat)
                    }
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
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
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
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}