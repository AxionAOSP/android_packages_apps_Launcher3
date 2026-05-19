/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;

import org.xmlpull.v1.XmlPullParser;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * Extension of {@link IconProvider} with support for overriding theme icons
 */
@LauncherAppSingleton
public class LauncherIconProvider extends IconProvider {

    private static final String TAG_ICON = "icon";
    private static final String ATTR_PACKAGE = "package";
    private static final String ATTR_DRAWABLE = "drawable";

    private static final String TAG = "LIconProvider";
    private static final Map<String, ThemeData> DISABLED_MAP = Collections.emptyMap();

    private static final String KEY_THEMED_ICON_PACK = "themed_icon_pack";

    private Map<String, ThemeData> mThemedIconMap;
    private String mLoadedThemedIconPack;

    protected final ThemeManager mThemeManager;

    @Inject
    public LauncherIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager) {
        super(context);
        mThemeManager = themeManager;
        mThemedIconMap = FeatureFlags.USE_LOCAL_ICON_OVERRIDES.get() ? null : DISABLED_MAP;
    }

    @Override
    protected ThemeData getThemeDataForPackage(String packageName) {
        return getThemedIconMap().get(packageName);
    }

    @Override
    public void updateSystemState() {
        super.updateSystemState();
        mSystemState += "," + mThemeManager.getIconState().toUniqueId();
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        if (info instanceof ActivityInfo) {
            ActivityInfo activityInfo = (ActivityInfo) info;
            ComponentName cn = new ComponentName(activityInfo.packageName, activityInfo.name);
            Drawable iconPackIcon = ThemedIconSettings.loadIconPackDrawable(mContext, cn, iconDpi);
            if (iconPackIcon != null) {
                return iconPackIcon;
            }
        }
        return super.getIcon(info, iconDpi);
    }

    public void invalidateThemedIconMap() {
        if (mThemedIconMap != DISABLED_MAP) {
            mThemedIconMap = null;
        }
    }

    private String getThemedIconPackPackage() {
        try {
            return Settings.Secure.getString(
                    mContext.getContentResolver(), KEY_THEMED_ICON_PACK);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, ThemeData> getThemedIconMap() {
        String currentPack = getThemedIconPackPackage();
        boolean packChanged = (currentPack == null && mLoadedThemedIconPack != null)
                || (currentPack != null && !currentPack.equals(mLoadedThemedIconPack));
        if (packChanged && mThemedIconMap != DISABLED_MAP) {
            mThemedIconMap = null;
        }

        if (mThemedIconMap != null) {
            return mThemedIconMap;
        }

        ArrayMap<String, ThemeData> map = new ArrayMap<>();

        loadExternalThemedIconPack(map, currentPack);

        Resources res = mContext.getResources();
        try (XmlResourceParser parser = res.getXml(R.xml.grayscale_icon_map)) {
            final int depth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT);

            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) {
                    continue;
                }
                if (TAG_ICON.equals(parser.getName())) {
                    String pkg = parser.getAttributeValue(null, ATTR_PACKAGE);
                    int iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0);
                    if (iconId != 0 && !TextUtils.isEmpty(pkg) && !map.containsKey(pkg)) {
                        map.put(pkg, new ThemeData(res, iconId, mContext));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to parse icon map", e);
        }

        mLoadedThemedIconPack = currentPack;
        mThemedIconMap = map;
        return mThemedIconMap;
    }

    private void loadExternalThemedIconPack(ArrayMap<String, ThemeData> map, String packPackage) {
        if (packPackage == null || packPackage.isEmpty()) return;

        try {
            Resources packRes = mContext.getPackageManager()
                    .getResourcesForApplication(packPackage);

            int mapResId = packRes.getIdentifier(
                    "grayscale_icon_map", "xml", packPackage);
            if (mapResId != 0) {
                loadThemedIconMapFromResource(map, packRes, mapResId, packPackage);
                return;
            }

            int filterResId = packRes.getIdentifier("appfilter", "xml", packPackage);
            if (filterResId != 0) {
                loadThemedIconMapFromAppFilter(map, packRes, filterResId, packPackage);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Themed icon pack not found: " + packPackage);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load themed icon pack: " + packPackage, e);
        }
    }

    private void loadThemedIconMapFromResource(ArrayMap<String, ThemeData> map,
            Resources packRes, int resId, String packPackage) {
        try (XmlResourceParser parser = packRes.getXml(resId)) {
            final int depth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT);

            while (((type = parser.next()) != XmlPullParser.END_TAG
                    || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) continue;
                if (TAG_ICON.equals(parser.getName())) {
                    String pkg = parser.getAttributeValue(null, ATTR_PACKAGE);
                    int iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0);
                    if (iconId != 0 && !TextUtils.isEmpty(pkg)) {
                        map.put(pkg, new ThemeData(packRes, iconId, mContext));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse grayscale_icon_map from " + packPackage, e);
        }
    }

    private void loadThemedIconMapFromAppFilter(ArrayMap<String, ThemeData> map,
            Resources packRes, int resId, String packPackage) {
        try (XmlResourceParser parser = packRes.getXml(resId)) {
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) continue;
                if (!"item".equals(parser.getName())) continue;

                String component = parser.getAttributeValue(null, "component");
                String drawableName = parser.getAttributeValue(null, ATTR_DRAWABLE);
                if (component == null || drawableName == null) continue;

                String pkg = extractPackageFromComponent(component);
                if (pkg == null || map.containsKey(pkg)) continue;

                String fgName = drawableName + "_foreground";
                int fgId = packRes.getIdentifier(fgName, "drawable", packPackage);
                if (fgId == 0) {
                    fgId = packRes.getIdentifier(drawableName, "drawable", packPackage);
                }
                if (fgId != 0) {
                    map.put(pkg, new ThemeData(packRes, fgId, mContext));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse appfilter from " + packPackage, e);
        }
    }

    private static String extractPackageFromComponent(String component) {
        if (component == null) return null;
        if (component.startsWith("ComponentInfo{") && component.endsWith("}")) {
            String inner = component.substring(14, component.length() - 1);
            int slash = inner.indexOf('/');
            return slash > 0 ? inner.substring(0, slash) : null;
        }
        return null;
    }
}
