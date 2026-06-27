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
package com.android.launcher3.popup;

import android.content.ComponentName;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.customicon.CustomIconPickerBridge;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;

public class CustomIconShortcut extends SystemShortcut<Launcher> {

    public static final Factory<Launcher> CUSTOM_ICON = (context, itemInfo, originalView) -> {
        if (!CustomIconPickerBridge.isAvailable()) return null;
        ComponentName componentName = itemInfo.getTargetComponent();
        if (componentName == null) return null;
        if (!(itemInfo instanceof ItemInfoWithIcon)) return null;
        if (!Utilities.isWorkspaceEditAllowed(context)) return null;
        return new CustomIconShortcut(context, itemInfo, originalView);
    };

    public CustomIconShortcut(Launcher target, ItemInfo itemInfo, @NonNull View originalView) {
        super(R.drawable.ic_palette, R.string.custom_icon_shortcut_label, target, itemInfo,
                originalView, false);
    }

    @Override
    public void onClick(View view) {
        dismissTaskMenuView();
        ComponentName componentName = mItemInfo.getTargetComponent();
        if (componentName == null) return;
        String label = mItemInfo.title != null ? mItemInfo.title.toString()
                : componentName.getPackageName();
        Drawable iconDrawable = null;
        if (mItemInfo instanceof ItemInfoWithIcon info && info.bitmap != null
                && info.bitmap.icon != null) {
            iconDrawable = new BitmapDrawable(mTarget.getResources(), info.bitmap.icon);
        }
        if (iconDrawable == null) {
            iconDrawable = mTarget.getPackageManager().getDefaultActivityIcon();
        }
        CustomIconPickerBridge.show(
                mTarget,
                componentName,
                label,
                iconDrawable,
                mItemInfo.user);
    }
}
