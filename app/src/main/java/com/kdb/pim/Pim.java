package com.kdb.pim;

import android.app.Application;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.kdb.pim.utils.PresenceUtils;

/**
 * Application class for the app. Contains some initialization code
 */
public class Pim extends Application {
    private static final String TAG = "pim.app";

    /**
     * Contains the room ID of the room the user is currently in, so that notifications don't show up
     * for that chat while the user is in it.
     */
    private static String currentRoom = null;

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseDatabase.getInstance().setPersistenceEnabled(false);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Log.d(TAG, "onCreate: ");
            PresenceUtils.starts = -1;
            PresenceUtils.setOnline(user, true);
        }
    }

    public static String getCurrentRoom() {
        return currentRoom;
    }

    public static void setCurrentRoom(String currentRoom) {
        Pim.currentRoom = currentRoom;
    }
}
