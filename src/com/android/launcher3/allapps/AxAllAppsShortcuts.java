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
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION;

import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.R;
import com.android.launcher3.folder.AxFolderExt;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.views.ActivityContext;

public final class AxAllAppsShortcuts {

    public static final SystemShortcut.Factory<ActivityContext> PIN_TO_DRAWER =
            (activity, itemInfo, originalView) -> {
                if (!isAllAppsItem(activity, itemInfo)) {
                    return null;
                }
                if (!(itemInfo instanceof ItemInfoWithIcon info)
                        || (info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_NOT_PINNABLE) != 0
                        || itemInfo.getTargetComponent() == null) {
                    return null;
                }
                return new PinToDrawer<>(activity, itemInfo, originalView);
            };

    private AxAllAppsShortcuts() { }

    private static boolean isAllAppsItem(ActivityContext activity, ItemInfo itemInfo) {
        return itemInfo != null && (itemInfo.container == CONTAINER_ALL_APPS
                || itemInfo.container == CONTAINER_ALL_APPS_PREDICTION
                || AxFolderExt.isAllAppsFolderItem(activity, itemInfo));
    }

    private static class PinToDrawer<T extends ActivityContext> extends SystemShortcut<T> {

        private final boolean mIsPinned;

        PinToDrawer(T target, ItemInfo itemInfo, @NonNull View originalView) {
            this(target, itemInfo, originalView,
                    PinnedApps.isPinned(originalView.getContext(), itemInfo));
        }

        private PinToDrawer(T target, ItemInfo itemInfo, @NonNull View originalView,
                boolean isPinned) {
            super(isPinned ? R.drawable.ic_unpin : R.drawable.ic_pin,
                    isPinned ? R.string.unpin_from_drawer : R.string.pin_to_drawer,
                    target, itemInfo, originalView, false);
            mIsPinned = isPinned;
        }

        @Override
        public void onClick(View view) {
            PinnedApps.setPinned(view.getContext(), mItemInfo, !mIsPinned);
            AbstractFloatingView.closeAllOpenViews(mTarget);
        }
    }
}
