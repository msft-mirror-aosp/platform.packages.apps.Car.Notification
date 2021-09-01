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

import static android.app.Notification.EXTRA_IS_GROUP_CONVERSATION;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.View;

import androidx.core.app.NotificationCompat.MessagingStyle;

import com.android.car.notification.AlertEntry;
import com.android.car.notification.NotificationClickHandlerFactory;
import com.android.car.notification.PreprocessingManager;
import com.android.car.notification.R;

import java.util.List;

/**
 * Messaging notification template that displays a messaging notification and a voice reply button.
 */
public class MessageNotificationViewHolder extends CarNotificationBaseViewHolder {
    private static final String SENDER_TITLE_SEPARATOR = " • ";
    private static final String SENDER_BODY_SEPARATOR = ": ";
    private static final String NEW_LINE = "\n";

    private final Context mContext;
    private final CarNotificationBodyView mBodyView;
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationActionsView mActionsView;
    private final String mNewMessageText;
    private final int mMaxMessageCount;
    private final int mMaxLineCount;
    private final Drawable mGroupIcon;

    private NotificationClickHandlerFactory mClickHandlerFactory;

    public MessageNotificationViewHolder(
            View view, NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mHeaderView = view.findViewById(R.id.notification_header);
        mContext = view.getContext();
        mActionsView = view.findViewById(R.id.notification_actions);
        mBodyView = view.findViewById(R.id.notification_body);

        mNewMessageText = mContext.getString(R.string.restricted_hun_message_content);
        mMaxMessageCount =
                mContext.getResources().getInteger(R.integer.config_maxNumberOfMessagesInPanel);
        mMaxLineCount =
                mContext.getResources().getInteger(R.integer.config_maxNumberOfMessageLinesInPanel);
        mGroupIcon = mContext.getDrawable(R.drawable.ic_group);

        mClickHandlerFactory = clickHandlerFactory;
    }

    /**
     * Binds a {@link AlertEntry} to a messaging car notification template without
     * UX restriction.
     */
    @Override
    public void bind(AlertEntry alertEntry, boolean isInGroup,
            boolean isHeadsUp) {
        super.bind(alertEntry, isInGroup, isHeadsUp);
        bindBody(alertEntry, isInGroup, /* isRestricted= */ false, isHeadsUp);
        mHeaderView.bind(alertEntry, isInGroup);
        mActionsView.bind(mClickHandlerFactory, alertEntry);
    }

    /**
     * Binds a {@link AlertEntry} to a messaging car notification template with
     * UX restriction.
     */
    public void bindRestricted(AlertEntry alertEntry, boolean isInGroup, boolean isHeadsUp) {
        super.bind(alertEntry, isInGroup, isHeadsUp);
        bindBody(alertEntry, isInGroup, /* isRestricted= */ true, isHeadsUp);
        mHeaderView.bind(alertEntry, isInGroup);

        mActionsView.bind(mClickHandlerFactory, alertEntry);
    }

    /**
     * Private method that binds the data to the view.
     */
    private void bindBody(AlertEntry alertEntry, boolean isInGroup, boolean isRestricted,
            boolean isHeadsUp) {
        mBodyView.setCountTextColor(getAccentColor());
        Notification notification = alertEntry.getNotification();
        StatusBarNotification sbn = alertEntry.getStatusBarNotification();
        Bundle extras = notification.extras;
        CharSequence messageText = null;
        CharSequence conversationTitle = null;
        Icon avatar = null;
        Integer messageCount = null;

        List<Notification.MessagingStyle.Message> messages = null;
        MessagingStyle messagingStyle =
                MessagingStyle.extractMessagingStyleFromNotification(notification);
        if (messagingStyle != null) {
            conversationTitle = messagingStyle.getConversationTitle();
        }
        boolean isGroupConversation =
                ((messagingStyle != null && messagingStyle.isGroupConversation())
                        || extras.getBoolean(EXTRA_IS_GROUP_CONVERSATION));

        Parcelable[] messagesData = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messagesData != null) {
            messages = Notification.MessagingStyle.Message
                    .getMessagesFromBundleArray(messagesData);
            if (messages != null && !messages.isEmpty()) {
                messageCount = messages.size();
                // Use the latest message
                Notification.MessagingStyle.Message message = messages.get(messages.size() - 1);
                messageText = message.getText();
                Person sender = message.getSenderPerson();
                if (sender != null) {
                    avatar = sender.getIcon();
                }
                CharSequence senderName =
                        (sender != null ? sender.getName() : message.getSender());
                if (isGroupConversation && conversationTitle != null && isHeadsUp) {
                    // If conversation title has been set, conversation is a group conversation
                    // and notification is a HUN, then prepend sender's name to title.
                    conversationTitle = senderName + SENDER_TITLE_SEPARATOR + conversationTitle;
                } else if (conversationTitle == null) {
                    // If conversation title has not been set, set it as sender's name.
                    conversationTitle = senderName;
                }

                if (!isHeadsUp && isGroupConversation) {
                    // If conversation is a group conversation and notification is not a HUN,
                    // then prepend sender's name to title.
                    messageText = senderName + SENDER_BODY_SEPARATOR + messageText;
                }
            }
        }

        boolean messageStyleFlag = true;
        // App did not use messaging style, fall back to standard fields
        if (messageCount == null) {
            messageStyleFlag = false;
            messageCount = notification.number;
            if (messageCount == 0) {
                // A notification should at least represent 1 message
                messageCount = 1;
            }
        }

        if (TextUtils.isEmpty(conversationTitle)) {
            conversationTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
        }
        if (isRestricted) {
            if (isHeadsUp || messageCount == 1) {
                messageText = mNewMessageText;
            } else {
                messageText =
                        mContext.getString(R.string.restricted_numbered_message_content,
                                messageCount);
            }
        } else if (TextUtils.isEmpty(messageText)) {
            messageText = extras.getCharSequence(Notification.EXTRA_TEXT);
        }
        if (!TextUtils.isEmpty(messageText)) {
            messageText = PreprocessingManager.getInstance(mContext).trimText(messageText);
        }

        if (avatar == null) {
            avatar = notification.getLargeIcon();
        }

        Long when;
        if (notification.showsTime()) {
            when = notification.when;
        } else {
            when = null;
        }

        Drawable groupIcon;
        if (isGroupConversation) {
            groupIcon = mGroupIcon;
        } else {
            groupIcon = null;
        }

        int unshownCount = messageCount - 1;
        String unshownCountText = null;
        if (!isRestricted && unshownCount > 0 && !isHeadsUp && messageStyleFlag) {
            unshownCountText = mContext.getString(R.string.message_unshown_count, unshownCount);
            View.OnClickListener listener =
                    getCountViewOnClickListener(unshownCount, messages, isGroupConversation,
                            sbn, conversationTitle, avatar, groupIcon, when);
            mBodyView.setCountOnClickListener(listener);
        }

        mBodyView.bind(conversationTitle, messageText, loadAppLauncherIcon(sbn), avatar, groupIcon,
                unshownCountText, when);
    }

    @Override
    void reset() {
        super.reset();
        mBodyView.reset();
        mHeaderView.reset();
        mActionsView.reset();
    }

    private View.OnClickListener getCountViewOnClickListener(int unshownCount,
            @Nullable List<Notification.MessagingStyle.Message> messages,
            boolean isGroupConversation, StatusBarNotification sbn, CharSequence title,
            @Nullable Icon avatar, @Nullable Drawable groupIcon, @Nullable Long when) {
        StringBuilder builder = new StringBuilder();
        for (int i = messages.size() - 1; i >= messages.size() - 1 - mMaxMessageCount && i >= 0;
                i--) {
            if (i != messages.size() - 1) {
                builder.append(NEW_LINE);
                builder.append(NEW_LINE);
            }
            unshownCount--;
            Notification.MessagingStyle.Message message = messages.get(i);
            Person sender = message.getSenderPerson();
            CharSequence senderName =
                    (sender != null ? sender.getName() : message.getSender());
            if (isGroupConversation) {
                builder.append(senderName + SENDER_BODY_SEPARATOR + message.getText());
            } else {
                builder.append(message.getText());
            }
            if (builder.toString().split(NEW_LINE).length >= mMaxLineCount) {
                break;
            }
        }
        String finalMessage = builder.toString();

        int finalUnshownCount = unshownCount;
        return view -> {
            String unshownCountText;
            if (finalUnshownCount <= 0) {
                unshownCountText = null;
            } else {
                unshownCountText = mContext.getString(R.string.restricted_numbered_message_content,
                        finalUnshownCount);
            }

            Drawable launcherIcon = loadAppLauncherIcon(sbn);
            mBodyView.bind(title, finalMessage, launcherIcon, avatar, groupIcon, unshownCountText,
                    when);
            mBodyView.setContentMaxLines(mMaxLineCount);
            mBodyView.setCountOnClickListener(null);
        };
    }
}
