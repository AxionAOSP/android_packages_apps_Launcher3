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

import static com.android.launcher3.LauncherPrefsExt.PINNED_APPS;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PinnedApps {

    private PinnedApps() { }

    public static boolean isPinned(Context context, ItemInfo itemInfo) {
        String key = encode(context, itemInfo);
        return key != null && LauncherPrefs.get(context).get(PINNED_APPS).contains(key);
    }

    public static void setPinned(Context context, ItemInfo itemInfo, boolean pinned) {
        String key = encode(context, itemInfo);
        if (key == null) {
            return;
        }
        Set<String> pinnedApps = new LinkedHashSet<>(LauncherPrefs.get(context).get(PINNED_APPS));
        if (pinned ? pinnedApps.add(key) : pinnedApps.remove(key)) {
            LauncherPrefs.get(context).put(PINNED_APPS, pinnedApps);
        }
    }

    public static List<WorkspaceItemInfo> getPinnedWorkspaceItems(
            Context context, AllAppsStore allAppsStore) {
        List<AppInfo> appInfos = new ArrayList<>();
        Collections.addAll(appInfos, allAppsStore.getApps());
        appInfos = getPinnedApps(context, appInfos);

        List<WorkspaceItemInfo> items = new ArrayList<>(appInfos.size());
        for (AppInfo appInfo : appInfos) {
            items.add(appInfo.makeWorkspaceItem(context));
        }
        return items;
    }

    public static List<AppInfo> getPinnedApps(Context context, List<AppInfo> apps) {
        Set<String> pinnedApps = LauncherPrefs.get(context).get(PINNED_APPS);
        if (pinnedApps.isEmpty()) {
            return Collections.emptyList();
        }

        List<AppInfo> appInfos = new ArrayList<>();
        for (AppInfo appInfo : apps) {
            if (pinnedApps.contains(encode(context, appInfo))) {
                appInfos.add(appInfo);
            }
        }
        appInfos.sort(new AppInfoComparator(context));
        return appInfos;
    }

    @Nullable
    static String encode(Context context, ItemInfo itemInfo) {
        ComponentName componentName = itemInfo.getTargetComponent();
        if (componentName == null) {
            return null;
        }
        return AllAppsFolderStore.encodeAppKey(context, componentName, itemInfo.user);
    }
}
