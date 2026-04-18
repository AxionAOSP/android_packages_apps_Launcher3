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
package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.util.Themes;

/**
 * Utility class to render selection indicators (circles) on icons.
 */
public class SelectionIndicatorRenderer {

    private final Paint mEmptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFilledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mSize;
    private final int mMargin;

    public SelectionIndicatorRenderer(Context context) {
        mEmptyPaint.setStyle(Paint.Style.STROKE);
        mEmptyPaint.setColor(Color.WHITE);
        mEmptyPaint.setStrokeWidth(4f);

        mFilledPaint.setStyle(Paint.Style.FILL);
        int accentColor = Themes.getColorAccent(context);
        if (accentColor == 0 || accentColor == Color.WHITE) {
            accentColor = Color.parseColor("#4285F4"); // Google Blue fallback
        }
        mFilledPaint.setColor(accentColor);

        DeviceProfile dp = Launcher.getLauncher(context).getDeviceProfile();
        mSize = (int) (dp.getWorkspaceIconProfile().getIconSizePx() * 0.35f);
        mMargin = mSize / 4;
    }

    public void draw(Canvas canvas, Rect iconBounds, boolean isSelected) {
        int cx = iconBounds.right - mMargin - mSize / 2;
        int cy = iconBounds.top + mMargin + mSize / 2;
        float radius = mSize / 2f;

        if (isSelected) {
            canvas.drawCircle(cx, cy, radius, mFilledPaint);
            Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dotPaint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, radius * 0.4f, dotPaint);
        } else {
            canvas.drawCircle(cx, cy, radius, mEmptyPaint);
        }
    }
}
