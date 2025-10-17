package com.yumzy.admin.utils

import android.net.Uri
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.UUID

object ImageUploadHelper {

    private val storage = Firebase.storage

    /**
     * Uploads an image to Firebase Storage and returns the download URL
     * @param uri The local URI of the image to upload
     * @param folder The folder path in Firebase Storage (e.g., "subcategories", "mini_restaurants", "store_items")
     * @return The download URL of the uploaded image
     */
    suspend fun uploadImage(uri: Uri, folder: String): String {
        // Generate a unique filename
        val filename = "${UUID.randomUUID()}.jpg"
        val storageRef = storage.reference.child("$folder/$filename")

        // Upload the file
        storageRef.putFile(uri).await()

        // Get the download URL
        return storageRef.downloadUrl.await().toString()
    }

    /**
     * Deletes an image from Firebase Storage given its URL
     * @param imageUrl The full download URL of the image
     */
    suspend fun deleteImage(imageUrl: String) {
        try {
            if (imageUrl.isNotBlank() && imageUrl.contains("firebase")) {
                val storageRef = storage.getReferenceFromUrl(imageUrl)
                storageRef.delete().await()
            }
        } catch (e: Exception) {
            // Image might not exist or URL is invalid, ignore
        }
    }

    /**
     * Replaces an old image with a new one
     * @param oldImageUrl The URL of the image to delete
     * @param newImageUri The URI of the new image to upload
     * @param folder The folder path in Firebase Storage
     * @return The download URL of the new image
     */
    suspend fun replaceImage(oldImageUrl: String?, newImageUri: Uri, folder: String): String {
        // Delete old image if exists
        oldImageUrl?.let { deleteImage(it) }

        // Upload new image
        return uploadImage(newImageUri, folder)
    }
}