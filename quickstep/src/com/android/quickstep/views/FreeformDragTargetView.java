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

package com.android.quickstep.views;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import com.android.launcher3.R;

public class FreeformDragTargetView {

    private static final float HOVER_SCALE = 1.2f;
    private static final long HOVER_ANIM_DURATION = 500L;
    private static final long SHOW_ANIM_DURATION = 200L;
    private static final long HIDE_ANIM_DURATION = 150L;

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final int mHoverSizePx;
    private final int mMarginBottomPx;
    private final int mNavBarHeight;
    private View mTargetView;
    private boolean mAttached;
    private boolean mHovering;

    public FreeformDragTargetView(Context context) {
        mContext = context;
        mWindowManager = context.getSystemService(WindowManager.class);
        mHoverSizePx = context.getResources()
                .getDimensionPixelSize(R.dimen.freeform_target_hover_size);
        mMarginBottomPx = context.getResources()
                .getDimensionPixelSize(R.dimen.freeform_target_margin_bottom);
        mNavBarHeight = getNavBarHeight(context);
    }

    private void ensureView() {
        if (mTargetView != null) return;
        mTargetView = LayoutInflater.from(mContext)
                .inflate(R.layout.freeform_drag_target, null);
    }

    public void show() {
        if (mAttached) return;
        ensureView();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mHoverSizePx, mHoverSizePx,
                TYPE_NAVIGATION_BAR_PANEL,
                FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.setTitle("FreeformDragTarget");
        lp.packageName = mContext.getPackageName();
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = mMarginBottomPx + mNavBarHeight;
        lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        float slideOffset = mHoverSizePx + mMarginBottomPx + mNavBarHeight;
        mTargetView.setTranslationY(slideOffset);
        mTargetView.setAlpha(0f);
        mWindowManager.addView(mTargetView, lp);
        mAttached = true;
        mTargetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(SHOW_ANIM_DURATION)
                .start();
    }

    public void hide() {
        if (!mAttached) return;
        float slideOffset = mHoverSizePx + mMarginBottomPx + mNavBarHeight;
        mTargetView.animate()
                .alpha(0f)
                .translationY(slideOffset)
                .setDuration(HIDE_ANIM_DURATION)
                .withEndAction(this::removeView)
                .start();
    }

    public void removeImmediately() {
        if (!mAttached) return;
        mTargetView.animate().cancel();
        removeView();
    }

    private void removeView() {
        if (!mAttached) return;
        mWindowManager.removeViewImmediate(mTargetView);
        mAttached = false;
        mHovering = false;
        mTargetView.setScaleX(1f);
        mTargetView.setScaleY(1f);
    }

    public void setHovering(boolean hovering) {
        if (mHovering == hovering) return;
        mHovering = hovering;
        if (mTargetView == null) return;
        float scale = hovering ? HOVER_SCALE : 1.0f;
        mTargetView.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(HOVER_ANIM_DURATION)
                .start();
        if (hovering) {
            mTargetView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    public boolean isHovering() {
        return mHovering;
    }

    public boolean isShowing() {
        return mAttached;
    }

    public boolean isEventOver(float rawX, float rawY) {
        if (!mAttached || mTargetView == null) return false;
        int[] loc = new int[2];
        mTargetView.getLocationOnScreen(loc);
        float expand = mHoverSizePx * 0.3f;
        return rawX >= loc[0] - expand && rawX <= loc[0] + mHoverSizePx + expand
                && rawY >= loc[1] - expand && rawY <= loc[1] + mHoverSizePx + expand;
    }

    private static int getNavBarHeight(Context context) {
        int resId = context.getResources().getIdentifier(
                "navigation_bar_height", "dimen", "android");
        return resId > 0 ? context.getResources().getDimensionPixelSize(resId) : 0;
    }
}
