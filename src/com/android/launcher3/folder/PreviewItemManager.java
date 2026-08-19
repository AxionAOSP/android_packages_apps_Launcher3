/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.launcher3.BubbleTextView.DISPLAY_FOLDER;
import static com.android.launcher3.LauncherPrefsExt.ALLAPPS_THEMED_ICONS;
import static com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG;
import static com.android.launcher3.Utilities.dpToPx;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ENTER_INDEX;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.EXIT_INDEX;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderIcon.DROP_IN_ANIMATION_DURATION;
import static com.android.launcher3.graphics.PreloadIconDelegate.newPendingIcon;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.apppairs.AppPairIconDrawingParams;
import com.android.launcher3.apppairs.AppPairIconGraphic;
import com.android.launcher3.folder.AxFolderExt;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderPreviewSettings;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Manages the drawing and animations of {@link PreviewItemDrawingParams} for a {@link FolderIcon}.
 */
public class PreviewItemManager {

    private static final String TAG = "PreviewItemManager";

    private static final FloatProperty<PreviewItemManager> CURRENT_PAGE_ITEMS_TRANS_X =
            new FloatProperty<PreviewItemManager>("currentPageItemsTransX") {
                @Override
                public void setValue(PreviewItemManager manager, float v) {
                    manager.mCurrentPageItemsTransX = v;
                    manager.onParamsChanged();
                }

                @Override
                public Float get(PreviewItemManager manager) {
                    return manager.mCurrentPageItemsTransX;
                }
            };

    private final Context mContext;
    private final FolderIcon mIcon;
    @VisibleForTesting
    public final int mIconSize;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private float mIntrinsicIconSize = -1;
    private int mTotalWidth = -1;
    private int mTotalHeight = -1;
    private int mPrevSpanX = -1;
    private int mPrevSpanY = -1;
    private int mPrevTopPadding = -1;
    private Drawable mReferenceDrawable = null;

    private int mNumOfPrevItems = 0;

    // These hold the first page preview items
    private ArrayList<PreviewItemDrawingParams> mFirstPageParams = new ArrayList<>();
    // These hold the current page preview items. It is empty if the current page is the first page.
    private ArrayList<PreviewItemDrawingParams> mCurrentPageParams = new ArrayList<>();

    // We clip the preview items during the middle of the animation, so that it does not go outside
    // of the visual shape. We stop clipping at this threshold, since the preview items ultimately
    // do not get cropped in their resting state.
    private final float mClipThreshold;
    private float mCurrentPageItemsTransX = 0;
    private float mPageSlideDistance;
    private boolean mShouldSlideInFirstPage;

    static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    private static final int SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY = 100;
    private static final int SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION = 300;

    public PreviewItemManager(FolderIcon icon) {
        mContext = icon.getContext();
        mIcon = icon;
        mIconSize = ActivityContext.lookupContext(
                mContext).getDeviceProfile().getFolderProfile().getChildIconSizePx();
        mClipThreshold = dpToPx(1f);
    }

    /**
     * @param reverse If true, animates the final item in the preview to be full size. If false,
     *                animates the first item to its position in the preview.
     */
    public FolderPreviewItemAnim createFirstItemAnimation(final boolean reverse,
            final Runnable onCompleteRunnable) {
        return reverse
                ? new FolderPreviewItemAnim(this, mFirstPageParams.get(0), 0, 2, -1, -1,
                FINAL_ITEM_ANIMATION_DURATION, onCompleteRunnable)
                : new FolderPreviewItemAnim(this, mFirstPageParams.get(0), -1, -1, 0, 2,
                        INITIAL_ITEM_ANIMATION_DURATION, onCompleteRunnable);
    }

    Drawable prepareCreateAnimation(final View destView) {
        Drawable animateDrawable = destView instanceof AppPairIcon
                ? ((AppPairIcon) destView).getIconDrawableArea().getDrawable()
                : ((BubbleTextView) destView).getIcon();
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth(),
                destView.getMeasuredHeight());
        mReferenceDrawable = animateDrawable;
        return animateDrawable;
    }

    public void recomputePreviewDrawingParams() {
        if (mReferenceDrawable != null) {
            computePreviewDrawingParams(mReferenceDrawable.getIntrinsicWidth(),
                    mIcon.getMeasuredWidth(),
                    mIcon.getMeasuredHeight());
        }
    }

    private void computePreviewDrawingParams(
            int drawableSize, int totalWidth, int totalHeight) {
        int spanX = mIcon.getCurrentSpanX();
        int spanY = mIcon.getCurrentSpanY();

        boolean geometryChanged =
                mTotalWidth != totalWidth
                        || mTotalHeight != totalHeight
                        || mPrevSpanX != spanX
                        || mPrevSpanY != spanY
                        || mPrevTopPadding != mIcon.getPaddingTop();

        if (mIntrinsicIconSize != drawableSize || geometryChanged) {
            boolean animateResize =
                    geometryChanged
                            && mIntrinsicIconSize > 0
                            && !mFirstPageParams.isEmpty()
                            && mIcon.usesWorkspacePreviewLayout();

            Rect previousBackgroundBounds = null;
            FolderPreviewLayout.Snapshot previousSnapshot = null;

            if (animateResize) {
                previousBackgroundBounds = new Rect();
                mIcon.mBackground.getBounds(previousBackgroundBounds);
                previousSnapshot = calculateWorkspacePreviewSnapshot();
            }

            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalWidth;
            mTotalHeight = totalHeight;
            mPrevSpanX = spanX;
            mPrevSpanY = spanY;
            mPrevTopPadding = mIcon.getPaddingTop();

            mIcon.mBackground.setup(
                    mIcon.getContext(),
                    mIcon.mActivity,
                    mIcon,
                    mTotalWidth,
                    mTotalHeight,
                    mIcon.getPaddingTop(),
                    spanX,
                    spanY);

            mIcon.mPreviewLayoutRule.init(
                    mIcon.mBackground.previewSize,
                    mIntrinsicIconSize,
                    Utilities.isRtl(mIcon.getResources()),
                    mIcon.mActivity.getDeviceProfile()
                            .getFolderProfile()
                            .getNumColumns());

            if (animateResize) {
                FolderPreviewLayout.Snapshot newSnapshot =
                        calculateWorkspacePreviewSnapshot();
                animateWorkspacePreviewResize(previousSnapshot, newSnapshot);
                mIcon.mBackground.animateBoundsFrom(
                        previousBackgroundBounds,
                        DROP_IN_ANIMATION_DURATION);
            } else {
                updatePreviewItems(false);
            }
        }
    }

    private FolderPreviewLayout.Grid calculateWorkspacePreviewGrid(
            RectF backgroundBounds) {
        Resources resources = mContext.getResources();

        float itemScale =
                FolderPreviewSettings.getItemScale(resources, mIcon.mInfo);
        float padding =
                FolderPreviewSettings.getPadding(resources, mIcon.mInfo);
        float minGap =
                FolderPreviewSettings.getMinimumGap(resources, mIcon.mInfo);

        RectF availableBounds = new RectF(backgroundBounds);
        availableBounds.inset(padding, padding);

        float defaultPadding =
                resources.getDimension(R.dimen.folder_workspace_preview_padding);
        float baseContentSize =
                mIcon.mActivity.getDeviceProfile().folderIconSizePx - 2 * defaultPadding;

        float maxItemSize =
                Math.min(availableBounds.width(), availableBounds.height());
        float itemSize = Math.min(baseContentSize * itemScale, maxItemSize);

        return FolderPreviewLayout.calculateGrid(availableBounds, itemSize, minGap);
    }

    private FolderPreviewLayout.Grid calculateWorkspacePreviewGrid(
            int availableSpaceX,
            int availableSpaceY,
            int spanX,
            int spanY) {
        Rect backgroundBounds = new Rect();
        PreviewBackground.calculateBackgroundBounds(
                mIcon.mActivity.getDeviceProfile(),
                availableSpaceX,
                availableSpaceY,
                mIcon.getPaddingTop(),
                spanX,
                spanY,
                backgroundBounds);
        return calculateWorkspacePreviewGrid(new RectF(backgroundBounds));
    }

    FolderPreviewLayout.GridUsage calculateWorkspacePreviewGridUsage(
            int availableSpaceX,
            int availableSpaceY,
            int spanX,
            int spanY) {
        FolderPreviewLayout.Grid grid =
                calculateWorkspacePreviewGrid(availableSpaceX, availableSpaceY, spanX, spanY);

        return FolderPreviewLayout.calculateGridUsage(
            mIcon.mInfo.getContents().size(),
            grid);
    }

    boolean isPreviewTightlyWrapped(
            int availableSpaceX,
            int availableSpaceY,
            int spanX,
            int spanY) {
        if (availableSpaceX <= 0
            || availableSpaceY <= 0
            || spanX <= 0
            || spanY <= 0) return false;

        FolderPreviewLayout.Grid grid =
                calculateWorkspacePreviewGrid(availableSpaceX, availableSpaceY, spanX, spanY);

        return FolderPreviewLayout.isTightlyWrapped(
            mIcon.mInfo.getContents().size(),
            grid);
    }

    FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshot() {
        return calculateWorkspacePreviewSnapshot(mIcon.mInfo.getContents());
    }

    FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshotForPage(int page) {
        List<View> pageViews = mIcon.getFolder().getItemsOnPage(page);
        List<ItemInfo> pageItems = new ArrayList<>(pageViews.size());

        for (View view : pageViews) {
            pageItems.add((ItemInfo) view.getTag());
        }

        return calculateWorkspacePreviewSnapshot(pageItems);
    }

    private FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshot(
            List<ItemInfo> items) {
        Rect backgroundBounds = new Rect();
        mIcon.mBackground.getTargetBounds(backgroundBounds);

        RectF snapshotBounds = new RectF(backgroundBounds);
        FolderPreviewLayout.Grid grid =
                calculateWorkspacePreviewGrid(snapshotBounds);

        int folderColumnCount = mIcon.mActivity.getDeviceProfile()
                .getFolderProfile().getNumColumns();
        boolean isRtl = Utilities.isRtl(mIcon.getResources());

        return FolderPreviewLayout.calculateSnapshot(
                items,
                snapshotBounds,
                grid,
                mIntrinsicIconSize,
                isRtl,
                folderColumnCount);
    }

    @Nullable
    FolderPreviewLayout.ItemPlacement findDirectItemAt(float x, float y) {
        if (!mIcon.usesWorkspacePreviewLayout() || mIntrinsicIconSize <= 0) {
            return null;
        }

        for (FolderPreviewLayout.ItemPlacement placement
                : calculateWorkspacePreviewSnapshot().getItems()) {
            if (placement.getRole() == FolderPreviewLayout.ItemRole.DIRECT
                    && placement.getBounds().contains(x, y)) {
                return placement;
            }
        }

        return null;
    }

    @Nullable
    FolderPreviewLayout.ItemPlacement findWorkspacePreviewPlacement(
            FolderPreviewLayout.Snapshot snapshot, ItemInfo item) {
        for (FolderPreviewLayout.ItemPlacement placement : snapshot.getItems()) {
            if (placement.getItem() == item) {
                return placement;
            }
        }
        return null;
    }

    void setWorkspacePreviewItemHidden(ItemInfo item, boolean hidden) {
        for (PreviewItemDrawingParams params : mFirstPageParams) {
            if (params.item == item) {
                params.hidden = hidden;
                onParamsChanged();
                return;
            }
        }
    }

    PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        // We use an index of -1 to represent an icon on the workspace for the destroy and
        // create animations
        if (index == -1) {
            return getFinalIconParams(params);
        }
        return mIcon.mPreviewLayoutRule.computePreviewItemDrawingParams(index, curNumItems, params);
    }

    private PreviewItemDrawingParams getFinalIconParams(PreviewItemDrawingParams params) {
        float iconSize = mIcon.mActivity.getDeviceProfile().getWorkspaceIconProfile().getIconSizePx();

        final float scale = iconSize / mReferenceDrawable.getIntrinsicWidth();
        final float trans = (mIcon.mBackground.previewSize - iconSize) / 2;

        if (params == null) {
            params = new PreviewItemDrawingParams(0, 0, 0);
        }
        params.update(trans, trans, scale);
        return params;
    }

    public void drawParams(Canvas canvas, ArrayList<PreviewItemDrawingParams> params,
            PointF offset, boolean shouldClipPath, Path clipPath) {
        // The first item should be drawn last (ie. on top of later items)
        for (int i = params.size() - 1; i >= 0; i--) {
            PreviewItemDrawingParams p = params.get(i);
            if (!p.hidden) {
                // Exiting param should always be clipped.
                boolean isExiting = p.index == EXIT_INDEX;
                drawPreviewItem(canvas, p, offset, isExiting | shouldClipPath, clipPath);
            }
        }
    }

    /**
     * Draws the preview items on {@param canvas}.
     */
    public void draw(Canvas canvas) {
        int saveCount = canvas.getSaveCount();
        // The items are drawn in coordinates relative to the preview offset
        PreviewBackground bg = mIcon.getFolderBackground();
        Path clipPath = bg.getClipPath();
        boolean shouldClipResize = bg.isBoundsAnimating();
        float firstPageItemsTransX = 0;
        if (mShouldSlideInFirstPage) {
            PointF firstPageOffset = new PointF(bg.getPreviewLeft() + mCurrentPageItemsTransX,
                    bg.getPreviewTop());
            boolean shouldClip =
                    Math.abs(mCurrentPageItemsTransX) > mClipThreshold;

            drawParams(
                    canvas,
                    mCurrentPageParams,
                    firstPageOffset,
                    shouldClip || shouldClipResize,
                    clipPath);

            firstPageItemsTransX = -mPageSlideDistance + mCurrentPageItemsTransX;
        }

        PointF firstPageOffset = new PointF(bg.getPreviewLeft() + firstPageItemsTransX,
                bg.getPreviewTop());
        boolean shouldClipFirstPage =
                shouldClipResize
                        || Math.abs(firstPageItemsTransX) > mClipThreshold;
        drawParams(
                canvas,
                mFirstPageParams,
                firstPageOffset,
                shouldClipFirstPage,
                clipPath);
        canvas.restoreToCount(saveCount);
    }

    public void onParamsChanged() {
        mIcon.invalidate();
    }

    /**
     * Draws each preview item.
     *
     * @param offset         The offset needed to draw the preview items.
     * @param shouldClipPath Iff true, clip path using {@param clipPath}.
     * @param clipPath       The clip path of the folder icon.
     */
    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params, PointF offset,
            boolean shouldClipPath, Path clipPath) {
        canvas.save();
        if (shouldClipPath) {
            canvas.clipPath(clipPath);
        }
        canvas.translate(offset.x + params.transX, offset.y + params.transY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            Rect bounds = d.getBounds();
            canvas.save();
            canvas.translate(-bounds.left, -bounds.top);
            canvas.scale(mIntrinsicIconSize / bounds.width(), mIntrinsicIconSize / bounds.height());
            d.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    public void hidePreviewItem(int index, boolean hidden) {
        int paramIndex = index;

        // If there are more params than visible in the preview, they are used for enter/exit
        // animation purposes and they were added to the front of the list.
        // To index the params properly, we need to skip these params.
        if (!mIcon.usesWorkspacePreviewLayout()) {
            paramIndex += Math.max(
                mFirstPageParams.size() - MAX_NUM_ITEMS_IN_PREVIEW,
                0);
        }

        if (paramIndex < 0 || paramIndex >= mFirstPageParams.size()) {
            return;
        }

        mFirstPageParams.get(paramIndex).hidden = hidden;
    }

    private static void matchParamCount(
            ArrayList<PreviewItemDrawingParams> params, int itemCount) {
        while (itemCount < params.size()) {
            params.remove(params.size() - 1);
        }

        while (itemCount > params.size()) {
            params.add(new PreviewItemDrawingParams(0, 0, 0));
        }
    }

    private void applyPlacement(
            FolderPreviewLayout.ItemPlacement placement,
            PreviewItemDrawingParams params) {
        RectF bounds = placement.getBounds();

        float scale = bounds.width() / mIntrinsicIconSize;
        float transX = bounds.left - mIcon.mBackground.getTargetPreviewLeft();
        float transY = bounds.top - mIcon.mBackground.getTargetPreviewTop();

        params.update(transX, transY, scale);
    }

    private PreviewItemDrawingParams createPlacementParams(
            FolderPreviewLayout.ItemPlacement placement) {
        PreviewItemDrawingParams params = new PreviewItemDrawingParams(0, 0, 0);
        applyPlacement(placement, params);
        return params;
    }

    private PreviewItemDrawingParams createCollapsedParams(
            FolderPreviewLayout.Snapshot snapshot) {
        RectF collapseBounds = snapshot.getOverviewBounds();
        if (collapseBounds == null) {
            collapseBounds = snapshot.getBackgroundBounds();
        }

        RectF backgroundBounds = snapshot.getBackgroundBounds();
        float previewLeft =
                backgroundBounds.centerX()
                        - mIcon.mBackground.previewSize / 2f;
        float previewTop =
                backgroundBounds.centerY()
                        - mIcon.mBackground.previewSize / 2f;

        return new PreviewItemDrawingParams(
                collapseBounds.centerX() - previewLeft,
                collapseBounds.centerY() - previewTop,
                0f);
    }

    private void animateToPlacement(
            PreviewItemDrawingParams params,
            FolderPreviewLayout.ItemPlacement placement,
            Runnable onComplete) {
        PreviewItemDrawingParams target = createPlacementParams(placement);
        FolderPreviewItemAnim anim = new FolderPreviewItemAnim(
                this,
                params,
                target.scale,
                target.transX,
                target.transY,
                DROP_IN_ANIMATION_DURATION,
                onComplete);

        if (params.anim != null) {
            if (params.anim.hasEqualFinalState(anim)) return;
            params.anim.cancel();
        }
        params.anim = anim;
        anim.start();
    }

    @Nullable
    private static PreviewItemDrawingParams removeParamForItem(
            List<PreviewItemDrawingParams> params, ItemInfo item) {
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).item == item) {
                return params.remove(i);
            }
        }
        return null;
    }

    void animateWorkspacePreviewSnapshot(
            FolderPreviewLayout.Snapshot oldSnapshot,
            FolderPreviewLayout.Snapshot newSnapshot,
            @Nullable ItemInfo droppedItem) {
        ArrayList<PreviewItemDrawingParams> unmatchedParams = new ArrayList<>();

        for (FolderPreviewLayout.ItemPlacement placement : oldSnapshot.getItems()) {
            PreviewItemDrawingParams params = createPlacementParams(placement);
            setDrawable(params, placement.getItem());
            unmatchedParams.add(params);
        }

        animateWorkspacePreviewParams(
                unmatchedParams,
                newSnapshot,
                droppedItem,
                true,
                null);
    }

    private void animateWorkspacePreviewParams(
            ArrayList<PreviewItemDrawingParams> unmatchedParams,
            FolderPreviewLayout.Snapshot newSnapshot,
            @Nullable ItemInfo hiddenItem,
            boolean updateHiddenState,
            @Nullable FolderPreviewLayout.Snapshot resizeStartSnapshot) {
        ArrayList<PreviewItemDrawingParams> nextParams =
                new ArrayList<>();

        for (FolderPreviewLayout.ItemPlacement placement
                : newSnapshot.getItems()) {
            PreviewItemDrawingParams params =
                    removeParamForItem(
                            unmatchedParams, placement.getItem());

            if (params == null) {
                params = resizeStartSnapshot == null
                        ? createPlacementParams(placement)
                        : createCollapsedParams(resizeStartSnapshot);
                params.scale = 0f;
                setDrawable(params, placement.getItem());
            }

            if (updateHiddenState) {
                params.hidden = placement.getItem() == hiddenItem;
            }
            nextParams.add(params);
            animateToPlacement(params, placement, null);
        }

        for (PreviewItemDrawingParams params : unmatchedParams) {
            float exitTransX = params.transX;
            float exitTransY = params.transY;

            if (resizeStartSnapshot != null) {
                PreviewItemDrawingParams collapsed =
                        createCollapsedParams(newSnapshot);
                exitTransX = collapsed.transX;
                exitTransY = collapsed.transY;
            }

            FolderPreviewItemAnim anim = new FolderPreviewItemAnim(
                    this,
                    params,
                    0f,
                    exitTransX,
                    exitTransY,
                    DROP_IN_ANIMATION_DURATION,
                    () -> {
                        mFirstPageParams.remove(params);
                        onParamsChanged();
                    });

            params.anim = anim;
            nextParams.add(0, params);
            anim.start();
        }

        mFirstPageParams.clear();
        mFirstPageParams.addAll(nextParams);
        onParamsChanged();
    }

    private void animateWorkspacePreviewResize(
            FolderPreviewLayout.Snapshot oldSnapshot,
            FolderPreviewLayout.Snapshot newSnapshot) {
        animateWorkspacePreviewParams(
                new ArrayList<>(mFirstPageParams),
                newSnapshot,
                null,
                false,
                oldSnapshot);
    }

    private void applySnapshot(
            FolderPreviewLayout.Snapshot snapshot,
            ArrayList<PreviewItemDrawingParams> params) {
        List<FolderPreviewLayout.ItemPlacement> placements = snapshot.getItems();

        for (PreviewItemDrawingParams drawingParams : new ArrayList<>(params)) {
            if (drawingParams.anim != null) {
                drawingParams.anim.cancel();
            }
        }

        matchParamCount(params, placements.size());

        for (int i = 0; i < placements.size(); i++) {
            FolderPreviewLayout.ItemPlacement placement = placements.get(i);
            PreviewItemDrawingParams drawingParams = params.get(i);

            setDrawable(drawingParams, placement.getItem());
            applyPlacement(placement, drawingParams);
        }
    }

    void buildParamsForPage(int page, ArrayList<PreviewItemDrawingParams> params, boolean animate) {
        if (mIcon.usesWorkspacePreviewLayout() && mIntrinsicIconSize > 0) {
            FolderPreviewLayout.Snapshot snapshot = page == 0
                    ? calculateWorkspacePreviewSnapshot()
                    : calculateWorkspacePreviewSnapshotForPage(page);
            applySnapshot(snapshot, params);
            return;
        }

        List<ItemInfo> items = mIcon.getPreviewItemsOnPage(page);

        matchParamCount(params, items.size());

        int numItemsInFirstPagePreview = page == 0 ? items.size() : MAX_NUM_ITEMS_IN_PREVIEW;
        for (int i = 0; i < params.size(); i++) {
            PreviewItemDrawingParams p = params.get(i);
            setDrawable(p, items.get(i));

            if (!animate) {
                if (p.anim != null) {
                    p.anim.cancel();
                }
                computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, p);
                if (mReferenceDrawable == null) {
                    mReferenceDrawable = p.drawable;
                }
            } else {
                FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, i,
                        mNumOfPrevItems, i, numItemsInFirstPagePreview, DROP_IN_ANIMATION_DURATION,
                        null);

                if (p.anim != null) {
                    if (p.anim.hasEqualFinalState(anim)) {
                        // do nothing, let the current animation finish
                        continue;
                    }
                    p.anim.cancel();
                }
                p.anim = anim;
                p.anim.start();
            }
        }
    }

    void onFolderClose(int currentPage) {
        // If we are not closing on the first page, we animate the current page preview items
        // out, and animate the first page preview items in.
        mShouldSlideInFirstPage = currentPage != 0;
        if (mShouldSlideInFirstPage) {
            Rect backgroundBounds = new Rect();
            mIcon.mBackground.getBounds(backgroundBounds);

            float slideDirection =
                    Utilities.isRtl(mIcon.getResources()) ? -1f : 1f;
            mPageSlideDistance = backgroundBounds.width() * slideDirection;

            mCurrentPageItemsTransX = 0;
            buildParamsForPage(currentPage, mCurrentPageParams, false);
            onParamsChanged();

            ValueAnimator slideAnimator = ObjectAnimator
                    .ofFloat(this, CURRENT_PAGE_ITEMS_TRANS_X, 0f, mPageSlideDistance);
            slideAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCurrentPageParams.clear();
                }
            });
            slideAnimator.setStartDelay(SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY);
            slideAnimator.setDuration(SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION);
            slideAnimator.start();
        }
    }

    void updatePreviewItems(boolean animate) {
        int numOfPrevItemsAux = mFirstPageParams.size();
        buildParamsForPage(0, mFirstPageParams, animate);
        mNumOfPrevItems = numOfPrevItemsAux;
    }

    void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        boolean modified = false;
        for (PreviewItemDrawingParams param : mFirstPageParams) {
            if (itemCheck.test(param.item)
                    || (param.item instanceof AppPairInfo api && api.anyMatch(itemCheck))) {
                setDrawable(param, param.item);
                modified = true;
            }
        }
        for (PreviewItemDrawingParams param : mCurrentPageParams) {
            if (itemCheck.test(param.item)
                    || (param.item instanceof AppPairInfo api && api.anyMatch(itemCheck))) {
                setDrawable(param, param.item);
                modified = true;
            }
        }
        if (modified) {
            mIcon.invalidate();
        }
    }

    boolean verifyDrawable(@NonNull Drawable who) {
        for (int i = 0; i < mFirstPageParams.size(); i++) {
            if (mFirstPageParams.get(i).drawable == who) {
                return true;
            }
        }
        return false;
    }

    float getIntrinsicIconSize() {
        return mIntrinsicIconSize;
    }

    /**
     * Handles the case where items in the preview are either:
     * - Moving into the preview
     * - Moving into a new position
     * - Moving out of the preview
     *
     * @param oldItems The list of items in the old preview.
     * @param newItems The list of items in the new preview.
     * @param dropped  The item that was dropped onto the FolderIcon.
     */
    public void onDrop(List<ItemInfo> oldItems, List<ItemInfo> newItems, ItemInfo dropped) {
        int numItems = newItems.size();
        final ArrayList<PreviewItemDrawingParams> params = mFirstPageParams;
        buildParamsForPage(0, params, false);

        if (mIcon.usesWorkspacePreviewLayout()) {
            onParamsChanged();
            return;
        }

        // New preview items for items that are moving in (except for the dropped item).
        List<ItemInfo> moveIn = new ArrayList<>();
        for (ItemInfo newItem : newItems) {
            if (!oldItems.contains(newItem) && !newItem.equals(dropped)) {
                moveIn.add(newItem);
            }
        }
        for (int i = 0; i < moveIn.size(); ++i) {
            int prevIndex = newItems.indexOf(moveIn.get(i));
            PreviewItemDrawingParams p = params.get(prevIndex);
            computePreviewItemDrawingParams(prevIndex, numItems, p);
            updateTransitionParam(p, moveIn.get(i), ENTER_INDEX, newItems.indexOf(moveIn.get(i)),
                    numItems);
        }

        // Items that are moving into new positions within the preview.
        for (int newIndex = 0; newIndex < newItems.size(); ++newIndex) {
            int oldIndex = oldItems.indexOf(newItems.get(newIndex));
            if (oldIndex >= 0 && newIndex != oldIndex) {
                PreviewItemDrawingParams p = params.get(newIndex);
                updateTransitionParam(p, newItems.get(newIndex), oldIndex, newIndex, numItems);
            }
        }

        // Old preview items that need to be moved out.
        List<ItemInfo> moveOut = new ArrayList<>(oldItems);
        moveOut.removeAll(newItems);
        for (int i = 0; i < moveOut.size(); ++i) {
            ItemInfo item = moveOut.get(i);
            int oldIndex = oldItems.indexOf(item);
            PreviewItemDrawingParams p = computePreviewItemDrawingParams(oldIndex, numItems, null);
            updateTransitionParam(p, item, oldIndex, EXIT_INDEX, numItems);
            params.add(0, p); // We want these items first so that they are on drawn last.
        }

        for (int i = 0; i < params.size(); ++i) {
            if (params.get(i).anim != null) {
                params.get(i).anim.start();
            }
        }
    }

    private void updateTransitionParam(final PreviewItemDrawingParams p, ItemInfo item,
            int prevIndex, int newIndex, int numItems) {
        setDrawable(p, item);

        FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, prevIndex, numItems,
                newIndex, numItems, DROP_IN_ANIMATION_DURATION, null);
        if (p.anim != null && !p.anim.hasEqualFinalState(anim)) {
            p.anim.cancel();
        }
        p.anim = anim;
    }

    @VisibleForTesting
    public void setDrawable(PreviewItemDrawingParams p, ItemInfo item) {
        setDrawableInternal(p, item, true /* loadHighResIcon */);
    }

    private void setDrawableInternal(
            PreviewItemDrawingParams p, ItemInfo item, boolean loadHighResIcon) {
        if (item instanceof WorkspaceItemInfo wii) {
            boolean useThemed = !AxFolderExt.isAllAppsFolder(mIcon.mInfo)
                    || ALLAPPS_THEMED_ICONS.get(mContext);
            int flags = useThemed ? FLAG_THEMED : 0;
            if (wii.shouldShowPendingIcon()) {
                p.drawable = newPendingIcon(wii, mContext, flags);
            } else {
                p.drawable = wii.newIcon(mContext, flags);
            }
            p.drawable.setBounds(0, 0, mIconSize, mIconSize);
        } else if (item instanceof AppPairInfo api) {
            AppPairIconDrawingParams appPairParams =
                    new AppPairIconDrawingParams(mContext, DISPLAY_FOLDER);
            p.drawable = AppPairIconGraphic.composeDrawable(api, appPairParams);
            p.drawable.setBounds(0, 0, mIconSize, mIconSize);
        }

        p.item = item;
        // Set the callback to FolderIcon as it is responsible to drawing the icon. The
        // callback will be released when the folder is opened.
        p.drawable.setCallback(mIcon);

        // Verify high res
        if (item instanceof ItemInfoWithIcon info
                && info.getMatchingLookupFlag().isVisuallyLessThan(DESKTOP_ICON_FLAG)) {
            if (loadHighResIcon) {
                LauncherAppState.getInstance(mContext).getIconCache().updateIconInBackground(
                        newInfo -> {
                            if (p.item == newInfo) {
                                setDrawableInternal(p, newInfo, false /* loadHighResIcon */);
                                mIcon.invalidate();
                            }
                        }, info, DESKTOP_ICON_FLAG);
            } else {
                Log.d(TAG, "Skipping high res icon load with flags: " + info.getMatchingLookupFlag()
                        + " for " + info);
            }
        }
    }
}
