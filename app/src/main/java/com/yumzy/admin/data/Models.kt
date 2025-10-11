package com.yumzy.admin.data

// This file is the single source of truth for our shared data blueprints.

data class MainCategory(
    val id: String = "",
    val name: String = ""
)

data class StoreSubCategory(
    val id: String = "",
    val name: String = "",
    val parentCategory: String = "",
    val imageUrl: String? = null,
    val availableLocations: List<String> = emptyList()
)

data class GroupedCategory(
    val mainCategory: MainCategory,
    val subCategories: List<StoreSubCategory>
)

// NEW DATA CLASS FOR MINI RESTAURANTS
data class MiniRestaurant(
    val id: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val open: String = "yes", // "yes" or "no"
    val parentCategory: String = "",
    val availableLocations: List<String> = emptyList()
)

// NEW DATA CLASS FOR STORE ITEMS
data class StoreItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val itemDescription: String = "",
    val subCategory: String = "",
    val miniRes: String = "", // The ID of the mini restaurant
    val additionalDeliveryCharge: Double = 0.0,
    val additionalServiceCharge: Double = 0.0,
    val stock: String = "yes" // "yes" or "no"
)