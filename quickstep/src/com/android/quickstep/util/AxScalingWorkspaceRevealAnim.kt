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

package com.android.quickstep.util

import android.animation.AnimatorSet
import android.graphics.RectF
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import com.android.app.animation.Animations
import com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_WORKSPACE_STATE
import com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA
import com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY
import com.android.launcher3.LauncherState
import com.android.launcher3.anim.AlphaUpdateListener
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.anim.PropertySetter
import com.android.launcher3.states.StateAnimationConfig
import com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER
import com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW
import com.android.launcher3.states.StateAnimationConfig.SKIP_SCRIM
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.quickstep.views.RecentsView

class AxScalingWorkspaceRevealAnim(
    private val launcher: QuickstepLauncher,
    siblingAnimation: RectFSpringAnim?,
    windowTargetRect: RectF?,
    targetView: View?,
) {
    companion object {
        private const val LOG_TAG = "AxScalingWorkspaceRevealAnim"
        private const val MAX_ALPHA = 1f
        private const val MIN_ALPHA = 0f
        private const val MAX_SIZE = 1f

        @JvmStatic
        fun start(
            launcher: QuickstepLauncher,
            siblingAnimation: RectFSpringAnim?,
            windowTargetRect: RectF?,
            targetView: View?,
            useAxAnim: Boolean,
        ) {
            AxAnimationEngine.trace(LOG_TAG, "start useAx=$useAxAnim")
            if (useAxAnim) {
                AxScalingWorkspaceRevealAnim(
                    launcher,
                    siblingAnimation,
                    windowTargetRect,
                    targetView,
                )
                    .start()
            } else {
                ScalingWorkspaceRevealAnim(
                    launcher,
                    siblingAnimation,
                    windowTargetRect,
                )
                    .start()
            }
        }
    }

    private val animation = PendingAnimation(AxAnimationEngine.HOME_GESTURE_WORKSPACE_DURATION)

    init {
        val setupConfig = StateAnimationConfig()
        setupConfig.animFlags = SKIP_OVERVIEW.or(SKIP_DEPTH_CONTROLLER).or(SKIP_SCRIM)
        setupConfig.duration = 0
        launcher.stateManager
            .createAtomicAnimation(LauncherState.BACKGROUND_APP, LauncherState.NORMAL, setupConfig)
            .start()
        launcher
            .getOverviewPanel<RecentsView<QuickstepLauncher, LauncherState>>()
            .forceFinishScroller()
        launcher.workspace.stateTransitionAnimation.setScrim(
            PropertySetter.NO_ANIM_PROPERTY_SETTER,
            LauncherState.BACKGROUND_APP,
            setupConfig,
        )
        val workspace = launcher.workspace
        val hotseat = launcher.hotseat
        workspace.visibility = View.VISIBLE
        hotseat.visibility = View.VISIBLE

        val workspaceStartScale =
            if (workspace.scaleX != MAX_SIZE) {
                workspace.scaleX
            } else {
                AxAnimationEngine.HOME_GESTURE_WORKSPACE_SCALE
            }
        val hotseatStartScale =
            if (hotseat.scaleX != MAX_SIZE) {
                hotseat.scaleX
            } else {
                AxAnimationEngine.HOME_GESTURE_WORKSPACE_SCALE
            }

        Animations.cancelOngoingAnimation(workspace)
        Animations.cancelOngoingAnimation(hotseat)

        workspace.setPivotToScaleWithSelf(hotseat)
        WORKSPACE_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE].setValue(
            workspace,
            workspaceStartScale,
        )
        HOTSEAT_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE].setValue(
            hotseat,
            hotseatStartScale,
        )
        animation.addFloat(
            workspace,
            WORKSPACE_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            workspaceStartScale,
            MAX_SIZE,
            AxAnimationEngine.HOME_GESTURE_WORKSPACE_INTERPOLATOR,
        )
        animation.addFloat(
            hotseat,
            HOTSEAT_SCALE_PROPERTY_FACTORY[SCALE_INDEX_WORKSPACE_STATE],
            hotseatStartScale,
            MAX_SIZE,
            AxAnimationEngine.HOME_GESTURE_WORKSPACE_INTERPOLATOR,
        )

        val transitionConfig = StateAnimationConfig()
        transitionConfig.duration = AxAnimationEngine.HOME_GESTURE_WORKSPACE_DURATION
        transitionConfig.setInterpolator(
            StateAnimationConfig.ANIM_DEPTH,
            AxAnimationEngine.HOME_GESTURE_WORKSPACE_INTERPOLATOR,
        )
        launcher.depthController.stateDepth.value =
            LauncherState.BACKGROUND_APP.getDepth(launcher)
        launcher.depthController.setStateWithAnimation(
            LauncherState.NORMAL,
            transitionConfig,
            animation,
        )

        workspace.alpha = MIN_ALPHA
        animation.setFloat(
            workspace,
            VIEW_ALPHA,
            MAX_ALPHA,
            AxAnimationEngine.HOME_GESTURE_WORKSPACE_INTERPOLATOR,
        )
        hotseat.alpha = MIN_ALPHA
        animation.setViewAlpha(
            hotseat,
            MAX_ALPHA,
            AxAnimationEngine.HOME_GESTURE_WORKSPACE_INTERPOLATOR,
        )

        val targetHelper =
            AxHomeGestureTargetHelper(siblingAnimation, windowTargetRect, launcher, targetView)
        targetHelper.update()

        workspace.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        hotseat.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        animation.addListener(
            AnimatorListeners.forEndCallback(
                Runnable {
                    workspace.alpha = MAX_ALPHA
                    hotseat.alpha = MAX_ALPHA
                    AlphaUpdateListener.updateVisibility(workspace)
                    AlphaUpdateListener.updateVisibility(hotseat)
                    if (!hotseat.isVisible || !workspace.isVisible) {
                        Log.e(
                            LOG_TAG,
                            "Unexpected invisibility after animation end:" +
                                " workspace.isVisible=${workspace.isVisible}" +
                                ", workspace.alpha=${workspace.alpha}" +
                                ", hotseat.isVisible=${hotseat.isVisible}" +
                                ", hotseat.alpha=${hotseat.alpha}",
                            Exception(),
                        )
                    }

                    workspace.setLayerType(View.LAYER_TYPE_NONE, null)
                    hotseat.setLayerType(View.LAYER_TYPE_NONE, null)

                    Animations.setOngoingAnimation(workspace, animation = null)
                    Animations.setOngoingAnimation(hotseat, animation = null)
                }
            )
        )
    }

    fun getAnimators(): AnimatorSet {
        return animation.buildAnim()
    }

    private fun start() {
        val animators = getAnimators()
        Animations.setOngoingAnimation(launcher.workspace, animators)
        Animations.setOngoingAnimation(launcher.hotseat, animators)
        launcher.stateManager.setCurrentAnimation(animators, LauncherState.NORMAL)
        animators.start()
    }
}
