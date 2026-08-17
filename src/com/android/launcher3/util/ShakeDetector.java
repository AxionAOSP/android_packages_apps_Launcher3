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
package com.android.launcher3.util;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {

    private static final float SHAKE_THRESHOLD_G = 2.0f;
    private static final int SHAKE_SLOP_TIME_MS = 2500;

    private final SensorManager mSensorManager;
    private final Runnable mOnShakeListener;
    private Sensor mAccelerometer;
    private long mLastShakeTime = 0;
    private boolean mIsRunning = false;

    private int mShakeCount = 0;
    private long mFirstShakeTimestamp = 0;
    private long mLastShakeTimestamp = 0;

    public ShakeDetector(Context context, Runnable onShakeListener) {
        Object service = context.getSystemService(Context.SENSOR_SERVICE);
        if (!(service instanceof SensorManager)) {
            service = context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        }
        mSensorManager = service instanceof SensorManager ? (SensorManager) service : null;
        mOnShakeListener = onShakeListener;
        if (mSensorManager != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public void start() {
        if (mIsRunning || mSensorManager == null || mAccelerometer == null) {
            return;
        }
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        mIsRunning = true;
        mShakeCount = 0;
        mFirstShakeTimestamp = 0;
        mLastShakeTimestamp = 0;
    }

    public void stop() {
        if (!mIsRunning || mSensorManager == null) {
            return;
        }
        mSensorManager.unregisterListener(this);
        mIsRunning = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float gForce = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
        if (gForce > SHAKE_THRESHOLD_G) {
            long now = System.currentTimeMillis();
            
            if (mFirstShakeTimestamp == 0 || now - mFirstShakeTimestamp > 1000) {
                mFirstShakeTimestamp = now;
                mLastShakeTimestamp = now;
                mShakeCount = 1;
            } else if (now - mLastShakeTimestamp > 150) {
                mShakeCount++;
                mLastShakeTimestamp = now;
            }

            if (mShakeCount >= 3) {
                if (now - mLastShakeTime > SHAKE_SLOP_TIME_MS) {
                    mLastShakeTime = now;
                    mShakeCount = 0;
                    mFirstShakeTimestamp = 0;
                    if (mOnShakeListener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(mOnShakeListener);
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
