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

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.settings.preference.RestartPreference;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SettingsCache;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Settings activity for Launcher.
 */
public class SettingsUISwitcher extends CollapsingToolbarBaseActivity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback {

    public static final String EXTRA_FRAGMENT_ARGS = ":settings:fragment_args";

    // Intent extra to indicate the pref-key to highlighted when opening the settings activity
    public static final String EXTRA_FRAGMENT_HIGHLIGHT_KEY = ":settings:fragment_args_key";
    // Intent extra to indicate the pref-key of the root screen when opening the settings activity
    public static final String EXTRA_FRAGMENT_ROOT_KEY = ARG_PREFERENCE_ROOT;

    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        Intent intent = getIntent();

        if (savedInstanceState == null) {
            Bundle args = intent.getBundleExtra(EXTRA_FRAGMENT_ARGS);
            if (args == null) {
                args = new Bundle();
            }

            String highlight = intent.getStringExtra(EXTRA_FRAGMENT_HIGHLIGHT_KEY);
            if (!TextUtils.isEmpty(highlight)) {
                args.putString(EXTRA_FRAGMENT_HIGHLIGHT_KEY, highlight);
            }
            String root = intent.getStringExtra(EXTRA_FRAGMENT_ROOT_KEY);
            if (!TextUtils.isEmpty(root)) {
                args.putString(EXTRA_FRAGMENT_ROOT_KEY, root);
            }

            final FragmentManager fm = getSupportFragmentManager();
            final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(),
                    getString(R.string.ui_switcher_settings_fragment_name));
            f.setArguments(args);
            // Display the fragment as the main content.
            fm.beginTransaction().replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame, f).commit();
        }
    }

    private boolean startPreference(String fragment, Bundle args, String key) {
        if (getSupportFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new preferences in that case.
            return false;
        }
        final FragmentManager fm = getSupportFragmentManager();
        final Fragment f = fm.getFragmentFactory().instantiate(getClassLoader(), fragment);
        if (f instanceof DialogFragment) {
            f.setArguments(args);
            ((DialogFragment) f).show(fm, key);
        } else {
            startActivity(new Intent(this, SettingsUISwitcher.class)
                    .putExtra(EXTRA_FRAGMENT_ARGS, args));
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragmentCompat preferenceFragment, Preference pref) {
        return startPreference(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(ARG_PREFERENCE_ROOT, pref.getKey());
        return startPreference(getString(R.string.ui_switcher_category_title), args, pref.getKey());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class UISwitcherSettingsFragment extends PreferenceFragmentCompat implements
            SettingsCache.OnChangeListener {

        private boolean mRestartOnResume = false;

        private String mHighLightKey;
        private boolean mPreferenceHighlighted = false;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();
            mHighLightKey = args == null ? null : args.getString(EXTRA_FRAGMENT_HIGHLIGHT_KEY);
            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.launcher_ui_switcher_preferences, rootKey);
            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            String currentLayout = prefs.getString("selected_ui_layout", "launcher_preferences");
            updateSummaries(getPreferenceScreen(), currentLayout);
            SwitchPreferenceCompat layoutSwitcher = findPreference("layout_switcher");
            if (layoutSwitcher != null) {
                layoutSwitcher.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isGridLayout = (Boolean) newValue;
                    prefs.edit().putBoolean("is_grid_layout", isGridLayout).apply();
                    RestartPreference restartPreference = (RestartPreference) findPreference("pref_restart");
                    if (restartPreference != null) {
                        restartPreference.performClick();
                    }
                    return true;
                });
            }
            String[] layoutKeys = getContext().getResources().getStringArray(R.array.ui_layout_keys);
            for (String key : layoutKeys) {
                Preference layoutPreference = findPreference(key);
                if (layoutPreference != null) {
                    layoutPreference.setOnPreferenceClickListener(preference -> {
                        prefs.edit().putString("selected_ui_layout", preference.getKey()).apply();
                        updateSummaries(getPreferenceScreen(), preference.getKey());
                        RestartPreference restartPreference = (RestartPreference) findPreference("pref_restart");
                        if (restartPreference != null) {
                            restartPreference.performClick();
                        }
                        return true;
                    });
                }
            }
        }

        private RestartPreference createRestartPreference() {
            RestartPreference restartPreference = new RestartPreference(getContext(), null);
            restartPreference.setKey("pref_restart");
            restartPreference.setTitle(getString(R.string.pref_restart_title));
            restartPreference.setSummary(getString(R.string.pref_restart_summary));
            restartPreference.setLayoutResource(R.layout.settings_layout_bottom);
            return restartPreference;
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            View listView = getListView();
            final int bottomPadding = listView.getPaddingBottom();
            listView.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        bottomPadding + insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            });

            // Overriding Text Direction in the Androidx preference library to support RTL
            view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        private void updateSummaries(PreferenceScreen preferenceScreen, String selectedKey) {
            for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
                Preference preference = preferenceScreen.getPreference(i);
                if (preference instanceof PreferenceCategory) {
                    PreferenceCategory category = (PreferenceCategory) preference;
                    for (int j = 0; j < category.getPreferenceCount(); j++) {
                        Preference subPreference = category.getPreference(j);
                        if (!TextUtils.isEmpty(preference.getSummary())) {
                            continue;
                        }
                        subPreference.setSummary(subPreference.getKey().equals(selectedKey) ?
                                getString(R.string.current_selection) : null);
                    }
                } else {
                    if (!TextUtils.isEmpty(preference.getSummary())) {
                        continue;
                    }
                    preference.setSummary(preference.getKey().equals(selectedKey) ?
                            getString(R.string.current_selection) : null);
                }
            }
        }

        @Override
        public void onResume() {
            super.onResume();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }

            if (mRestartOnResume) {
                recreateActivityNow();
            }
        }

        @Override
        public void onSettingsChanged(boolean isEnabled) {
            // Developer options changed, try recreate
            tryRecreateActivity();
        }

        /**
         * Tries to recreate the preference
         */
        protected void tryRecreateActivity() {
            if (isResumed()) {
                recreateActivityNow();
            } else {
                mRestartOnResume = true;
            }
        }

        private void recreateActivityNow() {
            Activity activity = getActivity();
            if (activity != null) {
                activity.recreate();
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(
                    list, position, screen.findPreference(mHighLightKey))
                    : null;
        }
    }
}
