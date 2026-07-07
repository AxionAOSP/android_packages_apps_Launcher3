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
package com.android.launcher3;

import android.view.RemoteAnimationTarget;

import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.SurfaceTransactionApplier;

final class AxRemoteTargetUpdater {
    private final SurfaceTransactionApplier mSurfaceApplier;

    AxRemoteTargetUpdater(SurfaceTransactionApplier surfaceApplier) {
        mSurfaceApplier = surfaceApplier;
    }

    void apply(AxMultiTargetsUpdateInfo updateInfo) {
        SurfaceTransaction transaction = buildTransaction(updateInfo);
        if (transaction != null) {
            mSurfaceApplier.scheduleApply(transaction);
        }
    }

    private SurfaceTransaction buildTransaction(AxMultiTargetsUpdateInfo updateInfo) {
        if (updateInfo.size() == 0) {
            return null;
        }
        SurfaceTransaction transaction = new SurfaceTransaction();
        boolean hasUpdates = false;
        for (int i = 0; i < updateInfo.size(); i++) {
            hasUpdates |= apply(transaction, updateInfo.get(i));
        }
        if (hasUpdates) {
            return transaction;
        }
        transaction.getTransaction().close();
        return null;
    }

    private boolean apply(SurfaceTransaction transaction, AxUpdateInfo updateInfo) {
        RemoteAnimationTarget target = updateInfo.getTarget();
        if (target == null || target.leash == null || !target.leash.isValid()) {
            return false;
        }
        SurfaceProperties builder = transaction.forSurface(target.leash);
        if (updateInfo.hasMatrix()) {
            builder.setMatrix(updateInfo.getMatrix());
        }
        if (updateInfo.hasCrop()) {
            builder.setWindowCrop(updateInfo.getCrop());
        }
        builder.setAlpha(updateInfo.getAlpha()).setShow();
        if (updateInfo.getCornerRadius() >= 0f) {
            builder.setCornerRadius(updateInfo.getCornerRadius());
        }
        if (updateInfo.getShadowRadius() >= 0f) {
            builder.setShadowRadius(updateInfo.getShadowRadius());
        }
        return true;
    }
}
