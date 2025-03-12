/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.car.drivingstate.CarUxRestrictions;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.List;

/**
 * This class is a bridge to collect signals from the notification and ux restriction services and
 * trigger the correct UI updates.
 */
public class NotificationViewController {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private static final String TAG = "NotificationViewControl";
    private final CarNotificationView mCarNotificationView;
    private final PreprocessingManager mPreprocessingManager;
    private final CarNotificationListener mCarNotificationListener;
    private final boolean mShowRecentsAndOlderHeaders;
    private final CarUxRestrictionManagerWrapper mUxResitrictionListener;
    private final NotificationDataManager mNotificationDataManager;
    private final NotificationUpdateHandler mNotificationUpdateHandler =
            new NotificationUpdateHandler();
    private boolean mIsVisible;

    public NotificationViewController(CarNotificationView carNotificationView,
            PreprocessingManager preprocessingManager,
            CarNotificationListener carNotificationListener,
            CarUxRestrictionManagerWrapper uxResitrictionListener) {
        mCarNotificationView = carNotificationView;
        mPreprocessingManager = preprocessingManager;
        mCarNotificationListener = carNotificationListener;
        mUxResitrictionListener = uxResitrictionListener;
        mNotificationDataManager = NotificationDataManager.getInstance();
        mShowRecentsAndOlderHeaders = mCarNotificationView.getContext()
                        .getResources().getBoolean(R.bool.config_showRecentAndOldHeaders);
        resetNotifications();
    }

    /**
     * Updates UI and registers required listeners
     */
    public void enable() {
        mCarNotificationListener.setHandler(mNotificationUpdateHandler);
        mUxResitrictionListener.setCarNotificationView(mCarNotificationView);
        try {
            CarUxRestrictions currentRestrictions =
                    mUxResitrictionListener.getCurrentCarUxRestrictions();
            mCarNotificationView.onUxRestrictionsChanged(currentRestrictions);
        } catch (RuntimeException e) {
            Log.e(TAG, "Car not connected", e);
        }
    }

    /**
     * Remove listeners.
     */
    public void disable() {
        mCarNotificationListener.setHandler(null);
        mUxResitrictionListener.setCarNotificationView(null);
    }

    /**
     * Called when the notification view's visibility is changed.
     */
    public void onVisibilityChanged(boolean isVisible) {
        mIsVisible = isVisible;
        mCarNotificationListener.onVisibilityChanged(mIsVisible);
        // Reset and collapse all groups when notification view disappears.
        if (!mIsVisible) {
            resetNotifications();
            mCarNotificationView.resetState();
        }
    }

    /**
     * Reset notifications to the latest state.
     */
    private void resetNotifications() {
        mPreprocessingManager.init(mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        if (DEBUG) {
            Log.d(TAG, "Unprocessed notification map: "
                    + mCarNotificationListener.getNotifications());
        }

        List<NotificationGroup> notificationGroups = mPreprocessingManager.process(
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        if (DEBUG) {
            Log.d(TAG, "Processed notification groups: " + notificationGroups);
        }

        if (!mShowRecentsAndOlderHeaders) {
            mNotificationDataManager.updateUnseenNotificationGroups(notificationGroups);
        }

        mCarNotificationView.setNotifications(notificationGroups);
    }

    /**
     * Update notifications: no grouping/ranking updates will go through.
     * Insertion, deletion and content update will apply immediately.
     */
    private void updateNotifications(int what, AlertEntry alertEntry) {

        if (mPreprocessingManager.shouldFilter(alertEntry,
                mCarNotificationListener.getCurrentRanking())
                && what != CarNotificationListener.NOTIFY_NOTIFICATION_REMOVED) {
            // if the new notification should be filtered out, return early
            return;
        }

        List<NotificationGroup> notificationGroups = mPreprocessingManager.updateNotifications(
                alertEntry,
                what,
                mCarNotificationListener.getCurrentRanking());
        if (what == CarNotificationListener.NOTIFY_NOTIFICATION_REMOVED) {
            mCarNotificationView.removeNotification(alertEntry);
        } else {
            if (DEBUG) {
                Log.d(TAG, "Updated notification groups: " + notificationGroups);
            }

            mCarNotificationView.setNotifications(notificationGroups);
        }
    }

    private class NotificationUpdateHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (mIsVisible) {
                if (message.what == CarNotificationListener.NOTIFY_RANKING_UPDATED) {
                    // Do not update notifications if ranking is updated while panel is visible.
                    return;
                }

                updateNotifications(message.what, (AlertEntry) message.obj);
            } else {
                resetNotifications();
            }
        }
    }
}
