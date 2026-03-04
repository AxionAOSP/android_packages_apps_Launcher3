package com.android.launcher3.allapps.compose.search.data

import com.android.launcher3.allapps.compose.search.model.UniversalSearchResult

import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CalendarSearchProvider(private val context: Context) {

    suspend fun search(query: String, limit: Int = 5): List<UniversalSearchResult.Calendar> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val results = mutableListOf<UniversalSearchResult.Calendar>()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION
        )

        val selection = "${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.VISIBLE} = 1"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${CalendarContract.Events.DTSTART} DESC"

        try {
            Log.d("UniversalSearch", "CalendarSearchProvider: Searching for '$query'")
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                Log.d("UniversalSearch", "CalendarSearchProvider: Found ${cursor.count} events")
                
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: continue
                    val start = cursor.getLong(startCol)
                    val end = cursor.getLong(endCol)
                    val allDay = cursor.getInt(allDayCol) == 1
                    val location = cursor.getString(locCol)

                    results.add(
                        UniversalSearchResult.Calendar(
                            id = id,
                            title = title,
                            startTime = start,
                            endTime = end,
                            isAllDay = allDay,
                            eventLocation = location
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("UniversalSearch", "CalendarSearchProvider: Error", e)
        }

        results
    }
}

