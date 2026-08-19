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

package com.android.launcher3.model.data;

import android.content.res.Resources;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.model.ModelWriter;

public final class FolderPreviewSettings {
    private FolderPreviewSettings() {}

    public static final int MIN_ITEM_SCALE_PERCENT = 50;
    public static final int MAX_ITEM_SCALE_PERCENT = 120;
    public static final int MIN_PADDING_DP = 0;
    public static final int MAX_PADDING_DP = 16;
    public static final int MIN_GAP_DP = 0;
    public static final int MAX_GAP_DP = 16;

    private static final int ITEM_SCALE_SHIFT = 8;
    private static final int ITEM_SCALE_MASK = 0x7F << ITEM_SCALE_SHIFT;
    private static final int PADDING_SHIFT = 15;
    private static final int PADDING_MASK = 0x1F << PADDING_SHIFT;
    private static final int MIN_GAP_SHIFT = 20;
    private static final int MIN_GAP_MASK = 0x1F << MIN_GAP_SHIFT;

    public static float getItemScale(Resources resources, FolderInfo folderInfo) {
        int encoded =
                (folderInfo.options & ITEM_SCALE_MASK) >>> ITEM_SCALE_SHIFT;

        if (encoded == 0) {
            return resources.getFloat(
                    R.dimen.folder_workspace_preview_item_scale);
        }

        int percent = MIN_ITEM_SCALE_PERCENT + encoded - 1;
        if (percent > MAX_ITEM_SCALE_PERCENT) {
            return resources.getFloat(
                    R.dimen.folder_workspace_preview_item_scale);
        }

        return percent / 100f;
    }

    public static int getItemScalePercent(
            Resources resources, FolderInfo folderInfo) {
        return Math.round(getItemScale(resources, folderInfo) * 100f);
    }

    public static void setItemScalePercent(
            FolderInfo folderInfo,
            int percent,
            @Nullable ModelWriter writer) {
        if (percent < MIN_ITEM_SCALE_PERCENT
                || percent > MAX_ITEM_SCALE_PERCENT) {
            throw new IllegalArgumentException(
                    "Item scale must be between "
                            + MIN_ITEM_SCALE_PERCENT
                            + " and "
                            + MAX_ITEM_SCALE_PERCENT);
        }

        int encoded = percent - MIN_ITEM_SCALE_PERCENT + 1;
        int newOptions =
                (folderInfo.options & ~ITEM_SCALE_MASK)
                        | (encoded << ITEM_SCALE_SHIFT);

        updateOptions(folderInfo, newOptions, writer);
    }

    public static float getPadding(
            Resources resources, FolderInfo folderInfo) {
        return getDimensionDp(
                resources,
                folderInfo,
                PADDING_MASK,
                PADDING_SHIFT,
                R.dimen.folder_workspace_preview_padding,
                MAX_PADDING_DP)
                * resources.getDisplayMetrics().density;
    }

    public static int getPaddingDp(
            Resources resources, FolderInfo folderInfo) {
        return getDimensionDp(
                resources,
                folderInfo,
                PADDING_MASK,
                PADDING_SHIFT,
                R.dimen.folder_workspace_preview_padding,
                MAX_PADDING_DP);
    }

    public static void setPaddingDp(
            FolderInfo folderInfo,
            int paddingDp,
            @Nullable ModelWriter writer) {
        setDimensionDp(
                folderInfo,
                paddingDp,
                MIN_PADDING_DP,
                MAX_PADDING_DP,
                PADDING_MASK,
                PADDING_SHIFT,
                "Padding",
                writer);
    }

    public static float getMinimumGap(
            Resources resources, FolderInfo folderInfo) {
        return getDimensionDp(
                resources,
                folderInfo,
                MIN_GAP_MASK,
                MIN_GAP_SHIFT,
                R.dimen.folder_workspace_preview_min_gap,
                MAX_GAP_DP)
                * resources.getDisplayMetrics().density;
    }

    public static int getMinimumGapDp(
            Resources resources, FolderInfo folderInfo) {
        return getDimensionDp(
                resources,
                folderInfo,
                MIN_GAP_MASK,
                MIN_GAP_SHIFT,
                R.dimen.folder_workspace_preview_min_gap,
                MAX_GAP_DP);
    }

    public static void setMinimumGapDp(
            FolderInfo folderInfo,
            int gapDp,
            @Nullable ModelWriter writer) {
        setDimensionDp(
                folderInfo,
                gapDp,
                MIN_GAP_DP,
                MAX_GAP_DP,
                MIN_GAP_MASK,
                MIN_GAP_SHIFT,
                "Minimum gap",
                writer);
    }

    public static void resetAll(
            FolderInfo folderInfo, @Nullable ModelWriter writer) {
        updateOptions(
                folderInfo,
                folderInfo.options
                        & ~(ITEM_SCALE_MASK | PADDING_MASK | MIN_GAP_MASK),
                writer);
    }

    private static int getDimensionDp(
            Resources resources,
            FolderInfo folderInfo,
            int mask,
            int shift,
            int defaultResource,
            int maximum) {
        int encoded = (folderInfo.options & mask) >>> shift;
        if (encoded != 0) {
            int value = encoded - 1;
            if (value <= maximum) return value;
        }

        return Math.round(
                resources.getDimension(defaultResource)
                        / resources.getDisplayMetrics().density);
    }

    private static void setDimensionDp(
            FolderInfo folderInfo,
            int value,
            int minimum,
            int maximum,
            int mask,
            int shift,
            String name,
            @Nullable ModelWriter writer) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum);
        }

        int encoded = value + 1;
        int newOptions =
                (folderInfo.options & ~mask) | (encoded << shift);
        updateOptions(folderInfo, newOptions, writer);
    }

    private static void updateOptions(
            FolderInfo folderInfo,
            int newOptions,
            @Nullable ModelWriter writer) {
        if (folderInfo.options == newOptions) return;

        folderInfo.options = newOptions;
        if (writer != null) {
            writer.updateItemInDatabase(folderInfo);
        }
    }
}