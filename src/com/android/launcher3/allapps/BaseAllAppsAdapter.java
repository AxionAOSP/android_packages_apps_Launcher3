/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static android.view.View.GONE;

import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_RIGHT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_TOP_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_TOP_RIGHT;
import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;

import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.AxSearchHistory;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.views.ActivityContext;

import java.util.Objects;

/**
 * Adapter for all the apps.
 */
public abstract class BaseAllAppsAdapter
        extends RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> {

    public static final String TAG = "BaseAllAppsAdapter";

    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 2;
    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_ALL_APPS_DIVIDER = 1 << 3;

    public static final int VIEW_TYPE_WORK_EDU_CARD = 1 << 4;
    public static final int VIEW_TYPE_WORK_DISABLED_CARD = 1 << 5;
    public static final int VIEW_TYPE_PRIVATE_SPACE_HEADER = 1 << 6;
    public static final int VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER = 1 << 7;
    public static final int VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO = 1 << 8;
    public static final int VIEW_TYPE_PRIVATE_SPACE_APP_ICON = 1 << 9;
    public static final int VIEW_TYPE_FOLDER_ICON = 1 << 10;
    public static final int VIEW_TYPE_SEARCH_ACTION = 1 << 11;
    public static final int VIEW_TYPE_SEARCH_PILL = 1 << 12;
    public static final int VIEW_TYPE_SEARCH_SECTION = 1 << 13;
    public static final int NEXT_ID = 14;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON | VIEW_TYPE_PRIVATE_SPACE_APP_ICON
            | VIEW_TYPE_FOLDER_ICON;

    public static final int VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER =
            VIEW_TYPE_PRIVATE_SPACE_HEADER;
    public static final int VIEW_TYPE_MASK_PRIVATE_SPACE_SYS_APPS_DIVIDER =
            VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER;

    protected final SearchAdapterProvider<?> mAdapterProvider;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /** Sets the number of apps to be displayed in one row of the all apps screen. */
    public abstract void setAppsPerRow(int appsPerRow);

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        public static final int SEARCH_GROUP_SINGLE = 0;
        public static final int SEARCH_GROUP_TOP = 1;
        public static final int SEARCH_GROUP_MIDDLE = 2;
        public static final int SEARCH_GROUP_BOTTOM = 3;

        /** Common properties */
        // The type of this item
        public final int viewType;

        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated ItemInfoWithIcon for the item
        public AppInfo itemInfo = null;
        public AllAppsFolderInfo folderInfo = null;
        public CharSequence searchActionTitle = null;
        public CharSequence searchActionSubtitle = null;
        public Drawable searchActionIcon = null;
        public int searchActionIconRes = 0;
        public boolean searchActionIconTinted = true;
        public boolean searchActionIconFullBleed = false;
        public boolean searchActionThumbnailTrailing = false;
        public int searchActionEndIconRes = R.drawable.ic_chevron_end;
        public Intent searchActionIntent = null;
        public ShortcutInfo searchActionShortcut = null;
        public String searchActionQuery = null;
        public int searchActionGroupPosition = SEARCH_GROUP_SINGLE;
        // Private App Decorator
        public SectionDecorationInfo decorationInfo = null;
        public AdapterItem(int viewType) {
            this.viewType = viewType;
        }

        /**
         * Factory method for AppIcon AdapterItem
         */
        public static AdapterItem asApp(AppInfo appInfo) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_ICON);
            item.itemInfo = appInfo;
            return item;
        }

        public static AdapterItem asAppWithDecorationInfo(AppInfo appInfo,
                SectionDecorationInfo decorationInfo, boolean isPrivateSpaceApp) {
            AdapterItem item = new AdapterItem(isPrivateSpaceApp ? VIEW_TYPE_PRIVATE_SPACE_APP_ICON
                    : VIEW_TYPE_ICON);
            item.itemInfo = appInfo;
            item.decorationInfo = decorationInfo;
            return item;
        }

        public static AdapterItem asFolder(AllAppsFolderInfo folderInfo) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_FOLDER_ICON);
            item.folderInfo = folderInfo;
            return item;
        }

        public static AdapterItem asSearchAction(CharSequence title, CharSequence subtitle,
                int iconRes, Intent intent) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_SEARCH_ACTION);
            item.searchActionTitle = title;
            item.searchActionSubtitle = subtitle;
            item.searchActionIconRes = iconRes;
            item.searchActionIntent = intent;
            return item;
        }

        public static AdapterItem asSearchAction(CharSequence title, CharSequence subtitle,
                Drawable icon, Intent intent) {
            return asSearchAction(title, subtitle, icon, intent, false);
        }

        public static AdapterItem asSearchAction(CharSequence title, CharSequence subtitle,
                Drawable icon, Intent intent, boolean iconFullBleed) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_SEARCH_ACTION);
            item.searchActionTitle = title;
            item.searchActionSubtitle = subtitle;
            item.searchActionIcon = icon;
            item.searchActionIconRes = R.drawable.ic_allapps_search;
            item.searchActionIconTinted = false;
            item.searchActionIconFullBleed = iconFullBleed;
            item.searchActionIntent = intent;
            return item;
        }

        public static AdapterItem asSearchPill(CharSequence title, int iconRes, Intent intent) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_SEARCH_PILL);
            item.searchActionTitle = title;
            item.searchActionIconRes = iconRes;
            item.searchActionIntent = intent;
            return item;
        }

        public static AdapterItem asSearchPill(CharSequence title, Drawable icon, Intent intent) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_SEARCH_PILL);
            item.searchActionTitle = title;
            item.searchActionIcon = icon;
            item.searchActionIconRes = R.drawable.ic_allapps_search;
            item.searchActionIconTinted = icon == null;
            item.searchActionIntent = intent;
            return item;
        }

        public static AdapterItem asSearchHistory(String query) {
            AdapterItem item = asSearchAction(query, null, R.drawable.ic_allapps_search, null);
            item.searchActionQuery = query;
            item.searchActionEndIconRes = R.drawable.ic_search_north_west;
            return item;
        }

        public static AdapterItem asSearchSection(CharSequence title, int iconRes) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_SEARCH_SECTION);
            item.searchActionTitle = title;
            item.searchActionIconRes = iconRes;
            item.searchActionEndIconRes = 0;
            return item;
        }

        public static AdapterItem asSearchSection(CharSequence title, Drawable icon,
                int fallbackIconRes) {
            AdapterItem item = asSearchSection(title, fallbackIconRes);
            item.searchActionIcon = icon;
            item.searchActionIconTinted = icon == null;
            return item;
        }

        protected boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON || viewType == VIEW_TYPE_FOLDER_ICON;
        }

        /**
         * Returns true if the items represent the same object
         */
        public boolean isSameAs(AdapterItem other) {
            if (other.viewType != viewType || other.getClass() != getClass()) {
                return false;
            }
            if (viewType == VIEW_TYPE_FOLDER_ICON) {
                return Objects.equals(folderInfo, other.folderInfo);
            }
            if (viewType == VIEW_TYPE_SEARCH_ACTION || viewType == VIEW_TYPE_SEARCH_PILL
                    || viewType == VIEW_TYPE_SEARCH_SECTION) {
                return searchActionIconRes == other.searchActionIconRes
                        && searchActionIconTinted == other.searchActionIconTinted
                        && searchActionIconFullBleed == other.searchActionIconFullBleed
                        && searchActionThumbnailTrailing == other.searchActionThumbnailTrailing
                        && searchActionEndIconRes == other.searchActionEndIconRes
                        && searchActionGroupPosition == other.searchActionGroupPosition
                        && Objects.equals(searchActionTitle, other.searchActionTitle)
                        && Objects.equals(searchActionSubtitle, other.searchActionSubtitle)
                        && Objects.equals(getSearchActionIntentKey(searchActionIntent),
                                getSearchActionIntentKey(other.searchActionIntent))
                        && Objects.equals(getSearchActionShortcutKey(searchActionShortcut),
                                getSearchActionShortcutKey(other.searchActionShortcut))
                        && Objects.equals(searchActionQuery, other.searchActionQuery);
            }
            return true;
        }

        /**
         * This is called only if {@link #isSameAs} returns true to check if the contents are same
         * as well. Returning true will prevent redrawing of thee item.
         */
        public boolean isContentSame(AdapterItem other) {
            if (viewType == VIEW_TYPE_FOLDER_ICON) {
                return Objects.equals(folderInfo, other.folderInfo);
            }
            if (viewType == VIEW_TYPE_SEARCH_ACTION || viewType == VIEW_TYPE_SEARCH_PILL
                    || viewType == VIEW_TYPE_SEARCH_SECTION) {
                return isSameAs(other);
            }
            return itemInfo == null && other.itemInfo == null;
        }

        private static String getSearchActionIntentKey(Intent intent) {
            return intent == null ? null : intent.toUri(0);
        }

        private static String getSearchActionShortcutKey(ShortcutInfo shortcutInfo) {
            return shortcutInfo == null ? null : shortcutInfo.getPackage() + "/"
                    + shortcutInfo.getId() + "/" + shortcutInfo.getUserHandle();
        }

        @Nullable
        public SectionDecorationInfo getDecorationInfo() {
            return decorationInfo;
        }

        /** Sets the alpha of the decorator for this item. */
        protected void setDecorationFillAlpha(int alpha) {
            if (decorationInfo == null || decorationInfo.getDecorationHandler() == null) {
                return;
            }
            decorationInfo.getDecorationHandler().setFillAlpha(alpha);
        }
    }

    protected final ActivityContext mActivityContext;
    protected final AlphabeticalAppsList mApps;
    // The text to show when there are no search results and no market search handler.
    protected int mAppsPerRow;

    protected final LayoutInflater mLayoutInflater;
    protected final OnClickListener mOnIconClickListener;
    protected final OnLongClickListener mOnIconLongClickListener;
    protected OnFocusChangeListener mIconFocusListener;

    public BaseAllAppsAdapter(ActivityContext activityContext, LayoutInflater inflater,
            AlphabeticalAppsList apps, SearchAdapterProvider<?> adapterProvider) {
        mActivityContext = activityContext;
        mApps = apps;
        mLayoutInflater = inflater;

        mOnIconClickListener = mActivityContext.getItemOnClickListener();
        mOnIconLongClickListener = mActivityContext.getAllAppsItemLongClickListener();

        mAdapterProvider = adapterProvider;
    }

    /** Checks if the passed viewType represents all apps divider. */
    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    /** Checks if the passed viewType represents all apps icon. */
    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    /** Checks if the passed viewType represents private space header. */
    public static boolean isPrivateSpaceHeaderView(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER);
    }

    /** Checks if the passed viewType represents private space system apps divider. */
    public static boolean isPrivateSpaceSysAppsDividerView(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_PRIVATE_SPACE_SYS_APPS_DIVIDER);
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Returns the layout manager.
     */
    public abstract RecyclerView.LayoutManager getLayoutManager();

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                return new ViewHolder(getIconOnCreateSetup(parent));
            case VIEW_TYPE_FOLDER_ICON:
                return AxAllAppsFolderAdapter.onCreateViewHolder(mActivityContext, parent);
            case VIEW_TYPE_PRIVATE_SPACE_APP_ICON:
                BubbleTextView icon = getIconOnCreateSetup(parent);
                icon.setOnClickListener(v ->
                        PopupContainerWithArrow.showForPrivateSpaceApp(icon));
                icon.setOnLongClickListener(v -> {
                    PopupContainerWithArrow.showForPrivateSpaceApp(icon);
                    return true;
                });
                return new ViewHolder(icon);
            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case VIEW_TYPE_ALL_APPS_DIVIDER, VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.private_space_divider, parent, false));
            case VIEW_TYPE_WORK_EDU_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_edu, parent, false));
            case VIEW_TYPE_WORK_DISABLED_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_paused, parent, false));
            case VIEW_TYPE_PRIVATE_SPACE_HEADER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.private_space_header, parent, false));
            case VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO:
                return new ViewHolder(new View(mActivityContext.asContext()));
            default:
                if (mAdapterProvider.isViewSupported(viewType)) {
                    return mAdapterProvider.onCreateViewHolder(mLayoutInflater, parent, viewType);
                }
                throw new RuntimeException("Unexpected view type" + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.itemView.setVisibility(View.VISIBLE);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_FOLDER_ICON:
                AxAllAppsFolderAdapter.onBindViewHolder(mActivityContext, mApps,
                        mIconFocusListener, holder, position);
                break;
            case VIEW_TYPE_PRIVATE_SPACE_APP_ICON:
            case VIEW_TYPE_ICON: {
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                icon.reset();
                icon.applyFromApplicationInfo(adapterItem.itemInfo);
                icon.setOnFocusChangeListener(mIconFocusListener);
                icon.configureMinimalPopup(
                        holder.getItemViewType() == VIEW_TYPE_PRIVATE_SPACE_APP_ICON);
                PrivateProfileManager privateProfileManager = mApps.getPrivateProfileManager();
                if (privateProfileManager != null) {
                    // Set the alpha of the private space icon to 0 upon expanding the header so the
                    // alpha can animate -> 1. This should only be in effect when doing a
                    // transitioning between Locked/Unlocked state.
                    boolean isPrivateSpaceItem =
                            privateProfileManager.isPrivateSpaceItem(adapterItem);
                    if (icon.getAlpha() == 0 || icon.getAlpha() == 1) {
                        icon.setAlpha(isPrivateSpaceItem
                                && privateProfileManager.isStateTransitioning()
                                && (privateProfileManager.isScrolling() ||
                                    privateProfileManager.getReadyToAnimate())
                                && privateProfileManager.getCurrentState() == STATE_ENABLED
                                ? 0 : 1);
                        Log.d(TAG, "onBindViewHolder: "
                                + "isPrivateSpaceItem: " + isPrivateSpaceItem
                        + " isStateTransitioning: " + privateProfileManager.isStateTransitioning()
                        + " isScrolling: " + privateProfileManager.isScrolling()
                        + " readyToAnimate: " + privateProfileManager.getReadyToAnimate()
                        + " currentState: " + privateProfileManager.getCurrentState()
                        + " currentAlpha: " + icon.getAlpha());
                    }
                    // Views can still be bounded before the app list is updated hence showing icons
                    // after collapsing.
                    if (privateProfileManager.getCurrentState() == STATE_DISABLED
                            && isPrivateSpaceItem) {
                        adapterItem.decorationInfo = null;
                        icon.setVisibility(GONE);
                    }
                }
                break;
            }
            case VIEW_TYPE_EMPTY_SEARCH: {
                AppInfo info = mApps.getAdapterItems().get(position).itemInfo;
                if (info != null) {
                    ((TextView) holder.itemView).setText(mActivityContext.asContext().getString(
                            R.string.all_apps_no_search_results, info.title));
                }
                break;
            }
            case VIEW_TYPE_PRIVATE_SPACE_HEADER:
                RelativeLayout psHeaderLayout = holder.itemView.findViewById(
                        R.id.ps_header_layout);
                mApps.getPrivateProfileManager().bindPrivateSpaceHeaderViewElements(psHeaderLayout);
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                int roundRegions = ROUND_TOP_LEFT | ROUND_TOP_RIGHT;
                if (mApps.getPrivateProfileManager().getCurrentState() == STATE_DISABLED) {
                    roundRegions |= (ROUND_BOTTOM_LEFT | ROUND_BOTTOM_RIGHT);
                }
                adapterItem.decorationInfo =
                        new SectionDecorationInfo(mActivityContext.asContext(), roundRegions);
                break;
            case VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER:
                adapterItem = mApps.getAdapterItems().get(position);
                adapterItem.decorationInfo =
                        mApps.getPrivateProfileManager().getCurrentState() == STATE_DISABLED
                                ? null
                                : new SectionDecorationInfo(
                                        mActivityContext.asContext(), ROUND_NOTHING);
                break;
            case VIEW_TYPE_BOTTOM_VIEW_TO_SCROLL_TO:
            case VIEW_TYPE_ALL_APPS_DIVIDER:
            case VIEW_TYPE_WORK_DISABLED_CARD:
                // nothing to do
                break;
            case VIEW_TYPE_WORK_EDU_CARD:
                ((WorkEduCard) holder.itemView).setPosition(position);
                break;
            default:
                if (mAdapterProvider.isViewSupported(holder.getItemViewType())) {
                    mAdapterProvider.onBindView(holder, position);
                }
        }
    }

    private BubbleTextView getIconOnCreateSetup(ViewGroup parent) {
        int layout = mActivityContext.getDeviceProfile().inv.enableTwoLinesInAllApps
                ? R.layout.all_apps_icon_twoline : R.layout.all_apps_icon;
        BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                layout, parent, false);
        icon.setLongPressTimeoutFactor(1f);
        icon.setOnFocusChangeListener(mIconFocusListener);
        icon.setOnClickListener(v -> {
            AxSearchHistory.recordCurrentQuery(mActivityContext);
            mOnIconClickListener.onClick(v);
        });
        icon.setOnLongClickListener(mOnIconLongClickListener);
        // Ensure the all apps icon height matches the workspace icons in portrait mode.
        icon.getLayoutParams().height =
                mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx();
        return icon;
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    protected static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

}
