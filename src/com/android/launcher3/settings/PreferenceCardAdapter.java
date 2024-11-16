/*
 * Copyright (C) 2024 The risingOS Android Project
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

package com.android.launcher3.settings;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.utils.WallpaperView;

public class PreferenceCardAdapter extends RecyclerView.Adapter<PreferenceCardAdapter.ViewHolder> {

    private final PreferenceScreen preferenceScreen;
    private final String selectedLayout;

    public PreferenceCardAdapter(PreferenceScreen preferenceScreen, Context context) {
        this.preferenceScreen = preferenceScreen;
        this.selectedLayout = (context == null)
            ? "launcher_preferences"
            : LauncherPrefs.SELECTED_UI_LAYOUT.get(context) != null
                ? LauncherPrefs.SELECTED_UI_LAYOUT.get(context)
                : "launcher_preferences";
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if ("launcher_preferences".equals(selectedLayout)) {
            Log.d("PreferenceCardAdapter", "Skipping adapter setup for launcher_preferences");
            return null;
        }
        if ("preference_card_rising".equals(selectedLayout)) {
            switch (viewType) {
                case 0:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_icons, parent, false);
                    break;
                case 1:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_home_screen, parent, false);
                    break;
                case 2:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_app_drawer, parent, false);
                    break;
                case 3:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_recents, parent, false);
                    break;
                case 4:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_misc, parent, false);
                    break;
                case 5:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_ui_switcher, parent, false);
                    break;
                case 6:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card_about, parent, false);
                    break;
                default:
                    view = LayoutInflater.from(parent.getContext()).inflate(R.layout.preference_card, parent, false);
                    break;
            }
        } else {
            int layoutId;
            switch (selectedLayout) {
                case "preference_card":
                    layoutId = R.layout.preference_card;
                    break;
                case "preference_card_wallpaperview":
                    layoutId = R.layout.preference_card_wallpaperview;
                    break;
                case "preference_card_smallicon":
                    layoutId = R.layout.preference_card_smallicon;
                    break;
                case "preference_card_left":
                    layoutId = R.layout.preference_card_left;
                    break;
                default:
                    layoutId = R.layout.preference_card;
                    break;
            }
            view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        }

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if ("launcher_preferences".equals(selectedLayout)) {
            return;
        }
        Preference preference = preferenceScreen.getPreference(position);

        if ("preference_card_rising".equals(selectedLayout)) {
            switch (preference.getKey()) {
                case "icons":
                    holder.title = holder.itemView.findViewById(R.id.title_icons);
                    break;

                case "home_screen":
                    holder.title = holder.itemView.findViewById(R.id.title_home);
                    holder.wallpaperPreview = holder.itemView.findViewById(R.id.wallpaper_preview_home);
                    break;

                case "app_drawer":
                    holder.title = holder.itemView.findViewById(R.id.title_app);
                    break;

                case "recents":
                    holder.title = holder.itemView.findViewById(R.id.title_recents);
                    break;

                case "miscellaneous":
                    holder.title = holder.itemView.findViewById(R.id.title_misc);
                    holder.bannerIcon = holder.itemView.findViewById(R.id.banner_icon_misc);
                    break;

                case "ui_switcher":
                    holder.title = holder.itemView.findViewById(R.id.title_ui);
                    holder.bannerIcon = holder.itemView.findViewById(R.id.banner_icon_ui);
                    break;

                case "about":
                    holder.title = holder.itemView.findViewById(R.id.title_about);
                    holder.bannerIcon = holder.itemView.findViewById(R.id.banner_icon_about);
                    break;
            }
        }

        if (holder.title != null) {
            holder.title.setText(preference.getTitle());
        }
        if (holder.summary != null) {
            holder.summary.setText(preference.getSummary());
        }
        if (holder.bannerIcon != null && preference.getIcon() != null) {
            holder.bannerIcon.setImageDrawable(preference.getIcon());
        }
        if (holder.wallpaperPreview != null) {
            holder.wallpaperPreview.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = preference.getIntent();
            if (intent != null) {
                v.getContext().startActivity(intent);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        String key = preferenceScreen.getPreference(position).getKey();
        switch (key) {
            case "icons":
                return 0;
            case "home_screen":
                return 1;
            case "app_drawer":
                return 2;
            case "recents":
                return 3;
            case "miscellaneous":
                return 4;
            case "ui_switcher":
                return 5;
            case "about":
                return 6;
            default:
                return -1;
        }
    }

    @Override
    public int getItemCount() {
        return preferenceScreen.getPreferenceCount();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView bannerIcon;
        TextView title;
        TextView summary;
        FrameLayout bannerContainer;
        WallpaperView wallpaperPreview;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            summary = itemView.findViewById(R.id.summary);
            bannerContainer = itemView.findViewById(R.id.banner_container);
            bannerIcon = itemView.findViewById(R.id.banner_icon);
            if (itemView.findViewById(R.id.wallpaper_preview) instanceof WallpaperView) {
                wallpaperPreview = itemView.findViewById(R.id.wallpaper_preview);
            }
        }
    }
}
