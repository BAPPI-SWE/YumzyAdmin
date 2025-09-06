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

// --- UPDATED BLUEPRINT ---
// Added deliveryCharge and serviceCharge to our order details
data class RiderOrderDetail(
    val id: String = "",
    val restaurantName: String = "",
    val userName: String = "",
    val totalPrice: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val serviceCharge: Double = 0.0,
    val orderStatus: String = "",
    val items: List<Map<String, Any>> = emptyList()
)

// This new class will hold all our calculated totals
data class DailyTotals(
    val totalItems: Int = 0,
    val totalGoodsValue: Double = 0.0,
    val totalDeliveryCharge: Double = 0.0,
    val totalServiceCharge: Double = 0.0
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
    var dailyTotals by remember { mutableStateOf(DailyTotals()) } // State for all our totals
    var isLoading by remember { mutableStateOf(true) }
    val selectedDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    LaunchedEffect(key1 = Unit) {
        val zoneId = ZoneId.systemDefault()
        val startOfDay = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startTimestamp = Timestamp(startOfDay / 1000, 0)
        val endTimestamp = Timestamp(endOfDay / 1000, 0)

        val acceptedOrders = Firebase.firestore.collection("orders")
            .whereEqualTo("riderId", riderId)
            .whereGreaterThanOrEqualTo("createdAt", startTimestamp)
            .whereLessThan("createdAt", endTimestamp)
            .get()
            .await()
            .documents.mapNotNull { doc ->
                val status = doc.getString("orderStatus") ?: ""
                if (status in listOf("Pending", "Rejected", "Cancelled")) {
                    return@mapNotNull null
                }
                val itemsList = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                RiderOrderDetail(
                    id = doc.id,
                    restaurantName = doc.getString("restaurantName") ?: "N/A",
                    userName = doc.getString("userName") ?: "N/A",
                    totalPrice = doc.getDouble("totalPrice") ?: 0.0,
                    deliveryCharge = doc.getDouble("deliveryCharge") ?: 0.0,
                    serviceCharge = doc.getDouble("serviceCharge") ?: 0.0,
                    orderStatus = status,
                    items = itemsList
                )
            }

        orders = acceptedOrders

        // --- NEW CALCULATION LOGIC ---
        val itemsCount = acceptedOrders.sumOf { order ->
            order.items.sumOf { item -> (item["quantity"] as? Long)?.toInt() ?: 0 }
        }
        val deliveryChargeSum = acceptedOrders.sumOf { it.deliveryCharge }
        val serviceChargeSum = acceptedOrders.sumOf { it.serviceCharge }
        // Goods value is the total price minus the charges
        val goodsValueSum = acceptedOrders.sumOf { it.totalPrice - it.deliveryCharge - it.serviceCharge }

        dailyTotals = DailyTotals(
            totalItems = itemsCount,
            totalGoodsValue = goodsValueSum,
            totalDeliveryCharge = deliveryChargeSum,
            totalServiceCharge = serviceChargeSum
        )

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
                    // Pass all the new totals to the redesigned summary card
                    SummaryCard(totals = dailyTotals)
                    Spacer(modifier = Modifier.height(16.dp))
                    if(orders.isNotEmpty()){
                        Text("Order List", style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (orders.isEmpty()){
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center){
                            Text("No orders found for this rider on this day.")
                        }
                    }
                } else {
                    items(orders) { order ->
                        OrderDetailCard(order = order)
                    }
                }
            }
        }
    }
}

// --- REDESIGNED SUMMARY CARD ---
@Composable
fun SummaryCard(totals: DailyTotals) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryRow("Total Items Handled", "${totals.totalItems}")
            Divider()
            SummaryRow("Total Goods Value", "৳${"%.2f".format(totals.totalGoodsValue)}")
            Divider()
            SummaryRow("Total Delivery Charge", "৳${"%.2f".format(totals.totalDeliveryCharge)}")
            Divider()
            SummaryRow("Total Service Charge", "৳${"%.2f".format(totals.totalServiceCharge)}")
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
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
                Text("Total: ৳${"%.2f".format(order.totalPrice)}", fontWeight = FontWeight.Bold)
                Text(order.orderStatus, fontWeight = FontWeight.Bold, color = statusColor)
            }
        }
    }
}