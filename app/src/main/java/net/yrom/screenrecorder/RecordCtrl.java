package net.yrom.screenrecorder;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecordCtrl implements IRecordCtrl {
    public static final String TAG = "RecordCtrl";
    private MediaCaptureService mediaCaptureService = null;
    private MediaCaptureServiceConnection serviceConnection = null;
    Context context;
    BroadcastReceiver receiver;
    boolean isInit = false;
    int notificationId;
    Notification notification;

    Intent captureIntent;
    VideoEncodeConfig mVideoEncodeConfig;
    AudioEncodeConfig mAudioEncodeConfig;
    File mFile;
    private final Object binderSync = new Object();

    public void init(Context context, BroadcastReceiver receiver, int notificationId, Notification notification) {
        Log.d(TAG, "init");
        if (isInit) {
            return;
        }
        if (context == null) {
            throw new IllegalStateException("context is null");
        }
        // 在这里注册接收器时指定权限
        if (receiver != null) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.xjmz.openxr.test.RECEIVER");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, intentFilter);
            }
        }
        this.context = context;
        this.receiver = receiver;
        this.notificationId = notificationId;
        this.notification = notification;

        isInit = true;
    }

    @Override
    public void destroy() {
        Log.d(TAG, "destroy");
        if (!isInit) {
            return;
        }
        stopService();
        unbindService();
        // 注销广播接收器
        if (receiver != null) {
            context.unregisterReceiver(receiver);
            receiver = null;
        }
    }

    /*
     *
     * 停止录制
     * */
    @Override
    public void stopRecord() {
        Log.d(TAG, "stopRecord");
        if (MediaCaptureService.isServiceRunning()) {
            mediaCaptureService.stopRecording();
            stopService();
        }
    }

    /*

     * 开始录制
     * */
    @Override
    public void startRecord(Intent captureIntent, VideoEncodeConfig videoEncodeConfig, AudioEncodeConfig audioEncodeConfig) {
        Log.d(TAG, "startRecord");
        if (!isInit) {
            throw new IllegalStateException("not init");
        }
        if (captureIntent == null) {
            Log.e(TAG, "startRecording: callingIntent is null");
            throw new IllegalStateException("callingIntent is null");
        }
        this.captureIntent = captureIntent;
        this.mVideoEncodeConfig = videoEncodeConfig;
        this.mAudioEncodeConfig = audioEncodeConfig;

        File dir = getSavingDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("failed to create saving directory");
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        mFile = new File(dir, "Screenshots-" + format.format(new Date())
                + "-" + mVideoEncodeConfig.width + "x" + mVideoEncodeConfig.height + ".mp4");
        Log.d("@@", "Create recorder with :" + mVideoEncodeConfig + " \n " + mAudioEncodeConfig + "\n " + mFile);
        if (MediaCaptureService.isRecordRunning()) {
            throw new IllegalStateException("isRecording");
        }
        if (!MediaCaptureService.isServiceRunning()) {
            startService(notificationId, notification);
        }
    }

    @Override
    public void screenshot() throws IllegalStateException {
        Log.d(TAG, "screenshot");
        if (!isInit) {
            throw new IllegalStateException("not init");
        }
        if (MediaCaptureService.isRecordRunning()) {
            Log.e(TAG, "screenshot: isRecording");
            throw new IllegalStateException("isRecording");
        }
    }

    @Override
    public String getRecordPath() {
        if (mFile == null) {
            return null;
        }
        return mFile.getAbsolutePath();
    }

    private class MediaCaptureServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connected");
            MediaCaptureService.MediaCaptureServiceBinder binder = (MediaCaptureService.MediaCaptureServiceBinder) service;
            mediaCaptureService = binder.getService();
            mediaCaptureService.startRecording(captureIntent, mVideoEncodeConfig, mAudioEncodeConfig, mFile);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaCaptureService = null;
            Log.e(TAG, "Service unexpectedly exited");
        }
    }

    private void startService(int notificationId, Notification notification) {
        Intent serviceIntent = new Intent(context, MediaCaptureService.class);
        serviceIntent.putExtra("notificationId", notificationId);
        serviceIntent.putExtra("notification", notification);
        serviceIntent.setAction(MediaCaptureService.START_ACTION);
        ContextCompat.startForegroundService(context, serviceIntent);
        serviceConnection = new MediaCaptureServiceConnection();
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopService() {
        if (MediaCaptureService.isServiceRunning()) {
            unbindService();
            Intent serviceIntent = new Intent(context, MediaCaptureService.class);
            serviceIntent.setAction(MediaCaptureService.SHUTDOWN_ACTION);
            context.stopService(serviceIntent);
        } else {
            Log.d(TAG, "Record is not running");
        }
    }

    private void unbindService() {
        if (serviceConnection == null) return;

        context.unbindService(serviceConnection);
        serviceConnection = null;
    }

    private File getSavingDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Screenshots");
    }
}
