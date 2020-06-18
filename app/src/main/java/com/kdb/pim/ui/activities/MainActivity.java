package com.kdb.pim.ui.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.kdb.pim.R;
import com.kdb.pim.data.Chat;
import com.kdb.pim.data.Contact;
import com.kdb.pim.data.Message;
import com.kdb.pim.utils.PresenceUtils;
import com.kdb.pim.utils.TimeUtils;
import com.mikhaellopez.circularimageview.CircularImageView;

import java.util.Date;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.kdb.pim.data.Message.TYPE_IMAGE;
import static com.kdb.pim.utils.Constants.DEFAULT_PHOTO_URL;

/**
 * Activity to list all active chats for the user. Launched only if a valid user is signed-in.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "pim.main";
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.fab) FloatingActionButton fab;
    @BindView(R.id.main_chat_list) RecyclerView chatRecycler;

    // Firebase
    private FirebaseUser user;
    private FirebaseAuth mAuth;
    private DatabaseReference rootRef;      // Root reference
    private DataSnapshot contactsSnapshot;

    // Set to true after chat list has been created
    boolean initialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        rootRef = FirebaseDatabase.getInstance().getReference();
        setSupportActionBar(toolbar);

        fab.setOnClickListener((view -> startActivity(new Intent(this, ContactsActivity.class))));

        if (mAuth.getCurrentUser() != null) {
            Toast.makeText(this, "Logged in as " + user.getEmail(), Toast.LENGTH_SHORT).show();
        }

        if (user != null) initRecyclerView();
    }

    /**
     * Utility method to create the list of active chats
     */
    private void initRecyclerView() {
        DatabaseReference chatsRef = rootRef.child("chats");
        DatabaseReference userRoomsRef = rootRef.child("users").child(user.getUid()).child("rooms");
        DatabaseReference userContactsRef = rootRef.child("users").child(user.getUid()).child("contacts");
        userContactsRef.keepSynced(true);

        // Get all of the user's contacts which are also on Pim
        userContactsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                contactsSnapshot = dataSnapshot;
                if (!initialized) {
                    FirebaseRecyclerOptions<Chat> options = new FirebaseRecyclerOptions.Builder<Chat>()
                            // Order chats by the most recent message, so the chat with the newest
                            // message is on top.
                            .setIndexedQuery(userRoomsRef.orderByChild("lastMessageTimestamp"), chatsRef, snapshot -> {
                                Chat chat = snapshot.getValue(Chat.class);
                                if (!chat.isGroup()) {
                                    Message message = chat.getLastMessage();
                                    Contact contact;
                                    if (message.getFrom().equals(user.getUid())) {
                                        // User-sent message
                                        contact = contactsSnapshot.child(message.getTo()).getValue(Contact.class);
                                        if (contact != null) contact.setUid(message.getTo());
                                    } else {
                                        // Received message
                                        contact = contactsSnapshot.child(message.getFrom()).getValue(Contact.class);
                                        if (contact != null) contact.setUid(message.getFrom());
                                    }
                                    chat.setContact(contact);
                                }
                                chat.setRoomId(snapshot.getKey());
                                return chat;
                            })
                            .setLifecycleOwner(MainActivity.this)
                            .build();
                    FirebaseRecyclerAdapter<Chat, ChatViewHolder> adapter = new FirebaseRecyclerAdapter<Chat, ChatViewHolder>(options) {
                        @NonNull
                        @Override
                        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            return new ChatViewHolder(LayoutInflater
                                    .from(parent.getContext())
                                    .inflate(R.layout.layout_chat_item, parent, false)
                            );
                        }

                        @Override
                        protected void onBindViewHolder(@NonNull ChatViewHolder holder, int position, @NonNull Chat chat) {
                            holder.bind(chat);
                        }
                    };
                    LinearLayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
                    /* Setting layout manager to reverse since we ordered chats by lastMessageTimestamp.
                     * If not set to reverse, the most recent chat will be at the bottom instead of the top. */
                    layoutManager.setReverseLayout(true);
                    chatRecycler.setLayoutManager(layoutManager);
                    chatRecycler.setItemAnimator(null);
                    chatRecycler.setAdapter(adapter);
                    initialized = true;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }

    /**
     * {@link androidx.recyclerview.widget.RecyclerView.ViewHolder} subclass to hold {@link Chat}
     * items in the RecyclerView
     */
    class ChatViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.chat_item_contact_name) TextView name;
        @BindView(R.id.chat_item_last_message) TextView lastContent;
        @BindView(R.id.chat_item_timestamp) TextView timestamp;
        @BindView(R.id.chat_item_image_contact) CircularImageView image;
        @BindView(R.id.chat_item_root) View root;
        @BindView(R.id.chat_item_image_seen) ImageView statusImage;
        @BindView(R.id.chat_item_unread_dot) ImageView unreadDot;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        void bind(Chat chat) {
            if (chat.isGroup()) {
                // Group chat
                Message message = chat.getLastMessage();
                if (message != null) {
                    setContent(message, chat.isWork());
                    timestamp.setText(TimeUtils.getReadableTime(new Date((Long) message.getServerTimestamp())));
                    if (message.getFrom().equals(user.getUid())) {
                        statusImage.setVisibility(View.VISIBLE);
                    } else {
                        statusImage.setVisibility(View.GONE);

                        // Find name of sender and prepend it to message content
                        DatabaseReference usersRef = rootRef.child("users");
                        usersRef.child(user.getUid()).child("contacts").addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                if (dataSnapshot.hasChild(message.getFrom())) {
                                    String name = (String) dataSnapshot.child(message.getFrom()).child("name").getValue();
                                    message.setContent(name + ": " + message.getContent());
                                    setContent(message, chat.isWork());
                                } else {
                                    usersRef.child(message.getFrom()).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                            String name = (String) dataSnapshot.getValue();
                                            message.setContent(name + ": " + message.getContent());
                                            setContent(message, chat.isWork());
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
                } else {
                    // No messages in group chat, i.e. newly made group
                    statusImage.setVisibility(View.GONE);
                    lastContent.setText("New group");
                    timestamp.setText(TimeUtils.getReadableTime(new Date((Long) chat.getCreated())));
                }

                name.setText(chat.getTitle());
                Glide.with(getApplicationContext())
                        .load(R.drawable.group_default_profile)
                        .into(image);
                root.setOnClickListener((v) -> {
                    // Create bundle containing metadata for ChatActivity
                    Bundle contactBundle = new Bundle();
                    contactBundle.putString("roomId", chat.getRoomId());      // roomId being null implies new room to be created
                    contactBundle.putBoolean("group", true);
                    contactBundle.putString("title", chat.getTitle());
                    contactBundle.putString("desc", chat.getDesc());
                    contactBundle.putBoolean("work", chat.isWork());

                    Intent intent = new Intent(MainActivity.this, ChatActivity.class)
                            .putExtras(contactBundle);

                    // Launch ChatActivity for chat
                    startActivity(intent);
                });
            } else {
                // Regular chat (DM)
                Message message = chat.getLastMessage();
                if (user.getUid().equals(message.getTo())) {
                    statusImage.setVisibility(View.GONE);
                } else {
                    statusImage.setVisibility(View.VISIBLE);
                }
                if (message.isSeen()) {
                    statusImage.setImageResource(R.drawable.ic_seen);
                } else {
                    statusImage.setImageResource(R.drawable.ic_delivered);
                }
                Contact contact = chat.getContact();

                // Set chat message content
                setContent(message, chat.isWork());
                timestamp.setText(TimeUtils.getReadableTime(new Date((Long) message.getServerTimestamp())));

                if (contact != null) {
                    // Contact exists in user's contacts, so we can get their name directly

                    // Observe contact typing status
                    observeTyping(chat, contact.getUid());
                    name.setText(contact.getName());
                    Glide.with(getApplicationContext())
                            .load(contact.getThumb())
                            .placeholder(R.drawable.chat_default_profile)
                            .into(image);
                    root.setOnClickListener((v) -> {
                        // Create bundle containing metadata for ChatActivity
                        Bundle contactBundle = new Bundle();
                        contactBundle.putString("roomId", chat.getRoomId());      // roomId being null implies new room to be created
                        contactBundle.putSerializable("contactData", contact.toMap());
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class)
                                .putExtras(contactBundle);

                        // Launch ChatActivity for chat
                        startActivity(intent);
                    });
                } else {
                    // Contact is new, so we need to fetch the contact's user info from the database

                    String contactUid;
                    if (message.getFrom().equals(user.getUid())) {
                        // Sent
                        contactUid = message.getTo();
                    } else {
                        // Received
                        contactUid = message.getFrom();
                    }
                    // Observe contact typing status
                    observeTyping(chat, contactUid);
                    DatabaseReference contactRef = FirebaseDatabase.getInstance().getReference().child("users").child(contactUid);

                    contactRef.addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                            Contact c = dataSnapshot.getValue(Contact.class);
                            if (c != null) {
                                // Found user
                                c.setUid(contactUid);
                                name.setText(c.getName());
                                if (!c.getThumb().equals(DEFAULT_PHOTO_URL)) {
                                    Glide.with(getApplicationContext())
                                            .load(c.getThumb())
                                            .into(image);
                                }
                                root.setOnClickListener((v) -> {
                                    // Create bundle containing metadata for ChatActivity
                                    Bundle contactBundle = new Bundle();
                                    contactBundle.putString("roomId", chat.getRoomId());      // roomId being null implies new room to be created
                                    contactBundle.putSerializable("contactData", c.toMap());
                                    Intent intent = new Intent(MainActivity.this, ChatActivity.class)
                                            .putExtras(contactBundle);

                                    // Launch ChatActivity for chat
                                    startActivity(intent);
                                });
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {}
                    });
                }
            }
        }

        private void setContent(Message message, boolean work) {
            if (message.isDistracting() && !message.getFrom().equals(user.getUid()) && work) {
                lastContent.setTypeface(null, Typeface.ITALIC);
                lastContent.setText(getText(R.string.msg_distracting_italics));
                //lastContent.setEnabled(false);
            } else {
                //lastContent.setEnabled(true);
                if (message.getType() != null && message.getType().equals(TYPE_IMAGE)) {
                    lastContent.setText("Image");
                    lastContent.setTypeface(null, Typeface.ITALIC);
                    return;
                }
                if (!message.isSeen() && message.getTo().equals(user.getUid())) {
                    unreadDot.setVisibility(View.VISIBLE);
                    lastContent.setTypeface(lastContent.getTypeface(), Typeface.BOLD);
                } else {
                    unreadDot.setVisibility(View.GONE);
                    lastContent.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                }
                lastContent.setText(message.getContent());
            }
        }

        private void observeTyping(Chat chat, String contactUid) {
            DatabaseReference typingRef = rootRef.child("members").child(chat.getRoomId()).child(contactUid).child("typing");
            typingRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        boolean typing = (boolean) dataSnapshot.getValue(Boolean.class);
                        if (typing) {
                            lastContent.setText(R.string.label_typing);
                            lastContent.setTextColor(getResources().getColor(R.color.colorAccent));
                            lastContent.setTypeface(lastContent.getTypeface(), Typeface.BOLD);
                        } else {
                            setContent(chat.getLastMessage(), chat.isWork());
                            lastContent.setTextColor(getResources().getColor(R.color.colorTextTertiary));
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {}
            });
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: ");
        super.onStop();
        PresenceUtils.setOnline(mAuth.getCurrentUser(), false);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart: ");
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        updateUI(mAuth.getCurrentUser());
        PresenceUtils.setOnline(mAuth.getCurrentUser(), true);
    }

    /**
     * Utility method to check if the user is signed in, and sent to login again if not.
     * @param currentUser The current FirebaseUser using the app
     */
    private void updateUI(FirebaseUser currentUser) {
        if (currentUser != null){
            Toast.makeText(this, currentUser.getEmail() + " logged in!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            sendToLogin();
        }
    }

    /**
     * Utility method to start {@link LoginActivity} and clear the backstack
     */
    private void sendToLogin() {
        startActivity(new Intent(this, LoginActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case R.id.main_log_out:
                logOut();
                break;
            case R.id.main_account:
                startActivity(new Intent(this, AccountActivity.class));
                break;
            case R.id.main_new_grp:
                startActivity(new Intent(this, ParticipantsActivity.class));
                break;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * Utility method to log the current user out.
     */
    private void logOut() {
        PresenceUtils.setOnline(mAuth.getCurrentUser(), false);
        rootRef.child("users").child(mAuth.getCurrentUser().getUid()).child("deviceToken").removeValue().addOnSuccessListener(aVoid -> {
            mAuth.signOut();
            Toast.makeText(this, "Logged out", Toast.LENGTH_LONG).show();
            PresenceUtils.starts = 1;
            sendToLogin();
        });
    }

    @Override
    public void onClick(View view) {
        String name = ((TextView) view.findViewById(R.id.chat_item_contact_name)).getText().toString();
        startActivity(new Intent(this, ChatActivity.class)
                .putExtra("contact_name", name));
    }
}
