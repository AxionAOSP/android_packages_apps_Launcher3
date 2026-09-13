/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.icons

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.axion.iconloader.AdaptiveIconHelper
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.UserIconInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Wrapper class to provide access to [BaseIconFactory] and also to provide pool of this class that
 * are threadsafe.
 */
class LauncherIcons
@AssistedInject
internal constructor(
    @ApplicationContext context: Context,
    idp: InvariantDeviceProfile,
    themeManager: ThemeManager,
    private var userCache: UserCache,
    @Assisted private val pool: ConcurrentLinkedQueue<LauncherIcons>,
) :
    BaseIconFactory(
        context,
        idp.fillResIconDpi,
        idp.iconBitmapSize,
        Flags.enableLauncherIconShapes() && !LauncherPrefsExt.isAdaptiveDisabled(context),
        themeManager.themeController,
    ),
    AutoCloseable {

    override fun createBadgedIconBitmap(icon: Drawable?, options: IconOptions): BitmapInfo {
        if (AdaptiveIconHelper.isAdaptiveDisabled(context)) {
            options.setWrapNonAdaptiveIcon(false)
            options.setDrawFullBleed(false)
            if (icon is AdaptiveIconDrawable && AdaptiveIconHelper.canUnwrapAdaptiveIcon(icon)) {
                val directIcon = AdaptiveIconHelper.wrapToDirectIcon(icon)
                return super.createBadgedIconBitmap(directIcon, options)
            }
        }
        return super.createBadgedIconBitmap(icon, options)
    }

    /** Recycles a LauncherIcons that may be in-use. */
    fun recycle() {
        clear()
        pool.add(this)
    }

    override fun getUserInfo(user: UserHandle): UserIconInfo {
        return userCache.getUserInfo(user)
    }

    override fun close() {
        recycle()
    }

    @AssistedFactory
    internal interface LauncherIconsFactory {
        fun create(pool: ConcurrentLinkedQueue<LauncherIcons>): LauncherIcons
    }

    @LauncherAppSingleton
    class IconPool @Inject internal constructor(private val factory: LauncherIconsFactory) {
        private var pool = ConcurrentLinkedQueue<LauncherIcons>()

        fun obtain(): LauncherIcons = pool.let { it.poll() ?: factory.create(it) }

        fun clear() {
            pool = ConcurrentLinkedQueue()
        }
    }

    companion object {

        /**
         * Return a new LauncherIcons instance from the global pool. Allows us to avoid allocating
         * new objects in many cases.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons = context.appComponent.iconPool.obtain()

        @JvmStatic fun clearPool(context: Context) = context.appComponent.iconPool.clear()
    }
}
