/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import com.android.launcher3.GridType.Companion.GRID_TYPE_ANY
import com.android.launcher3.InvariantDeviceProfile.GRID_NAME_PREFS_KEY
import com.android.launcher3.InvariantDeviceProfile.NON_FIXED_LANDSCAPE_GRID_NAME_PREFS_KEY
import com.android.launcher3.LauncherFiles.DEVICE_PREFERENCES_KEY
import com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.DeviceGridState
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.provider.RestoreDbTask.FIRST_LOAD_AFTER_RESTORE_KEY
import com.android.launcher3.settings.SettingsActivity
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DisplayController
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject

/**
 * Manages Launcher [SharedPreferences] through [Item] instances.
 *
 * TODO(b/262721340): Replace all direct SharedPreference refs with LauncherPrefs / Item methods.
 */
@LauncherAppSingleton
open class LauncherPrefs
@Inject
constructor(@ApplicationContext private val encryptedContext: Context) {

    private val deviceProtectedSharedPrefs: SharedPreferences by lazy {
        encryptedContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(BOOT_AWARE_PREFS_KEY, MODE_PRIVATE)
    }
    private val settingsObserverHandler = Handler(Looper.getMainLooper())
    private val settingsObservers = ConcurrentHashMap<SettingsObserverKey, ContentObserver>()

    open protected fun getSharedPrefs(item: Item): SharedPreferences =
        item.run {
            if (encryptionType == EncryptionType.DEVICE_PROTECTED) deviceProtectedSharedPrefs
            else encryptedContext.getSharedPreferences(sharedPrefFile, MODE_PRIVATE)
        }

    @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
    val backedUpPrefs: SharedPreferences
        get() = getSharedPrefs(GRID_NAME)

    @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
    val devicePrefs: SharedPreferences
        get() = getSharedPrefs(IS_FIRST_LOAD_AFTER_RESTORE)

    /** Returns the value with type [T] for [item]. */
    fun <T> get(item: ContextualItem<T>): T =
        getInner(item, item.defaultValueFromContext(encryptedContext))

    /** Returns the value with type [T] for [item]. */
    fun <T> get(item: ConstantItem<T>): T = getInner(item, item.defaultValue)

    /**
     * Retrieves the value for an [Item] from [SharedPreferences]. It handles method typing via the
     * default value type, and will throw an error if the type of the item provided is not a
     * `String`, `Boolean`, `Float`, `Int`, `Long`, or `Set<String>`.
     */
    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    private fun <T> getInner(item: Item, default: T): T {
        if (item.encryptionType == EncryptionType.SECURE_SETTINGS) {
            return getSecureSetting(item, default)
        }
        val sp = getSharedPrefs(item)
        return when {
            item.type == String::class.java -> sp.getString(item.sharedPrefKey, default as? String)
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                sp.getBoolean(item.sharedPrefKey, default as Boolean)
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                sp.getInt(item.sharedPrefKey, default as Int)
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                sp.getFloat(item.sharedPrefKey, default as Float)
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                sp.getLong(item.sharedPrefKey, default as Long)
            Set::class.java.isAssignableFrom(item.type) ->
                sp.getStringSet(item.sharedPrefKey, default as? Set<String>)
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type}" + " is not compatible with sharedPref methods"
                )
        }
            as T
    }

    /**
     * Stores each of the values provided in `SharedPreferences` according to the configuration
     * contained within the associated items provided. Internally, it uses apply, so the caller
     * cannot assume that the values that have been put are immediately available for use.
     *
     * The forEach loop is necessary here since there is 1 `SharedPreference.Editor` returned from
     * prepareToPutValue(itemsToValues) for every distinct `SharedPreferences` file present in the
     * provided item configurations.
     */
    fun put(vararg itemsToValues: Pair<Item, Any>) {
        putSecureSettings(itemsToValues)
        prepareToPutValues(itemsToValues).forEach { it.apply() }
    }

    /** See referenced `put` method above. */
    fun <T : Any> put(item: Item, value: T): Unit = put(item.to(value))

    /**
     * Synchronously stores all the values provided according to their associated Item
     * configuration.
     */
    fun putSync(vararg itemsToValues: Pair<Item, Any>) {
        putSecureSettings(itemsToValues)
        prepareToPutValues(itemsToValues).forEach { it.commit() }
    }

    /**
     * Updates the values stored in `SharedPreferences` for each corresponding Item-value pair. If
     * the item is boot aware, this method updates both the boot aware and the encrypted files. This
     * is done because: 1) It allows for easy roll-back if the data is already in encrypted prefs
     * and we need to turn off the boot aware data feature & 2) It simplifies Backup/Restore, which
     * already points to encrypted storage.
     *
     * Returns a list of editors with all transactions added so that the caller can determine to use
     * .apply() or .commit()
     */
    private fun prepareToPutValues(
        updates: Array<out Pair<Item, Any>>
    ): List<SharedPreferences.Editor> {
        val updatesPerPrefFile = updates
            .filterNot { it.first.encryptionType == EncryptionType.SECURE_SETTINGS }
            .groupBy { getSharedPrefs(it.first) }
            .toMap()

        return updatesPerPrefFile.map { (sharedPref, itemList) ->
            sharedPref.edit().apply { itemList.forEach { (item, value) -> putValue(item, value) } }
        }
    }

    /**
     * Handles adding values to `SharedPreferences` regardless of type. This method is especially
     * helpful for updating `SharedPreferences` values for `List<<Item>Any>` that have multiple
     * types of Item values.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun SharedPreferences.Editor.putValue(
        item: Item,
        value: Any?,
    ): SharedPreferences.Editor =
        when {
            item.type == String::class.java -> putString(item.sharedPrefKey, value as? String)
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                putBoolean(item.sharedPrefKey, value as Boolean)
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                putInt(item.sharedPrefKey, value as Int)
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                putFloat(item.sharedPrefKey, value as Float)
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                putLong(item.sharedPrefKey, value as Long)
            Set::class.java.isAssignableFrom(item.type) ->
                putStringSet(item.sharedPrefKey, value as? Set<String>)
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type} is not compatible with sharedPref methods"
                )
        }


    @Suppress("IMPLICIT_CAST_TO_ANY", "UNCHECKED_CAST")
    private fun <T> getSecureSetting(item: Item, default: T): T {
        val resolver = encryptedContext.contentResolver
        val key = item.sharedPrefKey
        return when {
            item.type == String::class.java -> Settings.Secure.getString(resolver, key)
                ?: default as? String
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                Settings.Secure.getInt(resolver, key, if (default as Boolean) 1 else 0) != 0
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                Settings.Secure.getInt(resolver, key, default as Int)
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                Settings.Secure.getFloat(resolver, key, default as Float)
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                Settings.Secure.getLong(resolver, key, default as Long)
            Set::class.java.isAssignableFrom(item.type) ->
                Settings.Secure.getString(resolver, key)?.let {
                    decodeStringSet(it, default as? Set<String>)
                } ?: default
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type} is not compatible with secure settings"
                )
        }
            as T
    }

    private fun putSecureSettings(updates: Array<out Pair<Item, Any>>) {
        updates
            .filter { it.first.encryptionType == EncryptionType.SECURE_SETTINGS }
            .forEach { (item, value) -> putSecureSetting(item, value) }
    }

    private fun putSecureSetting(item: Item, value: Any) {
        val resolver = encryptedContext.contentResolver
        val key = item.sharedPrefKey
        when {
            item.type == String::class.java -> Settings.Secure.putString(
                resolver,
                key,
                value as String,
            )
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                Settings.Secure.putInt(resolver, key, if (value as Boolean) 1 else 0)
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                Settings.Secure.putInt(resolver, key, value as Int)
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                Settings.Secure.putFloat(resolver, key, value as Float)
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                Settings.Secure.putLong(resolver, key, value as Long)
            Set::class.java.isAssignableFrom(item.type) ->
                Settings.Secure.putString(resolver, key, encodeStringSet(value as Set<*>))
            else ->
                throw IllegalArgumentException(
                    "item type: ${item.type} is not compatible with secure settings"
                )
        }
    }

    private fun removeSecureSettings(items: Array<out Item>) {
        items
            .filter { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .forEach {
                Settings.Secure.putString(encryptedContext.contentResolver, it.sharedPrefKey, null)
            }
    }

    private fun registerSecureSettingsObserver(listener: LauncherPrefChangeListener, item: Item) {
        val observerKey = SettingsObserverKey(listener, item.sharedPrefKey)
        if (settingsObservers.containsKey(observerKey)) {
            return
        }
        val observer = object : ContentObserver(settingsObserverHandler) {
            override fun onChange(selfChange: Boolean) {
                listener.onPrefChanged(item.sharedPrefKey)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                listener.onPrefChanged(item.sharedPrefKey)
            }
        }
        if (settingsObservers.putIfAbsent(observerKey, observer) == null) {
            encryptedContext.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(item.sharedPrefKey),
                false,
                observer,
            )
        }
    }

    private fun unregisterSecureSettingsObserver(listener: LauncherPrefChangeListener, item: Item) {
        settingsObservers.remove(SettingsObserverKey(listener, item.sharedPrefKey))?.let {
            encryptedContext.contentResolver.unregisterContentObserver(it)
        }
    }

    private fun decodeStringSet(value: String, default: Set<String>?): Set<String>? =
        try {
            val array = JSONArray(value)
            val set = LinkedHashSet<String>(array.length())
            for (i in 0 until array.length()) {
                set.add(array.optString(i))
            }
            set
        } catch (e: JSONException) {
            default
        }

    private fun encodeStringSet(value: Set<*>): String {
        val array = JSONArray()
        value.forEach { array.put(it?.toString().orEmpty()) }
        return array.toString()
    }

    /**
     * After calling this method, the listener will be notified of any future updates to the
     * `SharedPreferences` files associated with the provided list of items. The listener will need
     * to filter update notifications so they don't activate for non-relevant updates.
     */
    fun addListener(listener: LauncherPrefChangeListener, vararg items: Item) {
        items
            .filterNot { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .map { getSharedPrefs(it) }
            .distinct()
            .forEach { it.registerOnSharedPreferenceChangeListener(listener) }
        items
            .filter { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .distinctBy { it.sharedPrefKey }
            .forEach { registerSecureSettingsObserver(listener, it) }
    }

    /**
     * Stops the listener from getting notified of any more updates to any of the
     * `SharedPreferences` files associated with any of the provided list of [Item].
     */
    fun removeListener(listener: LauncherPrefChangeListener, vararg items: Item) {
        // If a listener is not registered to a SharedPreference, unregistering it does nothing
        items
            .filterNot { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .map { getSharedPrefs(it) }
            .distinct()
            .forEach { it.unregisterOnSharedPreferenceChangeListener(listener) }
        items
            .filter { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .distinctBy { it.sharedPrefKey }
            .forEach { unregisterSecureSettingsObserver(listener, it) }
    }

    /**
     * Checks if all the provided [Item] have values stored in their corresponding
     * `SharedPreferences` files.
     */
    fun has(vararg items: Item): Boolean {
        items
            .filterNot { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .groupBy { getSharedPrefs(it) }
            .forEach { (prefs, itemsSublist) ->
                if (!itemsSublist.none { !prefs.contains(it.sharedPrefKey) }) return false
            }
        return items
            .filter { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .none {
                Settings.Secure.getString(
                    encryptedContext.contentResolver,
                    it.sharedPrefKey,
                ) == null
            }
    }

    /**
     * Asynchronously removes the [Item]'s value from its corresponding `SharedPreferences` file.
     */
    fun remove(vararg items: Item) {
        removeSecureSettings(items)
        prepareToRemove(items).forEach { it.apply() }
    }

    /** Synchronously removes the [Item]'s value from its corresponding `SharedPreferences` file. */
    fun removeSync(vararg items: Item) {
        removeSecureSettings(items)
        prepareToRemove(items).forEach { it.commit() }
    }

    /**
     * Removes the key value pairs stored in `SharedPreferences` for each corresponding Item. If the
     * item is boot aware, this method removes the data from both the boot aware and encrypted
     * files.
     *
     * @return a list of editors with all transactions added so that the caller can determine to use
     *   .apply() or .commit()
     */
    private fun prepareToRemove(items: Array<out Item>): List<SharedPreferences.Editor> {
        val itemsPerFile = items
            .filterNot { it.encryptionType == EncryptionType.SECURE_SETTINGS }
            .groupBy { getSharedPrefs(it) }
            .toMap()

        return itemsPerFile.map { (prefs, items) ->
            prefs.edit().also { editor ->
                items.forEach { item -> editor.remove(item.sharedPrefKey) }
            }
        }
    }

    companion object {
        @VisibleForTesting const val BOOT_AWARE_PREFS_KEY = "boot_aware_prefs"

        @JvmField val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getLauncherPrefs)

        @JvmStatic fun get(context: Context): LauncherPrefs = INSTANCE.get(context)

        const val TASKBAR_PINNING_KEY = "TASKBAR_PINNING_KEY"
        const val TASKBAR_PINNING_DESKTOP_MODE_KEY = "TASKBAR_PINNING_DESKTOP_MODE_KEY"

        @JvmField val PROMISE_ICON_IDS = nonRestorableItem(InstallSessionHelper.PROMISE_ICON_IDS, "")
        @JvmField val WORK_EDU_STEP = backedUpItem("showed_work_profile_edu", 0)
        @JvmField
        val WORKSPACE_SIZE =
            backedUpItem(DeviceGridState.KEY_WORKSPACE_SIZE, "", EncryptionType.ENCRYPTED)
        @JvmField
        val HOTSEAT_COUNT =
            backedUpItem(DeviceGridState.KEY_HOTSEAT_COUNT, -1, EncryptionType.ENCRYPTED)
        @JvmField
        val TASKBAR_PINNING =
            backedUpItem(TASKBAR_PINNING_KEY, false, EncryptionType.DEVICE_PROTECTED)
        @JvmField
        val TASKBAR_PINNING_IN_DESKTOP_MODE =
            backedUpItem(TASKBAR_PINNING_DESKTOP_MODE_KEY, true, EncryptionType.DEVICE_PROTECTED)

        @JvmField
        val DEVICE_TYPE =
            backedUpItem(
                DeviceGridState.KEY_DEVICE_TYPE,
                InvariantDeviceProfile.TYPE_PHONE,
                EncryptionType.ENCRYPTED,
            )
        @JvmField
        val DB_FILE = backedUpItem(DeviceGridState.KEY_DB_FILE, "", EncryptionType.ENCRYPTED)
        @JvmField
        val GRID_TYPE =
            backedUpItem(DeviceGridState.KEY_GRID_TYPE, GRID_TYPE_ANY, EncryptionType.ENCRYPTED)
        @JvmField
        val RESTORE_DEVICE =
            backedUpItem(
                RestoreDbTask.RESTORED_DEVICE_TYPE,
                InvariantDeviceProfile.TYPE_PHONE,
                EncryptionType.ENCRYPTED,
            )
        @JvmField
        val NO_DB_FILES_RESTORED =
            nonRestorableItem("no_db_files_restored", false, EncryptionType.DEVICE_PROTECTED)
        @JvmField
        val IS_FIRST_LOAD_AFTER_RESTORE =
            nonRestorableItem(FIRST_LOAD_AFTER_RESTORE_KEY, false, EncryptionType.ENCRYPTED)
        @JvmField val APP_WIDGET_IDS = backedUpItem(RestoreDbTask.APPWIDGET_IDS, "")
        @JvmField val OLD_APP_WIDGET_IDS = backedUpItem(RestoreDbTask.APPWIDGET_OLD_IDS, "")

        @JvmField
        val GRID_NAME =
            ConstantItem(
                GRID_NAME_PREFS_KEY,
                isBackedUp = true,
                defaultValue = null,
                encryptionType = EncryptionType.ENCRYPTED,
                type = String::class.java,
            )
        @JvmField
        val ALLOW_ROTATION =
            backedUpItem(RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY, Boolean::class.java) {
                RotationHelper.getAllowRotationDefaultValue(DisplayController.INSTANCE.get(it).info)
            }

        @JvmField
        val FIXED_LANDSCAPE_MODE = backedUpItem(SettingsActivity.FIXED_LANDSCAPE_MODE, false)

        @JvmField
        val NON_FIXED_LANDSCAPE_GRID_NAME =
            ConstantItem(
                NON_FIXED_LANDSCAPE_GRID_NAME_PREFS_KEY,
                isBackedUp = true,
                defaultValue = null,
                encryptionType = EncryptionType.ENCRYPTED,
                type = String::class.java,
            )

        // Preferences for widget configurations
        @JvmField
        val RECONFIGURABLE_WIDGET_EDUCATION_TIP_SEEN =
            backedUpItem("launcher.reconfigurable_widget_education_tip_seen", false)

        @JvmStatic
        fun <T> backedUpItem(
            sharedPrefKey: String,
            defaultValue: T,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
        ): ConstantItem<T> =
            ConstantItem(sharedPrefKey, isBackedUp = true, defaultValue, encryptionType)

        @JvmStatic
        fun <T> backedUpItem(
            sharedPrefKey: String,
            type: Class<out T>,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
            defaultValueFromContext: (c: Context) -> T,
        ): ContextualItem<T> =
            ContextualItem(
                sharedPrefKey,
                isBackedUp = true,
                defaultValueFromContext,
                encryptionType,
                type,
            )

        @JvmStatic
        fun <T> nonRestorableItem(
            sharedPrefKey: String,
            defaultValue: T,
            encryptionType: EncryptionType = EncryptionType.ENCRYPTED,
        ): ConstantItem<T> =
            ConstantItem(sharedPrefKey, isBackedUp = false, defaultValue, encryptionType)

        @Deprecated("Don't use shared preferences directly. Use other LauncherPref methods.")
        @JvmStatic
        fun getPrefs(context: Context) = INSTANCE[context].backedUpPrefs
    }
}

abstract class Item {
    abstract val sharedPrefKey: String
    abstract val isBackedUp: Boolean
    abstract val type: Class<*>
    abstract val encryptionType: EncryptionType
    val sharedPrefFile: String
        get() = if (isBackedUp) SHARED_PREFERENCES_KEY else DEVICE_PREFERENCES_KEY

    fun <T> to(value: T): Pair<Item, T> = Pair(this, value)
}

data class ConstantItem<T>(
    override val sharedPrefKey: String,
    override val isBackedUp: Boolean,
    val defaultValue: T,
    override val encryptionType: EncryptionType,
    // The default value can be null. If so, the type needs to be explicitly stated, or else NPE
    override val type: Class<out T> = defaultValue!!::class.java,
) : Item() {

    fun get(c: Context): T = LauncherPrefs.get(c).get(this)
}

data class ContextualItem<T>(
    override val sharedPrefKey: String,
    override val isBackedUp: Boolean,
    private val defaultSupplier: (c: Context) -> T,
    override val encryptionType: EncryptionType,
    override val type: Class<out T>,
) : Item() {
    private var default: T? = null

    fun defaultValueFromContext(context: Context): T {
        if (default == null) {
            default = defaultSupplier(context)
        }
        return default!!
    }

    fun get(c: Context): T = LauncherPrefs.get(c).get(this)
}

enum class EncryptionType {
    ENCRYPTED,
    DEVICE_PROTECTED,
    SECURE_SETTINGS,
}

private data class SettingsObserverKey(
    val listener: LauncherPrefChangeListener,
    val key: String,
)

/**
 * LauncherPrefs which delegates all lookup to [prefs] but uses the real prefs for initial values
 */
class ProxyPrefs(context: Context, private val prefs: SharedPreferences) : LauncherPrefs(context) {

    private val copiedPrefs = ConcurrentHashMap<SharedPreferences, Boolean>()

    override fun getSharedPrefs(item: Item): SharedPreferences {
        val originalPrefs = super.getSharedPrefs(item)
        // Copy all existing values, when the pref is accessed for the first time
        copiedPrefs.computeIfAbsent(originalPrefs) { op ->
            val editor = prefs.edit()
            op.all.forEach { (key, value) ->
                if (value != null) {
                    editor.putValue(backedUpItem(key, value), value)
                }
            }
            editor.commit()
        }
        return prefs
    }
}
