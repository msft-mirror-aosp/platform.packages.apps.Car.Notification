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

import android.annotation.ColorInt;
import android.annotation.Nullable;
import android.app.Notification;
import android.app.Person;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.View;
import android.widget.DateTimeView;
import android.widget.ImageView;
import android.widget.TextView;

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

    @ColorInt
    private final int mDefaultPrimaryForegroundColor;
    private final Context mContext;
    private final CarNotificationBodyView mBodyView;
    private final CarNotificationHeaderView mHeaderView;
    private final CarNotificationActionsView mActionsView;
    private final TextView mTitleView;
    private final DateTimeView mTimeView;
    private final TextView mMessageView;
    private final TextView mUnshownCountView;
    private final ImageView mAvatarView;
    private final String mNewMessageText;
    private final int mMaxMessageCount;
    private final int mMaxLineCount;

    private NotificationClickHandlerFactory mClickHandlerFactory;

    public MessageNotificationViewHolder(
            View view, NotificationClickHandlerFactory clickHandlerFactory) {
        super(view, clickHandlerFactory);
        mContext = view.getContext();
        mDefaultPrimaryForegroundColor = mContext.getColor(R.color.primary_text_color);
        mHeaderView = view.findViewById(R.id.notification_header);
        mActionsView = view.findViewById(R.id.notification_actions);
        mTitleView = view.findViewById(R.id.notification_body_title);
        mTimeView = view.findViewById(R.id.in_group_time_stamp);
        if (mTimeView != null) {
            // HUN template does not include the time stamp.
            mTimeView.setShowRelativeTime(true);
        }
        mMessageView = view.findViewById(R.id.notification_body_content);
        mBodyView = view.findViewById(R.id.notification_body);
        mUnshownCountView = view.findViewById(R.id.message_count);
        mAvatarView = view.findViewById(R.id.notification_body_icon);
        mNewMessageText = mContext.getString(R.string.restricted_hun_message_content);
        mMaxMessageCount =
                mContext.getResources().getInteger(R.integer.config_maxNumberOfMessagesInPanel);
        mMaxLineCount =
                mContext.getResources().getInteger(R.integer.config_maxNumberOfMessageLinesInPanel);
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
        Notification notification = alertEntry.getNotification();
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

        if (avatar == null) {
            avatar = notification.getLargeIcon();
        }

        if (!TextUtils.isEmpty(conversationTitle)) {
            mTitleView.setVisibility(View.VISIBLE);
            mTitleView.setText(conversationTitle);
        }

        if (isInGroup && notification.showsTime()) {
            mTimeView.setVisibility(View.VISIBLE);
            mTimeView.setTime(notification.when);
        }

        if (!TextUtils.isEmpty(messageText)) {
            messageText = PreprocessingManager.getInstance(mContext).trimText(messageText);
            mMessageView.setVisibility(View.VISIBLE);
            mMessageView.setText(messageText);
        }

        if (avatar != null) {
            mAvatarView.setVisibility(View.VISIBLE);
            mAvatarView.setImageIcon(avatar);
        }

        int unshownCount = messageCount - 1;
        String unshownCountText;
        if (!isRestricted && unshownCount > 0 && !isHeadsUp && messageStyleFlag) {
            // If car is in parked mode, notification is not HUN, more messages exist
            // and notification used message style, then add an expandable message count section.
            unshownCountText = mContext.getString(R.string.message_unshown_count, unshownCount);
            mUnshownCountView.setVisibility(View.VISIBLE);
            mUnshownCountView.setText(unshownCountText);
            mUnshownCountView.setTextColor(getAccentColor());
            mUnshownCountView.setOnClickListener(
                    getCountViewOnClickListener(unshownCount, messages, isGroupConversation));
        }

        if (isHeadsUp) {
            mBodyView.bindTitleAndMessage(conversationTitle, messageText);
        }
    }

    @Override
    void reset() {
        super.reset();
        mTitleView.setVisibility(View.GONE);
        mTitleView.setText(null);
        if (mTimeView != null) {
            mTimeView.setVisibility(View.GONE);
        }

        mMessageView.setVisibility(View.GONE);
        mMessageView.setText(null);

        mAvatarView.setVisibility(View.GONE);
        mAvatarView.setImageIcon(null);

        if (mUnshownCountView != null) {
            // unshown count doesn't exist in HUN.
            mUnshownCountView.setVisibility(View.GONE);
            mUnshownCountView.setText(null);
            mUnshownCountView.setOnClickListener(null);
            mUnshownCountView.setTextColor(mDefaultPrimaryForegroundColor);
        }
    }

    private View.OnClickListener getCountViewOnClickListener(int unshownCount,
            @Nullable List<Notification.MessagingStyle.Message> messages,
            boolean isGroupConversation) {
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
            mMessageView.setMaxLines(mMaxLineCount);

            if (finalUnshownCount <= 0) {
                mUnshownCountView.setVisibility(View.GONE);
            } else {
                String unshownCountText =
                        mContext.getString(R.string.restricted_numbered_message_content,
                                finalUnshownCount);
                mUnshownCountView.setText(unshownCountText);
            }

            mMessageView.setText(finalMessage);
            mUnshownCountView.setOnClickListener(null);
        };
    }
}
