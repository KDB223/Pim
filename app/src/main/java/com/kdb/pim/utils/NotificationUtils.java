package com.kdb.pim.utils;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import com.google.firebase.auth.FirebaseAuth;
import com.kdb.pim.Pim;
import com.kdb.pim.R;
import com.kdb.pim.data.Message;
import com.kdb.pim.receivers.ReplyReceiver;
import com.kdb.pim.ui.activities.ChatActivity;

import java.util.HashMap;

import static android.content.Context.NOTIFICATION_SERVICE;

/**
 * Utility class to create and manage notifications for incoming messages
 */
public class NotificationUtils {
    private static final String CHANNEL_ID = "msg";
    private static final String CHANNEL_ID_SILENT = "msg-silent";
    private static final int NOTIFICATION_ID = 9000;
    private static final String TAG = "pim.notification";

    /**
     * Adds a new message to the notification for the chat room.
     * Uses {@link NotificationCompat.MessagingStyle} on API 24 and above
     * @param context Context required for NotificationManager
     * @param fromUid The UID of the user the message is from
     * @param fromName The name of the user the message is from
     * @param toUid The UID of the user the message is to
     * @param thumb The name of the user the message is to
     * @param distracting Whether the message is distracting or not
     * @param messageId The ID of the {@link Message} object
     * @param roomId The ID of the chat room the message is in
     * @param content Message content
     * @param timestamp Message timestamp
     * @param person {@link Person} the message is from (API 24+)
     * @param alert Whether the notification should be alerting or silent
     */
    public static void addMessageToNotification(Context context, String fromUid, @Nullable String fromName, String toUid, String thumb, boolean distracting, String messageId, String roomId, String content, long timestamp, @Nullable Person person, boolean alert) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Add message to MessagingStyle
            boolean found = false;
            for (StatusBarNotification notification : notificationManager.getActiveNotifications()) {
                if (notification.getTag().equals(roomId)) {
                    // Add message to room notification
                    NotificationCompat.MessagingStyle style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification.getNotification());
                    style.addMessage(content, timestamp, person);
                    createNotification(context, fromUid, toUid, content, distracting, fromName, thumb, roomId, messageId, timestamp, style, alert);
                    found = true;
                }
            }
            if (!found) {
                createNotification(context, fromUid, toUid, content, distracting, fromName, thumb, roomId, messageId, timestamp, null, alert);
            }
        } else {
            createNotification(context, fromUid, toUid, content, distracting, fromName, thumb, roomId, messageId, timestamp, null, true);
        }
    }

    /**
     * Creates a new notification for an incoming message
     * @param context Context required for NotificationManager
     * @param fromUid The UID of the user the message is from
     * @param toUid The UID of the user the message is to
     * @param content Message content
     * @param distracting Whether the message is distracting or not
     * @param fromName The name of the user the message is from
     * @param thumb URL of the message sender's profile picture thumbnail
     * @param roomId The ID of the chat room the message is in
     * @param messageId The ID of the {@link Message} object
     * @param timestamp Message timestamp
     * @param style {@link NotificationCompat.MessagingStyle} object, used in API 24+
     * @param alert Whether the notification should be alerting or silent
     */
    private static void createNotification(Context context, String fromUid, String toUid, String content, boolean distracting, @Nullable String fromName, String thumb, String roomId, String messageId, long timestamp, @Nullable NotificationCompat.MessagingStyle style, boolean alert) {
        if (Pim.getCurrentRoom() == null || !Pim.getCurrentRoom().equals(roomId)) {
            createNotificationChannel(context);
            int rc = (int) System.currentTimeMillis();

            String userUid = toUid;
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                userUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }

            Intent intent = new Intent(context, ChatActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            HashMap<String, String> contactMap = new HashMap<>();
            contactMap.put("contactName", fromName);
            contactMap.put("contactThumb", thumb);
            contactMap.put("contactUid", userUid.equals(toUid) ? fromUid : toUid);
            //contactMap.put("contactPhone", fromName);
            //contactMap.put("contactStatus", fromName);
            Bundle bundle = new Bundle();
            bundle.putSerializable("contactData", contactMap);
            bundle.putString("roomId", roomId);
            bundle.putString("userUid", userUid);
            intent.putExtras(bundle);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, rc, intent, 0);

            // TODO: Images

            if (style == null) {
                Person contact = new Person.Builder().setKey(fromUid).setName(fromName).build();
                style = new NotificationCompat.MessagingStyle(new Person.Builder().setName("You").build())
                        .addMessage(content, timestamp, contact);
            }
            RemoteInput remoteInput = new RemoteInput.Builder("reply")
                    .setLabel("Reply...")
                    .addExtras(bundle)
                    .build();

            Intent replyIntent = new Intent(context, ReplyReceiver.class);
            replyIntent.putExtras(bundle);
            PendingIntent replyPendingIntent = PendingIntent.getBroadcast(context, rc, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Action reply = new NotificationCompat.Action.Builder(R.drawable.ic_reply, "Reply", replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .build();

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification_icon)
                    .setContentTitle(fromName)
                    .addAction(reply)
                    .setColor(context.getResources().getColor(R.color.colorAccent))
                    .setContentText(content)
                    .setContentIntent(pendingIntent)
                    .setStyle(style)
                    .setChannelId(alert ? CHANNEL_ID : CHANNEL_ID_SILENT)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    //.setOnlyAlertOnce(true)
                    .setAutoCancel(true);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context.getApplicationContext());
            notificationManager.notify(roomId, NOTIFICATION_ID, builder.build());
        }
    }

    /**
     * Creates the message notification channel (only on API 26 and above)
     * @param context Context required for NotificationManager
     */
    @TargetApi(Build.VERSION_CODES.O)
    private static void createNotificationChannel(Context context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Message notifications";
            String description = "Notifications for new incoming messages";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channelMessages = new NotificationChannel(CHANNEL_ID, name, importance);
            channelMessages.setDescription(description);
            name = "Silent notifications";
            NotificationChannel channelReplies = new NotificationChannel(CHANNEL_ID_SILENT, name, importance);
            channelReplies.setSound(null, null);
            channelReplies.enableVibration(false);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channelMessages);
            notificationManager.createNotificationChannel(channelReplies);
        }
    }
}
