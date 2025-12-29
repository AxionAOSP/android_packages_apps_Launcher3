package com.android.launcher3.allapps.compose.search

import android.content.ContentUris
import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotosSearchProvider(private val context: Context) {
    
    suspend fun search(query: String, limit: Int = 10): List<UniversalSearchResult.Photo> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        val results = mutableListOf<UniversalSearchResult.Photo>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        
        try {
            Log.d("UniversalSearch", "PhotosSearchProvider: Searching for '$query'")
            
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media.DATE_MODIFIED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                queryArgs,
                null
            )?.use { cursor ->
                Log.d("UniversalSearch", "PhotosSearchProvider: Found ${cursor.count} photos")
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val date = cursor.getLong(dateCol) * 1000
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    
                    results.add(
                        UniversalSearchResult.Photo(
                            id = id,
                            name = name,
                            uri = uri,
                            date = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
             Log.e("UniversalSearch", "PhotosSearchProvider: Error", e)
        }
        
        results
    }
}
