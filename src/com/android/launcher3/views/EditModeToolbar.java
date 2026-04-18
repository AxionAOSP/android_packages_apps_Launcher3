/*
 * Copyright (C) 2025-2026 AxionOS
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
package com.android.launcher3.views;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherState.NORMAL;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.app.animation.Interpolators;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.ItemInfoMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Toolbar shown during EDIT_MODE to provide actions for selected items.
 */
public class EditModeToolbar extends FrameLayout implements StateHandler<LauncherState> {

    private final Launcher mLauncher;
    private TextView mSelectionCount;
    private View mBtnCreateFolder;

    public EditModeToolbar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLauncher = Launcher.getLauncher(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSelectionCount = findViewById(R.id.selection_count);
        mBtnCreateFolder = findViewById(R.id.btn_create_folder);

        findViewById(R.id.btn_dismiss).setOnClickListener(v -> {
            mLauncher.getMultiSelectController().clearSelection();
            mLauncher.getStateManager().goToState(NORMAL);
        });

        findViewById(R.id.btn_done).setOnClickListener(v -> {
            mLauncher.getMultiSelectController().clearSelection();
            mLauncher.getStateManager().goToState(NORMAL);
        });

        mBtnCreateFolder.setOnClickListener(v -> {
            Set<ItemInfo> selected = mLauncher.getMultiSelectController().getSelectedItems();
            
            List<ItemInfo> itemsInFolder = new ArrayList<>();
            for (ItemInfo info : selected) {
                if (info.itemType == Favorites.ITEM_TYPE_APPLICATION || 
                    info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                    itemsInFolder.add(info);
                }
            }
            
            if (itemsInFolder.size() < 2) {
                Toast.makeText(getContext(), "Select at least 2 apps", Toast.LENGTH_SHORT).show();
                return;
            }

            Workspace workspace = mLauncher.getWorkspace();
            int currentPage = workspace.getNextPage();
            View page = workspace.getChildAt(currentPage);
            if (!(page instanceof CellLayout layout)) {
                return;
            }
            
            int[] targetCell = new int[2];
            if (layout.findNearestVacantArea(layout.getWidth() / 2, layout.getHeight() / 2, 
                    1, 1, 1, 1, targetCell, null) != null) {
                
                int screenId = workspace.getScreenIdForPageIndex(currentPage);
                FolderIcon fi = mLauncher.addFolder(layout, (int) CONTAINER_DESKTOP, screenId, 
                        targetCell[0], targetCell[1]);

                for (ItemInfo info : itemsInFolder) {
                    View itemView = workspace.getFirstMatch(ItemInfoMatcher.ofItemIds(IntSet.wrap(info.id)));
                    if (itemView != null) {
                        CellLayout parentLayout = workspace.getParentCellLayoutForView(itemView);
                        if (parentLayout != null) {
                            parentLayout.removeView(itemView);
                        }
                        fi.getFolder().addFolderContent(info);
                    }
                }
                
                mLauncher.getMultiSelectController().clearSelection();
                mLauncher.getStateManager().goToState(NORMAL);
            } else {
                Toast.makeText(getContext(), "No space to create folder", Toast.LENGTH_SHORT).show();
            }
        });
        
        mLauncher.getMultiSelectController().setSelectionChangeListener(count -> {
            if (mSelectionCount != null) {
                mSelectionCount.setText(count + " selected");
            }
            if (mBtnCreateFolder != null) {
                mBtnCreateFolder.setVisibility(count >= 2 ? View.VISIBLE : View.GONE);
            }
            if (mLauncher.getWorkspace() != null) {
                mLauncher.getWorkspace().mapOverItems((info, view) -> {
                    view.invalidate();
                    return false;
                });
            }
        });
    }

    @Override
    public void setState(LauncherState state) {
        float targetAlpha = state == LauncherState.EDIT_MODE ? 1f : 0f;
        setAlpha(targetAlpha);
        AlphaUpdateListener.updateVisibility(this, View.GONE);
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, 
            StateAnimationConfig config, 
            PendingAnimation animation) {
        float targetAlpha = toState == LauncherState.EDIT_MODE ? 1f : 0f;
        animation.setViewAlpha(this, targetAlpha, Interpolators.LINEAR);
        animation.addEndListener(anim -> AlphaUpdateListener.updateVisibility(this, View.GONE));
    }
}
