package com.kdb.pim.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.kdb.pim.data.Contact;
import com.kdb.pim.data.Group;
import com.kdb.pim.data.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility methods to send messages and create chat rooms
 */
public class Messenger {
    private static final String TAG = "pim.messenger";

    public interface OnSentListener {
        void onSuccess();

        void onFailure(Exception e);
    }

    public interface OnCreateListener {
        void onFailure(Exception e);
    }

    /**
     * Creates a new chat room
     * @param message The first message to send in the room. Set to null for groups
     * @param rootRef Reference to database root node
     * @param listener Callback invoked after creation of room
     * @param group The {@link Group} representing the group chat for the room, if any
     * @return The roomId of the new room
     */
    public static String createRoom(@Nullable Message message, DatabaseReference rootRef, OnCreateListener listener, @Nullable Group group) {
        // Create room ID
        DatabaseReference newRoomRef = rootRef.child("rooms").push();
        String roomId = newRoomRef.getKey();

        if (group != null) {
            // Set chat as group chat
            DatabaseReference chatsRef = rootRef.child("chats").child(roomId);
            chatsRef.child("group").setValue(true);
            chatsRef.child("work").setValue(group.isWork());
            chatsRef.child("title").setValue(group.getTitle());
            chatsRef.child("desc").setValue(group.getDescription());
            chatsRef.child("created").setValue(ServerValue.TIMESTAMP);

            // Add room to all users
            DatabaseReference usersRef = rootRef.child("users");

            for (Contact c : group.getParticipants()) {
                String userUid = c.getUid();

                String roomsPath = "/" + userUid + "/rooms/" + roomId;

                Map<String, Object> roomCreateMap = new HashMap<>();
                roomCreateMap.put(roomsPath + "/group", true);
                roomCreateMap.put(roomsPath + "/lastMessageTimestamp", ServerValue.TIMESTAMP);
                usersRef.updateChildren(roomCreateMap).addOnFailureListener(listener::onFailure);

                String membersPath = "/members/" + roomId + "/" + userUid + "/typing";
                rootRef.child(membersPath).setValue(false).addOnFailureListener(listener::onFailure);
            }
        } else if (message != null) {
            DatabaseReference usersRef = rootRef.child("users");
            String userUid = message.getFrom();
            String contactUid = message.getTo();

            String roomsUserPath = "/" + userUid + "/rooms/" + roomId + "/";
            String roomsContactPath = "/" + contactUid + "/rooms/" + roomId + "/";

            Map<String, Object> roomCreateMap = new HashMap<>();
            // /users/user-123/rooms/room-991: contact-456

            roomCreateMap.put(roomsUserPath, contactUid);
            roomCreateMap.put(roomsContactPath, userUid);
            usersRef.updateChildren(roomCreateMap).addOnFailureListener(listener::onFailure);

            // For checking room members and typing status
            String membersUserPath = "/members/" + roomId + "/" + userUid + "/typing";
            String membersContactPath = "/members/" + roomId + "/" + contactUid + "/typing";
            Map<String, Object> membersMap = new HashMap<>();
            membersMap.put(membersUserPath, false);
            membersMap.put(membersContactPath, false);
            rootRef.updateChildren(membersMap).addOnFailureListener(listener::onFailure);
        }
        return roomId;
    }

    /**
     * Sends a message to a chat room
     * @param message The {@link Message} to send
     * @param roomId The roomId of the chat room to send {@param message}
     * @param rootRef Reference to database root node
     * @param listener Callback invoked after {@param message} is sent
     * @param group The {@link Group} representing the group chat for the room, if any
     * @param context {@link Context} required for getting application SHA-1 signature
     */
    public static void sendMessage(Message message, String roomId, DatabaseReference rootRef, OnSentListener listener, @Nullable Group group, Context context) {
        Log.d(TAG, "sendMessage: SEnding " + message.getContent() + " to " + roomId);

        DatabaseReference roomRef = rootRef.child("rooms").child(roomId);
        DatabaseReference usersRef = rootRef.child("users");
        DatabaseReference messageRef = roomRef.push();

        if (group != null) {
            for (Contact c : group.getParticipants()) {
                String userUid = c.getUid();

                String roomsUserPath = "/" + userUid + "/rooms/" + roomId + "/lastMessageTimestamp";
                usersRef.child(roomsUserPath).setValue(message.getServerTimestamp());
            }
        } else {
            // Add message timestamp to users/{user)/rooms/{room}/lastMessageTimestamp
            String roomsUserPath = "/" + message.getFrom() + "/rooms/" + roomId + "/lastMessageTimestamp";
            String roomsContactPath = "/" + message.getTo() + "/rooms/" + roomId + "/lastMessageTimestamp";
            Map<String, Object> timestampUpdateMap = new HashMap<>();
            timestampUpdateMap.put(roomsUserPath, message.getServerTimestamp());
            timestampUpdateMap.put(roomsContactPath, message.getServerTimestamp());
            usersRef.updateChildren(timestampUpdateMap);
        }

        // Determine if content YouTube links are distracting
        MessageUtils.checkMessageForContent(message, distracting -> {
            message.setDistracting(distracting);
            messageRef.setValue(message)
                    .addOnFailureListener(listener::onFailure)
                    .addOnSuccessListener(aVoid -> listener.onSuccess());

            // Add message to chats (contains last message for each room)
            DatabaseReference chatMessageRef = rootRef.child("chats").child(roomId).child("lastMessage");

            chatMessageRef.setValue(message).addOnFailureListener(listener::onFailure);
        }, context);

    }
}
