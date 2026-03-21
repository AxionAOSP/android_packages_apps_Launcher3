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

package com.android.quickstep

import android.app.FreeformLauncher
import android.provider.Settings
import android.view.View
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.views.DesktopTaskView
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.RecentsViewContainer.containerFromContext
import com.android.quickstep.views.TaskView
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus

/**
 * Represents a system shortcut that can be shown for a [TaskView]. Appears as a single entry in the
 * dropdown menu that shows up when you tap the app chip in Overview.
 */
interface TaskViewShortFactory {

    fun getShortcuts(
        container: RecentsViewContainer,
        taskView: TaskView,
    ): List<SystemShortcut<ActivityContext>>

    fun showForGroupedTask() = false

    fun showForDesktopTask() = false

    class LockAppSystemShortcut(
        iconResId: Int,
        textResId: Int,
        container: RecentsViewContainer,
        private val taskView: TaskView,
    ) :
        SystemShortcut<ActivityContext>(
            iconResId,
            textResId,
            container,
            taskView.itemInfo,
            taskView,
        ) {
        override fun onClick(view: View) {
            val recentsView = taskView.recentsView ?: return
            val task = taskView.firstTask ?: return
            val packageName = task.key?.packageName ?: return
            dismissTaskMenuView()
            recentsView.lockApp(packageName, !taskView.isLocked, task.key)
            (mTarget as RecentsViewContainer).actionsView.updateLockState(taskView.isLocked)
            taskView.updateLockBadge()
        }
    }

    class FreeformSystemShortcut(
        iconResId: Int,
        textResId: Int,
        container: RecentsViewContainer,
        private val taskView: TaskView,
    ) :
        SystemShortcut<ActivityContext>(
            iconResId,
            textResId,
            container,
            taskView.itemInfo,
            taskView,
        ) {
        override fun onClick(view: View) {
            val recentsView = taskView.recentsView ?: return
            dismissTaskMenuView()
            recentsView.switchToScreenshot {
                recentsView.finishRecentsAnimation(true, false) {
                    (mTarget as RecentsViewContainer).returnToHomescreen()
                    recentsView.handler.post {
                        val taskKey = taskView.firstTask?.key ?: return@post
                        val component = taskKey.component
                        if (component != null) {
                            FreeformLauncher.launch(component.packageName, component.className)
                        } else {
                            val pkg = taskKey.packageName ?: return@post
                            FreeformLauncher.launch(pkg)
                        }
                        mTarget.statsLogManager.logger()
                            .withItemInfo(taskView.itemInfo)
                            .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP)
                    }
                }
            }
        }
    }

    class RemoveTaskSystemShortcut(
        iconResId: Int,
        textResId: Int,
        container: RecentsViewContainer,
        private val taskView: TaskView,
    ) :
        SystemShortcut<ActivityContext>(
            iconResId,
            textResId,
            container,
            taskView.itemInfo,
            taskView,
        ) {
        override fun onClick(view: View) {
            val recentsView = taskView.recentsView ?: return
            dismissTaskMenuView()
            val proxy = SystemUiProxy.INSTANCE.get(view.context)
            for (container in taskView.taskContainers) {
                val key = container.task.key
                val packageName = key.packageName ?: continue
                proxy.forceStopPackage(packageName, key.userId)
            }
            recentsView.dismissTaskView(taskView, true, true)
            mTarget.statsLogManager
                .logger()
                .withItemInfo(taskView.itemInfo)
                .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_CLOSE_APP_TAP)
        }
    }

    companion object {
        /** Returns menu options associated with TaskView. */
        fun getEnabledShortcuts(taskView: TaskView) =
            TASK_VIEW_MENU_OPTIONS.filter {
                    taskView !is GroupedTaskView || it.showForGroupedTask()
                }
                .filter { taskView !is DesktopTaskView || it.showForDesktopTask() }
                .flatMap { it.getShortcuts(containerFromContext(taskView.context), taskView) }

        private val FREE_FORM: TaskViewShortFactory =
            object : TaskViewShortFactory {
                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskView: TaskView,
                ): List<SystemShortcut<ActivityContext>> {
                    val task = taskView.firstTask ?: return emptyList()
                    if (!task.isDockable) return emptyList()
                    val context = container.asContext()
                    val freeformEnabled = Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0
                    ) != 0
                    if (!freeformEnabled || DesktopModeStatus.canEnterDesktopMode(context)) {
                        return emptyList()
                    }
                    return listOf(
                        FreeformSystemShortcut(
                            R.drawable.ic_caption_desktop_button_foreground,
                            R.string.recent_task_option_freeform,
                            container,
                            taskView,
                        )
                    )
                }
            }

        private val LOCK_APP: TaskViewShortFactory =
            object : TaskViewShortFactory {
                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskView: TaskView,
                ): List<SystemShortcut<ActivityContext>> {
                    if (taskView.firstTask == null) return emptyList()
                    val isLocked = taskView.isLocked
                    val iconRes = if (isLocked) R.drawable.ic_app_locked
                        else R.drawable.ic_app_unlocked
                    val textRes = if (isLocked) R.string.recent_task_option_unlock
                        else R.string.recent_task_option_lock
                    return listOf(
                        LockAppSystemShortcut(iconRes, textRes, container, taskView)
                    )
                }

                override fun showForGroupedTask() = true
            }

        private val REMOVE_TASK: TaskViewShortFactory =
            object : TaskViewShortFactory {
                override fun getShortcuts(
                    container: RecentsViewContainer,
                    taskView: TaskView,
                ): List<SystemShortcut<ActivityContext>> {
                    val recentsView = taskView.recentsView ?: return emptyList()
                    if (!recentsView.canRemoveTaskView(taskView)) {
                        return emptyList()
                    }
                    return listOf<SystemShortcut<ActivityContext>>(
                        RemoveTaskSystemShortcut(
                            R.drawable.ic_remove_task_option,
                            R.string.recent_task_option_remove_task,
                            container,
                            taskView,
                        )
                    )
                }

                override fun showForGroupedTask() = true

                override fun showForDesktopTask() = true
            }

        private val TASK_VIEW_MENU_OPTIONS: Array<TaskViewShortFactory> =
            arrayOf(FREE_FORM, LOCK_APP, REMOVE_TASK)
    }
}
