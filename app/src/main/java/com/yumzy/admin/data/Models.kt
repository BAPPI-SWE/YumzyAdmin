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