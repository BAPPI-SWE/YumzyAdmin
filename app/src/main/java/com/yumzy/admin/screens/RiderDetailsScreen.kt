package com.yumzy.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Blueprint for the detailed order information we need for this screen
data class RiderOrderDetail(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val totalPrice: Double = 0.0,
    val orderStatus: String = "",
    val items: List<Map<String, Any>> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderDetailsScreen(
    riderId: String,
    riderName: String,
    dateMillis: Long,
    navController: NavController
) {
    var orders by remember { mutableStateOf<List<RiderOrderDetail>>(emptyList()) }
    var totalItems by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    LaunchedEffect(key1 = Unit) {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startTimestamp = Timestamp(startOfDay / 1000, 0)
        val endTimestamp = Timestamp(endOfDay / 1000, 0)

        // This is the new, safer way to get the data
        val acceptedOrders = Firebase.firestore.collection("orders")
            .whereEqualTo("riderId", riderId)
            .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
            .whereLessThan("createdAt", endTimestamp)
            .get()
            .await()
            .documents.mapNotNull { doc ->
                // Manually and safely get each piece of data
                val status = doc.getString("orderStatus") ?: ""
                if (status in listOf("Pending", "Rejected", "Cancelled")) {
                    return@mapNotNull null // Skip this order
                }

                // The 'items' field is a list, so we get it as a list.
                // We provide an empty list '?: emptyList()' in case it's missing.
                val itemsList = doc.get("items") as? List<Map<String, Any>> ?: emptyList()

                RiderOrderDetail(
                    id = doc.id,
                    restaurantName = doc.getString("restaurantName") ?: "Unknown Restaurant",
                    userName = doc.getString("userName") ?: "Unknown User",
                    totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                    orderStatus = status,
                    items = itemsList
                )
            }

        orders = acceptedOrders
        totalItems = acceptedOrders.sumOf { order ->
            order.items.sumOf { item ->
                (item["quantity"] as? Long)?.toInt() ?: 0
            }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(riderName) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val formatter = DateTimeFormatter.ofPattern("dd MMMM, yyyy")
                    Text("Accepted Orders on ${selectedDate.format(formatter)}", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    SummaryCard(totalItems = totalItems)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                items(orders) { order ->
                    OrderDetailCard(order = order)
                }
            }
        }
    }
}

@Composable
fun SummaryCard(totalItems: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total Items Handled:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$totalItems", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun OrderDetailCard(order: RiderOrderDetail) {
    val statusColor = when (order.orderStatus) {
        "Delivered" -> Color(0xFF1B5E20) // Dark Green
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(order.restaurantName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Text("To: ${order.userName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total: ৳${order.totalPrice}", fontWeight = FontWeight.Bold)
                Text(order.orderStatus, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}