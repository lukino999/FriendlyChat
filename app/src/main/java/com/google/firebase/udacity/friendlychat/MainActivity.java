/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    // request codes
    private static final int RC_SIGN_IN = 1001;
    private static final int RC_PHOTO_PICKER = 1002;
    private static final int RC_READ_EXT_STORAGE = 1003;
    private static final String REMOTE_MSG_LENGHT_KEY = "max_msg_length";

    @BindView(R.id.progressBar)
    ProgressBar mProgressBar;
    @BindView(R.id.messageListView)
    ListView mMessageListView;
    @BindView(R.id.photoPickerButton)
    ImageButton mPhotoPickerButton;
    @BindView(R.id.messageEditText)
    EditText mMessageEditText;
    @BindView(R.id.sendButton)
    Button mSendButton;

    // child event listener
    private ChildEventListener mChildEventListener;
    private MessageAdapter mMessageAdapter;
    private String mUsername;
    // database main access point
    private FirebaseDatabase mFirebaseDatabase;
    // reference to a specific part of the database
    private DatabaseReference mMessagesDatabaseReference;
    // auth
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    // storage
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mStorageReference;
    // remote config
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // plant timber
        Timber.plant(new Timber.DebugTree());
        Timber.i("onCreate: ");

        // bind
        ButterKnife.bind(this);

        mUsername = ANONYMOUS;

        // initialize
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        // get a reference to the root node "messages"
        mMessagesDatabaseReference = mFirebaseDatabase.getReference().child("messages");
        // get reference to remote folder "chat_photos"
        mStorageReference = mFirebaseStorage.getReference().child("chat_photos");


        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        //mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});
        applyMsgMaxLenght(DEFAULT_MSG_LENGTH_LIMIT);


        // initialize the mAuthStateListener
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {

                FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {

                    onSignedInInitialize(user.getDisplayName());

                } else {

                    // User is null. We are signed out
                    onSignedOutCleanup();

                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                            new AuthUI.IdpConfig.GoogleBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        // set remote config settings
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(true)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);

        Map<String, Object> defaultConfigSettings = new HashMap<>();
        defaultConfigSettings.put(REMOTE_MSG_LENGHT_KEY, DEFAULT_MSG_LENGTH_LIMIT);

        mFirebaseRemoteConfig.setDefaults(defaultConfigSettings);
        //fetchConfig();

    }


    private void fetchConfig() {
        long cacheExpiration = 3600;
        Timber.d("fetchConfig: begin");

        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            Timber.d("fetchConfig: devModeEnabled");
            cacheExpiration= 0;
        }

        mFirebaseRemoteConfig.fetch(cacheExpiration)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                mFirebaseRemoteConfig.activateFetched();
                long max_msg_length = mFirebaseRemoteConfig.getLong(REMOTE_MSG_LENGHT_KEY);
                Timber.d("fetchConfig().onSuccess: max_msg_length  = %s", max_msg_length);
                applyMsgMaxLenght((int) max_msg_length);
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Timber.d(e,"fetchConfig().onFailure: ");
                long max_msg_length = mFirebaseRemoteConfig.getLong(REMOTE_MSG_LENGHT_KEY);
                applyMsgMaxLenght((int) max_msg_length);
            }
        });


    }

    private void applyMsgMaxLenght(int max_msg_length) {
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max_msg_length)});
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed in", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER) {
            if (resultCode == RESULT_OK) {

                Uri imageUri = data.getData();
                StorageReference imageRef = mStorageReference.child(imageUri.getLastPathSegment());


                final UploadTask uploadTask = imageRef.putFile(imageUri);

                uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                        // At the time I build this, I am using
                        // com.google.firebase:firebase-storage:16.0.1
                        // Apparently you can no longer use taskSnapshot.getDownloadUrl()
                        // In this version from taskSnapshot .getMetadata().getReference()
                        // .getDownloadUrl() as asynchronous task:
                        // first get the task
                        Task<Uri> downloadUrl = taskSnapshot
                                .getMetadata()
                                .getReference()
                                .getDownloadUrl();

                        // attach a OnSuccessListener<Uri>()
                        downloadUrl.addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {

                                // we got the Uri. Push the message
                                FriendlyMessage friendlyMessage = new FriendlyMessage(
                                        null, mUsername, uri.toString()
                                );
                                mMessagesDatabaseReference.push().setValue(friendlyMessage);

                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Timber.e(e, "onFailure: %s", e.getMessage());
                    }
                });

            }
        }
    }


    //
    private void onSignedOutCleanup() {

        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    private void detachDatabaseReadListener() {

        if (mChildEventListener != null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventListener);
            mChildEventListener = null;
        }

    }


    //
    private void onSignedInInitialize(String displayName) {

        mUsername = displayName;
        attachDatabaseReadListener();

    }

    private void attachDatabaseReadListener() {

        if (mChildEventListener == null) {

            // initialize the mChildEventListener
            mChildEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                }
            };
            // add the listener to the mMessagesDatabaseReference
            mMessagesDatabaseReference.addChildEventListener(mChildEventListener);
        }

    }


    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);

        // fetch remote configuration
        fetchConfig();
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }

        detachDatabaseReadListener();
        mMessageAdapter.clear();

    }


    // Send button click
    // Sends the message and clears the EditText
    @OnClick(R.id.sendButton)
    void sendButtonClick() {

        String message = mMessageEditText.getText().toString();

        // create a friendlyMessage
        FriendlyMessage friendlyMessage = new FriendlyMessage(message, mUsername, null);

        // use the push method in order to assign a unique ID to the message
        mMessagesDatabaseReference.push().setValue(friendlyMessage);

        // Clear input box
        mMessageEditText.setText("");

    }


    // Pick Photo Click
    @OnClick(R.id.photoPickerButton)
    void photoPickerButtonClick() {
        // check permission
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pickPhoto();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    RC_READ_EXT_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case RC_READ_EXT_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickPhoto();
                }
                break;
        }


    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                // sign out
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }
}
