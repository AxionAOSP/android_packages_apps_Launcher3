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
package com.android.launcher3.allapps.search;

import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_SEARCH_HISTORY;
import static com.android.launcher3.LauncherPrefsExt.SEARCH_HISTORY_ENABLED;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.views.ActivityContext;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public final class AxSearchHistory {

    private static final int MAX_HISTORY_ITEMS = 8;
    private static final String EMPTY_HISTORY = "[]";

    private AxSearchHistory() { }

    public static boolean isEnabled(Context context) {
        return LauncherPrefs.get(context).get(SEARCH_HISTORY_ENABLED);
    }

    public static boolean showHistory(Context context, SearchCallback<AdapterItem> callback) {
        if (!isEnabled(context)) {
            return false;
        }
        ArrayList<AdapterItem> items = getHistoryItems(context);
        if (items.isEmpty()) {
            return false;
        }
        callback.onSearchResult("", items);
        return true;
    }

    public static void recordCurrentQuery(ActivityContext launcher) {
        ActivityAllAppsContainerView<?> appsView = launcher.getAppsView();
        if (appsView == null) {
            return;
        }
        ExtendedEditText editText = appsView.getSearchUiManager().getEditText();
        if (editText == null) {
            return;
        }
        record(launcher.asContext(), editText.getText());
    }

    public static void record(Context context, @Nullable CharSequence rawQuery) {
        if (!isEnabled(context)) {
            return;
        }
        String query = normalizeQuery(rawQuery);
        if (query.isEmpty()) {
            return;
        }
        ArrayList<String> history = readHistory(context);
        removeQuery(history, query);
        history.add(0, query);
        trimHistory(history);
        writeHistory(context, history);
    }

    public static boolean hasHistory(Context context) {
        return !readHistory(context).isEmpty();
    }

    public static void clear(Context context) {
        LauncherPrefs.get(context).put(ALL_APPS_SEARCH_HISTORY, EMPTY_HISTORY);
    }

    private static ArrayList<AdapterItem> getHistoryItems(Context context) {
        ArrayList<String> history = readHistory(context);
        if (history.isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<AdapterItem> items = new ArrayList<>(history.size() + 1);
        items.add(AdapterItem.asSearchSection(
                context.getText(R.string.search_history_section_title),
                R.drawable.ic_allapps_search));
        for (int i = 0; i < history.size(); i++) {
            AdapterItem item = AdapterItem.asSearchHistory(history.get(i));
            item.searchActionGroupPosition = getGroupPosition(i, history.size());
            items.add(item);
        }
        return items;
    }

    private static int getGroupPosition(int index, int size) {
        if (size == 1) {
            return AdapterItem.SEARCH_GROUP_SINGLE;
        }
        if (index == 0) {
            return AdapterItem.SEARCH_GROUP_TOP;
        }
        return index == size - 1 ? AdapterItem.SEARCH_GROUP_BOTTOM
                : AdapterItem.SEARCH_GROUP_MIDDLE;
    }

    private static ArrayList<String> readHistory(Context context) {
        ArrayList<String> history = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(
                    LauncherPrefs.get(context).get(ALL_APPS_SEARCH_HISTORY));
            for (int i = 0; i < array.length(); i++) {
                String query = normalizeQuery(array.optString(i));
                if (!query.isEmpty() && !containsQuery(history, query)) {
                    history.add(query);
                }
            }
        } catch (JSONException e) {
            clear(context);
        }
        trimHistory(history);
        return history;
    }

    private static void writeHistory(Context context, ArrayList<String> history) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < history.size(); i++) {
            array.put(history.get(i));
        }
        LauncherPrefs.get(context).put(ALL_APPS_SEARCH_HISTORY, array.toString());
    }

    private static void trimHistory(ArrayList<String> history) {
        while (history.size() > MAX_HISTORY_ITEMS) {
            history.remove(history.size() - 1);
        }
    }

    private static void removeQuery(ArrayList<String> history, String query) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (query.equalsIgnoreCase(history.get(i))) {
                history.remove(i);
            }
        }
    }

    private static boolean containsQuery(ArrayList<String> history, String query) {
        for (int i = 0; i < history.size(); i++) {
            if (query.equalsIgnoreCase(history.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeQuery(@Nullable CharSequence rawQuery) {
        return rawQuery == null ? "" : Utilities.trim(rawQuery.toString());
    }
}
