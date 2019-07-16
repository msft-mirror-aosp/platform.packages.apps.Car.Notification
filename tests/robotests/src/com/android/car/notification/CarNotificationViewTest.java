/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.notification;

import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CarNotificationViewTest {

    private CarNotificationView mCarNotificationView;
    @Mock
    private NotificationClickHandlerFactory mClickHandlerFactory;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Context context = ApplicationProvider.getApplicationContext();
        FrameLayout frameLayout = new FrameLayout(context);
        LayoutInflater.from(context)
                .inflate(R.layout.test_car_notification_view_layout, frameLayout);
        mCarNotificationView = frameLayout.findViewById(R.id.notification_view);
        mCarNotificationView.setClickHandlerFactory(mClickHandlerFactory);
    }

    @Test
    public void onClickClearAllButton_callsFactoryClearAllMethod() {
        Button clearAllButton = mCarNotificationView.findViewById(R.id.clear_all_button);

        clearAllButton.callOnClick();

        verify(mClickHandlerFactory).clearAllNotifications();
    }
}