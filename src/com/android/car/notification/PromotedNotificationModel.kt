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

import android.graphics.drawable.Icon

/**
 * Relevant data about a current promoted ongoing notification.
 */
data class PromotedNotificationModel(
    val key: String,
    val isHeadsUp: Boolean,
    val postTime: Long,
    val shortCriticalText: String?,
    val smallIcon: Icon?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PromotedNotificationModel) return false

        if (key != other.key) return false
        if (isHeadsUp != other.isHeadsUp) return false
        if (postTime != other.postTime) return false
        if (shortCriticalText != other.shortCriticalText) return false

        return areIconsEqual(other)
    }

    fun areIconsEqual(other: PromotedNotificationModel): Boolean {
        if (smallIcon == other.smallIcon) {
            return true
        }
        if (smallIcon == null || other.smallIcon == null) {
            return false
        }
        if (smallIcon.sameAs(other.smallIcon)) {
            return true
        }
        val type = smallIcon.type
        if (type != other.smallIcon.type) {
            return false
        }
        if (type == Icon.TYPE_BITMAP || type == Icon.TYPE_ADAPTIVE_BITMAP) {
            val bitmap = smallIcon.bitmap
            val otherBitmap = other.smallIcon.bitmap
            return bitmap.getWidth() == otherBitmap.getWidth() &&
                    bitmap.getHeight() == otherBitmap.getHeight() &&
                    bitmap.getConfig() == otherBitmap.getConfig() &&
                    bitmap.getGenerationId() == otherBitmap.getGenerationId()
        }
        return false
    }
}
