/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS;

import android.app.contextualsearch.ContextualSearchManager;
import android.content.Context;

import com.android.launcher3.R;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.Utilities;

/** Utilities to work with Assistant functionality. */
public class AssistUtils {

    private static final int[] SYS_UI_ASSIST_OVERRIDE_INVOCATION_TYPES = {
        INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS
    };

    private static AssistUtils sInstance;

    private Context mContext;
    private ContextualSearchManager mContextualSearchManager;

    public AssistUtils(Context context) {
        mContext = context.getApplicationContext();
        mContextualSearchManager = (ContextualSearchManager) mContext.getSystemService(Context.CONTEXTUAL_SEARCH_SERVICE);
    }

    /** Creates AssistUtils as specified by overrides */
    public static synchronized AssistUtils newInstance(Context context) {
        if (sInstance == null) {
            sInstance = new AssistUtils(context);
        }
        return sInstance;
    }

    /** @return Array of AssistUtils.INVOCATION_TYPE_* that we want to handle instead of SysUI. */
    public int[] getSysUiAssistOverrideInvocationTypes() {
        return SYS_UI_ASSIST_OVERRIDE_INVOCATION_TYPES;
    }

    /**
     * @return {@code true} if the override was handled, i.e. an assist surface was shown or the
     * request should be ignored. {@code false} means the caller should start assist another way.
     */
    public boolean tryStartAssistOverride(int invocationType) {
        if (invocationType != INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS) {
            return false;
        }
        return Utilities.startContextualSearch(mContext, ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME);
    }
}
