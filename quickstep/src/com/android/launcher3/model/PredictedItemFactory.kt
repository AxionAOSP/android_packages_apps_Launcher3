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

package com.android.launcher3.model

import android.app.prediction.AppPredictionContext
import android.app.prediction.AppPredictionManager
import android.app.prediction.AppPredictor
import android.app.prediction.AppTarget
import android.app.prediction.AppTargetEvent
import android.app.prediction.AppTargetId
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import androidx.annotation.VisibleForTesting
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.cache.CacheLookupFlag
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.PredictedItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.shortcuts.ShortcutKey
import com.android.launcher3.shortcuts.ShortcutRequest
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PersistedItemArray
import com.android.launcher3.util.PersistedItemArray.ItemFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** [PersistedItemArray.ItemFactory] to parse predicted items */
class PredictedItemFactory
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val pmHelper: PackageManagerHelper,
    private val userCache: UserCache,
    private val apiWrapper: ApiWrapper,
    private val iconCache: IconCache,
    @Assisted private val maxItemCount: Int,
    @Assisted private val predictorState: PredictorState,
) : ItemFactory<ItemInfo> {

    // Number of items persisted can be different than what is needed if the grid changed between
    // the two operations
    private var readCount = 0

    override fun createInfo(itemType: Int, user: UserHandle, intent: Intent): PredictedItemInfo? {
        if (readCount >= maxItemCount) {
            return null
        }
        return when (itemType) {
            Favorites.ITEM_TYPE_APPLICATION -> {
                val lai =
                    context
                        .getSystemService(LauncherApps::class.java)
                        ?.resolveActivity(intent, user) ?: return null
                val info =
                    AppInfo(
                        lai,
                        userCache.userManagerState.getCachedInfo(user),
                        apiWrapper,
                        pmHelper,
                    )
                info.container = predictorState.containerId
                iconCache.getTitleAndIcon(info, lai, predictorState.lookupFlag)
                readCount++
                return PredictedItemInfo(info.makeWorkspaceItem(context))
            }

            Favorites.ITEM_TYPE_DEEP_SHORTCUT -> {
                val key = ShortcutKey.fromIntent(intent, user) ?: return null
                val si: ShortcutInfo =
                    key.buildRequest(context).query(ShortcutRequest.PINNED).getOrNull(0)
                        ?: return null
                val wii = WorkspaceItemInfo(si, context)
                wii.container = predictorState.containerId
                iconCache.getShortcutIcon(wii, si)
                readCount++
                return PredictedItemInfo(wii)
            }

            else -> null
        }
    }

    @AssistedFactory
    interface Factory {
        fun newParser(maxItemCount: Int, predictorState: PredictorState): PredictedItemFactory
    }
}

/** Class to manage predicted items for a particular prediction type */
class PredictorState(
    @JvmField val containerId: Int,
    storageName: String,
    @JvmField val lookupFlag: CacheLookupFlag,
) {

    @JvmField val storage: PersistedItemArray<ItemInfo> = PersistedItemArray(storageName)

    @VisibleForTesting var predictor: AppPredictor? = null

    private var lastTargets: List<AppTarget> = emptyList()

    private var localCtx: Context? = null
    private var localModel: LauncherModel? = null
    private var localMaxCount: Int = 0
    private var localTaskFactory: ((PredictorState, List<AppTarget>) -> ModelUpdateTask)? = null

    /** Creates and registers a predictor, and destroys any previously created predictor */
    fun registerPredictor(
        ctx: Context,
        predictionContext: AppPredictionContext,
        model: LauncherModel,
        taskFactory: (PredictorState, List<AppTarget>) -> ModelUpdateTask,
    ) {
        destroyPredictor()

        val apm = ctx.getSystemService(AppPredictionManager::class.java)
        lastTargets = emptyList()

        if (apm != null) {
            predictor =
                apm.createAppPredictionSession(predictionContext).apply {
                    registerPredictionUpdates(MODEL_EXECUTOR) {
                        val oldTargets = lastTargets
                        lastTargets = it

                        // If no diff, skip
                        if (
                            oldTargets.size != lastTargets.size ||
                                oldTargets.zip(lastTargets).any { (a1, a2) ->
                                    !areAppTargetsSame(a1, a2)
                                }
                        ) {
                            model.enqueueModelUpdateTask(
                                taskFactory.invoke(this@PredictorState, it)
                            )
                        }
                    }
                    requestPredictionUpdate()
                }
        } else {
            localCtx = ctx
            localModel = model
            localMaxCount = predictionContext.predictedTargetCount
            localTaskFactory = taskFactory
            val targets = queryLocalTargets(ctx, predictionContext.predictedTargetCount)
            lastTargets = targets
            if (targets.isNotEmpty()) {
                model.enqueueModelUpdateTask(
                    taskFactory.invoke(this@PredictorState, targets)
                )
            }
        }
    }

    /** Destroys a previously created predictor */
    fun destroyPredictor() {
        predictor?.destroy()
        predictor = null
        localCtx = null
        localModel = null
        localMaxCount = 0
        localTaskFactory = null
    }

    /** see [AppPredictor.requestPredictionUpdate] */
    fun requestPredictionUpdate() {
        if (predictor != null) {
            predictor?.requestPredictionUpdate()
        } else {
            localCtx?.let { ctx ->
                localModel?.let { model ->
                    localTaskFactory?.let { factory ->
                        val targets = queryLocalTargets(ctx, localMaxCount)
                        if (targets != lastTargets) {
                            lastTargets = targets
                            if (targets.isNotEmpty()) {
                                model.enqueueModelUpdateTask(
                                    factory.invoke(this@PredictorState, targets)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** see [AppPredictor.notifyAppTargetEvent] */
    fun notifyAppTargetEvent(event: AppTargetEvent) = predictor?.notifyAppTargetEvent(event)

    /** Compares two targets for the properties which we care about */
    private fun areAppTargetsSame(t1: AppTarget, t2: AppTarget): Boolean {
        if (
            (t1.packageName != t2.packageName) ||
                (t1.user != t2.user) ||
                (t1.className != t2.className)
        ) {
            return false
        }
        return t1.shortcutInfo?.id == t2.shortcutInfo?.id
    }
}

private fun Context.getUsageStatsManager(): UsageStatsManager? =
    getSystemService(UsageStatsManager::class.java)

private fun Context.getLauncherApps(): LauncherApps? =
    getSystemService(LauncherApps::class.java)

fun queryLocalTargets(ctx: Context, maxCount: Int): List<AppTarget> {
    ctx.getUsageStatsManager()?.let { usm ->
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 24 * 60 * 60 * 1000L
        val usageStats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        } catch (e: SecurityException) {
            null
        }
        if (usageStats != null) {
            ctx.getLauncherApps()?.let { la ->
                val myUser = Process.myUserHandle()
                val targets = usageStats
                    .filter { it.lastTimeUsed >= startTime }
                    .sortedByDescending { it.lastTimeUsed }
                    .distinctBy { it.packageName }
                    .take(maxCount)
                    .mapNotNull { us ->
                        la.getActivityList(us.packageName, myUser)?.firstOrNull()?.let { lai ->
                            AppTarget.Builder(
                                AppTargetId("app:" + us.packageName),
                                us.packageName,
                                myUser
                            )
                                .setClassName(lai.componentName.className)
                                .build()
                        }
                    }
                if (targets.isNotEmpty()) return targets
            }
        }
    }
    return emptyList()
}
