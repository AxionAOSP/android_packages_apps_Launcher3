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

import static android.content.pm.ApplicationInfo.CATEGORY_AUDIO;
import static android.content.pm.ApplicationInfo.CATEGORY_GAME;
import static android.content.pm.ApplicationInfo.CATEGORY_IMAGE;
import static android.content.pm.ApplicationInfo.CATEGORY_MAPS;
import static android.content.pm.ApplicationInfo.CATEGORY_NEWS;
import static android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY;
import static android.content.pm.ApplicationInfo.CATEGORY_SOCIAL;
import static android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED;
import static android.content.pm.ApplicationInfo.CATEGORY_VIDEO;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_LAYOUT_MODE;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_SMART_DRAWER_FOLDERS;
import static com.android.launcher3.LauncherPrefsExt.PINNED_APPS;
import static com.android.launcher3.LauncherPrefsExt.SHOW_ALLAPPS_PREDICTIONS;

import android.content.Context;
import android.content.pm.PackageManager;

import com.android.launcher3.AxPreferenceFeature;
import com.android.launcher3.Item;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.DaggerSingletonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxSmartDrawerManager extends AxPreferenceFeature {

    public static final int DRAWER_LAYOUT_DEFAULT = 0;
    public static final int DRAWER_LAYOUT_SMART = 1;

    private static final String CATEGORY_PREFIX = "category:";
    private static final String FOLDER_PREFIX = "folder:";
    private static final String PINNED_ID = "pinned";
    private static final String PREDICTIONS_ID = "predictions";
    private static final List<Item> SMART_DRAWER_ITEMS = List.of(
            ALL_APPS_DRAWER_LAYOUT_MODE,
            ALL_APPS_SMART_DRAWER_FOLDERS,
            PINNED_APPS,
            SHOW_ALLAPPS_PREDICTIONS);

    public static final DaggerSingletonObject<AxSmartDrawerManager> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getSmartDrawerManager);

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final List<String> mPredictionKeys = new ArrayList<>();

    @Inject
    public AxSmartDrawerManager(@ApplicationContext Context context) {
        super(SMART_DRAWER_ITEMS);
        mContext = context;
        mPackageManager = context.getPackageManager();
    }

    public static boolean isEnabled(Context context) {
        return LauncherPrefs.get(context).get(ALL_APPS_DRAWER_LAYOUT_MODE) == DRAWER_LAYOUT_SMART;
    }

    public boolean isEnabled() {
        return isEnabled(mContext);
    }

    public void setPredictedItems(List<ItemInfo> items) {
        mPredictionKeys.clear();
        for (ItemInfo item : items) {
            String key = PinnedApps.encode(mContext, item);
            if (key != null && !mPredictionKeys.contains(key)) {
                mPredictionKeys.add(key);
            }
        }
    }

    public List<AxSmartDrawerCategory> getEntries(List<AppInfo> apps) {
        List<AxSmartDrawerCategory> entries = new ArrayList<>();
        Map<String, AppInfo> appsByKey = mapApps(apps);
        List<AppInfo> pinnedApps = PinnedApps.getPinnedApps(mContext, apps);
        Set<String> excludedKeys = new HashSet<>();
        for (AppInfo app : pinnedApps) {
            String key = PinnedApps.encode(mContext, app);
            if (key != null) {
                excludedKeys.add(key);
            }
        }
        if (!pinnedApps.isEmpty()) {
            entries.add(new AxSmartDrawerCategory(PINNED_ID,
                    mContext.getText(R.string.title_pinned_apps), pinnedApps,
                    AxSmartDrawerCategory.TYPE_PINNED));
        }

        List<AllAppsFolderInfo> customFolders = getVisibleFolders(
                AxSmartDrawerFolderStore.getFolders(mContext, apps), excludedKeys);
        Set<String> customFolderKeys = getFolderedKeys(customFolders);

        Set<String> predictedExcludedKeys = new HashSet<>(excludedKeys);
        predictedExcludedKeys.addAll(customFolderKeys);
        List<AppInfo> predictions = getPredictedApps(appsByKey, predictedExcludedKeys);
        if (LauncherPrefs.get(mContext).get(SHOW_ALLAPPS_PREDICTIONS)
                && !predictions.isEmpty()) {
            entries.add(new AxSmartDrawerCategory(PREDICTIONS_ID,
                    mContext.getText(R.string.title_app_suggestions), predictions,
                    AxSmartDrawerCategory.TYPE_PREDICTIONS));
        }

        for (AllAppsFolderInfo folder : customFolders) {
            entries.add(new AxSmartDrawerCategory(FOLDER_PREFIX + folder.getId(),
                    folder.getTitle(), folder.getApps(),
                    AxSmartDrawerCategory.TYPE_CUSTOM_FOLDER));
        }
        excludedKeys.addAll(customFolderKeys);

        Map<Integer, List<AppInfo>> groupedApps = new LinkedHashMap<>();
        for (int category : getCategoryOrder()) {
            groupedApps.put(category, new ArrayList<>());
        }
        for (AppInfo app : apps) {
            String key = PinnedApps.encode(mContext, app);
            if (key != null && excludedKeys.contains(key)) {
                continue;
            }
            int category = resolveCategory(app);
            groupedApps.get(category).add(app);
        }
        for (Map.Entry<Integer, List<AppInfo>> entry : groupedApps.entrySet()) {
            List<AppInfo> categoryApps = entry.getValue();
            if (!categoryApps.isEmpty()) {
                entries.add(new AxSmartDrawerCategory(CATEGORY_PREFIX + entry.getKey(),
                        getCategoryTitle(entry.getKey()), categoryApps,
                        AxSmartDrawerCategory.TYPE_CATEGORY));
            }
        }
        return entries;
    }

    private List<AllAppsFolderInfo> getVisibleFolders(List<AllAppsFolderInfo> folders,
            Set<String> excludedKeys) {
        List<AllAppsFolderInfo> visibleFolders = new ArrayList<>();
        for (AllAppsFolderInfo folder : folders) {
            List<AppInfo> folderApps = new ArrayList<>();
            for (AppInfo app : folder.getApps()) {
                String key = PinnedApps.encode(mContext, app);
                if (key != null && !excludedKeys.contains(key)) {
                    folderApps.add(app);
                }
            }
            if (!folderApps.isEmpty()) {
                visibleFolders.add(new AllAppsFolderInfo(folder.getId(), folder.getTitle(),
                        folderApps));
            }
        }
        return visibleFolders;
    }

    private Set<String> getFolderedKeys(List<AllAppsFolderInfo> folders) {
        Set<String> folderedKeys = new HashSet<>();
        for (AllAppsFolderInfo folder : folders) {
            for (AppInfo app : folder.getApps()) {
                String key = PinnedApps.encode(mContext, app);
                if (key != null) {
                    folderedKeys.add(key);
                }
            }
        }
        return folderedKeys;
    }

    private Map<String, AppInfo> mapApps(List<AppInfo> apps) {
        Map<String, AppInfo> appsByKey = new HashMap<>();
        for (AppInfo app : apps) {
            String key = PinnedApps.encode(mContext, app);
            if (key != null) {
                appsByKey.put(key, app);
            }
        }
        return appsByKey;
    }

    private List<AppInfo> getPredictedApps(Map<String, AppInfo> appsByKey, Set<String> excludedKeys) {
        List<AppInfo> predictions = new ArrayList<>();
        for (String key : mPredictionKeys) {
            if (excludedKeys.contains(key)) {
                continue;
            }
            AppInfo app = appsByKey.get(key);
            if (app != null) {
                predictions.add(app);
            }
            if (predictions.size() == 4) {
                break;
            }
        }
        return predictions;
    }

    private int resolveCategory(AppInfo app) {
        String packageName = app.getTargetPackage();
        if (packageName == null) {
            return CATEGORY_UNDEFINED;
        }
        return resolveCategory(mPackageManager, packageName);
    }

    public static int resolveCategory(Context context, String packageName) {
        if (packageName == null) {
            return CATEGORY_UNDEFINED;
        }
        return resolveCategory(context.getPackageManager(), packageName);
    }

    private static int resolveCategory(PackageManager packageManager, String packageName) {
        try {
            int category = packageManager.getApplicationInfo(packageName, 0).category;
            return switch (category) {
                case CATEGORY_AUDIO, CATEGORY_VIDEO, CATEGORY_IMAGE -> CATEGORY_VIDEO;
                case CATEGORY_GAME, CATEGORY_MAPS, CATEGORY_NEWS, CATEGORY_PRODUCTIVITY,
                        CATEGORY_SOCIAL -> category;
                default -> CATEGORY_UNDEFINED;
            };
        } catch (PackageManager.NameNotFoundException e) {
            return CATEGORY_UNDEFINED;
        }
    }

    private CharSequence getCategoryTitle(int category) {
        return mContext.getText(getCategoryTitleRes(category));
    }

    public static int getCategoryTitleRes(int category) {
        return switch (category) {
            case CATEGORY_GAME -> R.string.smart_drawer_category_games;
            case CATEGORY_SOCIAL -> R.string.smart_drawer_category_social;
            case CATEGORY_VIDEO -> R.string.smart_drawer_category_media;
            case CATEGORY_NEWS -> R.string.smart_drawer_category_news;
            case CATEGORY_MAPS -> R.string.smart_drawer_category_travel;
            case CATEGORY_PRODUCTIVITY -> R.string.smart_drawer_category_productivity;
            default -> R.string.smart_drawer_category_other;
        };
    }

    public static int[] getCategoryOrder() {
        return new int[] {
                CATEGORY_GAME,
                CATEGORY_SOCIAL,
                CATEGORY_VIDEO,
                CATEGORY_PRODUCTIVITY,
                CATEGORY_NEWS,
                CATEGORY_MAPS,
                CATEGORY_UNDEFINED,
        };
    }
}
