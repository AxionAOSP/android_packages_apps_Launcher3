/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.lineage.trust.db;

import android.app.AppLockManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TrustDatabaseHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "trust_apps_db";

    private static final String TABLE_NAME = "trust_apps";
    private static final String KEY_UID = "uid";
    private static final String KEY_PKGNAME = "pkgname";
    private static final String KEY_HIDDEN = "hidden";
    private static final String KEY_PROTECTED = "protected";

    @Nullable
    private static TrustDatabaseHelper sSingleton;
    
    private final AppLockManager mAppLockManager;

    private TrustDatabaseHelper(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        mAppLockManager = context.getSystemService(AppLockManager.class);
    }

    public static synchronized TrustDatabaseHelper getInstance(@NonNull Context context) {
        if (sSingleton == null) {
            sSingleton = new TrustDatabaseHelper(context);
        }

        return sSingleton;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {}

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void addHiddenApp(@NonNull String packageName) {
        if (isPackageHidden(packageName)) {
            return;
        }
        mAppLockManager.setPackageHidden(packageName, true);
    }

    public void addProtectedApp(@NonNull String packageName) {
        if (isPackageProtected(packageName)) {
            return;
        }
        mAppLockManager.setShouldProtectApp(packageName, true);
    }


    public void removeHiddenApp(@NonNull String packageName) {
        if (!isPackageHidden(packageName)) {
            return;
        }
        mAppLockManager.setPackageHidden(packageName, false);
    }

    public void removeProtectedApp(@NonNull String packageName) {
        if (!isPackageProtected(packageName)) {
            return;
        }
        mAppLockManager.setShouldProtectApp(packageName, false);
    }

    public boolean isPackageHidden(@NonNull String packageName) {
        return mAppLockManager.isPackageHidden(packageName);
    }

    public boolean isPackageProtected(@NonNull String packageName) {
        return mAppLockManager.isPackageProtected(packageName);
    }
}
