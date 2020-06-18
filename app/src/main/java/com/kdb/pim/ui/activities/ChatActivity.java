package com.kdb.pim.ui.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.kdb.pim.Pim;
import com.kdb.pim.data.Contact;
import com.kdb.pim.data.Message;
import com.kdb.pim.R;
import com.kdb.pim.utils.Messenger;
import com.kdb.pim.utils.PresenceUtils;
import com.kdb.pim.utils.TimeUtils;
import com.mikhaellopez.circularimageview.CircularImageView;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import id.zelory.compressor.Compressor;

/**
 * Activity to show incoming and send outgoing messages in a chat.
 */
public class ChatActivity extends AppCompatActivity {
    private static final int PHOTO_RC = 9000;
    private static final String TAG = "pim.chat";
    private Contact contact;
    private String roomId;
    private boolean initialized;
    private String groupDesc = "";
    private boolean work;
    private boolean group;
    private List<String> participantNames = new ArrayList<>();

    @BindView(R.id.chat_contact_image) CircularImageView contactImage;
    @BindView(R.id.chat_online_status) TextView contactOnlineStatusText;
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.chat_contact_name) TextView contactNameText;
    @BindView(R.id.frame_back) FrameLayout backButtonFrame;
    @BindView(R.id.button_send) ImageView send;
    @BindView(R.id.button_cam) ImageView cam;
    @BindView(R.id.messages_list) RecyclerView messageRecycler;
    @BindView(R.id.message_editText) TextInputEditText inputEditText;

    // Firebase
    private FirebaseUser user;
    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;
    private DatabaseReference usersRef;
    private FirebaseRecyclerAdapter<Message, MessageViewHolder> adapter;

    private LinearLayoutManager layoutManager;
    private RecyclerView.AdapterDataObserver adapterDataObserver;
    private int lastViewablePosition;
    private TextWatcher textWatcher;
    private boolean contactOnline;

    private static final long TEXT_TYPING_DELAY_MILLIS = 1500;
    private Handler typingHandler = new Handler();
    DatabaseReference userTypingRef;
    private boolean registeredObserver;
    private int lastViewablePositionSave = -1;
    private TextRecognizer textRecognizer;
    private StorageReference storageRef;
    private UploadTask uploadTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        ButterKnife.bind(this);

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        rootRef = FirebaseDatabase.getInstance().getReference();
        usersRef = FirebaseDatabase.getInstance().getReference().child("users");
        storageRef = FirebaseStorage.getInstance().getReference();

        backButtonFrame.setOnClickListener(view -> finish());
        setSupportActionBar(toolbar);

        if (getIntent().getExtras() != null) {
            Bundle b = getIntent().getExtras();
            if (b.getBoolean("group")) {
                // Group chat
                group = true;
                work = b.getBoolean("work");
                contact = new Contact();
                contact.setName(b.getString("title"));
            } else {
                // Regular chat (DM)

                // Get contact data if available
                Map<String, String> contactMap = (Map<String, String>) getIntent().getExtras().getSerializable("contactData");
                if (contactMap != null) {
                    contact = new Contact(contactMap);
                }
            }
            // If roomId is null, a new room must be created for the chat
            roomId = getIntent().getStringExtra("roomId");
        }

        // AdapterDataObserver to scroll to bottom as new messages come in
        adapterDataObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int messageCount = adapter.getItemCount();
                lastViewablePosition = layoutManager.findLastCompletelyVisibleItemPosition();
                int firstViewablePosition = layoutManager.findFirstVisibleItemPosition();
                if (lastViewablePosition == -1) {
                    if (lastViewablePositionSave != -1) {
                        messageRecycler.scrollToPosition(lastViewablePositionSave);
                    } else {
                        // Loading in, scroll directly to bottom
                        messageRecycler.scrollToPosition(positionStart);
                    }
                } else if ((positionStart == messageCount - 1) && (lastViewablePosition == positionStart - 1)) {
                    // At bottom, scroll smoothly
                    messageRecycler.smoothScrollToPosition(positionStart);
                }
            }
        };

        updateUi(contact);

        if (group) {
            // Watch 'work' field (work chats will filter out distracting messages)
            rootRef.child("chats").child(roomId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    boolean changed = false;
                    boolean newWork = (boolean) dataSnapshot.child("work").getValue();
                    if (newWork != work) {
                        work = newWork;
                        changed = true;
                    }
                    if (!contact.getName().equals(dataSnapshot.child("title").getValue())) {
                        contact.setName((String) dataSnapshot.child("title").getValue());
                        changed = true;
                    }
                    if (!groupDesc.equals(dataSnapshot.child("desc").getValue())) {
                        groupDesc = (String) dataSnapshot.child("desc").getValue();
                        changed = true;
                    }
                    if (changed) {
                        updateUi(contact);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
        }

        if (roomId != null) {
            initRecyclerView(roomId);
            observeTypingStatus(roomId);
            setupTextWatcher(roomId);
            if (group) {
                loadGroupParticipants();
            }

            // Clear notifications for this room
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancel(roomId, 9000);

            initialized = true;
        }

        send.setOnClickListener(view -> {
            // Get message content from EditText
            final String content = inputEditText.getText().toString().trim();
            inputEditText.getText().clear();
            // Attempt to send message if not empty
            if (!content.isEmpty()) {
                // Create message object
                Message message;
                if (group) {
                    message = new Message(user.getUid(), roomId, content, ServerValue.TIMESTAMP);
                } else {
                    message = new Message(user.getUid(), contact.getUid(), content, ServerValue.TIMESTAMP);
                }
                sendMessage(message);
            }
        });

        textRecognizer = new TextRecognizer.Builder(this).build();
        cam.setOnClickListener(view -> showChooser());

        if (contact.getUid() != null) {
            // Load thumbnail for contact profile photo
            usersRef.child(contact.getUid()).child("thumb").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Glide.with(getApplicationContext())
                            .load(dataSnapshot.getValue())
                            .placeholder(R.drawable.chat_default_profile)
                            .into(contactImage);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
        }

        // Dismiss keyboard when activity first opens
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            // Image cropper result
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri uri = result.getUri();
                Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
                sendPicture(uri);
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, "Error: " + result.getError().getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PHOTO_RC && resultCode == RESULT_OK) {
            // Image selected, proceed to crop
            Uri uri = data.getData();
            Toast.makeText(this, uri.toString(), Toast.LENGTH_SHORT).show();
            CropImage.activity(uri)
                    .setGuidelines(CropImageView.Guidelines.ON)
                    .start(this);
        }
    }

    /**
     * Attempts to send a image as a {@link Message}. A 200 x 200 thumbnail is generated at 75%
     * JPEG quality, and the thumbnail and the compressed image are uploaded to Firebase Cloud Storage.
     * @param uri The {@link Uri} of the image file
     */
    private void sendPicture(Uri uri) {
        // compress, upload to thumbs and full
        File thumbnail = new File(uri.getPath());

        // Generate thumbnail
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

        StorageReference path = storageRef.child("images").child(roomId).child(mAuth.getCurrentUser().getUid() + System.currentTimeMillis() + ".jpg");
        StorageReference thumbsPath = storageRef.child("images").child(roomId).child("thumbs").child(mAuth.getCurrentUser().getUid() + System.currentTimeMillis() + ".jpg");
        byte[] thumbnailData = data;

        // Upload image
        if (uploadTask != null && uploadTask.isInProgress()) {
            uploadTask.cancel();
        }
        uploadTask = path.putFile(uri);
        uploadTask.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return path.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // Image uploaded
                Uri imageUri = task.getResult();
                Log.d(TAG, "sendPicture: Image uploaded to " + imageUri);
                if (thumbnailData != null) {
                    // Upload thumbnail
                    storeThumbnail(thumbnailData, thumbsPath, thumbUri -> {
                        // Thumbnail uploaded

                        // Extract text from image if any, and set it as message content for inference
                        String content = recognizeText(uri);
                        // Create message object
                        Message message;
                        if (group) {
                            message = new Message(user.getUid(), roomId, content, ServerValue.TIMESTAMP);
                        } else {
                            message = new Message(user.getUid(), contact.getUid(), content, ServerValue.TIMESTAMP);
                        }
                        message.setType(Message.TYPE_IMAGE);
                        message.setThumbUrl(thumbUri.toString());
                        message.setImageUrl(imageUri.toString());
                        sendMessage(message);
                    });
                }
            } else {
                // Thumbnail uploading failed
                Log.d(TAG, "storeProfilePicture: " + task.getException());
                Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Utility interface for executing code after an upload finishes
     */
    interface OnUploadFinishListener {
        void onFinish(Uri uri);
    }

    /**
     * Uploads thumbnail of an image to Firebase Cloud Storage
     * @param thumbnailData The {@link ByteArrayOutputStream} containing the thumbnail
     * @param thumbsPath The upload path for the thumbnail
     * @param listener {@link OnUploadFinishListener} for continuing operations after upload finishes.
     */
    private void storeThumbnail(byte[] thumbnailData, StorageReference thumbsPath, OnUploadFinishListener listener) {
        Log.d(TAG, "storeThumbnail: Storing thumbnail...");
        UploadTask uploadTaskThumb = thumbsPath.putBytes(thumbnailData);
        uploadTaskThumb.continueWithTask(task -> {
            if (!task.isSuccessful()) {
                throw task.getException();
            }
            return thumbsPath.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "storeThumbnail: Thumbnail uploaded");
                listener.onFinish(task.getResult());
            } else {
                Log.d(TAG, "storeThumbnail: " + task.getException());
                Toast.makeText(this, task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Uses {@link ChatActivity#textRecognizer} to extract text from images.
     * @param uri The {@link Uri} of the image file
     * @return Text content in the image
     */
    private String recognizeText(Uri uri) {
        File image = new File(uri.getPath());

        // Convert image to JPEG to conserve memory
        Bitmap compressedBitmap = null;
        try {
            compressedBitmap = new Compressor(this)
                    .setQuality(100)
                    .compressToBitmap(image);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Create Frame from bitmap and detect text
        final SparseArray<TextBlock> items = textRecognizer.detect(new Frame.Builder().setBitmap(compressedBitmap).build());
        if (items.size() != 0) {
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                TextBlock item = items.valueAt(i);
                Log.d(TAG, "recognizeText: " + item.getValue());
                stringBuilder.append(item.getValue());
                stringBuilder.append(" ");
            }
            return stringBuilder.toString();
        }
        return "";
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
                                .setGuidelines(CropImageView.Guidelines.ON)
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
        startActivityForResult(Intent.createChooser(gallery, "Select image"), PHOTO_RC);
    }

    /**
     * Sets the UI elements to the appropriate values based on if the chat is a group chat or the
     * contact's information.
     * @param contact The {@link Contact} in the DM (only if not a group chat)
     */
    private void updateUi(Contact contact) {
        contactNameText.setText(contact.getName());
        if (group) {
            Glide.with(getApplicationContext())
                    .load(R.drawable.group_default_profile)
                    .into(contactImage);
        } else {
            usersRef.child(user.getUid()).child("contacts").child(contact.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        String name = (String) dataSnapshot.child("name").getValue();
                        String thumb = (String) dataSnapshot.child("thumb").getValue();
                        contactNameText.setText(name);
                        Glide.with(getApplicationContext())
                                .load(thumb)
                                .placeholder(R.drawable.chat_default_profile)
                                .into(contactImage);
                        usersRef.child(user.getUid()).child("contacts").child(contact.getUid()).removeEventListener(this);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
            Glide.with(getApplicationContext())
                    .load(contact.getThumb())
                    .placeholder(R.drawable.chat_default_profile)
                    .into(contactImage);
            observeOnlineStatus(contact);
        }
    }

    /**
     * Watches the 'lastSeen' value of the contact's user node in the database and updates
     * {@link ChatActivity#contactOnlineStatusText} based on whether the contact is online or not
     * @param contact The {@link Contact} in the chat (DM)
     */
    private void observeOnlineStatus(Contact contact) {
        if (!group) {
            DatabaseReference contactRef = usersRef.child(contact.getUid());
            DatabaseReference contactOnlineRef = contactRef.child("online");
            contactOnlineRef.keepSynced(true);
            contactOnlineRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    Log.d(TAG, "onDataChange: " + dataSnapshot.toString());
                    if (dataSnapshot.getValue() != null) {
                        contactOnline = (boolean) dataSnapshot.getValue(Boolean.class);
                        contactOnlineStatusText.setVisibility(View.VISIBLE);
                        if (contactOnline) {
                            contactOnlineStatusText.setText(R.string.online);
                        } else {
                            contactOnlineStatusText.setVisibility(View.GONE);
                            contactOnlineStatusText.setText("");
                            contactRef.child("lastSeen").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.getValue() != null) {
                                        String lastSeenText = "last seen " + TimeUtils.getTimeAgo((Long) dataSnapshot.getValue());
                                        contactOnlineStatusText.setText(lastSeenText);
                                        contactOnlineStatusText.setVisibility(View.VISIBLE);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {
                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
        }
    }

    /**
     * Utility method to set the 'typing' field of the current user in the chat room specified. Once
     * the user starts typing, the value is changed back to false only if they do not type anything
     * for 1.5 seconds.
     * @param roomId The room ID of the current chat
     */
    private void setupTextWatcher(String roomId) {
        userTypingRef = rootRef.child("members").child(roomId).child(user.getUid()).child("typing");
        Runnable finishTypingRunnable = () -> {
            // Input finished, user stopped typing
            userTypingRef.setValue(false);
        };
        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // User is typing
                userTypingRef.setValue(true);
                typingHandler.removeCallbacks(finishTypingRunnable);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    typingHandler.postDelayed(finishTypingRunnable, TEXT_TYPING_DELAY_MILLIS);
                } else {
                    // Erased text
                    typingHandler.post(finishTypingRunnable);
                }
            }
        };
        inputEditText.addTextChangedListener(textWatcher);
    }

    /**
     * Watches the 'typing' field of the contact(s) in the specified chat room and updates
     * {@link ChatActivity#contactOnlineStatusText} based on whether the user is typing or not.
     * @param roomId The room ID of the current chat
     */
    private void observeTypingStatus(String roomId) {
        if (group) {
            rootRef.child("members").child(roomId).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        for (DataSnapshot member : dataSnapshot.getChildren()) {
                            boolean typing = (boolean) member.child("typing").getValue();

                            if (typing && member.getKey() != null && !member.getKey().equals(user.getUid())) {
                                // Contact is typing
                                usersRef.child(user.getUid()).child("contacts").addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                        if (dataSnapshot.hasChild(member.getKey())) {
                                            String name = (String) dataSnapshot.child(member.getKey()).child("name").getValue();
                                            contactOnlineStatusText.setText(String.format(Locale.getDefault(), getString(R.string.label_group_typing), name));
                                        } else {
                                            usersRef.child(member.getKey()).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                                    String name = (String) dataSnapshot.getValue();
                                                    contactOnlineStatusText.setText(String.format(Locale.getDefault(), getString(R.string.label_group_typing), name));
                                                }

                                                @Override
                                                public void onCancelled(@NonNull DatabaseError databaseError) {}
                                            });
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull DatabaseError databaseError) {}
                                });
                            } else {
                                // Done typing
                                showGroupParticipants();
                            }
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
        } else {
            DatabaseReference contactTypingRef = rootRef.child("members").child(roomId).child(contact.getUid()).child("typing");
            ValueEventListener typingEventListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        boolean typing = (boolean) dataSnapshot.getValue();
                        if (contactOnline) {
                            if (typing)
                                contactOnlineStatusText.setText(R.string.label_typing);     // Contact is online and typing
                            else
                                contactOnlineStatusText.setText(R.string.online);       // Done typing
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.d(TAG, "onCancelled: Typing listener cancelled: " + databaseError.getMessage());
                }
            };
            contactTypingRef.addValueEventListener(typingEventListener);
        }
    }

    /**
     * Utlity method to show the names of the participants of the group in
     * {@link ChatActivity#contactOnlineStatusText}
     */
    private void showGroupParticipants() {
        Log.d(TAG, "showGroupParticipants: " + participantNames);
        if (!participantNames.isEmpty()) {
            contactOnlineStatusText.setText("");
            StringBuilder text = new StringBuilder();
            for (String s : participantNames) {
                text.append(s).append(", ");
            }
            text.append("You");
            contactOnlineStatusText.setText(text.toString());
        }
    }

    /**
     * Reads UUIDs from the 'members' node, fetches their names from the database
     * and populates {@link ChatActivity#participantNames} with them.
     */
    private void loadGroupParticipants() {
        Log.d(TAG, "loadGroupParticipants: ");
        participantNames.clear();
        contactOnlineStatusText.setVisibility(View.VISIBLE);
        rootRef.child("members").child(roomId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot member : dataSnapshot.getChildren()) {
                    if (member.getKey() != null) {
                        String uid = member.getKey();
                        if (!uid.equals(user.getUid())) {
                            usersRef.child(user.getUid()).child("contacts").addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    Log.d(TAG, "loadGroupParticipants: onDataChange: ");
                                    if (dataSnapshot.hasChild(uid)) {
                                        String name = (String) dataSnapshot.child(member.getKey()).child("name").getValue();
                                        participantNames.add(name);
                                        showGroupParticipants();
                                    } else {
                                        usersRef.child(member.getKey()).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                                            @Override
                                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                                String name = (String) dataSnapshot.getValue();
                                                participantNames.add(name);
                                                showGroupParticipants();
                                            }

                                            @Override
                                            public void onCancelled(@NonNull DatabaseError databaseError) {}
                                        });
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {}
                            });
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    /**
     * Sets up the main RecyclerView holding the chat items
     * @param roomId
     */
    private void initRecyclerView(String roomId) {
        DatabaseReference roomRef = rootRef.child("rooms").child(roomId);
        FirebaseRecyclerOptions<Message> options = new FirebaseRecyclerOptions.Builder<Message>()
                .setQuery(roomRef, Message.class)
                .setLifecycleOwner(this)
                .build();

        adapter = new FirebaseRecyclerAdapter<Message, MessageViewHolder>(options) {
            private static final int VIEW_TYPE_SENT = 0, VIEW_TYPE_RECEIVED = 1;

            @NonNull
            @Override
            public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == VIEW_TYPE_SENT) {
                    return new MessageViewHolder(LayoutInflater
                            .from(parent.getContext())
                            .inflate(
                                    R.layout.chat_message_sent,
                                    parent,
                                    false
                            ));
                } else {
                    if (group) {
                        return new MessageViewHolder(LayoutInflater
                                .from(parent.getContext())
                                .inflate(
                                        R.layout.chat_group_message_received,
                                        parent,
                                        false
                                ));
                    } else {
                        return new MessageViewHolder(LayoutInflater
                                .from(parent.getContext())
                                .inflate(
                                        R.layout.chat_message_received,
                                        parent,
                                        false
                                ));
                    }
                }
            }

            @Override
            protected void onBindViewHolder(@NonNull MessageViewHolder holder, int position, @NonNull Message message) {
                holder.bind(message, position);
                //messageRecycler.onScrolled(0, 0);
            }

            @Override
            public int getItemViewType(int position) {
                Message message = getItem(position);
                if (message.getFrom().equals(user.getUid())) {
                    return VIEW_TYPE_SENT;
                } else {
                    return VIEW_TYPE_RECEIVED;
                }
            }
        };

        adapter.registerAdapterDataObserver(adapterDataObserver);
        registeredObserver = true;

        layoutManager = new LinearLayoutManager(this);
        messageRecycler.setLayoutManager(layoutManager);
        messageRecycler.setAdapter(adapter);

        // Scroll to bottom when keyboard is pulled up
        messageRecycler.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom) {       // Keyboard is up
                int messageCount = adapter.getItemCount();
                if (lastViewablePosition == messageCount - 1 || lastViewablePosition == -1) {       // Was at bottom, scroll to last message
                    messageRecycler.smoothScrollToPosition(messageCount - 1);
                }
            }
        });

        // Mark messages as seen as the user scrolls past them
        messageRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // Update lastViewablePosition for LayoutChangeListener
                lastViewablePosition = layoutManager.findLastCompletelyVisibleItemPosition();
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                Log.d(TAG, "onScrolled: ");
                int lastPosition = layoutManager.findLastCompletelyVisibleItemPosition();
                int firstPosition = layoutManager.findFirstVisibleItemPosition();
                if (firstPosition >= 0 && lastPosition >= 0)
                    seeMessages(firstPosition, lastPosition);
            }
        });
    }

    /**
     * Utility method to mark a range of messages as "seen"
     * @param start The starting message index
     * @param end The ending message index
     */
    private void seeMessages(int start, int end) {
        DatabaseReference chatMessageRef = rootRef.child("chats").child(roomId).child("lastMessage");
        for (int i = start; i <= end; i++) {
            Message message = (Message) adapter.getItem(i);
            DatabaseReference messageRef = adapter.getRef(i);
            if (!message.isSeen() && message.getFrom().equals(contact.getUid())) {
                Log.d(TAG, "seeMessages: Seeing message " + message.getContent());
                message.setSeen(true);
                messageRef.child("seen").setValue(true);

                chatMessageRef.setValue(message).addOnFailureListener(e -> {
                    Log.d(TAG, "sendMessage: " + "Error: " + e.getMessage());
                    Toast.makeText(ChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }
    }//

    /**
     * {@link androidx.recyclerview.widget.RecyclerView.ViewHolder} subclass to hold {@link Message}
     * items in the RecyclerView
     */
    class MessageViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.content) TextView content;
        @Nullable @BindView(R.id.name) TextView name;
        @Nullable @BindView(R.id.nickname) TextView nickname;
        @BindView(R.id.label_timestamp) TextView timestamp;
        @BindView(R.id.image_seen_check) ImageView statusImage;
        @BindView(R.id.message_root) View root;
        @BindView(R.id.chat_warning_distracting) TextView warning;
        @BindView(R.id.chat_image) ImageView image;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        void bind(Message message, int pos) {
            if (group) {
                // Group chat
                if (!message.getFrom().equals(contact.getUid()) && nickname != null && name != null) {
                    usersRef.child(user.getUid()).child("contacts").child(message.getFrom()).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            if (dataSnapshot.child("name").getValue() != null) {
                                String savedName = (String) dataSnapshot.child("name").getValue();
                                name.setText(savedName);
                                nickname.setVisibility(View.GONE);
                            } else {
                                usersRef.child(message.getFrom()).child("phone").addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                        if (dataSnapshot.getValue() != null) {
                                            name.setText((CharSequence) dataSnapshot.getValue());
                                            nickname.setVisibility(View.VISIBLE);

                                            // Load nickname
                                            usersRef.child(message.getFrom()).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                                                @Override
                                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                                    if (dataSnapshot.getValue() != null) {
                                                        nickname.setText(String.format(Locale.getDefault(), "(%s)", (CharSequence) dataSnapshot.getValue()));
                                                    }
                                                }
                                                @Override
                                                public void onCancelled(@NonNull DatabaseError databaseError) {}
                                            });
                                        }
                                    }
                                    @Override
                                    public void onCancelled(@NonNull DatabaseError databaseError) {}
                                });
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {}
                    });
                }
            }

            if (message.getType() != null && message.getType().equals(Message.TYPE_IMAGE)) {
                // Message is an image
                image.setVisibility(View.VISIBLE);
                content.setVisibility(View.GONE);
                Glide.with(getApplicationContext())
                        .load(message.getThumbUrl())
                        .listener(new RequestListener<Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                return false;
                            }

                            @Override
                            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                new Handler().post(() -> {
                                    Glide.with(getApplicationContext())
                                            .load(message.getImageUrl())
                                            .into(image);
                                });
                                return false;
                            }
                        })
                        .into(image);
                root.setOnClickListener(v -> {
                    // Show image in full-screen
                    Bundle bundle = new Bundle();
                    bundle.putString("url", message.getImageUrl());
                    bundle.putString("name", name != null ? name.getText().toString() : contactNameText.getText().toString());
                    startActivity(new Intent(ChatActivity.this, ImageActivity.class).putExtras(bundle));
                });
                root.setOnLongClickListener(null);
            } else {
                image.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                content.setText(message.getContent());
                setCopyLongClickListener(root, message.getContent());
            }
            timestamp.setText(TimeUtils.getReadableTime(new Date((Long) message.getServerTimestamp())));
            if (message.isSeen()) {
                statusImage.setImageResource(R.drawable.ic_seen);
            } else {
                statusImage.setImageResource(R.drawable.ic_delivered);
            }

            warning.setVisibility(View.GONE);

            // Check if message is distracting and filter out if work conversation
            if (work) {
                if (message.isDistracting()) {
                    if (!message.getFrom().equals(user.getUid())) {
                        // Received a distracting message
                        image.setVisibility(View.GONE);
                        content.setVisibility(View.VISIBLE);
                        content.setText(R.string.msg_distracting);
                        content.setEnabled(false);
                        content.setTypeface(content.getTypeface(), Typeface.ITALIC);
                        root.setOnLongClickListener(view -> {
                            // View message content anyway
                            content.setEnabled(true);
                            content.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

                            Linkify.addLinks(content, Linkify.ALL);
                            setMessageNonDistracting(pos);
                            root.setSelected(false);
                            setCopyLongClickListener(root, message.getContent());
                            return true;
                        });
                    } else {
                        // Sent a distracting message
                        statusImage.setImageResource(R.drawable.ic_delivered);
                        warning.setText(R.string.warning_distracting_flagged);
                        warning.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (warning.getVisibility() == View.VISIBLE) {
                        warning.setText(R.string.warning_distracting_unflagged);
                        new Handler().postDelayed(() -> warning.setVisibility(View.GONE), 2000);
                    }
                }
            }
        }

        /**
         * Utility method to set a {@link android.view.View.OnLongClickListener} to copy message
         * content to clipboard
         * @param root The root view of the message item
         * @param content The content of the message
         */
        private void setCopyLongClickListener(View root, String content) {
            root.setOnLongClickListener((v) -> {
                ClipboardManager clipboard = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Message text", content);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ChatActivity.this, "Message text copied to clipboard", Toast.LENGTH_SHORT).show();
                root.getBackground().setState(new int[]{});
                return true;
            });
        }

        /**
         * Utility method to set method as non-distracting
         * @param pos The index of the message in the adapter
         */
        private void setMessageNonDistracting(int pos) {
            DatabaseReference messageRef = adapter.getRef(pos);
            messageRef.child("distracting").setValue(false);

            if (pos == adapter.getItemCount() - 1) {        // Last message
                rootRef.child("chats").child(roomId).child("lastMessage").child("distracting").setValue(false);
            }
        }
    }

    /**
     * Attempts to send the message to the current room using {@link Messenger}.
     * If {@link ChatActivity#roomId} is null, a new room is created.
     * @param message The message to be sent
     */
    private void sendMessage(Message message) {
        if (roomId == null) {
            roomId = Messenger.createRoom(message, rootRef, e -> {
                Log.d(TAG, "sendMessage: " + "Error: " + e.getMessage());
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }, null);
        }
        Messenger.sendMessage(message, roomId, rootRef, new Messenger.OnSentListener() {
            @Override
            public void onSuccess() {
                if (!initialized) {
                    initRecyclerView(roomId);
                    observeTypingStatus(roomId);
                    setupTextWatcher(roomId);
                    Pim.setCurrentRoom(roomId);
                    initialized = true;
                }
            }
            @Override
            public void onFailure(Exception e) {
                Log.d(TAG, "sendMessage: " + "Error: " + e.getMessage());
                Toast.makeText(ChatActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, null, this);
    }

    /**
     * Shows contact profile details
     * @param view The clicked view
     */
    public void viewContact(View view) {
        // TODO
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: ");
        super.onStop();
        PresenceUtils.setOnline(user, false);
        inputEditText.removeTextChangedListener(textWatcher);
        if (adapter != null && registeredObserver) {
            registeredObserver = false;
        }
        lastViewablePositionSave = layoutManager.findLastCompletelyVisibleItemPosition();
        Log.d(TAG, "onStop: saved pos = " + lastViewablePositionSave);
        Pim.setCurrentRoom(null);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart: ");
        super.onStart();
        PresenceUtils.setOnline(user, true);
        if (textWatcher != null)
            inputEditText.addTextChangedListener(textWatcher);
        if (adapter != null && !registeredObserver && initialized) {
            //adapter.registerAdapterDataObserver(adapterDataObserver);
            registeredObserver = true;
        }
        if (roomId != null) {
            Pim.setCurrentRoom(roomId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.menu_chat_view:
                if (!group) {
                    viewContact(null);
                } else {

                }
                break;
            case R.id.menu_chat_mute:
                // TODO
                break;
            case R.id.menu_chat_block:
                // TODO
                break;
            case R.id.menu_chat_work:
                rootRef.child("chats").child(roomId).child("work").setValue(!item.isChecked()).addOnSuccessListener((v) -> {
                    item.setChecked(!item.isChecked());
                    initRecyclerView(roomId);
                });
                break;
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (group) {
            menu.findItem(R.id.menu_chat_work).setChecked(work);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (group) {
            getMenuInflater().inflate(R.menu.menu_group_chat, menu);
        } else {
            getMenuInflater().inflate(R.menu.menu_chat, menu);
        }
        return true;
    }
}
