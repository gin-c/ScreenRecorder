package net.yrom.screenrecorder;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public interface IRecordCtrl {
    /**
     * 初始化
     *
     * @param context        上下文
     * @param receiver       广播接收器
     *                       ExtraName: error
     * @param notificationId 通知ID
     * @param notification   通知
     * @throws IllegalStateException
     */
    void init(Context context, BroadcastReceiver receiver, int notificationId, Notification notification) throws IllegalStateException;

    /**
     * 释放资源
     *
     * @throws IllegalStateException
     */
    void destroy() throws IllegalStateException;

    /**
     * 开始录制
     *
     * @param captureIntent 申请录制获取到的Intent
     * @throws IllegalStateException
     */
    void startRecord(Intent captureIntent, VideoEncodeConfig videoEncodeConfig, AudioEncodeConfig audioEncodeConfig) throws IllegalStateException;

    /**
     * 停止录制
     *
     * @throws IllegalStateException
     */
    void stopRecord() throws IllegalStateException;

    /**
     * 截图
     *
     * @throws IllegalStateException
     */
    void screenshot() throws IllegalStateException;

    /**
     * 获取录制文件路径
     *
     * @return 录制文件路径
     * @throws IllegalStateException
     */
    String getRecordPath() throws IllegalStateException;

}
