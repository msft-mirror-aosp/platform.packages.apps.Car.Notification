/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.notification.headsup;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;

import com.android.car.notification.CarNotificationTypeItem;
import com.android.car.notification.R;
import com.android.car.notification.headsup.animationhelper.HeadsUpNotificationAnimationHelper;

import java.util.LinkedList;

/**
 * An abstract base class that serves as the foundation for displaying Heads-Up Notifications (HUNs)
 * in a car environment. It provides the core logic for managing HUNs but does not define the
 * specifics of how they are displayed on the screen.
 */
public class CarHeadsUpNotificationContainer {
    private static final String TAG = "CarHUNContainer";
    private final LinkedList<HunImportance> mHunImportanceLinkedList = new LinkedList<>();
    protected ViewGroup mHunRootView;
    protected ViewGroup mHunContent;
    protected boolean mShowHunOnBottom;
    private final Context mContext;

    public CarHeadsUpNotificationContainer(Context context) {
        mContext = context;
    }

    /**
     * Inflates the layout for the HUN container.
     *
     * <p>This method determines whether to show the HUN on the bottom based on the configuration
     * and inflates the appropriate layout. It is called by the constructor and can be overridden
     * by subclasses to provide custom layout inflation logic.
     */
    protected void inflateLayout(Context context) {
        mShowHunOnBottom = context.getResources().getBoolean(
                R.bool.config_showHeadsUpNotificationOnBottom);
        mHunRootView = (ViewGroup) LayoutInflater.from(context).inflate(
                mShowHunOnBottom ? R.layout.headsup_container_bottom
                        : R.layout.headsup_container, /* root= */ null, /* attachToRoot= */ false);
        mHunContent = mHunRootView.findViewById(R.id.headsup_content);
    }

    /**
     * Returns the animation helper for the HUN container. Subclasses can override this to
     * provide a different animation helper.
     */
    public HeadsUpNotificationAnimationHelper getAnimationHelper() {
        String helperName = mContext.getResources().getString(
                R.string.config_headsUpNotificationAnimationHelper);
        try {
            Class<?> clazz = Class.forName(helperName);
            return (HeadsUpNotificationAnimationHelper) clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("Invalid animation helper: %s", helperName), e);
        }
    }

    /**
     * Sets the initial visibility of the container. Can be overridden by subclasses that manage
     * visibility differently, such as through a state framework.
     */
    protected void initializeVisibility() {
        getHunRootView().setVisibility(View.INVISIBLE);
    }

    protected Context getContext() {
        return mContext;
    }

    /**
     * Displays a given notification View to the user and inserts the view at Z-index according to
     * its {@link HunImportance},
     */
    public void displayNotification(View notificationView,
            CarNotificationTypeItem notificationTypeItem) {
        HunImportance hunImportance = getImportanceForCarNotificationTypeItem(notificationTypeItem);

        displayNotificationInner(notificationView, hunImportance);

        if (shouldShowHunPanel()) {
            presentContainer();
        }
    }

    private void displayNotificationInner(View notificationView, HunImportance hunImportance) {
        if (mHunImportanceLinkedList.isEmpty() || hunImportance.equals(HunImportance.EMERGENCY)) {
            mHunImportanceLinkedList.add(hunImportance);
            getHunContent().addView(notificationView);
            return;
        }

        int index = 0;
        for (; index < mHunImportanceLinkedList.size(); index++) {
            if (hunImportance.isLessImportantThan(mHunImportanceLinkedList.get(index))) break;
        }
        if (index < mHunImportanceLinkedList.size()) {
            mHunImportanceLinkedList.add(index, hunImportance);
            getHunContent().addView(notificationView, index);
            return;
        }

        mHunImportanceLinkedList.add(hunImportance);
        getHunContent().addView(notificationView);
    }

    /**
     * @return {@code true} if Hun panel should be set as visible after displaying HUN.
     */
    public boolean shouldShowHunPanel() {
        return !isVisible();
    }

    /**
     * Removes a given notification View from the container.
     */
    public void removeNotification(View notificationView) {
        if (getHunContent().getChildCount() == 0) return;

        int index = getHunContent().indexOfChild(notificationView);
        if (index == -1) return;

        getHunContent().removeViewAt(index);
        mHunImportanceLinkedList.remove(index);

        if (shouldHideHunPanel()) {
            dismissContainer();
        }
    }

    /**
     * Makes the HUN container visible. Can be overridden by subclasses to change behavior.
     */
    protected void presentContainer() {
        getHunRootView().setVisibility(View.VISIBLE);
    }

    /**
     * Makes the HUN container invisible. Can be overridden by subclasses to change behavior.
     */
    protected void dismissContainer() {
        getHunRootView().setVisibility(View.INVISIBLE);
    }

    /**
     * @return {@code true} if HUN panel should be set as invisible after removing a HUN.
     */
    public boolean shouldHideHunPanel() {
        return getHunContent().getChildCount() == 0;
    }

    /**
     * @return Whether or not the container is currently visible.
     */
    public final boolean isVisible() {
        return getHunRootView().getVisibility() == View.VISIBLE;
    }

    /**
     * @return HUN rootview.
     */
    public final ViewGroup getHunRootView() {
        return mHunRootView;
    }

    /**
     * @return HUN content inside of window.
     */
    protected final ViewGroup getHunContent() {
        return mHunContent;
    }

    /**
     * @return {@code true} if HUN should be shown on bottom.
     */
    public boolean shouldShowHunOnBottom() {
        return mShowHunOnBottom;
    }

    private HunImportance getImportanceForCarNotificationTypeItem(
            CarNotificationTypeItem notificationTypeItem) {
        if (notificationTypeItem == CarNotificationTypeItem.EMERGENCY) {
            return HunImportance.EMERGENCY;
        } else if (notificationTypeItem == CarNotificationTypeItem.WARNING) {
            return HunImportance.WARNING;
        } else if (notificationTypeItem == CarNotificationTypeItem.NAVIGATION) {
            return HunImportance.NAVIGATION;
        } else if (notificationTypeItem == CarNotificationTypeItem.CALL) {
            return HunImportance.CALL;
        } else {
            return HunImportance.OTHER;
        }
    }

    @VisibleForTesting
    enum HunImportance {
        OTHER(/* level= */ 0),
        CALL(/* level= */ 1),
        NAVIGATION(/* level= */ 2),
        WARNING(/* level= */ 3),
        EMERGENCY(/* level= */ 4);

        private final Integer mLevel;

        HunImportance(int level) {
            this.mLevel = level;
        }

        boolean isMoreImportantThan(HunImportance other) {
            return this.mLevel > other.mLevel;
        }

        boolean isLessImportantThan(HunImportance other) {
            return this.mLevel < other.mLevel;
        }
    }
}
