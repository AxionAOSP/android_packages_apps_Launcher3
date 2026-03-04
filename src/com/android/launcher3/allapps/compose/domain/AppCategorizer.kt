package com.android.launcher3.allapps.compose.domain

import android.content.Context
import android.content.pm.ApplicationInfo
import com.android.launcher3.R
import com.android.launcher3.allapps.compose.data.AppCategoryManager
import com.android.launcher3.allapps.compose.shared.model.AppCategory
import com.android.launcher3.model.data.AppInfo

fun categorizeApps(
    apps: List<AppInfo>,
    context: Context,
    categoryManager: AppCategoryManager
): List<AppCategory> {
    val pm = context.packageManager
    val configForceGamePackages = context.resources.getStringArray(R.array.config_categorize_force_game_packages).toSet()
    val overrides = categoryManager.overrides.value
    val customCats = categoryManager.customCategories.value

    val categoryMap = mutableMapOf<Int, MutableList<AppInfo>>()
    val customCategoryMap = mutableMapOf<Int, MutableList<AppInfo>>()

    apps.forEach { appInfo ->
        val component = appInfo.componentName?.flattenToString() ?: ""
        val override = overrides[component]

        if (override != null) {
            if (override >= AppCategoryManager.CUSTOM_ID_START) {
                customCategoryMap.getOrPut(override) { mutableListOf() }.add(appInfo)
            } else {
                categoryMap.getOrPut(override) { mutableListOf() }.add(appInfo)
            }
        } else {
            val pkg = appInfo.intent?.`package` ?: appInfo.componentName?.packageName ?: ""
            val androidCategory = when {
                configForceGamePackages.contains(pkg) -> ApplicationInfo.CATEGORY_GAME
                else -> try {
                    pm.getApplicationInfo(pkg, 0).category
                } catch (e: Exception) {
                    ApplicationInfo.CATEGORY_UNDEFINED
                }
            }
            val internalId = when (androidCategory) {
                ApplicationInfo.CATEGORY_GAME -> 1
                ApplicationInfo.CATEGORY_SOCIAL -> 2
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> 3
                ApplicationInfo.CATEGORY_NEWS -> 4
                ApplicationInfo.CATEGORY_MAPS -> 5
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> 6
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> 7
                else -> 8
            }
            categoryMap.getOrPut(internalId) { mutableListOf() }.add(appInfo)
        }
    }

    val systemCategories = listOf(
        1 to "Games", 2 to "Social", 3 to "Media", 4 to "News",
        5 to "Maps", 6 to "Productivity", 7 to "Accessibility", 8 to "Other"
    )

    val result = mutableListOf<AppCategory>()

    for ((id, name) in customCats) {
        val catApps = customCategoryMap[id]
        if (catApps != null && catApps.isNotEmpty()) {
            result.add(AppCategory(id, name, catApps, isCustom = true))
        }
    }

    for ((id, name) in systemCategories) {
        categoryMap[id]?.let { appList ->
            if (appList.isNotEmpty()) result.add(AppCategory(id, name, appList))
        }
    }

    return result.filter { it.apps.isNotEmpty() }
}
