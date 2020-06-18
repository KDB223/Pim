package com.kdb.pim.ui.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
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
import com.kdb.pim.utils.ContactUtils;
import com.kdb.pim.utils.PresenceUtils;
import com.mikhaellopez.circularimageview.CircularImageView;

import java.util.Map;
import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.kdb.pim.utils.Constants.DEFAULT_PHOTO_URL;
import static com.kdb.pim.utils.ContactUtils.RC_CONTACTS;

/**
 * Activity to select a contact to start a chat or join an existing chat with.
 */
public class ContactsActivity extends AppCompatActivity {
    static final String TAG = "pim.contacts";
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.contacts_recycler) RecyclerView contactsRecycler;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private DatabaseReference rootReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener((v) -> onBackPressed());

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        rootReference = FirebaseDatabase.getInstance().getReference();

        setupRecyclerView();
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

    /**
     * Sets up the RecyclerView which will hold the {@link Contact} items after getting the contacts
     * permission from the user
     */
    @AfterPermissionGranted(RC_CONTACTS)
    private void setupRecyclerView() {
        if (!EasyPermissions.hasPermissions(this, Manifest.permission.READ_CONTACTS)) {
            EasyPermissions.requestPermissions(this, "To start chatting, please allow Pim to access your contacts",
                    RC_CONTACTS, Manifest.permission.READ_CONTACTS);
        } else {
            // Refresh contacts
            DatabaseReference contactsReference = FirebaseDatabase.getInstance().getReference().child("users").child(user.getUid()).child("contacts");

            contactsRecycler.setLayoutManager(new LinearLayoutManager(this));

            FirebaseRecyclerOptions<Contact> options = new FirebaseRecyclerOptions.Builder<Contact>()
                    .setQuery(contactsReference.orderByChild("name"), snapshot -> {
                        String name = (String) snapshot.child("name").getValue();
                        String status = (String) snapshot.child("status").getValue();
                        String phone = (String) snapshot.child("phone").getValue();
                        String thumb = (String) snapshot.child("thumb").getValue();
                        Contact contact = new Contact(name, status, phone, thumb);
                        contact.setUid(snapshot.getKey());
                        return contact;
                    })
                    .setLifecycleOwner(this)
                    .build();

            FirebaseRecyclerAdapter<Contact, ContactViewHolder> adapter = new FirebaseRecyclerAdapter<Contact, ContactViewHolder>(options) {
                @NonNull
                @Override
                public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    Log.d(TAG, "onCreateViewHolder: ");
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_contact_item, parent, false);
                    return new ContactViewHolder(view, parent.getContext());
                }

                @Override
                protected void onBindViewHolder(@NonNull ContactViewHolder holder, int position, @NonNull Contact contact) {
                    Log.d(TAG, "onBindViewHolder: " + contact.getName());
                    setLoading(false);
                    holder.name.setText(contact.getName());
                    holder.status.setText(contact.getStatus());
                    if (!contact.getThumb().equals(DEFAULT_PHOTO_URL)) {
                        Glide.with(getApplicationContext())
                                .load(contact.getThumb())
                                .placeholder(R.drawable.chat_default_profile)
                                .into(holder.image);
                    }
                    holder.root.setOnClickListener((v) -> {
                        // Fetch and put room ID
                        DatabaseReference membersRef = rootReference.child("members");
                        membersRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                String roomId = null;
                                // Iterate through each room and check if both users exist in it
                                for (DataSnapshot room : dataSnapshot.getChildren()) {
                                    if (room.hasChild(user.getUid()) && room.hasChild(contact.getUid())) {      // Room exists
                                        roomId = room.getKey();
                                    }
                                }
                                Bundle contactBundle = new Bundle();
                                contactBundle.putString("roomId", roomId);      // roomId being null implies new room to be created
                                contactBundle.putSerializable("contactData", contact.toMap());
                                Intent intent = new Intent(holder.context, ChatActivity.class)
                                        .putExtras(contactBundle);
                                startActivity(intent);
                                finish();
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {
                                Toast.makeText(ContactsActivity.this, "Error opening chat: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }

            };

            contactsRecycler.setAdapter(adapter);
            Log.d(TAG, "setupRecyclerView: Adapter set");
        }
    }

    /**
     * Utility method to redo the contact adding process initially done at user registration
     */
    private void reloadContacts() {
        setLoading(true);
        ContactUtils.getInstance().setListener(() -> setLoading(false));
        ContactUtils.getInstance().loadContacts(this, FirebaseDatabase.getInstance().getReference().child("users"), mAuth.getCurrentUser());
    }

    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == R.id.contacts_menu_refresh) {
            reloadContacts();
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_contacts, menu);
        return true;
    }

    /**
     * {@link RecyclerView.ViewHolder} subclass to hold {@link Contact}
     * items in the RecyclerView
     */
    class ContactViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.contact_item_name) TextView name;
        @BindView(R.id.contact_item_status) TextView status;
        @BindView(R.id.contact_item_thumb) CircularImageView image;
        @BindView(R.id.contact_item_root) View root;
        Context context;

        ContactViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            this.context = context;
            ButterKnife.bind(this, itemView);
            Log.d(ContactsActivity.TAG, "ContactViewHolder: ");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
}
