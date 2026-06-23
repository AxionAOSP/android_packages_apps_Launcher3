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
    private final AxSmartDrawerManager mSmartDrawerManager;

    AxAllAppsListController(Context context) {
        mContext = context;
        mIndex = new AlphabeticIndexCompat(context);
        mSmartDrawerManager = AxSmartDrawerManager.INSTANCE.get(context);
    }

    void addPreferenceListener(LauncherPrefChangeListener listener) {
        LauncherPrefs prefs = LauncherPrefs.get(mContext);
        prefs.addListener(listener, ALL_APPS_FOLDERS);
        mSmartDrawerManager.addChangeListener(prefs, listener);
    }

    boolean handlesPrefChange(String key) {
        return PINNED_APPS.getSharedPrefKey().equals(key)
                || ALL_APPS_FOLDERS.getSharedPrefKey().equals(key)
                || mSmartDrawerManager.hasPreferenceKey(key);
    }

    boolean shouldShowApp(AppInfo info) {
        return mSmartDrawerManager.isEnabled() || !PinnedApps.isPinned(mContext, info);
    }

    boolean isSmartDrawerMode() {
        return mSmartDrawerManager.isEnabled();
    }

    List<Object> getEntries(List<AppInfo> appList, boolean hasPrivateApps,
            String expandedSmartCategoryId) {
        if (hasPrivateApps) {
            return new ArrayList<>(appList);
        }
        if (mSmartDrawerManager.isEnabled()) {
            List<AxSmartDrawerCategory> smartEntries = mSmartDrawerManager.getEntries(appList);
            if (expandedSmartCategoryId != null) {
                for (AxSmartDrawerCategory category : smartEntries) {
                    if (category.isExpandable()
                            && expandedSmartCategoryId.equals(category.getId())) {
                        List<Object> expandedEntries = new ArrayList<>();
                        expandedEntries.add(new SmartDrawerHeader(category));
                        expandedEntries.addAll(category.getApps());
                        return expandedEntries;
                    }
                }
            }
            return new ArrayList<>(smartEntries);
        }

        List<Object> entries = new ArrayList<>();
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

    boolean isSmartDrawerEntry(Object entry) {
        return entry instanceof AxSmartDrawerCategory || entry instanceof SmartDrawerHeader;
    }

    AdapterItem createSmartDrawerItem(Object entry) {
        if (entry instanceof SmartDrawerHeader header) {
            return AdapterItem.asSmartDrawerHeader(header.category);
        }
        AxSmartDrawerCategory category = (AxSmartDrawerCategory) entry;
        return category.isRow()
                ? AdapterItem.asSmartDrawerRow(category)
                : AdapterItem.asSmartDrawerCategory(category);
    }

    String getSectionName(Object entry) {
        if (entry instanceof SmartDrawerHeader header) {
            return mIndex.computeSectionName(header.category.getTitle());
        }
        if (entry instanceof AxSmartDrawerCategory category) {
            return mIndex.computeSectionName(category.getTitle());
        }
        if (entry instanceof AllAppsFolderInfo folderInfo) {
            return mIndex.computeSectionName(folderInfo.getTitle());
        }
        return ((AppInfo) entry).sectionName;
    }

    String getEntryTitle(Object entry) {
        if (entry instanceof SmartDrawerHeader header) {
            return String.valueOf(header.category.getTitle());
        }
        if (entry instanceof AxSmartDrawerCategory category) {
            return String.valueOf(category.getTitle());
        }
        if (entry instanceof AllAppsFolderInfo folderInfo) {
            return String.valueOf(folderInfo.getTitle());
        }
        AppInfo info = (AppInfo) entry;
        return info.title == null ? "" : info.title.toString();
    }

    private static final class SmartDrawerHeader {
        final AxSmartDrawerCategory category;

        SmartDrawerHeader(AxSmartDrawerCategory category) {
            this.category = category;
        }
    }
}
