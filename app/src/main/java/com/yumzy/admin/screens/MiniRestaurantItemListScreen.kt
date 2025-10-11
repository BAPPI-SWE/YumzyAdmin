package com.yumzy.admin.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.admin.data.StoreItem
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniRestaurantItemListScreen(
    miniResId: String,
    subCategoryName: String,
    navController: NavController
) {
    var items by remember { mutableStateOf<List<StoreItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<StoreItem?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refreshItems() {
        isLoading = true
        coroutineScope.launch {
            try {
                val result = Firebase.firestore.collection("store_items")
                    .whereEqualTo("miniRes", miniResId)
                    .whereEqualTo("subCategory", subCategoryName)
                    .get().await()
                items = result.documents.mapNotNull { it.toObject(StoreItem::class.java)?.copy(id = it.id) }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshItems()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Items in $subCategoryName") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                itemToEdit = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
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
                items(items) { item ->
                    AdminStoreItemCard(
                        item = item,
                        onEditClick = {
                            itemToEdit = item
                            showDialog = true
                        },
                        onStockChange = { newStockStatus ->
                            val stockValue = if (newStockStatus) "yes" else "no"
                            Firebase.firestore.collection("store_items").document(item.id)
                                .update("stock", stockValue)
                                .addOnSuccessListener { refreshItems() }
                        }
                    )
                }
            }
        }

        if (showDialog) {
            AddEditStoreItemDialog(
                item = itemToEdit,
                onDismiss = { showDialog = false },
                onSave = { itemData ->
                    val task = if (itemToEdit == null) {
                        Firebase.firestore.collection("store_items").add(itemData)
                    } else {
                        Firebase.firestore.collection("store_items").document(itemToEdit!!.id).set(itemData)
                    }
                    task.addOnSuccessListener {
                        Toast.makeText(context, "Item Saved!", Toast.LENGTH_SHORT).show()
                        refreshItems()
                    }.addOnFailureListener {
                        Toast.makeText(context, "Error saving item.", Toast.LENGTH_SHORT).show()
                    }
                    showDialog = false
                },
                fixedMiniResId = miniResId,
                fixedSubCategory = subCategoryName
            )
        }
    }
}

@Composable
fun AdminStoreItemCard(
    item: StoreItem,
    onEditClick: () -> Unit,
    onStockChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().width(120.dp)
            )
            Column(
                modifier = Modifier.padding(12.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("৳${"%.2f".format(item.price)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onEditClick) { Text("EDIT") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if(item.stock == "yes") "In Stock" else "Out", fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked = item.stock == "yes",
                            onCheckedChange = onStockChange,
                            modifier = Modifier.height(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStoreItemDialog(
    item: StoreItem?,
    onDismiss: () -> Unit,
    onSave: (HashMap<String, Any>) -> Unit,
    fixedMiniResId: String,
    fixedSubCategory: String
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var price by remember { mutableStateOf(item?.price?.toString() ?: "") }
    var description by remember { mutableStateOf(item?.itemDescription ?: "") }
    var imageUrl by remember { mutableStateOf(item?.imageUrl ?: "") }
    var deliveryCharge by remember { mutableStateOf(item?.additionalDeliveryCharge?.toString() ?: "0") }
    var serviceCharge by remember { mutableStateOf(item?.additionalServiceCharge?.toString() ?: "0") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (item == null) "Add Item" else "Edit Item", style = MaterialTheme.typography.titleLarge)
                    TextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") })
                    TextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price (৳)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TextField(value = description, onValueChange = { description = it }, label = { Text("Description (Optional)") }, maxLines = 3)
                    TextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URL (Optional)") })
                    TextField(
                        value = deliveryCharge,
                        onValueChange = { deliveryCharge = it },
                        label = { Text("Additional Delivery Charge") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    TextField(
                        value = serviceCharge,
                        onValueChange = { serviceCharge = it },
                        label = { Text("Additional Service Charge") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = {
                            val priceDouble = price.toDoubleOrNull()
                            val deliveryDouble = deliveryCharge.toDoubleOrNull() ?: 0.0
                            val serviceDouble = serviceCharge.toDoubleOrNull() ?: 0.0

                            // Only check if name is blank or price is invalid.
                            if (name.isBlank() || priceDouble == null) {
                                Toast.makeText(context, "Name and Price are required.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val newItemData = hashMapOf<String, Any>(
                                "name" to name,
                                "price" to priceDouble,
                                "itemDescription" to description,
                                "imageUrl" to imageUrl,
                                "additionalDeliveryCharge" to deliveryDouble,
                                "additionalServiceCharge" to serviceDouble,
                                "miniRes" to fixedMiniResId,
                                "subCategory" to fixedSubCategory,
                                "stock" to (item?.stock ?: "yes")
                            )
                            onSave(newItemData)
                        }) { Text("Save") }
                    }
                }
            }
        }
    }
}