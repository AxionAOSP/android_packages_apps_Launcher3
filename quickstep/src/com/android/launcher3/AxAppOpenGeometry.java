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
package com.android.launcher3;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.views.BubbleTextHolder;

public final class AxAppOpenGeometry {
    private AxAppOpenGeometry() {}

    public static RectF getFrozenBounds(Launcher launcher, View source, RectF fallback) {
        View target = source;
        if (target instanceof DeepShortcutView shortcut) {
            target = shortcut.getIconView();
        } else if (target.getParent() instanceof DeepShortcutView shortcut) {
            target = shortcut.getIconView();
        } else if (target instanceof BubbleTextHolder holder) {
            target = holder.getBubbleText();
        }
        if (target == null) {
            return new RectF(fallback);
        }
        Rect targetBounds = new Rect();
        if (target instanceof BubbleTextView icon) {
            icon.getIconBounds(targetBounds);
        } else if (target instanceof FolderIcon folder) {
            folder.getPreviewBounds(targetBounds);
        } else {
            targetBounds.set(0, 0, target.getWidth(), target.getHeight());
        }
        RectF bounds = new RectF();
        Utilities.getBoundsForViewInDragLayer(
                launcher.getDragLayer(), target, targetBounds, true, null, bounds);
        return bounds.isEmpty() ? new RectF(fallback) : bounds;
    }
}
