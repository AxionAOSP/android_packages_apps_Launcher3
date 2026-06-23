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

import com.android.launcher3.model.data.AppInfo;

import java.util.List;
import java.util.Objects;

public final class AxSmartDrawerCategory {

    public static final int TYPE_CATEGORY = 0;
    public static final int TYPE_PINNED = 1;
    public static final int TYPE_PREDICTIONS = 2;
    public static final int TYPE_CUSTOM_FOLDER = 3;

    private final String mId;
    private final CharSequence mTitle;
    private final List<AppInfo> mApps;
    private final int mType;

    AxSmartDrawerCategory(String id, CharSequence title, List<AppInfo> apps, int type) {
        mId = id;
        mTitle = title;
        mApps = List.copyOf(apps);
        mType = type;
    }

    public String getId() {
        return mId;
    }

    public CharSequence getTitle() {
        return mTitle;
    }

    public List<AppInfo> getApps() {
        return mApps;
    }

    public boolean isRow() {
        return mType == TYPE_PINNED || mType == TYPE_PREDICTIONS;
    }

    public boolean isExpandable() {
        return !isRow();
    }

    public int getType() {
        return mType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AxSmartDrawerCategory other)) {
            return false;
        }
        return mType == other.mType
                && Objects.equals(mId, other.mId)
                && Objects.equals(mTitle, other.mTitle)
                && Objects.equals(mApps, other.mApps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle, mApps, mType);
    }
}
