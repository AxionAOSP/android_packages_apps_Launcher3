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
package com.android.launcher3;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AxWorkspaceGridDb {

    private static final String PREFIX = "launcher_axion_";
    private static final String SUFFIX = ".db";
    private static final String GRID_SEPARATOR = "_by_";
    private static final String HOTSEAT_SEPARATOR = "_h";

    private AxWorkspaceGridDb() { }

    public static String getFileName(int columns, int rows, int hotseatIcons) {
        return String.format(Locale.ENGLISH, "%s%d%s%d%s%d%s", PREFIX, columns, GRID_SEPARATOR,
                rows, HOTSEAT_SEPARATOR, hotseatIcons, SUFFIX);
    }

    public static List<String> getGridDbFiles(Context context, List<String> defaultFiles) {
        ArrayList<String> files = new ArrayList<>(defaultFiles);
        String[] databaseFiles = context.databaseList();
        if (databaseFiles == null) {
            return files;
        }
        for (String file : databaseFiles) {
            if (isFileName(file) && !files.contains(file)) {
                files.add(file);
            }
        }
        return files;
    }

    public static boolean isFileName(String fileName) {
        if (TextUtils.isEmpty(fileName) || !fileName.startsWith(PREFIX)
                || !fileName.endsWith(SUFFIX)) {
            return false;
        }

        String body = fileName.substring(PREFIX.length(), fileName.length() - SUFFIX.length());
        int hotseatIndex = body.lastIndexOf(HOTSEAT_SEPARATOR);
        if (hotseatIndex <= 0 || hotseatIndex == body.length() - HOTSEAT_SEPARATOR.length()) {
            return false;
        }

        String grid = body.substring(0, hotseatIndex);
        String hotseat = body.substring(hotseatIndex + HOTSEAT_SEPARATOR.length());
        int gridIndex = grid.indexOf(GRID_SEPARATOR);
        if (gridIndex <= 0 || gridIndex == grid.length() - GRID_SEPARATOR.length()) {
            return false;
        }

        return isValidSize(grid.substring(0, gridIndex))
                && isValidSize(grid.substring(gridIndex + GRID_SEPARATOR.length()))
                && isValidSize(hotseat);
    }

    public static boolean isValidGridSize(int size) {
        return size >= AxWorkspaceDisplayPrefs.MIN_GRID_SIZE
                && size <= AxWorkspaceDisplayPrefs.MAX_TABLET_GRID_SIZE;
    }

    private static boolean isValidSize(String value) {
        try {
            return isValidGridSize(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
