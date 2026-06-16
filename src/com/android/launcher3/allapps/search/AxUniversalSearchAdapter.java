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
package com.android.launcher3.allapps.search;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_ACTION;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_PILL;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SEARCH_SECTION;

import android.content.ActivityNotFoundException;
import android.content.res.ColorStateList;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

public final class AxUniversalSearchAdapter {

    private static final String TAG = "AxUniversalSearch";
    private static final int SEARCH_PILL_ITEMS_PER_ROW = 2;
    private static final int[] SUPPORTED_ITEMS_PER_ROW = {SEARCH_PILL_ITEMS_PER_ROW};

    private AxUniversalSearchAdapter() { }

    public static boolean isViewSupported(int viewType) {
        return viewType == VIEW_TYPE_SEARCH_ACTION || viewType == VIEW_TYPE_SEARCH_PILL
                || viewType == VIEW_TYPE_SEARCH_SECTION;
    }

    public static AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater layoutInflater,
            ViewGroup parent, int viewType) {
        int layout = switch (viewType) {
            case VIEW_TYPE_SEARCH_PILL -> R.layout.universal_search_pill;
            case VIEW_TYPE_SEARCH_SECTION -> R.layout.universal_search_section_header;
            default -> R.layout.universal_search_action;
        };
        return new AllAppsGridAdapter.ViewHolder(layoutInflater.inflate(layout, parent, false));
    }

    public static int[] getSupportedItemsPerRowArray() {
        return SUPPORTED_ITEMS_PER_ROW;
    }

    public static int getItemsPerRow(int viewType, int appsPerRow) {
        if (viewType == VIEW_TYPE_SEARCH_PILL) {
            return Math.max(1, Math.min(SEARCH_PILL_ITEMS_PER_ROW, appsPerRow));
        }
        return 1;
    }

    public static boolean onBindView(ActivityContext launcher, AllAppsGridAdapter.ViewHolder holder,
            int position) {
        if (!isViewSupported(holder.getItemViewType())) {
            return false;
        }
        AdapterItem item = launcher.getAppsView().getSearchResultList().getAdapterItems()
                .get(position);
        if (holder.getItemViewType() == VIEW_TYPE_SEARCH_SECTION) {
            bindSearchSection(holder.itemView, item);
            return true;
        }
        bindSearchAction(holder.itemView, item, launcher);
        return true;
    }

    public static boolean launchHighlightedItem(ActivityContext launcher, View highlightedView) {
        if (highlightedView != null && highlightedView.getTag() instanceof AdapterItem item
                && isActionable(item)) {
            return launchSearchAction(launcher, highlightedView, item);
        }
        List<AdapterItem> items = launcher.getAppsView().getSearchResultList().getAdapterItems();
        for (int i = 0; i < items.size(); i++) {
            if (launchAdapterItem(launcher.getAppsView(), launcher, items.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static void bindSearchSection(View view, AdapterItem item) {
        bindIcon(view, item);
        bindGroupBackground(view, item);
        ((TextView) view.findViewById(R.id.search_action_title)).setText(item.searchActionTitle);
        bindEndIcon(view, item);
        view.setTag(item);
        view.setOnClickListener(null);
        view.setClickable(false);
        view.setFocusable(false);
    }

    private static void bindSearchAction(View view, AdapterItem item, ActivityContext launcher) {
        bindIcon(view, item);
        bindGroupBackground(view, item);
        ((TextView) view.findViewById(R.id.search_action_title)).setText(item.searchActionTitle);
        TextView subtitle = view.findViewById(R.id.search_action_subtitle);
        if (subtitle != null) {
            subtitle.setText(item.searchActionSubtitle);
            subtitle.setVisibility(TextUtils.isEmpty(item.searchActionSubtitle) ? View.GONE
                    : View.VISIBLE);
        }
        bindThumbnail(view, item);
        bindEndIcon(view, item);
        view.setTag(item);
        view.setOnClickListener(v -> launchAdapterItem(v, launcher, item));
    }

    private static void bindThumbnail(View view, AdapterItem item) {
        ImageView thumbnail = view.findViewById(R.id.search_action_thumbnail);
        FrameLayout container = view.findViewById(R.id.search_action_thumbnail_container);
        if (thumbnail == null || container == null) {
            return;
        }
        if (!item.searchActionThumbnailTrailing || item.searchActionIcon == null) {
            container.setVisibility(View.GONE);
            thumbnail.setImageDrawable(null);
            return;
        }
        container.setVisibility(View.VISIBLE);
        thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
        thumbnail.setImageDrawable(item.searchActionIcon);
    }

    private static void bindEndIcon(View view, AdapterItem item) {
        ImageView endIcon = view.findViewById(R.id.search_action_end_icon);
        if (endIcon != null) {
            if (item.searchActionEndIconRes == 0) {
                endIcon.setVisibility(View.GONE);
                return;
            }
            endIcon.setVisibility(View.VISIBLE);
            endIcon.setImageResource(item.searchActionEndIconRes);
        }
    }

    private static void bindGroupBackground(View view, AdapterItem item) {
        if (item.viewType == VIEW_TYPE_SEARCH_PILL || item.viewType == VIEW_TYPE_SEARCH_SECTION) {
            return;
        }
        int background = switch (item.searchActionGroupPosition) {
            case AdapterItem.SEARCH_GROUP_TOP -> R.drawable.universal_search_group_top_background;
            case AdapterItem.SEARCH_GROUP_MIDDLE ->
                    R.drawable.universal_search_group_middle_background;
            case AdapterItem.SEARCH_GROUP_BOTTOM ->
                    R.drawable.universal_search_group_bottom_background;
            default -> R.drawable.universal_search_action_background;
        };
        view.setBackgroundResource(background);
        int groupMargin = view.getResources().getDimensionPixelSize(
                R.dimen.universal_search_group_margin_vertical);
        int itemMargin = view.getResources().getDimensionPixelSize(
                R.dimen.universal_search_group_inner_margin_vertical);
        boolean startsGroup = item.searchActionGroupPosition == AdapterItem.SEARCH_GROUP_SINGLE
                || item.searchActionGroupPosition == AdapterItem.SEARCH_GROUP_TOP;
        boolean endsGroup = item.searchActionGroupPosition == AdapterItem.SEARCH_GROUP_SINGLE
                || item.searchActionGroupPosition == AdapterItem.SEARCH_GROUP_BOTTOM;
        setVerticalMargins(view, startsGroup ? groupMargin : itemMargin,
                endsGroup ? groupMargin : 0);
    }

    private static void bindIcon(View view, AdapterItem item) {
        ImageView icon = view.findViewById(R.id.search_action_icon);
        FrameLayout container = view.findViewById(R.id.search_action_icon_container);
        if (icon == null) {
            return;
        }
        if (item.viewType == VIEW_TYPE_SEARCH_SECTION && item.searchActionIcon == null) {
            if (container != null) {
                container.setVisibility(View.GONE);
            }
            return;
        }
        if (container != null) {
            container.setVisibility(View.VISIBLE);
        }
        if (item.searchActionThumbnailTrailing) {
            icon.setImageResource(item.searchActionIconRes);
        } else if (item.searchActionIcon != null) {
            icon.setImageDrawable(item.searchActionIcon);
        } else {
            icon.setImageResource(item.searchActionIconRes);
        }
        boolean tintIcon = item.searchActionThumbnailTrailing || item.searchActionIconTinted;
        icon.setImageTintList(tintIcon
                ? ColorStateList.valueOf(view.getContext().getColor(R.color.materialColorPrimary))
                : null);
        icon.setScaleType(item.searchActionIconFullBleed && !item.searchActionThumbnailTrailing
                ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
        if (container != null) {
            int containerWidth = getIconContainerWidth(view, item);
            int containerHeight = getIconContainerHeight(view, item);
            boolean hasIconBackground = tintIcon
                    || (item.searchActionIconFullBleed && !item.searchActionThumbnailTrailing);
            setSize(container, containerWidth, containerHeight);
            int iconSize = hasIconBackground ? getContainedIconSize(view, item)
                    : ViewGroup.LayoutParams.MATCH_PARENT;
            setSize(icon, iconSize, iconSize);
            if (hasIconBackground) {
                container.setBackgroundResource(R.drawable.universal_search_icon_background);
            } else {
                container.setBackground(null);
            }
            container.setClipToOutline(item.searchActionIconFullBleed
                    && !item.searchActionThumbnailTrailing);
        }
    }

    private static int getContainedIconSize(View view, AdapterItem item) {
        if (item.viewType == VIEW_TYPE_SEARCH_SECTION) {
            return view.getResources().getDimensionPixelSize(
                    R.dimen.universal_search_section_icon_size);
        }
        return item.searchActionIconFullBleed && !item.searchActionThumbnailTrailing
                ? ViewGroup.LayoutParams.MATCH_PARENT
                : view.getResources().getDimensionPixelSize(R.dimen.universal_search_icon_size);
    }

    private static int getIconContainerWidth(View view, AdapterItem item) {
        int dimen;
        if (item.searchActionIconFullBleed && !item.searchActionThumbnailTrailing) {
            dimen = R.dimen.universal_search_thumbnail_width;
        } else if (item.viewType == VIEW_TYPE_SEARCH_SECTION) {
            dimen = R.dimen.universal_search_section_icon_container_size;
        } else {
            dimen = R.dimen.universal_search_icon_container_size;
        }
        return view.getResources().getDimensionPixelSize(dimen);
    }

    private static int getIconContainerHeight(View view, AdapterItem item) {
        int dimen;
        if (item.searchActionIconFullBleed && !item.searchActionThumbnailTrailing) {
            dimen = R.dimen.universal_search_thumbnail_height;
        } else if (item.viewType == VIEW_TYPE_SEARCH_SECTION) {
            dimen = R.dimen.universal_search_section_icon_container_size;
        } else {
            dimen = R.dimen.universal_search_icon_container_size;
        }
        return view.getResources().getDimensionPixelSize(dimen);
    }

    private static void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private static void setVerticalMargins(View view, int top, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams margins) {
            margins.topMargin = top;
            margins.bottomMargin = bottom;
            view.setLayoutParams(margins);
        }
    }

    private static boolean launchAdapterItem(View view, ActivityContext launcher, AdapterItem item) {
        if (item.itemInfo != null && item.itemInfo.getIntent() != null) {
            AxSearchHistory.recordCurrentQuery(launcher);
            return launcher.startActivitySafely(view, item.itemInfo.getIntent(), item.itemInfo)
                    != null;
        }
        return launchSearchAction(launcher, view, item);
    }

    private static boolean isActionable(AdapterItem item) {
        return item.itemInfo != null || item.searchActionIntent != null
                || item.searchActionShortcut != null || item.searchActionQuery != null;
    }

    private static boolean launchSearchAction(ActivityContext launcher, View view,
            AdapterItem item) {
        if (item.searchActionQuery != null) {
            return applySearchQuery(launcher, item.searchActionQuery);
        }
        if (item.searchActionShortcut != null) {
            AxSearchHistory.recordCurrentQuery(launcher);
            return launchShortcut(launcher, view, item.searchActionShortcut);
        }
        if (item.searchActionIntent != null) {
            AxSearchHistory.recordCurrentQuery(launcher);
        }
        return item.searchActionIntent != null
                && launcher.startActivitySafely(null, item.searchActionIntent, null) != null;
    }

    private static boolean applySearchQuery(ActivityContext launcher, String query) {
        ActivityAllAppsContainerView<?> appsView = launcher.getAppsView();
        if (appsView == null) {
            return false;
        }
        ExtendedEditText editText = appsView.getSearchUiManager().getEditText();
        if (editText == null || TextUtils.isEmpty(query)) {
            return false;
        }
        editText.setText(query);
        editText.showKeyboard();
        return true;
    }

    private static boolean launchShortcut(ActivityContext launcher, View view,
            ShortcutInfo shortcutInfo) {
        LauncherApps launcherApps = launcher.asContext().getSystemService(LauncherApps.class);
        if (launcherApps == null) {
            return false;
        }
        try {
            launcherApps.startShortcut(shortcutInfo.getPackage(), shortcutInfo.getId(),
                    Utilities.getViewBounds(view), launcher.makeDefaultActivityOptions(-1)
                            .toBundle(), shortcutInfo.getUserHandle());
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalStateException e) {
            Toast.makeText(launcher.asContext(), R.string.activity_not_found,
                    Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch shortcut " + shortcutInfo.getPackage() + "/"
                    + shortcutInfo.getId(), e);
            return false;
        }
    }
}
