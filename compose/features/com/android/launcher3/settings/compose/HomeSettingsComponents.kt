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
package com.android.launcher3.settings.compose

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.axion.compose.preferences.ClickablePreference
import com.android.axion.compose.preferences.SettingsType
import com.android.axion.compose.preferences.SliderPreference
import com.android.axion.compose.preferences.SwitchPreference
import com.android.axion.compose.preferences.rememberSettingInt
import com.android.axion.compose.preferences.rememberSettingsFlow
import com.android.launcher3.ConstantItem
import com.android.launcher3.ContextualItem
import com.android.launcher3.EncryptionType
import com.android.launcher3.Item
import com.android.launcher3.LauncherPrefChangeListener
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import kotlin.math.roundToInt

@Composable
internal fun PercentSliderPreference(
    item: ConstantItem<Int>,
    @StringRes titleRes: Int,
    min: Int = 50,
    max: Int = 150,
) {
    IntSliderPreference(
        item = item,
        titleRes = titleRes,
        min = min,
        max = max,
        defaultValue = 100,
        interval = 5,
        valueLabel = { stringResource(R.string.home_settings_percent_value, it) },
    )
}

@Composable
internal fun IntSliderPreference(
    item: ConstantItem<Int>,
    @StringRes titleRes: Int,
    min: Int,
    max: Int,
    defaultValue: Int,
    interval: Int = 1,
    resetValue: Int = defaultValue,
    valueOverride: (Int) -> Int = { it },
    valueLabel: @Composable (Int) -> String,
) {
    val preference = rememberLauncherPreference(item)
    IntSliderPreference(
        preference = preference,
        titleRes = titleRes,
        min = min,
        max = max,
        defaultValue = defaultValue,
        interval = interval,
        resetValue = resetValue,
        valueOverride = valueOverride,
        valueLabel = valueLabel,
    )
}

@Composable
internal fun IntSliderPreference(
    preference: PreferenceState<Int>,
    @StringRes titleRes: Int,
    min: Int,
    max: Int,
    defaultValue: Int,
    interval: Int = 1,
    resetValue: Int = defaultValue,
    valueOverride: (Int) -> Int = { it },
    valueLabel: @Composable (Int) -> String,
) {
    val value = valueOverride(preference.value).coerceIn(min, max)
    SliderPreference(
        title = stringResource(titleRes),
        summary = "",
        value = value.toFloat(),
        onValueChange = { preference.onChange(roundSliderValue(it, min, max, interval)) },
        onValueChangeFinished = {},
        valueRange = min.toFloat()..max.toFloat(),
        steps = ((max - min) / interval - 1).coerceAtLeast(0),
        displayValue = valueLabel(value),
        onReset = { preference.onChange(resetValue) },
    )
}

private fun roundSliderValue(value: Float, min: Int, max: Int, interval: Int): Int {
    return (min + ((value - min) / interval).roundToInt() * interval).coerceIn(min, max)
}

@Composable
internal fun CategoryPreference(
    @StringRes titleRes: Int,
    onClick: () -> Unit,
    @StringRes summaryRes: Int = 0,
    @DrawableRes iconRes: Int = 0,
) {
    ClickablePreference(
        title = stringResource(titleRes),
        summary = summaryRes.takeIf { it != 0 }?.let { stringResource(it) },
        customIcon = iconRes.takeIf { it != 0 }?.let { preferenceIcon(it) },
        onClick = onClick,
    )
}

@Composable
internal fun BooleanPreference(
    item: ConstantItem<Boolean>,
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int = 0,
    @StringRes summaryRes: Int = 0,
    @StringRes summaryOnRes: Int = 0,
    @StringRes summaryOffRes: Int = 0,
    onChanged: (Boolean) -> Unit = {},
) {
    val preference = rememberLauncherPreference(item)
    SwitchPreference(
        title = stringResource(titleRes),
        summary = preferenceSummary(
            checked = preference.value,
            summaryRes = summaryRes,
            summaryOnRes = summaryOnRes,
            summaryOffRes = summaryOffRes,
        ),
        checked = preference.value,
        onCheckedChange = {
            preference.onChange(it)
            onChanged(it)
        },
        customIcon = iconRes.takeIf { it != 0 }?.let { preferenceIcon(it) },
    )
}

@Composable
internal fun BooleanPreference(
    item: ContextualItem<Boolean>,
    @StringRes titleRes: Int,
    @DrawableRes iconRes: Int = 0,
    @StringRes summaryRes: Int = 0,
    @StringRes summaryOnRes: Int = 0,
    @StringRes summaryOffRes: Int = 0,
    onChanged: (Boolean) -> Unit = {},
) {
    val preference = rememberLauncherPreference(item)
    SwitchPreference(
        title = stringResource(titleRes),
        summary = preferenceSummary(
            checked = preference.value,
            summaryRes = summaryRes,
            summaryOnRes = summaryOnRes,
            summaryOffRes = summaryOffRes,
        ),
        checked = preference.value,
        onCheckedChange = {
            preference.onChange(it)
            onChanged(it)
        },
        customIcon = iconRes.takeIf { it != 0 }?.let { preferenceIcon(it) },
    )
}

@Composable
private fun preferenceSummary(
    checked: Boolean,
    @StringRes summaryRes: Int,
    @StringRes summaryOnRes: Int,
    @StringRes summaryOffRes: Int,
): String? {
    val summary = when {
        summaryOnRes != 0 && summaryOffRes != 0 -> if (checked) summaryOnRes else summaryOffRes
        summaryRes != 0 -> summaryRes
        else -> 0
    }
    return summary.takeIf { it != 0 }?.let { stringResource(it) }
}

@Composable
internal fun rememberPackageEnabled(packageName: String): Boolean {
    val context = LocalContext.current
    return remember(context, packageName) {
        context.getSystemService(LauncherApps::class.java)
            ?.isPackageEnabled(packageName, Process.myUserHandle()) == true
    }
}

@Composable
internal fun rememberResumeVersion(): Int {
    val lifecycleOwner = LocalLifecycleOwner.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                version++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return version
}

@Composable
internal fun <T : Any> rememberLauncherPreference(item: ConstantItem<T>): PreferenceState<T> {
    if (item.encryptionType == EncryptionType.SECURE_SETTINGS) {
        return rememberSecureSettingPreference(item)
    }
    return rememberLauncherPreference(
        item = item,
        read = { it.get(item) },
        write = { prefs, value -> prefs.put(item, value) },
    )
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun <T : Any> rememberSecureSettingPreference(item: ConstantItem<T>): PreferenceState<T> {
    val flow = rememberSettingsFlow(SettingsType.SECURE)
    if (item.defaultValue is Int) {
        val value by rememberSettingInt(
            key = item.sharedPrefKey,
            type = SettingsType.SECURE,
            default = item.defaultValue as Int,
        )
        return PreferenceState(value as T) { flow.putInt(item.sharedPrefKey, it as Int) }
    }
    val context = LocalContext.current
    val prefs = remember(context) { LauncherPrefs.get(context) }
    var value by remember(prefs, item) { mutableStateOf(prefs.get(item)) }
    DisposableEffect(prefs, item) {
        val listener = LauncherPrefChangeListener { key ->
            if (key == item.sharedPrefKey) {
                value = prefs.get(item)
            }
        }
        prefs.addListener(listener, item)
        onDispose { prefs.removeListener(listener, item) }
    }
    return PreferenceState(value) { newValue ->
        value = newValue
        prefs.put(item, newValue)
    }
}

@Composable
private fun <T : Any> rememberLauncherPreference(item: ContextualItem<T>): PreferenceState<T> {
    return rememberLauncherPreference(
        item = item,
        read = { it.get(item) },
        write = { prefs, value -> prefs.put(item, value) },
    )
}

@Composable
internal fun <T : Any> rememberLauncherPreference(
    item: Item,
    read: (LauncherPrefs) -> T,
    write: (LauncherPrefs, T) -> Unit,
): PreferenceState<T> {
    val context = LocalContext.current
    val prefs = remember(context) { LauncherPrefs.get(context) }
    var value by remember(prefs, item) { mutableStateOf(read(prefs)) }
    DisposableEffect(prefs, item) {
        val listener = LauncherPrefChangeListener { key ->
            if (key == item.sharedPrefKey) {
                value = read(prefs)
            }
        }
        prefs.addListener(listener, item)
        onDispose { prefs.removeListener(listener, item) }
    }
    return PreferenceState(value) { newValue ->
        value = newValue
        write(prefs, newValue)
    }
}

internal data class PreferenceState<T>(
    val value: T,
    val onChange: (T) -> Unit,
)

internal fun showToast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}

internal fun preferenceIcon(@DrawableRes iconRes: Int): (@Composable () -> Unit) = {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
    }
}
