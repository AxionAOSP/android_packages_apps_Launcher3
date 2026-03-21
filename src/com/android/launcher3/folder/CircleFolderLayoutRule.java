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

public class CircleFolderLayoutRule extends ClippedFolderIconLayoutRule {

    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 7;
    private static final int PERIMETER_ICONS = 6;

    @Override
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl, int numFolderColumns) {
        super.init(availableSpace, intrinsicIconSize, rtl, numFolderColumns);
        mRadius = availableSpace * 0.32f;
    }

    public void init(int availableSpace, float intrinsicIconSize) {
        init(availableSpace, intrinsicIconSize, false, 0);
    }

    @Override
    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float totalScale = scaleForItem(curNumItems, 0);
        float iconSizeScaled = mIconSize * totalScale;
        float centerX = mAvailableSpace / 2f;
        float centerY = mAvailableSpace / 2f;
        
        float transX;
        float transY;
        
        if (index == PERIMETER_ICONS) {
            transX = centerX - iconSizeScaled / 2f;
            transY = centerY - iconSizeScaled / 2f;
        } else {
            double angle = (2 * Math.PI * index / PERIMETER_ICONS) - Math.PI / 2;
             if (mIsRtl) {
                 int rtlIndex = (PERIMETER_ICONS - 1) - index;
                 if (index < PERIMETER_ICONS) {
                     angle = (2 * Math.PI * rtlIndex / PERIMETER_ICONS) - Math.PI / 2;
                 }
            }

            transX = (float) (centerX + mRadius * Math.cos(angle) - iconSizeScaled / 2f);
            transY = (float) (centerY + mRadius * Math.sin(angle) - iconSizeScaled / 2f);
        }

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale);
        } else {
            params.update(transX, transY, totalScale);
        }
        return params;
    }

    @Override
    public float scaleForItem(int numItems, int page) {
        return 0.30f * mBaselineIconScale;
    }

    @Override
    public float getIconSize() {
        return mIconSize;
    }
}
