package net.yrom.screenrecorder;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import java.io.File;


public class MediaCaptureService extends Service {
    public static final String TAG = "MediaCaptureService";
    public static final String SHUTDOWN_ACTION = "com.xjmz.openxr.ctrl.record.MediaCaptureService.SHUTDOWN";
    public static final String START_ACTION = "com.xjmz.openxr.ctrl.record.MediaCaptureService.START";

    private MediaProjectionManager mMediaProjectionManager;

    private ScreenRecorder mRecorder;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;

    private static boolean isRunning = false;
    private static boolean isRecording = false;

    private final IBinder iBinder = new MediaCaptureServiceBinder();
    private Intent broadcastIntent = new Intent("com.xjmz.openxr.test.RECEIVER");


    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        Log.d(TAG, "Service created");
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        if (intent != null) {
            Log.d(TAG, "onStartCommand: " + intent.getAction());
            if (START_ACTION.equals(intent.getAction())) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    int notificationId = intent.getIntExtra("notificationId", 0);
                    Notification notification = intent.getParcelableExtra("notification");
                    startForeground(notificationId, notification);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                }
            } else if (SHUTDOWN_ACTION.equals(intent.getAction())) {
                stopSelf();
            }
        }
        // 系统不重启
        return START_NOT_STICKY;
    }

    public void startRecording(Intent intent, VideoEncodeConfig video, AudioEncodeConfig audio, File file) {
        if (intent == null) {
            Log.e(TAG, "startRecording: intent is null");
            broadcastIntent.putExtra("error", "startRecording: intent is null");
            sendBroadcast(broadcastIntent);
            return;
        }
        if (mMediaProjectionManager == null) {
            Log.e(TAG, "startRecording: mMediaProjectionManager is null");
            broadcastIntent.putExtra("error", "startRecording: mMediaProjectionManager is null");
            sendBroadcast(broadcastIntent);
            return;
        }
        mMediaProjection = mMediaProjectionManager.getMediaProjection(-1, intent);
        if (mMediaProjection == null) {
            Log.e(TAG, "startRecording: mediaProjection is null");
            broadcastIntent.putExtra("error", "startRecording: mediaProjection is null");
            sendBroadcast(broadcastIntent);
            return;
        }
        mRecorder = newRecorder(mMediaProjection, video, audio, file);
        mRecorder.start();
    }

    private ScreenRecorder newRecorder(MediaProjection mediaProjection, VideoEncodeConfig video,
                                       AudioEncodeConfig audio, File output) {
        final VirtualDisplay display = getOrCreateVirtualDisplay(mediaProjection, video);
        final AudioPlaybackCaptureConfiguration audioPlaybackCaptureConfiguration = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).addMatchingUsage(AudioAttributes.USAGE_GAME).build();
        ScreenRecorder r = new ScreenRecorder(video, audio, display, audioPlaybackCaptureConfiguration, output.getAbsolutePath());
        r.setCallback(new ScreenRecorder.Callback() {
            long startTime = 0;

            @Override
            public void onStop(Throwable error) {
                if (error != null) {
                    broadcastIntent.putExtra("error", "Recorder error ! See logcat for more details");
                    error.printStackTrace();
                    output.delete();
                } else {
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                            .addCategory(Intent.CATEGORY_DEFAULT)
                            .setData(Uri.fromFile(output));
                    sendBroadcast(intent);
                }
            }

            @Override
            public void onStart() {
                broadcastIntent.putExtra("start", "Recorder started");
            }

            @Override
            public void onRecording(long presentationTimeUs) {
                if (startTime <= 0) {
                    startTime = presentationTimeUs;
                }
                long time = (presentationTimeUs - startTime) / 1000;
                broadcastIntent.putExtra("time", time);
            }
        });
        return r;
    }

    private MediaProjection.Callback mProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            if (mRecorder != null) {
                stopSelf();
            }
        }
    };

    public static boolean isServiceRunning() {
        return isRunning;
    }

    public static boolean isRecordRunning() {
        return isRecording;
    }

    public class MediaCaptureServiceBinder extends Binder {
        MediaCaptureService getService() {
            return MediaCaptureService.this;
        }
    }

    public void stopRecording() {
        isRecording = false;
        if (null != mRecorder) {
            mRecorder.quit();
            mRecorder = null;
        }
    }
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        stopRecording();
        if (mVirtualDisplay != null) {
            mVirtualDisplay.setSurface(null);
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaProjection != null) {
            mMediaProjection.unregisterCallback(mProjectionCallback);
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        isRunning = false;
        Log.d(TAG, "Service destroyed");
    }

    private VirtualDisplay getOrCreateVirtualDisplay(MediaProjection mediaProjection, VideoEncodeConfig config) {
        if (mVirtualDisplay == null) {
            mVirtualDisplay = mediaProjection.createVirtualDisplay("ScreenRecorder-display0",
                    config.width, config.height, 1 /*dpi*/,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null /*surface*/, null, null);
        } else {
            // resize if size not matched
            Point size = new Point();
            mVirtualDisplay.getDisplay().getSize(size);
            if (size.x != config.width || size.y != config.height) {
                mVirtualDisplay.resize(config.width, config.height, 1);
            }
        }
        return mVirtualDisplay;
    }

}
