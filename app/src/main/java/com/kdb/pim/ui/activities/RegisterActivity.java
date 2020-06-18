package com.kdb.pim.ui.activities;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.iid.FirebaseInstanceId;
import com.kdb.pim.R;
import com.kdb.pim.utils.ContactUtils;
import com.kdb.pim.utils.PresenceUtils;
import com.kdb.pim.utils.UiUtils;

import java.util.HashMap;

import butterknife.BindView;
import butterknife.ButterKnife;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.kdb.pim.utils.Constants.DEFAULT_PHOTO_URL;

/**
 * Activity for signing up new users
 */

public class RegisterActivity extends AppCompatActivity {
    private static final String TAG = "pim.register";
    private static final int RC_CONTACTS = 123;

    @BindView(R.id.input_email) TextInputLayout textInputLayoutEmail;
    @BindView(R.id.input_pwd) TextInputLayout textInputLayoutPwd;
    @BindView(R.id.input_name) TextInputLayout textInputLayoutName;
    @BindView(R.id.input_phone) TextInputLayout textInputLayoutPhone;
    @BindView(R.id.button_create_acc) Button createButton;
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.frame_loading) FrameLayout loadingFrame;

    // Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        ButterKnife.bind(this);

        mAuth = FirebaseAuth.getInstance();

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        createButton.setOnClickListener((view) -> {
            // Get user input from text fields
            String email = textInputLayoutEmail.getEditText().getText().toString();
            String pwd = textInputLayoutPwd.getEditText().getText().toString();
            String name = textInputLayoutName.getEditText().getText().toString().trim();
            String phone = textInputLayoutPhone.getEditText().getText().toString().trim();
            // Attempt account creation if inputs are not empty
            if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(pwd) && !TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
                createAccount(email, pwd, name, phone);
                setLoading(true);
            }
        });
    }

    /**
     * Uses Firebase Auth instance {@link RegisterActivity#mAuth} to create a new user. Once signed in,
     * the new user's instance ID token (device-specific) is extracted
     * @param email New user's email address
     * @param password New user's password
     * @param name New user's name
     * @param phone New user's phone number
     */
    private void createAccount(String email, String password, String name, String phone) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign in successful, update UI with the signed-in user's information
                        Log.d(TAG, "createUserWithEmail:success");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            FirebaseInstanceId.getInstance().getInstanceId()
                                    .addOnCompleteListener(task1 -> {
                                                if (!task1.isSuccessful()) {
                                                    Log.w(TAG, "getInstanceId failed", task1.getException());
                                                    Toast.makeText(this, "Failed to get instance ID", Toast.LENGTH_SHORT).show();
                                                    mAuth.signOut();
                                                    setLoading(false);
                                                    return;
                                                }
                                                // Get new Instance ID token
                                                String token = task1.getResult().getToken();
                                                // Add the user to the database
                                                addUser(user, name, phone, token);
                                    })
                                    .addOnFailureListener(e -> {
                                        // Error finding token
                                        setLoading(false);
                                        mAuth.signOut();
                                        Log.w(TAG, "getInstanceId:failure" + e.getMessage());
                                        Toast.makeText(this, "getInstanceId:failure" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });

                        }
                    } else {
                        // If sign in fails, display a message to the user.
                        setLoading(false);
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(this, findError(task.getException()), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Adds the new user information to the 'users' node of the database and updates the display
     * name using FirebaseAuth
     * @param user The newly signed in FirebaseUser
     * @param name New user's (display) name
     * @param phone New user's phone number
     * @param token New user's device instance ID token
     */
    private void addUser(FirebaseUser user, String name, String phone, String token) {
        // Set user's display name
        updateName(user, name);

        String uid = user.getUid();

        // Create a Map with the user node data
        HashMap<String, Object> userMap = new HashMap<>();
        userMap.put("status", this.getString(R.string.default_status));
        userMap.put("thumb", DEFAULT_PHOTO_URL);
        userMap.put("name", name);
        userMap.put("phone", phone);
        userMap.put("online", true);
        userMap.put("lastSeen", ServerValue.TIMESTAMP);
        userMap.put("deviceToken", token);

        // Add user contacts
        addContacts();

        // Reference to /users/{user-id}/
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(uid);

        // Add user to database
        databaseReference.setValue(userMap).addOnCompleteListener(task1 -> {
            if (task1.isSuccessful()) {
                Toast.makeText(this, "Sign-in success!", Toast.LENGTH_SHORT).show();
                setLoading(false);
                PresenceUtils.starts = 0;

                // Signed in successfully, start MainActivity
                startActivity(new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            }
        });
    }

    /**
     * Gets contacts permission from user and attempts to read and contacts data to database.
     */
    @AfterPermissionGranted(RC_CONTACTS)
    private void addContacts() {
        if (!EasyPermissions.hasPermissions(this, Manifest.permission.READ_CONTACTS)) {
            EasyPermissions.requestPermissions(this, "To chat with your contacts, please allow Pim to access your contacts",
                    RC_CONTACTS, Manifest.permission.READ_CONTACTS);
        } else {
            ContactUtils.getInstance().loadContacts(this, FirebaseDatabase.getInstance().getReference().child("users"), mAuth.getCurrentUser());
        }
    }

    /**
     * Utility method to create a readable error String FirebaseAuth exceptions
     * @param taskException The thrown exception
     * @return Human-readable error string for UI
     */
    private String findError(Exception taskException) {
        String error;
        try {
            throw taskException;
        } catch (FirebaseAuthWeakPasswordException e) {
            error = "Password must be more than 6 characters";
        } catch (FirebaseAuthInvalidCredentialsException e) {
            error = "Invalid email";
        } catch (FirebaseAuthUserCollisionException e) {
            error = "Account already exists";
        } catch (Exception e) {
            error = "Unknown error";
        }
        return error;
    }

    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     */
    private void setLoading(boolean loading) {
        UiUtils.setLoading(loading, progressBar, loadingFrame, getWindow());
    }

    /**
     *
     * Utility method to update user's display name
     * @param user The FirebaseUser whose display name is to be changed
     * @param name The new name
     */
    private void updateName(FirebaseUser user, String name) {
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .setPhotoUri(Uri.parse(DEFAULT_PHOTO_URL))
                .build();
        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task1 -> {
                    if (task1.isSuccessful()) {
                        Log.d(TAG, "User profile updated.");
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
}
