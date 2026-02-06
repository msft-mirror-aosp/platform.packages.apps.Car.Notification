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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository with information about current promoted ongoing notifications.
 */
class PromotedNotificationsRepository private constructor() {
    private val _promotedNotifications =
        MutableStateFlow<List<PromotedNotificationModel>>(emptyList())
    val promotedNotifications: Flow<List<PromotedNotificationModel>> =
        _promotedNotifications.asStateFlow()

    /**
     * Add a promoted notification to the repository. If the key already exists, the existing entry
     * will be updated.
     */
    fun addPromotedNotification(notification: PromotedNotificationModel) {
        _promotedNotifications.update { currentList ->
            if (currentList.any { it.key == notification.key }) {
                currentList.map {
                    if (it.key == notification.key) notification else it
                }
            } else {
                currentList + notification
            }
        }
    }

    /**
     * Remove a promoted notification from the repository based on its key.
     */
    fun removePromotedNotification(key: String) {
        _promotedNotifications.update { currentList ->
            currentList.filter { it.key != key }
        }
    }

    /**
     * Clear all notifications from the repository.
     */
    fun clearPromotedNotifications() {
        _promotedNotifications.value = emptyList()
    }

    /**
     * Given a list of alert entries, add or remove them to the repository based on the passed
     * in parameters and whether or not the notification is considered promoted.
     */
    fun updateFromAlertEntries(
        alertEntries: List<AlertEntry>,
        isHeadsUp: Boolean,
        remove: Boolean
    ) {
        for (entry in alertEntries) {
            if (remove || entry.notification == null || !entry.notification.isPromotedOngoing) {
                removePromotedNotification(entry.key)
            } else {
                addPromotedNotification(
                    PromotedNotificationModel(
                        entry.key,
                        isHeadsUp,
                        entry.postTime,
                        entry.notification.shortCriticalText,
                        entry.notification.smallIcon
                    )
                )
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PromotedNotificationsRepository? = null

        fun getInstance(): PromotedNotificationsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PromotedNotificationsRepository().also { INSTANCE = it }
            }
        }
    }
}
