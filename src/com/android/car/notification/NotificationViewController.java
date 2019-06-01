package com.android.car.notification;

import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.os.Handler;
import android.os.Message;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import java.util.List;

/**
 * This class is a bridge to collect signals from the notification and ux restriction services and
 * trigger the correct UI updates.
 */
public class NotificationViewController {

    private static final String TAG = "NotificationViewControl";
    private final CarNotificationView mCarNotificationView;
    private final PreprocessingManager mPreprocessingManager;
    private final CarNotificationListener mCarNotificationListener;
    private CarUxRestrictionManagerWrapper mUxResitrictionListener;
    private NotificationDataManager mNotificationDataManager;
    private NotificationUpdateHandler mNotificationUpdateHandler = new NotificationUpdateHandler();
    private boolean mShowLessImportantNotifications;
    private boolean mIsInForeground;

    public NotificationViewController(CarNotificationView carNotificationView,
            PreprocessingManager preprocessingManager,
            CarNotificationListener carNotificationListener,
            CarUxRestrictionManagerWrapper uxResitrictionListener,
            NotificationDataManager notificationDataManager) {
        mCarNotificationView = carNotificationView;
        mPreprocessingManager = preprocessingManager;
        mCarNotificationListener = carNotificationListener;
        mUxResitrictionListener = uxResitrictionListener;
        mNotificationDataManager = notificationDataManager;

        // Temporary hack for demo purposes: Long clicking on the notification center title toggles
        // hiding media, navigation, and less important (< IMPORTANCE_DEFAULT) ongoing
        // foreground service notifications.
        // This hack should be removed after OEM integration.
        View view = mCarNotificationView.findViewById(R.id.notification_center_title);
        if (view != null) {
            view.setOnLongClickListener(v -> {
                mShowLessImportantNotifications = !mShowLessImportantNotifications;
                Toast.makeText(
                        carNotificationView.getContext(),
                        "Foreground, navigation and media notifications " + (
                                mShowLessImportantNotifications ? "ENABLED" : "DISABLED"),
                        Toast.LENGTH_SHORT).show();
                resetNotifications(mShowLessImportantNotifications);
                return true;
            });
        }
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
        } catch (CarNotConnectedException e) {
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
     * Reset the list view. Called when the notification list is not in the foreground.
     */
    public void setIsInForeground(boolean isInForeground) {
        mIsInForeground = isInForeground;
        // Reset when we are not in foreground.
        if (!mIsInForeground) {
            resetNotifications(mShowLessImportantNotifications);
        }
    }

    /**
     * Reset notifications to the latest state.
     */
    private void resetNotifications(boolean showLessImportantNotifications) {
        mPreprocessingManager.init(
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        List<NotificationGroup> notificationGroups = mPreprocessingManager.process(
                showLessImportantNotifications,
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        mNotificationDataManager.updateUnseenNotification(notificationGroups);
        mCarNotificationView.setNotifications(notificationGroups);
    }

    /**
     * Update notifications: no grouping/ranking updates will go through.
     * Insertion, deletion and content update will apply immediately.
     */
    private void updateNotifications(
            boolean showLessImportantNotifications, int what, StatusBarNotification sbn) {
        mCarNotificationView.setNotifications(
                mPreprocessingManager.updateNotifications(
                        showLessImportantNotifications,
                        sbn,
                        what,
                        mCarNotificationListener.getCurrentRanking()));
    }

    private class NotificationUpdateHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (mIsInForeground) {
                updateNotifications(
                        mShowLessImportantNotifications,
                        message.what,
                        (StatusBarNotification) message.obj);
            } else {
                resetNotifications(mShowLessImportantNotifications);
            }
        }
    }
}
