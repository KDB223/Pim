package com.kdb.pim.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.tomash.androidcontacts.contactgetter.entity.ContactData;
import com.tomash.androidcontacts.contactgetter.entity.PhoneNumber;
import com.tomash.androidcontacts.contactgetter.main.contactsGetter.ContactsGetterBuilder;

import java.util.List;
import java.util.Map;

/**
 * Utility class for reading and loading on-device contacts
 */
public class ContactUtils {
    public static final int RC_CONTACTS = 123;

    private static ContactUtils instance;
    private List<ContactData> contactDataList;
    private boolean inProgress = false;
    private MappingCompletedListener listener;

    public interface MappingCompletedListener {
        void onComplete();
    }

    public void setListener(MappingCompletedListener listener) {
        this.listener = listener;
    }

    /**
     * Returns a singleton instance of {@link ContactUtils}
     * @return singleton instance of {@link ContactUtils}
     */
    public static ContactUtils getInstance() {
        if (instance == null) {
            synchronized (ContactUtils.class) {
                if (instance == null) {
                    instance = new ContactUtils();
                }
            }
        }
        return instance;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    /**
     * Reads all of the user's contacts on-device, fetches all users from database
     * and attempts to map the contacts to the database
     *
     * @param context {@link Context} parameter for {@link ContactsGetterBuilder}
     * @param referenceUsers Reference to the 'users' node of the database
     * @param user The currently signed-in user
     */
    public void loadContacts(Context context, DatabaseReference referenceUsers, FirebaseUser user) {
        inProgress = true;
        contactDataList = new ContactsGetterBuilder(context).onlyWithPhones().buildList();
        AppExecutors.getInstance().networkIO().execute(() -> {
            referenceUsers.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    mapNumbers(referenceUsers, (Map<String,Object>) dataSnapshot.getValue(), contactDataList, user);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(context, "Error: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Adds the user's contacts to the 'contacts' node of the current user in the database. This
     * method can be called again to refresh user's contacts in the app. It iterates through all users
     * and looks for phone numbers that match with the on-device contacts. If a match is found
     * the contact is added to the database.
     * @param referenceUsers Reference to the 'users' node of the database
     * @param map The map of all users in the database
     * @param contactDataList On-device contacts
     * @param user The currently signed-in user
     */
    private void mapNumbers(DatabaseReference referenceUsers, Map<String, Object> map, List<ContactData> contactDataList, FirebaseUser user) {
        String currentUid = user.getUid();
        // Clear all contacts from db before starting, to remove deleted contacts
        referenceUsers.child(currentUid).child("contacts").removeValue().addOnSuccessListener(aVoid -> {
            for (ContactData contactData : contactDataList) {
                List<PhoneNumber> contactPhones = contactData.getPhoneList();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String uid = entry.getKey();
                    if (!uid.equals(currentUid)) {
                        Map userMap = (Map) entry.getValue();
                        if (userMap.containsKey("phone")) {

                            // Remove spaces
                            String phone = ((String) userMap.get("phone")).replaceAll("\\s", "");

                            for (PhoneNumber number : contactPhones) {

                                // Remove spaces
                                String contactPhoneString = number.getMainData().replaceAll("\\s", "");
                                if (phone.equals(contactPhoneString)) {         // Contact match
                                    userMap.remove("contacts");
                                    userMap.remove("rooms");

                                    userMap.put("name", contactData.getCompositeName());

                                    // Add to database
                                    referenceUsers.child(currentUid).child("contacts").child(uid).setValue(userMap);
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            inProgress = false;
            if (listener != null) {
                listener.onComplete();
            }
        });
    }
}
