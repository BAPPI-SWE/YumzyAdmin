package com.yumzy.admin.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// Blueprint for a Store Sub-Category
data class StoreSubCategory(
    val id: String = "",
    val name: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreManagementScreen() {
    var subCategories by remember { mutableStateOf<List<StoreSubCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    // Fetch the list of sub-categories to show in the dropdown
    LaunchedEffect(key1 = Unit) {
        Firebase.firestore.collection("store_sub_categories").get()
            .addOnSuccessListener { result ->
                subCategories = result.documents.mapNotNull {
                    it.toObject(StoreSubCategory::class.java)?.copy(id = it.id)
                }
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yumzy Store Management") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddItemDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        "Store Sub-Categories",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(subCategories) { category ->
                    Text("- ${category.name}", modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }

        if (showAddItemDialog) {
            AddItemDialog(
                subCategories = subCategories,
                onDismiss = { showAddItemDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    subCategories: List<StoreSubCategory>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    var isSubCategoryExpanded by remember { mutableStateOf(false) }
    var selectedSubCategory by remember { mutableStateOf<StoreSubCategory?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Add New Store Item", style = MaterialTheme.typography.titleLarge)
                TextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") })
                TextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                TextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URL") })

                ExposedDropdownMenuBox(
                    expanded = isSubCategoryExpanded,
                    onExpandedChange = { isSubCategoryExpanded = !isSubCategoryExpanded }
                ) {
                    TextField(
                        value = selectedSubCategory?.name ?: "Select Sub-Category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isSubCategoryExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = isSubCategoryExpanded,
                        onDismissRequest = { isSubCategoryExpanded = false }
                    ) {
                        subCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedSubCategory = category
                                    isSubCategoryExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val priceDouble = price.toDoubleOrNull()
                        // This is the FIX: Create a local copy
                        val currentCategory = selectedSubCategory

                        // Use the local copy in the check
                        if (name.isBlank() || priceDouble == null || currentCategory == null || description.isBlank()) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newItem = hashMapOf(
                            "name" to name,
                            "price" to priceDouble,
                            // Use the local copy here to save the data
                            "subCategory" to currentCategory.name,
                            "itemDescription" to description,
                            "imageUrl" to imageUrl
                        )
                        Firebase.firestore.collection("store_items").add(newItem)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Item added successfully!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Error adding item", Toast.LENGTH_SHORT).show()
                            }
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}