/*
 * Copyright (C) 2025 The AxionAOSP Project
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
package com.android.launcher3.util

import android.os.Process
import com.android.internal.util.BoostHelper

class AxCpuBindController private constructor() {

    private var animationBoostType = 0
    private var bindStatus = STATUS_UNBIND
    private var animationBoost = ANIMATION_BOOST_OFF
    private val pid get() = Process.myPid()

    private fun bindBigCore() {
        if (bindStatus == STATUS_BIND_BIG_CORE) {
            return
        }
        bindStatus = STATUS_BIND_BIG_CORE
        BoostHelper.setThreadAffinity(pid, STATUS_BIND_BIG_CORE)
    }

    private fun unbind() {
        if (bindStatus == STATUS_UNBIND) {
            return
        }
        bindStatus = STATUS_UNBIND
        BoostHelper.setThreadAffinity(pid, STATUS_UNBIND)
    }

    private fun animationBoostOn(type: Int) {
        animationBoostType = animationBoostType or type
        if (animationBoost == ANIMATION_BOOST_ON) {
            return
        }
        bindBigCore()
        animationBoost = ANIMATION_BOOST_ON
        BoostHelper.animationBoost(pid, ANIMATION_BOOST_ON)
    }

    private fun animationBoostOff(type: Int) {
        animationBoostType = animationBoostType and type.inv()
        if (animationBoostType <= 0 && animationBoost != ANIMATION_BOOST_OFF) {
            unbind()
            animationBoost = ANIMATION_BOOST_OFF
            BoostHelper.animationBoost(pid, ANIMATION_BOOST_OFF)
        }
    }

    fun acquireAppOpenBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_APP_OPEN)
    fun releaseAppOpenBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_APP_OPEN)

    fun acquireAppCloseBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_APP_CLOSE)
    fun releaseAppCloseBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_APP_CLOSE)

    fun acquireHomeTransitionBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_HOME_TRANSITION)
    fun releaseHomeTransitionBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_HOME_TRANSITION)

    fun acquireDrawerScrollBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_DRAWER_SCROLL)
    fun releaseDrawerScrollBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_DRAWER_SCROLL)

    fun acquirePagedViewBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_PAGED_TRANSITION)
    fun releasePagedViewBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_PAGED_TRANSITION)

    fun acquireDragBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_DRAG)
    fun releaseDragBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_DRAG)

    fun acquireStateDragBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_STATE_DRAG)
    fun releaseStateDragBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_STATE_DRAG)

    fun acquireTaskDismissBoost() = animationBoostOn(REQUEST_ANIMATION_BOOST_TYPE_TASK_DISMISS)
    fun releaseTaskDismissBoost() = animationBoostOff(REQUEST_ANIMATION_BOOST_TYPE_TASK_DISMISS)

    companion object {
        private const val STATUS_BIND_BIG_CORE = 0
        private const val STATUS_BIND_SMALL_CORE = 1
        private const val STATUS_UNBIND = 2

        private const val ANIMATION_BOOST_ON = 0L
        private const val ANIMATION_BOOST_OFF = -1L

        const val REQUEST_ANIMATION_BOOST_TYPE_BASE = 1
        const val REQUEST_ANIMATION_BOOST_TYPE_APP_OPEN = 1
        const val REQUEST_ANIMATION_BOOST_TYPE_APP_CLOSE = 1 shl 1
        const val REQUEST_ANIMATION_BOOST_TYPE_HOME_TRANSITION = 1 shl 2
        const val REQUEST_ANIMATION_BOOST_TYPE_DRAWER_SCROLL = 1 shl 3
        const val REQUEST_ANIMATION_BOOST_TYPE_PAGED_TRANSITION = 1 shl 4
        const val REQUEST_ANIMATION_BOOST_TYPE_DRAG = 1 shl 5
        const val REQUEST_ANIMATION_BOOST_TYPE_STATE_DRAG = 1 shl 6
        const val REQUEST_ANIMATION_BOOST_TYPE_TASK_DISMISS = 1 shl 7

        @Volatile
        private var instance: AxCpuBindController? = null

        @JvmStatic
        fun get(): AxCpuBindController {
            return instance ?: synchronized(this) {
                instance ?: AxCpuBindController().also { instance = it }
            }
        }
    }
}
