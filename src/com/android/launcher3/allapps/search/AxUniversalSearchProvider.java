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

import static com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem.SEARCH_GROUP_BOTTOM;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem.SEARCH_GROUP_MIDDLE;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem.SEARCH_GROUP_TOP;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_PILL;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.text.TextUtils;

import com.android.axion.search.UniversalSearchProvider;
import com.android.axion.search.UniversalSearchResult;
import com.android.launcher3.ConstantItem;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.PackageManagerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AxUniversalSearchProvider {

    private static final int MAX_RESULT_LIMIT = 20;
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube";
    private static final String SPOTIFY_PACKAGE = "com.spotify.music";
    private static final String YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music";

    private AxUniversalSearchProvider() { }

    public static ArrayList<AdapterItem> getSearchResults(Context context, List<AppInfo> apps,
            String query) {
        String queryText = query == null ? "" : query.trim();
        String queryTextLower = queryText.toLowerCase();
        String normalizedQuery = normalize(queryText);
        ArrayList<AdapterItem> result = new ArrayList<>();
        if (queryTextLower.isEmpty()) {
            return result;
        }

        SearchResultSettings settings = SearchResultSettings.from(context);
        ArrayList<ScoredApp> scoredApps = new ArrayList<>();
        if (settings.showApps || settings.showAppActions) {
            StringMatcherUtility.StringMatcher matcher =
                    StringMatcherUtility.StringMatcher.getInstance();
            scoredApps = getScoredApps(context, apps, queryText, queryTextLower, normalizedQuery,
                    matcher, settings.fuzzyAppSearch);
        }
        if (settings.showApps) {
            ArrayList<AdapterItem> appResults = new ArrayList<>();
            int resultCount = Math.min(scoredApps.size(), settings.maxAppResults);
            for (int i = 0; i < resultCount; i++) {
                appResults.add(AdapterItem.asApp(scoredApps.get(i).appInfo));
            }
            result.addAll(appResults);
        }
        if (settings.showAppActions) {
            addActionGroup(context, result, R.string.search_section_app_actions,
                    R.drawable.ic_allapps_search, getAppShortcutActions(context, scoredApps,
                            settings.maxAppActionResults));
        }
        addSdkSearchResultGroups(context, result, queryText, settings);
        return result;
    }

    private static ArrayList<ScoredApp> getScoredApps(Context context, List<AppInfo> apps,
            String queryText, String queryTextLower, String normalizedQuery,
            StringMatcherUtility.StringMatcher matcher, boolean fuzzyAppSearch) {
        ArrayList<ScoredApp> scoredApps = new ArrayList<>();
        UserManager userManager = UserManager.get(context);
        int total = apps.size();
        for (int i = 0; i < total; i++) {
            AppInfo info = apps.get(i);
            if (userManager.isQuietModeEnabled(info.user)
                    || !PackageManagerHelper.isLauncherAppTarget(info.intent)) {
                continue;
            }
            int score = getAppMatchScore(queryText, queryTextLower, normalizedQuery, info,
                    matcher, fuzzyAppSearch);
            if (score > 0) {
                scoredApps.add(new ScoredApp(info, score));
            }
        }
        scoredApps.sort(AxUniversalSearchProvider::compareScoredApps);
        return scoredApps;
    }

    private static int getAppMatchScore(String queryText, String queryTextLower,
            String normalizedQuery, AppInfo info, StringMatcherUtility.StringMatcher matcher,
            boolean fuzzyAppSearch) {
        String title = info.title == null ? "" : info.title.toString();
        int titleScore = getTextMatchScore(queryText, queryTextLower, normalizedQuery, title,
                matcher, fuzzyAppSearch);
        if (titleScore > 0) {
            return titleScore;
        }
        String packageName = info.getTargetPackage();
        if (containsLower(packageName, queryTextLower)) {
            return 35;
        }
        ComponentName componentName = info.componentName;
        return componentName != null && componentName.flattenToString().toLowerCase()
                .contains(queryTextLower) ? 30 : 0;
    }

    private static int getTextMatchScore(String queryText, String queryTextLower,
            String normalizedQuery, String title, StringMatcherUtility.StringMatcher matcher,
            boolean fuzzyAppSearch) {
        String titleLower = title.toLowerCase();
        String normalizedTitle = normalize(title);
        if (title.equalsIgnoreCase(queryText)) {
            return 100;
        }
        if (!normalizedQuery.isEmpty() && normalizedTitle.equals(normalizedQuery)) {
            return 95;
        }
        if (titleLower.startsWith(queryTextLower)) {
            return 80;
        }
        if (!normalizedQuery.isEmpty() && normalizedTitle.startsWith(normalizedQuery)) {
            return 75;
        }
        if (fuzzyAppSearch && StringMatcherUtility.matches(queryTextLower, title, matcher)) {
            return 60;
        }
        if (titleLower.contains(queryTextLower)) {
            return 45;
        }
        return !normalizedQuery.isEmpty() && normalizedTitle.contains(normalizedQuery) ? 40 : 0;
    }

    private static ArrayList<AdapterItem> getAppShortcutActions(Context context,
            List<ScoredApp> scoredApps, int maxResults) {
        ArrayList<AdapterItem> result = new ArrayList<>();
        if (maxResults <= 0) {
            return result;
        }
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
        if (launcherApps == null) {
            return result;
        }
        int densityDpi = context.getResources().getDisplayMetrics().densityDpi;
        Set<String> seenPackages = new HashSet<>();
        Set<String> seenShortcuts = new HashSet<>();
        for (ScoredApp scoredApp : scoredApps) {
            AppInfo appInfo = scoredApp.appInfo;
            String packageName = appInfo.getTargetPackage();
            if (packageName == null || !seenPackages.add(packageName)) {
                continue;
            }
            List<ShortcutInfo> shortcuts = new ShortcutRequest(context, appInfo.user)
                    .forPackage(packageName).query(ShortcutRequest.PUBLISHED);
            for (ShortcutInfo shortcutInfo : shortcuts) {
                CharSequence label = getShortcutLabel(shortcutInfo);
                if (!shortcutInfo.isEnabled() || TextUtils.isEmpty(label)
                        || !seenShortcuts.add(getShortcutKey(shortcutInfo))) {
                    continue;
                }
                Drawable icon;
                try {
                    icon = launcherApps.getShortcutIconDrawable(shortcutInfo, densityDpi);
                } catch (SecurityException | IllegalStateException e) {
                    icon = null;
                }
                AdapterItem item = icon == null
                        ? AdapterItem.asSearchPill(label, R.drawable.ic_allapps_search, null)
                        : AdapterItem.asSearchPill(label, icon, null);
                item.searchActionShortcut = shortcutInfo;
                item.searchActionEndIconRes = 0;
                result.add(item);
                if (result.size() >= maxResults) {
                    return result;
                }
            }
        }
        return result;
    }

    private static void addSdkSearchResultGroups(Context context, ArrayList<AdapterItem> result,
            String queryText, SearchResultSettings settings) {
        if (settings.sdkSearchMask == 0) {
            return;
        }
        ArrayList<AdapterItem> settingsResults = new ArrayList<>();
        ArrayList<AdapterItem> answers = new ArrayList<>();
        ArrayList<AdapterItem> contacts = new ArrayList<>();
        ArrayList<AdapterItem> images = new ArrayList<>();
        ArrayList<AdapterItem> files = new ArrayList<>();
        ArrayList<AdapterItem> calendar = new ArrayList<>();
        ArrayList<AdapterItem> web = new ArrayList<>();
        ArrayList<AdapterItem> inApps = new ArrayList<>();
        ArrayList<AdapterItem> youtube = new ArrayList<>();
        ArrayList<AdapterItem> spotify = new ArrayList<>();
        ArrayList<AdapterItem> youtubeMusic = new ArrayList<>();
        ArrayList<AdapterItem> media = new ArrayList<>();
        ArrayList<UniversalSearchResult> searchResults = UniversalSearchProvider.getSearchResults(
                context, queryText, false, settings.sdkSearchMask);
        int total = searchResults.size();
        for (int i = 0; i < total; i++) {
            UniversalSearchResult searchResult = searchResults.get(i);
            AdapterItem item = toAdapterItem(searchResult);
            switch (searchResult.getType()) {
                case UniversalSearchResult.TYPE_ANSWER -> answers.add(item);
                case UniversalSearchResult.TYPE_SETTINGS -> settingsResults.add(item);
                case UniversalSearchResult.TYPE_CONTACT -> contacts.add(item);
                case UniversalSearchResult.TYPE_IMAGE -> images.add(item);
                case UniversalSearchResult.TYPE_FILE -> files.add(item);
                case UniversalSearchResult.TYPE_CALENDAR -> calendar.add(item);
                case UniversalSearchResult.TYPE_WEB -> web.add(item);
                case UniversalSearchResult.TYPE_IN_APP -> inApps.add(item);
                case UniversalSearchResult.TYPE_MEDIA -> addMediaResult(searchResult, item,
                        youtube, spotify, youtubeMusic, media);
            }
        }
        trimResults(answers, settings.maxExternalResults);
        trimResults(settingsResults, settings.maxExternalResults);
        trimResults(contacts, settings.maxExternalResults);
        trimResults(images, settings.maxExternalResults);
        trimResults(files, settings.maxExternalResults);
        trimResults(calendar, settings.maxExternalResults);
        trimResults(web, settings.maxExternalResults);
        trimResults(inApps, settings.maxExternalResults);
        trimResults(youtube, settings.maxExternalResults);
        trimResults(spotify, settings.maxExternalResults);
        trimResults(youtubeMusic, settings.maxExternalResults);
        trimResults(media, settings.maxExternalResults);
        addActionGroup(context, result, R.string.search_section_quick_answers,
                R.drawable.ic_universal_search_calculator, answers);
        addSourceActionGroup(context, result, R.string.search_section_youtube, YOUTUBE_PACKAGE,
                youtube);
        addSourceActionGroup(context, result, R.string.search_section_spotify, SPOTIFY_PACKAGE,
                spotify);
        addSourceActionGroup(context, result, R.string.search_section_youtube_music,
                YOUTUBE_MUSIC_PACKAGE, youtubeMusic);
        addActionGroup(context, result, R.string.search_section_media,
                R.drawable.ic_universal_search_language, media);
        addActionGroup(context, result, R.string.search_section_web,
                R.drawable.ic_allapps_search, web);
        addActionGroup(context, result, R.string.search_section_settings,
                R.drawable.ic_home_settings_search, settingsResults);
        addActionGroup(context, result, R.string.search_section_contacts,
                R.drawable.ic_universal_search_contact, contacts);
        addActionGroup(context, result, R.string.search_section_images,
                R.drawable.ic_universal_search_image, images);
        addActionGroup(context, result, R.string.search_section_files,
                R.drawable.ic_universal_search_file, files);
        addActionGroup(context, result, R.string.search_section_calendar,
                R.drawable.ic_universal_search_calendar, calendar);
        addActionGroup(context, result, R.string.search_section_in_apps, R.drawable.ic_apps,
                inApps);
    }


    private static void trimResults(ArrayList<AdapterItem> items, int maxResults) {
        if (maxResults < 0) {
            return;
        }
        while (items.size() > maxResults) {
            items.remove(items.size() - 1);
        }
    }

    private static void addMediaResult(UniversalSearchResult searchResult, AdapterItem item,
            ArrayList<AdapterItem> youtube, ArrayList<AdapterItem> spotify,
            ArrayList<AdapterItem> youtubeMusic, ArrayList<AdapterItem> media) {
        String sourcePackage = searchResult.getSourcePackage();
        if (YOUTUBE_PACKAGE.equals(sourcePackage)) {
            youtube.add(item);
        } else if (SPOTIFY_PACKAGE.equals(sourcePackage)) {
            spotify.add(item);
        } else if (YOUTUBE_MUSIC_PACKAGE.equals(sourcePackage)) {
            youtubeMusic.add(item);
        } else {
            media.add(item);
        }
    }

    private static AdapterItem toAdapterItem(UniversalSearchResult searchResult) {
        Drawable icon = searchResult.getIcon();
        AdapterItem item;
        if (icon == null) {
            item = AdapterItem.asSearchAction(searchResult.getTitle(),
                    searchResult.getSubtitle(), R.drawable.ic_allapps_search,
                    searchResult.getIntent());
        } else {
            item = AdapterItem.asSearchAction(searchResult.getTitle(),
                    searchResult.getSubtitle(), icon, searchResult.getIntent(),
                    searchResult.isIconFullBleed());
        }
        item.searchActionIconTinted = searchResult.isIconTinted() || icon == null;
        item.searchActionThumbnailTrailing = searchResult.getType()
                == UniversalSearchResult.TYPE_WEB && searchResult.isIconFullBleed();
        if (searchResult.isExternal()) {
            item.searchActionEndIconRes = R.drawable.ic_search_north_west;
        }
        return item;
    }

    private static void addActionGroup(Context context, ArrayList<AdapterItem> result,
            int titleRes, int iconRes, ArrayList<AdapterItem> items) {
        if (items.isEmpty()) {
            return;
        }
        AdapterItem header = AdapterItem.asSearchSection(context.getString(titleRes), iconRes);
        boolean usesPillItems = items.get(0).viewType == VIEW_TYPE_SEARCH_PILL;
        header.searchActionGroupPosition = AdapterItem.SEARCH_GROUP_SINGLE;
        result.add(header);
        if (!usesPillItems) {
            setActionGroupPositions(items);
        }
        result.addAll(items);
    }

    private static void addSourceActionGroup(Context context, ArrayList<AdapterItem> result,
            int titleRes, String packageName, ArrayList<AdapterItem> items) {
        if (items.isEmpty()) {
            return;
        }
        Drawable icon = getApplicationIcon(context, packageName);
        if (icon == null && !items.get(0).searchActionIconTinted
                && !items.get(0).searchActionIconFullBleed) {
            icon = items.get(0).searchActionIcon;
        }
        AdapterItem header = icon == null
                ? AdapterItem.asSearchSection(context.getString(titleRes),
                        R.drawable.ic_universal_search_language)
                : AdapterItem.asSearchSection(context.getString(titleRes), icon,
                        R.drawable.ic_universal_search_language);
        header.searchActionGroupPosition = AdapterItem.SEARCH_GROUP_SINGLE;
        result.add(header);
        setActionGroupPositions(items);
        result.addAll(items);
    }

    private static void setActionGroupPositions(ArrayList<AdapterItem> items) {
        int lastIndex = items.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            items.get(i).searchActionGroupPosition = lastIndex == 0
                    ? AdapterItem.SEARCH_GROUP_SINGLE
                    : i == 0 ? SEARCH_GROUP_TOP
                    : i == lastIndex ? SEARCH_GROUP_BOTTOM : SEARCH_GROUP_MIDDLE;
        }
    }

    private static CharSequence getShortcutLabel(ShortcutInfo shortcutInfo) {
        CharSequence label = shortcutInfo.getShortLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getLongLabel();
        }
        return TextUtils.isEmpty(label) ? shortcutInfo.getId() : label;
    }

    private static String getShortcutKey(ShortcutInfo shortcutInfo) {
        return shortcutInfo.getPackage() + "/" + shortcutInfo.getId() + "/"
                + shortcutInfo.getUserHandle();
    }

    private static boolean containsLower(String value, String queryTextLower) {
        return value != null && value.toLowerCase().contains(queryTextLower);
    }

    private static Drawable getApplicationIcon(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static int compareScoredApps(ScoredApp left, ScoredApp right) {
        int scoreComparison = Integer.compare(right.score, left.score);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        String leftTitle = left.appInfo.title == null ? "" : left.appInfo.title.toString();
        String rightTitle = right.appInfo.title == null ? "" : right.appInfo.title.toString();
        return leftTitle.compareToIgnoreCase(rightTitle);
    }

    private static String normalize(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(Character.toLowerCase(c));
            }
        }
        return builder.toString();
    }

    private static final class SearchResultSettings {
        final boolean showApps;
        final boolean showAppActions;
        final boolean fuzzyAppSearch;
        final int maxAppResults;
        final int maxAppActionResults;
        final int maxExternalResults;
        final int sdkSearchMask;

        private SearchResultSettings(boolean showApps, boolean showAppActions,
                boolean fuzzyAppSearch, int maxAppResults, int maxAppActionResults,
                int maxExternalResults, int sdkSearchMask) {
            this.showApps = showApps;
            this.showAppActions = showAppActions;
            this.fuzzyAppSearch = fuzzyAppSearch;
            this.maxAppResults = maxAppResults;
            this.maxAppActionResults = maxAppActionResults;
            this.maxExternalResults = maxExternalResults;
            this.sdkSearchMask = sdkSearchMask;
        }

        static SearchResultSettings from(Context context) {
            LauncherPrefs launcherPrefs = LauncherPrefs.get(context);
            int sdkSearchMask = 0;
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_QUICK_ANSWERS)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_ANSWER;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_SETTINGS)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_SETTINGS;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_CONTACTS)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_CONTACT;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_IMAGES)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_IMAGE;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_FILES)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_FILE;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_CALENDAR)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_CALENDAR;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_WEB)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_WEB;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_IN_APPS)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_IN_APP;
            }
            if (launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_MEDIA)) {
                sdkSearchMask |= UniversalSearchResult.TYPE_MASK_MEDIA;
            }
            return new SearchResultSettings(
                    launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_APPS),
                    launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_RESULT_APP_ACTIONS),
                    launcherPrefs.get(LauncherPrefsExt.ALL_APPS_SEARCH_FUZZY_APPS),
                    getBoundedResultLimit(launcherPrefs, LauncherPrefsExt.ALL_APPS_SEARCH_MAX_APPS),
                    getBoundedResultLimit(launcherPrefs,
                            LauncherPrefsExt.ALL_APPS_SEARCH_MAX_APP_ACTIONS),
                    getBoundedResultLimit(launcherPrefs,
                            LauncherPrefsExt.ALL_APPS_SEARCH_MAX_EXTERNAL_RESULTS),
                    sdkSearchMask);
        }

        private static int getBoundedResultLimit(LauncherPrefs launcherPrefs,
                ConstantItem<Integer> item) {
            return Utilities.boundToRange(launcherPrefs.get(item), 0, MAX_RESULT_LIMIT);
        }
    }

    private static final class ScoredApp {
        final AppInfo appInfo;
        final int score;

        ScoredApp(AppInfo appInfo, int score) {
            this.appInfo = appInfo;
            this.score = score;
        }
    }
}
