package com.kdb.pim.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.kdb.pim.Pim;
import com.kdb.pim.R;
import com.kdb.pim.receivers.ReplyReceiver;
import com.kdb.pim.ui.activities.ChatActivity;
import com.kdb.pim.utils.NotificationUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link FirebaseMessagingService} service to handle Firebase Cloud Notifications for incoming
 * messages in the background.
 */
public class PimMessagingService extends FirebaseMessagingService {
    private static final String TAG = "pim.msg.service";
    private static final String CHANNEL_ID = "messages";
    private static final int NOTIFICATION_ID = 9000;

    /**
     * Extracts message information from the data sent from FCM and creates the notification using
     * {@link NotificationUtils}
     * @param remoteMessage The notification data map
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "onMessageReceived: data: " + remoteMessage.getData());
        Map<String, String> data = remoteMessage.getData();
        String content = data.get("content");
        String fromName = data.get("fromName");
        String fromUid = data.get("fromUid");
        String toUid = data.get("toUid");
        String thumb = data.get("icon");
        String roomId = data.get("roomId");
        String messageId = data.get("messageId");
        long timestamp = Long.parseLong(data.get("timestamp"));
        boolean distracting = Boolean.parseBoolean(data.get("distracting"));

        // TODO: Cancel notification if distracting

        Person contact = new Person.Builder().setKey(fromUid).setName(fromName).build();

        // Create notification
        NotificationUtils.addMessageToNotification(this, fromUid, fromName, toUid, thumb, distracting, messageId, roomId, content, timestamp, contact, true);
    }

    /**
     * Updates the new instance ID token in the database, once a new one is generated
     * @param token The new token
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            FirebaseDatabase.getInstance().getReference().child("users")
                    .child(uid)
                    .child("deviceToken")
                    .setValue(token);
        }
    }
}
