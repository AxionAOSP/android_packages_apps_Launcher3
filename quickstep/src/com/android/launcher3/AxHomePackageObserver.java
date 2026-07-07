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

import static com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI;

import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.concurrent.annotations.LightweightBackground;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.SafeCloseable;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;

import java.util.concurrent.Executor;

import javax.inject.Inject;

@LauncherAppSingleton
public final class AxHomePackageObserver implements SafeCloseable {
    private static final String TAG = "AxHomePackageObserver";

    public static final DaggerSingletonObject<AxHomePackageObserver> INSTANCE =
            new DaggerSingletonObject<>(QuickstepBaseAppComponent::getAxHomePackageObserver);

    private final RoleManager mRoleManager;
    private final PackageManager mPackageManager;
    private final Intent mHomeIntent =
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    private final UserHandle mUser = Process.myUserHandle();
    private final Executor mExecutor;
    private final OnRoleHoldersChangedListener mListener = this::onRoleChanged;

    private volatile String mHomePackageName;
    private boolean mRegistered;

    @Inject
    AxHomePackageObserver(
            @ApplicationContext Context context,
            DaggerSingletonTracker tracker,
            @LightweightBackground(priority = UI) Executor executor) {
        mRoleManager = context.getSystemService(RoleManager.class);
        mPackageManager = context.getPackageManager();
        mExecutor = executor;
        updateHomePackageName();
        mExecutor.execute(this::register);
        tracker.addCloseable(this);
    }

    public boolean isDefaultHome(String packageName) {
        return packageName != null && packageName.equals(mHomePackageName);
    }

    @Override
    public void close() {
        mExecutor.execute(this::unregister);
    }

    private void register() {
        if (mRoleManager == null || mRegistered) {
            return;
        }
        try {
            mRoleManager.addOnRoleHoldersChangedListenerAsUser(mExecutor, mListener, mUser);
            mRegistered = true;
        } catch (SecurityException e) {
            Log.w(TAG, "Unable to observe default home changes", e);
        }
    }

    private void unregister() {
        if (mRoleManager == null || !mRegistered) {
            return;
        }
        mRoleManager.removeOnRoleHoldersChangedListenerAsUser(mListener, mUser);
        mRegistered = false;
    }

    private void onRoleChanged(String roleName, UserHandle user) {
        if (RoleManager.ROLE_HOME.equals(roleName) && mUser.equals(user)) {
            updateHomePackageName();
        }
    }

    private void updateHomePackageName() {
        ResolveInfo home =
                mPackageManager.resolveActivity(mHomeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        mHomePackageName =
                home == null || home.activityInfo == null ? null : home.activityInfo.packageName;
    }
}
