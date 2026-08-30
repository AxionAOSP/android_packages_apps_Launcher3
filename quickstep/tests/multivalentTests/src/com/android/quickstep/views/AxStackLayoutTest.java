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
package com.android.quickstep.views;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class AxStackLayoutTest {
    @Test
    public void getTransform_normalTaskCrossingIntoStack_keepsOriginalPosition() {
        AxStackLayout layout = new AxStackLayout();
        AxStackLayout.Transform transform = new AxStackLayout.Transform();

        layout.getTransform(0.001f, 0.1f, 1.1f, -0.01f, 100f, true, false, transform);

        assertThat(transform.scale).isEqualTo(1f);
        assertThat(transform.primaryTranslation).isEqualTo(-1.1f);
        assertThat(transform.alpha).isEqualTo(1f);
        assertThat(transform.iconAlpha).isEqualTo(1f);
    }

    @Test
    public void getTransform_stackTaskCrossingIntoNormal_keepsOriginalPosition() {
        AxStackLayout layout = new AxStackLayout();
        AxStackLayout.Transform transform = new AxStackLayout.Transform();

        layout.getTransform(-0.001f, -0.1f, -1.1f, 0.01f, 100f, true, false, transform);

        assertThat(transform.scale).isGreaterThan(1f);
        assertThat(transform.primaryTranslation).isEqualTo(0f);
        assertThat(transform.alpha).isEqualTo(1f);
        assertThat(transform.iconAlpha).isEqualTo(0f);
    }

    @Test
    public void getTransform_aboveFocusedTask_scalesLarger() {
        AxStackLayout layout = new AxStackLayout();
        AxStackLayout.Transform transform = new AxStackLayout.Transform();

        layout.getTransform(-1f, -100f, 0f, 1f, 100f, true, false, transform);

        assertThat(transform.scale).isEqualTo(1.04f);
        assertThat(transform.iconAlpha).isEqualTo(0f);
    }

    @Test
    public void getTransform_behindFocusedTask_keepsExistingScale() {
        AxStackLayout layout = new AxStackLayout();
        AxStackLayout.Transform transform = new AxStackLayout.Transform();

        layout.getTransform(1f, 100f, 0f, 1f, 100f, true, false, transform);

        assertThat(transform.scale).isEqualTo(0.96f);
    }

}
