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

import static com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_GRID_COLUMNS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_GRID_ROWS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_HOTSEAT_ICONS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_GRID_ROWS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS;

import android.content.Context;

import com.android.launcher3.dagger.LauncherAppSingleton;

import java.util.List;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxWorkspaceDisplayPrefs extends AxPreferenceFeature {

    public static final int MIN_GRID_SIZE = 3;
    public static final int MAX_GRID_SIZE = 10;
    public static final int MAX_TABLET_GRID_SIZE = 16;
    private static final List<Item> WORKSPACE_DISPLAY_ITEMS = List.of(
            WORKSPACE_GRID_COLUMNS,
            WORKSPACE_GRID_ROWS,
            WORKSPACE_HOTSEAT_ICONS,
            WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS,
            WORKSPACE_TABLET_PORTRAIT_GRID_ROWS,
            WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS,
            WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS,
            WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS,
            WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS);

    @Inject
    public AxWorkspaceDisplayPrefs() {
        super(WORKSPACE_DISPLAY_ITEMS);
    }

    public int getColumns(Context context, int defaultColumns) {
        return getGridSize(context, WORKSPACE_GRID_COLUMNS, defaultColumns);
    }

    public int getRows(Context context, int defaultRows) {
        return getGridSize(context, WORKSPACE_GRID_ROWS, defaultRows);
    }

    public int getHotseatIcons(Context context, int defaultHotseatIcons) {
        return getGridSize(context, WORKSPACE_HOTSEAT_ICONS, defaultHotseatIcons);
    }

    public void applyToInvariantProfile(Context context, InvariantDeviceProfile profile) {
        int defaultColumns = profile.numColumns;
        int defaultRows = profile.numRows;
        int defaultHotseatIcons = profile.numShownHotseatIcons;
        profile.numColumns = getGridSize(context, getColumnsItem(profile),
                getFallbackColumnsItem(profile), defaultColumns, getMaxGridSize(profile));
        profile.numRows = getGridSize(context, getRowsItem(profile), getFallbackRowsItem(profile),
                defaultRows, getMaxGridSize(profile));
        profile.numShownHotseatIcons = getGridSize(context, getHotseatIconsItem(profile),
                getFallbackHotseatIconsItem(profile), defaultHotseatIcons,
                getMaxGridSize(profile));
        profile.numDatabaseHotseatIcons = profile.numShownHotseatIcons;
        if (profile.numColumns != defaultColumns || profile.numRows != defaultRows
                || profile.numShownHotseatIcons != defaultHotseatIcons) {
            profile.dbFile = AxWorkspaceGridDb.getFileName(profile.numColumns, profile.numRows,
                    profile.numDatabaseHotseatIcons);
        }
        profile.numSearchContainerColumns = Math.min(profile.numSearchContainerColumns,
                profile.numColumns);
    }

    public boolean usesOrientationSpecificGrid(InvariantDeviceProfile profile) {
        return isTablet(profile);
    }

    private int getGridSize(Context context, ConstantItem<Integer> item, int defaultSize) {
        return getGridSize(context, item, null, defaultSize, MAX_GRID_SIZE);
    }

    private int getGridSize(Context context, ConstantItem<Integer> item,
            ConstantItem<Integer> fallbackItem, int defaultSize, int maxSize) {
        LauncherPrefs prefs = LauncherPrefs.get(context);
        int size = prefs.get(item);
        if (size <= 0 && fallbackItem != null) {
            size = prefs.get(fallbackItem);
        }
        if (size <= 0) {
            return defaultSize;
        }
        return Utilities.boundToRange(size, MIN_GRID_SIZE, maxSize);
    }

    private ConstantItem<Integer> getColumnsItem(InvariantDeviceProfile profile) {
        if (!isTablet(profile)) {
            return WORKSPACE_GRID_COLUMNS;
        }
        return isLandscape(profile)
                ? WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS
                : WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS;
    }

    private ConstantItem<Integer> getRowsItem(InvariantDeviceProfile profile) {
        if (!isTablet(profile)) {
            return WORKSPACE_GRID_ROWS;
        }
        return isLandscape(profile)
                ? WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS
                : WORKSPACE_TABLET_PORTRAIT_GRID_ROWS;
    }

    private ConstantItem<Integer> getHotseatIconsItem(InvariantDeviceProfile profile) {
        if (!isTablet(profile)) {
            return WORKSPACE_HOTSEAT_ICONS;
        }
        return isLandscape(profile)
                ? WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS
                : WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS;
    }

    private ConstantItem<Integer> getFallbackColumnsItem(InvariantDeviceProfile profile) {
        return isTablet(profile) ? WORKSPACE_GRID_COLUMNS : null;
    }

    private ConstantItem<Integer> getFallbackRowsItem(InvariantDeviceProfile profile) {
        return isTablet(profile) ? WORKSPACE_GRID_ROWS : null;
    }

    private ConstantItem<Integer> getFallbackHotseatIconsItem(InvariantDeviceProfile profile) {
        return isTablet(profile) ? WORKSPACE_HOTSEAT_ICONS : null;
    }

    private int getMaxGridSize(InvariantDeviceProfile profile) {
        return isTablet(profile) ? MAX_TABLET_GRID_SIZE : MAX_GRID_SIZE;
    }

    private static boolean isTablet(InvariantDeviceProfile profile) {
        return profile.deviceType == TYPE_TABLET;
    }

    private static boolean isLandscape(InvariantDeviceProfile profile) {
        return profile.displayInfo.realBounds.isLandscape();
    }

}
