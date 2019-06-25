/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NotificationListenerService that fetches all notifications from system.
 */
public class CarNotificationListener extends NotificationListenerService implements
        CarHeadsUpNotificationManager.OnHeadsUpNotificationStateChange {
    private static final String TAG = "CarNotificationListener";
    static final String ACTION_LOCAL_BINDING = "local_binding";
    static final int NOTIFY_NOTIFICATION_POSTED = 1;
    static final int NOTIFY_NOTIFICATION_REMOVED = 2;
    /** Temporary {@link Ranking} object that serves as a reused value holder */
    final private Ranking mTemporaryRanking = new Ranking();

    private Handler mHandler;
    private RankingMap mRankingMap;
    private CarHeadsUpNotificationManager mHeadsUpManager;
    private NotificationDataManager mNotificationDataManager;
    private CarUserManagerHelper mCarUserManagerHelper;

    /**
     * Map that contains all the active notifications. These notifications may or may not be
     * visible to the user if they get filtered out. The only time these will be removed from the
     * map is when the {@llink NotificationListenerService} calls the onNotificationRemoved method.
     * New notifications will be added to the map from {@link CarHeadsUpNotificationManager}.
     */
    private Map<String, AlertEntry> mActiveNotifications = new HashMap<>();

    /**
     * Call this if to register this service as a system service and connect to HUN. This is useful
     * if the notification service is being used as a lib instead of a standalone app. The
     * standalone app version has a manifest entry that will have the same effect.
     *
     * @param context Context required for registering the service.
     * @param carUxRestrictionManagerWrapper will have the heads up manager registered with it.
     * @param carHeadsUpNotificationManager HUN controller.
     * @param notificationDataManager used for keeping track of additional notification states.
     */
    public void registerAsSystemService(Context context,
            CarUxRestrictionManagerWrapper carUxRestrictionManagerWrapper,
            CarHeadsUpNotificationManager carHeadsUpNotificationManager,
            NotificationDataManager notificationDataManager) {
        try {
            mNotificationDataManager = notificationDataManager;
            mCarUserManagerHelper = new CarUserManagerHelper(context);
            registerAsSystemService(context,
                    new ComponentName(context.getPackageName(), getClass().getCanonicalName()),
                    mCarUserManagerHelper.getCurrentForegroundUserId());
            mHeadsUpManager = carHeadsUpNotificationManager;
            mHeadsUpManager.registerHeadsUpNotificationStateChangeListener(this);
            carUxRestrictionManagerWrapper.setCarHeadsUpNotificationManager(
                    carHeadsUpNotificationManager);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to register notification listener", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationDataManager = new NotificationDataManager();
        NotificationApplication app = (NotificationApplication) getApplication();

        app.getClickHandlerFactory().setNotificationDataManager(mNotificationDataManager);
        mHeadsUpManager = new CarHeadsUpNotificationManager(/* context= */ this,
                app.getClickHandlerFactory(), mNotificationDataManager);
        mCarUserManagerHelper = new CarUserManagerHelper(/* context= */ this);
        mHeadsUpManager.registerHeadsUpNotificationStateChangeListener(this);
        app.getCarUxRestrictionWrapper().setCarHeadsUpNotificationManager(mHeadsUpManager);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return ACTION_LOCAL_BINDING.equals(intent.getAction())
                ? new LocalBinder() : super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.d(TAG, "onNotificationPosted: " + sbn);
        // Notifications should only be shown for the current user and the the notifications from
        // the system when CarNotification is running as SystemUI component.
        if (sbn.getUser().getIdentifier() !=  mCarUserManagerHelper.getCurrentForegroundUserId()
                && sbn.getUser().getIdentifier() != UserHandle.USER_ALL) {
            return;
        }
        AlertEntry alertEntry = new AlertEntry(sbn);
        mRankingMap = rankingMap;
        notifyNotificationPosted(alertEntry);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationRemoved: " + sbn);
        AlertEntry alertEntry = mActiveNotifications.get(sbn.getKey());
        if (alertEntry != null) {
            mActiveNotifications.remove(alertEntry.getKey());
            mHeadsUpManager.maybeRemoveHeadsUp(alertEntry);
        }
        sendNotificationEventToHandler(alertEntry, NOTIFY_NOTIFICATION_REMOVED);
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        mRankingMap = rankingMap;
        for (AlertEntry alertEntry : mActiveNotifications.values()) {
            if (!mRankingMap.getRanking(alertEntry.getKey(), mTemporaryRanking)) {
                continue;
            }
            String oldOverrideGroupKey =
                    alertEntry.getStatusBarNotification().getOverrideGroupKey();
            String newOverrideGroupKey = getOverrideGroupKey(alertEntry.getKey());
            if (!Objects.equals(oldOverrideGroupKey, newOverrideGroupKey)) {
                alertEntry.getStatusBarNotification().setOverrideGroupKey(newOverrideGroupKey);
            }
        }
    }

    /**
     * Get the override group key of a {@link AlertEntry} given its key.
     */
    @Nullable
    private String getOverrideGroupKey(String key) {
        if (mRankingMap != null) {
            mRankingMap.getRanking(key, mTemporaryRanking);
            return mTemporaryRanking.getOverrideGroupKey();
        }
        return null;
    }

    /**
     * Get all active notifications.
     *
     * @return a map of all active notifications with key being the notification key.
     */
    Map<String, AlertEntry> getNotifications() {
        return mActiveNotifications;
    }

    @Override
    public RankingMap getCurrentRanking() {
        return mRankingMap;
    }

    @Override
    public void onListenerConnected() {
        mActiveNotifications = Stream.of(getActiveNotifications()).collect(
                Collectors.toMap(StatusBarNotification::getKey, sbn -> new AlertEntry(sbn)));
        mRankingMap = super.getCurrentRanking();
    }

    @Override
    public void onListenerDisconnected() {
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    private void notifyNotificationPosted(AlertEntry alertEntry) {
        mNotificationDataManager.addNewMessageNotification(alertEntry);

        // check for notification in mActiveNotifications before posting to NC. As app could have
        // canceled it.
        boolean isOngoing = mActiveNotifications.containsKey(alertEntry.getKey());

        boolean isShowingHeadsUp = mHeadsUpManager.maybeShowHeadsUp(alertEntry, getCurrentRanking(),
                mActiveNotifications);

        if (isOngoing && (!isShowingHeadsUp)) {
            sendNotificationEventToHandler(alertEntry, NOTIFY_NOTIFICATION_POSTED);
        }
    }

    @Override
    public void onStateChange(AlertEntry alertEntry, boolean isHeadsUp) {
        // No more a HUN
        if (!isHeadsUp) {
            sendNotificationEventToHandler(alertEntry, NOTIFY_NOTIFICATION_POSTED);
        }
    }

    class LocalBinder extends Binder {
        public CarNotificationListener getService() {
            return CarNotificationListener.this;
        }
    }

    private void sendNotificationEventToHandler(AlertEntry alertEntry, int eventType) {
        if (mHandler == null) {
            return;
        }
        Message msg = Message.obtain(mHandler);
        msg.what = eventType;
        msg.obj = alertEntry;
        mHandler.sendMessage(msg);
    }
}
