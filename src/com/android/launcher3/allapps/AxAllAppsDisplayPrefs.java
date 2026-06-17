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

import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_COLUMNS;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_ICON_SCALE;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_LABEL_SCALE;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_ROW_SCALE;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_SIDE_PADDING_SCALE;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_HAPTIC_FEEDBACK;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_REMEMBER_POSITION;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_SHOW_SCROLLBAR;

import android.content.Context;

import com.android.launcher3.AxPreferenceFeature;
import com.android.launcher3.ConstantItem;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Item;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.deviceprofile.AllAppsProfile;
import com.android.launcher3.util.DaggerSingletonObject;

import java.util.List;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxAllAppsDisplayPrefs extends AxPreferenceFeature {

    private static final int DEFAULT_PERCENT = 100;
    public static final int MIN_COLUMNS_FOR_SETTINGS = 3;
    public static final int MAX_COLUMNS_FOR_SETTINGS = 10;
    private static final int MIN_PERCENT = 50;
    private static final int MAX_PERCENT = 150;

    public static final DaggerSingletonObject<AxAllAppsDisplayPrefs> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getAllAppsDisplayPrefs);
    private static final List<Item> ALL_APPS_DISPLAY_ITEMS = List.of(
            ALL_APPS_DRAWER_COLUMNS,
            ALL_APPS_DRAWER_ICON_SCALE,
            ALL_APPS_DRAWER_LABEL_SCALE,
            ALL_APPS_DRAWER_ROW_SCALE,
            ALL_APPS_DRAWER_SIDE_PADDING_SCALE);

    @Inject
    public AxAllAppsDisplayPrefs() {
        super(ALL_APPS_DISPLAY_ITEMS);
    }

    public int getDrawerColumns(Context context, int defaultColumns) {
        int columns = LauncherPrefs.get(context).get(ALL_APPS_DRAWER_COLUMNS);
        return Utilities.boundToRange(columns > 0 ? columns : defaultColumns,
                MIN_COLUMNS_FOR_SETTINGS, MAX_COLUMNS_FOR_SETTINGS);
    }

    public boolean shouldRememberPosition(Context context) {
        return LauncherPrefs.get(context).get(ALL_APPS_REMEMBER_POSITION);
    }

    public boolean shouldShowScrollbar(Context context) {
        return LauncherPrefs.get(context).get(ALL_APPS_SHOW_SCROLLBAR);
    }

    public boolean shouldPlayOpenHaptic(Context context) {
        return LauncherPrefs.get(context).get(ALL_APPS_HAPTIC_FEEDBACK);
    }

    public void applyToDeviceProfile(Context context, DeviceProfile deviceProfile) {
        AllAppsProfile profile = deviceProfile.getAllAppsProfile();
        int iconPercent = getPercent(context, ALL_APPS_DRAWER_ICON_SCALE);
        int labelPercent = getPercent(context, ALL_APPS_DRAWER_LABEL_SCALE);
        int rowPercent = getPercent(context, ALL_APPS_DRAWER_ROW_SCALE);
        int iconSize = scale(profile.getIconSizePx(), iconPercent);
        float labelSize = Math.max(1f, profile.getIconTextSizePx() * labelPercent / 100f);
        int minHeight = iconSize + profile.getIconDrawablePaddingPx()
                + Utilities.calculateTextHeight(labelSize) * profile.getMaxAllAppsTextLineCount()
                + profile.getBorderSpacePx().y;
        int cellHeight = Math.max(minHeight, scale(profile.getCellHeightPx(), rowPercent));
        int cellWidth = Math.max(iconSize + 2 * profile.getIconDrawablePaddingPx(),
                profile.getCellWidthPx());
        deviceProfile.setAllAppsProfile(profile.copy(profile.getBorderSpacePx(), cellHeight,
                iconSize, labelSize, profile.getIconDrawablePaddingPx(),
                profile.getMaxAllAppsTextLineCount(), cellWidth));
        int paddingPercent = getPercent(context, ALL_APPS_DRAWER_SIDE_PADDING_SCALE, 0,
                MAX_PERCENT);
        deviceProfile.allAppsPadding.left = scale(deviceProfile.allAppsPadding.left,
                paddingPercent);
        deviceProfile.allAppsPadding.right = scale(deviceProfile.allAppsPadding.right,
                paddingPercent);
        deviceProfile.allAppsLeftRightMargin = scale(deviceProfile.allAppsLeftRightMargin,
                paddingPercent);
    }

    private static int getPercent(Context context, ConstantItem<Integer> item) {
        return getPercent(context, item, MIN_PERCENT, MAX_PERCENT);
    }

    private static int getPercent(Context context, ConstantItem<Integer> item, int min, int max) {
        return Utilities.boundToRange(LauncherPrefs.get(context).get(item), min, max);
    }

    private static int scale(int value, int percent) {
        return Math.max(0, Math.round(value * percent / (float) DEFAULT_PERCENT));
    }
}
