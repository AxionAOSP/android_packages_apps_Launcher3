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

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static com.android.launcher3.LauncherPrefsExt.ALL_APPS_DRAWER_LAYOUT_MODE;
import static com.android.launcher3.LauncherPrefsExt.PINNED_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.keyboard.FocusIndicatorHelper;
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class PinnedAppsRowView extends LinearLayout implements OnDeviceProfileChangeListener,
        FloatingHeaderRow, AllAppsStore.OnUpdateListener, LauncherPrefChangeListener {

    private final ActivityContext mActivityContext;
    private final AllAppsStore mAllAppsStore;
    private final FocusIndicatorHelper mFocusHelper;
    private final List<WorkspaceItemInfo> mPinnedApps = new ArrayList<>();
    private final int mTopRowExtraHeight;
    private final int mVerticalPadding;

    private FloatingHeaderView mParent;
    private int mNumPinnedAppsPerRow;
    private boolean mPinnedAppsVisible;

    public PinnedAppsRowView(@NonNull Context context) {
        this(context, null);
    }

    public PinnedAppsRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.HORIZONTAL);

        mActivityContext = ActivityContext.lookupContext(context);
        mAllAppsStore = mActivityContext.getActivityComponent().getAppsStore();
        mFocusHelper = new SimpleFocusIndicatorHelper(this);
        mNumPinnedAppsPerRow = mActivityContext.getDeviceProfile().numShownAllAppsColumns;
        mTopRowExtraHeight = getResources().getDimensionPixelSize(
                R.dimen.all_apps_search_top_row_extra_height);
        mVerticalPadding = getResources().getDimensionPixelSize(
                R.dimen.all_apps_predicted_icon_vertical_padding);
        updateVisibility();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Build.VERSION.SDK_INT >= UPSIDE_DOWN_CAKE) {
            info.setContainerTitle(getContext().getString(R.string.title_pinned_apps));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mActivityContext.addOnDeviceProfileChangeListener(this);
        mAllAppsStore.addUpdateListener(this);
        LauncherPrefs.get(getContext()).addListener(this, PINNED_APPS, ALL_APPS_DRAWER_LAYOUT_MODE);
        updatePinnedApps();
    }

    @Override
    protected void onDetachedFromWindow() {
        LauncherPrefs.get(getContext()).removeListener(this, PINNED_APPS,
                ALL_APPS_DRAWER_LAYOUT_MODE);
        mAllAppsStore.removeUpdateListener(this);
        mActivityContext.removeOnDeviceProfileChangeListener(this);
        mAllAppsStore.unregisterIconContainer(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
    }

    @Override
    public int getExpectedHeight() {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int iconHeight = deviceProfile.getAllAppsProfile().getIconSizePx();
        int iconPadding = deviceProfile.getAllAppsProfile().getIconDrawablePaddingPx();
        int textHeight = Utilities.calculateTextHeight(
                deviceProfile.getAllAppsProfile().getIconTextSizePx());
        int totalHeight = iconHeight + iconPadding + textHeight + mVerticalPadding * 2;
        int extraHeight = deviceProfile.inv.enableTwoLinesInAllApps
                ? textHeight + mTopRowExtraHeight : mTopRowExtraHeight;
        totalHeight += extraHeight;
        return getVisibility() == GONE ? 0 : totalHeight + getPaddingTop() + getPaddingBottom();
    }

    @Override
    public boolean shouldDraw() {
        return getVisibility() != GONE;
    }

    @Override
    public boolean hasVisibleContent() {
        return mPinnedAppsVisible;
    }

    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        if (!isScrolledOut) {
            setTranslationY(scroll);
        }
        setAlpha(isScrolledOut ? 0 : 1);
        if (getVisibility() != GONE) {
            AlphaUpdateListener.updateVisibility(this);
        }
    }

    @Override
    public Class<PinnedAppsRowView> getTypeClass() {
        return PinnedAppsRowView.class;
    }

    @Override
    public View getFocusedChild() {
        return getChildAt(0);
    }

    @Override
    public boolean isVisible() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mNumPinnedAppsPerRow = dp.numShownAllAppsColumns;
        removeAllViews();
        applyPinnedApps();
    }

    @Override
    public void onAppsUpdated() {
        updatePinnedApps();
    }

    @Override
    public void onPrefChanged(String key) {
        if (PINNED_APPS.getSharedPrefKey().equals(key)
                || ALL_APPS_DRAWER_LAYOUT_MODE.getSharedPrefKey().equals(key)) {
            updatePinnedApps();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getExpectedHeight(),
                MeasureSpec.EXACTLY));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        mFocusHelper.draw(canvas);
        super.dispatchDraw(canvas);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "PinnedAppsRowView");
        writer.println(prefix + "\tmPinnedAppsVisible: " + mPinnedAppsVisible);
        writer.println(prefix + "\tmNumPinnedAppsPerRow: " + mNumPinnedAppsPerRow);
        writer.println(prefix + "\tmPinnedApps: " + mPinnedApps.size());
        for (WorkspaceItemInfo info : mPinnedApps) {
            writer.println(prefix + "\t\t" + info);
        }
    }

    private void updatePinnedApps() {
        mPinnedApps.clear();
        mPinnedApps.addAll(PinnedApps.getPinnedWorkspaceItems(getContext(), mAllAppsStore));
        applyPinnedApps();
    }

    private void applyPinnedApps() {
        updatePinnedIconSlots();
        int pinnedCount = mPinnedApps.size();

        for (int i = 0; i < getChildCount(); i++) {
            BubbleTextView icon = (BubbleTextView) getChildAt(i);
            icon.reset();
            if (pinnedCount > i) {
                icon.setVisibility(View.VISIBLE);
                WorkspaceItemInfo pinnedItem = mPinnedApps.get(i);
                pinnedItem.container = CONTAINER_ALL_APPS;
                pinnedItem.rank = i;
                pinnedItem.cellX = i;
                pinnedItem.cellY = 0;
                icon.applyFromWorkspaceItem(pinnedItem);
            } else {
                icon.setVisibility(pinnedCount == 0 ? GONE : INVISIBLE);
            }
        }

        boolean pinnedAppsVisible = pinnedCount > 0;
        if (pinnedAppsVisible != mPinnedAppsVisible) {
            mPinnedAppsVisible = pinnedAppsVisible;
            updateVisibility();
        }
        if (mParent != null) {
            mParent.onHeightUpdated();
        }
    }

    private void updatePinnedIconSlots() {
        if (getChildCount() == mNumPinnedAppsPerRow) {
            return;
        }
        while (getChildCount() > mNumPinnedAppsPerRow) {
            removeViewAt(0);
        }
        LayoutInflater inflater = LayoutInflater.from(getContext());
        while (getChildCount() < mNumPinnedAppsPerRow) {
            BubbleTextView icon = (BubbleTextView) inflater.inflate(
                    R.layout.all_apps_prediction_row_icon, this, false);
            icon.setOnClickListener(mActivityContext.getItemOnClickListener());
            icon.setOnLongClickListener(mActivityContext.getAllAppsItemLongClickListener());
            icon.setLongPressTimeoutFactor(1f);
            icon.setOnFocusChangeListener(mFocusHelper);

            LayoutParams lp = (LayoutParams) icon.getLayoutParams();
            if (Flags.enableFocusOutline()) {
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            } else {
                lp.height = mActivityContext.getDeviceProfile().getAllAppsProfile()
                        .getCellHeightPx();
            }
            lp.width = 0;
            lp.weight = 1;
            addView(icon);
        }
    }

    private void updateVisibility() {
        boolean visible = mPinnedAppsVisible && !AxSmartDrawerManager.isEnabled(getContext());
        setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            mAllAppsStore.registerIconContainer(this);
        } else {
            mAllAppsStore.unregisterIconContainer(this);
        }
    }
}
