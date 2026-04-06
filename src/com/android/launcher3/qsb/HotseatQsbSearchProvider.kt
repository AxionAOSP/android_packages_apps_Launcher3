/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.launcher3.qsb

import android.content.Context
import android.net.Uri
import android.provider.Settings

object HotseatQsbSearchProvider {
    const val KEY: String = "hotseat_qsb_search_provider"
    const val DEFAULT: String = "none"

    @JvmStatic
    fun getUri(): Uri = Settings.Secure.getUriFor(KEY)

    @JvmStatic
    fun get(context: Context): String =
        Settings.Secure.getString(context.contentResolver, KEY) ?: DEFAULT
}
