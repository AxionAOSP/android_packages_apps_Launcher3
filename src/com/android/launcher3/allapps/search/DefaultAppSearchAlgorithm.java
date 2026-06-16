/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_EMPTY_SEARCH;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.AnyThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    private final LauncherAppState mAppState;
    private final Handler mResultHandler;
    private final boolean mAddNoResultsMessage;
    private final AtomicInteger mSearchToken = new AtomicInteger();

    public DefaultAppSearchAlgorithm(Context context) {
        this(context, false);
    }

    public DefaultAppSearchAlgorithm(Context context, boolean addNoResultsMessage) {
        mAppState = LauncherAppState.getInstance(context);
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
        mAddNoResultsMessage = addNoResultsMessage;
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mSearchToken.incrementAndGet();
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        int searchToken = mSearchToken.incrementAndGet();
        int delayMs = getSearchDelayMs();
        if (delayMs > 0) {
            mResultHandler.postDelayed(() -> enqueueSearch(query, callback, searchToken), delayMs);
        } else {
            enqueueSearch(query, callback, searchToken);
        }
    }

    private int getSearchDelayMs() {
        return Utilities.boundToRange(
                LauncherPrefs.get(mAppState.getContext())
                        .get(LauncherPrefsExt.ALL_APPS_SEARCH_WEB_DELAY_MS),
                0, 1000);
    }

    private void enqueueSearch(String query, SearchCallback<AdapterItem> callback,
            int searchToken) {
        mAppState.getModel().enqueueModelUpdateTask((taskController, dataModel, apps) ->  {
            if (searchToken != mSearchToken.get()) {
                return;
            }
            ArrayList<AdapterItem> result = getTitleMatchResult(mAppState.getContext(), apps.data,
                    query);
            if (searchToken != mSearchToken.get()) {
                return;
            }
            if (mAddNoResultsMessage && result.isEmpty()) {
                result.add(getEmptyMessageAdapterItem(query));
            }
            mResultHandler.post(() -> {
                if (searchToken == mSearchToken.get()) {
                    callback.onSearchResult(query, result);
                }
            });
        });
    }

    private static AdapterItem getEmptyMessageAdapterItem(String query) {
        AdapterItem item = new AdapterItem(VIEW_TYPE_EMPTY_SEARCH);
        // Add a place holder info to propagate the query
        AppInfo placeHolder = new AppInfo();
        placeHolder.title = query;
        item.itemInfo = placeHolder;
        return item;
    }

    /**
     * Filters {@link AppInfo}s matching specified query
     */
    @AnyThread
    public static ArrayList<AdapterItem> getTitleMatchResult(Context context, List<AppInfo> apps,
            String query) {
        return AxUniversalSearchProvider.getSearchResults(context, apps, query);
    }

}
