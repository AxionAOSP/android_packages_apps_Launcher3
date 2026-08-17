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

import com.android.launcher3.util.Themes;

public class SelectionIndicatorRenderer {

    private final Paint mEmptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFilledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInnerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SelectionIndicatorRenderer(Context context) {
        mEmptyPaint.setStyle(Paint.Style.STROKE);
        mEmptyPaint.setColor(Color.WHITE);
        mEmptyPaint.setStrokeWidth(3f);

        mShadowPaint.setStyle(Paint.Style.FILL);
        mShadowPaint.setColor(Color.parseColor("#80000000"));

        mFilledPaint.setStyle(Paint.Style.FILL);
        int accentColor = Themes.getColorAccent(context);
        if (accentColor == 0 || accentColor == Color.WHITE) {
            accentColor = Color.parseColor("#4285F4");
        }
        mFilledPaint.setColor(accentColor);

        mInnerDotPaint.setStyle(Paint.Style.FILL);
        mInnerDotPaint.setColor(Color.WHITE);
    }

    public void draw(Canvas canvas, Rect iconBounds, boolean isSelected, int iconSize) {
        int size = (int) (iconSize * 0.35f);
        int margin = size / 4;

        int cx = iconBounds.right - margin - size / 2;
        int cy = iconBounds.top + margin + size / 2;
        float radius = size / 2f;

        canvas.drawCircle(cx, cy, radius, mShadowPaint);

        if (isSelected) {
            canvas.drawCircle(cx, cy, radius - 1f, mFilledPaint);
            canvas.drawCircle(cx, cy, radius * 0.45f, mInnerDotPaint);
        } else {
            canvas.drawCircle(cx, cy, radius, mEmptyPaint);
        }
    }
}
