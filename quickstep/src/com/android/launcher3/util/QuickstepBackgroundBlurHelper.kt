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

package com.android.launcher3.util

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.graphics.PathWrapper
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject

@ActivityContextSingleton
class QuickstepBackgroundBlurHelper
@Inject
constructor(
    private val activityContext: ActivityContext,
) : BlurBackgroundHelper() {
    private val blurRadius =
        activityContext.asContext().resources.getDimension(R.dimen.folder_blur_radius)
    private val cornerRadius = Themes.getDialogCornerRadius(activityContext.asContext())
    private val workspaceBlurRenderNode = RenderNode("workspaceBlur")
    private val workspaceBlurRenderNodeOutline = Outline()
    private val workspaceBlurPath = Path()
    private val bounds = Rect()
    private val blurDrawable: BackgroundBlurDrawable? by lazy {
        if (!isFolderBlurStyleEnabled()) {
            null
        } else {
            activityContext.dragLayer.viewRootImpl
                .createBackgroundBlurDrawable()
                ?.apply {
                    setBlurRadius(blurRadius.toInt())
                    setVisible(false, false)
                }
        }
    }

    override fun prepareToOpenFolder(folder: Folder) {
        if (!isFolderBlurStyleEnabled()) {
            return
        }

        val folderIcon = folder.folderIcon
        val folderNameVisibility = folderIcon.folderName.visibility
        val isIconVisible = folderIcon.iconVisible

        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)

        val dragLayer = activityContext.dragLayer
        val canvas = workspaceBlurRenderNode.beginRecording(dragLayer.width, dragLayer.height)
        dragLayer.draw(canvas)
        workspaceBlurRenderNode.endRecording()
        workspaceBlurRenderNode.setPosition(0, 0, dragLayer.width, dragLayer.height)

        folderIcon.folderName.visibility = folderNameVisibility
        folderIcon.setIconVisible(isIconVisible)
    }

    override fun drawFolderBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        if (!isFolderBlurStyleEnabled()) {
            return
        }

        drawCrossWindowBlur(canvas, pathWrapper, view)
        drawWorkspaceBlur(canvas, pathWrapper?.path, view)
    }

    private fun drawWorkspaceBlur(canvas: Canvas, path: Path?, view: View) {
        if (!workspaceBlurRenderNode.hasDisplayList()) {
            return
        }
        workspaceBlurRenderNode.translationX = -view.left.toFloat()
        workspaceBlurRenderNode.translationY = -view.top.toFloat()

        if (path != null) {
            workspaceBlurPath.set(path)
            workspaceBlurPath.offset(view.left.toFloat(), view.top.toFloat())
            workspaceBlurRenderNodeOutline.setPath(workspaceBlurPath)
            workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
            workspaceBlurRenderNode.setClipToOutline(true)
        }

        workspaceBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                Shader.TileMode.CLAMP
            )
        )
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val drawable = blurDrawable ?: return

        drawable.setVisible(true, false)
        if (pathWrapper != null) {
            pathWrapper.bounds.roundOut(bounds)
            drawable.bounds = bounds
            drawable.setCornerRadius(pathWrapper.cornerRadius)
        } else {
            drawable.setBounds(0, 0, view.width, view.height)
            drawable.setCornerRadius(cornerRadius)
        }
        drawable.draw(canvas)
    }

    override fun folderCloseComplete() {
        if (workspaceBlurRenderNode.hasDisplayList()) {
            workspaceBlurRenderNode.discardDisplayList()
        }
        blurDrawable?.setVisible(false, false)
    }

    private fun isFolderBlurStyleEnabled(): Boolean {
        return activityContext.isCrossWindowBlurEnabled
    }
}
