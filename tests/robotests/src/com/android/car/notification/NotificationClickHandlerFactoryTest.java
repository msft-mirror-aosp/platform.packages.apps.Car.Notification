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
 * limitations under the License
 */

package com.android.car.notification;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import com.android.internal.statusbar.IStatusBarService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class NotificationClickHandlerFactoryTest {

    private Context mContext;
    private NotificationClickHandlerFactory mNotificationClickHandlerFactory;

    @Mock
    private IStatusBarService mBarService;
    @Mock
    private AlertEntry mAlertEntry1;
    @Mock
    private AlertEntry mAlertEntry2;
    @Mock
    private NotificationClickHandlerFactory.OnNotificationClickListener mListener1;
    @Mock
    private NotificationClickHandlerFactory.OnNotificationClickListener mListener2;
    @Mock
    private View mView;
    @Mock
    private Notification mNotification;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mNotificationClickHandlerFactory = new NotificationClickHandlerFactory(mBarService);
        mNotification.contentIntent = PendingIntent.getForegroundService(mContext, 0, new Intent(),
                0);
        when(mAlertEntry1.getNotification()).thenReturn(mNotification);
        when(mAlertEntry2.getNotification()).thenReturn(mNotification);
    }

    @Test
    public void onNotificationClicked_twoListenersRegistered_invokesTwoHandlerCallbacks() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener2);
        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener2).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
    }

    @Test
    public void onNotificationClicked_oneListenersUnregistered_invokesRegisteredCallback() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener2);
        mNotificationClickHandlerFactory.unregisterClickListener(mListener2);

        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener2, never()).onNotificationClicked(ActivityManager.START_SUCCESS,
                mAlertEntry1);
    }

    @Test
    public void onNotificationClicked_duplicateListenersRegistered_invokesCallbackOnce() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1).onClick(mView);

        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
    }

    @Test
    public void onNotificationClicked_twoAlertEntriesSubscribe_passesTheRightAlertEntry() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        View.OnClickListener clickListener1 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1);
        View.OnClickListener clickListener2 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry2);

        clickListener2.onClick(mView);

        verify(mListener1, never())
                .onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry2);
    }

    @Test
    public void onNotificationClicked_multipleListenersAndAes_passesTheRightAeToAllListeners() {
        mNotificationClickHandlerFactory.registerClickListener(mListener1);
        mNotificationClickHandlerFactory.registerClickListener(mListener2);
        View.OnClickListener clickListener1 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry1);
        View.OnClickListener clickListener2 =
                mNotificationClickHandlerFactory.getClickHandler(mAlertEntry2);

        clickListener2.onClick(mView);

        verify(mListener1, never())
                .onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener1).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry2);

        verify(mListener2, never())
                .onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry1);
        verify(mListener2).onNotificationClicked(ActivityManager.START_SUCCESS, mAlertEntry2);
    }
}
