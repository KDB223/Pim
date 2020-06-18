package com.kdb.pim.utils;

import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

/**
 * Utility class to manage "online" presence features of the app
 */
public class PresenceUtils {
    private static final String TAG = "pim.presence";
    private static DatabaseReference connectedRef;
    private static boolean isOnline;

    /**
     * Integer variable to keep track of how many activities have been launched or "started". Similar
     * to a semaphore, it is incremented each time a new activity is started and decremented when it
     * is stopped/closed. If it drops to 0 or below, the user has left the app and can be now
     * considered offline.
     */
    public static int starts = -1;

    /**
     * Sets the "online" status of the user and update it in the database under the 'users' node.
     * {@value starts} is incremented if {@param online} is true and decremented if false. It also
     * sets up a connection reference with the database to track the connection to the
     * database through the internet.
     * @param user Currently signed-in {@link FirebaseUser}
     * @param online Boolean representing if the user is online or offline
     */
    public static void setOnline(@Nullable FirebaseUser user, boolean online) {
        if (user != null) {
            isOnline = online;
            if (!online) {
                // Update "last seen" time
                setLastSeenNow(user);
            }
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid());
            userRef.child("online").onDisconnect().setValue(false);

            if (!online) {
                starts--;
                if (starts == 0) {
                    // starts is now 0, user has left the app
                    userRef.child("online").setValue(false);
                }
            } else {
                if (starts == 0) {
                    // starts is 0 before incrementing, user has come back into the app
                    userRef.child("online").setValue(true);
                }
                starts++;
            }

            // Set up connection reference
            if (connectedRef == null) {
                Log.d(TAG, "setOnline: Setting up connection reference");
                connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
                connectedRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean connected = snapshot.getValue(Boolean.class);
                        if (connected) {
                            Log.d(TAG, "connected, online = " + isOnline);
                        } else {
                            Log.d(TAG, "not connected, online = " + isOnline);
                        }
                        if (connected) {
                            userRef.child("online").setValue(isOnline);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Listener was cancelled");
                    }
                });
            }
        }
    }

    /**
     * Sets the 'lastSeen' value of the specified user to the current (server-based) time
     * @param user Currently signed-in {@link FirebaseUser}
     */
    private static void setLastSeenNow(FirebaseUser user) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid());
        userRef.child("lastSeen").setValue(ServerValue.TIMESTAMP);
        userRef.child("lastSeen").onDisconnect().setValue(ServerValue.TIMESTAMP);
    }
}
