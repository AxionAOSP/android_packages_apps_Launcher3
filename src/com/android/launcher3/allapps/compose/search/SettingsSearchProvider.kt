package com.android.launcher3.allapps.compose.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsSearchProvider(private val context: Context) {

    private val packageManager = context.packageManager

    suspend fun search(query: String, limit: Int = 5): List<UniversalSearchResult.Setting> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setPackage("com.android.settings")
            }
            
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            
            activities.asSequence()
                .filter { resolveInfo ->
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    label.contains(query, ignoreCase = true)
                }
                .take(limit)
                .map { resolveInfo ->
                    val title = resolveInfo.loadLabel(packageManager).toString()
                    val activityInfo = resolveInfo.activityInfo
                    val targetIntent = Intent().apply {
                        setClassName(activityInfo.packageName, activityInfo.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    UniversalSearchResult.Setting(activityInfo.name, title, targetIntent)
                }
                .toList()
                
        } catch (e: Exception) {
            emptyList()
        }
    }
}
