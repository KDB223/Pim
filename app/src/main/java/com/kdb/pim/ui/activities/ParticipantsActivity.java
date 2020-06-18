package com.kdb.pim.ui.activities;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.kdb.pim.R;
import com.kdb.pim.data.Contact;
import com.kdb.pim.data.Group;
import com.kdb.pim.utils.Messenger;
import com.mikhaellopez.circularimageview.CircularImageView;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.kdb.pim.utils.Constants.DEFAULT_PHOTO_URL;
import static com.kdb.pim.utils.ContactUtils.RC_CONTACTS;

/**
 * Activity similar to ContactsActivity but used for creating new group chats
 */
public class ParticipantsActivity extends AppCompatActivity {

    static final String TAG = "pim.participants";
    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.contacts_recycler) RecyclerView contactsRecycler;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    private Set<Contact> participantsSet;

    private FirebaseAuth mAuth;
    private FirebaseUser user;
    private DatabaseReference usersReference;
    private DatabaseReference rootReference;
    private FirebaseRecyclerAdapter<Contact, ContactViewHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_participants);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener((v) -> onBackPressed());

        mAuth = FirebaseAuth.getInstance();
        user = mAuth.getCurrentUser();
        rootReference = FirebaseDatabase.getInstance().getReference();
        usersReference = rootReference.child("users");

        participantsSet = new HashSet<>();
        setupRecyclerView();
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
            usersReference = FirebaseDatabase.getInstance().getReference().child("users");
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

            adapter = new FirebaseRecyclerAdapter<Contact, ContactViewHolder>(options) {
                ValueAnimator animator;
                @NonNull
                @Override
                public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    Log.d(TAG, "onCreateViewHolder: ");
                    View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_participant_item, parent, false);
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
                        // Select contact and update list
                        if (participantsSet.contains(contact)) {
                            // Hide check
                            holder.check.setVisibility(View.GONE);

                            // Make picture normal sized
                            animateImage(holder.image, true);

                            participantsSet.remove(contact);
                        } else {
                            // Show check
                            holder.check.setVisibility(View.VISIBLE);

                            // Make picture small
                            animateImage(holder.image, false);

                            participantsSet.add(contact);
                        }
                    });
                }

                private void animateImage(CircularImageView image, boolean reset) {
                    if (animator == null) {
                        animator = ValueAnimator.ofFloat(1.0f, 0.8f);
                        animator.setDuration(150);
                        animator.setInterpolator(new FastOutSlowInInterpolator());
                    }
                    animator.removeAllUpdateListeners();
                    animator.addUpdateListener(valueAnimator -> {
                        image.setScaleX((Float) valueAnimator.getAnimatedValue());
                        image.setScaleY((Float) valueAnimator.getAnimatedValue());
                    });
                    if (reset) {
                        animator.reverse();
                    } else {
                        animator.start();
                    }
                }

            };

            contactsRecycler.setAdapter(adapter);
            Log.d(TAG, "setupRecyclerView: Adapter set");
        }
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
        @BindView(R.id.contact_selected_check) ImageView check;
        Context context;

        ContactViewHolder(@NonNull View itemView, Context context) {
            super(itemView);
            this.context = context;
            ButterKnife.bind(this, itemView);
            Log.d(TAG, "ContactViewHolder: ");
        }
    }

    /**
     * Utility method to set the UI to a loading state (greyed out screen and active loading bar)
     * @param loading boolean value for whether to set UI to loading state or not
     */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        super.onOptionsItemSelected(item);
        if (item.getItemId() == R.id.menu_participants_done) {
            if (participantsSet.isEmpty()) {
                Toast.makeText(this, "No participants selected", Toast.LENGTH_SHORT).show();
            } else {
                showNameDialog();
            }
        }
        return true;
    }

    /**
     * Shows an {@link AlertDialog} with input fields for group information
     */
    private void showNameDialog() {
        View groupForm = LayoutInflater.from(this).inflate(R.layout.layout_dialog_group_name, null, false);
        EditText title = groupForm.findViewById(R.id.title_editText);
        EditText desc = groupForm.findViewById(R.id.desc_editText);
        CheckBox work = groupForm.findViewById(R.id.checkBox_work);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setPositiveButton("Create", (d, i) -> {
                    createGroup(title.getText().toString(), desc.getText().toString(), work.isChecked());
                    finish();
                })
                .setNegativeButton("Cancel", (d, i) -> {
                    // Do nothing
                })
                .setTitle("Add group info")
                .setView(groupForm)
                .create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);

        title.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Set "Create" button to enabled only if group name field is not empty
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(title.getText().length() != 0);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

    }

    /**
     * Creates a new group chat room using {@link Messenger}
     * @param title
     * @param desc
     * @param work
     */
    private void createGroup(String title, String desc, boolean work) {
        Contact user = new Contact();
        user.setUid(mAuth.getCurrentUser().getUid());
        participantsSet.add(user);
        Group g =  new Group(title, desc, participantsSet, work);
        Messenger.createRoom(null, rootReference, e -> {
            Toast.makeText(this, "Error creating group: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }, g);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_participants, menu);
        return true;
    }
}
