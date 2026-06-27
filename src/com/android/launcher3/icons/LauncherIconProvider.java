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
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.customicon.IconOverride;
import com.android.launcher3.icons.customicon.IconOverrideRepository;
import com.android.launcher3.icons.customicon.IconPackDrawableResolver;
import com.android.launcher3.icons.customicon.IconPackPreferenceStore;

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

    private Map<String, ThemeData> mThemedIconMap;
    private Map<String, ThemeData> mExternalThemedIconMap;
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
        mSystemState += "," + mThemeManager.getIconState().toUniqueId()
                + "," + IconPackPreferenceStore.getIconPackPackage(mContext)
                + "," + getThemedIconPackPackage()
                + "," + LauncherPrefsExt.ICON_OVERRIDES.get(mContext).hashCode();
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        if (info instanceof ActivityInfo activityInfo) {
            ComponentName componentName =
                    new ComponentName(activityInfo.packageName, activityInfo.name);
            IconOverride override = IconOverrideRepository.getOverride(mContext, componentName);
            if (override != null) {
                Drawable customIcon = IconPackDrawableResolver.loadDrawable(
                        mContext,
                        override.getPackPackage(),
                        override.getDrawableName(),
                        iconDpi);
                if (customIcon != null) {
                    return customIcon;
                }
            }
            Drawable iconPackIcon = IconPackDrawableResolver.loadForComponent(
                    mContext,
                    IconPackPreferenceStore.getIconPackPackage(mContext),
                    componentName,
                    iconDpi);
            if (iconPackIcon != null) {
                return iconPackIcon;
            }
            Drawable icon = super.getIcon(info, iconDpi);
            ThemeData themeData = getExternalThemeDataForPackage(activityInfo.packageName);
            if (icon instanceof AdaptiveIconDrawable adaptiveIcon && themeData != null) {
                Drawable themedIcon = themeData.loadPaddedDrawable();
                if (themedIcon != null) {
                    return new AdaptiveIconDrawable(
                            adaptiveIcon.getBackground(), adaptiveIcon.getForeground(), themedIcon);
                }
            }
            return icon;
        }
        return super.getIcon(info, iconDpi);
    }

    private String getThemedIconPackPackage() {
        return IconPackPreferenceStore.getThemedIconPackPackage(mContext);
    }

    private Map<String, ThemeData> getThemedIconMap() {
        String currentPack = getThemedIconPackPackage();
        boolean packChanged = (currentPack == null && mLoadedThemedIconPack != null)
                || (currentPack != null && !currentPack.equals(mLoadedThemedIconPack));
        if (packChanged && mThemedIconMap != DISABLED_MAP) {
            mThemedIconMap = null;
            mExternalThemedIconMap = null;
        }
        if (mThemedIconMap != null) {
            return mThemedIconMap;
        }
        ArrayMap<String, ThemeData> map = new ArrayMap<>();
        loadExternalThemedIconPack(map, currentPack);
        mExternalThemedIconMap = new ArrayMap<>(map);
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
                        map.put(pkg, new ThemeData(res, iconId));
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

    private ThemeData getExternalThemeDataForPackage(String packageName) {
        getThemedIconMap();
        return mExternalThemedIconMap != null ? mExternalThemedIconMap.get(packageName) : null;
    }

    private void loadExternalThemedIconPack(ArrayMap<String, ThemeData> map, String packPackage) {
        if (TextUtils.isEmpty(packPackage)) {
            return;
        }
        try {
            Resources packRes = mContext.getPackageManager()
                    .getResourcesForApplication(packPackage);
            int mapResId = packRes.getIdentifier("grayscale_icon_map", "xml", packPackage);
            if (mapResId != 0) {
                loadThemedIconMapFromResource(map, packRes, mapResId);
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

    private void loadThemedIconMapFromResource(
            ArrayMap<String, ThemeData> map, Resources packRes, int resId) {
        try (XmlResourceParser parser = packRes.getXml(resId)) {
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
                    if (iconId != 0 && !TextUtils.isEmpty(pkg)) {
                        map.put(pkg, new ThemeData(packRes, iconId));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse themed icon map", e);
        }
    }

    private void loadThemedIconMapFromAppFilter(
            ArrayMap<String, ThemeData> map, Resources packRes, int resId, String packPackage) {
        try (XmlResourceParser parser = packRes.getXml(resId)) {
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG || !"item".equals(parser.getName())) {
                    continue;
                }
                String component = parser.getAttributeValue(null, "component");
                String drawableName = parser.getAttributeValue(null, ATTR_DRAWABLE);
                if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawableName)) {
                    continue;
                }
                String pkg = extractPackageFromComponent(component);
                if (TextUtils.isEmpty(pkg) || map.containsKey(pkg)) {
                    continue;
                }
                int iconId = IconPackDrawableResolver.getDrawableId(
                        packRes,
                        packPackage,
                        drawableName + "_foreground");
                if (iconId == 0) {
                    iconId = IconPackDrawableResolver.getDrawableId(
                            packRes,
                            packPackage,
                            drawableName);
                }
                if (iconId != 0) {
                    map.put(pkg, new ThemeData(packRes, iconId));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse themed appfilter", e);
        }
    }

    private static String extractPackageFromComponent(String component) {
        if (component.startsWith("ComponentInfo{") && component.endsWith("}")) {
            String inner = component.substring(14, component.length() - 1);
            int slash = inner.indexOf('/');
            return slash > 0 ? inner.substring(0, slash) : null;
        }
        return null;
    }
}
