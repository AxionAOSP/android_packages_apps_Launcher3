/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.FloatProperty
import android.util.Property
import android.view.View
import android.view.animation.PathInterpolator
import androidx.dynamicanimation.animation.DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA
import androidx.dynamicanimation.animation.DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS
import androidx.dynamicanimation.animation.DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
import com.android.launcher3.BubbleTextView
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY
import com.android.launcher3.R
import com.android.launcher3.Utilities.isDarkTheme
import com.android.launcher3.anim.SpringAnimationBuilder
import com.android.launcher3.apppairs.AppPairIcon
import com.android.launcher3.util.Themes

/** Holder for Animators created from [FolderAnimationSpringBuilderManager] */
class FolderSpringAnimatorSet(val animatorSet: AnimatorSet) {

    fun start() {
        animatorSet.start()
    }

    companion object Factory {
        private const val LAUNCHER_SCALE = 0.975f
        private const val FOLDER_NAME_ALPHA_DURATION = 32
        private const val LARGE_FOLDER_FOOTER_DURATION = 128
        private const val ITEM_ALPHA_START_DELAY = 10L
        private const val ITEM_ALPHA_DURATION = 150L
        private const val STIFFNESS_SHAPE_POSITION = 380f
        private const val DAMPING_SHAPE_POSITION = 0.8f
        private const val STIFFNESS_ALPHA = 1600f
        private const val DAMPING_ALPHA = 0.9f
        private const val STIFFNESS_LAUNCHER_SCRIM = 380f
        private const val DAMPING_LAUNCHER_SCRIM = 0.98f
        private val ITEM_ALPHA_INTERPOLATOR = PathInterpolator(0.4f, 0f, 0.6f, 1f)

        /**
         * Factory method to take data calculated from [FolderAnimationSpringBuilderManager], and
         * create a [FolderSpringAnimatorSet] with [SpringAnimationBuilder].
         */
        fun build(
            folder: Folder,
            launcherDelegate: LauncherDelegate,
            folderAnimData: FolderAnimationData,
            clipRevealData: ClipRevealData,
            iconAnimData: List<IconAnimationData>,
        ): FolderSpringAnimatorSet {
            val animatorSet = AnimatorSet()
            setupFolder(folder, folderAnimData)
            addFolderScaleAndTranslateAnimators(folder, animatorSet, folderAnimData)
            addClipRevealAnimators(folder, animatorSet, clipRevealData)
            addAlphaAndColorAnimators(folder, animatorSet, folderAnimData)
            addScrimAnimators(
                folder.context,
                animatorSet,
                folderAnimData.isOpening,
                launcherDelegate,
            )
            iconAnimData.forEach { addContentIconAnimators(folder.context, animatorSet, it) }
            return FolderSpringAnimatorSet(animatorSet)
        }

        private fun playSpringAnimation(
            context: Context,
            animatorSet: AnimatorSet,
            isOpening: Boolean,
            startDelay: Int,
            stiffness: Float,
            damping: Float,
            startValue: Float,
            endValue: Float,
            minVisibleChange: Float,
            property: Property<View, Float>,
            view: View,
        ) {
            val animatorBuilder =
                SpringAnimationBuilder(context)
                    .setStiffness(stiffness)
                    .setDampingRatio(damping)
                    .setStartValue(if (isOpening) startValue else endValue)
                    .setEndValue(if (isOpening) endValue else startValue)
                    .setMinimumVisibleChange(minVisibleChange)

            val animator =
                animatorBuilder.build(view, property as FloatProperty<View>).apply {
                    setStartDelay(startDelay.toLong())
                }
            animatorSet.play(animator)
        }

        private fun setupFolder(folder: Folder, folderAnimationData: FolderAnimationData) {
            folder.folderIcon.previewItemManager.recomputePreviewDrawingParams()
            folder.apply {
                alpha = 1f
                pivotX = 0f
                pivotY = 0f
            }
            folder.content.apply {
                scaleX = folderAnimationData.startScale
                scaleY = folderAnimationData.startScale
                pivotX = 0f
                pivotY = 0f
            }
            folder.mFooter.apply {
                scaleX = folderAnimationData.startScale
                scaleY = folderAnimationData.startScale
                pivotX = 0f
                pivotY = 0f
            }
        }

        private fun addFolderScaleAndTranslateAnimators(
            folder: Folder,
            animatorSet: AnimatorSet,
            animationData: FolderAnimationData,
        ) {
            val isOpening = animationData.isOpening
            playSpringAnimation(
                context = folder.context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_SHAPE_POSITION,
                damping = DAMPING_SHAPE_POSITION,
                startValue = animationData.xDistance,
                endValue = 0f,
                minVisibleChange = MIN_VISIBLE_CHANGE_PIXELS,
                property = View.TRANSLATION_X,
                view = folder,
            )
            playSpringAnimation(
                context = folder.context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_SHAPE_POSITION,
                damping = DAMPING_SHAPE_POSITION,
                startValue = animationData.yDistance,
                endValue = 0f,
                minVisibleChange = MIN_VISIBLE_CHANGE_PIXELS,
                property = View.TRANSLATION_Y,
                view = folder,
            )
            playSpringAnimation(
                context = folder.context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_SHAPE_POSITION,
                damping = DAMPING_SHAPE_POSITION,
                startValue = animationData.initialFolderScale,
                endValue = 1f,
                minVisibleChange = MIN_VISIBLE_CHANGE_SCALE,
                property = SCALE_PROPERTY,
                view = folder.content,
            )
            playSpringAnimation(
                context = folder.context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_SHAPE_POSITION,
                damping = DAMPING_SHAPE_POSITION,
                startValue = animationData.initialFolderScale,
                endValue = 1f,
                minVisibleChange = MIN_VISIBLE_CHANGE_SCALE,
                property = SCALE_PROPERTY,
                view = folder.mFooter,
            )
            // Translate the footer so that it tracks the bottom of the content.
            playSpringAnimation(
                context = folder.context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_SHAPE_POSITION,
                damping = DAMPING_SHAPE_POSITION,
                startValue = -(animationData.contentHeightDifference),
                endValue = 0f,
                minVisibleChange = MIN_VISIBLE_CHANGE_PIXELS,
                property = View.TRANSLATION_Y,
                view = folder.mFooter,
            )

            // Animate the elevation midway so that the shadow is not noticeable in the background.
            val midDuration = animationData.defaultDuration / 2
            playSpringAnimation(
                context = folder.context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = if (animationData.isOpening) midDuration else 0,
                stiffness = STIFFNESS_SHAPE_POSITION,
                damping = DAMPING_SHAPE_POSITION,
                startValue = -folder.elevation,
                endValue = 0f,
                minVisibleChange = MIN_VISIBLE_CHANGE_PIXELS,
                property = View.TRANSLATION_Z,
                view = folder,
            )
            // store clip variables
            animatorSet.addListener(
                FolderOpenCloseAnimationListener(folder, animationData.isOpening)
            )
        }

        private fun addAlphaAndColorAnimators(
            folder: Folder,
            animatorSet: AnimatorSet,
            animationData: FolderAnimationData,
        ) {
            with(folder) {
                val folderBackground = folder.background as GradientDrawable
                // Set up the Folder background.
                val isOpening = animationData.isOpening
                val initialColor = Themes.getAttrColor(context, R.attr.folderPreviewColor)
                val finalColor = Themes.getAttrColor(context, R.attr.folderBackgroundColor)
                folderBackground.mutate()
                folderBackground.setColor(if (isOpening) initialColor else finalColor)
                // TODO: convert to spring animation?
                animatorSet.play(
                    ObjectAnimator.ofArgb(
                            folderBackground,
                            "color",
                            if (isOpening) initialColor else finalColor,
                            if (isOpening) finalColor else initialColor,
                        )
                        .apply { duration = animationData.defaultDuration.toLong() }
                )

                var footerStartDelay = 0
                val isLargeFolder = folder.itemCount > maxOf(folder.folderIcon.maxPreviewItems, 1)
                if (isLargeFolder) {
                    folder.mFooter.alpha = 0f
                    if (isOpening) {
                        footerStartDelay =
                            animationData.defaultDuration - LARGE_FOLDER_FOOTER_DURATION
                    }
                }

                if (!isLargeFolder || isOpening) {
                    playSpringAnimation(
                        context = folder.context,
                        animatorSet = animatorSet,
                        isOpening = isOpening,
                        startDelay = if (animationData.isOpening) footerStartDelay else 0,
                        stiffness = STIFFNESS_ALPHA,
                        damping = DAMPING_ALPHA,
                        startValue = 0f,
                        endValue = 1f,
                        minVisibleChange = MIN_VISIBLE_CHANGE_ALPHA,
                        property = View.ALPHA,
                        view = mFooter,
                    )
                }
                // Fade in the folder name, as the text can overlap the icons when grid size is
                // small.
                folder.folderName.alpha = 0f
                if (isOpening) {
                    animatorSet.play(
                        ObjectAnimator.ofFloat(folderName, View.ALPHA, 0f, 1f).apply {
                            startDelay =
                                maxOf(0, animationData.defaultDuration - FOLDER_NAME_ALPHA_DURATION)
                                    .toLong()
                            duration = FOLDER_NAME_ALPHA_DURATION.toLong()
                        }
                    )
                }
            }
        }

        private fun addClipRevealAnimators(
            folder: Folder,
            animatorSet: AnimatorSet,
            revealData: ClipRevealData,
        ) {
            with(revealData) {
                // Create reveal animator for the folder background
                animatorSet.play(
                    shapeDelegate.createRevealAnimator(
                        folder,
                        backgroundStartRect,
                        backgroundEndRect,
                        finalRadius,
                        !isOpening,
                    )
                )
                // animated contents of folder with the folder background
                animatorSet.play(
                    shapeDelegate.createRevealAnimator(
                        folder.content,
                        contentStart,
                        contentEnd,
                        finalRadius,
                        !isOpening,
                    )
                )
            }
        }

        private fun addScrimAnimators(
            context: Context,
            animatorSet: AnimatorSet,
            isOpening: Boolean,
            launcherDelegate: LauncherDelegate,
        ) {
            val launcher = launcherDelegate.launcher ?: return
            val scrimView = launcher.scrimView
            val workspace = launcher.workspace
            val hotseat = launcher.hotseat
            val finalScrimAlpha =
                context.resources.getFloat(
                    if (isDarkTheme(context)) R.dimen.config_folderScrimAlphaDark
                    else R.dimen.config_folderScrimAlphaLight
                )
            scrimView.alpha = if (isOpening) 0f else finalScrimAlpha
            scrimView.setBackgroundColor(Color.BLACK)
            playSpringAnimation(
                context = context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_LAUNCHER_SCRIM,
                damping = DAMPING_LAUNCHER_SCRIM,
                startValue = 0f,
                endValue = finalScrimAlpha,
                minVisibleChange = MIN_VISIBLE_CHANGE_ALPHA,
                property = LauncherAnimUtils.VIEW_ALPHA,
                view = scrimView,
            )
            playSpringAnimation(
                context = context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_LAUNCHER_SCRIM,
                damping = DAMPING_LAUNCHER_SCRIM,
                startValue = 1f,
                endValue = LAUNCHER_SCALE,
                minVisibleChange = MIN_VISIBLE_CHANGE_SCALE,
                property = SCALE_PROPERTY,
                view = workspace,
            )
            playSpringAnimation(
                context = context,
                animatorSet = animatorSet,
                isOpening = isOpening,
                startDelay = 0,
                stiffness = STIFFNESS_LAUNCHER_SCRIM,
                damping = DAMPING_LAUNCHER_SCRIM,
                startValue = 1f,
                endValue = LAUNCHER_SCALE,
                minVisibleChange = MIN_VISIBLE_CHANGE_SCALE,
                property = SCALE_PROPERTY,
                view = hotseat,
            )
            animatorSet.addListener(FolderScrimAnimationListener(scrimView, isOpening))
        }

        /**
         * Gets the [BubbleTextView] from an icon. In some cases the BubbleTextView is the whole
         * icon itself, while in others it is contained within the view and only serves to store the
         * title text.
         */
        private fun getBubbleTextView(v: View): BubbleTextView {
            return if (v is AppPairIcon) v.titleTextView else (v as BubbleTextView)
        }

        private fun playItemAlphaAnimator(
            animatorSet: AnimatorSet,
            animator: ObjectAnimator,
            isOpening: Boolean,
        ) {
            animator.startDelay = if (isOpening) ITEM_ALPHA_START_DELAY else 0L
            animator.duration = ITEM_ALPHA_DURATION
            animator.interpolator = ITEM_ALPHA_INTERPOLATOR
            animatorSet.play(animator)
        }

        private fun addContentIconAnimators(
            context: Context,
            animatorSet: AnimatorSet,
            iconData: IconAnimationData,
        ) {
            with(iconData) {
                val titleText = getBubbleTextView(icon)
                titleText.setTextVisibility(false)
                if (!itemsInPreview.contains(icon)) {
                    icon.alpha = 0f
                    if (isOpening) {
                        playItemAlphaAnimator(
                            animatorSet,
                            ObjectAnimator.ofFloat(icon, View.ALPHA, 0f, 1f),
                            isOpening,
                        )
                    }
                }
                playSpringAnimation(
                    context = context,
                    animatorSet = animatorSet,
                    isOpening = isOpening,
                    startDelay = 0,
                    stiffness = STIFFNESS_SHAPE_POSITION,
                    damping = DAMPING_SHAPE_POSITION,
                    startValue = xDistance,
                    endValue = 0f,
                    minVisibleChange = MIN_VISIBLE_CHANGE_PIXELS,
                    property = View.TRANSLATION_X,
                    view = icon,
                )
                playSpringAnimation(
                    context = context,
                    animatorSet = animatorSet,
                    isOpening = isOpening,
                    startDelay = 0,
                    stiffness = STIFFNESS_SHAPE_POSITION,
                    damping = DAMPING_SHAPE_POSITION,
                    startValue = yDistance,
                    endValue = 0f,
                    minVisibleChange = MIN_VISIBLE_CHANGE_PIXELS,
                    property = View.TRANSLATION_Y,
                    view = icon,
                )
                playSpringAnimation(
                    context = context,
                    animatorSet = animatorSet,
                    isOpening = isOpening,
                    startDelay = 0,
                    stiffness = STIFFNESS_SHAPE_POSITION,
                    damping = DAMPING_SHAPE_POSITION,
                    startValue = initialIconScale,
                    endValue = 1f,
                    minVisibleChange = MIN_VISIBLE_CHANGE_SCALE,
                    property = SCALE_PROPERTY,
                    view = icon,
                )
                animatorSet.addListener(
                    getIconAnimatorListener(
                        icon = icon,
                        xDistance = xDistance,
                        yDistance = yDistance,
                        initialScale = initialIconScale,
                        pivotX = pivotX,
                        pivotY = pivotY,
                        itemsInPreview = itemsInPreview,
                        isOpening = isOpening,
                    )
                )
            }
        }

        private fun getIconAnimatorListener(
            icon: View,
            xDistance: Float,
            yDistance: Float,
            initialScale: Float,
            pivotX: Float,
            pivotY: Float,
            itemsInPreview: List<View>,
            isOpening: Boolean,
        ) =
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    icon.pivotX = 0f
                    icon.pivotY = 0f
                    if (isOpening) {
                        icon.translationX = xDistance
                        icon.translationY = yDistance
                        icon.scaleX = initialScale
                        icon.scaleY = initialScale
                    }
                    if (!itemsInPreview.contains(icon)) {
                        icon.alpha = 0f
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    if (isOpening) {
                        icon.translationX = 0.0f
                        icon.translationY = 0.0f
                        icon.scaleX = 1f
                        icon.scaleY = 1f
                        if (!itemsInPreview.contains(icon)) {
                            icon.alpha = 1f
                        }
                    }
                    icon.pivotX = pivotX
                    icon.pivotY = pivotY
                }
            }
    }
}
