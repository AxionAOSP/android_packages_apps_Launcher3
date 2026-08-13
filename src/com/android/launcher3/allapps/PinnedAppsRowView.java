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
    private FloatingHeaderView mParent;
    private int mNumPinnedAppsPerRow;
    private boolean mPinnedAppsVisible;

    public PinnedAppsRowView(@NonNull Context context) {
        this(context, null);
    }

    public PinnedAppsRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(LinearLayout.VERTICAL);

        mActivityContext = ActivityContext.lookupContext(context);
        mAllAppsStore = mActivityContext.getActivityComponent().getAppsStore();
        mFocusHelper = new SimpleFocusIndicatorHelper(this);
        mNumPinnedAppsPerRow = mActivityContext.getDeviceProfile().numShownAllAppsColumns;
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
        for (int i = 0; i < getChildCount(); i++) {
            mAllAppsStore.unregisterIconContainer((ViewGroup) getChildAt(i));
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] rows, boolean tabsHidden) {
        mParent = parent;
    }

    @Override
    public int getExpectedHeight() {
        DeviceProfile deviceProfile = mActivityContext.getDeviceProfile();
        int perRowHeight = deviceProfile.getAllAppsProfile().getCellHeightPx();
        int totalHeight = Math.max(1, getChildCount()) * perRowHeight;
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
        if (getChildCount() == 0) {
            return null;
        }
        LinearLayout iconRow = (LinearLayout) getChildAt(0);
        return iconRow.getChildAt(0);
    }

    @Override
    public boolean isVisible() {
        return getVisibility() == VISIBLE;
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mNumPinnedAppsPerRow = dp.numShownAllAppsColumns;
        for (int i = 0; i < getChildCount(); i++) {
            mAllAppsStore.unregisterIconContainer((ViewGroup) getChildAt(i));
        }
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
        int iconIndex = 0;

        for (int row = 0; row < getChildCount(); row++) {
            LinearLayout iconRow = (LinearLayout) getChildAt(row);
            for (int col = 0; col < iconRow.getChildCount(); col++) {
                BubbleTextView icon = (BubbleTextView) iconRow.getChildAt(col);
                icon.reset();
                if (pinnedCount > iconIndex) {
                    icon.setVisibility(View.VISIBLE);
                    WorkspaceItemInfo pinnedItem = mPinnedApps.get(iconIndex);
                    pinnedItem.container = CONTAINER_ALL_APPS;
                    pinnedItem.rank = iconIndex;
                    pinnedItem.cellX = col;
                    pinnedItem.cellY = row;
                    icon.applyFromWorkspaceItem(pinnedItem);
                } else {
                    icon.setVisibility(pinnedCount == 0 ? GONE : INVISIBLE);
                }
                iconIndex++;
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
        int pinnedCount = mPinnedApps.size();
        int neededRows = Math.max(1,
                (int) Math.ceil(pinnedCount / (double) mNumPinnedAppsPerRow));
        while (getChildCount() > neededRows) {
            mAllAppsStore.unregisterIconContainer((ViewGroup) getChildAt(getChildCount() - 1));
            removeViewAt(getChildCount() - 1);
        }
        LayoutInflater inflater = LayoutInflater.from(getContext());
        while (getChildCount() < neededRows) {
            addView(createIconRow(inflater));
        }
    }

    private LinearLayout createIconRow(LayoutInflater inflater) {
        LinearLayout iconRow = new LinearLayout(getContext());
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx()));
        for (int i = 0; i < mNumPinnedAppsPerRow; i++) {
            BubbleTextView icon = (BubbleTextView) inflater.inflate(
                    R.layout.all_apps_prediction_row_icon, iconRow, false);
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
            iconRow.addView(icon);
        }
        mAllAppsStore.registerIconContainer(iconRow);
        return iconRow;
    }

    private void updateVisibility() {
        boolean visible = mPinnedAppsVisible && !AxSmartDrawerManager.isEnabled(getContext());
        setVisibility(visible ? VISIBLE : GONE);
        for (int i = 0; i < getChildCount(); i++) {
            ViewGroup iconRow = (ViewGroup) getChildAt(i);
            if (visible) {
                mAllAppsStore.registerIconContainer(iconRow);
            } else {
                mAllAppsStore.unregisterIconContainer(iconRow);
            }
        }
    }
}
