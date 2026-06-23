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
package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_FOLDERS;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.pm.UserCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AllAppsFolderStore {

    public static final int NO_FOLDER_ID = -1;

    private static final String TAG = "AllAppsFolderStore";
    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_APPS = "apps";

    private AllAppsFolderStore() { }

    public static String encodeAppKey(Context context, ComponentName componentName,
            UserHandle user) {
        if (componentName == null || user == null) {
            return null;
        }
        return componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(context).getSerialNumberForUser(user);
    }

    public static List<AllAppsFolderInfo> getFolders(Context context, List<AppInfo> apps) {
        return getFolders(context, apps, ALL_APPS_FOLDERS);
    }

    static List<AllAppsFolderInfo> getFolders(Context context, List<AppInfo> apps,
            ConstantItem<String> item) {
        Map<String, AppInfo> appMap = new HashMap<>();
        for (AppInfo appInfo : apps) {
            String key = PinnedApps.encode(context, appInfo);
            if (key != null) {
                appMap.put(key, appInfo);
            }
        }

        JSONArray folders = readFolders(context, item);
        List<AllAppsFolderInfo> folderInfos = new ArrayList<>();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            JSONArray folderApps = folder == null ? null : folder.optJSONArray(KEY_APPS);
            if (folder == null || folderApps == null) {
                continue;
            }
            List<AppInfo> folderAppInfos = new ArrayList<>();
            for (int j = 0; j < folderApps.length(); j++) {
                AppInfo appInfo = appMap.get(folderApps.optString(j));
                if (appInfo != null) {
                    folderAppInfos.add(appInfo);
                }
            }
            if (!folderAppInfos.isEmpty()) {
                folderInfos.add(new AllAppsFolderInfo(
                        folder.optInt(KEY_ID), getTitle(context, folder), folderAppInfos));
            }
        }
        return folderInfos;
    }

    public static Set<String> getFolderedAppKeys(Context context, List<AppInfo> apps) {
        return getFolderedAppKeys(context, apps, ALL_APPS_FOLDERS);
    }

    static Set<String> getFolderedAppKeys(Context context, List<AppInfo> apps,
            ConstantItem<String> item) {
        Set<String> appKeys = new HashSet<>();
        for (AppInfo appInfo : apps) {
            String key = PinnedApps.encode(context, appInfo);
            if (key != null) {
                appKeys.add(key);
            }
        }

        Set<String> folderedKeys = new HashSet<>();
        JSONArray folders = readFolders(context, item);
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            JSONArray folderApps = folder == null ? null : folder.optJSONArray(KEY_APPS);
            if (folderApps == null) {
                continue;
            }
            for (int j = 0; j < folderApps.length(); j++) {
                String key = folderApps.optString(j);
                if (appKeys.contains(key)) {
                    folderedKeys.add(key);
                }
            }
        }
        return folderedKeys;
    }

    public static List<FolderRecord> getFolderRecords(Context context) {
        return getFolderRecords(context, ALL_APPS_FOLDERS);
    }

    static List<FolderRecord> getFolderRecords(Context context, ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        List<FolderRecord> records = new ArrayList<>(folders.length());
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder != null) {
                JSONArray apps = folder.optJSONArray(KEY_APPS);
                records.add(new FolderRecord(folder.optInt(KEY_ID), getTitle(context, folder),
                        apps == null ? 0 : apps.length()));
            }
        }
        return records;
    }

    public static int createFolder(Context context, CharSequence title) {
        return createFolder(context, title, ALL_APPS_FOLDERS);
    }

    static int createFolder(Context context, CharSequence title, ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        int folderId = nextFolderId(folders);
        try {
            folders.put(createFolder(folderId, getStoredTitle(title), new JSONArray()));
            writeFolders(context, folders, item);
        } catch (JSONException e) {
            Log.w(TAG, "Unable to create all apps folder", e);
            return NO_FOLDER_ID;
        }
        return folderId;
    }

    public static CharSequence setFolderTitle(Context context, int folderId, CharSequence title) {
        return setFolderTitle(context, folderId, title, ALL_APPS_FOLDERS);
    }

    static CharSequence setFolderTitle(Context context, int folderId, CharSequence title,
            ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        CharSequence displayTitle = getDisplayTitle(context, title);
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder == null || folder.optInt(KEY_ID) != folderId) {
                continue;
            }
            try {
                folder.put(KEY_TITLE, getStoredTitle(title));
                writeFolders(context, folders, item);
            } catch (JSONException e) {
                Log.w(TAG, "Unable to rename all apps folder", e);
            }
            return displayTitle;
        }
        return displayTitle;
    }

    public static void deleteFolder(Context context, int folderId) {
        deleteFolder(context, folderId, ALL_APPS_FOLDERS);
    }

    static void deleteFolder(Context context, int folderId, ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        JSONArray updatedFolders = new JSONArray();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder != null && folder.optInt(KEY_ID) != folderId) {
                updatedFolders.put(folder);
            }
        }
        writeFolders(context, updatedFolders, item);
    }

    public static void moveFolder(Context context, int fromPosition, int toPosition) {
        moveFolder(context, fromPosition, toPosition, ALL_APPS_FOLDERS);
    }

    static void moveFolder(Context context, int fromPosition, int toPosition,
            ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        if (fromPosition == toPosition || fromPosition < 0 || toPosition < 0
                || fromPosition >= folders.length() || toPosition >= folders.length()) {
            return;
        }
        List<JSONObject> orderedFolders = new ArrayList<>(folders.length());
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder != null) {
                orderedFolders.add(folder);
            }
        }
        if (fromPosition >= orderedFolders.size() || toPosition >= orderedFolders.size()) {
            return;
        }
        orderedFolders.add(toPosition, orderedFolders.remove(fromPosition));
        JSONArray updatedFolders = new JSONArray();
        for (JSONObject folder : orderedFolders) {
            updatedFolders.put(folder);
        }
        writeFolders(context, updatedFolders, item);
    }

    public static List<String> getFolderAppKeys(Context context, int folderId) {
        return getFolderAppKeys(context, folderId, ALL_APPS_FOLDERS);
    }

    static List<String> getFolderAppKeys(Context context, int folderId, ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder == null || folder.optInt(KEY_ID) != folderId) {
                continue;
            }
            JSONArray apps = folder.optJSONArray(KEY_APPS);
            List<String> keys = new ArrayList<>(apps == null ? 0 : apps.length());
            for (int j = 0; apps != null && j < apps.length(); j++) {
                keys.add(apps.optString(j));
            }
            return keys;
        }
        return List.of();
    }

    public static void setFolderAppKeys(Context context, int folderId, List<String> appKeys) {
        setFolderAppKeys(context, folderId, appKeys, ALL_APPS_FOLDERS);
    }

    static void setFolderAppKeys(Context context, int folderId, List<String> appKeys,
            ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        LinkedHashSet<String> selectedKeys = new LinkedHashSet<>();
        for (String appKey : appKeys) {
            if (appKey != null && !appKey.isEmpty()) {
                selectedKeys.add(appKey);
            }
        }

        boolean foundFolder = false;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder == null) {
                continue;
            }
            try {
                if (folder.optInt(KEY_ID) == folderId) {
                    folder.put(KEY_APPS, toJsonArray(selectedKeys));
                    foundFolder = true;
                } else {
                    folder.put(KEY_APPS, copyWithout(folder.optJSONArray(KEY_APPS), selectedKeys));
                }
            } catch (JSONException e) {
                Log.w(TAG, "Unable to update all apps folder", e);
            }
        }
        if (foundFolder) {
            writeFolders(context, folders, item);
        }
    }

    public static Map<String, CharSequence> getFolderTitlesByAppKey(Context context) {
        return getFolderTitlesByAppKey(context, ALL_APPS_FOLDERS);
    }

    static Map<String, CharSequence> getFolderTitlesByAppKey(Context context,
            ConstantItem<String> item) {
        JSONArray folders = readFolders(context, item);
        Map<String, CharSequence> titlesByKey = new LinkedHashMap<>();
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            JSONArray apps = folder == null ? null : folder.optJSONArray(KEY_APPS);
            if (folder == null || apps == null) {
                continue;
            }
            CharSequence title = getTitle(context, folder);
            for (int j = 0; j < apps.length(); j++) {
                String key = apps.optString(j);
                if (!key.isEmpty()) {
                    titlesByKey.put(key, title);
                }
            }
        }
        return titlesByKey;
    }

    private static JSONArray copyWithout(JSONArray apps, Set<String> excludedKeys) {
        JSONArray updatedApps = new JSONArray();
        for (int i = 0; apps != null && i < apps.length(); i++) {
            String appKey = apps.optString(i);
            if (!excludedKeys.contains(appKey)) {
                updatedApps.put(appKey);
            }
        }
        return updatedApps;
    }

    private static JSONObject createFolder(int folderId, String title, JSONArray apps)
            throws JSONException {
        JSONObject folder = new JSONObject();
        folder.put(KEY_ID, folderId);
        folder.put(KEY_TITLE, title);
        folder.put(KEY_APPS, apps);
        return folder;
    }

    private static JSONArray toJsonArray(Collection<String> appKeys) {
        JSONArray apps = new JSONArray();
        for (String appKey : appKeys) {
            apps.put(appKey);
        }
        return apps;
    }

    private static int nextFolderId(JSONArray folders) {
        int id = 0;
        for (int i = 0; i < folders.length(); i++) {
            JSONObject folder = folders.optJSONObject(i);
            if (folder != null) {
                id = Math.max(id, folder.optInt(KEY_ID));
            }
        }
        return id + 1;
    }

    static CharSequence getTitle(Context context, JSONObject folder) {
        return getDisplayTitle(context, folder.optString(KEY_TITLE));
    }

    private static CharSequence getDisplayTitle(Context context, CharSequence title) {
        String storedTitle = getStoredTitle(title);
        return storedTitle.isEmpty() ? context.getText(R.string.unnamed_folder) : storedTitle;
    }

    private static String getStoredTitle(CharSequence title) {
        return title == null ? "" : title.toString().trim();
    }

    static JSONArray readFolders(Context context) {
        return readFolders(context, ALL_APPS_FOLDERS);
    }

    static JSONArray readFolders(Context context, ConstantItem<String> item) {
        try {
            return new JSONArray(LauncherPrefs.get(context).get(item));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void writeFolders(Context context, JSONArray folders) {
        writeFolders(context, folders, ALL_APPS_FOLDERS);
    }

    static void writeFolders(Context context, JSONArray folders, ConstantItem<String> item) {
        LauncherPrefs.get(context).put(item, folders.toString());
    }

    public static final class FolderRecord {
        public final int id;
        public final CharSequence title;
        public final int appCount;

        FolderRecord(int id, CharSequence title, int appCount) {
            this.id = id;
            this.title = title;
            this.appCount = appCount;
        }
    }
}
