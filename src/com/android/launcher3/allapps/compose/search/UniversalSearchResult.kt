package com.android.launcher3.allapps.compose.search

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.android.launcher3.model.data.AppInfo

@Immutable
sealed interface UniversalSearchResult {
    
    @Immutable
    data class App(
        val appInfo: AppInfo
    ) : UniversalSearchResult
    
    @Immutable
    data class Contact(
        val id: Long,
        val name: String,
        val photoUri: Uri?,
        val phoneNumber: String?,
        val lookupKey: String
    ) : UniversalSearchResult
    
    @Immutable
    data class Message(
        val id: Long,
        val address: String,
        val body: String,
        val date: Long,
        val isIncoming: Boolean
    ) : UniversalSearchResult
    
    @Immutable
    data class File(
        val id: Long,
        val name: String,
        val path: String,
        val mimeType: String,
        val size: Long
    ) : UniversalSearchResult
    
    @Immutable
    data class Photo(
        val id: Long,
        val name: String,
        val uri: Uri,
        val date: Long
    ) : UniversalSearchResult
    
    @Immutable
    data class Setting(
        val id: String,
        val title: String,
        val intent: Intent
    ) : UniversalSearchResult

    @Immutable
    data class InAppSearch(
        val appInfo: AppInfo,
        val query: String
    ) : UniversalSearchResult
    
    @Immutable
    data class Calendar(
        val id: Long,
        val title: String,
        val startTime: Long,
        val endTime: Long,
        val isAllDay: Boolean,
        val eventLocation: String?
    ) : UniversalSearchResult

    @Immutable
    data class WebAction(
        val query: String,
        val type: WebActionType,
        val subtitle: String? = null,
        val packageName: String? = null
    ) : UniversalSearchResult

    @Immutable
    data class PrivateSpace(
        val isLocked: Boolean,
        val appCount: Int
    ) : UniversalSearchResult
}

enum class WebActionType {
    GOOGLE,
    BROWSER,
    STORE,
    SUGGESTION
}

@Immutable
data class UniversalSearchState(
    val query: String = "",
    val apps: List<UniversalSearchResult.App> = emptyList(),
    val contacts: List<UniversalSearchResult.Contact> = emptyList(),
    val messages: List<UniversalSearchResult.Message> = emptyList(),
    val files: List<UniversalSearchResult.File> = emptyList(),
    val photos: List<UniversalSearchResult.Photo> = emptyList(),
    val settings: List<UniversalSearchResult.Setting> = emptyList(),
    val calendar: List<UniversalSearchResult.Calendar> = emptyList(),
    val inAppSearches: List<UniversalSearchResult.InAppSearch> = emptyList(),
    val webActions: List<UniversalSearchResult.WebAction> = emptyList(),
    val privateSpace: UniversalSearchResult.PrivateSpace? = null,
    val isLoading: Boolean = false,
    val hasContactsPermission: Boolean = true,
    val hasSmsPermission: Boolean = true,
    val hasFilesPermission: Boolean = true,
    val hasPhotosPermission: Boolean = true,
    val hasCalendarPermission: Boolean = true
)
