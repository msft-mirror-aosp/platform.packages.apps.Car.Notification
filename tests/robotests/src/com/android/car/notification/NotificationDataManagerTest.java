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

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import com.android.car.notification.testutils.ShadowCarAssistUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarNotificationRobolectricTestRunner.class)
@Config(shadows = {ShadowCarAssistUtils.class})
public class NotificationDataManagerTest {

    private static final String PKG_1 = "package_1";
    private static final String PKG_2 = "package_2";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final long POST_TIME = 12345l;
    private static final UserHandle USER_HANDLE = new UserHandle(12);

    private StatusBarNotification mMessageNotification;
    private StatusBarNotification mNonMessageNotification;

    private NotificationDataManager mNotificationDataManager;

    @Before
    public void setup() {
        Context mContext = RuntimeEnvironment.application;
        Notification.Builder mNotificationBuilder1 = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE);
        Notification.Builder mNotificationBuilder2 = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE);

        mMessageNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNonMessageNotification = new StatusBarNotification(PKG_2, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder2.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);

        ShadowCarAssistUtils.addMessageNotification(mMessageNotification.getKey());
        mNotificationDataManager = new NotificationDataManager();
    }

    @After
    public void tearDown() {
        mNotificationDataManager = null;
        ShadowCarAssistUtils.reset();
    }

    @Test
    public void addNewNotification_newNonMessageNotification_doesNothing() {
        mNotificationDataManager.addNewMessageNotification(mNonMessageNotification);

        assertThat(mNotificationDataManager.isMessageNotificationMuted(mNonMessageNotification))
                .isFalse();
    }

    @Test
    public void addNewNotification_notificationExists_muteStateNotUpdated() {
        mNotificationDataManager.addNewMessageNotification(mMessageNotification);
        mNotificationDataManager.toggleMute(mMessageNotification);

        mNotificationDataManager.addNewMessageNotification(mMessageNotification);
        assertThat(mNotificationDataManager.isMessageNotificationMuted(mMessageNotification))
                .isTrue();
    }

    @Test
    public void toggleMute_nonMessagingNotification_doesNothing() {
        mNotificationDataManager.addNewMessageNotification(mNonMessageNotification);
        mNotificationDataManager.toggleMute(mNonMessageNotification);

        assertThat(mNotificationDataManager.isMessageNotificationMuted(mNonMessageNotification))
                .isFalse();
    }

    @Test
    public void toggleMute_messagingNotification_togglesMuteState() {
        mNotificationDataManager.addNewMessageNotification(mMessageNotification);
        mNotificationDataManager.toggleMute(mMessageNotification);

        assertThat(mNotificationDataManager.isMessageNotificationMuted(mMessageNotification))
                .isTrue();
    }

    @Test
    public void toggleMute_notAddedNotification_doesNothing() {
        mNotificationDataManager.toggleMute(mNonMessageNotification);
        assertThat(mNotificationDataManager.isMessageNotificationMuted(mNonMessageNotification))
                .isFalse();
    }

    @Test
    public void toggleMute_notAddedMessageNotification_doesNothing() {
        mNotificationDataManager.toggleMute(mMessageNotification);
        assertThat(mNotificationDataManager.isMessageNotificationMuted(mMessageNotification))
                .isFalse();
    }
}
