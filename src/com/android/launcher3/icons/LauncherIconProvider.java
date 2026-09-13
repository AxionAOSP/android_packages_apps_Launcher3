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
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.axion.iconprovider.AxIconEngine;
import com.android.axion.iconloader.ThemedIconItem;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.IconProvider.ThemeData;

import org.xmlpull.v1.XmlPullParser;

import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;

/**
 * Extension of {@link IconProvider} with support for overriding theme icons
 */
@LauncherAppSingleton
public class LauncherIconProvider extends IconProvider {

    private static final String TAG = "LIconProvider";
    private static final Map<String, ThemeData> DISABLED_MAP = Collections.emptyMap();

    private Map<String, ThemeData> mThemedIconMap;

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
        ThemedIconItem item = AxIconEngine.getThemedIconItem(mContext, packageName);
        if (item != null) {
            return new ThemeData(item.getResources(), item.getResId());
        }
        return getThemedIconMap().get(packageName);
    }

    @Override
    public void updateSystemState() {
        super.updateSystemState();
        mSystemState += AxIconEngine.getSystemState(mContext, mThemeManager.getIconState().toUniqueId());
    }

    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        if (info instanceof ActivityInfo activityInfo) {
            ComponentName componentName =
                    new ComponentName(activityInfo.packageName, activityInfo.name);
            Drawable customIcon = AxIconEngine.resolveCustomOrPackIcon(mContext, componentName, iconDpi);
            if (customIcon != null) {
                return customIcon;
            }
            Drawable icon = super.getIcon(info, iconDpi);
            icon = AxIconEngine.processIcon(mContext, icon);
            ThemeData themeData = getThemeDataForPackage(activityInfo.packageName);
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

    private Map<String, ThemeData> getThemedIconMap() {
        if (mThemedIconMap != null) {
            return mThemedIconMap;
        }
        ArrayMap<String, ThemeData> map = new ArrayMap<>();
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
                if ("icon".equals(parser.getName())) {
                    String pkg = parser.getAttributeValue(null, "package");
                    int iconId = parser.getAttributeResourceValue(null, "drawable", 0);
                    if (iconId != 0 && !TextUtils.isEmpty(pkg) && !map.containsKey(pkg)) {
                        map.put(pkg, new ThemeData(res, iconId));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to parse icon map", e);
        }
        mThemedIconMap = map;
        return mThemedIconMap;
    }
}
