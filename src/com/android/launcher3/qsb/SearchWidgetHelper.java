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

package com.android.launcher3.qsb;

import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.widget.WidgetManagerHelper;

import java.util.ArrayList;
import java.util.List;

public class SearchWidgetHelper {

    @NonNull
    public static List<AppWidgetProviderInfo> getAvailableSearchWidgets(@NonNull Context context) {
        WidgetManagerHelper widgetManagerHelper = new WidgetManagerHelper(context);
        List<AppWidgetProviderInfo> searchWidgets = new ArrayList<>();
        for (AppWidgetProviderInfo info : widgetManagerHelper.getAllProviders(null)) {
            if ((info.widgetCategory & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                searchWidgets.add(info);
            }
        }
        return searchWidgets;
    }

    @Nullable
    public static AppWidgetProviderInfo getSearchWidgetProvider(@NonNull Context context) {
        String providerStr = HotseatQsbSearchProvider.get(context);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

        if (providerStr != null && !providerStr.isEmpty()) {
            if ("none".equals(providerStr)) {
                return null;
            }
            ComponentName component = ComponentName.unflattenFromString(providerStr);
            if (component != null) {
                List<AppWidgetProviderInfo> providers =
                        appWidgetManager.getInstalledProvidersForPackage(
                                component.getPackageName(), null);
                for (AppWidgetProviderInfo providerInfo : providers) {
                    if (providerInfo.provider.equals(component)) {
                        return providerInfo;
                    }
                }
            }
        }

        String providerPkg = getSearchWidgetPackageName(context);
        if (providerPkg == null) {
            return null;
        }

        AppWidgetProviderInfo defaultWidgetForSearchPackage = null;
        for (AppWidgetProviderInfo info :
                appWidgetManager.getInstalledProvidersForPackage(providerPkg, null)) {
            if (info.provider.getPackageName().equals(providerPkg) && info.configure == null) {
                if ((info.widgetCategory
                        & AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX) != 0) {
                    return info;
                } else if (defaultWidgetForSearchPackage == null) {
                    defaultWidgetForSearchPackage = info;
                }
            }
        }
        return defaultWidgetForSearchPackage;
    }

    @WorkerThread
    @Nullable
    public static String getSearchWidgetPackageName(@NonNull Context context) {
        String providerStr = HotseatQsbSearchProvider.get(context);

        if (providerStr != null && !providerStr.isEmpty()) {
            if ("none".equals(providerStr)) {
                return null;
            }
            ComponentName component = ComponentName.unflattenFromString(providerStr);
            if (component != null) {
                return component.getPackageName();
            }
        }

        SearchManager searchManager = context.getSystemService(SearchManager.class);
        ComponentName componentName = searchManager.getGlobalSearchActivity();
        if (componentName != null) {
            return componentName.getPackageName();
        }
        return null;
    }
}
