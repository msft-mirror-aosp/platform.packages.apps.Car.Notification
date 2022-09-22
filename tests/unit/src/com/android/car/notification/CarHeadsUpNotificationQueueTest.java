/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackListener;
import android.content.ComponentName;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;

import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.PriorityQueue;

@RunWith(AndroidJUnit4.class)
public class CarHeadsUpNotificationQueueTest {
    private CarHeadsUpNotificationQueue mCarHeadsUpNotificationQueue;

    @Mock
    private CarHeadsUpNotificationQueue.CarHeadsUpNotificationQueueCallback
            mCarHeadsUpNotificationQueueCallback;
    @Mock
    private NotificationListenerService.RankingMap mRankingMap;
    @Mock
    private ActivityTaskManager mActivityTaskManager;

    @Captor
    private ArgumentCaptor<TaskStackListener> mTaskStackListenerArg;
    @Captor
    private ArgumentCaptor<AlertEntry> mAlertEntryArg;
    @Captor
    private ArgumentCaptor<Notification> mNotificationArg;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());

    private static final String PKG_1 = "PKG_1";
    private static final String PKG_2 = "PKG_2";
    private static final String CHANNEL_ID = "CHANNEL_ID";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        // To add elements to the queue rather than displaying immediately
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>(Collections.singletonList(mock(AlertEntry.class))));
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
    }

    private CarHeadsUpNotificationQueue createCarHeadsUpNotificationQueue(
            @Nullable ActivityTaskManager activityTaskManager,
            @Nullable NotificationManager notificationManager,
            CarHeadsUpNotificationQueue.CarHeadsUpNotificationQueueCallback callback) {
        return new CarHeadsUpNotificationQueue(mContext,
                activityTaskManager != null ? activityTaskManager
                        : ActivityTaskManager.getInstance(),
                notificationManager != null ? notificationManager
                        : mContext.getSystemService(NotificationManager.class),
                callback);
    }

    @Test
    public void addToQueue_prioritises_postTimeOfHeadsUp() {
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 4000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), 2000);
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), 3000);
        AlertEntry alertEntry4 = new AlertEntry(generateMockStatusBarNotification(
                "key4", "msg"), 5000);
        AlertEntry alertEntry5 = new AlertEntry(generateMockStatusBarNotification(
                "key5", "msg"), 1000);

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry2, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry3, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry4, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry5, mRankingMap);

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.poll()).isEqualTo("key5");
        assertThat(result.poll()).isEqualTo("key2");
        assertThat(result.poll()).isEqualTo("key3");
        assertThat(result.poll()).isEqualTo("key1");
        assertThat(result.poll()).isEqualTo("key4");
    }

    @Test
    public void addToQueue_prioritises_categoriesOfHeadsUp() {
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_category_immediate_show, /* value= */ new String[0]);
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_category_priority, /* value= */ new String[]{
                        "car_emergency", "navigation", "call", "msg"});
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "navigation"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "car_emergency"), 1000);
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), 1000);
        AlertEntry alertEntry4 = new AlertEntry(generateMockStatusBarNotification(
                "key4", "call"), 1000);
        AlertEntry alertEntry5 = new AlertEntry(generateMockStatusBarNotification(
                "key5", "msg"), 1000);

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry2, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry3, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry4, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry5, mRankingMap);

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.poll()).isEqualTo("key2");
        assertThat(result.poll()).isEqualTo("key1");
        assertThat(result.poll()).isEqualTo("key4");
        assertThat(result.poll()).isEqualTo("key3");
        assertThat(result.poll()).isEqualTo("key5");
    }

    @Test
    public void addToQueue_prioritises_internalCategoryAsLeastPriority() {
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_category_immediate_show, /* value= */ new String[0]);
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_category_priority, /* value= */ new String[]{"msg"});
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "HUN_QUEUE_INTERNAL"), 1000);
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), 1000);
        AlertEntry alertEntry4 = new AlertEntry(generateMockStatusBarNotification(
                "key4", "msg"), 1000);

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry2, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry3, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry4, mRankingMap);

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        assertThat(result.size()).isEqualTo(4);
        assertThat(result.poll()).isNotEqualTo("key2");
        assertThat(result.poll()).isNotEqualTo("key2");
        assertThat(result.poll()).isNotEqualTo("key2");
        assertThat(result.poll()).isEqualTo("key2");
    }

    @Test
    public void addToQueue_merges_newHeadsUpWithSameKey() {
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), 2000);
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 3000);

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry2, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry3, mRankingMap);

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.poll()).isEqualTo("key1");
        assertThat(result.poll()).isEqualTo("key2");
    }

    @Test
    public void addToQueue_shows_immediateShowHeadsUp() {
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_category_immediate_show, /* value= */
                new String[]{"car_emergency"});
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "car_emergency"), 2000);
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), 3000);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>(Collections.singletonList(alertEntry3)));

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry2, mRankingMap);

        verify(mCarHeadsUpNotificationQueueCallback).dismissHeadsUp(alertEntry3);
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(mAlertEntryArg.capture(),
                        any(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key2");
    }

    @Test
    public void addToQueue_handles_notificationWithNoCategory() {
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", /* category= */ null), 4000);

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.poll()).isEqualTo("key1");
    }

    @Test
    public void triggerCallback_expireNotifications_whenParked() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_parked_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.setActiveUxRestriction(false); // car is parked
        Instant now = Instant.now();
        mCarHeadsUpNotificationQueue.setClock(Clock.fixed(now, ZoneId.systemDefault()));
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), now.minusMillis(3000).toEpochMilli());
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), now.minusMillis(500).toEpochMilli());
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), now.toEpochMilli());
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry3);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        verify(mCarHeadsUpNotificationQueueCallback)
                .removedFromHeadsUpQueue(mAlertEntryArg.capture());
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(mAlertEntryArg.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key2");
        assertThat(result.contains("key3")).isTrue();
    }

    @Test
    public void triggerCallback_doesNot_expireNotifications_whenParked() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_driving_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.setActiveUxRestriction(false); // car is parked
        Instant now = Instant.now();
        mCarHeadsUpNotificationQueue.setClock(Clock.fixed(now, ZoneId.systemDefault()));
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), now.minusMillis(3000).toEpochMilli());
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), now.minusMillis(500).toEpochMilli());
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), now.toEpochMilli());
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry3);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(mAlertEntryArg.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
        assertThat(result.contains("key2")).isTrue();
        assertThat(result.contains("key3")).isTrue();
    }

    @Test
    public void triggerCallback_expireNotifications_whenDriving() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenDriving, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_driving_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.setActiveUxRestriction(true); // car is driving
        Instant now = Instant.now();
        mCarHeadsUpNotificationQueue.setClock(Clock.fixed(now, ZoneId.systemDefault()));
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), now.minusMillis(3000).toEpochMilli());
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), now.minusMillis(500).toEpochMilli());
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), now.toEpochMilli());
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry3);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        verify(mCarHeadsUpNotificationQueueCallback)
                .removedFromHeadsUpQueue(mAlertEntryArg.capture());
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(mAlertEntryArg.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key2");
        assertThat(result.contains("key3")).isTrue();
    }

    @Test
    public void triggerCallback_doesNot_expireNotifications_forInternalCategory() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_parked_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.setActiveUxRestriction(false); // car is parked
        Instant now = Instant.now();
        mCarHeadsUpNotificationQueue.setClock(Clock.fixed(now, ZoneId.systemDefault()));
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "HUN_QUEUE_INTERNAL"), now.minusMillis(3000).toEpochMilli());
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());

        mCarHeadsUpNotificationQueue.triggerCallback();

        verify(mCarHeadsUpNotificationQueueCallback, times(0))
                .removedFromHeadsUpQueue(mAlertEntryArg.capture());
    }

    @Test
    public void triggerCallback_setHunExpiredFlagToTrue_onHunExpired() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_parked_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.setActiveUxRestriction(false); // car is parked
        Instant now = Instant.now();
        mCarHeadsUpNotificationQueue.setClock(Clock.fixed(now, ZoneId.systemDefault()));
        AlertEntry alertEntry_expired = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), now.minusMillis(3000).toEpochMilli());
        AlertEntry alertEntry_notExpired = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), now.minusMillis(500).toEpochMilli());
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry_expired);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry_notExpired);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());

        mCarHeadsUpNotificationQueue.triggerCallback();

        verify(mCarHeadsUpNotificationQueueCallback).removedFromHeadsUpQueue(any(AlertEntry.class));
        assertThat(mCarHeadsUpNotificationQueue.mAreNotificationsExpired).isTrue();
    }

    @Test
    public void triggerCallback_setHunExpiredFlagToFalse_onHunExpiredAndEmptyQueue() {
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());
        mCarHeadsUpNotificationQueue.mAreNotificationsExpired = true;

        mCarHeadsUpNotificationQueue.triggerCallback();

        assertThat(mCarHeadsUpNotificationQueue.mAreNotificationsExpired).isFalse();
    }

    @Test
    public void triggerCallback_setHunRemovalFlagToTrue_onHunExpiredAndEmptyQueue() {
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());
        mCarHeadsUpNotificationQueue.mAreNotificationsExpired = true;

        mCarHeadsUpNotificationQueue.triggerCallback();

        assertThat(mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange).isTrue();
    }

    @Test
    public void triggerCallback_sendsNotificationToCurrentUser_onHunExpiredAndEmptyQueue() {
        NotificationManager notificationManager = mock(NotificationManager.class);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null, notificationManager,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.mAreNotificationsExpired = true;

        mCarHeadsUpNotificationQueue.triggerCallback();

        verify(notificationManager).notifyAsUser(anyString(), anyInt(), mNotificationArg.capture(),
                eq(UserHandle.CURRENT));
        assertThat(mNotificationArg.getValue().category).isEqualTo("HUN_QUEUE_INTERNAL");
    }

    @Test
    public void triggerCallback_doesNot_expireNotifications_whenDriving() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenDriving, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_driving_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null,
                /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        mCarHeadsUpNotificationQueue.setActiveUxRestriction(true); // car is driving
        Instant now = Instant.now();
        mCarHeadsUpNotificationQueue.setClock(Clock.fixed(now, ZoneId.systemDefault()));
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), now.minusMillis(3000).toEpochMilli());
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), now.minusMillis(500).toEpochMilli());
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), now.toEpochMilli());
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry3);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(mAlertEntryArg.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
        assertThat(result.contains("key2")).isTrue();
        assertThat(result.contains("key3")).isTrue();
    }

    @Test
    public void triggerCallback_doesNot_showNotifications_whenAllowlistAppsAreInForeground()
            throws RemoteException {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_throttled_foreground_packages, /* value= */ new String[]{PKG_1});
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mActivityTaskManager, /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        verify(mActivityTaskManager).registerTaskStackListener(mTaskStackListenerArg.capture());
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), /* postTime= */ 1000);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());
        ActivityManager.RunningTaskInfo mockRunningTaskInfo =
                generateRunningTaskInfo(PKG_1, /* displayAreaFeatureId= */ 111);

        mTaskStackListenerArg.getValue().onTaskMovedToFront(mockRunningTaskInfo);
        mCarHeadsUpNotificationQueue.triggerCallback();

        verify(mCarHeadsUpNotificationQueueCallback, never()).showAsHeadsUp(
                any(AlertEntry.class), nullable(NotificationListenerService.RankingMap.class));
    }

    @Test
    public void triggerCallback_does_showNotifications_whenAllowlistAppsAreNotInForeground()
            throws RemoteException {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_throttled_foreground_packages, /* value= */ new String[]{PKG_1});
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mActivityTaskManager, /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        verify(mActivityTaskManager).registerTaskStackListener(mTaskStackListenerArg.capture());
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), /* postTime= */ 1000);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());
        ActivityManager.RunningTaskInfo mockRunningTaskInfo =
                generateRunningTaskInfo(PKG_2, /* displayAreaFeatureId= */ 111);

        mTaskStackListenerArg.getValue().onTaskMovedToFront(mockRunningTaskInfo);
        mCarHeadsUpNotificationQueue.triggerCallback();

        verify(mCarHeadsUpNotificationQueueCallback).showAsHeadsUp(
                mAlertEntryArg.capture(), nullable(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
    }

    @Test
    public void triggerCallback_called_whenNonAllowlistAppsComesToForeground()
            throws RemoteException {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.array.headsup_throttled_foreground_packages, /* value= */ new String[]{PKG_1});
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mActivityTaskManager, /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        verify(mActivityTaskManager).registerTaskStackListener(mTaskStackListenerArg.capture());
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), /* postTime= */ 1000);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>());
        ActivityManager.RunningTaskInfo mockRunningTaskInfo_AllowlistPkg =
                generateRunningTaskInfo(PKG_1, /* displayAreaFeatureId= */ 111);
        ActivityManager.RunningTaskInfo mockRunningTaskInfo_nonAllowlistPkg =
                generateRunningTaskInfo(PKG_2, /* displayAreaFeatureId= */ 111);

        mTaskStackListenerArg.getValue().onTaskMovedToFront(mockRunningTaskInfo_AllowlistPkg);
        mTaskStackListenerArg.getValue().onTaskMovedToFront(mockRunningTaskInfo_nonAllowlistPkg);

        verify(mCarHeadsUpNotificationQueueCallback).showAsHeadsUp(
                mAlertEntryArg.capture(), nullable(NotificationListenerService.RankingMap.class));
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
    }

    @Test
    public void removeFromQueue_returnsFalse_whenNotificationNotInQueue() {
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), 2000);
        AlertEntry alertEntryNotAddedToQueue = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), 3000);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);

        boolean result = mCarHeadsUpNotificationQueue.removeFromQueue(alertEntryNotAddedToQueue);

        assertThat(result).isFalse();
    }

    @Test
    public void removeFromQueue_returnsTrue_whenNotificationInQueue() {
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), 2000);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);

        boolean result = mCarHeadsUpNotificationQueue.removeFromQueue(alertEntry2);

        assertThat(result).isTrue();
    }

    @Test
    public void releaseQueue_removes_notificationsFromQueue() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_dismissHeadsUpWhenNotificationCenterOpens, /* value= */ false);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mActivityTaskManager, /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), /* postTime= */ 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "msg"), /* postTime= */ 2000);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry1);
        mCarHeadsUpNotificationQueue.addToPriorityQueue(alertEntry2);

        mCarHeadsUpNotificationQueue.releaseQueue();

        verify(mCarHeadsUpNotificationQueueCallback, times(2)).removedFromHeadsUpQueue(
                mAlertEntryArg.capture());
        assertThat(mAlertEntryArg.getAllValues().size()).isEqualTo(2);
    }

    @Test
    public void releaseQueue_dismiss_activeHUNs() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_dismissHeadsUpWhenNotificationCenterOpens, /* value= */ true);
        AlertEntry alertEntry = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), /* postTime= */ 1000);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>(Collections.singletonList(alertEntry)));
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mActivityTaskManager, /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);

        mCarHeadsUpNotificationQueue.releaseQueue();

        verify(mCarHeadsUpNotificationQueueCallback).dismissHeadsUp(mAlertEntryArg.capture());
        assertThat(mAlertEntryArg.getValue().getKey()).isEqualTo("key1");
    }

    @Test
    public void releaseQueue_doesNot_dismiss_nonDismissibleHUNs() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_dismissHeadsUpWhenNotificationCenterOpens, /* value= */ true);
        AlertEntry alertEntry = new AlertEntry(
                generateMockStatusBarNotification("key1", Notification.CATEGORY_CALL,
                        /* isOngoing= */ true, /* hasFullScreenIntent= */ true),
                /* postTime= */ 1000);
        when(mCarHeadsUpNotificationQueueCallback.getActiveHeadsUpNotifications()).thenReturn(
                new ArrayList<>(Collections.singletonList(alertEntry)));
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mActivityTaskManager, /* notificationManager= */ null,
                mCarHeadsUpNotificationQueueCallback);

        mCarHeadsUpNotificationQueue.releaseQueue();

        verify(mCarHeadsUpNotificationQueueCallback, times(0)).removedFromHeadsUpQueue(any());
    }

    @Test
    public void onStateChange_internalCategory_hunRemovalFlagTrue_setHunRemovalFlagToFalse() {
        AlertEntry alertEntry = new AlertEntry(generateMockStatusBarNotification(
                "key1", "HUN_QUEUE_INTERNAL"), 1000);
        mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange = true;

        mCarHeadsUpNotificationQueue.onStateChange(alertEntry, /* isHeadsUp= */ false);

        assertThat(mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange).isFalse();
    }

    @Test
    public void onStateChange_internalCategory_hunRemovalFlagTrue_setHunExpiredToFalse() {
        AlertEntry alertEntry = new AlertEntry(generateMockStatusBarNotification(
                "key1", "HUN_QUEUE_INTERNAL"), 1000);
        mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange = true;

        mCarHeadsUpNotificationQueue.onStateChange(alertEntry, /* isHeadsUp= */ false);

        assertThat(mCarHeadsUpNotificationQueue.mAreNotificationsExpired).isFalse();
    }

    @Test
    public void onStateChange_internalCategory_hunRemovalFlagTrue_cancelHunForCurrentUser() {
        NotificationManager notificationManager = mock(NotificationManager.class);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null, notificationManager,
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry = new AlertEntry(generateMockStatusBarNotification(
                "key1", "HUN_QUEUE_INTERNAL"), 1000);
        mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange = true;

        mCarHeadsUpNotificationQueue.onStateChange(alertEntry, /* isHeadsUp= */ false);

        verify(notificationManager).cancelAsUser(anyString(), eq(/* id= */ 2000),
                eq(UserHandle.CURRENT));
    }

    @Test
    public void onStateChange_notInternalCategory_hunRemovalFlagTrue_notCancelHunForCurrentUser() {
        NotificationManager notificationManager = mock(NotificationManager.class);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                /* activityTaskManager= */ null, notificationManager,
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange = true;

        mCarHeadsUpNotificationQueue.onStateChange(alertEntry, /* isHeadsUp= */ false);

        verify(notificationManager, times(0)).cancelAsUser(anyString(), anyInt(),
                any(UserHandle.class));
    }

    @Test
    public void onStateChange_notInternalCategory_hunRemovalFlagTrue_doesNotsetHunRemovalFlag() {
        AlertEntry alertEntry = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange = true;

        mCarHeadsUpNotificationQueue.onStateChange(alertEntry, /* isHeadsUp= */ false);

        assertThat(mCarHeadsUpNotificationQueue.mCancelInternalNotificationOnStateChange).isTrue();
    }

    private StatusBarNotification generateMockStatusBarNotification(String key, String category) {
        return generateMockStatusBarNotification(key, category,
                /* isOngoing= */ false, /* hasFullScreenIntent= */ false);
    }

    private StatusBarNotification generateMockStatusBarNotification(String key, String category,
            boolean isOngoing, boolean hasFullScreenIntent) {
        StatusBarNotification sbn = mock(StatusBarNotification.class);
        Notification.Builder notificationBuilder = new Notification.Builder(mContext,
                CHANNEL_ID).setCategory(category).setOngoing(isOngoing);
        if (hasFullScreenIntent) {
            notificationBuilder.setFullScreenIntent(mock(PendingIntent.class),
                    /* highPriority= */ true);
        }
        when(sbn.getNotification()).thenReturn(notificationBuilder.build());
        when(sbn.getKey()).thenReturn(key);
        return sbn;
    }

    private ActivityManager.RunningTaskInfo generateRunningTaskInfo(String pkg,
            int displayAreaFeatureId) {
        ComponentName componentName = mock(ComponentName.class);
        when(componentName.getPackageName()).thenReturn(pkg);
        ActivityManager.RunningTaskInfo mockRunningTaskInfo = mock(
                ActivityManager.RunningTaskInfo.class);
        mockRunningTaskInfo.baseActivity = componentName;
        mockRunningTaskInfo.displayAreaFeatureId = displayAreaFeatureId;
        return mockRunningTaskInfo;
    }

}
