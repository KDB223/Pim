package com.kdb.pim.ui.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.kdb.pim.R;
import com.kdb.pim.utils.PresenceUtils;
import com.kdb.pim.utils.UiUtils;
import com.mikhaellopez.circularimageview.CircularImageView;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import butterknife.BindView;
import butterknife.ButterKnife;
import id.zelory.compressor.Compressor;

import static com.kdb.pim.utils.Constants.DEFAULT_PHOTO_URL;

/**
 * Activity to view and update user profile photo and status
 */
public class AccountActivity extends AppCompatActivity {
    private static final String TAG = "pim.account";

    @BindView(R.id.account_status) TextView statusText;
    @BindView(R.id.account_name) TextView name;
    @BindView(R.id.button_image_change) Button changeImage;
    @BindView(R.id.button_status_change) Button changeStatus;
    @BindView(R.id.image_debug) CircularImageView profileImage;
    @BindView(R.id.progressBar) ProgressBar progressBar;
    @BindView(R.id.frame_loading) FrameLayout loadingFrame;
    @BindView(R.id.toolbar) Toolbar toolbar;

    private FirebaseAuth mAuth;
    private DatabaseReference databaseReference;
    private StorageReference storageRef;
    private UploadTask uploadTask;

    private static int PROFILE_PHOTO_RC = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);
        ButterKnife.bind(this);

        mAuth = FirebaseAuth.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener((v) -> onBackPressed());

        updateUi(mAuth.getCurrentUser());

        changeStatus.setOnClickListener((v) -> {
            Intent intent = new Intent(this, StatusChangeActivity.class);
            intent.putExtra("uid", mAuth.getCurrentUser().getUid());
            if (!TextUtils.isEmpty(statusText.getText().toString()))
                intent.putExtra("status", statusText.getText().toString());
            startActivity(intent);
        });

        changeImage.setOnClickListener((v -> showChooser()));
    }

    /**
     * Utility method to show a dialog with 2 options for sending images - taking a new photo or
     * choosing an existing one.
     */
    private void showChooser() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_selectable_list_item);
        adapter.add("Take photo");
        adapter.add("Choose photo");
        new AlertDialog.Builder(this)
                .setAdapter(adapter, (dialogInterface, pos) -> {
                    if (pos == 0) {
                        CropImage.activity()
                                //.setMaxCropResultSize(500, 500)
                                .setGuidelines(CropImageView.Guidelines.ON)
                                .setRequestedSize(500, 500, CropImageView.RequestSizeOptions.RESIZE_FIT)
                                .setAspectRatio(1, 1)
                                .start(this);
                    } else {
                        createGalleryIntent();
                    }
                    dialogInterface.dismiss();
                })
                .create().show();
    }

    /**
     * Utility method to show image chooser if user decides to choose an existing image
     */
    private void createGalleryIntent() {
        Intent gallery = new Intent();
        gallery.setType("image/*");
        gallery.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(gallery, "Select image"), PROFILE_PHOTO_RC);
    }

    /**
     * Updates the name and status texts to the corresponding values by fetching them from FirebaseAuth
     * and FirebaseDatabase respectively.
     * @param user The current signed-in FirebaseUser
     */
    private void updateUi(@Nullable FirebaseUser user) {
        if (user != null) {
            setLoading(true);
            Log.d(TAG, "updateUi: Updating UI...");
            databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid());

            name.setText(user.getDisplayName());
            databaseReference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    name.setText((CharSequence) dataSnapshot.child("name").getValue());

                    if (dataSnapshot.child("status").getValue() != null) {
                        String status = dataSnapshot.child("status").getValue().toString();
                        if (!TextUtils.isEmpty(status)) {
                            statusText.setText(status);
                            Log.d(TAG, "onDataChange: Setting status");
                        }
                    }
                    if (dataSnapshot.child("thumb").getValue() != null) {
                        String url = (String) dataSnapshot.child("thumb").getValue();
                        if (!url.equals(DEFAULT_PHOTO_URL)) {
                            Log.d(TAG, "onDataChange: Loading thumbnail...");
                            Glide.with(getApplicationContext())
                                    .load(url)
                                    .placeholder(R.drawable.chat_default_profile)
                                    .listener(new RequestListener<Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                            return false;
                                        }

                                        @Override
                                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                            setLoading(false);
                                            Log.d(TAG, "onResourceReady: Thumb loaded");
                                            return false;
                                        }
                                    })
                                    .into(profileImage);
                        } else {
                            setLoading(false);
                            Log.d(TAG, "onDataChange: Not loading thumb");
                            // Toast.makeText(AccountActivity.this, "Not loading thumbnail", Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(AccountActivity.this, "Error: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri uri = result.getUri();
                Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
                storeProfilePicture(uri);
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, "Error: " + result.getError().getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PROFILE_PHOTO_RC && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
            CropImage.activity(uri)
                    .setGuidelines(CropImageView.Guidelines.ON)
                    .setAspectRatio(1, 1)
                    .start(this);
        }
    }

    /**
     * Uploads a new profile picture for the current user to Firebase Cloud Storage.
     * Also generates and uploads a thumbnail.
     * @param uri The {@link Uri} of the new image
     */
    private void storeProfilePicture(Uri uri) {
        setLoading(true);

        File thumbnail = new File(uri.getPath());
        byte[] data = null;
        try {
            Bitmap compressedBitmap = new Compressor(this)
                    .setMaxHeight(200)
                    .setMaxWidth(200)
                    .setQuality(75)
                    .compressToBitmap(thumbnail);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            data = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }

        StorageReference path = storageRef.child("profile_images").child(mAuth.getCurrentUser().getUid() + ".jpg");
        StorageReference thumbsPath = storageRef.child("profile_images").child("thumbs").child(mAuth.getCurrentUser().getUid() + ".jpg");
        byte[] finalData = data;


        if (uploadTask != null && uploadTask.isInProgress()) {
            uploadTask.cancel();
        }
        uploadTask = path.putFile(uri);
        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                throw task.getException();
            }
            return path.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Uri downloadUri = task.getResult();
                Log.d(TAG, "storeProfilePicture: Profile photo uploaded to " + downloadUri);
                if (finalData != null) {
                    storeThumbnail(finalData, thumbsPath);
                }
                setProfilePhoto(downloadUri);
            } else {
                Log.d(TAG, "storeProfilePicture: " + task.getException());
                Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Uploads the provided thumbnail to Firebase Cloud Storage.
     * @param thumbnailData The {@link ByteArrayOutputStream} containing the thumbnail image
     * @param thumbsPath The upload path for the thumbnail
     */
    private void storeThumbnail(byte[] thumbnailData, StorageReference thumbsPath) {
        Log.d(TAG, "storeThumbnail: Storing thumbnail...");
        UploadTask uploadTaskThumb = thumbsPath.putBytes(thumbnailData);
        uploadTaskThumb.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                setLoading(false);
                throw task.getException();
            }
            return thumbsPath.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "storeThumbnail: Thumbnail uploaded");
                saveThumbnail(task.getResult());
            } else {
                Log.d(TAG, "storeThumbnail: " + task.getException());
                Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Saves the uploaded thumbnail URL to the database under the corresponding user node
     * @param thumbUri The  {@link Uri} of the uploaded thumbnail
     */
    private void saveThumbnail(Uri thumbUri) {
        Log.d(TAG, "saveThumbnail: Saving thumbnail to db...");
        databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(mAuth.getCurrentUser().getUid()).child("thumb");
        databaseReference.setValue(thumbUri.toString()).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "saveThumbnail: Thumbnail URL saved. Updating UI...");
                updateUi(mAuth.getCurrentUser());
            }
        });
    }

    /**
     * Sets the profile photo URI of the FirebaseUser
     * @param downloadUri The {@link Uri} of the uploaded profile photo
     */
    private void setProfilePhoto(Uri downloadUri) {
        Log.d(TAG, "setProfilePhoto: Setting profile photo to user");
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setPhotoUri(downloadUri)
                .build();
        mAuth.getCurrentUser().updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Log.d(TAG, "setProfilePhoto: User profile photo updated.");
                        Toast.makeText(this, "Profile photo updated", Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     */
    private void setLoading(boolean loading) {
        UiUtils.setLoading(loading, progressBar, loadingFrame, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        setLoading(true);
        PresenceUtils.setOnline(mAuth.getCurrentUser(), true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        PresenceUtils.setOnline(mAuth.getCurrentUser(), false);
    }
}
