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
