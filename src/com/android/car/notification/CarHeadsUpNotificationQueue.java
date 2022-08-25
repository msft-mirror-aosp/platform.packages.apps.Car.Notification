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

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;

import androidx.annotation.AnyThread;

import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Queue for throttling heads up notifications.
 */
public class CarHeadsUpNotificationQueue implements
        CarHeadsUpNotificationManager.OnHeadsUpNotificationStateChange {
    private final PriorityQueue<String> mPriorityQueue;
    private final ActivityTaskManager mActivityTaskManager;
    private final CarHeadsUpNotificationQueueCallback mQueueCallback;
    private final TaskStackListener mTaskStackListener;
    private final long mNotificationExpirationTimeFromQueueWhenDriving;
    private final long mNotificationExpirationTimeFromQueueWhenParked;
    private final boolean mExpireHeadsUpWhileDriving;
    private final boolean mExpireHeadsUpWhileParked;
    private final Set<String> mNotificationCategoriesForImmediateShow;
    private final Set<String> mPackagesToThrottleHeadsUp;
    private final Map<String, AlertEntry> mKeyToAlertEntryMap;
    private final Set<Integer> mThrottledDisplays;
    private NotificationListenerService.RankingMap mRankingMap;
    private Clock mClock;
    private boolean mIsActiveUxRestriction;

    public CarHeadsUpNotificationQueue(Context context, ActivityTaskManager activityTaskManager,
            CarHeadsUpNotificationQueueCallback queuePopCallback) {
        mActivityTaskManager = activityTaskManager;
        mQueueCallback = queuePopCallback;
        mKeyToAlertEntryMap = new HashMap<>();
        mThrottledDisplays = new HashSet<>();

        mExpireHeadsUpWhileDriving = context.getResources().getBoolean(
                R.bool.config_expireHeadsUpWhenDriving);
        mExpireHeadsUpWhileParked = context.getResources().getBoolean(
                R.bool.config_expireHeadsUpWhenParked);
        mNotificationExpirationTimeFromQueueWhenDriving = context.getResources().getInteger(
                R.integer.headsup_queue_expire_driving_duration_ms);
        mNotificationExpirationTimeFromQueueWhenParked = context.getResources().getInteger(
                R.integer.headsup_queue_expire_parked_duration_ms);
        mNotificationCategoriesForImmediateShow = Set.of(context.getResources().getStringArray(
                R.array.headsup_category_immediate_show));
        mPackagesToThrottleHeadsUp = Set.of(context.getResources().getStringArray(
                R.array.headsup_throttled_foreground_packages));

        mPriorityQueue = new PriorityQueue<>(
                new PrioritisedNotifications(context.getResources().getStringArray(
                        R.array.headsup_category_priority), mKeyToAlertEntryMap));

        mClock = Clock.systemUTC();

        mTaskStackListener = new TaskStackListener() {
            @Override
            public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
                    throws RemoteException {
                super.onTaskMovedToFront(taskInfo);
                if (taskInfo.baseActivity == null) {
                    return;
                }
                if (mPackagesToThrottleHeadsUp.contains(taskInfo.baseActivity.getPackageName())) {
                    mThrottledDisplays.add(taskInfo.displayAreaFeatureId);
                } else {
                    mThrottledDisplays.remove(taskInfo.displayAreaFeatureId);
                    triggerCallback();
                }
            }
        };
        mActivityTaskManager.registerTaskStackListener(mTaskStackListener);
    }

    /**
     * Adds an {@link AlertEntry} into the queue.
     */
    public void addToQueue(AlertEntry alertEntry,
            NotificationListenerService.RankingMap rankingMap) {
        mRankingMap = rankingMap;
        if (isCategoryImmediateShow(alertEntry.getNotification().category)) {
            mQueueCallback.dismissAllActiveHeadsUp();
            mQueueCallback.showAsHeadsUp(alertEntry, rankingMap);
            return;
        }
        boolean headsUpExistsInQueue = mKeyToAlertEntryMap.containsKey(alertEntry.getKey());
        mKeyToAlertEntryMap.put(alertEntry.getKey(), alertEntry);
        if (!headsUpExistsInQueue) {
            mPriorityQueue.add(alertEntry.getKey());
        }
        triggerCallback();
    }

    /**
     * Triggers {@code CarHeadsUpNotificationQueueCallback.showAsHeadsUp} on non expired HUN and
     * {@code CarHeadsUpNotificationQueueCallback.removedFromHeadsUpQueue} for expired HUN if
     * there are no active HUNs.
     */
    @VisibleForTesting
    void triggerCallback() {
        if (mQueueCallback.isHunActive()) {
            return;
        }

        if (!mThrottledDisplays.isEmpty()) {
            return;
        }

        AlertEntry alertEntry;
        do {
            if (mPriorityQueue.isEmpty()) {
                return;
            }
            String key = mPriorityQueue.poll();
            alertEntry = mKeyToAlertEntryMap.get(key);
            mKeyToAlertEntryMap.remove(key);

            if (alertEntry == null) {
                continue;
            }

            long timeElapsed = mClock.millis() - alertEntry.getPostTime();
            boolean isExpired = (mIsActiveUxRestriction && mExpireHeadsUpWhileDriving
                    && mNotificationExpirationTimeFromQueueWhenDriving < timeElapsed) || (
                    !mIsActiveUxRestriction && mExpireHeadsUpWhileParked
                            && mNotificationExpirationTimeFromQueueWhenParked < timeElapsed);

            if (isExpired) {
                mQueueCallback.removedFromHeadsUpQueue(alertEntry);
                alertEntry = null;
            }
        } while (alertEntry == null);
        mQueueCallback.showAsHeadsUp(alertEntry, mRankingMap);
    }

    /**
     * Returns {@code true} if the {@code category} should be shown immediately.
     */
    private boolean isCategoryImmediateShow(@Nullable String category) {
        return category != null && mNotificationCategoriesForImmediateShow.contains(category);
    }

    @Override
    public void onStateChange(AlertEntry alertEntry, boolean isHeadsUp) {
        if (!isHeadsUp) {
            triggerCallback();
        }
    }

    /**
     * Called when distraction optimisation state changes.
     * {@link CarUxRestrictionsManager} can be used to get this state.
     */
    public void setActiveUxRestriction(boolean isActiveUxRestriction) {
        mIsActiveUxRestriction = isActiveUxRestriction;
    }

    /**
     * Unregisters all listeners.
     */
    public void unregisterListeners() {
        mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
    }

    /**
     * Callback to communicate status of HUN.
     */
    public interface CarHeadsUpNotificationQueueCallback {
        /**
         * Show the AlertEntry as HUN.
         */
        @AnyThread
        void showAsHeadsUp(AlertEntry alertEntry,
                NotificationListenerService.RankingMap rankingMap);

        /**
         * AlertEntry removed from the queue without being shown as HUN.
         */
        void removedFromHeadsUpQueue(AlertEntry alertEntry);

        /**
         * Dismiss all active HUNs.
         */
        void dismissAllActiveHeadsUp();

        /**
         * Returns {@code true} if there are active HUNs.
         */
        boolean isHunActive();
    }

    /**
     * Used to assign priority for {@link AlertEntry} based on category and postTime.
     */
    private static class PrioritisedNotifications implements Comparator<String> {
        private final String[] mNotificationsCategoryInPriorityOrder;
        private final Map<String, AlertEntry> mKeyToAlertEntryMap;

        PrioritisedNotifications(String[] notificationsCategoryInPriorityOrder,
                Map<String, AlertEntry> mapKeyToAlertEntry) {
            mNotificationsCategoryInPriorityOrder = notificationsCategoryInPriorityOrder;
            mKeyToAlertEntryMap = mapKeyToAlertEntry;
        }

        public int compare(String aKey, String bKey) {
            AlertEntry a = mKeyToAlertEntryMap.get(aKey);
            AlertEntry b = mKeyToAlertEntryMap.get(bKey);
            if (a == null || b == null) {
                return 0;
            }
            int priorityA = -1;
            int priorityB = -1;

            for (int i = 0; i < mNotificationsCategoryInPriorityOrder.length; i++) {
                if (mNotificationsCategoryInPriorityOrder[i].equals(a.getNotification().category)) {
                    priorityA = i;
                }
                if (mNotificationsCategoryInPriorityOrder[i].equals(b.getNotification().category)) {
                    priorityB = i;
                }
            }
            if (priorityA != priorityB) {
                return Integer.compare(priorityA, priorityB);
            } else {
                return Long.compare(a.getPostTime(), b.getPostTime());
            }
        }
    }

    @VisibleForTesting
    PriorityQueue<String> getPriorityQueue() {
        return mPriorityQueue;
    }

    @VisibleForTesting
    void addToPriorityQueue(AlertEntry alertEntry) {
        mKeyToAlertEntryMap.put(alertEntry.getKey(), alertEntry);
        mPriorityQueue.add(alertEntry.getKey());
    }

    @VisibleForTesting
    void setClock(Clock clock) {
        mClock = clock;
    }
}
