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

package com.android.launcher3.allapps.compose.ui

import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitions
import com.android.compose.animation.scene.transitions

object AllAppsScenes {
    val Drawer = SceneKey("drawer")
    val SmartDrawer = SceneKey("smart_drawer")
    val FolderExpanded = SceneKey("folder_expanded")
    val Search = SceneKey("search")
}

object AllAppsElements {
    val DrawerRoot = ElementKey("drawer_root")
    val SmartDrawerRoot = ElementKey("smart_drawer_root")
    val FolderRoot = ElementKey("folder_root")
    val SearchRoot = ElementKey("search_root")
}

fun allAppsTransitions(): SceneTransitions = transitions {
    from(AllAppsScenes.SmartDrawer, to = AllAppsScenes.FolderExpanded) {
        spec = tween(350)
        fade(AllAppsElements.SmartDrawerRoot)
        fade(AllAppsElements.FolderRoot)
        translate(AllAppsElements.FolderRoot, y = 40.dp)
    }
    from(AllAppsScenes.FolderExpanded, to = AllAppsScenes.SmartDrawer) {
        spec = tween(500)
        fade(AllAppsElements.FolderRoot)
        fade(AllAppsElements.SmartDrawerRoot)
        translate(AllAppsElements.FolderRoot, y = (-40).dp)
        translate(AllAppsElements.SmartDrawerRoot, y = 40.dp)
    }
    from(AllAppsScenes.Drawer, to = AllAppsScenes.FolderExpanded) {
        spec = tween(350)
        fade(AllAppsElements.DrawerRoot)
        fade(AllAppsElements.FolderRoot)
        translate(AllAppsElements.FolderRoot, y = 40.dp)
    }
    from(AllAppsScenes.FolderExpanded, to = AllAppsScenes.Drawer) {
        spec = tween(500)
        fade(AllAppsElements.FolderRoot)
        fade(AllAppsElements.DrawerRoot)
        translate(AllAppsElements.FolderRoot, y = (-40).dp)
        translate(AllAppsElements.DrawerRoot, y = 40.dp)
    }
    from(AllAppsScenes.Drawer, to = AllAppsScenes.Search) {
        spec = tween(350)
        fractionRange(end = 0.4f) { fade(AllAppsElements.DrawerRoot) }
        fractionRange(start = 0.2f) { fade(AllAppsElements.SearchRoot) }
        translate(AllAppsElements.SearchRoot, y = 24.dp)
    }
    from(AllAppsScenes.SmartDrawer, to = AllAppsScenes.Search) {
        spec = tween(350)
        fractionRange(end = 0.4f) { fade(AllAppsElements.SmartDrawerRoot) }
        fractionRange(start = 0.2f) { fade(AllAppsElements.SearchRoot) }
        translate(AllAppsElements.SearchRoot, y = 24.dp)
    }
    from(AllAppsScenes.Search, to = AllAppsScenes.Drawer) {
        spec = tween(500)
        fade(AllAppsElements.SearchRoot)
        fade(AllAppsElements.DrawerRoot)
        translate(AllAppsElements.SearchRoot, y = (-24).dp)
        translate(AllAppsElements.DrawerRoot, y = 40.dp)
    }
    from(AllAppsScenes.Search, to = AllAppsScenes.SmartDrawer) {
        spec = tween(500)
        fade(AllAppsElements.SearchRoot)
        fade(AllAppsElements.SmartDrawerRoot)
        translate(AllAppsElements.SearchRoot, y = (-24).dp)
        translate(AllAppsElements.SmartDrawerRoot, y = 40.dp)
    }
}
