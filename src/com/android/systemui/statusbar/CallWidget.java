
package com.android.systemui.statusbar;

import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.internal.telephony.CallManager;
import com.android.internal.telephony.ITelephony;
import com.android.systemui.R;

public class CallWidget extends LinearLayout implements OnClickListener {

    private static final FrameLayout.LayoutParams WIDGET_LAYOUT_PARAMS = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, // width = match_parent
            ViewGroup.LayoutParams.WRAP_CONTENT // height = wrap_content
    );

    private static final LinearLayout.LayoutParams BUTTON_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width = wrap_content
            ViewGroup.LayoutParams.MATCH_PARENT, // height = match_parent
            2.0f // weight = 1
    );

    public Button mPhoneButton;
    public Button mMuteOn;
    public Button mMuteOff;
    public Button mSpeakerOn;
    public Button mSpeakerOff;
    public Button mEndCallButton;

    private Chronometer mTimer;

    ITelephony phone;
    TelephonyManager telephony;
    AudioManager am;
    CallManager mCM;

    private StatusBarManager mStatusBar;

    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    mMuteOn.setEnabled(false);
                    mMuteOff.setEnabled(false);
                    mTimer.setVisibility(View.GONE);
                    hideMusic();
                    show();
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    mMuteOn.setEnabled(true);
                    mMuteOff.setEnabled(true);
                    show();
                    hideMusic();
                    mTimer.setVisibility(View.VISIBLE);
                    mTimer.setBase(SystemClock.elapsedRealtime());
                    mTimer.start();
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    hide();
                    showMusic();
                    mTimer.setVisibility(View.GONE);
                    mMuteOn.setEnabled(true);
                    mMuteOff.setEnabled(true);
                    break;
            }
            updateWidget();
        }

    };
    private LayoutInflater mInflater;

    private static String TAG = "CallWidget";

    private CallHandler mHandler = new CallHandler();

    public CallWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        hide();
        Slog.i(TAG, "Creating call widget from XML");

        mStatusBar = (StatusBarManager) context.getSystemService(Context.STATUS_BAR_SERVICE);
        telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        setupButtons();

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        context.registerReceiver(new PhoneStateReceiver(), filter);

    }

    protected void showMusic() {
        mContext.sendOrderedBroadcast(new Intent(MusicControls.CALL_ENDED_INTENT), null);
    }

    protected void hideMusic() {
        mContext.sendOrderedBroadcast(new Intent(MusicControls.CALL_STARTED_INTENT), null);
    }

    public void setupButtons() {
        Slog.i(TAG, "Setting up call widget");
        View callView = mInflater.inflate(R.layout.call_ongoing, null, false);
        addView(callView, BUTTON_LAYOUT_PARAMS);

        mPhoneButton = (Button) findViewById(R.id.ongoing_call_button);
        mPhoneButton.setOnClickListener(this);

        mMuteOff = (Button) findViewById(R.id.quickpanel_call_mute_off);
        mMuteOff.setOnClickListener(this);

        mMuteOn = (Button) findViewById(R.id.quickpanel_call_mute_on);
        mMuteOn.setOnClickListener(this);

        mSpeakerOff = (Button) findViewById(R.id.quickpanel_call_speaker_off);
        mSpeakerOff.setOnClickListener(this);

        mSpeakerOn = (Button) findViewById(R.id.quickpanel_call_speaker_on);
        mSpeakerOn.setOnClickListener(this);

        mEndCallButton = (Button) findViewById(R.id.quickpanel_call_end);
        mEndCallButton.setOnClickListener(this);

        mTimer = (Chronometer) findViewById(R.id.quickpanel_time);
    }

    public void updateWidget() {
        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        if (am.isSpeakerphoneOn()) {
            Slog.i(TAG, "Speaker is on");
            mSpeakerOn.setVisibility(View.VISIBLE);
            mSpeakerOff.setVisibility(View.GONE);
        } else {
            Slog.i(TAG, "Speaker is off");
            mSpeakerOff.setVisibility(View.VISIBLE);
            mSpeakerOn.setVisibility(View.GONE);
        }

        if (am.isMicrophoneMute()) {
            mMuteOn.setVisibility(View.VISIBLE);
            mMuteOff.setVisibility(View.GONE);
        } else {
            mMuteOff.setVisibility(View.VISIBLE);
            mMuteOn.setVisibility(View.GONE);
        }

        // mSpeakerPhoneButton.setChecked(am.isSpeakerphoneOn());
        // mMuteButton.setChecked(am.isMicrophoneMute());
    }

    public void show() {
        Slog.i(TAG, "Show call widget");
        setVisibility(View.VISIBLE);

        if (mTimer != null) {
            mTimer.setVisibility(View.VISIBLE);
        }
    }

    public void hide() {
        Slog.i(TAG, "Hide call widget");
        setVisibility(View.GONE);

        if (mTimer != null) {
            mTimer.stop();
            mTimer.setVisibility(View.GONE);
        }
    }

    class PhoneStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            telephony.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    private static final int ACTION_HANGUP = 10;
    private static final int ACTION_UPDATE = 11;
    private static final int ACTION_MUTE = 12;
    private static final int ACTION_SPEAKER = 13;
    private static final int ACTION_SHOW_SCREEN = 14;

    private class CallHandler extends Handler {

        public void handleMessage(Message m) {
            switch (m.what) {
                case ACTION_HANGUP:
                    mContext.sendOrderedBroadcast(new Intent("com.android.phone.END"), null);
                    break;
                case ACTION_UPDATE:
                    updateWidget();
                    break;
                case ACTION_MUTE:
                    mContext.sendOrderedBroadcast(new Intent("com.android.phone.MUTE"), null);
                    break;
                case ACTION_SPEAKER:
                    mContext.sendOrderedBroadcast(new Intent("com.android.phone.SPEAKER"), null);
                    break;
                case ACTION_SHOW_SCREEN:
                    mContext.sendOrderedBroadcast(new Intent("com.android.phone.SHOW_SCREEN"), null);
                    break;
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mPhoneButton) {
            mHandler.sendEmptyMessage(ACTION_SHOW_SCREEN);
        } else if (v == mEndCallButton) {
            mHandler.sendEmptyMessage(ACTION_HANGUP);
        } else if (v == mMuteOff) {
            mHandler.sendEmptyMessage(ACTION_MUTE);
            mMuteOff.setVisibility(View.GONE);
            mMuteOn.setVisibility(View.VISIBLE);
        } else if (v == mMuteOn) {
            mHandler.sendEmptyMessage(ACTION_MUTE);
            mMuteOn.setVisibility(View.GONE);
            mMuteOff.setVisibility(View.VISIBLE);
        } else if (v == mSpeakerOff) {
            mHandler.sendEmptyMessage(ACTION_SPEAKER);
            mSpeakerOff.setVisibility(View.GONE);
            mSpeakerOn.setVisibility(View.VISIBLE);
        } else if (v == mSpeakerOn) {
            mHandler.sendEmptyMessage(ACTION_SPEAKER);
            mSpeakerOn.setVisibility(View.GONE);
            mSpeakerOff.setVisibility(View.VISIBLE);
        }
        mHandler.sendEmptyMessageDelayed(ACTION_UPDATE, 1000);
    }
}
