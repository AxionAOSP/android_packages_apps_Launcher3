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
package com.android.launcher3.util;

import com.android.launcher3.model.data.ItemInfo;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controller for managing multiple item selection in Launcher.
 */
public class MultiSelectController {

    private final Set<ItemInfo> mSelectedItems = new LinkedHashSet<>();
    private Consumer<Integer> mSelectionChangeListener;

    public void setSelectionChangeListener(Consumer<Integer> listener) {
        mSelectionChangeListener = listener;
    }

    public boolean isSelected(ItemInfo info) {
        return mSelectedItems.contains(info);
    }

    public void toggleSelection(ItemInfo info) {
        if (mSelectedItems.contains(info)) {
            mSelectedItems.remove(info);
        } else {
            mSelectedItems.add(info);
        }
        notifyChange();
    }

    public void clearSelection() {
        if (!mSelectedItems.isEmpty()) {
            mSelectedItems.clear();
            notifyChange();
        }
    }

    public Set<ItemInfo> getSelectedItems() {
        return new LinkedHashSet<>(mSelectedItems);
    }

    public int getSelectedCount() {
        return mSelectedItems.size();
    }

    private void notifyChange() {
        if (mSelectionChangeListener != null) {
            mSelectionChangeListener.accept(mSelectedItems.size());
        }
    }
}
