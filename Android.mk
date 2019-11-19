# Copyright (C) 2018 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

ifneq ($(TARGET_BUILD_PDK), true)

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := CarNotification

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true

LOCAL_USE_AAPT2 := true

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.cardview_cardview \
    androidx-constraintlayout_constraintlayout \
    androidx.palette_palette \
    androidx.recyclerview_recyclerview \
    car-assist-client-lib \
    car-apps-common \
    car-ui-lib

# Including the resources for the static android libraries allows to pick up their static overlays.
LOCAL_RESOURCE_DIR += \
    $(LOCAL_PATH)/../libs/car-assist-client-lib/res \
    $(LOCAL_PATH)/../libs/car-apps-common/res

LOCAL_STATIC_JAVA_LIBRARIES += \
    android.car.userlib \
    androidx-constraintlayout_constraintlayout-solver

LOCAL_JAVA_LIBRARIES += \
    android.car

include $(BUILD_PACKAGE)

# As Lib
include $(CLEAR_VARS)
LOCAL_MODULE := CarNotificationLib
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true

LOCAL_USE_AAPT2 := true
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.cardview_cardview \
    androidx-constraintlayout_constraintlayout \
    androidx.palette_palette \
    androidx.recyclerview_recyclerview \
    car-assist-client-lib \
    car-apps-common \
    car-ui-lib

LOCAL_STATIC_JAVA_LIBRARIES += \
    android.car.userlib \
    androidx-constraintlayout_constraintlayout-solver

LOCAL_JAVA_LIBRARIES += \
    android.car

include $(BUILD_STATIC_JAVA_LIBRARY)

###################################################################################
# Duplicate of CarNotification which includes testing only resources for Robolectric #
###################################################################################

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PACKAGE_NAME := CarNotificationForTesting

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true

LOCAL_USE_AAPT2 := true

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.cardview_cardview \
    androidx-constraintlayout_constraintlayout \
    androidx.palette_palette \
    androidx.recyclerview_recyclerview \
    car-assist-client-lib \
    car-apps-common \
    car-ui-lib

LOCAL_STATIC_JAVA_LIBRARIES += \
    android.car.userlib \
    androidx-constraintlayout_constraintlayout-solver

LOCAL_JAVA_LIBRARIES += \
    android.car

include $(BUILD_PACKAGE)

# As Lib
include $(CLEAR_VARS)
LOCAL_MODULE := CarNotificationModuleForTesting
LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_CERTIFICATE := platform

LOCAL_MODULE_TAGS := optional

LOCAL_PRIVILEGED_MODULE := true

LOCAL_USE_AAPT2 := true
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_DEX_PREOPT := false

LOCAL_STATIC_ANDROID_LIBRARIES += \
    androidx.cardview_cardview \
    androidx-constraintlayout_constraintlayout \
    androidx.palette_palette \
    androidx.recyclerview_recyclerview \
    car-assist-client-lib \
    car-apps-common \
    car-ui-lib

LOCAL_STATIC_JAVA_LIBRARIES += \
    android.car.userlib \
    androidx-constraintlayout_constraintlayout-solver

LOCAL_JAVA_LIBRARIES += \
    android.car

include $(BUILD_STATIC_JAVA_LIBRARY)


# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
    include $(call first-makefiles-under, $(LOCAL_PATH))
endif

endif

