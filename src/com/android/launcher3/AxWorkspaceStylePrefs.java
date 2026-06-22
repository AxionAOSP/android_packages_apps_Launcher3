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

import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_WALLPAPER_SCROLLING;
import static com.android.launcher3.util.SystemUiController.FLAG_DARK_NAV;
import static com.android.launcher3.util.SystemUiController.FLAG_DARK_STATUS;
import static com.android.launcher3.util.SystemUiController.FLAG_LIGHT_NAV;
import static com.android.launcher3.util.SystemUiController.FLAG_LIGHT_STATUS;
import static com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW;

import android.content.Context;

import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.Themes;

import java.util.List;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxWorkspaceStylePrefs extends AxPreferenceFeature {

    public static final DaggerSingletonObject<AxWorkspaceStylePrefs> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getWorkspaceStylePrefs);
    private static final List<Item> WORKSPACE_STYLE_ITEMS = List.of(
            WORKSPACE_WALLPAPER_SCROLLING);

    @Inject
    public AxWorkspaceStylePrefs() {
        super(WORKSPACE_STYLE_ITEMS);
    }

    public boolean shouldScrollWallpaper(Context context) {
        return LauncherPrefs.get(context).get(WORKSPACE_WALLPAPER_SCROLLING);
    }

    public void applySystemBars(Launcher launcher) {
        launcher.getSystemUiController().updateUiState(UI_STATE_BASE_WINDOW,
                getBaseSystemUiFlags(launcher));
    }

    private int getBaseSystemUiFlags(Context context) {
        boolean lightBars = Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText);
        int navFlags = lightBars ? FLAG_LIGHT_NAV : FLAG_DARK_NAV;
        int statusFlags = lightBars ? FLAG_LIGHT_STATUS : FLAG_DARK_STATUS;
        return navFlags | statusFlags;
    }
}
