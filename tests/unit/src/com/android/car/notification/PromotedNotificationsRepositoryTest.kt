/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.car.notification

import android.app.Notification
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class PromotedNotificationsRepositoryTest {

    private lateinit var repository: PromotedNotificationsRepository

    @Mock
    private lateinit var mockAlertEntry: AlertEntry
    @Mock
    private lateinit var mockNotification: Notification
    @Mock
    private lateinit var mockContext: android.content.Context
    @Mock
    private lateinit var mockStatusBarNotification:
        android.service.notification.StatusBarNotification

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        repository = PromotedNotificationsRepository.getInstance()
        repository.clearPromotedNotifications()
    }

    @After
    fun tearDown() {
        repository.clearPromotedNotifications()
    }

    @Test
    fun testAddPromotedNotification() {
        runBlocking {
            val model = PromotedNotificationModel(
                key = "key1",
                isHeadsUp = false,
                postTime = 123L,
                shortCriticalText = "text",
                smallIcon = null,
                appName = "app"
            )

            repository.addPromotedNotification(model)

            val list = repository.promotedNotifications.first()
            assertThat(list).containsExactly(model)
        }
    }

    @Test
    fun testRemovePromotedNotification() {
        runBlocking {
            val model = PromotedNotificationModel(
                key = "key1",
                isHeadsUp = false,
                postTime = 123L,
                shortCriticalText = "text",
                smallIcon = null,
                appName = "app"
            )
            repository.addPromotedNotification(model)

            repository.removePromotedNotification("key1")

            val list = repository.promotedNotifications.first()
            assertThat(list).isEmpty()
        }
    }

    @Test
    fun testUpdateFromAlertEntries_addsPromotedOngoing() {
        runBlocking {
            `when`(mockAlertEntry.key).thenReturn("key1")
            `when`(mockAlertEntry.postTime).thenReturn(123L)
            `when`(mockAlertEntry.notification).thenReturn(mockNotification)
            `when`(mockAlertEntry.statusBarNotification).thenReturn(mockStatusBarNotification)
            `when`(mockStatusBarNotification.getPackageContext(mockContext)).thenReturn(mockContext)
            `when`(
                mockContext.packageManager
            ).thenReturn(org.mockito.Mockito.mock(android.content.pm.PackageManager::class.java))
            `when`(mockStatusBarNotification.notification).thenReturn(mockNotification)
            `when`(mockNotification.isPromotedOngoing).thenReturn(true)
            `when`(mockNotification.smallIcon).thenReturn(null)
            mockNotification.extras = android.os.Bundle()

            repository.updateFromAlertEntries(
                mockContext,
                listOf(mockAlertEntry),
                    isHeadsUp = false,
                remove = false
            )

            val list = repository.promotedNotifications.first()
            assertThat(list).hasSize(1)
            assertThat(list[0].key).isEqualTo("key1")
        }
    }

    @Test
    fun testUpdateFromAlertEntries_removesNotPromoted() {
        runBlocking {
            val model = PromotedNotificationModel(
                key = "key1",
                isHeadsUp = false,
                postTime = 123L,
                shortCriticalText = "text",
                smallIcon = null,
                appName = "app"
            )
            repository.addPromotedNotification(model)

            `when`(mockAlertEntry.key).thenReturn("key1")
            `when`(mockAlertEntry.notification).thenReturn(mockNotification)
            `when`(mockNotification.isPromotedOngoing).thenReturn(false)

            repository.updateFromAlertEntries(
                mockContext,
                listOf(mockAlertEntry),
                    isHeadsUp = false,
                remove = false
            )

            val list = repository.promotedNotifications.first()
            assertThat(list).isEmpty()
        }
    }

    @Test
    fun testUpdateFromAlertEntries_removesWhenRemoveIsTrue() {
        runBlocking {
            val model = PromotedNotificationModel(
                key = "key1",
                isHeadsUp = false,
                postTime = 123L,
                shortCriticalText = "text",
                smallIcon = null,
                appName = "app"
            )
            repository.addPromotedNotification(model)

            `when`(mockAlertEntry.key).thenReturn("key1")
            `when`(mockAlertEntry.notification).thenReturn(mockNotification)

            repository.updateFromAlertEntries(
                mockContext,
                listOf(mockAlertEntry),
                    isHeadsUp = false,
                remove = true
            )

            val list = repository.promotedNotifications.first()
            assertThat(list).isEmpty()
        }
    }
}
