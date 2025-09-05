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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

// --- DATA BLUEPRINTS FOR THIS SCREEN ---

// Represents a document from the 'store_main_categories' collection
data class MainCategory(
    val id: String = "",
    val name: String = ""
)

// Represents a document from the 'store_sub_categories' collection
data class StoreSubCategory(
    val id: String = "",
    val name: String = "",
    val parentCategory: String = ""
)

// A special class we create in code to hold the organized data
data class GroupedCategory(
    val mainCategory: MainCategory,
    val subCategories: List<StoreSubCategory>
)

// HIGHLIGHT: This line has been corrected from '3ai' to '3Api'
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreManagementScreen() {
    // This state will hold our final, organized list
    var groupedCategories by remember { mutableStateOf<List<GroupedCategory>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    // This state tracks which sub-category we are adding an item to
    var subCategoryForDialog by remember { mutableStateOf<StoreSubCategory?>(null) }

    LaunchedEffect(key1 = Unit) {
        isLoading = true
        val db = Firebase.firestore

        // Step 1: Fetch all main categories and all sub-categories at the same time
        val mainCategoriesResult = db.collection("store_main_categories").get().await()
        val subCategoriesResult = db.collection("store_sub_categories").get().await()

        val mainCategories = mainCategoriesResult.documents.mapNotNull {
            it.toObject(MainCategory::class.java)?.copy(id = it.id)
        }
        val subCategories = subCategoriesResult.documents.mapNotNull {
            it.toObject(StoreSubCategory::class.java)?.copy(id = it.id)
        }

        // Step 2: Group the sub-categories by their 'parentCategory' ID
        val subCategoriesMap = subCategories.groupBy { it.parentCategory }

        // Step 3: Create the final organized list
        groupedCategories = mainCategories.map { mainCat ->
            GroupedCategory(
                mainCategory = mainCat,
                subCategories = subCategoriesMap[mainCat.id] ?: emptyList()
            )
        }
        isLoading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yumzy Store Management") }) }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Loop through each main category group
                items(groupedCategories) { group ->
                    // Main Category Header
                    Text(
                        text = group.mainCategory.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Cards for each sub-category in that group
                    group.subCategories.forEach { subCat ->
                        SubCategoryManagementCard(
                            subCategory = subCat,
                            onAddItemClick = {
                                // When clicked, set the state to show the dialog for this specific sub-category
                                subCategoryForDialog = subCat
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        // The dialog will appear only when 'subCategoryForDialog' is not null
        subCategoryForDialog?.let { currentSubCategory ->
            AddItemDialog(
                subCategory = currentSubCategory,
                onDismiss = { subCategoryForDialog = null } // Hide dialog on dismiss
            )
        }
    }
}

@Composable
fun SubCategoryManagementCard(
    subCategory: StoreSubCategory,
    onAddItemClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = subCategory.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAddItemClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Item")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    subCategory: StoreSubCategory, // Now receives the specific sub-category
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Add Item to ${subCategory.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                TextField(value = name, onValueChange = { name = it }, label = { Text("Item Name") })
                TextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (৳)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
                TextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URL") })

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val priceDouble = price.toDoubleOrNull()
                        if (name.isBlank() || priceDouble == null || description.isBlank()) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newItem = hashMapOf(
                            "name" to name,
                            "price" to priceDouble,
                            "subCategory" to subCategory.name, // We already know the sub-category
                            "itemDescription" to description,
                            "imageUrl" to imageUrl
                        )
                        Firebase.firestore.collection("store_items").add(newItem)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Item added to ${subCategory.name}!", Toast.LENGTH_SHORT).show()
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