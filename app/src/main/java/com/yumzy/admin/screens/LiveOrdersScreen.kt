package com.yumzy.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// This is our "Blueprint" for an order.
// It tells the app what pieces of information to expect for each order.
// The names here (like "restaurantName") MUST EXACTLY match the field names in your Firestore database.
data class AdminOrder(
    val id: String = "", // We will add the document ID here ourselves
    val restaurantName: String = "",
    val userName: String = "",
    val riderName: String? = null, // This can be empty, so we mark it as nullable (?)
    val orderStatus: String = "",
    val totalPrice: Double = 0.0,
    val createdAt: Timestamp = Timestamp.now()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveOrdersScreen() {
    // These variables will hold the state of our screen
    var allOrders by remember { mutableStateOf<List<AdminOrder>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // This block runs once when the screen is first shown.
    // It sets up the real-time listener to Firestore.
    LaunchedEffect(key1 = Unit) {
        Firebase.firestore.collection("orders")
            .orderBy("createdAt", Query.Direction.DESCENDING) // Show newest orders first
            .limit(50) // Get the 50 most recent orders to start
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle any errors, like no internet connection
                    isLoading = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    // When we get new data, we convert it into our AdminOrder blueprint
                    val orders = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(AdminOrder::class.java)?.copy(id = doc.id)
                    }
                    allOrders = orders
                }
                isLoading = false
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Live Order Feed") }) }
    ) { paddingValues ->
        // If data is still loading, show a spinning circle.
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Once loaded, show the list of orders.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allOrders) { order ->
                    AdminOrderCard(order = order)
                }
            }
        }
    }
}

@Composable
fun AdminOrderCard(order: AdminOrder) {
    // This function defines how each individual order card will look.
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val statusColor = when (order.orderStatus) {
        "Pending" -> Color.Gray
        "Accepted", "Preparing" -> Color(0xFF0D47A1) // Dark Blue
        "On the way" -> Color(0xFFE65100) // Orange
        "Delivered" -> Color(0xFF1B5E20) // Dark Green
        "Rejected", "Cancelled" -> Color.Red
        else -> Color.Black
    }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.restaurantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("৳${order.totalPrice}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text("Customer: ${order.userName}", style = MaterialTheme.typography.bodyMedium)
            Text("Rider: ${order.riderName ?: "Not Assigned"}", style = MaterialTheme.typography.bodyMedium)
            Text("Placed at: ${sdf.format(order.createdAt.toDate())}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            Spacer(Modifier.height(8.dp))

            // A small colored surface to highlight the status
            Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.1f)) {
                Text(
                    text = order.orderStatus,
                    color = statusColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

