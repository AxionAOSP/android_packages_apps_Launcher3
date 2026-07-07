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
package com.android.quickstep.util;

import android.content.Context;

final class AxTaskViewSimulatorExt {
    private static final float EPSILON = 0.0001f;

    private final float mDensity;
    private final boolean mDesktop;
    private boolean mEnabled;
    private boolean mSplit;

    AxTaskViewSimulatorExt(Context context, boolean desktop) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mDesktop = desktop;
    }

    void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    void setSplit(boolean split) {
        mSplit = split;
    }

    float getRadius(
            float fallback,
            float fullscreenProgress,
            float recentsScale,
            float carouselScale) {
        if (!mEnabled || mDesktop || mSplit) {
            return fallback;
        }
        float parentScale = Math.abs(recentsScale * carouselScale);
        if (parentScale <= EPSILON) {
            return fallback;
        }
        return AxAnimationEngine.getHomeRadius(mDensity, fullscreenProgress)
                / parentScale;
    }
}
