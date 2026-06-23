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
package com.android.launcher3.allapps;

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SMART_DRAWER_CATEGORY;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SMART_DRAWER_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_SMART_DRAWER_ROW;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

public final class AxSmartDrawerAdapter {

    public static final int SMART_CARD_COLUMNS = 2;
    public static final int EXPANDED_APP_COLUMNS = 4;

    private static final int CARD_RADIUS_DP = 28;
    private static final int CARD_PADDING_DP = 16;
    private static final int CARD_GAP_DP = 12;
    private static final int BIG_ICON_DP = 64;
    private static final int SMALL_ICON_DP = 28;

    private AxSmartDrawerAdapter() { }

    public static BaseAllAppsAdapter.ViewHolder onCreateViewHolder(
            ActivityContext activityContext, ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SMART_DRAWER_HEADER) {
            return new BaseAllAppsAdapter.ViewHolder(new HeaderView(activityContext, parent));
        }
        return new BaseAllAppsAdapter.ViewHolder(new CardView(activityContext, parent,
                viewType == VIEW_TYPE_SMART_DRAWER_ROW));
    }

    public static void onBindViewHolder(ActivityContext activityContext, AlphabeticalAppsList apps,
            @Nullable View.OnFocusChangeListener focusListener,
            BaseAllAppsAdapter.ViewHolder holder, int position) {
        AdapterItem item = apps.getAdapterItems().get(position);
        if (holder.itemView instanceof HeaderView headerView) {
            headerView.bind(apps, item.smartDrawerInfo);
        } else if (holder.itemView instanceof CardView cardView) {
            cardView.bind(activityContext, apps, item.smartDrawerInfo, focusListener);
        }
    }

    public static boolean isSmartDrawerViewType(int viewType) {
        return viewType == VIEW_TYPE_SMART_DRAWER_ROW
                || viewType == VIEW_TYPE_SMART_DRAWER_CATEGORY
                || viewType == VIEW_TYPE_SMART_DRAWER_HEADER;
    }

    public static boolean isSmartDrawerCategoryViewType(int viewType) {
        return viewType == VIEW_TYPE_SMART_DRAWER_CATEGORY;
    }

    public static int getItemsPerRow(int viewType) {
        return viewType == VIEW_TYPE_SMART_DRAWER_CATEGORY ? SMART_CARD_COLUMNS : 1;
    }

    private static final class HeaderView extends LinearLayout {

        private final TextView mTitleView;
        private final TextView mCountView;
        private final int mTextColor;
        private final int mSubtextColor;

        HeaderView(ActivityContext activityContext, ViewGroup parent) {
            super(activityContext.asContext());
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            int margin = dp(parent, 6);
            int paddingHorizontal = dp(parent, 10);
            int paddingVertical = dp(parent, 8);
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(margin, margin, margin, margin);
            setLayoutParams(layoutParams);
            mTextColor = Themes.getAttrColor(getContext(), android.R.attr.textColorPrimary);
            mSubtextColor = Themes.getAttrColor(getContext(), android.R.attr.textColorSecondary);

            ImageButton back = new ImageButton(getContext());
            back.setImageResource(R.drawable.ic_arrow_back);
            back.setColorFilter(mTextColor);
            back.setContentDescription(getContext().getString(
                    R.string.smart_drawer_back_to_categories));
            back.setBackground(createBackground(getContext().getColor(android.R.color.transparent),
                    dp(parent, 20)));
            LayoutParams backLp = new LayoutParams(dp(parent, 40), dp(parent, 40));
            addView(back, backLp);
            back.setOnClickListener(v -> activityContext.getAppsView().getActiveRecyclerView()
                    .getApps().collapseSmartDrawerCategory());

            LinearLayout textContainer = new LinearLayout(getContext());
            textContainer.setOrientation(VERTICAL);
            LayoutParams textLp = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textLp.leftMargin = dp(parent, 8);
            addView(textContainer, textLp);

            mTitleView = new TextView(getContext());
            mTitleView.setTextColor(mTextColor);
            mTitleView.setTextSize(24);
            mTitleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            textContainer.addView(mTitleView);

            mCountView = new TextView(getContext());
            mCountView.setTextColor(mSubtextColor);
            mCountView.setTextSize(13);
            textContainer.addView(mCountView);
        }

        void bind(AlphabeticalAppsList apps, AxSmartDrawerCategory category) {
            mTitleView.setText(category.getTitle());
            mCountView.setText(getResources().getQuantityString(
                    R.plurals.all_apps_folder_app_count, category.getApps().size(),
                    category.getApps().size()));
            setOnClickListener(v -> apps.collapseSmartDrawerCategory());
            animateIn(this, 0);
        }
    }

    private static final class CardView extends LinearLayout {

        private final TextView mTitleView;
        private final LinearLayout mPreviewContainer;
        private final boolean mRowCard;

        CardView(ActivityContext activityContext, ViewGroup parent, boolean rowCard) {
            super(activityContext.asContext());
            mRowCard = rowCard;
            setOrientation(VERTICAL);
            int margin = dp(parent, 6);
            int padding = dp(parent, CARD_PADDING_DP);
            setPadding(padding, padding, padding, padding);
            setBackground(createBackground(Themes.getAttrColor(getContext(),
                    R.attr.allappsHeaderProtectionColor), dp(parent, CARD_RADIUS_DP)));
            setClipToOutline(true);
            setMinimumHeight(dp(parent, rowCard ? 132 : 196));
            RecyclerView.LayoutParams layoutParams = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.setMargins(margin, margin, margin, margin);
            setLayoutParams(layoutParams);

            mTitleView = new TextView(getContext());
            mTitleView.setTextColor(Themes.getAttrColor(getContext(), android.R.attr.textColorPrimary));
            mTitleView.setTextSize(14);
            mTitleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            addView(mTitleView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            mPreviewContainer = new LinearLayout(getContext());
            mPreviewContainer.setOrientation(rowCard ? HORIZONTAL : VERTICAL);
            mPreviewContainer.setGravity(rowCard ? Gravity.CENTER_VERTICAL
                    : Gravity.CENTER_HORIZONTAL);
            LayoutParams previewLp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            previewLp.topMargin = dp(parent, 12);
            addView(mPreviewContainer, previewLp);
        }

        void bind(ActivityContext activityContext, AlphabeticalAppsList apps,
                AxSmartDrawerCategory category, @Nullable View.OnFocusChangeListener focusListener) {
            mTitleView.setText(category.getTitle());
            mPreviewContainer.removeAllViews();
            if (mRowCard) {
                bindRow(activityContext, category.getApps(), focusListener);
                setOnClickListener(null);
            } else {
                View.OnClickListener expandClickListener =
                        v -> apps.expandSmartDrawerCategory(category);
                bindCategory(activityContext, category.getApps(), focusListener,
                        expandClickListener);
                setOnClickListener(null);
                setClickable(false);
            }
            setContentDescription(category.getTitle());
            if (mRowCard) {
                animateIn(this, 0);
            }
        }

        private void bindRow(ActivityContext activityContext, List<AppInfo> apps,
                @Nullable View.OnFocusChangeListener focusListener) {
            int gap = dp(this, CARD_GAP_DP);
            int size = dp(this, BIG_ICON_DP);
            for (int i = 0; i < Math.min(apps.size(), 4); i++) {
                BubbleTextView icon = createIcon(activityContext, apps.get(i), size,
                        focusListener, true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                if (i != 0) {
                    lp.leftMargin = gap;
                }
                mPreviewContainer.addView(icon, lp);
            }
        }

        private void bindCategory(ActivityContext activityContext, List<AppInfo> apps,
                @Nullable View.OnFocusChangeListener focusListener,
                View.OnClickListener expandClickListener) {
            int gap = dp(this, CARD_GAP_DP);
            int cell = dp(this, BIG_ICON_DP);
            int stride = cell + gap;
            FrameLayout grid = new FrameLayout(getContext());
            grid.setOnClickListener(expandClickListener);
            mPreviewContainer.setOnClickListener(expandClickListener);
            mTitleView.setOnClickListener(expandClickListener);
            LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                    cell * 2 + gap, cell * 2 + gap);
            mPreviewContainer.addView(grid, gridLp);
            int count = Math.min(apps.size(), 4);
            for (int i = 0; i < count; i++) {
                View icon = apps.size() > 4 && i == 3
                        ? createCluster(activityContext, apps.subList(3, Math.min(apps.size(), 7)),
                                expandClickListener)
                        : createIcon(activityContext, apps.get(i), cell, focusListener, true);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cell, cell);
                lp.leftMargin = (i % 2) * stride;
                lp.topMargin = (i / 2) * stride;
                grid.addView(icon, lp);
            }
        }

        private View createCluster(ActivityContext activityContext, List<AppInfo> apps,
                View.OnClickListener expandClickListener) {
            FrameLayout container = new FrameLayout(getContext());
            container.setClickable(true);
            container.setFocusable(true);
            container.setOnClickListener(expandClickListener);
            FrameLayout grid = new FrameLayout(getContext());
            int gap = dp(this, 4);
            int iconSize = dp(this, SMALL_ICON_DP);
            int gridSize = iconSize * 2 + gap;
            FrameLayout.LayoutParams gridLp = new FrameLayout.LayoutParams(
                    gridSize, gridSize, Gravity.CENTER);
            container.addView(grid, gridLp);
            for (int i = 0; i < Math.min(apps.size(), 4); i++) {
                BubbleTextView icon = createIcon(activityContext, apps.get(i), iconSize, null,
                        false);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconSize, iconSize);
                lp.leftMargin = (i % 2) * (iconSize + gap);
                lp.topMargin = (i / 2) * (iconSize + gap);
                grid.addView(icon, lp);
            }
            return container;
        }

        private BubbleTextView createIcon(ActivityContext activityContext, AppInfo app, int size,
                @Nullable View.OnFocusChangeListener focusListener, boolean interactive) {
            int layout = size <= dp(this, SMALL_ICON_DP)
                    ? R.layout.all_apps_smart_drawer_icon_small
                    : R.layout.all_apps_smart_drawer_icon;
            BubbleTextView icon = (BubbleTextView) LayoutInflater.from(getContext()).inflate(
                    layout, this, false);
            icon.reset();
            icon.applyFromApplicationInfo(app);
            icon.setCenterVertically(false);
            icon.setCompoundDrawablePadding(0);
            icon.setTextVisibility(false);
            int verticalPadding = Math.max(0, (size - icon.getIconSize()) / 2);
            icon.setPadding(0, verticalPadding, 0, 0);
            if (interactive) {
                icon.setOnClickListener(activityContext.getItemOnClickListener());
                icon.setOnLongClickListener(activityContext.getAllAppsItemLongClickListener());
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(focusListener);
            } else {
                icon.setClickable(false);
                icon.setLongClickable(false);
                icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            icon.setLayoutParams(new ViewGroup.LayoutParams(size, size));
            return icon;
        }
    }

    private static void animateIn(View view, long delay) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setStartDelay(delay)
                .start();
    }

    private static GradientDrawable createBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
