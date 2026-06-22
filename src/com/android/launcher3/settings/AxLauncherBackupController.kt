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
package com.android.launcher3.settings

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import com.android.launcher3.ConstantItem
import com.android.launcher3.ContextualItem
import com.android.launcher3.Item
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.concurrent.annotations.ThreadPool
import com.android.launcher3.concurrent.annotations.Ui
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherBaseAppComponent
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.LayoutImportExportHelper
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONException

@LauncherAppSingleton
class AxLauncherBackupController
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val layoutImportExportHelper: LayoutImportExportHelper,
    @ThreadPool private val ioExecutor: ExecutorService,
    @Ui private val uiExecutor: ExecutorService,
) {

    fun exportTo(uri: Uri, callback: (Boolean) -> Unit) {
        layoutImportExportHelper.exportModelDbAsXml { layoutXml ->
            ioExecutor.execute {
                dispatch(callback, writeLayout(uri, layoutXml))
            }
        }
    }

    fun importFrom(uri: Uri, callback: (Boolean) -> Unit) {
        ioExecutor.execute {
            dispatch(callback, importLayout(uri))
        }
    }

    private fun writeLayout(uri: Uri, layoutXml: String): Boolean {
        return try {
            val stream = context.contentResolver.openOutputStream(uri) ?: return false
            stream.use { it.write(createBackupXml(layoutXml).toByteArray(StandardCharsets.UTF_8)) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export launcher layout", e)
            false
        }
    }

    private fun importLayout(uri: Uri): Boolean {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return false
            val data = stream.use { it.readAllBytes() }
            importBackup(data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import launcher layout", e)
            false
        }
    }

    private fun importBackup(data: ByteArray) {
        val backup = parseBackup(data.toString(StandardCharsets.UTF_8))
        if (backup == null) {
            layoutImportExportHelper.importModelFromXml(data)
            return
        }
        restoreSettings(backup.settings)
        layoutImportExportHelper.importModelFromXml(backup.layoutXml)
    }

    private fun createBackupXml(layoutXml: String): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument(StandardCharsets.UTF_8.name(), true)
        serializer.startTag(null, TAG_BACKUP)
        serializer.attribute(null, ATTR_VERSION, BACKUP_VERSION.toString())
        serializer.startTag(null, TAG_SETTINGS)
        val prefs = LauncherPrefs.get(context)
        LauncherPrefsExt.AXION_SETTINGS_BACKUP_ITEMS.forEach { item ->
            val value = readSetting(prefs, item) ?: return@forEach
            serializer.startTag(null, TAG_SETTING)
            serializer.attribute(null, ATTR_KEY, item.sharedPrefKey)
            serializer.text(value)
            serializer.endTag(null, TAG_SETTING)
        }
        serializer.endTag(null, TAG_SETTINGS)
        serializer.startTag(null, TAG_LAYOUT)
        serializer.text(layoutXml)
        serializer.endTag(null, TAG_LAYOUT)
        serializer.endTag(null, TAG_BACKUP)
        serializer.endDocument()
        return writer.toString()
    }

    private fun parseBackup(xml: String): LauncherBackup? {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var type = parser.eventType
        while (type != org.xmlpull.v1.XmlPullParser.START_TAG &&
            type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            type = parser.next()
        }
        if (type != org.xmlpull.v1.XmlPullParser.START_TAG || parser.name != TAG_BACKUP) {
            return null
        }
        val settings = mutableMapOf<String, String>()
        var layoutXml = ""
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) {
                continue
            }
            when (parser.name) {
                TAG_SETTING -> {
                    val key = parser.getAttributeValue(null, ATTR_KEY)
                    val value = parser.nextText()
                    if (!key.isNullOrEmpty()) {
                        settings[key] = value
                    }
                }
                TAG_LAYOUT -> layoutXml = parser.nextText()
            }
        }
        return LauncherBackup(layoutXml, settings)
    }

    private fun restoreSettings(settings: Map<String, String>) {
        val prefs = LauncherPrefs.get(context)
        LauncherPrefsExt.AXION_SETTINGS_BACKUP_ITEMS.forEach { item ->
            settings[item.sharedPrefKey]?.let { restoreSetting(prefs, item, it) }
        }
    }

    private fun readSetting(prefs: LauncherPrefs, item: Item): String? {
        val value = when (item) {
            is ConstantItem<*> -> prefs.get(item)
            is ContextualItem<*> -> prefs.get(item)
            else -> null
        }
        return value?.let(::settingToString)
    }

    private fun settingToString(value: Any): String =
        if (value is Set<*>) {
            encodeStringSet(value)
        } else {
            value.toString()
        }

    private fun restoreSetting(prefs: LauncherPrefs, item: Item, value: String) {
        parseSetting(item, value)?.let { prefs.put(item, it) }
    }

    private fun parseSetting(item: Item, value: String): Any? =
        when {
            item.type == String::class.java -> value
            item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java ->
                parseBoolean(value)
            item.type == Int::class.java || item.type == java.lang.Integer::class.java ->
                value.toIntOrNull()
            item.type == Float::class.java || item.type == java.lang.Float::class.java ->
                value.toFloatOrNull()
            item.type == Long::class.java || item.type == java.lang.Long::class.java ->
                value.toLongOrNull()
            Set::class.java.isAssignableFrom(item.type) -> decodeStringSet(value)
            else -> null
        }

    private fun parseBoolean(value: String): Boolean? =
        when (value) {
            "true", "1" -> true
            "false", "0" -> false
            else -> null
        }

    private fun encodeStringSet(value: Set<*>): String {
        val array = JSONArray()
        value.forEach { array.put(it?.toString().orEmpty()) }
        return array.toString()
    }

    private fun decodeStringSet(value: String): Set<String>? =
        try {
            val array = JSONArray(value)
            val set = LinkedHashSet<String>(array.length())
            for (i in 0 until array.length()) {
                set.add(array.optString(i))
            }
            set
        } catch (e: JSONException) {
            null
        }

    private fun dispatch(callback: (Boolean) -> Unit, success: Boolean) {
        uiExecutor.execute { callback(success) }
    }

    private data class LauncherBackup(
        val layoutXml: String,
        val settings: Map<String, String>,
    )

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherBaseAppComponent::getLauncherBackupController)

        private const val TAG = "AxLauncherBackup"
        private const val TAG_BACKUP = "ax-launcher-backup"
        private const val TAG_SETTINGS = "settings"
        private const val TAG_SETTING = "setting"
        private const val TAG_LAYOUT = "layout"
        private const val ATTR_KEY = "key"
        private const val ATTR_VERSION = "version"
        private const val BACKUP_VERSION = 1
    }
}
