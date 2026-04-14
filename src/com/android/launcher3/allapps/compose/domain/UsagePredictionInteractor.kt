package com.android.launcher3.allapps.compose.domain

import android.app.usage.UsageStatsManager
import android.content.Context
import com.android.launcher3.model.data.AppInfo
import java.util.Calendar

class UsagePredictionInteractor(private val context: Context) {

    private var cachedPackageUsage: List<String>? = null
    private var cachedAt: Long = 0L

    fun getPredictions(allApps: List<AppInfo>, limit: Int): List<AppInfo> {
        val packageUsage = getOrRefreshPackageUsage() ?: return emptyList()
        if (packageUsage.isEmpty()) return emptyList()

        val predictions = mutableListOf<AppInfo>()
        val seenPackages = HashSet<String>()
        val myPackage = context.packageName

        for (packageName in packageUsage) {
            if (packageName == myPackage) continue
            if (predictions.size >= limit) break

            val app = allApps.find { it.componentName?.packageName == packageName }
            if (app != null && seenPackages.add(packageName)) {
                predictions.add(app)
            }
        }

        return predictions
    }

    private fun getOrRefreshPackageUsage(): List<String>? {
        val now = System.currentTimeMillis()
        val cached = cachedPackageUsage
        if (cached != null && now - cachedAt < CACHE_TTL_MS) {
            return cached
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -USAGE_WINDOW_DAYS)
        val startTime = calendar.timeInMillis

        val stats = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, now)
        } catch (e: Exception) {
            null
        } ?: return null

        val packageUsage = stats.values.asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .toList()

        cachedPackageUsage = packageUsage
        cachedAt = now
        return packageUsage
    }

    companion object {
        private const val CACHE_TTL_MS = 5L * 60L * 1000L
        private const val USAGE_WINDOW_DAYS = 2
    }
}
