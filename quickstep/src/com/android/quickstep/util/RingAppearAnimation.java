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

package com.android.quickstep.util;

import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;

import android.animation.TimeInterpolator;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.LauncherRootView;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.IntSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RingAppearAnimation {

    private static final float ALPHA_DURATION_FRACTION = 0.25f;
    private static final float ICON_SCALE_START = 0.65f;
    private static final float ICON_TRANSLATION_FACTOR = 0.35000002f;
    private static final float RING_STAGGER = 0.08f;

    private RingAppearAnimation() { }

    static void addAnimators(PendingAnimation animation, QuickstepLauncher launcher,
            float startFraction) {
        Workspace<?> workspace = launcher.getWorkspace();
        Hotseat hotseat = launcher.getHotseat();
        LauncherRootView rootView = launcher.getRootView();
        List<CellLayout> visiblePages = getVisiblePages(workspace);
        if (visiblePages.isEmpty()) {
            return;
        }
        float[] pivot = getAveragePageCenter(rootView, visiblePages);
        List<List<ViewWithPivot>> rings = buildViewRingsWithPivots(
                visiblePages, hotseat, rootView, pivot);
        for (int i = 0; i < rings.size(); i++) {
            float ringStart = startFraction + (1f - startFraction) * i * RING_STAGGER;
            TimeInterpolator alphaInterpolator = clampToProgress(
                    LINEAR, ringStart, ringStart + (1f - ringStart) * ALPHA_DURATION_FRACTION);
            TimeInterpolator iconInterpolator = clampToProgress(
                    EMPHASIZED_DECELERATE, ringStart, 1f);
            for (ViewWithPivot viewWithPivot : rings.get(i)) {
                View view = viewWithPivot.view;
                animation.addFloat(view, VIEW_ALPHA, 0f, 1f, alphaInterpolator);
                animation.addFloat(view, SCALE_PROPERTY, ICON_SCALE_START, 1f, iconInterpolator);
                animation.addFloat(view, VIEW_TRANSLATE_X,
                        (viewWithPivot.pivotX - view.getPivotX()) * ICON_TRANSLATION_FACTOR,
                        0f, iconInterpolator);
                animation.addFloat(view, VIEW_TRANSLATE_Y,
                        (viewWithPivot.pivotY - view.getPivotY()) * ICON_TRANSLATION_FACTOR,
                        0f, iconInterpolator);
            }
        }
    }

    private static List<CellLayout> getVisiblePages(Workspace<?> workspace) {
        IntSet visiblePageIndices = workspace.getVisiblePageIndices();
        List<CellLayout> visiblePages = new ArrayList<>(visiblePageIndices.size());
        for (int pageIndex : visiblePageIndices) {
            visiblePages.add((CellLayout) workspace.getPageAt(pageIndex));
        }
        return visiblePages;
    }

    private static float[] getAveragePageCenter(LauncherRootView rootView,
            List<CellLayout> visiblePages) {
        float pivotX = 0f;
        float pivotY = 0f;
        for (CellLayout page : visiblePages) {
            float[] pageCenter = {
                    page.getShortcutsAndWidgets().getWidth() / 2f,
                    page.getShortcutsAndWidgets().getHeight() / 2f,
            };
            Utilities.getDescendantCoordRelativeToAncestor(page, rootView, pageCenter, true, false);
            pivotX += pageCenter[0];
            pivotY += pageCenter[1];
        }
        return new float[] {pivotX / visiblePages.size(), pivotY / visiblePages.size()};
    }

    private static List<List<ViewWithPivot>> buildViewRingsWithPivots(List<CellLayout> visiblePages,
            View hotseat, ViewGroup rootView, float[] pivot) {
        VirtualPage virtualPage = VirtualPage.create(visiblePages);
        List<List<ViewWithPivot>> rings = new ArrayList<>();
        Set<View> seenViews = new HashSet<>();
        if (virtualPage == null) {
            addHotseat(rings, seenViews, hotseat, rootView, pivot);
            return rings;
        }

        Rect bounds = new Rect(0, 0, virtualPage.totalColumns - 1, virtualPage.maxRows - 1);
        List<ViewWithPivot> cornerRing = new ArrayList<>();
        addCell(rootView, virtualPage, pivot, cornerRing, seenViews,
                new Point(bounds.left, bounds.top));
        addCell(rootView, virtualPage, pivot, cornerRing, seenViews,
                new Point(bounds.right, bounds.top));
        addCell(rootView, virtualPage, pivot, cornerRing, seenViews,
                new Point(bounds.left, bounds.bottom));
        addCell(rootView, virtualPage, pivot, cornerRing, seenViews,
                new Point(bounds.right, bounds.bottom));
        if (!cornerRing.isEmpty()) {
            rings.add(cornerRing);
        }

        while (bounds.top <= bounds.bottom && bounds.left <= bounds.right) {
            List<ViewWithPivot> ring = new ArrayList<>();
            for (int cellY = bounds.top; cellY <= bounds.bottom; cellY++) {
                if (cellY == bounds.top || cellY == bounds.bottom) {
                    for (int cellX = bounds.left; cellX <= bounds.right; cellX++) {
                        addCell(rootView, virtualPage, pivot, ring, seenViews,
                                new Point(cellX, cellY));
                    }
                } else {
                    addCell(rootView, virtualPage, pivot, ring, seenViews,
                            new Point(bounds.left, cellY));
                    addCell(rootView, virtualPage, pivot, ring, seenViews,
                            new Point(bounds.right, cellY));
                }
            }
            if (!ring.isEmpty()) {
                rings.add(ring);
            }
            bounds.inset(1, 1);
        }

        addHotseat(rings, seenViews, hotseat, rootView, pivot);
        return rings;
    }

    private static void addCell(ViewGroup rootView, VirtualPage virtualPage, float[] pivot,
            List<ViewWithPivot> ring, Set<View> seenViews, Point cell) {
        int pageIndex = cell.x / virtualPage.pageColumns;
        if (pageIndex < 0 || pageIndex >= virtualPage.pages.size()) {
            return;
        }
        CellLayout page = virtualPage.pages.get(pageIndex);
        int localCellX = cell.x % virtualPage.pageColumns;
        if (localCellX < 0 || localCellX >= page.getCountX()
                || cell.y < 0 || cell.y >= page.getCountY()) {
            return;
        }
        View child = page.getChildAt(localCellX, cell.y);
        if (child == null || seenViews.contains(child)) {
            return;
        }
        ring.add(createViewWithPivot(child, rootView, pivot));
        seenViews.add(child);
    }

    private static void addHotseat(List<List<ViewWithPivot>> rings, Set<View> seenViews,
            View hotseat, ViewGroup rootView, float[] pivot) {
        if (hotseat == null || seenViews.contains(hotseat)) {
            return;
        }
        if (rings.isEmpty()) {
            rings.add(new ArrayList<>());
        }
        rings.get(0).add(createViewWithPivot(hotseat, rootView, pivot));
        seenViews.add(hotseat);
    }

    private static ViewWithPivot createViewWithPivot(View view, ViewGroup rootView, float[] pivot) {
        float[] viewPivot = pivot.clone();
        Utilities.mapCoordInSelfToDescendant(view, rootView, viewPivot);
        return new ViewWithPivot(view, viewPivot[0], viewPivot[1]);
    }

    private static final class VirtualPage {
        final List<CellLayout> pages;
        final int totalColumns;
        final int maxRows;
        final int pageColumns;

        private VirtualPage(List<CellLayout> pages, int totalColumns, int maxRows,
                int pageColumns) {
            this.pages = pages;
            this.totalColumns = totalColumns;
            this.maxRows = maxRows;
            this.pageColumns = pageColumns;
        }

        static VirtualPage create(List<CellLayout> pages) {
            if (pages.isEmpty()) {
                return null;
            }
            int totalColumns = 0;
            int maxRows = 0;
            for (CellLayout page : pages) {
                totalColumns += page.getCountX();
                maxRows = Math.max(maxRows, page.getCountY());
            }
            int pageColumns = pages.get(pages.size() - 1).getCountX();
            if (totalColumns <= 0 || maxRows <= 0 || pageColumns <= 0) {
                return null;
            }
            return new VirtualPage(pages, totalColumns, maxRows, pageColumns);
        }
    }

    private static final class ViewWithPivot {
        final View view;
        final float pivotX;
        final float pivotY;

        ViewWithPivot(View view, float pivotX, float pivotY) {
            this.view = view;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }
    }
}
