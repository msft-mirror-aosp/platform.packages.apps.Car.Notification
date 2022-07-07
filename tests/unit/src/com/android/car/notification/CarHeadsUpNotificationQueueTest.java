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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.PriorityQueue;

@RunWith(AndroidJUnit4.class)
public class CarHeadsUpNotificationQueueTest {
    private CarHeadsUpNotificationQueue mCarHeadsUpNotificationQueue;
    @Mock
    private CarHeadsUpNotificationQueue.CarHeadsUpNotificationQueueCallback
            mCarHeadsUpNotificationQueueCallback;
    @Mock
    private NotificationListenerService.RankingMap mRankingMap;
    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());

    private static final String CHANNEL_ID = "CHANNEL_ID";

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        // To add elements to the queue rather than displaying immediately
        when(mCarHeadsUpNotificationQueueCallback.isHunActive()).thenReturn(true);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
                mCarHeadsUpNotificationQueueCallback);
    }

    private CarHeadsUpNotificationQueue createCarHeadsUpNotificationQueue(
            CarHeadsUpNotificationQueue.CarHeadsUpNotificationQueueCallback callback) {
        return new CarHeadsUpNotificationQueue(mContext,
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
                mCarHeadsUpNotificationQueueCallback);
        AlertEntry alertEntry1 = new AlertEntry(generateMockStatusBarNotification(
                "key1", "msg"), 1000);
        AlertEntry alertEntry2 = new AlertEntry(generateMockStatusBarNotification(
                "key2", "car_emergency"), 2000);
        AlertEntry alertEntry3 = new AlertEntry(generateMockStatusBarNotification(
                "key3", "msg"), 3000);

        mCarHeadsUpNotificationQueue.addToQueue(alertEntry1, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry2, mRankingMap);
        mCarHeadsUpNotificationQueue.addToQueue(alertEntry3, mRankingMap);

        verify(mCarHeadsUpNotificationQueueCallback)
                .dismissAllActiveHeadsUp();
        ArgumentCaptor<AlertEntry> argument = ArgumentCaptor.forClass(AlertEntry.class);
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(argument.capture(),
                        any(NotificationListenerService.RankingMap.class));
        assertThat(argument.getValue().getKey()).isEqualTo("key2");

    }

    @Test
    public void triggerCallback_expireNotifications_whenParked() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_parked_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
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
        when(mCarHeadsUpNotificationQueueCallback.isHunActive()).thenReturn(false);

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        ArgumentCaptor<AlertEntry> argument = ArgumentCaptor.forClass(AlertEntry.class);
        verify(mCarHeadsUpNotificationQueueCallback)
                .removedFromHeadsUpQueue(argument.capture());
        assertThat(argument.getValue().getKey()).isEqualTo("key1");
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(argument.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(argument.getValue().getKey()).isEqualTo("key2");
        assertThat(result.contains("key3")).isTrue();
    }

    @Test
    public void triggerCallback_doesNot_expireNotifications_whenParked() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenParked, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_driving_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
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
        when(mCarHeadsUpNotificationQueueCallback.isHunActive()).thenReturn(false);

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        ArgumentCaptor<AlertEntry> argument = ArgumentCaptor.forClass(AlertEntry.class);
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(argument.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(argument.getValue().getKey()).isEqualTo("key1");
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
        when(mCarHeadsUpNotificationQueueCallback.isHunActive()).thenReturn(false);

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        ArgumentCaptor<AlertEntry> argument = ArgumentCaptor.forClass(AlertEntry.class);
        verify(mCarHeadsUpNotificationQueueCallback)
                .removedFromHeadsUpQueue(argument.capture());
        assertThat(argument.getValue().getKey()).isEqualTo("key1");
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(argument.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(argument.getValue().getKey()).isEqualTo("key2");
        assertThat(result.contains("key3")).isTrue();
    }

    @Test
    public void triggerCallback_doesNot_expireNotifications_whenDriving() {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_expireHeadsUpWhenDriving, /* value= */ false);
        mContext.getOrCreateTestableResources().addOverride(
                R.integer.headsup_queue_expire_driving_duration_ms, /* value= */ 1000);
        mCarHeadsUpNotificationQueue = createCarHeadsUpNotificationQueue(
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
        when(mCarHeadsUpNotificationQueueCallback.isHunActive()).thenReturn(false);

        mCarHeadsUpNotificationQueue.triggerCallback();

        PriorityQueue<String> result = mCarHeadsUpNotificationQueue.getPriorityQueue();
        ArgumentCaptor<AlertEntry> argument = ArgumentCaptor.forClass(AlertEntry.class);
        verify(mCarHeadsUpNotificationQueueCallback)
                .showAsHeadsUp(argument.capture(),
                        nullable(NotificationListenerService.RankingMap.class));
        assertThat(argument.getValue().getKey()).isEqualTo("key1");
        assertThat(result.contains("key2")).isTrue();
        assertThat(result.contains("key3")).isTrue();
    }

    private StatusBarNotification generateMockStatusBarNotification(String key, String category) {
        StatusBarNotification sbn = mock(StatusBarNotification.class);
        Notification.Builder notificationBuilder = new Notification.Builder(mContext,
                CHANNEL_ID).setCategory(category);
        when(sbn.getNotification()).thenReturn(notificationBuilder.build());
        when(sbn.getKey()).thenReturn(key);
        return sbn;
    }
}
