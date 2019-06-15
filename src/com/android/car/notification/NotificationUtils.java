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

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
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
    public static boolean isSystemPrivilegedOrPlatformKey(Context context,
            AlertEntry alertEntry) {
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
}
