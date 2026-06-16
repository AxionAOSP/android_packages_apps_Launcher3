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

import android.content.Context;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.R;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.views.ActivityContext;

public final class AxAllAppsFolderAdapter {

    private AxAllAppsFolderAdapter() { }

    public static BaseAllAppsAdapter.ViewHolder onCreateViewHolder(
            ActivityContext activityContext, ViewGroup parent) {
        FrameLayout folderIconContainer = new FrameLayout(parent.getContext());
        folderIconContainer.setClipChildren(false);
        folderIconContainer.setClipToPadding(false);
        folderIconContainer.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx()));
        return new BaseAllAppsAdapter.ViewHolder(folderIconContainer);
    }

    public static void onBindViewHolder(ActivityContext activityContext, AlphabeticalAppsList apps,
            OnFocusChangeListener focusListener, BaseAllAppsAdapter.ViewHolder holder,
            int position) {
        BaseAllAppsAdapter.AdapterItem adapterItem = apps.getAdapterItems().get(position);
        FrameLayout folderIconContainer = (FrameLayout) holder.itemView;
        folderIconContainer.removeAllViews();
        FolderIcon folderIcon = FolderIcon.inflateFolderAndIcon(R.layout.all_apps_folder_icon,
                getContextActivity(activityContext), null,
                adapterItem.folderInfo.toFolderInfo(activityContext.asContext()));
        folderIcon.onTitleChanged(adapterItem.folderInfo.getTitle());
        folderIcon.setTextVisible(true);
        folderIcon.setOnFocusChangeListener(focusListener);
        folderIconContainer.addView(folderIcon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Context & ActivityContext> T getContextActivity(
            ActivityContext activityContext) {
        return (T) activityContext;
    }
}
