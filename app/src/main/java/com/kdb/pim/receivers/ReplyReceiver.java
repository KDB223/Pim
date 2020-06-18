package com.kdb.pim.receivers;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.kdb.pim.data.Contact;
import com.kdb.pim.data.Message;
import com.kdb.pim.ui.activities.ChatActivity;
import com.kdb.pim.utils.Messenger;
import com.kdb.pim.utils.NotificationUtils;

import java.util.HashMap;

/**
 * {@link BroadcastReceiver} to handle sending replies directly from the notification {@link RemoteInput}
 */
public class ReplyReceiver extends BroadcastReceiver {
    private static final String TAG = "pim.reply";

    /**
     * Called when the user presses "send" from the notification
     * @param context Required to create intents
     * @param intent {@link Intent} containing the message typed by the user, extracted using
     *                             {@link RemoteInput#getResultsFromIntent}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle input = RemoteInput.getResultsFromIntent(intent);
        Bundle data = intent.getExtras();
        Contact contact = new Contact();
        contact.createFromMap((HashMap<String, String>) data.getSerializable("contactData"));
        String userUid = data.getString("userUid");
        String roomId = data.getString("roomId");
        Log.d(TAG, "onReceive: from: " + userUid + ", to: " + contact.getUid());
        if (input == null) {
            Intent chatIntent = new Intent(context, ChatActivity.class);
            chatIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            chatIntent.putExtras(data);
            context.startActivity(chatIntent);
            return;
        }
        Message message = new Message(userUid, contact.getUid(), input.getString("reply"), ServerValue.TIMESTAMP);
        Messenger.sendMessage(message, roomId, FirebaseDatabase.getInstance().getReference(), new Messenger.OnSentListener() {
            @Override
            public void onSuccess() {
                NotificationUtils.addMessageToNotification(
                        context, userUid, null, contact.getUid(), contact.getThumb(),
                        false, null, roomId, message.getContent(),
                        System.currentTimeMillis(), null, false
                );
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(context, "Error sending reply: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, null, context);
    }
}
