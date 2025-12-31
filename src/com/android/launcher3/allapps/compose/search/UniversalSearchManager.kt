package com.android.launcher3.allapps.compose.search

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.android.launcher3.model.data.AppInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class UniversalSearchManager(private val context: Context) {
    
    private val contactsProvider = ContactsSearchProvider(context)
    private val messagesProvider = MessagesSearchProvider(context)
    private val filesProvider = FilesSearchProvider(context)
    private val photosProvider = PhotosSearchProvider(context)
    private val calendarProvider = CalendarSearchProvider(context)
    private val settingsProvider = SettingsSearchProvider(context)
    
    private val _searchState = MutableStateFlow(UniversalSearchState())
    val searchState: StateFlow<UniversalSearchState> = _searchState.asStateFlow()
    
    private var searchJob: Job? = null
    private val searchScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val prefs = context.getSharedPreferences("com.android.launcher3.prefs", Context.MODE_PRIVATE)
    private val _preferences = MutableStateFlow(loadPreferences())
    val preferences: StateFlow<SearchPreferences> = _preferences.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("pref_search_")) {
            _preferences.value = loadPreferences()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadPreferences(): SearchPreferences {
        return SearchPreferences(
            searchContacts = prefs.getBoolean("pref_search_contacts", true),
            searchMessages = prefs.getBoolean("pref_search_messages", true),
            searchFiles = prefs.getBoolean("pref_search_files", true),
            searchPhotos = prefs.getBoolean("pref_search_photos", true),
            searchCalendar = prefs.getBoolean("pref_search_calendar", true),
            searchSettings = prefs.getBoolean("pref_search_settings", true),
            searchWeb = prefs.getBoolean("pref_search_web", true)
        )
    }

    fun setSearchContacts(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_contacts", enabled).apply()
    }

    fun setSearchMessages(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_messages", enabled).apply()
    }

    fun setSearchFiles(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_files", enabled).apply()
    }

    fun setSearchPhotos(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_photos", enabled).apply()
    }

    fun setSearchCalendar(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_calendar", enabled).apply()
    }

    fun setSearchWeb(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_web", enabled).apply()
    }

    fun setSearchSettings(enabled: Boolean) {
        prefs.edit().putBoolean("pref_search_settings", enabled).apply()
    }
    
    fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }
    
    fun hasFilesPermission(): Boolean {
        return Environment.isExternalStorageManager()
    }

    fun hasPhotosPermission(): Boolean {
        if (Environment.isExternalStorageManager()) {
            return true
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
    
    fun isGoogleAppAvailable(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.googlequicksearchbox", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun search(
        query: String,
        apps: List<AppInfo>,
        hasPrivateSpace: Boolean = false,
        isPrivateSpaceLocked: Boolean = true,
        privateAppCount: Int = 0
    ) {
        searchJob?.cancel()
        
        if (query.isBlank()) {
            _searchState.value = UniversalSearchState()
            return
        }
        
        searchJob = searchScope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)
            
            delay(300)
            
            val filteredApps = apps.filter { appInfo ->
                appInfo.title?.toString()?.contains(query, ignoreCase = true) == true
            }.take(10).map { UniversalSearchResult.App(it) }
            
            val privateSpaceResult = if (hasPrivateSpace && isPrivateSpaceKeyword(query)) {
                UniversalSearchResult.PrivateSpace(
                    isLocked = isPrivateSpaceLocked,
                    appCount = privateAppCount
                )
            } else null
            
            val contactsDeferred = async {
                if (preferences.value.searchContacts && hasContactsPermission()) {
                    contactsProvider.search(query)
                } else emptyList()
            }
            
            val messagesDeferred = async {
                if (preferences.value.searchMessages && hasSmsPermission()) {
                    messagesProvider.search(query)
                } else emptyList()
            }
            
            
            val filesDeferred = async {
                val hasPerm = hasFilesPermission()
                Log.d("UniversalSearch", "Search Files: enabled=${preferences.value.searchFiles}, permission=$hasPerm")
                if (preferences.value.searchFiles && hasPerm) {
                    filesProvider.search(query).also { Log.d("UniversalSearch", "Files found: ${it.size}") }
                } else emptyList()
            }


            val photosDeferred = async {
                val hasPerm = hasPhotosPermission()
                Log.d("UniversalSearch", "Search Photos: enabled=${preferences.value.searchPhotos}, permission=$hasPerm")
                if (preferences.value.searchPhotos && hasPerm) {
                    photosProvider.search(query).also { Log.d("UniversalSearch", "Photos found: ${it.size}") }
                } else emptyList()
            }

            val calendarDeferred = async {
                val hasPerm = hasCalendarPermission()
                Log.d("UniversalSearch", "Search Calendar: enabled=${preferences.value.searchCalendar}, permission=$hasPerm")
                if (preferences.value.searchCalendar && hasPerm) {
                    calendarProvider.search(query).also { Log.d("UniversalSearch", "Calendar events found: ${it.size}") }
                } else emptyList()
            }

            val settingsDeferred = async {
                if (preferences.value.searchSettings) {
                    settingsProvider.search(query)
                } else emptyList()
            }
            
            val contacts = contactsDeferred.await()
            val messages = messagesDeferred.await()
            val files = filesDeferred.await()
            val photos = photosDeferred.await()
            val calendar = calendarDeferred.await()
            val settings = settingsDeferred.await()
            
            val webActions = buildList {
                 if (preferences.value.searchWeb) {
                    if (isGoogleAppAvailable()) {
                        add(UniversalSearchResult.WebAction(query, WebActionType.GOOGLE, "Search on Google"))
                    }
                    add(UniversalSearchResult.WebAction(query, WebActionType.BROWSER, "Search on the web"))
                    
                    if (isPackageInstalled("com.google.android.youtube")) {
                         add(UniversalSearchResult.WebAction(query, WebActionType.SUGGESTION, "Search on YouTube", "com.google.android.youtube"))
                    }
                    if (isPackageInstalled("com.spotify.music")) {
                         add(UniversalSearchResult.WebAction(query, WebActionType.SUGGESTION, "Search on Spotify", "com.spotify.music"))
                    }
                    if (isPackageInstalled("com.google.android.apps.youtube.music")) {
                         add(UniversalSearchResult.WebAction(query, WebActionType.SUGGESTION, "Search on YouTube Music", "com.google.android.apps.youtube.music"))
                    }
                 }
                add(UniversalSearchResult.WebAction(query, WebActionType.STORE, "Search in Play Store"))
            }

            val inAppSearches = getSearchableApps(query)
            
            _searchState.value = UniversalSearchState(
                query = query,
                apps = filteredApps,
                contacts = contacts,
                messages = messages,
                files = files,
                photos = photos,
                calendar = calendar,
                settings = settings,
                inAppSearches = inAppSearches,
                webActions = webActions,
                privateSpace = privateSpaceResult,
                isLoading = false,
                hasContactsPermission = hasContactsPermission(),
                hasSmsPermission = hasSmsPermission(),
                hasFilesPermission = hasFilesPermission(),
                hasPhotosPermission = hasPhotosPermission(),
                hasCalendarPermission = hasCalendarPermission()
            )
        }
    }

    private fun isPrivateSpaceKeyword(query: String): Boolean {
        return query.equals("private space", ignoreCase = true)
    }
    
    fun getContactIntent(contact: UniversalSearchResult.Contact): Intent {
        val uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        return Intent(Intent.ACTION_VIEW, uri)
    }
    
    fun getMessageIntent(message: UniversalSearchResult.Message): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("sms:${message.address}")
        }
    }
    
    fun getFileIntent(file: UniversalSearchResult.File): Intent {
        val uri = Uri.parse("content://media/external/file/${file.id}")
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getPhotoIntent(photo: UniversalSearchResult.Photo): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(photo.uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun getCalendarIntent(event: UniversalSearchResult.Calendar): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
        return Intent(Intent.ACTION_VIEW).apply {
             data = uri
             putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTime)
             putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTime)
        }
    }
    
    fun getGoogleSearchIntent(query: String): Intent {
        return Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            setPackage("com.google.android.googlequicksearchbox")
        }
    }
    
    fun getBrowserSearchIntent(query: String): Intent {
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
        return Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
    }
    
    fun getStoreSearchIntent(query: String): Intent {
        val searchUrl = "market://search?q=${Uri.encode(query)}&c=apps"
        return Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
    }
    
    fun getAppSearchIntent(packageName: String, query: String): Intent {
        val uri = when (packageName) {
            "com.google.android.youtube" -> Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
            "com.spotify.music" -> Uri.parse("spotify:search:${Uri.encode(query)}")
            "com.google.android.apps.youtube.music" -> Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
            else -> Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
             if (packageName != "com.google.android.apps.youtube.music") {
                setPackage(packageName)
             }
        }
    }

    private fun getSearchableApps(query: String): List<UniversalSearchResult.InAppSearch> {
        val intent = Intent(Intent.ACTION_SEARCH)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        
        return activities.mapNotNull { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == "com.google.android.googlequicksearchbox" || 
                packageName == "com.android.vending" || 
                packageName == context.packageName) {
                null
            } else {
                try {
                    val componentName = ComponentName(packageName, resolveInfo.activityInfo.name)
                    val title = resolveInfo.activityInfo.loadLabel(context.packageManager)
                    val user = Process.myUserHandle()
                    val intent = Intent(Intent.ACTION_SEARCH)
                    intent.setComponent(componentName)
                    
                    val appInfo = AppInfo(componentName, title, user, intent)
                    UniversalSearchResult.InAppSearch(appInfo, query)
                } catch (e: Exception) {
                    null
                }
            }
        }.take(5)
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    fun clear() {
        searchJob?.cancel()
        _searchState.value = UniversalSearchState()
    }
    
    fun cleanup() {
        searchJob?.cancel()
        searchScope.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

data class SearchPreferences(
    val searchContacts: Boolean = true,
    val searchMessages: Boolean = true,
    val searchFiles: Boolean = true,
    val searchPhotos: Boolean = true,
    val searchCalendar: Boolean = true,
    val searchSettings: Boolean = true,
    val searchWeb: Boolean = true
)
