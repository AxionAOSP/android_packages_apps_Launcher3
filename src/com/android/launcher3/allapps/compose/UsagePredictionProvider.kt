/*
 * Copyright (C) 2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.allapps.compose

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.android.launcher3.model.data.AppInfo
import java.util.Calendar

class UsagePredictionProvider(private val context: Context) {

    fun getPredictions(allApps: List<AppInfo>, limit: Int): List<AppInfo> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val endTime = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startTime = calendar.timeInMillis

        val stats = try {
            usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        if (stats.isEmpty()) return emptyList()

        val packageUsage = stats.values.asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .toList()

        val predictions = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()
        val myPackage = context.packageName

        for (packageName in packageUsage) {
            if (packageName == myPackage) continue
            if (predictions.size >= limit) break
            
            val app = allApps.find { it.componentName?.packageName == packageName }
            if (app != null && !seenPackages.contains(packageName)) {
                predictions.add(app)
                seenPackages.add(packageName)
            }
        }

        return predictions
    }
}
