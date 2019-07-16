package com.android.car.notification;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;


/**
 * Layout that contains Car Notifications.
 *
 * It does some extra setup in the onFinishInflate method because it may not get used from an
 * activity where one would normally attach RecyclerViews
 */
public class CarNotificationView extends ConstraintLayout
        implements CarUxRestrictionsManager.OnUxRestrictionsChangedListener {

    private CarNotificationViewAdapter mAdapter;
    private Context mContext;
    private LinearLayoutManager mLayoutManager;
    private NotificationClickHandlerFactory mClickHandlerFactory;
    private NotificationDataManager mNotificationDataManager;
    private boolean mIsClearAllActive = false;

    public CarNotificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    /**
     * Attaches the CarNotificationViewAdapter and CarNotificationItemTouchListener to the
     * notification list.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        RecyclerView listView = findViewById(R.id.notifications);

        listView.setClipChildren(false);
        mLayoutManager = new LinearLayoutManager(mContext);
        listView.setLayoutManager(mLayoutManager);
        listView.addItemDecoration(new TopAndBottomOffsetDecoration(
                mContext.getResources().getDimensionPixelSize(R.dimen.item_spacing)));
        listView.addItemDecoration(new ItemSpacingDecoration(
                mContext.getResources().getDimensionPixelSize(R.dimen.item_spacing)));
        mAdapter = new CarNotificationViewAdapter(mContext, /* isGroupNotificationAdapter= */
                false, this::startClearAllNotifications);
        listView.setAdapter(mAdapter);
        listView.addOnItemTouchListener(new CarNotificationItemTouchListener(mContext, mAdapter));

        listView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // RecyclerView is not currently scrolling.
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    setVisibleNotificationsAsSeen();
                }
            }
        });

        Button clearAllButton = findViewById(R.id.clear_all_button);

        if (clearAllButton != null) {
            clearAllButton.setOnClickListener(v -> startClearAllNotifications());
        }
    }

    /**
     * Updates notifications and update views.
     */
    public void setNotifications(List<NotificationGroup> notifications) {
        mAdapter.setNotifications(notifications, /* setRecyclerViewListHeaderAndFooter= */ true);
    }

    /**
     * Collapses all expanded groups.
     */
    public void collapseAllGroups() {
        mAdapter.collapseAllGroups();
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        mAdapter.setCarUxRestrictions(restrictionInfo);
    }

    /**
     * Sets the NotificationClickHandlerFactory that allows for a hook to run a block off code
     * when  the notification is clicked. This is useful to dismiss a screen after
     * a notification list clicked.
     */
    public void setClickHandlerFactory(NotificationClickHandlerFactory clickHandlerFactory) {
        mClickHandlerFactory = clickHandlerFactory;
        mAdapter.setClickHandlerFactory(clickHandlerFactory);
    }

    /**
     * Sets NotificationDataManager that handles additional states for notifications such as "seen",
     * and muting a messaging type notification.
     *
     * @param notificationDataManager An instance of NotificationDataManager.
     */
    public void setNotificationDataManager(NotificationDataManager notificationDataManager) {
        mNotificationDataManager = notificationDataManager;
        mAdapter.setNotificationDataManager(notificationDataManager);
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add a top offset to the first item and bottom
     * offset to the last item in the RecyclerView it is added to.
     */
    private static class TopAndBottomOffsetDecoration extends RecyclerView.ItemDecoration {
        private int mTopAndBottomOffset;

        private TopAndBottomOffsetDecoration(int topOffset) {
            mTopAndBottomOffset = topOffset;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            if (position == 0) {
                outRect.top = mTopAndBottomOffset;
            }
            if (position == state.getItemCount() - 1) {
                outRect.bottom = mTopAndBottomOffset;
            }
        }
    }

    /**
     * Identifies dismissible notifications views and animates them out in the order
     * specified in config. Calls finishClearNotifications on animation end.
     */
    private void startClearAllNotifications() {
        // Prevent invoking the click listeners again until the current clear all flow is complete.
        if (mIsClearAllActive) {
            return;
        }
        mIsClearAllActive = true;

        List<View> dismissibleNotificationViews = getDismissibleNotificationViews();

        if (dismissibleNotificationViews.isEmpty()) {
            finishClearAllNotifications();
            return;
        }

        AnimatorSet animatorSet = createDismissAnimation(dismissibleNotificationViews);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                finishClearAllNotifications();
            }
        });
        animatorSet.start();
    }

    /**
     * Returns the views of dismissible notifications sorted so that their positions are in the
     * ascending order.
     */
    private List<View> getDismissibleNotificationViews() {
        RecyclerView listView = findViewById(R.id.notifications);
        TreeMap<Integer, View> dismissibleNotificationViews = new TreeMap<>();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View currentChildView = listView.getChildAt(i);
            RecyclerView.ViewHolder holder = listView.getChildViewHolder(currentChildView);
            int position = holder.getLayoutPosition();
            if (mAdapter.isDismissible(position)) {
                dismissibleNotificationViews.put(position, currentChildView);
            }
        }
        List<View> dismissibleNotificationViewsSorted =
                new ArrayList<>(dismissibleNotificationViews.values());

        return dismissibleNotificationViewsSorted;
    }

    /**
     * Returns {@link AnimatorSet} for dismissing notifications from the clear all event.
     */
    private AnimatorSet createDismissAnimation(List<View> dismissibleNotificationViews) {
        ArrayList<Animator> animators = new ArrayList<>();
        boolean dismissFromBottomUp = getContext().getResources().getBoolean(
                R.bool.config_clearAllNotificationsAnimationFromBottomUp);
        int delayInterval = getContext().getResources().getInteger(
                R.integer.clear_all_notifications_animation_delay_interval_ms);
        for (int i = 0; i < dismissibleNotificationViews.size(); i++) {
            View currentView = dismissibleNotificationViews.get(i);
            ObjectAnimator animator = (ObjectAnimator) AnimatorInflater.loadAnimator(mContext,
                    R.animator.clear_all_animate_out);
            animator.setTarget(currentView);

            /*
             * Each animator is assigned a different start delay value in order to generate the
             * animation effect of dismissing notifications one by one.
             * Therefore, the delay calculation depends on whether the notifications are
             * dismissed from bottom up or from top down.
             */
            int delayMultiplier = dismissFromBottomUp ? dismissibleNotificationViews.size() - i : i;
            int delay = delayInterval * delayMultiplier;

            animator.setStartDelay(delay);
            animators.add(animator);
        }
        ObjectAnimator[] animatorsArray = animators.toArray(new ObjectAnimator[animators.size()]);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animatorsArray);

        return animatorSet;
    }

    /**
     * Clears all notifications with {@link com.android.internal.statusbar.IStatusBarService} and
     * optionally collapses the shade panel.
     */
    private void finishClearAllNotifications() {
        boolean collapsePanel = getContext().getResources().getBoolean(
                R.bool.config_collapseShadePanelAfterClearAllNotifications);
        int collapsePanelDelay = getContext().getResources().getInteger(
                R.integer.delay_between_clear_all_notifications_end_and_collapse_shade_panel_ms);
        mClickHandlerFactory.clearAllNotifications();

        if (collapsePanel) {
            Handler handler = getHandler();
            if (handler != null) {
                handler.postDelayed(() -> {
                    mClickHandlerFactory.collapsePanel();
                }, collapsePanelDelay);
            }
        }

        mIsClearAllActive = false;
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add spacing between each item in the
     * RecyclerView that it is added to.
     */
    private static class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private int mItemSpacing;

        private ItemSpacingDecoration(int itemSpacing) {
            mItemSpacing = itemSpacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            // Skip offset for last item.
            if (position == state.getItemCount() - 1) {
                return;
            }

            outRect.bottom = mItemSpacing;
        }
    }

    /**
     * Sets currently visible notifications as "seen".
     */
    public void setVisibleNotificationsAsSeen() {
        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = mLayoutManager.findLastVisibleItemPosition();

        // No visible items are found.
        if (firstVisible == RecyclerView.NO_POSITION) return;


        for (int i = firstVisible; i <= lastVisible; i++) {
            mAdapter.setNotificationAsSeen(i);
        }
    }
}
