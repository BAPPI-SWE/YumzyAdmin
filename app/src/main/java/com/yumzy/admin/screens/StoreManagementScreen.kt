package com.yumzy.admin.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yumzy.admin.components.MultiSelectDropdown
import com.yumzy.admin.data.GroupedCategory
import com.yumzy.admin.data.MainCategory
import com.yumzy.admin.data.MiniRestaurant
import com.yumzy.admin.data.StoreSubCategory
import com.yumzy.admin.navigation.Screen
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreManagementScreen(navController: NavController) {
    var groupedCategories by remember { mutableStateOf<List<GroupedCategory>>(emptyList()) }
    var miniRestaurants by remember { mutableStateOf<List<MiniRestaurant>>(emptyList()) }
    var mainCategories by remember { mutableStateOf<List<MainCategory>>(emptyList()) }
    var allSubLocations by remember { mutableStateOf<List<String>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Categories", "Shops")
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var showAddEditDialog by remember { mutableStateOf(false) }
    var restaurantToEdit by remember { mutableStateOf<MiniRestaurant?>(null) }


    fun refreshData() {
        isLoading = true
        coroutineScope.launch {
            try {
                val db = Firebase.firestore
                val mainCatsResult = db.collection("store_main_categories").get().await()
                val subCatsResult = db.collection("store_sub_categories").get().await()
                val locationsResult = db.collection("locations").get().await()
                val miniResResult = db.collection("mini_restaurants").get().await()

                mainCategories = mainCatsResult.documents.mapNotNull { it.toObject(MainCategory::class.java)?.copy(id = it.id) }
                val subCategories = subCatsResult.documents.mapNotNull { it.toObject(StoreSubCategory::class.java)?.copy(id = it.id) }
                allSubLocations = locationsResult.documents.flatMap { it.toObject(LocationData::class.java)?.subLocations ?: emptyList() }.distinct()
                miniRestaurants = miniResResult.documents.mapNotNull { it.toObject(MiniRestaurant::class.java)?.copy(id = it.id) }

                val subCategoriesMap = subCategories.groupBy { it.parentCategory }
                groupedCategories = mainCategories.map { mainCat ->
                    GroupedCategory(mainCategory = mainCat, subCategories = subCategoriesMap[mainCat.id] ?: emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching data: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yumzy Store Management") }) },
        floatingActionButton = {
            if (selectedTabIndex == 1) { // Show FAB only on "Shops" tab
                FloatingActionButton(onClick = {
                    restaurantToEdit = null // Ensure we are adding, not editing
                    showAddEditDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Mini-Restaurant")
                }
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                when (selectedTabIndex) {
                    0 -> CategoriesContent(groupedCategories)
                    1 -> ShopsContent(
                        miniRestaurants = miniRestaurants,
                        onCardClick = { miniRes ->
                            navController.navigate(Screen.MiniResSubCategories.createRoute(miniRes.id, miniRes.name, miniRes.parentCategory))
                        },
                        onEditClick = { miniRes ->
                            restaurantToEdit = miniRes
                            showAddEditDialog = true
                        },
                        onStatusChange = { miniRes, isOpen ->
                            val newStatus = if (isOpen) "yes" else "no"
                            Firebase.firestore.collection("mini_restaurants").document(miniRes.id)
                                .update("open", newStatus)
                                .addOnSuccessListener { refreshData() }
                        }
                    )
                }
            }
        }

        if (showAddEditDialog) {
            AddEditMiniRestaurantDialog(
                restaurant = restaurantToEdit,
                mainCategories = mainCategories,
                allLocations = allSubLocations,
                onDismiss = { showAddEditDialog = false },
                onSave = { name, imageUrl, parentId, locations, openStatus ->
                    val data = hashMapOf(
                        "name" to name,
                        "imageUrl" to imageUrl,
                        "parentCategory" to parentId,
                        "availableLocations" to locations,
                        "open" to if (openStatus) "yes" else "no"
                    )
                    val task = if (restaurantToEdit == null) {
                        Firebase.firestore.collection("mini_restaurants").add(data)
                    } else {
                        Firebase.firestore.collection("mini_restaurants").document(restaurantToEdit!!.id).set(data)
                    }
                    task.addOnSuccessListener {
                        Toast.makeText(context, "Shop Saved!", Toast.LENGTH_SHORT).show()
                        refreshData()
                    }.addOnFailureListener {
                        Toast.makeText(context, "Error saving shop.", Toast.LENGTH_SHORT).show()
                    }
                    showAddEditDialog = false
                }
            )
        }
    }
}

@Composable
fun CategoriesContent(groupedCategories: List<GroupedCategory>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(groupedCategories) { group ->
            Text(
                text = group.mainCategory.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            group.subCategories.forEach { subCat ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = subCat.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ShopsContent(
    miniRestaurants: List<MiniRestaurant>,
    onCardClick: (MiniRestaurant) -> Unit,
    onEditClick: (MiniRestaurant) -> Unit,
    onStatusChange: (MiniRestaurant, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(miniRestaurants) { restaurant ->
            MiniRestaurantAdminCard(
                restaurant = restaurant,
                onClick = { onCardClick(restaurant) },
                onStatusChange = { isOpen -> onStatusChange(restaurant, isOpen) },
                onEditClick = { onEditClick(restaurant) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniRestaurantAdminCard(
    restaurant: MiniRestaurant,
    onClick: () -> Unit,
    onStatusChange: (Boolean) -> Unit,
    onEditClick: () -> Unit
) {
    val isClosed = restaurant.open.equals("no", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth().height(180.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = restaurant.imageUrl,
                contentDescription = restaurant.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            )
            if (isClosed) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = "Closed", tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onEditClick) {
                        Text("EDIT", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isClosed) "Closed" else "Open", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = !isClosed,
                            onCheckedChange = onStatusChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
                Text(
                    text = restaurant.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 22.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMiniRestaurantDialog(
    restaurant: MiniRestaurant?,
    mainCategories: List<MainCategory>,
    allLocations: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, imageUrl: String, parentId: String, locations: List<String>, openStatus: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(restaurant?.name ?: "") }
    var imageUrl by remember { mutableStateOf(restaurant?.imageUrl ?: "") }
    var isOpen by remember { mutableStateOf(restaurant?.open?.equals("yes", true) ?: true) }

    var expanded by remember { mutableStateOf(false) }
    var selectedParent by remember { mutableStateOf(mainCategories.find { it.id == restaurant?.parentCategory }) }
    var selectedLocations by remember { mutableStateOf(restaurant?.availableLocations ?: emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (restaurant == null) "Add Shop" else "Edit Shop", style = MaterialTheme.typography.titleLarge)
                TextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") })
                TextField(value = imageUrl, onValueChange = { imageUrl = it }, label = { Text("Image URL") })

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    TextField(
                        value = selectedParent?.name ?: "Select Main Category",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor()
                    )
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shop Status:")
                    Spacer(Modifier.width(8.dp))
                    Text(if (isOpen) "Open" else "Closed")
                    Spacer(Modifier.weight(1f))
                    Switch(checked = isOpen, onCheckedChange = { isOpen = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = {
                        val parent = selectedParent
                        if (name.isBlank() || imageUrl.isBlank() || parent == null || selectedLocations.isEmpty()) {
                        //    Toast.makeText(LocalContext.current, "All fields are required.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSave(name, imageUrl, parent.id, selectedLocations, isOpen)
                    }) { Text("Save") }
                }
            }
        }
    }
}