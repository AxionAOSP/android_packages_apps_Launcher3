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

import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_ALLOW_WIDGET_OVERLAP;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_FORCE_WIDGET_RESIZE;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_ROUNDED_WIDGETS;
import static com.android.launcher3.LauncherPrefsExt.WORKSPACE_WIDGET_UNLIMITED_SIZE;

import android.content.Context;

import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.util.DaggerSingletonObject;

import java.util.List;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxWorkspaceWidgetPrefs extends AxPreferenceFeature {

    public static final DaggerSingletonObject<AxWorkspaceWidgetPrefs> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getWorkspaceWidgetPrefs);
    private static final List<Item> WORKSPACE_WIDGET_ITEMS = List.of(
            WORKSPACE_ROUNDED_WIDGETS,
            WORKSPACE_ALLOW_WIDGET_OVERLAP,
            WORKSPACE_FORCE_WIDGET_RESIZE,
            WORKSPACE_WIDGET_UNLIMITED_SIZE);

    @Inject
    public AxWorkspaceWidgetPrefs() {
        super(WORKSPACE_WIDGET_ITEMS);
    }

    public boolean shouldRoundWidgets(Context context) {
        return LauncherPrefs.get(context).get(WORKSPACE_ROUNDED_WIDGETS);
    }

    public boolean shouldAllowWidgetOverlap(Context context) {
        return LauncherPrefs.get(context).get(WORKSPACE_ALLOW_WIDGET_OVERLAP);
    }

    public boolean shouldForceWidgetResize(Context context) {
        return LauncherPrefs.get(context).get(WORKSPACE_FORCE_WIDGET_RESIZE);
    }

    public boolean shouldUseUnlimitedSize(Context context) {
        return LauncherPrefs.get(context).get(WORKSPACE_WIDGET_UNLIMITED_SIZE);
    }

    public boolean hasSizePreferenceKey(String key) {
        return WORKSPACE_WIDGET_UNLIMITED_SIZE.getSharedPrefKey().equals(key);
    }
}
