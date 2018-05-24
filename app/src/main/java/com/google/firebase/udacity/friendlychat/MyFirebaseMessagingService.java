package com.google.firebase.udacity.friendlychat;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import timber.log.Timber;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private String TAG = "MyFirebaseMessagingService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Timber.d("onMessageReceived: from: %s", remoteMessage.getFrom());

        if (remoteMessage.getData().size() > 0) {
            Timber.d("onMessageReceived: payload: %s", remoteMessage.getData());
        }

        if (remoteMessage.getNotification() != null) {
            Timber.d("onMessageReceived: notification %s", remoteMessage.getNotification().getBody());
        }

    }
}
