package com.kdb.pim.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.kdb.pim.R;
import com.kdb.pim.utils.PresenceUtils;
import com.kdb.pim.utils.UiUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Activity to sign-in existing users
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "pim.login";

    @BindView(R.id.button_sign_up) Button signupButton;
    @BindView(R.id.button_login) Button loginButton;
    @BindView(R.id.input_email) TextInputLayout textInputLayoutEmail;
    @BindView(R.id.input_pwd) TextInputLayout textInputLayoutPwd;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.frame_loading) FrameLayout loadingFrame;

    // Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        ButterKnife.bind(this);

        mAuth = FirebaseAuth.getInstance();

        signupButton.setOnClickListener((v) -> startActivity(new Intent(this, RegisterActivity.class)));

        loginButton.setOnClickListener((view) -> {
            String email = textInputLayoutEmail.getEditText().getText().toString();
            String pwd = textInputLayoutPwd.getEditText().getText().toString();
            if (!TextUtils.isEmpty(email) && !TextUtils.isEmpty(pwd)) {
                signIn(email, pwd);
                setLoading(true);
                hideKeyboard();
            }
        });
    }

    /**
     * Utility method to dismiss the virtual keyboard (IME input)
     */
    private void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(textInputLayoutPwd.getApplicationWindowToken(), 0);
    }

    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     */
    private void setLoading(boolean loading) {
        UiUtils.setLoading(loading, progressBar, loadingFrame, getWindow());
    }

    /**
     * Attempts to sign-in an existing user using FirebaseAuth. Once signed-in, the device instance
     * ID token is extracted and updated in the database.
     * @param email Email address of the FirebaseUser
     * @param password Password of the FirebaseUser
     */
    private void signIn(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        // Sign in successful, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithEmail:success");
                        Toast.makeText(this, "Success!", Toast.LENGTH_SHORT).show();
                        FirebaseUser user = mAuth.getCurrentUser();
                        PresenceUtils.starts = 0;
                        Log.d(TAG, "signIn: setting starts to 0");
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
                                    // Save to database
                                    FirebaseDatabase.getInstance().getReference().child("users")
                                            .child(user.getUid())
                                            .child("deviceToken")
                                            .setValue(token)
                                            .addOnSuccessListener(aVoid -> {
                                                // Log and toast
                                                Log.d(TAG, token);
                                                // Signed in, start MainActivity
                                                startActivity(new Intent(LoginActivity.this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(this, "Failed to save token: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                mAuth.signOut();
                                                setLoading(false);
                                            });
                                });
                    } else {
                        // If sign in fails, display a message to the user.
                        setLoading(false);
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(this, "Authentication failed. Please check your password.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
