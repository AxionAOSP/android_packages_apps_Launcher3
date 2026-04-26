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
package com.android.quickstep.util;

import android.app.ActivityManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import java.util.concurrent.ConcurrentHashMap;

public final class TransitionSmoothHelper {
    private static final String TAG = "TransitionSmoothHelper";
    public static final boolean SMOOTH_FEATURE = true;

    private static final ConcurrentHashMap<Integer, TransitionData> sCache =
            new ConcurrentHashMap<>();
    private static volatile float sAllAppsContentAlpha = 0f;
    private static volatile long sDuration = 0L;
    private static volatile boolean sInRecents = false;

    private TransitionSmoothHelper() {}

    public static final class TransitionData {
        private float mCornerRadius = 1.0f;
        private Matrix mMatrix;
        private Rect mWindowCrop;

        public float getCornerRadius() { return mCornerRadius; }
        public Matrix getMatrix() { return mMatrix; }
        public Rect getWindowCrop() { return mWindowCrop; }

        public void setCornerRadius(float r) { mCornerRadius = r; }
        public void setMatrix(Matrix m) { mMatrix = m; }
        public void setWindowCrop(Rect rect) { mWindowCrop = rect; }
    }

    public static void putData(int taskId, Matrix matrix, Rect windowCrop, float cornerRadius) {
        if (!SMOOTH_FEATURE || matrix == null || windowCrop == null) return;
        TransitionData data = new TransitionData();
        float[] values = new float[9];
        matrix.getValues(values);
        Matrix copy = new Matrix();
        copy.setValues(values);
        data.setMatrix(copy);
        data.setWindowCrop(new Rect(windowCrop));
        data.setCornerRadius(cornerRadius);
        sCache.put(taskId, data);
    }

    public static void removeData(int taskId) {
        sCache.remove(taskId);
    }

    public static TransitionData getData(int taskId) {
        return sCache.get(taskId);
    }

    public static boolean hasData(int taskId) {
        return sCache.containsKey(taskId);
    }

    public static boolean hasData() {
        return !sCache.isEmpty();
    }

    public static void clear() {
        if (!SMOOTH_FEATURE) return;
        sCache.clear();
        sDuration = 0L;
        sAllAppsContentAlpha = 0f;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "clear");
        }
    }

    public static void setInRecents(boolean inRecents) {
        sInRecents = inRecents;
    }

    public static boolean inRecents() {
        return sInRecents;
    }

    public static long getDuration() {
        return sDuration;
    }

    public static void updateDuration(long durationMs) {
        sDuration = durationMs / 2;
    }

    public static float getAllAppsContentAlpha() {
        return sAllAppsContentAlpha;
    }

    public static void updateAllAppsContentAlpha(float alpha) {
        if (!SMOOTH_FEATURE) return;
        sAllAppsContentAlpha = alpha;
    }

    public static void buildStartTransaction(TransitionInfo transitionInfo,
            SurfaceControl.Transaction t, RemoteAnimationTarget[] appTargets) {
        if (t == null || appTargets == null) return;
        if (!hasData() || !isOpenHome(transitionInfo)) return;
        if (transitionInfo != null) {
            int size = transitionInfo.getChanges().size();
            for (int i = 0; i < size; i++) {
                TransitionInfo.Change change = transitionInfo.getChanges().get(i);
                ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                if (taskInfo == null) continue;
                if (taskInfo.parentTaskId != -1) continue;
                if (!sCache.containsKey(taskInfo.taskId)) continue;
                if (taskInfo.topActivityType == 2) continue;
                int mode = change.getMode();
                if (mode != 2 && mode != 4) continue;
                t.setCornerRadius(change.getLeash(), 0.0f);
                t.setMatrix(change.getLeash(), 1.0f, 0.0f, 0.0f, 1.0f);
                t.setAlpha(change.getLeash(), 1.0f);
                t.setCrop(change.getLeash(), null);
            }
        }
        applyCachedToTargets(t, appTargets);
    }

    public static void buildStartTransaction(SurfaceControl.Transaction t,
            RemoteAnimationTarget[] appTargets) {
        if (t == null || appTargets == null) return;
        if (!SMOOTH_FEATURE) return;
        applyCachedToTargets(t, appTargets);
    }

    private static void applyCachedToTargets(SurfaceControl.Transaction t,
            RemoteAnimationTarget[] appTargets) {
        for (RemoteAnimationTarget target : appTargets) {
            ActivityManager.RunningTaskInfo info = target.taskInfo;
            if (info == null) continue;
            if (info.topActivityType == 2) continue;
            if (target.mode != 1) continue;
            TransitionData data = getData(info.taskId);
            if (data == null) continue;
            if (data.getMatrix() != null) {
                t.setMatrix(target.leash, data.getMatrix(), new float[9]);
            }
            Rect crop = data.getWindowCrop();
            if (crop != null) {
                t.setWindowCrop(target.leash, crop);
            }
            t.setAlpha(target.leash, 1.0f);
            t.setCornerRadius(target.leash, data.getCornerRadius());
        }
    }

    public static void buildFinishTransaction(TransitionInfo transitionInfo,
            SurfaceControl.Transaction t) {
        if (t == null || transitionInfo == null) return;
        if (transitionInfo.getChanges().size() <= 0 || !hasData()) return;
        int size = transitionInfo.getChanges().size();
        for (int i = 0; i < size; i++) {
            TransitionInfo.Change change = transitionInfo.getChanges().get(i);
            ActivityManager.RunningTaskInfo info = change.getTaskInfo();
            if (info == null) continue;
            TransitionData data = getData(info.taskId);
            if (data != null) {
                Matrix m = data.getMatrix();
                if (m != null) {
                    t.setMatrix(change.getLeash(), m, new float[9]);
                }
                Rect crop = data.getWindowCrop();
                if (crop != null) {
                    t.setWindowCrop(change.getLeash(), crop);
                }
                t.setAlpha(change.getLeash(), 1.0f);
                t.setCornerRadius(change.getLeash(), data.getCornerRadius());
            } else if (info.getActivityType() == 2) {
                t.show(change.getLeash());
            }
        }
    }

    private static boolean isOpenHome(TransitionInfo transitionInfo) {
        if (transitionInfo == null) return false;
        int size = transitionInfo.getChanges().size();
        for (int i = 0; i < size; i++) {
            TransitionInfo.Change change = transitionInfo.getChanges().get(i);
            ActivityManager.RunningTaskInfo info = change.getTaskInfo();
            if (info == null) continue;
            if (info.topActivityType == 2 && (change.getMode() == 1 || change.getMode() == 3)) {
                return true;
            }
        }
        return false;
    }
}
