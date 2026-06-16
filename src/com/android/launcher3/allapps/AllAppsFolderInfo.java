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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;

import android.content.Context;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import java.util.List;
import java.util.Objects;

public final class AllAppsFolderInfo {

    private final int mId;
    private final CharSequence mTitle;
    private final List<AppInfo> mApps;

    public AllAppsFolderInfo(int id, CharSequence title, List<AppInfo> apps) {
        mId = id;
        mTitle = title;
        mApps = List.copyOf(apps);
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public FolderInfo toFolderInfo(Context context) {
        FolderInfo folderInfo = new FolderInfo();
        folderInfo.id = mId;
        folderInfo.container = CONTAINER_ALL_APPS;
        folderInfo.title = mTitle;
        for (int i = 0; i < mApps.size(); i++) {
            WorkspaceItemInfo item = mApps.get(i).makeWorkspaceItem(context);
            item.container = mId;
            item.rank = i;
            folderInfo.add(item);
        }
        return folderInfo;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AllAppsFolderInfo other)) {
            return false;
        }
        return mId == other.mId
                && Objects.equals(mTitle, other.mTitle)
                && Objects.equals(mApps, other.mApps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle, mApps);
    }
}
