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

import android.app.Notification;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationUtils {
    private static final String TAG = "NotificationUtils";

    private NotificationUtils() {
    }

    /**
     * Returns the color assigned to the given attribute.
     */
    public static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    /**
     * Validates if the notification posted by the application meets at least one of the below
     * conditions.
     *
     * <ul>
     * <li>application is signed with platform key.
     * <li>application is a system and privileged app.
     * </ul>
     */
    public static boolean isSystemPrivilegedOrPlatformKey(Context context, AlertEntry alertEntry) {
        return isSystemPrivilegedOrPlatformKeyInner(context, alertEntry,
                /* checkForPrivilegedApp= */ true);
    }

    /**
     * Validates if the notification posted by the application meets at least one of the below
     * conditions.
     *
     * <ul>
     * <li>application is signed with platform key.
     * <li>application is a system app.
     * </ul>
     */
    public static boolean isSystemOrPlatformKey(Context context, AlertEntry alertEntry) {
        return isSystemPrivilegedOrPlatformKeyInner(context, alertEntry,
                /* checkForPrivilegedApp= */ false);
    }

    /**
     * Validates if the notification posted by the application is a system application.
     */
    public static boolean isSystemApp(Context context,
            StatusBarNotification statusBarNotification) {
        PackageManager packageManager = context.getPackageManager();
        CarUserManagerHelper carUserManagerHelper = new CarUserManagerHelper(context);
        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfoAsUser(
                    statusBarNotification.getPackageName(), /* flags= */ 0,
                    carUserManagerHelper.getCurrentForegroundUserId());
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, "package not found: " + statusBarNotification.getPackageName());
        }
        if (packageInfo == null) return false;

        return packageInfo.applicationInfo.isSystemApp();
    }

    /**
     * Validates if the notification posted by the application is signed with platform key.
     */
    public static boolean isSignedWithPlatformKey(Context context,
            StatusBarNotification statusBarNotification) {
        PackageManager packageManager = context.getPackageManager();
        CarUserManagerHelper carUserManagerHelper = new CarUserManagerHelper(context);
        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfoAsUser(
                    statusBarNotification.getPackageName(), /* flags= */ 0,
                    carUserManagerHelper.getCurrentForegroundUserId());
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG, "package not found: " + statusBarNotification.getPackageName());
        }
        if (packageInfo == null) return false;

        return packageInfo.applicationInfo.isSignedWithPlatformKey();
    }

    private static boolean isSystemPrivilegedOrPlatformKeyInner(Context context,
            AlertEntry alertEntry, boolean checkForPrivilegedApp) {
        PackageManager packageManager = context.getPackageManager();
        CarUserManagerHelper carUserManagerHelper = new CarUserManagerHelper(context);
        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfoAsUser(
                    alertEntry.getStatusBarNotification().getPackageName(), /* flags= */ 0,
                    carUserManagerHelper.getCurrentForegroundUserId());
        } catch (PackageManager.NameNotFoundException ex) {
            Log.e(TAG,
                    "package not found: " + alertEntry.getStatusBarNotification().getPackageName());
        }
        if (packageInfo == null) return false;

        // Only include the privilegedApp check if the caller wants this check.
        boolean isPrivilegedApp =
                (!checkForPrivilegedApp) || packageInfo.applicationInfo.isPrivilegedApp();

        return (packageInfo.applicationInfo.isSignedWithPlatformKey() ||
                (packageInfo.applicationInfo.isSystemApp()
                        && isPrivilegedApp));
    }

    /**
     * Choose a correct notification layout for this heads-up notification.
     * Note that the layout chosen can be different for the same notification
     * in the notification center.
     */
    public static CarNotificationTypeItem getNotificationViewType(AlertEntry alertEntry) {
        String category = alertEntry.getNotification().category;
        if (category != null) {
            switch (category) {
                case Notification.CATEGORY_CAR_EMERGENCY:
                    return CarNotificationTypeItem.EMERGENCY;
                case Notification.CATEGORY_NAVIGATION:
                    return CarNotificationTypeItem.NAVIGATION;
                case Notification.CATEGORY_CALL:
                    return CarNotificationTypeItem.CALL;
                case Notification.CATEGORY_CAR_WARNING:
                    return CarNotificationTypeItem.WARNING;
                case Notification.CATEGORY_CAR_INFORMATION:
                    return CarNotificationTypeItem.INFORMATION;
                case Notification.CATEGORY_MESSAGE:
                    return CarNotificationTypeItem.MESSAGE;
                default:
                    break;
            }
        }
        Bundle extras = alertEntry.getNotification().extras;
        if (extras.containsKey(Notification.EXTRA_BIG_TEXT)
                && extras.containsKey(Notification.EXTRA_SUMMARY_TEXT)) {
            return CarNotificationTypeItem.INBOX;
        }
        // progress, media, big text, big picture, and basic templates
        return CarNotificationTypeItem.BASIC;
    }
}
