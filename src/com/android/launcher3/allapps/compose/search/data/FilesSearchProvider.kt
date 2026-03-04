package com.android.launcher3.allapps.compose.search.data

import com.android.launcher3.allapps.compose.search.model.UniversalSearchResult

import android.content.ContentUris
import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilesSearchProvider(private val context: Context) {
    
    suspend fun search(query: String, limit: Int = 5): List<UniversalSearchResult.File> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        val results = mutableListOf<UniversalSearchResult.File>()
        
        fun queryCollection(collectionUri: Uri, mimeTypeColumn: String, selectionExtras: String? = null) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                mimeTypeColumn,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            val baseSelection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val selection = if (selectionExtras != null) "$baseSelection AND $selectionExtras" else baseSelection
            val selectionArgs = arrayOf("%$query%")
            try {
                Log.d("UniversalSearch", "FilesSearchProvider: Querying $collectionUri with selection '$selection'")
                
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                }
                
                context.contentResolver.query(collectionUri, projection, queryArgs, null)?.use { cursor ->
                     Log.d("UniversalSearch", "FilesSearchProvider: Cursor returned ${cursor.count} rows")
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val mimeCol = cursor.getColumnIndexOrThrow(mimeTypeColumn)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    
                    while (cursor.moveToNext() && results.size < limit * 3) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val path = cursor.getString(dataCol) ?: ""
                        val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                        val size = cursor.getLong(sizeCol)
                        
                        if (results.none { it.path == path }) {
                            results.add(
                                UniversalSearchResult.File(
                                    id = id,
                                    name = name,
                                    path = path,
                                    mimeType = mimeType,
                                    size = size
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UniversalSearch", "FilesSearchProvider: Error querying $collectionUri", e)
            }
        }

        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.MIME_TYPE)
        
        queryCollection(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.MIME_TYPE)
        
        val fileSelection = "${MediaStore.Files.FileColumns.MIME_TYPE} NOT LIKE 'image/%'"
        queryCollection(MediaStore.Files.getContentUri("external"), MediaStore.Files.FileColumns.MIME_TYPE, fileSelection)
        
        try {
             queryCollection(MediaStore.Downloads.EXTERNAL_CONTENT_URI, MediaStore.MediaColumns.MIME_TYPE)
        } catch (e: Exception) {}

        results.take(limit)
    }
}

