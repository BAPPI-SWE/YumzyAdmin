package com.yumzy.admin.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.admin.components.MultiSelectDropdown
import com.yumzy.admin.data.GroupedCategory
import com.yumzy.admin.data.MainCategory
import com.yumzy.admin.data.StoreSubCategory
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Data class for locations, since it's only used here.
data class LocationData(val subLocations: List<String> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen() {
    var groupedCategories by remember { mutableStateOf<List<GroupedCategory>>(emptyList()) }
    var mainCategories by remember { mutableStateOf<List<MainCategory>>(emptyList()) }
    var allSubLocations by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<StoreSubCategory?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refreshData() {
        isLoading = true
        coroutineScope.launch {
            val db = Firebase.firestore
            val mainCatsResult = db.collection("store_main_categories").get().await()
            val subCatsResult = db.collection("store_sub_categories").get().await()
            val locationsResult = db.collection("locations").get().await()

            mainCategories = mainCatsResult.documents.mapNotNull { it.toObject(MainCategory::class.java)?.copy(id = it.id) }
            val subCategories = subCatsResult.documents.mapNotNull { it.toObject(StoreSubCategory::class.java)?.copy(id = it.id) }
            allSubLocations = locationsResult.documents.flatMap { it.toObject(LocationData::class.java)?.subLocations ?: emptyList() }.distinct()

            val subCategoriesMap = subCategories.groupBy { it.parentCategory }
            groupedCategories = mainCategories.map { mainCat ->
                GroupedCategory(mainCategory = mainCat, subCategories = subCategoriesMap[mainCat.id] ?: emptyList())
            }
            isLoading = false
        }
    }

    LaunchedEffect(key1 = Unit) {
        refreshData()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Manage Store Categories") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Sub-Category")
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(modifier = Modifier.padding(paddingValues), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(groupedCategories) { group ->
                    Text(text = group.mainCategory.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    group.subCategories.forEach { subCat ->
                        SubCategoryRow(subCategory = subCat, onClick = { categoryToEdit = subCat })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (showAddDialog) {
            EditCategoryDialog(
                mainCategories = mainCategories,
                allLocations = allSubLocations,
                onDismiss = { showAddDialog = false },
                onSave = { name, imageUrl, parentId, locations ->
                    val newCategory = hashMapOf("name" to name, "imageUrl" to imageUrl, "parentCategory" to parentId, "availableLocations" to locations)
                    Firebase.firestore.collection("store_sub_categories").add(newCategory).addOnSuccessListener {
                        Toast.makeText(context, "Category Added", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }
                    showAddDialog = false
                }
            )
        }

        categoryToEdit?.let { category ->
            EditCategoryDialog(
                mainCategories = mainCategories,
                allLocations = allSubLocations,
                subCategory = category,
                onDismiss = { categoryToEdit = null },
                onSave = { name, imageUrl, parentId, locations ->
                    val updatedCategory = mapOf("name" to name, "imageUrl" to imageUrl, "parentCategory" to parentId, "availableLocations" to locations)
                    Firebase.firestore.collection("store_sub_categories").document(category.id).update(updatedCategory).addOnSuccessListener {
                        Toast.makeText(context, "Category Updated", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }
                    categoryToEdit = null
                },
                onDelete = {
                    Firebase.firestore.collection("store_sub_categories").document(category.id).delete().addOnSuccessListener {
                        Toast.makeText(context, "Category Deleted", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }
                    categoryToEdit = null
                }
            )
        }
    }
}

@Composable
fun SubCategoryRow(subCategory: StoreSubCategory, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(subCategory.name, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryDialog(
    mainCategories: List<MainCategory>,
    allLocations: List<String>,
    subCategory: StoreSubCategory? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, imageUrl: String, parentId: String, locations: List<String>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(subCategory?.name ?: "") }
    var imageUrl by remember { mutableStateOf(subCategory?.imageUrl ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var selectedParent by remember { mutableStateOf(mainCategories.find { it.id == subCategory?.parentCategory }) }
    var selectedLocations by remember { mutableStateOf(subCategory?.availableLocations ?: emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (subCategory == null) "Add Sub-Category" else "Edit Sub-Category", style = MaterialTheme.typography.titleLarge)
                TextField(value = name, onValueChange = { name = it }, label = { Text("Sub-Category Name") })
                TextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URL") })

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(value = selectedParent?.name ?: "Select Main Category", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        mainCategories.forEach { mainCat ->
                            DropdownMenuItem(text = { Text(mainCat.name) }, onClick = { selectedParent = mainCat; expanded = false })
                        }
                    }
                }

                MultiSelectDropdown(
                    label = "Available Locations",
                    options = allLocations,
                    selectedOptions = selectedLocations,
                    onSelectionChanged = { selectedLocations = it }
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                        if(showDeleteConfirm){
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                title = { Text("Delete Category?") },
                                text = { Text("Are you sure you want to delete '${subCategory?.name}'? This cannot be undone.")},
                                confirmButton = { Button(onClick = { onDelete(); showDeleteConfirm = false }) { Text("Delete") } },
                                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }}
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val currentParent = selectedParent
                        if(name.isBlank() || imageUrl.isBlank() || currentParent == null || selectedLocations.isEmpty()){
                          //  Toast.makeText(LocalContext.current, "All fields are required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(name, imageUrl, currentParent.id, selectedLocations)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}