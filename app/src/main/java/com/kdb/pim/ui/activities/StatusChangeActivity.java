package com.kdb.pim.ui.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kdb.pim.R;
import com.kdb.pim.utils.PresenceUtils;
import com.kdb.pim.utils.UiUtils;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Activity to change current user's status
 */
public class StatusChangeActivity extends AppCompatActivity {
    private static final String TAG = "pim.status_change";
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.button_save) Button saveButton;
    @BindView(R.id.input_status) TextInputLayout statusInputLayout;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.frame_loading) FrameLayout loadingFrame;

    private DatabaseReference databaseReference;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status_change);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(view -> onBackPressed());

        // Get current user UID from intent
        String uid = getIntent().getExtras().getString("uid");
        databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(uid).child("status");
        user = FirebaseAuth.getInstance().getCurrentUser();

        if (getIntent().hasExtra("status")) {
            statusInputLayout.getEditText().setText(getIntent().getStringExtra("status"));
        } else {
            fetchAndShowStatus();
        }

        saveButton.setOnClickListener((v) -> {
            String status = statusInputLayout.getEditText().getText().toString();
            if (!TextUtils.isEmpty(status)) {
                setLoading(true);
                databaseReference.setValue(status).addOnCompleteListener((task) -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        onBackPressed();
                    } else {
                        Log.d(TAG, "setValue:failure: " + task.getException());
                        Toast.makeText(StatusChangeActivity.this, "Error saving changes", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * Fetch the current user's status value from the database and show
     * it in the {@link StatusChangeActivity#statusInputLayout)
     */
    private void fetchAndShowStatus() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    String status = dataSnapshot.getValue().toString();
                    if (!TextUtils.isEmpty(status))
                        statusInputLayout.getEditText().setText(status);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(StatusChangeActivity.this, "Error: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     */
    private void setLoading(boolean loading) {
        UiUtils.setLoading(loading, progressBar, loadingFrame, getWindow());
    }

    @Override
    protected void onStart() {
        super.onStart();
        setLoading(true);
        PresenceUtils.setOnline(user, true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        PresenceUtils.setOnline(user, false);
    }
}
