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
package com.android.launcher3.folder;

import static com.android.launcher3.LauncherPrefsExt.SHOW_DESKTOP_LABELS;
import static com.android.launcher3.LauncherPrefsExt.SHOW_DRAWER_LABELS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsFolderStore;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.PopupControllerForAppIcon;
import com.android.launcher3.views.ActivityContext;

public final class AxFolderExt {

    private AxFolderExt() { }

    public static boolean isAllAppsFolder(FolderInfo info) {
        return info != null && info.container == CONTAINER_ALL_APPS;
    }

    public static boolean shouldShowFolderLabel(Context context, FolderInfo info) {
        if (isAllAppsFolder(info)) {
            return SHOW_DRAWER_LABELS.get(context);
        }
        return SHOW_DESKTOP_LABELS.get(context);
    }

    public static boolean onLongClick(FolderInfo info, View view) {
        if (!isAllAppsFolder(info)) {
            return false;
        }
        if (view instanceof BubbleTextView && view.getTag() instanceof ItemInfo) {
            new PopupControllerForAppIcon<Launcher>().show(view);
        }
        return true;
    }

    public static boolean setTitle(ActivityContext activityContext, FolderInfo info,
            CharSequence title) {
        if (!isAllAppsFolder(info)) {
            return false;
        }
        info.title = AllAppsFolderStore.setFolderTitle(activityContext.asContext(), info.id, title);
        return true;
    }

    public static boolean onBind(FolderInfo info, TextView folderName) {
        if (isAllAppsFolder(info)) {
            folderName.setVisibility(View.VISIBLE);
            return true;
        }
        return false;
    }

    public static boolean isAllAppsFolderItem(ActivityContext activityContext, ItemInfo itemInfo) {
        Folder folder = Folder.getOpen(activityContext);
        return folder != null && isAllAppsFolder(folder.mInfo) && itemInfo != null
                && itemInfo.container == folder.mInfo.id;
    }

    public static FolderInfo getAllAppsFolderInfo(Context context, ItemInfo itemInfo) {
        if (itemInfo == null) {
            return null;
        }
        ActivityContext activityContext = ActivityContext.lookupContextNoThrow(context);
        if (activityContext != null) {
            Folder folder = Folder.getOpen(activityContext);
            if (folder != null && isAllAppsFolder(folder.mInfo)
                    && itemInfo.container == folder.mInfo.id) {
                return folder.mInfo;
            }
        }
        return AllAppsFolderStore.getFolderInfo(context, itemInfo.container);
    }

    public static void collapseIfAllAppsFolder(ActivityContext activityContext, ItemInfo itemInfo) {
        Folder folder = Folder.getOpen(activityContext);
        if (folder != null && isAllAppsFolder(folder.mInfo)
                && (itemInfo == null || itemInfo.container == folder.mInfo.id)) {
            folder.close(true);
        }
    }
}
