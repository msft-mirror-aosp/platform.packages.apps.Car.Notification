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

package com.android.car.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.AnimatorSet;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.testing.TestableContext;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.notification.headsup.CarHeadsUpNotificationContainer;
import com.android.car.notification.headsup.animationhelper.HeadsUpNotificationAnimationHelper;
import com.android.car.notification.utils.MockMessageNotificationBuilder;
import com.android.systemui.car.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class CarHeadsUpNotificationManagerTest {
    private static final String PKG_1 = "package_1";
    private static final String PKG_2 = "package_2";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CHANNEL_NAME = "CHANNEL_NAME";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final String EXTRA_BIG_TEXT_VALUE = "EXTRA_BIG_TEXT";
    private static final String EXTRA_SUMMARY_TEXT_VALUE = "EXTRA_SUMMARY_TEXT";
    private static final long POST_TIME = 12345L;
    private static final UserHandle USER_HANDLE = new UserHandle(/* userId= */ 12);
    private static final NotificationChannel CHANNEL = new NotificationChannel(CHANNEL_ID,
            CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);

    public final TestableContext mContext = new TestableContext(
            ApplicationProvider.getApplicationContext());
    @Mock
    NotificationListenerService.RankingMap mRankingMapMock;
    @Mock
    NotificationListenerService.Ranking mRankingMock;
    @Mock
    NotificationClickHandlerFactory mClickHandlerFactory;
    @Mock
    NotificationDataManager mNotificationDataManager;
    @Mock
    StatusBarNotification mMockStatusBarNotification;
    @Mock
    PackageManager mPackageManager;
    @Mock
    CarNotificationListener mCarNotificationListener;
    @Mock
    CarHeadsUpNotificationContainer mCarHeadsUpNotificationContainer;
    @Mock
    CarHeadsUpNotificationQueue mCarHeadsUpNotificationQueue;
    @Mock
    KeyguardManager mKeyguardManager;
    @Mock
    Handler mHandlerMock;
    @Mock
    private HeadsUpNotificationAnimationHelper mAnimationHelper;
    @Mock
    private AnimatorSet mAnimatorSet;
    @Captor
    ArgumentCaptor<View> mViewCaptor;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private CarHeadsUpNotificationManager mManager;
    private AlertEntry mAlertEntryMessageHeadsUp;
    private AlertEntry mAlertEntryNavigationHeadsUp;
    private AlertEntry mAlertEntryCallHeadsUp;
    private AlertEntry mAlertEntryCallHeadsUp2;
    private AlertEntry mAlertEntryInboxHeadsUp;
    private AlertEntry mAlertEntryCarInformationHeadsUp;
    private Map<String, AlertEntry> mActiveNotifications;
    private List<CarHeadsUpNotificationManager.HeadsUpState> mHeadsUpStates;


    @Before
    public void setup() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ true);
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_showNavigationHeadsup, /* value= */ true);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        when(mPackageManager.getApplicationInfoAsUser(anyString(),
                eq(PackageManager.MATCH_UNINSTALLED_PACKAGES), anyInt())).thenReturn(
                applicationInfo);
        when(mPackageManager.getApplicationInfo(anyString(),
                eq(PackageManager.MATCH_UNINSTALLED_PACKAGES))).thenReturn(applicationInfo);
        when(mPackageManager.getResourcesForApplication(applicationInfo)).thenReturn(
                mContext.getResources());
        mContext.setMockPackageManager(mPackageManager);

        when(mKeyguardManager.isKeyguardLocked()).thenReturn(false);
        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        when(mClickHandlerFactory.getClickHandler(any())).thenReturn(v -> {});

        when(mRankingMock.getChannel()).thenReturn(CHANNEL);
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);
        when(mCarNotificationListener.getCurrentRanking()).thenReturn(mRankingMapMock);
        when(mCarHeadsUpNotificationContainer.getAnimationHelper()).thenReturn(mAnimationHelper);
        when(mAnimationHelper.getAnimateOutAnimator(any(), any())).thenReturn(mAnimatorSet);

        Notification mNotificationMessageHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setHasMessagingStyle(true)
                .setHasReplyAction(true)
                .setHasMarkAsRead(true)
                .build();
        Notification mNotificationNavigationHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build();
        Notification mNotificationCallHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CALL)
                .build();
        Notification mNotificationCallHeadsUp2 = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CALL)
                .build();
        Notification mNotificationWarningHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_WARNING)
                .build();
        Notification mNotificationEmergencyHeadsUp = new MockMessageNotificationBuilder(mContext,
                CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_EMERGENCY)
                .build();
        Notification mNotificationCarInformationHeadsUp = new MockMessageNotificationBuilder(
                mContext, CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .build();

        Bundle bundle = new Bundle();
        bundle.putString(Notification.EXTRA_BIG_TEXT, EXTRA_BIG_TEXT_VALUE);
        bundle.putString(Notification.EXTRA_SUMMARY_TEXT, EXTRA_SUMMARY_TEXT_VALUE);
        Notification mNotificationBuilderInboxHeadsUp = new MockMessageNotificationBuilder(
                mContext, CHANNEL_ID, android.R.drawable.sym_def_app_icon)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setExtras(bundle)
                .setHasMessagingStyle(true)
                .setHasReplyAction(true)
                .setHasMarkAsRead(true)
                .build();

        mAlertEntryMessageHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationMessageHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryNavigationHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_2, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationNavigationHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));
        mAlertEntryCallHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationCallHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryCallHeadsUp2 = new AlertEntry(
                new StatusBarNotification(PKG_2, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationCallHeadsUp2, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));
        mAlertEntryInboxHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationBuilderInboxHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));
        mAlertEntryCarInformationHeadsUp = new AlertEntry(
                new StatusBarNotification(PKG_1, OP_PKG, ID, TAG, UID, INITIAL_PID,
                        mNotificationCarInformationHeadsUp, USER_HANDLE, OVERRIDE_GROUP_KEY,
                        POST_TIME));

        mActiveNotifications = new HashMap<>();
        mHeadsUpStates = new ArrayList<>();

        createCarHeadsUpNotificationManager();

        verify(mCarHeadsUpNotificationContainer).getAnimationHelper();
    }

    @Test
    public void maybeShowOrScheduleHun_isNotImportant_returnsFalseAndNotAddedToQueue()
            throws PackageManager.NameNotFoundException {
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_DEFAULT);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryNavigationHeadsUp,
                mActiveNotifications);

        assertThat(result).isFalse();
        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any());
    }

    @Test
    public void maybeShowOrScheduleHun_isImportanceHigh_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryNavigationHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryNavigationHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_categoryCarInformation_returnsFalseAndNotAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryCarInformationHeadsUp,
                mActiveNotifications);

        assertThat(result).isFalse();
        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any());
    }

    @Test
    public void maybeShowOrScheduleHun_categoryMessage_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryMessageHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryMessageHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_categoryCall_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryCallHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_categoryNavigation_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryNavigationHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryNavigationHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_inboxHeadsUp_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryInboxHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryInboxHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_isSignedWithPlatformKey_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ true);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryCarInformationHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryCarInformationHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_isSystemApp_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ true, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryCarInformationHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryCarInformationHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_nonMutedNotification_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        when(mNotificationDataManager.isMessageNotificationMuted(any())).thenReturn(false);
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryInboxHeadsUp,
                mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(mAlertEntryInboxHeadsUp);
    }

    @Test
    public void maybeShowOrScheduleHun_mutedNotification_returnsFalseAndNotAddedToQueue()
            throws PackageManager.NameNotFoundException {
        when(mNotificationDataManager.isMessageNotificationMuted(any())).thenReturn(true);
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        boolean result = mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp,
                mActiveNotifications);

        assertThat(result).isFalse();
        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any());
    }

    @Test
    public void maybeShowOrScheduleHun_simultaneousCall_returnsTrueAndAddedToPendingCalls()
            throws PackageManager.NameNotFoundException {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();

        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        Looper.prepare();

        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp, mActiveNotifications);
        assertThat(NotificationUtils.isCategoryCall(mAlertEntryCallHeadsUp)).isTrue();
        assertThat(mManager.getPendingCalls().size()).isEqualTo(0);

        HeadsUpEntry headsUpEntry = createMockHeadsUpEntry("key1");
        when(headsUpEntry.getNotification()).thenReturn(mAlertEntryCallHeadsUp.getNotification());
        assertThat(NotificationUtils.isCategoryCall(headsUpEntry)).isTrue();

        mManager.addActiveHeadsUpNotification(headsUpEntry);
        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp2, mActiveNotifications);
        assertThat(mManager.getPendingCalls().size()).isEqualTo(1);
    }

    @Test
    public void getActiveHeadsUpNotifications_shouldReturnOne()
            throws PackageManager.NameNotFoundException {
        // Queueing mechanism is not used
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();
        // This test fails if looper isn't forced to prepare due to Handler creation in {@link
        // HeadsUpEntry}.
        Looper.prepare();
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.maybeShowOrScheduleHun(mAlertEntryNavigationHeadsUp, mActiveNotifications);

        Map<String, HeadsUpEntry> activeHeadsUpNotifications =
                mManager.getActiveHeadsUpNotifications();

        assertThat(activeHeadsUpNotifications.size()).isEqualTo(1);
    }

    @Test
    public void getActiveHeadsUpNotifications_diffNotifications_shouldReturnTwo()
            throws PackageManager.NameNotFoundException {
        // Queueing mechanism is not used
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();
        // This test fails if looper isn't forced to prepare due to Handler creation in {@link
        // HeadsUpEntry}.
        Looper.prepare();
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp, mActiveNotifications);
        mManager.maybeShowOrScheduleHun(mAlertEntryNavigationHeadsUp, mActiveNotifications);

        Map<String, HeadsUpEntry> activeHeadsUpNotifications =
                mManager.getActiveHeadsUpNotifications();

        assertThat(activeHeadsUpNotifications.size()).isEqualTo(2);
    }

    @Test
    public void getActiveHeadsUpNotifications_sameNotifications_shouldReturnOne()
            throws PackageManager.NameNotFoundException {
        // Queueing mechanism is not used
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();
        // This test fails if looper isn't forced to prepare due to Handler creation in {@link
        // HeadsUpEntry}.
        Looper.prepare();
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp, mActiveNotifications);
        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp, mActiveNotifications);

        Map<String, HeadsUpEntry> activeHeadsUpNotifications =
                mManager.getActiveHeadsUpNotifications();

        assertThat(activeHeadsUpNotifications.size()).isEqualTo(1);
    }

    @Test
    public void notification_removedFromQueue_notifyListeners()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        mManager.registerHeadsUpNotificationStateChangeListener((alertEntry, headsUpState) -> {
            mHeadsUpStates.add(headsUpState);
        });
        CarHeadsUpNotificationQueue.CarHeadsUpNotificationQueueCallback queueCallback =
                mManager.getCarHeadsUpNotificationQueueCallback();

        queueCallback.removedFromHeadsUpQueue(mAlertEntryMessageHeadsUp);

        assertThat(mHeadsUpStates.size()).isEqualTo(1);
        assertThat(mHeadsUpStates.get(0)).isEqualTo(
                CarHeadsUpNotificationManager.HeadsUpState.REMOVED_FROM_QUEUE);
    }

    @Test
    public void clearCache_viewsRemovedFromCarHeadsUpNotificationContainer() {
        CarHeadsUpNotificationContainer container = mManager.mHunContainer;
        HeadsUpEntry notification1 = createMockHeadsUpEntry("key1");
        HeadsUpEntry notification2 = createMockHeadsUpEntry("key2");
        mManager.addActiveHeadsUpNotification(notification1);
        mManager.addActiveHeadsUpNotification(notification2);

        mManager.clearCache();

        verify(container, times(2)).removeNotification(mViewCaptor.capture());
        assertThat(mViewCaptor.getAllValues().containsAll(List.of(new View[]{
                notification1.getNotificationView(), notification2.getNotificationView()})))
                .isTrue();
    }


    @Test
    public void maybeRemoveHeadsUp_categoryCall_removesActiveEntry()
            throws PackageManager.NameNotFoundException {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();

        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        Looper.prepare();

        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp, mActiveNotifications);
        assertThat(NotificationUtils.isCategoryCall(mAlertEntryCallHeadsUp)).isTrue();
        assertThat(mManager.getPendingCalls().size()).isEqualTo(0);

        HeadsUpEntry headsUpEntry = createMockHeadsUpEntry(mAlertEntryCallHeadsUp.getKey());
        when(headsUpEntry.getNotification()).thenReturn(mAlertEntryCallHeadsUp.getNotification());
        assertThat(NotificationUtils.isCategoryCall(headsUpEntry)).isTrue();

        mManager.addActiveHeadsUpNotification(headsUpEntry);
        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp2, mActiveNotifications);
        assertThat(mManager.getPendingCalls().size()).isEqualTo(1);
        assertThat(headsUpEntry.getHandler().hasMessagesOrCallbacks()).isFalse();

        mManager.maybeRemoveHeadsUp(mAlertEntryCallHeadsUp);
        assertThat(mManager.getPendingCalls().size()).isEqualTo(1);
        // verify that headsUpEntry was reset before removed
        verify(headsUpEntry.getHandler(), times(1))
                .removeCallbacksAndMessages(any());
    }

    @Test
    public void maybeRemoveHeadsUp_categoryCall_removesPendingEntry()
            throws PackageManager.NameNotFoundException {
        mContext.getOrCreateTestableResources().addOverride(
                R.bool.config_suppressAndThrottleHeadsUp, /* value= */ false);
        createCarHeadsUpNotificationManager();

        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        setPackageInfo(PKG_2, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        Looper.prepare();

        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp, mActiveNotifications);
        assertThat(NotificationUtils.isCategoryCall(mAlertEntryCallHeadsUp)).isTrue();
        assertThat(mManager.getPendingCalls().size()).isEqualTo(0);

        HeadsUpEntry headsUpEntry = createMockHeadsUpEntry("key1");
        when(headsUpEntry.getNotification()).thenReturn(mAlertEntryCallHeadsUp.getNotification());
        assertThat(NotificationUtils.isCategoryCall(headsUpEntry)).isTrue();

        mManager.addActiveHeadsUpNotification(headsUpEntry);
        mManager.maybeShowOrScheduleHun(mAlertEntryCallHeadsUp2, mActiveNotifications);
        assertThat(mManager.getPendingCalls().size()).isEqualTo(1);

        mManager.maybeRemoveHeadsUp(mAlertEntryCallHeadsUp2);
        // verify that headsUpEntry was NOT reset and therefore remains displayed to user
        verify(headsUpEntry.getHandler(), times(0))
                .removeCallbacksAndMessages(any());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_PROMOTED_NOTIFICATIONS)
    public void maybeShowOrScheduleHun_promotedOngoing_returnsTrueAndAddedToQueue()
            throws PackageManager.NameNotFoundException {
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);
        Notification notification = mock(Notification.class);
        when(notification.isRequestPromotedOngoing()).thenReturn(true);
        when(notification.isOngoingEvent()).thenReturn(true);
        when(notification.hasTitle()).thenReturn(true);
        AlertEntry alertEntry = new AlertEntry(new StatusBarNotification(PKG_1, OP_PKG, ID, TAG,
                UID, INITIAL_PID, notification, USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME));

        boolean result = mManager.maybeShowOrScheduleHun(alertEntry, mActiveNotifications);

        assertThat(result).isTrue();
        verify(mCarHeadsUpNotificationQueue).addToQueue(alertEntry);
    }

    private void createCarHeadsUpNotificationManager() {
        createCarHeadsUpNotificationManager(mCarHeadsUpNotificationQueue);
    }

    private void createCarHeadsUpNotificationManager(
            @Nullable CarHeadsUpNotificationQueue carHeadsUpNotificationQueue) {
        mManager = new CarHeadsUpNotificationManager(mContext, mClickHandlerFactory,
                mCarHeadsUpNotificationContainer) {
            @Override
            protected NotificationListenerService.Ranking getRanking() {
                return mRankingMock;
            }
        };
        if (carHeadsUpNotificationQueue != null) {
            mManager.setCarHeadsUpNotificationQueue(carHeadsUpNotificationQueue);
        }
        mManager.setNotificationDataManager(mNotificationDataManager);
        mManager.setRankingMapProvider(mCarNotificationListener);
    }

    private void setPackageInfo(String packageName, boolean isSystem,
            boolean isSignedWithPlatformKey) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = mock(PackageInfo.class);
        ApplicationInfo applicationInfo = mock(ApplicationInfo.class);
        packageInfo.packageName = packageName;
        when(applicationInfo.isPrivilegedApp()).thenReturn(isSystem);
        when(applicationInfo.isSystemApp()).thenReturn(isSystem);
        when(applicationInfo.isSignedWithPlatformKey()).thenReturn(isSignedWithPlatformKey);
        packageInfo.applicationInfo = applicationInfo;
        when(mPackageManager.getPackageInfoAsUser(eq(packageName), anyInt(), anyInt())).thenReturn(
                packageInfo);
    }

    private HeadsUpEntry createMockHeadsUpEntry(String key) {
        HeadsUpEntry headsUpEntry = mock(HeadsUpEntry.class);
        when(headsUpEntry.getKey()).thenReturn(key);
        when(headsUpEntry.getHandler()).thenReturn(mHandlerMock);
        Notification notification = new Notification();
        when(mMockStatusBarNotification.getNotification()).thenReturn(notification);
        when(headsUpEntry.getStatusBarNotification()).thenReturn(mMockStatusBarNotification);
        View headsUpNotificationView = mock(View.class);
        when(headsUpEntry.getNotificationView()).thenReturn(headsUpNotificationView);
        return headsUpEntry;
    }

    @Test
    public void showHunImmediately_showsWithoutDismissingActiveHuns()
            throws PackageManager.NameNotFoundException {
        Looper.prepare();
        createCarHeadsUpNotificationManager();
        setPackageInfo(PKG_1, /* isSystem= */ false, /* isSignedWithPlatformKey= */ false);

        HeadsUpEntry headsUpEntry = createMockHeadsUpEntry(
                mAlertEntryNavigationHeadsUp.getKey());
        when(headsUpEntry.getNotification()).thenReturn(
                mAlertEntryNavigationHeadsUp.getNotification());
        when(headsUpEntry.getNotificationView()).thenReturn(mock(View.class));

        mManager.addActiveHeadsUpNotification(headsUpEntry);

        mManager.showHunImmediately(mAlertEntryMessageHeadsUp);

        verify(mCarHeadsUpNotificationQueue, never()).addToQueue(any());
        assertThat(mManager.getActiveHeadsUpNotifications().containsKey(
                mAlertEntryNavigationHeadsUp.getKey())).isTrue();
        assertThat(mManager.getActiveHeadsUpNotifications().containsKey(
                mAlertEntryMessageHeadsUp.getKey())).isTrue();
    }
}
