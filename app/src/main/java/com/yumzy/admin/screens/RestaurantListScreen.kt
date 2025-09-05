package com.yumzy.admin.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// Blueprint for a Restaurant object.
// The field names MUST match your Firestore document fields.
data class Restaurant(
    val id: String = "",
    val name: String = "",
    val cuisine: String? = null,
    val imageUrl: String? = null,
    val ownerId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantListScreen() {
    var restaurants by remember { mutableStateOf<List<Restaurant>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // This runs once to fetch the restaurant data from Firestore.
    // Unlike the orders screen, this one doesn't need to be real-time for now.
    LaunchedEffect(key1 = Unit) {
        Firebase.firestore.collection("restaurants")
            .get()
            .addOnSuccessListener { result ->
                restaurants = result.documents.mapNotNull { doc ->
                    doc.toObject(Restaurant::class.java)?.copy(id = doc.id)
                }
                isLoading = false
            }
            .addOnFailureListener {
                isLoading = false
                // Handle error
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("All Restaurants") }) }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(restaurants) { restaurant ->
                    RestaurantCard(restaurant = restaurant)
                }
            }
        }
    }
}

@Composable
fun RestaurantCard(restaurant: Restaurant) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Restaurant Image
            AsyncImage(
                model = restaurant.imageUrl,
                contentDescription = "${restaurant.name} image",
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Restaurant Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!restaurant.cuisine.isNullOrEmpty()) {
                    Text(
                        text = restaurant.cuisine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}