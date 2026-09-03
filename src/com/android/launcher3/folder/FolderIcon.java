/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS;
import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.axion.blur.AxBlurColors;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherState;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.graphics.AxBackdropBlurSurface;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.LabelState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.Poppable;
import com.android.launcher3.popup.PoppableType;
import com.android.launcher3.popup.PopupController;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.FloatingIconViewCompanion;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FloatingIconViewCompanion,
        DraggableView, Reorderable, Poppable {

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    @Thunk ActivityContext mActivity;
    @Thunk Folder mFolder;
    public FolderInfo mInfo;

    private final CheckLongPressHelper mLongPressHelper;

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    @Thunk BubbleTextView mFolderName;

    PreviewBackground mBackground = new PreviewBackground(getContext());

    private AxBackdropBlurSurface mBlurSurface;

    private final Path mBlurPath = new Path();

    private AxBackdropBlurSurface getBlurSurface() {
        if (mBlurSurface == null) {
            mBlurSurface = new AxBackdropBlurSurface(this, mActivity);
        }
        return mBlurSurface;
    }
    private boolean mBackgroundIsVisible = true;
    private com.android.launcher3.graphics.SelectionIndicatorRenderer mSelectionRenderer;
    private final Rect mTempIconBounds = new Rect();

    FolderGridOrganizer mPreviewVerifier;
    final ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private final PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private final List<ItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private final Alarm mOpenAlarm = new Alarm(Looper.getMainLooper());

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private Rect mTouchArea = new Rect();

    @Nullable
    private FolderPreviewLayout.ItemPlacement mPressedPreviewItem;

    private ValueAnimator mPressScaleAnimator;

    private int mDefaultLabelPaddingTop = -1;
    private int mDefaultLabelPaddingBottom = -1;

    private final FloatProperty<FolderIcon> mPressScaleProperty =
            new FloatProperty<FolderIcon>("pressScale") {
                @Override
                public void setValue(FolderIcon obj, float value) {
                    obj.setScaleX(value);
                    obj.setScaleY(value);
                }

                @Override
                public Float get(FolderIcon obj) {
                    return obj.getScaleX();
                }
            };

    @Nullable
    private PreviewItemLaunchSource mPreviewItemLaunchSource;

    private float mScaleForReorderBounce = 1f;
    private PopupController mPopupController;

    private static final Property<FolderIcon, Float> DOT_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mDotScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mDotScale = value;
            folderIcon.invalidate();
        }
    };

    private boolean mRequestedTextVisible = true;

    private boolean shouldShowFolderName() {
        return mRequestedTextVisible
                && mFolderName.shouldShowLabel();
    }

    public int getFolderLabelHeight() {
        if (shouldShowFolderName() && mFolderName != null) {
            Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
            return mFolderName.getCompoundDrawablePadding() + (int) Math.ceil(fm.bottom - fm.top);
        }
        return 0;
    }

    int getCurrentSpanX() {
        if (!usesWorkspacePreviewLayout()) return 1;

        return getLayoutParams() instanceof CellLayoutLayoutParams lp
                ? lp.cellHSpan
                : mInfo.spanX;
    }

    int getCurrentSpanY() {
        if (!usesWorkspacePreviewLayout()) return 1;

        return getLayoutParams() instanceof CellLayoutLayoutParams lp
                ? lp.cellVSpan
                : mInfo.spanY;
    }

    boolean usesWorkspacePreviewLayout() {
        return mInfo != null
                && mInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP;
    }

    private void updateTextVisibility() {
        mFolderName.setVisibility(shouldShowFolderName() ? VISIBLE : INVISIBLE);
        if (mFolderName != null && mFolderName.getLayoutParams() instanceof FrameLayout.LayoutParams lp) {
            lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.topMargin = 0;

            if (isMultiSpanFolder()) {
                lp.height = FrameLayout.LayoutParams.WRAP_CONTENT;
                mFolderName.setCompoundDrawables(null, null, null, null);
                mFolderName.setPadding(mFolderName.getPaddingLeft(), 0, mFolderName.getPaddingRight(), 0);
            } else if (mActivity != null && mInfo != null) {
                DeviceProfile grid = mActivity.getDeviceProfile();
                boolean isAllAppsFolder = AxFolderExt.isAllAppsFolder(mInfo);
                if (isAllAppsFolder) {
                    lp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                    lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    lp.topMargin = grid.getAllAppsProfile().getIconSizePx()
                            + grid.getAllAppsProfile().getIconDrawablePaddingPx();
                }
                mFolderName.setIconVisible(false);
                int topPadding = mDefaultLabelPaddingTop != -1 ? mDefaultLabelPaddingTop : mFolderName.getPaddingTop();
                int bottomPadding = mDefaultLabelPaddingBottom != -1 ? mDefaultLabelPaddingBottom : mFolderName.getPaddingBottom();
                mFolderName.setPadding(mFolderName.getPaddingLeft(), topPadding, mFolderName.getPaddingRight(), bottomPadding);
            }
            mFolderName.setLayoutParams(lp);
        }
    }

    public FolderIcon(Context context) {
        this(context, null);
    }

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLongPressHelper = new CheckLongPressHelper(this);
        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
        mDotParams.setDotColor(Themes.getAttrColor(context, R.attr.notificationDotColor));
        mDotParams.shapeInfo = ThemeManager.INSTANCE.get(context).getIconState().getIconShapeInfo();
    }

    public static <T extends Context & ActivityContext> FolderIcon inflateFolderAndIcon(int resId,
            T activityContext, ViewGroup group, FolderInfo folderInfo) {
        Folder folder = Folder.fromXml(activityContext);

        FolderIcon icon = inflateIcon(resId, activityContext, group, folderInfo);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);

        icon.setFolder(folder);
        return icon;
    }

    /**
     * Builds a FolderIcon to be added to the activity.
     * This method doesn't add any listeners to the FolderInfo, and hence any changes to the info
     * will not be reflected in the folder.
     */
    public static FolderIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        FolderIcon icon = (FolderIcon) inflater.inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = icon.findViewById(R.id.folder_icon_name);
        icon.mDefaultLabelPaddingTop = icon.mFolderName.getPaddingTop();
        icon.mDefaultLabelPaddingBottom = icon.mFolderName.getPaddingBottom();
        if (icon.mFolderName.shouldShowLabel()) {
            icon.mFolderName.applyLabel(folderInfo.title);
        }
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        lp.topMargin = AxFolderExt.isAllAppsFolder(folderInfo)
                ? grid.getAllAppsProfile().getIconSizePx()
                        + grid.getAllAppsProfile().getIconDrawablePaddingPx()
                : grid.getWorkspaceIconProfile().getIconSizePx()
                        + grid.getWorkspaceIconProfile().getIconDrawablePaddingPx();

        icon.setTag(folderInfo);
        icon.setOnClickListener(icon::handleClick);
        icon.mInfo = folderInfo;
        icon.mActivity = activity;
        icon.mDotRenderer = grid.mDotRendererWorkSpace;

        icon.updateDotInfo();
        icon.setContentDescription(icon.getAccessiblityTitle(folderInfo.title));

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        icon.mPreviewVerifier = createFolderGridOrganizer(activity.getDeviceProfile());
        icon.mPreviewVerifier.setFolderInfo(folderInfo);
        icon.updatePreviewItems(false);

        return icon;
    }

    public void animateBgShadowAndStroke() {
        mBackground.fadeInBackgroundShadow();
        mBackground.animateBackgroundStroke();
    }

    public BubbleTextView getFolderName() {
        return mFolderName;
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds, ICON_OVERLAP_FACTOR);
    }

    public void getPreviewBackgroundPath(Path outPath) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getDrawnShapePath(outPath);
    }

    public boolean isPreviewBackgroundAnimating() {
        return mBackground.isBoundsAnimating();
    }

    public float getBackgroundStrokeWidth() {
        return mBackground.getStrokeWidth();
    }

    public float getPreviewBackgroundCornerRadius() {
        return mBackground.getDrawnCornerRadius();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
    }

    public boolean isMultiSpanFolder() {
        return getCurrentSpanX() > 1 || getCurrentSpanY() > 1;
    }

    private boolean willAcceptItem(ItemInfo item) {
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public boolean isPointInBackground(float x, float y) {
        mBackground.getBounds(mTouchArea);
        return mTouchArea.contains((int) x, (int) y);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof WorkspaceItemFactory)
                        || (dragInfo instanceof PendingAddShortcutInfo)
                        || Folder.willAccept(dragInfo))) {
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
        }
    };

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final ItemInfo destInfo, final View destView,
            final ItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        prepareCreateAnimation(destView);
        getFolder().addFolderContent(destInfo);

        if (!usesWorkspacePreviewLayout()) {
            mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                    .start();
        }

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, d, dstRect, scaleRelativeToDragLayer, 1,
                false /* itemReturnedOnFailedDrop */);
    }

    public void performDestroyAnimation(Runnable onCompleteRunnable) {
        // This will animate the final item in the preview to be full size.
        mPreviewItemManager.createFirstItemAnimation(true /* reverse */, onCompleteRunnable)
                .start();
    }

    public void onDragExit() {
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;

        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null && mActivity instanceof Launcher) {
            final Launcher launcher = (Launcher) mActivity;
            DragLayer dragLayer = launcher.getDragLayer();
            Rect to = finalRect;

            if (to == null) {
                to = new Rect();
                Workspace<?> workspace = launcher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform();
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform();
            }

            boolean usesWorkspacePreview = usesWorkspacePreviewLayout();
            PreviewDropAnimationTarget target = usesWorkspacePreview
                    ? prepareWorkspacePreviewDrop(item, index)
                    : prepareLegacyPreviewDrop(
                            item, index, itemReturnedOnFailedDrop);

            int centerX = Math.round(
                    scaleRelativeToDragLayer * target.centerX);
            int centerY = Math.round(
                    scaleRelativeToDragLayer * target.centerY);

            to.offset(centerX - animateView.getMeasuredWidth() / 2,
                    centerY - animateView.getMeasuredHeight() / 2);

            float finalScale =
                    target.scale * scaleRelativeToDragLayer;

            // Account for potentially different icon sizes with non-default grid settings
            if (d.dragSource instanceof ActivityAllAppsContainerView) {
                DeviceProfile grid = mActivity.getDeviceProfile();
                float containerScale = (1f * grid.getWorkspaceIconProfile().getIconSizePx()
                        / grid.getAllAppsProfile().getIconSizePx());
                finalScale *= containerScale;
            }

            dragLayer.animateView(animateView, to, target.alpha,
                    finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DECELERATE_2,
                    () -> completePreviewDropAnimation(
                            item, target.index, usesWorkspacePreview),
                    DragLayer.ANIMATION_END_DISAPPEAR, null);

            mFolder.hideItem(item);

            if (!target.itemAdded) {
                mPreviewItemManager.hidePreviewItem(target.index, true);
            }

            d.folderNameSuggestionLoader.getSuggestedFolderName(mInfo.getAppContents(),
                    folderNameInfos -> postDelayed(() -> {
                        setLabelSuggestion(folderNameInfos, d.logInstanceId);
                        invalidate();
                    }, DROP_IN_ANIMATION_DURATION));

        } else {
            getFolder().addFolderContent(item);
        }
    }

    private static final class PreviewDropAnimationTarget {
        final int centerX;
        final int centerY;
        final float scale;
        final float alpha;
        final int index;
        final boolean itemAdded;

        PreviewDropAnimationTarget(
                int centerX, int centerY, float scale, float alpha,
                int index, boolean itemAdded) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.scale = scale;
            this.alpha = alpha;
            this.index = index;
            this.itemAdded = itemAdded;
        }
    }

    private PreviewDropAnimationTarget prepareWorkspacePreviewDrop(
            ItemInfo item, int index) {
        FolderPreviewLayout.Snapshot oldSnapshot =
                mPreviewItemManager.calculateWorkspacePreviewSnapshot();

        getFolder().addFolderContent(item, index, false);

        FolderPreviewLayout.Snapshot newSnapshot =
                mPreviewItemManager.calculateWorkspacePreviewSnapshot();
        mPreviewItemManager.animateWorkspacePreviewSnapshot(
                oldSnapshot, newSnapshot, item);

        FolderPreviewLayout.ItemPlacement placement =
                mPreviewItemManager.findWorkspacePreviewPlacement(
                        newSnapshot, item);

        RectF targetBounds;
        float targetScale;
        float targetAlpha;

        if (placement != null) {
            targetBounds = placement.getBounds();
            targetScale = targetBounds.width()
                    / mPreviewItemManager.getIntrinsicIconSize();
            targetAlpha = 1f;
        } else {
            targetBounds = newSnapshot.getOverviewBounds();
            targetAlpha = 0f;
            targetScale = calculateTargetScale(newSnapshot);
            if (targetBounds == null) {
                targetBounds = newSnapshot.getBackgroundBounds();
            }
        }

        return new PreviewDropAnimationTarget(
                Math.round(targetBounds.centerX()),
                Math.round(targetBounds.centerY()),
                targetScale,
                targetAlpha,
                index,
                true);
    }

    private PreviewDropAnimationTarget prepareLegacyPreviewDrop(
            ItemInfo item, int index,
            boolean itemReturnedOnFailedDrop) {
        int numItemsInPreview =
                Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index + 1);
        boolean itemAdded = false;

        if (itemReturnedOnFailedDrop
                || index >= MAX_NUM_ITEMS_IN_PREVIEW) {
            List<ItemInfo> oldPreviewItems =
                    new ArrayList<>(mCurrentPreviewItems);
            getFolder().addFolderContent(item, index, false);
            mCurrentPreviewItems.clear();
            mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));

            if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                int newIndex = mCurrentPreviewItems.indexOf(item);
                if (newIndex >= 0) {
                    index = newIndex;
                }

                mPreviewItemManager.hidePreviewItem(index, true);
                mPreviewItemManager.onDrop(
                        oldPreviewItems, mCurrentPreviewItems, item);
                itemAdded = true;
            } else {
                getFolder().removeFolderContent(false, item);
            }
        }

        if (!itemAdded) {
            getFolder().addFolderContent(item, index, true);
        }

        int[] center = new int[2];
        float scale = getLocalCenterForIndex(
                index, numItemsInPreview, center);
        float alpha =
                index < MAX_NUM_ITEMS_IN_PREVIEW ? 1f : 0f;
        return new PreviewDropAnimationTarget(
                center[0], center[1], scale, alpha, index, itemAdded);
    }

    private float calculateTargetScale(
            FolderPreviewLayout.Snapshot snapshot) {
        for (FolderPreviewLayout.ItemPlacement candidate
                : snapshot.getItems()) {
            if (candidate.getRole()
                    == FolderPreviewLayout.ItemRole.OVERVIEW) {
                return candidate.getBounds().width()
                        / mPreviewItemManager.getIntrinsicIconSize();
            }
        }
        return 0f;
    }

    private void completePreviewDropAnimation(
            ItemInfo item, int index,
            boolean usesWorkspacePreview) {
        if (usesWorkspacePreview) {
            mPreviewItemManager.setWorkspacePreviewItemHidden(
                    item, false);
        } else {
            mPreviewItemManager.hidePreviewItem(index, false);
        }

        mFolder.showItem(item);
    }

    private final class PreviewItemLaunchSource extends BubbleTextView {

        @Nullable
        private ItemInfo mItem;
        private boolean mIsAppCloseSource;

        PreviewItemLaunchSource(Context context) {
            super(context);
            setWillNotDraw(true);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(
                    IMPORTANT_FOR_ACCESSIBILITY_NO);
            setVisibility(INVISIBLE);
        }

        void prepare(ItemInfo item, RectF bounds) {
            resetLaunchSource();

            mItem = item;
            applyFromWorkspaceItem((WorkspaceItemInfo) item);

            int left = Math.round(bounds.left);
            int top = Math.round(bounds.top);
            int width = Math.max(1, Math.round(bounds.right) - left);
            int height = Math.max(1, Math.round(bounds.bottom) - top);

            FrameLayout.LayoutParams lp =
                    (FrameLayout.LayoutParams) getLayoutParams();
            lp.width = width;
            lp.height = height;
            lp.leftMargin = left - FolderIcon.this.getPaddingLeft();
            lp.topMargin = top - FolderIcon.this.getPaddingTop();
            setLayoutParams(lp);

            layout(left, top, left + width, top + height);
            setVisibility(VISIBLE);
        }

        void prepare(FolderPreviewLayout.ItemPlacement placement) {
            prepare(placement.getItem(), placement.getBounds());
        }

        @Override
        public void setIconVisible(boolean visible) {
            if (mItem == null) return;

            if (!visible) {
                mPreviewItemManager.setWorkspacePreviewItemHidden(
                        mItem, true);
            } else if (mIsAppCloseSource) {
                resetLaunchSource();
            } else {
                mPreviewItemManager.setWorkspacePreviewItemHidden(
                        mItem, false);
            }
        }

        @Override
        public void setForceHideDot(boolean hide) {
        }

        @Override
        public void onDraw(Canvas canvas) {
        }

        @Override
        public void getIconBounds(Rect outBounds) {
            outBounds.set(0, 0, getWidth(), getHeight());
        }

        private void resetLaunchSource() {
            if (mItem != null) {
                mPreviewItemManager.setWorkspacePreviewItemHidden(
                        mItem, false);
            }

            mItem = null;
            mIsAppCloseSource = false;
            super.reset();
            setVisibility(INVISIBLE);
        }

        void prepareForAppClose(
                ItemInfo item, RectF bounds) {
            prepare(item, bounds);
            mIsAppCloseSource = true;
        }
    }

    private View preparePreviewItemLaunchSource(
            FolderPreviewLayout.ItemPlacement placement) {
        if (mPreviewItemLaunchSource == null) {
            mPreviewItemLaunchSource =
                    new PreviewItemLaunchSource(getContext());
            addView(mPreviewItemLaunchSource,
                    new FrameLayout.LayoutParams(0, 0));
        }

        mPreviewItemLaunchSource.prepare(placement);
        return mPreviewItemLaunchSource;
    }

    @Nullable
    public View getPreviewItemLaunchSourceForAppClose(
            Predicate<ItemInfo> matcher) {
        ItemInfo item = null;
        for (ItemInfo candidate : mInfo.getContents()) {
            if (matcher.test(candidate)) {
                item = candidate;
                break;
            }
        }

        if (item == null) return null;

        FolderPreviewLayout.Snapshot snapshot =
                mPreviewItemManager.calculateWorkspacePreviewSnapshot();
        FolderPreviewLayout.ItemPlacement placement =
                mPreviewItemManager.findWorkspacePreviewPlacement(
                        snapshot, item);

        RectF targetBounds;
        if (placement != null) {
            targetBounds = placement.getBounds();
        } else {
            RectF overviewBounds = snapshot.getOverviewBounds();
            if (overviewBounds == null) return null;

            targetBounds = new RectF(
                overviewBounds.centerX(),
                overviewBounds.centerY(),
                overviewBounds.centerX(),
                overviewBounds.centerY());
        }

        if (mPreviewItemLaunchSource == null) {
            mPreviewItemLaunchSource =
                    new PreviewItemLaunchSource(getContext());
            addView(mPreviewItemLaunchSource,
                    new FrameLayout.LayoutParams(0, 0));
        }

        mPreviewItemLaunchSource.prepareForAppClose(
                item, targetBounds);
        return mPreviewItemLaunchSource;
    }

    private void handleClick(View view) {
        if (mPressedPreviewItem != null
                && mPressedPreviewItem.getItem() instanceof WorkspaceItemInfo item
                && mActivity instanceof Launcher launcher
                && mFolder != null
                && !mFolder.isOpen()
                && !mFolder.isDestroyed()
                && launcher.getWorkspace().isFinishedSwitchingState()
                && !launcher.isInState(LauncherState.EDIT_MODE)
                && !launcher.getDragController().isDragging()) {
            View launchSource =
                    preparePreviewItemLaunchSource(mPressedPreviewItem);
            ItemClickHandler.onClickAppShortcut(launchSource, item, launcher);
            return;
        }
        mActivity.getItemOnClickListener().onClick(view);
    }

    /**
     * Set the suggested folder name.
     */
    public void setLabelSuggestion(FolderNameInfos nameInfos, InstanceId instanceId) {
        if (!mInfo.getLabelState().equals(LabelState.UNLABELED)) {
            return;
        }
        if (nameInfos == null || !nameInfos.hasSuggestions()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS);
            return;
        }
        if (!nameInfos.hasPrimary()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY);
            return;
        }
        CharSequence newTitle = nameInfos.getLabels()[0];
        FromState fromState = mInfo.getFromLabelState();

        mInfo.setTitle(newTitle, mActivity.getModelWriter());
        onTitleChanged(mInfo.title);
        mFolder.getFolderName().setText(mInfo.title);

        // Logging for folder creation flow
        StatsLogManager.newInstance(getContext()).logger()
                .withInstanceId(instanceId)
                .withItemInfo(mInfo)
                .withFromState(fromState)
                .withToState(ToState.TO_SUGGESTION0)
                // When LAUNCHER_FOLDER_LABEL_UPDATED event.edit_text does not have delimiter,
                // event is assumed to be folder creation on the server side.
                .withEditText(newTitle.toString())
                .log(LAUNCHER_FOLDER_AUTO_LABELED);
    }


    public void onDrop(DragObject d, boolean itemReturnedOnFailedDrop) {
        if (!d.multiDragInfo.isEmpty() && mActivity instanceof Launcher) {
            Launcher launcher = (Launcher) mActivity;
            mFolder.notifyDrop();
            
            ItemInfo primaryItem = d.dragInfo;
            boolean animatedPrimary = false;

            for (int i = 0; i < d.multiDragInfo.size(); i++) {
                ItemInfo info = d.multiDragInfo.get(i);
                ItemInfo item;
                if (info instanceof WorkspaceItemFactory) {
                    item = ((WorkspaceItemFactory) info).makeWorkspaceItem(getContext());
                } else if (d.dragSource instanceof BaseItemDragListener) {
                    item = new WorkspaceItemInfo((WorkspaceItemInfo) info);
                } else {
                    item = info;
                }
                
                View itemView = launcher.getWorkspace().getFirstMatch(com.android.launcher3.util.ItemInfoMatcher.ofItemIds(com.android.launcher3.util.IntSet.wrap(item.id)));
                if (itemView != null) {
                    CellLayout parentLayout = launcher.getWorkspace().getParentCellLayoutForView(itemView);
                    if (parentLayout != null) {
                        parentLayout.getOccupied().markCells(info.cellX, info.cellY, info.spanX, info.spanY, false);
                        parentLayout.removeView(itemView);
                    }
                }
                
                boolean shouldAnimate = (primaryItem != null && info.id == primaryItem.id) || (!animatedPrimary && (i == d.multiDragInfo.size() - 1));
                if (shouldAnimate && !animatedPrimary) {
                    onDrop(item, d, null, 1.0f, mInfo.getContents().size(), false);
                    animatedPrimary = true;
                } else {
                    getFolder().addFolderContent(item);
                }
            }
            
            launcher.getMultiSelectController().clearSelection();
            launcher.getStateManager().goToState(com.android.launcher3.LauncherState.NORMAL);
            return;
        }

        ItemInfo item;
        if (d.dragInfo instanceof WorkspaceItemFactory) {
            // Came from all apps -- make a copy
            item = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(getContext());
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            if (d.dragInfo instanceof AppPairInfo) {
                // dragged item is app pair
                item = new AppPairInfo((AppPairInfo) d.dragInfo);
            } else {
                // dragged item is WorkspaceItemInfo
                item = new WorkspaceItemInfo((WorkspaceItemInfo) d.dragInfo);
            }
        } else {
            item = d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.getContents().size(),
                itemReturnedOnFailedDrop
        );
    }

    /** Keep the notification dot up to date with the sum of all the content's dots. */
    public void updateDotInfo() {
        boolean hadDot = mDotInfo.hasDot();
        mDotInfo.reset();
        for (ItemInfo si : mInfo.getContents()) {
            mDotInfo.addDotInfo(mActivity.getDotInfoForItem(si));
        }
        boolean isDotted = mDotInfo.hasDot();
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((hadDot ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        return mPreviewLayoutRule;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public boolean hasDot() {
        return mDotInfo != null && mDotInfo.hasDot();
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index), curNumItems, mTmpParams);

        mTmpParams.transX += mBackground.getPreviewLeft();
        mTmpParams.transY += mBackground.getPreviewTop();

        float intrinsicIconSize = mPreviewItemManager.getIntrinsicIconSize();
        float offsetX = mTmpParams.transX + (mTmpParams.scale * intrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * intrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mTmpParams.scale;
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    @Override
    public void setIconVisible(boolean visible) {
        mBackgroundIsVisible = visible;
        if (!visible && mBlurSurface != null) {
            mBlurSurface.release();
        }
        invalidate();
    }

    public boolean getIconVisible() {
        return mBackgroundIsVisible;
    }

    public PreviewBackground getFolderBackground() {
        return mBackground;
    }

    public PreviewItemManager getPreviewItemManager() {
        return mPreviewItemManager;
    }

    public FolderPreviewLayout.GridUsage calculateWorkspacePreviewGridUsage(
            int availableSpaceX,
            int availableSpaceY,
            int spanX,
            int spanY) {
        return mPreviewItemManager.calculateWorkspacePreviewGridUsage(
                availableSpaceX,
                availableSpaceY,
                spanX,
                spanY);
    }

    public boolean isPreviewTightlyWrapped(
            int availableSpaceX,
            int availableSpaceY,
            int spanX,
            int spanY) {
        return mPreviewItemManager.isPreviewTightlyWrapped(
            availableSpaceX,
            availableSpaceY,
            spanX,
            spanY);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean drewBlur = false;
        boolean canBlur = !mBackground.drawingDelegated() && mBackgroundIsVisible;
        if (canBlur && getLayerType() != View.LAYER_TYPE_HARDWARE) {
            mBackground.getDrawnShapePath(mBlurPath);
            drewBlur = getBlurSurface().drawPath(canvas, mBlurPath,
                    mBackground.getDrawnCornerRadius());
        } else if (canBlur) {
            postInvalidateOnAnimation();
        }

        super.dispatchDraw(canvas);

        if (!mBackgroundIsVisible) return;

        mPreviewItemManager.recomputePreviewDrawingParams();

        if (!mBackground.drawingDelegated()) {
            if (drewBlur) {
                mBackground.drawBackground(canvas,
                        AxBlurColors.surfaceEffect0(getContext()));
            } else {
                mBackground.drawBackground(canvas);
            }
        }

        if (mCurrentPreviewItems.isEmpty() && !mAnimating) return;

        mPreviewItemManager.draw(canvas);

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }

        drawDot(canvas);

        if (mActivity instanceof Launcher launcher && launcher.isInState(com.android.launcher3.LauncherState.EDIT_MODE)) {
            Object tag = getTag();
            if (tag instanceof ItemInfo info) {
                if (info.container != com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    info.container != com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    return;
                }
                if (mSelectionRenderer == null) {
                    mSelectionRenderer = new com.android.launcher3.graphics.SelectionIndicatorRenderer(getContext());
                }
                boolean isSelected = launcher.getMultiSelectController().isSelected(info);
                mBackground.getBounds(mTempIconBounds);
                mSelectionRenderer.draw(canvas, mTempIconBounds, isSelected, mActivity.getDeviceProfile().getWorkspaceIconProfile().getIconSizePx());
            }
        }
    }

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;
            // FolderIcon draws the icon to be top-aligned (with padding) & horizontally-centered
            int iconSize = mActivity.getDeviceProfile().getWorkspaceIconProfile().getIconSizePx();
            iconBounds.left = (getWidth() - iconSize) / 2;
            iconBounds.right = iconBounds.left + iconSize;
            iconBounds.top = getPaddingTop();
            iconBounds.bottom = iconBounds.top + iconSize;

            float iconScale = (float) mBackground.previewSize / iconSize;
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getAcceptScaleProgress());
            mDotRenderer.draw(canvas, mDotParams);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean isAllAppsFolder = AxFolderExt.isAllAppsFolder(mInfo);
        boolean shouldCenterIcon = isAllAppsFolder
                || mActivity.getDeviceProfile().getWorkspaceIconProfile().getIconCenterVertically()
                || !shouldShowFolderName();

        updateTextVisibility();

        if (shouldCenterIcon) {
            int iconSize = isAllAppsFolder
                    ? mActivity.getDeviceProfile().getAllAppsProfile().getIconSizePx()
                    : mActivity.getDeviceProfile().getWorkspaceIconProfile().getIconSizePx();
            int cellHeightPx;
            if (shouldShowFolderName()) {
                Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
                cellHeightPx = iconSize + mFolderName.getCompoundDrawablePadding()
                        + (int) Math.ceil(fm.bottom - fm.top);
            } else {
                cellHeightPx = iconSize;
            }
            setPadding(getPaddingLeft(), (MeasureSpec.getSize(heightMeasureSpec)
                    - cellHeightPx) / 2, getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (shouldShowFolderName() && mFolderName != null && isMultiSpanFolder()) {
            mPreviewItemManager.recomputePreviewDrawingParams();
            Rect bgBounds = new Rect();
            mBackground.getBounds(bgBounds);
            int textWidth = mFolderName.getMeasuredWidth();
            int textHeight = mFolderName.getMeasuredHeight();

            float density = mActivity.getDeviceProfile().getWorkspaceIconProfile().getIconSizePx() / 60.f;
            int gap = Math.round(8f * density);

            int textLeft = bgBounds.left + (bgBounds.width() - textWidth) / 2;
            int textTop = bgBounds.bottom + gap;

            mFolderName.layout(textLeft, textTop, textLeft + textWidth, textTop + textHeight);
        }
    }

    /** Sets the visibility of the icon's title text */
    public void setTextVisible(boolean visible) {
        mRequestedTextVisible = visible;
        updateTextVisibility();
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    /**
     * Returns the list of items which should be visible in the preview
     */
    public List<ItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.getContents());
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    void syncPreviewItems() {
        updatePreviewItems(false);
    }

    public void onPreviewSettingsChanged() {
        updatePreviewItems(false);
        invalidate();
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
    }

    public void onItemsChanged(boolean animate) {
        updatePreviewItems(false);
        updateDotInfo();
        setContentDescription(getAccessiblityTitle(mInfo.title));
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.applyLabel(title);
        setContentDescription(getAccessiblityTitle(title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mPressedPreviewItem = null;

                if (shouldIgnoreTouchDown(event.getX(), event.getY())) {
                    return false;
                }

                mPressedPreviewItem =
                        mPreviewItemManager.findDirectItemAt(event.getX(), event.getY());
                break;

            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                if (mPressedPreviewItem != null
                        && !mPressedPreviewItem.getBounds().contains(
                                event.getX(), event.getY())) {
                    mPressedPreviewItem = null;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                mPressedPreviewItem = null;
                break;
        }

        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        super.onTouchEvent(event);
        mLongPressHelper.onTouchEvent(event);

        if (action == MotionEvent.ACTION_UP) {
            post(() -> mPressedPreviewItem = null);
        }

        // Keep receiving the rest of the events
        return true;
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        if (isMultiSpanFolder()) {
            return !isPointInBackground(x, y);
        }

        mTouchArea.set(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        return !mTouchArea.contains((int) x, (int) y);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    private boolean isInHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).clearFolderLeaveBehind(this);
        }
    }

    public void drawLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).drawFolderLeaveBehindForIcon(this);
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (mPressScaleAnimator != null) {
            mPressScaleAnimator.cancel();
        }

        float targetScale = pressed ? 0.95f : 1.0f;
        float stiffness = pressed ? 400f : 200f;
        float damping = pressed ? 0.85f : 0.65f;

        ValueAnimator animator = new com.android.launcher3.anim.SpringAnimationBuilder(getContext())
                .setStartValue(getScaleX())
                .setEndValue(targetScale * mScaleForReorderBounce)
                .setStiffness(stiffness)
                .setDampingRatio(damping)
                .setMinimumVisibleChange(0.001f)
                .build(this, mPressScaleProperty);

        animator.start();
        mPressScaleAnimator = animator;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mPressScaleAnimator != null) {
            mPressScaleAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getPreviewBounds(bounds);
    }

    /**
     * Returns a formatted accessibility title for folder
     */
    public String getAccessiblityTitle(CharSequence title) {
        if (title == null) {
            // Avoids "Talkback -> Folder: null" announcement.
            title = getContext().getString(R.string.unnamed_folder);
        }
        int size = mInfo.getContents().size();
        if (size < MAX_NUM_ITEMS_IN_PREVIEW) {
            return getContext().getString(hasDot()
                    ? R.string.folder_name_format_exact_with_dot
                    : R.string.folder_name_format_exact,
                    title, size);
        } else {
            return getContext().getString(hasDot()
                    ? R.string.folder_name_format_overflow_with_dot
                    : R.string.folder_name_format_overflow,
                    title, MAX_NUM_ITEMS_IN_PREVIEW);
        }
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        mBackground.setHovered(hovered);
    }

    @NonNull
    @Override
    public PoppableType getPoppableType() {
        return PoppableType.FOLDER;
    }

    /**
     * Interface that provides callbacks to a parent ViewGroup that hosts this FolderIcon.
     */
    public interface FolderIconParent {
        /**
         * Tells the FolderIconParent to draw a "leave-behind" when the Folder is open and leaving a
         * gap where the FolderIcon would be when the Folder is closed.
         */
        void drawFolderLeaveBehindForIcon(FolderIcon child);
        /**
         * Tells the FolderIconParent to stop drawing the "leave-behind" as the Folder is closed.
         */
        void clearFolderLeaveBehind(FolderIcon child);
    }
}
