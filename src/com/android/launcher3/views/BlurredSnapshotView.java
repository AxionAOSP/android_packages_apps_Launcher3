/*
 * Copyright (C) 2026 AxionOS
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
package com.android.launcher3.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BitmapRenderer;

public class BlurredSnapshotView extends View {
    public static final int SNAPSHOT_WALLPAPER = 1;

    private static final int SNAPSHOT_NONE = 0;
    private static final float SNAPSHOT_SCALE = 0.10f;

    private final Paint mPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();

    @Nullable private Bitmap mSnapshot;
    private int mSnapshotType = SNAPSHOT_NONE;
    private int mBlurRadius;
    private float mContentScale = 1f;
    private float mWallpaperOffset = 0.5f;

    public BlurredSnapshotView(Context context) {
        this(context, null);
    }

    public BlurredSnapshotView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlurredSnapshotView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAlpha(0f);
    }

    public boolean captureWallpaper(@Nullable Drawable wallpaper, float wallpaperOffset,
            int blurRadius) {
        if (wallpaper == null) {
            return false;
        }
        int width = getWidth();
        int height = getHeight();
        float offset = normalizeWallpaperOffset(wallpaperOffset);
        int radius = Math.max(0, blurRadius);
        int intrinsicWidth = wallpaper.getIntrinsicWidth();
        int intrinsicHeight = wallpaper.getIntrinsicHeight();
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            intrinsicWidth = width;
            intrinsicHeight = height;
        }
        float scale = Math.max((float) width / intrinsicWidth, (float) height / intrinsicHeight);
        int wallpaperWidth = Math.round(intrinsicWidth * scale);
        int wallpaperHeight = Math.round(intrinsicHeight * scale);
        int left = getOffsetStart(width, wallpaperWidth, offset);
        int top = getOffsetStart(height, wallpaperHeight, 0.5f);
        boolean captured = capture(SNAPSHOT_WALLPAPER, radius, canvas -> {
            canvas.scale(SNAPSHOT_SCALE, SNAPSHOT_SCALE);
            wallpaper.setBounds(left, top, left + wallpaperWidth, top + wallpaperHeight);
            wallpaper.draw(canvas);
        });
        if (captured) {
            mWallpaperOffset = offset;
            mBlurRadius = radius;
        }
        return captured;
    }

    public boolean hasSnapshot(int snapshotType) {
        Bitmap snapshot = mSnapshot;
        return snapshot != null
                && !snapshot.isRecycled()
                && mSnapshotType == snapshotType
                && snapshot.getWidth() == getSnapshotWidth()
                && snapshot.getHeight() == getSnapshotHeight();
    }

    public boolean hasSnapshot(int snapshotType, float wallpaperOffset, int blurRadius) {
        return hasSnapshot(snapshotType)
                && Math.abs(mWallpaperOffset - normalizeWallpaperOffset(wallpaperOffset)) < 0.001f
                && mBlurRadius == Math.max(0, blurRadius);
    }

    public void setSnapshotProgress(float progress, float contentScale) {
        float alpha = Utilities.boundToRange(progress, 0f, 1f);
        float scale = Math.max(1f, contentScale);
        boolean scaleChanged = Float.compare(mContentScale, scale) != 0;
        mContentScale = scale;
        boolean hasSnapshot = mSnapshotType != SNAPSHOT_NONE
                && mSnapshot != null
                && !mSnapshot.isRecycled();
        int visibility = alpha > 0f && hasSnapshot ? VISIBLE : INVISIBLE;
        if (getVisibility() != visibility) {
            setVisibility(visibility);
        }
        boolean alphaChanged = Float.compare(getAlpha(), alpha) != 0;
        if (alphaChanged) {
            setAlpha(alpha);
        }
        if (scaleChanged && !alphaChanged && visibility == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    public void clearSnapshot() {
        mSnapshotType = SNAPSHOT_NONE;
        mBlurRadius = 0;
        mContentScale = 1f;
        mWallpaperOffset = 0.5f;
        setAlpha(0f);
        setVisibility(INVISIBLE);
        recycleSnapshot();
    }

    public void hideSnapshot() {
        mContentScale = 1f;
        if (getAlpha() != 0f) {
            setAlpha(0f);
        }
        if (getVisibility() != INVISIBLE) {
            setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleSnapshot();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if ((oldw > 0 || oldh > 0) && (w != oldw || h != oldh)) {
            clearSnapshot();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap snapshot = mSnapshot;
        if (snapshot == null || snapshot.isRecycled()) {
            return;
        }
        if (!canvas.isHardwareAccelerated() && snapshot.getConfig() == Bitmap.Config.HARDWARE) {
            return;
        }
        float insetX = snapshot.getWidth() * (1f - 1f / mContentScale) / 2f;
        float insetY = snapshot.getHeight() * (1f - 1f / mContentScale) / 2f;
        mSrc.set(
                Math.round(insetX),
                Math.round(insetY),
                Math.round(snapshot.getWidth() - insetX),
                Math.round(snapshot.getHeight() - insetY));
        mDst.set(0f, 0f, getWidth(), getHeight());
        canvas.drawBitmap(snapshot, mSrc, mDst, mPaint);
    }

    private boolean capture(int snapshotType, int blurRadius, BitmapRenderer renderer) {
        int snapshotWidth = getSnapshotWidth();
        int snapshotHeight = getSnapshotHeight();
        if (snapshotWidth <= 0 || snapshotHeight <= 0) {
            return false;
        }
        RenderNode contentNode = new RenderNode("BlurredSnapshotContent");
        contentNode.setPosition(0, 0, snapshotWidth, snapshotHeight);
        Canvas canvas = contentNode.beginRecording(snapshotWidth, snapshotHeight);
        renderer.draw(canvas);
        contentNode.endRecording();
        RenderNode renderNode = applyBlur(contentNode, snapshotWidth, snapshotHeight, blurRadius);
        Bitmap snapshot = HardwareRenderer.createHardwareBitmap(
                renderNode, snapshotWidth, snapshotHeight);
        if (snapshot == null) {
            return false;
        }
        setSnapshot(snapshot, snapshotType);
        return true;
    }

    private void setSnapshot(Bitmap snapshot, int snapshotType) {
        recycleSnapshot();
        mSnapshot = snapshot;
        mSnapshotType = snapshotType;
        setVisibility(VISIBLE);
        postInvalidateOnAnimation();
    }

    private void recycleSnapshot() {
        if (mSnapshot != null) {
            mSnapshot.recycle();
            mSnapshot = null;
        }
    }

    private int getSnapshotWidth() {
        return Math.round(getWidth() * SNAPSHOT_SCALE);
    }

    private int getSnapshotHeight() {
        return Math.round(getHeight() * SNAPSHOT_SCALE);
    }

    private static RenderNode applyBlur(RenderNode contentNode, int width, int height,
            int blurRadius) {
        float snapshotBlurRadius = blurRadius * SNAPSHOT_SCALE;
        if (snapshotBlurRadius <= 0f) {
            return contentNode;
        }
        contentNode.setRenderEffect(RenderEffect.createBlurEffect(
                snapshotBlurRadius, snapshotBlurRadius, Shader.TileMode.CLAMP));
        RenderNode renderNode = new RenderNode("BlurredSnapshotView");
        renderNode.setPosition(0, 0, width, height);
        Canvas canvas = renderNode.beginRecording(width, height);
        canvas.drawRenderNode(contentNode);
        renderNode.endRecording();
        return renderNode;
    }

    private static int getOffsetStart(int viewSize, int contentSize, float offset) {
        int extraSize = contentSize - viewSize;
        return extraSize > 0 ? -Math.round(extraSize * offset) : (viewSize - contentSize) / 2;
    }

    private static float normalizeWallpaperOffset(float wallpaperOffset) {
        return Utilities.boundToRange(wallpaperOffset, 0f, 1f);
    }
}
