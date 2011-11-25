/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;

public class MusicControls extends FrameLayout {

    private static final String TAG = "MusicControls";

    private static final FrameLayout.LayoutParams WIDGET_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, // width = match_parent
            ViewGroup.LayoutParams.WRAP_CONTENT // height = wrap_content
    );

    private static final LinearLayout.LayoutParams BUTTON_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width = wrap_content
            ViewGroup.LayoutParams.MATCH_PARENT, // height = match_parent
            2.0f // weight = 1
    );

    private static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart/");

    private Context mContext;
    private LayoutInflater mInflater;
    private AudioManager mAudioManager;

    private StatusBarService mSBService;
    private AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    private boolean mIsMusicActive = am.isMusicActive();

    private ImageButton mPlayIcon;
    private ImageButton mPauseIcon;
    private ImageButton mRewindIcon;
    private ImageButton mForwardIcon;
    private ImageButton mAlbumArt;

    private TextView mNowPlayingInfo;

    private static String mArtist = null;
    private static String mTrack = null;
    private static Boolean mPlaying = null;
    private static long mSongId = 0;
    private static long mAlbumId = 0;

    int noMusicPLayingCounter = 0;
    boolean mWasMusicActive = false;

    private LinearLayout ll;

    private MusicHandler musicHandler = new MusicHandler();

    public MusicControls(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        IntentFilter iF = new IntentFilter();
        iF.addAction("com.android.music.playstatechanged");
        iF.addAction("com.android.music.metachanged");
        iF.addAction("com.android.music.musicservicecommand.mediainfo");
        mContext.registerReceiver(mMusicReceiver, iF);
    }

    public void setupControls() {
        Slog.d(TAG, "Setting Up Music Controls");
        LinearLayout ll = new LinearLayout(mContext);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_HORIZONTAL);

        View controlsView = mInflater.inflate(R.layout.exp_music_controls, null, false);
        ll.addView(controlsView, BUTTON_LAYOUT_PARAMS);
        addView(ll, WIDGET_LAYOUT_PARAMS);

        mPauseIcon = (ImageButton) findViewById(R.id.musicControlPause);
        mPlayIcon = (ImageButton) findViewById(R.id.musicControlPlay);
        mPlayIcon.setVisibility(View.INVISIBLE);
        mPauseIcon.setVisibility(View.VISIBLE);
        mRewindIcon = (ImageButton) findViewById(R.id.musicControlPrevious);
        mForwardIcon = (ImageButton) findViewById(R.id.musicControlNext);
        mNowPlayingInfo = (TextView) findViewById(R.id.musicNowPlayingInfo);
        mNowPlayingInfo.setSelected(true); // set focus to TextView to allow
                                           // scrolling

        mNowPlayingInfo.setTextColor(0xffffffff);
        mAlbumArt = (ImageButton) findViewById(R.id.albumArt);

        mPauseIcon.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                mPlayIcon.setVisibility(View.VISIBLE);
                mPauseIcon.setVisibility(View.INVISIBLE);
            }
        });

        mPlayIcon.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                mPlayIcon.setVisibility(View.INVISIBLE);
                mPauseIcon.setVisibility(View.VISIBLE);
            }
        });

        mRewindIcon.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
                mPlayIcon.setVisibility(View.INVISIBLE);
                mPauseIcon.setVisibility(View.VISIBLE);
            }
        });

        mForwardIcon.setOnClickListener(new View.OnClickListener() {

            public void onClick(View v) {
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
                mPlayIcon.setVisibility(View.INVISIBLE);
                mPauseIcon.setVisibility(View.VISIBLE);
            }
        });

        setVisibility(View.GONE);
        updateControls();
        noMusicPLayingCounter = 0;
    }

    public void updateControls() {
        Slog.d(TAG, "Updating Music Controls Visibility");
        mIsMusicActive = am.isMusicActive();

        if (!mIsMusicActive && getVisibility() == View.VISIBLE) {

            if (noMusicPLayingCounter++ < 2) {
                mWasMusicActive = true;
            } else {
                mWasMusicActive = false;
                noMusicPLayingCounter = 0;
            }

        } else {
            mWasMusicActive = false;
        }

        if (mIsMusicActive || mWasMusicActive) {
            // Slog.d(TAG, "Music is active");
            updateInfo();
            mSBService.mMusicToggleButton.setVisibility(View.VISIBLE);
            setVisibility(View.VISIBLE);
        } else {
            // Slog.d(TAG, "Music is not active");
            mSBService.mMusicToggleButton.setVisibility(View.GONE);
            setVisibility(View.GONE);
            cancelSamsungNotification();
        }
    }

    public void cancelSamsungNotification() {
        musicHandler.postDelayed(clearSamsungNotification, 1000);

        // getContext().sendOrderedBroadcast(
        // new
        // Intent("com.android.music.musicservicecommand.hide.notification"),
        // null);
    }

    public void disable() {
        this.removeAllViews();
    }

    public void visibilityToggled() {
        if (this.getVisibility() == View.VISIBLE) {
            setVisibility(View.GONE);
        } else {
            setVisibility(View.VISIBLE);
        }
    }

    boolean mIsAlbumArtSet = false;

    public void updateInfo() {
        mIsMusicActive = am.isMusicActive();

        if (mIsMusicActive) {
            mPlayIcon.setVisibility(View.INVISIBLE);
            mPauseIcon.setVisibility(View.VISIBLE);
        } else {
            mPlayIcon.setVisibility(View.VISIBLE);
            mPauseIcon.setVisibility(View.INVISIBLE);
        }

        if (!mIsAlbumArtSet) {
            Uri uri = getArtworkUri(getContext(), SongId(), AlbumId());

            if (uri != null) {
                mAlbumArt.setImageURI(uri);
            } else {
                // updateInfo(uri);
                // setSamsungArtwork(getContext(), SongId(), AlbumId());
                mAlbumArt.setImageResource(R.drawable.default_artwork);

                mAlbumArt.setOnClickListener(startPlayer);
            }

        }
        String nowPlayingArtist = NowPlayingArtist();
        String nowPlayingAlbum = NowPlayingAlbum();
        mNowPlayingInfo.setText(nowPlayingArtist + " - " + nowPlayingAlbum);

    }

    public void updateInfo(Uri albumUri) {

        if (albumUri != null) {
            mAlbumArt.setImageURI(albumUri);
        } else {
            // updateInfo(uri);
            // setSamsungArtwork(getContext(), SongId(), AlbumId());
            mAlbumArt.setImageResource(R.drawable.default_artwork);
        }
        String nowPlayingArtist = NowPlayingArtist();
        String nowPlayingAlbum = NowPlayingAlbum();
        mNowPlayingInfo.setText(nowPlayingArtist + " - " + nowPlayingAlbum);

    }
    private OnClickListener startPlayer = new OnClickListener() {

        @Override
        public void onClick(View v) {
            sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);

        }
    };

    public static Uri getArtworkUri(Context context, long song_id, long album_id) {

        if (album_id < 0) {
            // This is something that is not in the database, so get the album
            // art directly
            // from the file.
            if (song_id >= 0) {
                return getArtworkUriFromFile(context, song_id, -1);
            }
            return null;
        }

        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                return uri;
            } catch (FileNotFoundException ex) {
                // The album art thumbnail does not actually exist. Maybe the
                // user deleted it, or
                // maybe it never existed to begin with.
                return getArtworkUriFromFile(context, song_id, album_id);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
        }
        return null;
    }

    private static Uri getArtworkUriFromFile(Context context, long songid, long albumid) {
        if (albumid < 0 && songid < 0) {
            return null;
        }

        try {
            if (albumid < 0) {
                Uri uri = Uri.parse("content://media/external/audio/media/" + songid + "/albumart");
                ParcelFileDescriptor pfd = context.getContentResolver()
                        .openFileDescriptor(uri, "r");
                if (pfd != null) {
                    return uri;
                }
            } else {
                Uri uri = ContentUris.withAppendedId(sArtworkUri, albumid);
                ParcelFileDescriptor pfd = context.getContentResolver()
                        .openFileDescriptor(uri, "r");
                if (pfd != null) {
                    return uri;
                }
            }
        } catch (FileNotFoundException ex) {
        }
        return null;
    }

    private Uri mMediaUri;
    private boolean updateAlbumArt = false;

    private BroadcastReceiver mMusicReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals("com.android.music.musicservicecommand.mediainfo")) {
                Slog.e(TAG, "Intent from Sammy");
                mMediaUri = intent.getParcelableExtra("mediauri");

                Cursor c = getContext().getContentResolver().query(mMediaUri, null, null, null,
                        null);
                if (c != null && c.getCount() != 0) {
                    c.moveToFirst();
                    // mTrack = c.getString(c.getColumnIndexOrThrow("title"));
                    // mArtist = c.getString(c.getColumnIndexOrThrow("artist"));
                    mAlbumId = Long.parseLong(c.getString(c.getColumnIndexOrThrow("album_id")));
                    c.close();
                    mIsAlbumArtSet = false;
                    // updateInfo(getArtworkUri(context, 0, mAlbumId));
                    musicHandler.removeMessages(MSG_UPDATEINFO);
                    musicHandler.sendEmptyMessage(MSG_UPDATESAMMYUNFO);

                }
            } else {

                Slog.e(TAG, action);
                Slog.e(TAG, "Intent from Android");
                mArtist = intent.getStringExtra("artist");
                mTrack = intent.getStringExtra("track");
                mPlaying = intent.getBooleanExtra("playing", false);
                mSongId = intent.getLongExtra("songid", 0);

                mAlbumId = intent.getLongExtra("albumid", 0);
                if (mAlbumId == 0)
                    mIsAlbumArtSet = false;

                musicHandler.sendEmptyMessageDelayed(MSG_UPDATEINFO, 500);
            }

        }
    };

    public static String NowPlayingArtist() {
        if (mArtist != null) {
            return (mArtist);
        } else {
            return "unknown";
        }
    }

    public static String NowPlayingAlbum() {
        if (mArtist != null) {
            return (mTrack);
        } else {
            return "unknown";
        }
    }

    public static long SongId() {
        return mSongId;
    }

    public static long AlbumId() {
        return mAlbumId;
    }

    private Runnable clearSamsungNotification = new Runnable() {

        @Override
        public void run() {
            getContext().sendOrderedBroadcast(
                    new Intent("com.android.music.musicservicecommand.hide.notification"), null);

        }
    };

    private void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        getContext().sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        getContext().sendOrderedBroadcast(upIntent, null);
    }

    final static int MSG_UPDATEINFO = 101;
    final static int MSG_CLEARSAMMYNOTIFICATION = 102;
    final static int MSG_UPDATESAMMYUNFO = 103;

    private class MusicHandler extends Handler {

        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_UPDATEINFO:
                    updateInfo();
                    break;
                case MSG_UPDATESAMMYUNFO:
                    updateInfo();
                    break;
            }
        }
    }
}
