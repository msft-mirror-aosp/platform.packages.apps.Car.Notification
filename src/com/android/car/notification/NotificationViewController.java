package com.android.car.notification;

import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.os.Handler;
import android.os.Message;
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
                updateNotifications(mShowLessImportantNotifications);
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
        updateNotifications(mShowLessImportantNotifications);
    }

    /**
     * Removes listeners
     */
    public void disable() {
        mCarNotificationListener.setHandler(null);
        mUxResitrictionListener.setCarNotificationView(null);
    }

    /**
     * Update all notifications and ranking
     */
    private void updateNotifications(boolean showLessImportantNotifications) {
        List<NotificationGroup> notifications = mPreprocessingManager.process(
                showLessImportantNotifications,
                mCarNotificationListener.getNotifications(),
                mCarNotificationListener.getCurrentRanking());

        mNotificationDataManager.updateUnseenNotification(notifications);
        mCarNotificationView.setNotifications(notifications);
    }

    private class NotificationUpdateHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            if (message.what == CarNotificationListener.NOTIFY_NOTIFICATIONS_CHANGED) {
                updateNotifications(mShowLessImportantNotifications);
            }
        }
    }
}
