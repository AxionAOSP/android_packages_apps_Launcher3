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
import static com.android.launcher3.LauncherPrefsExt.PINNED_APPS;

import android.content.Context;

import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.compat.AlphabeticIndexCompat;
import com.android.launcher3.model.data.AppInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class AxAllAppsListController {

    private final Context mContext;
    private final AlphabeticIndexCompat mIndex;

    AxAllAppsListController(Context context) {
        mContext = context;
        mIndex = new AlphabeticIndexCompat(context);
    }

    void addPreferenceListener(LauncherPrefChangeListener listener) {
        LauncherPrefs.get(mContext).addListener(listener, ALL_APPS_FOLDERS, PINNED_APPS);
    }

    boolean handlesPrefChange(String key) {
        return PINNED_APPS.getSharedPrefKey().equals(key)
                || ALL_APPS_FOLDERS.getSharedPrefKey().equals(key);
    }

    boolean shouldShowApp(AppInfo info) {
        return !PinnedApps.isPinned(mContext, info);
    }

    List<Object> getEntries(List<AppInfo> appList, boolean hasPrivateApps) {
        List<Object> entries = new ArrayList<>();
        if (hasPrivateApps) {
            entries.addAll(appList);
            return entries;
        }

        Set<String> folderedKeys = AllAppsFolderStore.getFolderedAppKeys(mContext, appList);
        List<AllAppsFolderInfo> folders = AllAppsFolderStore.getFolders(mContext, appList);
        entries.addAll(folders);
        for (AppInfo appInfo : appList) {
            String key = PinnedApps.encode(mContext, appInfo);
            if (key == null || !folderedKeys.contains(key)) {
                entries.add(appInfo);
            }
        }
        return entries;
    }

    boolean isFolderEntry(Object entry) {
        return entry instanceof AllAppsFolderInfo;
    }

    AdapterItem createFolderItem(Object entry) {
        return AdapterItem.asFolder((AllAppsFolderInfo) entry);
    }

    String getSectionName(Object entry) {
        if (entry instanceof AllAppsFolderInfo folderInfo) {
            return mIndex.computeSectionName(folderInfo.getTitle());
        }
        return ((AppInfo) entry).sectionName;
    }

    String getEntryTitle(Object entry) {
        if (entry instanceof AllAppsFolderInfo folderInfo) {
            return String.valueOf(folderInfo.getTitle());
        }
        AppInfo info = (AppInfo) entry;
        return info.title == null ? "" : info.title.toString();
    }
}
