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

package com.android.car.notification.template;

import android.annotation.CallSuper;
import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.R;
import com.android.car.notification.ThemesUtil;

/**
 * The base view holder class that all template view holders should extend.
 */
public abstract class CarNotificationBaseViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = CarNotificationBaseViewHolder.class.getSimpleName();

    private final PackageManager mPackageManager;
    private final NotificationClickHandlerFactory mClickHandlerFactory;

    @Nullable
    private final CardView mCardView; // can be null for group child or group summary notification
    @Nullable
    private final View mInnerView; // can be null for GroupNotificationViewHolder
    @Nullable
    private final CarNotificationHeaderView mHeaderView;
    @Nullable
    private final CarNotificationBodyView mBodyView;
    @Nullable
    private final CarNotificationActionsView mActionsView;

    @ColorInt
    private final int mDefaultBackgroundColor;
    @ColorInt
    private final int mDefaultCarAccentColor;
    @ColorInt
    private final int mDefaultPrimaryForegroundColor;
    @ColorInt
    private final int mDefaultSecondaryForegroundColor;

    private StatusBarNotification mStatusBarNotification;
    private boolean mIsAnimating;
    @ColorInt
    private int mBackgroundColor;

    CarNotificationBaseViewHolder(
            View itemView, NotificationClickHandlerFactory clickHandlerFactory) {
        super(itemView);
        Context context = itemView.getContext();
        mPackageManager = context.getPackageManager();
        mClickHandlerFactory = clickHandlerFactory;
        mCardView = itemView.findViewById(R.id.card_view);
        mInnerView = itemView.findViewById(R.id.inner_template_view);
        mHeaderView = itemView.findViewById(R.id.notification_header);
        mBodyView = itemView.findViewById(R.id.notification_body);
        mActionsView = itemView.findViewById(R.id.notification_actions);
        mDefaultBackgroundColor = ThemesUtil.getAttrColor(context, android.R.attr.colorPrimary);
        mDefaultCarAccentColor = ThemesUtil.getAttrColor(context, android.R.attr.colorAccent);
        mDefaultPrimaryForegroundColor = context.getColor(R.color.primary_text_color);
        mDefaultSecondaryForegroundColor = context.getColor(R.color.secondary_text_color);
    }

    /**
     * Binds a {@link StatusBarNotification} to a notification template. Base class sets the
     * clicking event for the card view and calls recycling methods.
     *
     * @param statusBarNotification the notification to be bound.
     * @param isInGroup whether this notification is part of a grouped notification.
     */
    @CallSuper
    public void bind(StatusBarNotification statusBarNotification, boolean isInGroup,
            boolean isHeadsUp) {
        reset();
        mStatusBarNotification = statusBarNotification;

        if (isInGroup) {
            mInnerView.setBackgroundColor(mDefaultBackgroundColor);
            mInnerView.setOnClickListener(
                    mClickHandlerFactory.getClickHandler(mStatusBarNotification));
        } else {
            mCardView.setOnClickListener(
                    mClickHandlerFactory.getClickHandler(mStatusBarNotification));
        }

        ApplicationInfo appInfo;
        try {
            appInfo =
                    mPackageManager.getApplicationInfo(mStatusBarNotification.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find app in bind() " + e);
            return;
        }

        Notification notification = mStatusBarNotification.getNotification();

        boolean isSystemApp = appInfo.isSystemApp();
        boolean isNavigationCategory =
                Notification.CATEGORY_NAVIGATION.equals(notification.category);
        boolean isSystemOrNavigation = isSystemApp || isNavigationCategory;
        boolean hasColor = notification.color != Notification.COLOR_DEFAULT;
        boolean isColorized = notification.extras.getBoolean(Notification.EXTRA_COLORIZED, false);

        int calculatedPrimaryForegroundColor = mDefaultPrimaryForegroundColor;
        int calculatedSecondaryForegroundColor = mDefaultSecondaryForegroundColor;
        if (isSystemOrNavigation && hasColor && isColorized && !isInGroup) {
            mBackgroundColor = notification.color;
            calculatedPrimaryForegroundColor = NotificationColorUtil.resolveContrastColor(
                    mDefaultPrimaryForegroundColor,
                    mBackgroundColor);
            calculatedSecondaryForegroundColor = NotificationColorUtil.resolveContrastColor(
                    mDefaultSecondaryForegroundColor,
                    mBackgroundColor);
            mCardView.setCardBackgroundColor(mBackgroundColor);
        }

        if (mHeaderView != null) {
            mHeaderView.setSmallIconColor(
                    hasCustomBackgroundColor() ? calculatedPrimaryForegroundColor
                            : getAccentColor());
            mHeaderView.setHeaderTextColor(calculatedPrimaryForegroundColor);
            mHeaderView.setTimeTextColor(calculatedPrimaryForegroundColor);
        }

        if (mBodyView != null) {
            mBodyView.setPrimaryTextColor(calculatedPrimaryForegroundColor);
            mBodyView.setSecondaryTextColor(calculatedSecondaryForegroundColor);
        }
    }

    /**
     * Returns the accent color for this notification.
     */
    @ColorInt
    int getAccentColor() {
        int color = mStatusBarNotification.getNotification().color;
        if (color != Notification.COLOR_DEFAULT) {
            return color;
        }
        return mDefaultCarAccentColor;
    }

    /**
     * Returns whether this card has a custom background color.
     */
    boolean hasCustomBackgroundColor() {
        return mBackgroundColor != mDefaultBackgroundColor;
    }

    /**
     * Child view holders should override and call super to recycle any custom component
     * that's not handled by {@link CarNotificationHeaderView}, {@link CarNotificationBodyView} and
     * {@link CarNotificationActionsView}.
     * Note that any child class that is not calling {@link #bind} has to call this method directly.
     */
    @CallSuper
    void reset() {
        mStatusBarNotification = null;
        mBackgroundColor = mDefaultBackgroundColor;

        itemView.setTranslationX(0);
        itemView.setAlpha(1f);

        if (mCardView != null) {
            mCardView.setOnClickListener(null);
            mCardView.setCardBackgroundColor(mDefaultBackgroundColor);
        }

        if (mHeaderView != null) {
            mHeaderView.reset();
        }

        if (mBodyView != null) {
            mBodyView.reset();
        }

        if (mActionsView != null) {
            mActionsView.reset();
        }
    }

    /**
     * Returns the current {@link StatusBarNotification} that this view holder is holding.
     * Note that any child class that is not calling {@link #bind} has to override this method.
     */
    public StatusBarNotification getStatusBarNotification() {
        return mStatusBarNotification;
    }

    /**
     * Returns true if the notification contained in this view holder can be swiped away.
     */
    public boolean isDismissible() {
        if (mStatusBarNotification == null) {
            return true;
        }

        return (mStatusBarNotification.getNotification().flags
                & (Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_ONGOING_EVENT)) == 0;
    }

    /**
     * Returns the TranslationX of the ItemView.
     */
    public float getSwipeTranslationX() {
        return itemView.getTranslationX();
    }

    /**
     * Sets the TranslationX of the ItemView.
     */
    public void setSwipeTranslationX(float translationX) {
        itemView.setTranslationX(translationX);
    }

    /**
     * Sets the alpha of the ItemView.
     */
    public void setSwipeAlpha(float alpha) {
        itemView.setAlpha(alpha);
    }

    /**
     * Sets whether this view holder has ongoing animation.
     */
    public void setIsAnimating(boolean animating) {
        mIsAnimating = animating;
    }

    /**
     * Returns true if this view holder has ongoing animation.
     */
    public boolean isAnimating() {
        return mIsAnimating;
    }
}
