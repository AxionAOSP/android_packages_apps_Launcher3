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

package com.android.launcher3.folder;

public class LargeFolderLayoutRule extends ClippedFolderIconLayoutRule {

    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 7;
    private static final int PRIMARY_ITEMS = 3;
    private static final int GRID_SIZE = 2;
    private static final float PRIMARY_SIZE_FACTOR = 0.36f;
    private static final float SECONDARY_SIZE_FACTOR = 0.41f;
    private static final float PRIMARY_GAP_FACTOR = 0.08f;
    private static final float SECONDARY_GAP_FACTOR = 0.08f;

    @Override
    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float primarySize = mAvailableSpace * PRIMARY_SIZE_FACTOR;
        float primaryScale = primarySize / mIconSize;
        float primaryGap = mAvailableSpace * PRIMARY_GAP_FACTOR;
        float primaryStart = (mAvailableSpace - (GRID_SIZE * primarySize) - primaryGap) / 2f;
        float transX;
        float transY;
        float scale;

        if (index < PRIMARY_ITEMS) {
            int col = index % GRID_SIZE;
            int row = index / GRID_SIZE;
            if (mIsRtl) {
                col = GRID_SIZE - 1 - col;
            }
            transX = primaryStart + col * (primarySize + primaryGap);
            transY = primaryStart + row * (primarySize + primaryGap);
            scale = primaryScale;
        } else {
            int secondaryIndex = index - PRIMARY_ITEMS;
            int col = secondaryIndex % GRID_SIZE;
            int row = secondaryIndex / GRID_SIZE;
            if (mIsRtl) {
                col = GRID_SIZE - 1 - col;
            }
            float secondarySize = primarySize * SECONDARY_SIZE_FACTOR;
            float secondaryGap = primarySize * SECONDARY_GAP_FACTOR;
            float secondaryInset =
                    (primarySize - (GRID_SIZE * secondarySize) - secondaryGap) / 2f;
            float secondaryStartX =
                    mIsRtl ? primaryStart : primaryStart + primarySize + primaryGap;
            float secondaryStartY = primaryStart + primarySize + primaryGap;
            transX = secondaryStartX + secondaryInset + col * (secondarySize + secondaryGap);
            transY = secondaryStartY + secondaryInset + row * (secondarySize + secondaryGap);
            scale = secondarySize / mIconSize;
        }

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, scale);
        } else {
            params.update(transX, transY, scale);
        }
        return params;
    }

    @Override
    public float scaleForItem(int numItems, int page) {
        return (mAvailableSpace * PRIMARY_SIZE_FACTOR) / mIconSize;
    }
}
