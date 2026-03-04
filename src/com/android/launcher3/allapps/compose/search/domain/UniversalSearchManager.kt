package com.android.launcher3.allapps.compose.search.domain

import com.android.launcher3.allapps.compose.shared.constants.PreferenceKeys
import com.android.launcher3.allapps.compose.search.data.*
import com.android.launcher3.allapps.compose.search.model.*

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.search.StringMatcherUtility
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

    private val historyPrefs = context.getSharedPreferences("com.android.launcher3.search_history", Context.MODE_PRIVATE)
    private var searchHistory: List<String> = loadHistory()

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        _searchState.value = _searchState.value.copy(history = searchHistory)
    }

    private fun loadHistory(): List<String> {
        val historyString = historyPrefs.getString(PreferenceKeys.SEARCH_HISTORY, "") ?: ""
        return if (historyString.isEmpty()) emptyList() else historyString.split("|")
    }

    private fun saveHistory() {
        historyPrefs.edit().putString(PreferenceKeys.SEARCH_HISTORY, searchHistory.joinToString("|")).apply()
        _searchState.value = _searchState.value.copy(history = searchHistory)
    }

    fun addToHistory(query: String) {
        if (query.isBlank()) return
        val currentHistory = searchHistory.toMutableList()
        currentHistory.remove(query)
        currentHistory.add(0, query)
        searchHistory = currentHistory.take(10)
        saveHistory()
    }

    fun removeFromHistory(query: String) {
        val currentHistory = searchHistory.toMutableList()
        currentHistory.remove(query)
        searchHistory = currentHistory
        saveHistory()
    }

    fun clearHistory() {
        searchHistory = emptyList()
        saveHistory()
    }

    private fun loadPreferences(): SearchPreferences {
        return SearchPreferences(
            searchContacts = prefs.getBoolean(PreferenceKeys.SEARCH_CONTACTS, true),
            searchMessages = prefs.getBoolean(PreferenceKeys.SEARCH_MESSAGES, true),
            searchFiles = prefs.getBoolean(PreferenceKeys.SEARCH_FILES, true),
            searchPhotos = prefs.getBoolean(PreferenceKeys.SEARCH_PHOTOS, true),
            searchCalendar = prefs.getBoolean(PreferenceKeys.SEARCH_CALENDAR, true),
            searchSettings = prefs.getBoolean(PreferenceKeys.SEARCH_SETTINGS, true),
            searchWeb = prefs.getBoolean(PreferenceKeys.SEARCH_WEB, true),
            showWebActions = prefs.getBoolean(PreferenceKeys.SEARCH_SHOW_WEB_ACTIONS, true),
            showInAppSearch = prefs.getBoolean(PreferenceKeys.SEARCH_SHOW_IN_APP_SEARCH, true)
        )
    }

    fun setSearchPreference(key: String, enabled: Boolean) {
        prefs.edit().putBoolean(key, enabled).apply()
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
            _searchState.value = UniversalSearchState(history = searchHistory)
            return
        }
        
        searchJob = searchScope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true)

            delay(100)

            val (filteredApps, appActionsResult, privateSpaceResult, inAppSearches, webActions) = withContext(Dispatchers.Default) {
                val matcher = StringMatcherUtility.StringMatcher.getInstance()
                val normalizedQuery = query.filter { it.isLetterOrDigit() }.lowercase()
                val scoredApps = apps.asSequence()
                    .filter { appInfo ->
                        PackageManagerHelper.isLauncherAppTarget(appInfo.intent)
                    }
                    .mapNotNull { appInfo ->
                        val title = appInfo.title?.toString() ?: ""
                        val normalizedTitle = title.filter { it.isLetterOrDigit() }.lowercase()

                        val score = when {
                            title.equals(query, ignoreCase = true) -> 100
                            normalizedTitle == normalizedQuery && normalizedQuery.isNotEmpty() -> 95
                            title.startsWith(query, ignoreCase = true) -> 80
                            normalizedTitle.startsWith(normalizedQuery) && normalizedQuery.isNotEmpty() -> 75
                            StringMatcherUtility.matches(query, title, matcher) -> 60
                            title.contains(query, ignoreCase = true) -> 40
                            normalizedTitle.contains(normalizedQuery) && normalizedQuery.isNotEmpty() -> 35
                            else -> 0
                        }
                        if (score > 0) appInfo to score else null
                    }
                    .sortedWith { a, b ->
                        if (a.second != b.second) b.second - a.second
                        else (a.first.title?.toString() ?: "").compareTo(b.first.title?.toString() ?: "", ignoreCase = true)
                    }
                    .toList()

                val scored = scoredApps
                    .take(10)
                    .map { UniversalSearchResult.App(it.first) }
                    .toList()

                val actions = scoredApps.asSequence()
                    .map { it.first }
                    .distinctBy { it.componentName }
                    .take(5)
                    .mapNotNull { candidateApp ->
                        val packageName = candidateApp.componentName?.packageName ?: return@mapNotNull null
                        val request = ShortcutRequest(context, candidateApp.user)
                        val shortcuts = request.forPackage(packageName).query(ShortcutRequest.PUBLISHED)
                        if (shortcuts.isEmpty()) return@mapNotNull null

                        val launcherApps = context.getSystemService(LauncherApps::class.java)
                        val density = context.resources.displayMetrics.densityDpi
                        val shortcutActions = shortcuts.take(4).map { shortcut ->
                            val icon = try {
                                launcherApps.getShortcutIconDrawable(shortcut, density)
                            } catch (e: Exception) {
                                null
                            }
                            UniversalSearchResult.AppActions.Action(
                                label = shortcut.shortLabel?.toString() ?: "",
                                icon = icon,
                                shortcutId = shortcut.id
                            )
                        }
                        UniversalSearchResult.AppActions(candidateApp, shortcutActions)
                    }
                    .firstOrNull()

                val psResult = if (hasPrivateSpace && isPrivateSpaceKeyword(query)) {
                    UniversalSearchResult.PrivateSpace(
                        isLocked = isPrivateSpaceLocked,
                        appCount = privateAppCount
                    )
                } else null

                val web = if (preferences.value.showWebActions) {
                    buildList {
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
                } else {
                    emptyList()
                }

                val inApp = if (preferences.value.showInAppSearch) {
                    getSearchableApps(query)
                } else {
                    emptyList()
                }

                SearchLocalResults(scored, actions, psResult, inApp, web)
            }

            val contactsDeferred = async(Dispatchers.IO) {
                if (preferences.value.searchContacts && hasContactsPermission()) {
                    contactsProvider.search(query)
                } else emptyList()
            }

            val messagesDeferred = async(Dispatchers.IO) {
                if (preferences.value.searchMessages && hasSmsPermission()) {
                    messagesProvider.search(query)
                } else emptyList()
            }

            val filesDeferred = async(Dispatchers.IO) {
                if (preferences.value.searchFiles && hasFilesPermission()) {
                    filesProvider.search(query)
                } else emptyList()
            }

            val photosDeferred = async(Dispatchers.IO) {
                if (preferences.value.searchPhotos && hasPhotosPermission()) {
                    photosProvider.search(query)
                } else emptyList()
            }

            val calendarDeferred = async(Dispatchers.IO) {
                if (preferences.value.searchCalendar && hasCalendarPermission()) {
                    calendarProvider.search(query)
                } else emptyList()
            }

            val settingsDeferred = async(Dispatchers.IO) {
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

            _searchState.value = UniversalSearchState(
                query = query,
                history = searchHistory,
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
                appActions = appActionsResult,
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
        val searchManager = context.getSystemService(SearchManager::class.java) ?: return emptyList()
        val matcher = StringMatcherUtility.StringMatcher.getInstance()
        
        val appsList = mutableListOf<UniversalSearchResult.InAppSearch>()
        appsList.add(UniversalSearchResult.InAppSearch(null, query)) 
        
        val specificApps = activities.asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName
                val componentName = ComponentName(packageName, resolveInfo.activityInfo.name)
                val searchableInfo = searchManager.getSearchableInfo(componentName)
                
                val isLauncherApp = context.packageManager.getLaunchIntentForPackage(packageName) != null
                val isInternal = packageName == "com.google.android.googlequicksearchbox" || 
                    packageName == "com.android.vending" || 
                    packageName == context.packageName
                    
                if (isInternal || !isLauncherApp || searchableInfo == null) {
                    null
                } else {
                    try {
                        val title = resolveInfo.activityInfo.loadLabel(context.packageManager).toString()
                        val normalizedTitle = title.filter { it.isLetterOrDigit() }.lowercase()
                        val normalizedQuery = query.filter { it.isLetterOrDigit() }.lowercase()
                        
                        val score = when {
                            title.equals(query, ignoreCase = true) -> 100
                            normalizedTitle == normalizedQuery && normalizedQuery.isNotEmpty() -> 95
                            title.startsWith(query, ignoreCase = true) -> 80
                            normalizedTitle.startsWith(normalizedQuery) && normalizedQuery.isNotEmpty() -> 75
                            StringMatcherUtility.matches(query, title, matcher) -> 60
                            title.contains(query, ignoreCase = true) -> 40
                            normalizedTitle.contains(normalizedQuery) && normalizedQuery.isNotEmpty() -> 35
                            else -> 10
                        }
                        val user = Process.myUserHandle()
                        val searchIntent = Intent(Intent.ACTION_SEARCH)
                        searchIntent.setComponent(componentName)
                        
                        val appInfo = AppInfo(componentName, title, user, searchIntent)
                        Triple(appInfo, score, packageName)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            .sortedByDescending { it.second }
            .distinctBy { it.third }
            .take(4)
            .map { UniversalSearchResult.InAppSearch(it.first, query) }
            .toList()
        
        appsList.addAll(specificApps)
        return appsList
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private data class SearchLocalResults(
        val apps: List<UniversalSearchResult.App>,
        val appActions: UniversalSearchResult.AppActions?,
        val privateSpace: UniversalSearchResult.PrivateSpace?,
        val inAppSearches: List<UniversalSearchResult.InAppSearch>,
        val webActions: List<UniversalSearchResult.WebAction>
    )

    fun clear() {
        searchJob?.cancel()
        _searchState.value = UniversalSearchState(history = searchHistory)
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
    val searchWeb: Boolean = true,
    val showWebActions: Boolean = true,
    val showInAppSearch: Boolean = true
)

