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

import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_SMART_DRAWER_FOLDERS;

import android.content.Context;

import com.android.launcher3.allapps.AllAppsFolderStore.FolderRecord;
import com.android.launcher3.model.data.AppInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AxSmartDrawerFolderStore {

    private AxSmartDrawerFolderStore() { }

    public static List<AllAppsFolderInfo> getFolders(Context context, List<AppInfo> apps) {
        return AllAppsFolderStore.getFolders(context, apps, ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static Set<String> getFolderedAppKeys(Context context, List<AppInfo> apps) {
        return AllAppsFolderStore.getFolderedAppKeys(context, apps,
                ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static List<FolderRecord> getFolderRecords(Context context) {
        return AllAppsFolderStore.getFolderRecords(context, ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static int createFolder(Context context, CharSequence title) {
        return AllAppsFolderStore.createFolder(context, title, ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static CharSequence setFolderTitle(Context context, int folderId, CharSequence title) {
        return AllAppsFolderStore.setFolderTitle(context, folderId, title,
                ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static void deleteFolder(Context context, int folderId) {
        AllAppsFolderStore.deleteFolder(context, folderId, ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static void moveFolder(Context context, int fromPosition, int toPosition) {
        AllAppsFolderStore.moveFolder(context, fromPosition, toPosition,
                ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static List<String> getFolderAppKeys(Context context, int folderId) {
        return AllAppsFolderStore.getFolderAppKeys(context, folderId,
                ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static void setFolderAppKeys(Context context, int folderId, List<String> appKeys) {
        AllAppsFolderStore.setFolderAppKeys(context, folderId, appKeys,
                ALL_APPS_SMART_DRAWER_FOLDERS);
    }

    public static Map<String, CharSequence> getFolderTitlesByAppKey(Context context) {
        return AllAppsFolderStore.getFolderTitlesByAppKey(context,
                ALL_APPS_SMART_DRAWER_FOLDERS);
    }
}
