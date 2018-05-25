package com.google.firebase.udacity.friendlychat;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import timber.log.Timber;

public class MessageAdapter extends ArrayAdapter<FriendlyMessage> {

    private final float IMAGE_WIDTH_RATIO = 0.75f;
    private String mUsername;
    private int mMessageLeft;
    private int mMessageRight;

    public MessageAdapter(Context context, int messageLeft, int messageRight, List<FriendlyMessage> objects, String username) {
        super(context, messageLeft, messageRight, objects);
        this.mUsername = username;
        this.mMessageLeft = messageLeft;
        this.mMessageRight = messageRight;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        FriendlyMessage message = getItem(position);

        // move msg to left if comes form logged in user
        // or right if comes from other users
        boolean isOwnMessage = message.getName().equals(mUsername);


        Timber.d("getView: mUsername = %s, message.getName() = %s, isOwnMessage %s",
                mUsername,
                message.getName(),
                isOwnMessage);


        int res;
        if (isOwnMessage) {
            Timber.d("getView: right");
            res = mMessageRight;
        } else {
            Timber.d("getView: left");
            res = mMessageLeft;
        }


        convertView = ((Activity) getContext()).getLayoutInflater().inflate(res, parent, false);


        ImageView photoImageView = convertView.findViewById(R.id.photoImageView);
        TextView messageTextView = convertView.findViewById(R.id.messageTextView);
        TextView authorTextView = convertView.findViewById(R.id.nameTextView);


        boolean isPhoto = message.getPhotoUrl() != null;
        if (isPhoto) {
            messageTextView.setVisibility(View.GONE);
            photoImageView.setVisibility(View.VISIBLE);
            int chatWidth = parent.getWidth();
            photoImageView.getLayoutParams().width = (int) ((float) chatWidth * IMAGE_WIDTH_RATIO);
            photoImageView.requestLayout();
            GlideApp.with(photoImageView.getContext())
                    .load(message.getPhotoUrl())
                    .placeholder(android.R.drawable.ic_media_pause)
                    .error(android.R.drawable.ic_dialog_alert)
                    .fitCenter()
                    .into(photoImageView);
        } else {
            messageTextView.setVisibility(View.VISIBLE);
            photoImageView.setVisibility(View.GONE);
            messageTextView.setText(message.getText());
        }
        authorTextView.setText(message.getName());

        return convertView;
    }
}
