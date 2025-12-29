package com.android.launcher3.allapps.compose.search

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessagesSearchProvider(private val context: Context) {
    
    suspend fun search(query: String, limit: Int = 5): List<UniversalSearchResult.Message> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        val results = mutableListOf<UniversalSearchResult.Message>()
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        val selection = "${Telephony.Sms.BODY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"
        
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: continue
                    val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: continue
                    val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    
                    results.add(
                        UniversalSearchResult.Message(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                        )
                    )
                }
            }
        } catch (e: Exception) {
        }
        
        results
    }
}
