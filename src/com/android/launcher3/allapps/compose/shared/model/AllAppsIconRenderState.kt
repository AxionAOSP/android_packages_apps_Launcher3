/*
 * Copyright 2025-2026 AxionOS
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

package com.android.launcher3.allapps.compose.shared.model

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.ThemeEngine
import android.provider.Settings
import androidx.compose.runtime.Immutable
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.ThemedIconSettings
import org.json.JSONObject

@Immutable
data class AllAppsIconRenderState(
    val uiMode: Int = 0,
    val version: Int = 0,
    val iconShape: IconShapeRenderState = IconShapeRenderState(),
    val iconPack: IconPackRenderState = IconPackRenderState(),
    val themedIcons: ThemedIconRenderState = ThemedIconRenderState(),
    val customIcons: CustomIconRenderState = CustomIconRenderState(),
) {
    val themed: Boolean
        get() = themedIcons.enabled

    val cacheKey: String
        get() = listOf(
            uiMode,
            version,
            iconShape.cacheKey,
            iconPack.cacheKey,
            themedIcons.cacheKey,
            customIcons.cacheKey,
        ).joinToString("|")

    companion object {
        val observedSecureSettings = listOf(
            KEY_THEME_ENGINE_DATA,
            KEY_THEMED_ICONS,
            KEY_THEMED_ICON_PACK,
            ThemedIconSettings.KEY_ICON_SCALE,
            ThemedIconSettings.KEY_BACKGROUND_COLOR,
            ThemedIconSettings.KEY_FOREGROUND_COLOR,
            ThemedIconSettings.KEY_COLOR_PRESET,
            KEY_ICON_OVERRIDES,
        )

        fun from(
            context: Context,
            themedIconsEnabled: Boolean,
            version: Int,
            uiMode: Int = context.resources.configuration.uiMode.let {
                it and Configuration.UI_MODE_NIGHT_MASK
            },
        ): AllAppsIconRenderState {
            val resolver = context.contentResolver
            val iconState = ThemeManager.INSTANCE.get(context).iconState
            val themeEngineData =
                Settings.Secure.getString(resolver, KEY_THEME_ENGINE_DATA).orEmpty()
            val iconPackPackage = ThemeEngine.getInstance(context)?.getIconPackPackage().orEmpty()
            val themedIconPackPackage =
                Settings.Secure.getString(resolver, KEY_THEMED_ICON_PACK).orEmpty()
            val customIconOverrides =
                Settings.Secure.getString(resolver, KEY_ICON_OVERRIDES).orEmpty()

            return AllAppsIconRenderState(
                uiMode = uiMode,
                version = version,
                iconShape = IconShapeRenderState(
                    mask = iconState.iconMask,
                    isCircle = iconState.isCircle,
                ),
                iconPack = IconPackRenderState.from(
                    context = context,
                    packageName = iconPackPackage,
                    sourceHash = themeEngineData.hashCode(),
                ),
                themedIcons = ThemedIconRenderState(
                    enabled = themedIconsEnabled,
                    themeKey = iconState.toUniqueId(),
                    iconPack = IconPackRenderState.from(
                        context = context,
                        packageName = themedIconPackPackage,
                    ),
                ),
                customIcons = CustomIconRenderState.from(context, customIconOverrides),
            )
        }
    }
}

@Immutable
data class IconShapeRenderState(
    val mask: String = "",
    val isCircle: Boolean = false,
) {
    val cacheKey: String
        get() = "$mask,$isCircle"
}

@Immutable
data class IconPackRenderState(
    val packageName: String = "",
    val versionCode: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val sourceHash: Int = 0,
) {
    val cacheKey: String
        get() = "$packageName,$versionCode,$lastUpdateTime,$sourceHash"

    companion object {
        fun from(context: Context, packageName: String, sourceHash: Int = 0): IconPackRenderState {
            if (packageName.isEmpty()) return IconPackRenderState(sourceHash = sourceHash)
            return try {
                context.packageManager.getPackageInfo(packageName, 0).let {
                    IconPackRenderState(
                        packageName = packageName,
                        versionCode = it.longVersionCode,
                        lastUpdateTime = it.lastUpdateTime,
                        sourceHash = sourceHash,
                    )
                }
            } catch (_: NameNotFoundException) {
                IconPackRenderState(packageName = packageName, sourceHash = sourceHash)
            }
        }
    }
}

@Immutable
data class ThemedIconRenderState(
    val enabled: Boolean = false,
    val themeKey: String = "",
    val iconPack: IconPackRenderState = IconPackRenderState(),
) {
    val cacheKey: String
        get() = "$enabled,$themeKey,${iconPack.cacheKey}"
}

@Immutable
data class CustomIconRenderState(
    val overridesHash: Int = 0,
    val iconPacks: List<IconPackRenderState> = emptyList(),
) {
    val cacheKey: String
        get() = "$overridesHash,${iconPacks.joinToString(";") { it.cacheKey }}"

    companion object {
        fun from(context: Context, overrides: String): CustomIconRenderState =
            CustomIconRenderState(
                overridesHash = overrides.hashCode(),
                iconPacks = overridePackPackages(overrides).map {
                    IconPackRenderState.from(context, it)
                },
            )

        private fun overridePackPackages(overrides: String): List<String> {
            if (overrides.isBlank()) return emptyList()
            return try {
                val result = mutableSetOf<String>()
                val root = JSONObject(overrides)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val pack = root.optJSONObject(keys.next())?.optString(FIELD_PACK).orEmpty()
                    if (pack.isNotEmpty()) result.add(pack)
                }
                result.sorted()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}

private const val KEY_THEME_ENGINE_DATA = "theme_engine_data"
private const val KEY_THEMED_ICONS = "themed_icons"
private const val KEY_THEMED_ICON_PACK = "themed_icon_pack"
private const val KEY_ICON_OVERRIDES = "launcher_icon_overrides"
private const val FIELD_PACK = "pack"
