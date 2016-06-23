package net.qyvideo.qianyiplayer;

import android.app.Activity;
import android.app.ActivityManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup.LayoutParams;
import android.content.Context;
import android.content.res.Configuration;

import com.ksyun.media.player.IMediaPlayer;
import com.ksyun.media.player.KSYMediaMeta;
import com.ksyun.media.player.KSYMediaPlayer;
import com.ksyun.media.player.misc.IMediaFormat;
import com.ksyun.media.player.misc.ITrackInfo;
import com.ksyun.media.player.misc.KSYMediaFormat;
import com.ksyun.media.player.misc.KSYQosInfo;


import net.qyvideo.qianyiplayer.util.QosObject;
import net.qyvideo.qianyiplayer.util.QosThread;

import org.w3c.dom.Text;

import java.io.IOException;
import java.security.MessageDigest;  
import java.security.NoSuchAlgorithmException; 


/**
 * @author xinbocheng@qyvideo.cn
 * 2015/5/19.
 */
public class VideoPlayerActivity extends Activity{

    private static final String TAG = "VideoPlayerActivity";

    public static final int UPDATE_SEEKBAR = 0;
    public static final int HIDDEN_SEEKBAR = 1;
    public static final int UPDATE_QOS  = 2;

    private Context mContext;
    private KSYMediaPlayer ksyMediaPlayer;
    private QosThread mQosThread;

    private Surface mSurface = null;
    private SurfaceView mVideoSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;

    private Handler mHandler;

    // UI
    private LinearLayout mPlayerPanel;
    private ImageView mPlayerStartBtn;
    private SeekBar mPlayerSeekbar;
    private TextView mPlayerPosition;
    private TextView mLoadText;
    private TextView mCpu;
    private TextView mMemInfo;
    private TextView mVideoResolution;
    private TextView mVideoBitrate;
    private TextView mFrameRate;
    private TextView mVideoBufferTime;
    private TextView mAudioBufferTime;
    private TextView mServerIp;
    private TextView mSdkVersion;
    private TextView mDNSTime;
    private TextView mHttpConnectionTime;

    private Button mPlayerScaleVideo;

    private boolean mPlayerPanelShow = false;
    private boolean mPause = false;

    private long mStartTime = 0;
    private long mPauseStartTime = 0;
    private long mPausedTime = 0;

    private int mVideoWidth = 0;
    private int mVideoHeight = 0;

    private int mVideoScaleIndex = 0;
    boolean     useHwCodec = false;

    private String mDataSource;

    private IMediaPlayer.OnPreparedListener mOnPreparedListener = new IMediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(IMediaPlayer mp) {

            mVideoWidth = ksyMediaPlayer.getVideoWidth();
            mVideoHeight = ksyMediaPlayer.getVideoHeight();

            // Set Video Scaling Mode
            ksyMediaPlayer.setVideoScalingMode(KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);

            //start player
            ksyMediaPlayer.start();

            //set progress
            setVideoProgress(0);

            //start hardware info thread
            if (mQosThread != null)
                mQosThread.start();

            //update UI
            mCpu.setVisibility(View.VISIBLE);
            mMemInfo.setVisibility(View.VISIBLE);

            if(ksyMediaPlayer.getServerAddress() != null)
                mServerIp.setText("ServerIP: "+ ksyMediaPlayer.getServerAddress());

            //  get meta data
            Bundle bundle = ksyMediaPlayer.getMediaMeta();
            KSYMediaMeta meta = KSYMediaMeta.parse(bundle);
            if (meta != null)
            {
                if (meta.mHttpConnectTime > 0) {
                    double http_connection_time = Double.valueOf(meta.mHttpConnectTime);
                    mHttpConnectionTime.setText("HTTP Connection Time: " + (int)http_connection_time);
                }

                int dns_time = meta.mAnalyzeDnsTime;
                if (dns_time > 0) {
                    mDNSTime.setText("DNS time: " + dns_time);
                }
            }

            mSdkVersion.setText("SDK version: " + ksyMediaPlayer.getVersion());

            mVideoResolution.setText("Resolution:" + ksyMediaPlayer.getVideoWidth() + "x" + ksyMediaPlayer.getVideoHeight());
            mVideoResolution.setVisibility(View.VISIBLE);
            mFrameRate.setVisibility(View.VISIBLE);
            mVideoBitrate.setVisibility(View.VISIBLE);

            mStartTime = System.currentTimeMillis();

        }
    };

    private IMediaPlayer.OnBufferingUpdateListener mOnBufferingUpdateListener = new IMediaPlayer.OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(IMediaPlayer mp, int percent) {
            long duration = ksyMediaPlayer.getDuration();
            long progress = duration * percent/100;
            mPlayerSeekbar.setSecondaryProgress((int)progress);
        }
    };

    private IMediaPlayer.OnVideoSizeChangedListener mOnVideoSizeChangeListener = new IMediaPlayer.OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
            if(mVideoWidth > 0 && mVideoHeight > 0) {
                if(width != mVideoWidth || height != mVideoHeight) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();

                    if(ksyMediaPlayer != null)
                        ksyMediaPlayer.setVideoScalingMode(KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                }
            }
        }
    };

    private IMediaPlayer.OnSeekCompleteListener mOnSeekCompletedListener = new IMediaPlayer.OnSeekCompleteListener() {
        @Override
        public void onSeekComplete(IMediaPlayer mp) {
            Log.e(TAG, "onSeekComplete...............");
        }
    };

    private IMediaPlayer.OnCompletionListener mOnCompletionListener = new IMediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(IMediaPlayer mp) {
            Toast.makeText(mContext, "OnCompletionListener, play complete.", Toast.LENGTH_LONG).show();
            videoPlayEnd();
        }
    };

    private IMediaPlayer.OnErrorListener mOnErrorListener = new IMediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(IMediaPlayer mp, int what, int extra) {
            switch (what)
            {
                case KSYMediaPlayer.MEDIA_ERROR_UNKNOWN:
                    Log.e(TAG, "OnErrorListener, Error Unknown:" + what + ",extra:" + extra);
                    break;
                default:
                    Log.e(TAG, "OnErrorListener, Error:" + what + ",extra:" + extra);
            }

            videoPlayEnd();

            return false;
        }
    };

    public IMediaPlayer.OnInfoListener mOnInfoListener = new IMediaPlayer.OnInfoListener() {
        @Override
        public boolean onInfo(IMediaPlayer iMediaPlayer, int i, int i1) {
            switch (i) {
                case KSYMediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.d(TAG, "Buffering Start.");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.d(TAG, "Buffering End.");
                    break;
                case KSYMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                    Toast.makeText(mContext, "Audio Rendering Start", Toast.LENGTH_SHORT).show();
                    break;
                case KSYMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    Toast.makeText(mContext, "Video Rendering Start", Toast.LENGTH_SHORT).show();
                    break;
                case KSYMediaPlayer.MEDIA_INFO_SUGGEST_RELOAD:
                    // Player find a new stream(video or audio), and we could reload the video.
                    if(ksyMediaPlayer != null)
                        ksyMediaPlayer.reload(mDataSource, false);
                    break;
            }
            return false;
        }
    };

    private View.OnClickListener mVideoScaleButton = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int mode = mVideoScaleIndex % 2;
            mVideoScaleIndex++;

            if(ksyMediaPlayer != null) {
                if(mode == 1)
                    ksyMediaPlayer.setVideoScalingMode(KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                else
                    ksyMediaPlayer.setVideoScalingMode(KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this.getApplicationContext();
        useHwCodec = getIntent().getBooleanExtra("HWCodec",false);

        setContentView(R.layout.qianyi_player);

        mPlayerPanel = (LinearLayout) findViewById(R.id.player_panel);
        mPlayerStartBtn = (ImageView) findViewById(R.id.player_start);
        mPlayerSeekbar = (SeekBar) findViewById(R.id.player_seekbar);
        mPlayerPosition = (TextView) findViewById(R.id.player_time);
        mLoadText = (TextView) findViewById(R.id.loading_text);
        mCpu = (TextView) findViewById(R.id.player_cpu);
        mMemInfo = (TextView) findViewById(R.id.player_mem);
        mVideoResolution = (TextView) findViewById(R.id.player_re);
        mVideoBitrate = (TextView) findViewById(R.id.player_br);
        mFrameRate = (TextView) findViewById(R.id.player_fr);
        mVideoBufferTime = (TextView) findViewById(R.id.player_video_time);
        mAudioBufferTime = (TextView) findViewById(R.id.player_audio_time);
        mServerIp = (TextView) findViewById(R.id.player_ip);
        mSdkVersion = (TextView) findViewById(R.id.player_sdk_version);
        mDNSTime = (TextView) findViewById(R.id.player_dns_time);
        mHttpConnectionTime = (TextView) findViewById(R.id.player_http_connection_time);

        mPlayerScaleVideo = (Button) findViewById(R.id.player_scale);
        mPlayerScaleVideo.setOnClickListener(mVideoScaleButton);

        mPlayerStartBtn.setOnClickListener(mStartBtnListener);
        mPlayerSeekbar.setOnSeekBarChangeListener(mSeekBarListener);
        mPlayerSeekbar.setEnabled(true);

        mVideoSurfaceView = (SurfaceView) findViewById(R.id.player_surface);
        mSurfaceHolder = mVideoSurfaceView.getHolder();
        mSurfaceHolder.addCallback(mSurfaceCallback);
        mVideoSurfaceView.setOnTouchListener(mTouchListener);
        mVideoSurfaceView.setKeepScreenOn(true);
        mLoadText.setVisibility(View.VISIBLE);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mHandler = new UIHandler(this);
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        mQosThread = new QosThread(activityManager, mHandler);

        mDataSource = getIntent().getStringExtra("path");

        ksyMediaPlayer = new KSYMediaPlayer.Builder(mContext).build();
        ksyMediaPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        ksyMediaPlayer.setOnCompletionListener(mOnCompletionListener);
        ksyMediaPlayer.setOnPreparedListener(mOnPreparedListener);
        ksyMediaPlayer.setOnInfoListener(mOnInfoListener);
        ksyMediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangeListener);
        ksyMediaPlayer.setOnErrorListener(mOnErrorListener);
        ksyMediaPlayer.setOnSeekCompleteListener(mOnSeekCompletedListener);
        ksyMediaPlayer.setScreenOnWhilePlaying(true);
        ksyMediaPlayer.setBufferTimeMax(5);

        if (useHwCodec) {
            //硬解264&265
            ksyMediaPlayer.setCodecFlag(KSYMediaPlayer.KSY_USE_MEDIACODEC_ALL);
        }
        try {
            ksyMediaPlayer.setDataSource(mDataSource);
            ksyMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(ksyMediaPlayer != null)
        {
            ksyMediaPlayer.pause();
            mPause = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        if(ksyMediaPlayer != null)
        {
            ksyMediaPlayer.start();
            mPause = false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            videoPlayEnd();
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void scaleVideoView()
    {
        if(ksyMediaPlayer == null || ksyMediaPlayer.getVideoHeight() <= 0 || mVideoSurfaceView == null)
            return;

        WindowManager wm = this.getWindowManager();
        int sw = wm.getDefaultDisplay().getWidth();
        int sh = wm.getDefaultDisplay().getHeight();
        int videoWidth = mVideoWidth;
        int videoHeight = mVideoHeight;
        int visibleWidth = 0;
        int visibleHeight = 0;

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
        {
            visibleWidth = sw > sh ? sh : sw;
            visibleHeight = (int) Math.ceil(visibleWidth * videoHeight / videoWidth);
        }
        else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {

            if(videoHeight*sw > videoWidth*sh)
            {
                visibleHeight = sh;
                visibleWidth = (int) Math.ceil(videoWidth * visibleHeight / videoHeight);
            }
            else
            {
                visibleWidth = sw;
                visibleHeight = (int) Math.ceil(visibleWidth * videoHeight / videoWidth);
            }
        }

        LayoutParams lp = mVideoSurfaceView.getLayoutParams();
        lp.width = visibleWidth;
        lp.height = visibleHeight;
        mVideoSurfaceView.setLayoutParams(lp);

        mVideoSurfaceView.invalidate();
    }

    // Maybe we could support gesture detect
    private void dealTouchEvent(View view, MotionEvent event) {
        mPlayerPanelShow = !mPlayerPanelShow;

        if(mPlayerPanelShow) {
            mPlayerPanel.setVisibility(View.VISIBLE);

            Message msg = new Message();
            msg.what = HIDDEN_SEEKBAR;
            mHandler.sendMessageDelayed(msg, 3000);
        }else{
            mPlayerPanel.setVisibility(View.GONE);
            mHandler.removeMessages(HIDDEN_SEEKBAR);
        }
    }

    public int setVideoProgress(int currentProgress) {

        if(ksyMediaPlayer == null)
            return -1;

        long time = currentProgress > 0 ? currentProgress : ksyMediaPlayer.getCurrentPosition();
        long length = ksyMediaPlayer.getDuration();

        // Update all view elements
        mPlayerSeekbar.setMax((int)length);
        mPlayerSeekbar.setProgress((int)time);

        if(time >= 0)
        {
            String progress = Strings.millisToString(time) + "/" + Strings.millisToString(length);
            mPlayerPosition.setText(progress);
        }

        Message msg = new Message();
        msg.what = UPDATE_SEEKBAR;

        if(mHandler != null)
            mHandler.sendMessageDelayed(msg, 1000);
        return (int)time;
    }

    private void updateQosInfo(QosObject obj) {
        mCpu.setText("Cpu usage:"+obj.cpuUsage);
        mMemInfo.setText("Memory:" + obj.pss + " KB");

        if(ksyMediaPlayer != null)
        {
            long bits = ksyMediaPlayer.getDecodedDataSize() * 8 / (mPause ? mPauseStartTime - mPausedTime - mStartTime : System.currentTimeMillis() - mPausedTime - mStartTime);
            mVideoBitrate.setText("Bitrate: " + bits + " kb/s");
            mFrameRate.setText("VideoOutputFrameRate:" + ksyMediaPlayer.getVideoOutputFramesPerSecond());
            KSYQosInfo info = ksyMediaPlayer.getStreamQosInfo();

            mVideoBufferTime.setText("VideoBufferTime:"+info.videoBufferTimeLength+"(ms)");
            mAudioBufferTime.setText("AudioBufferTime:"+info.audioBufferTimeLength+"(ms)");
        }

    }

    private String md5(String string)
    {
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Huh, MD5 should be supported?", e);
        }

        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) hex.append("0");
            hex.append(Integer.toHexString(b & 0xFF));
        }

        return hex.toString();
    }

    private void videoPlayEnd() {
        if(ksyMediaPlayer != null)
        {
            ksyMediaPlayer.release();
            ksyMediaPlayer = null;
        }

        if(mQosThread != null) {
            mQosThread.stopThread();
            mQosThread = null;
        }

        mHandler = null;

        finish();
    }

    private View.OnClickListener mStartBtnListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mPause = !mPause;
            if(mPause) {
                mPlayerStartBtn.setBackgroundResource(R.drawable.qyvideo_pause_btn);
                ksyMediaPlayer.pause();
                mPauseStartTime = System.currentTimeMillis();
            }else {
                mPlayerStartBtn.setBackgroundResource(R.drawable.qyvideo_start_btn);
                ksyMediaPlayer.start();
                mPausedTime += System.currentTimeMillis() - mPauseStartTime;
                mPauseStartTime = 0;
            }
        }
    };

    private int mVideoProgress = 0;
    private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(fromUser) {
                mVideoProgress = progress;
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            ksyMediaPlayer.seekTo(mVideoProgress);
            setVideoProgress(mVideoProgress);
        }
    };

    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            dealTouchEvent(v, event);
            return false;
        }
    };

    private final SurfaceHolder.Callback mSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(ksyMediaPlayer != null && ksyMediaPlayer.isPlaying())
                ksyMediaPlayer.setVideoScalingMode(KSYMediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if(ksyMediaPlayer != null)
                ksyMediaPlayer.setDisplay(holder);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed");
            if(ksyMediaPlayer != null) {
                ksyMediaPlayer.setDisplay(null);
            }
        }
    };

    private class UIHandler extends Handler {

        VideoPlayerActivity mActivtiy;

        public UIHandler(VideoPlayerActivity activty) {
            mActivtiy = activty;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_SEEKBAR:
                    if(mActivtiy != null)
                        mActivtiy.setVideoProgress(0);
                    break;
                case HIDDEN_SEEKBAR:
                    if(mActivtiy != null)
                    {
                        mActivtiy.mPlayerPanelShow = false;
                        mActivtiy.mPlayerPanel.setVisibility(View.GONE);
                    }
                    break;
                case UPDATE_QOS:
                    if(mActivtiy != null && msg.obj instanceof QosObject) {
                        mActivtiy.updateQosInfo((QosObject)msg.obj);
                    }
                    break;
            }
        }
    }
}
