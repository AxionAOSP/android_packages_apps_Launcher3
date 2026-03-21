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

public class GridFolderLayoutRule extends ClippedFolderIconLayoutRule {

    public static final int MAX_NUM_ITEMS_IN_PREVIEW = 9;
    private static final int GRID_SIZE = 3;

    @Override
    public void init(int availableSpace, float intrinsicIconSize, boolean rtl, int numFolderColumns) {
        super.init(availableSpace, intrinsicIconSize, rtl, numFolderColumns);
    }

    public void init(int availableSpace, float intrinsicIconSize) {
        init(availableSpace, intrinsicIconSize, false, 0);
    }

    @Override
    public PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        float totalScale = scaleForItem(curNumItems, 0);
        float iconSizeScaled = mIconSize * totalScale;
        float spacing = (mAvailableSpace - (GRID_SIZE * iconSizeScaled)) / (GRID_SIZE + 1);
        
        int row = index / GRID_SIZE;
        int col = index % GRID_SIZE;

        if (mIsRtl) {
            col = GRID_SIZE - 1 - col;
        }

        float transX = spacing + col * (iconSizeScaled + spacing);
        float transY = spacing + row * (iconSizeScaled + spacing);

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale);
        } else {
            params.update(transX, transY, totalScale);
        }
        return params;
    }

    @Override
    public float scaleForItem(int numItems, int page) {
        return 0.28f * mBaselineIconScale;
    }

    @Override
    public float getIconSize() {
        return mIconSize;
    }
}
