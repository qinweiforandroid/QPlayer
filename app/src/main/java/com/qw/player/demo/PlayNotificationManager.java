package com.qw.player.demo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class PlayNotificationManager {
    private static final String CHANNEL_ID = "music_s";
    private static PlayNotificationManager mInstance;
    private final Context context;
    //    private RemoteViews remoteViews;
//    private RemoteViews remoteViewsSmall;
    private NotificationManager manager;

    //    private NotificationReceiver receiver;
//    private NotificationCompat.Builder notificationBuilder;
//
    private PlayNotificationManager(Context context) {
        this.context = context;
        manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
//        initNotification();
    }

    //
//    private Context getApplicationContext() {
//        return context.getApplicationContext();
//    }
//
//    public static PlayNotificationManager getInstance(Context context) {
//        if (mInstance == null) {
//            mInstance = new PlayNotificationManager(context);
//        }
//        return mInstance;
//    }
//
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "channel_name";
            String description = "channel_description";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(null, null);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            manager.createNotificationChannel(channel);
        }
    }
//
//    /**
//     * 设置通知
//     */
//    private void initNotification() {
//        int state = Playlist.getInstance(context).getState();
//        remoteViews = new RemoteViews(context.getPackageName(), R.layout.music_play_notification);
//        remoteViewsSmall = new RemoteViews(context.getPackageName(), R.layout.music_play_small_notification);
//        remoteViews.setTextViewText(R.id.mNMusicPlayTitleLabel, "");
//        remoteViewsSmall.setTextViewText(R.id.mNMusicPlayTitleLabel, "");
//        if (state == IPodPlayer.STATE_PLAYING || state == IPodPlayer.STATE_CONNECT) {
//            remoteViews.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_pause);
//            remoteViewsSmall.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_pause);
//        } else {
//            remoteViews.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_play);
//            remoteViewsSmall.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_play);
//        }
//        Intent intent = new Intent(context, FragmentContentActivity.class);
//        FragmentClazz clazz = new FragmentClazz(SpeakFragment.class, "");
//        intent.putExtra(Constants.KEY_FRAGMENT_CLAZZ_ENTITY, clazz);
//        // 点击跳转到主界面
//        PendingIntent intentGo = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
//        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayIconImg, intentGo);
//        remoteViewsSmall.setOnClickPendingIntent(R.id.mNMusicPlayIconImg, intentGo);
//        //关闭通知栏
//        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayCloseImg, getBroadcastIntent(NotificationReceiver.ACTION_CLOSE, 2));
//        remoteViewsSmall.setOnClickPendingIntent(R.id.mNMusicPlayCloseImg, getBroadcastIntent(NotificationReceiver.ACTION_CLOSE, 2));
//        //设置上一曲
//        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayPreImg, getBroadcastIntent(NotificationReceiver.ACTION_PRE, 3));
//        remoteViewsSmall.setOnClickPendingIntent(R.id.mNMusicPlayPreImg, getBroadcastIntent(NotificationReceiver.ACTION_PRE, 3));
//        //播放/暂停
//        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayPlayImg, getBroadcastIntent(NotificationReceiver.ACTION_PLAY, 4));
//        remoteViewsSmall.setOnClickPendingIntent(R.id.mNMusicPlayPlayImg, getBroadcastIntent(NotificationReceiver.ACTION_PLAY, 4));
//        //下一曲
//        remoteViews.setOnClickPendingIntent(R.id.mNMusicPlayNextImg, getBroadcastIntent(NotificationReceiver.ACTION_NEXT, 5));
//        remoteViewsSmall.setOnClickPendingIntent(R.id.mNMusicPlayNextImg, getBroadcastIntent(NotificationReceiver.ACTION_NEXT, 5));
//        notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID);
//        notificationBuilder.setContent(remoteViewsSmall);
//        notificationBuilder.setCustomBigContentView(remoteViews);
//        notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
////        notificationBuilder.setSound(null);
////        notificationBuilder.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE);
////        notificationBuilder.setVibrate(new long[]{0});
//        notificationBuilder.setSmallIcon(R.drawable.logo);
//        notificationBuilder.setOngoing(true);
//    }
//
//    public void registerListener() {
//        receiver = new NotificationReceiver();
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(NotificationReceiver.ACTION_PLAY);
//        filter.addAction(NotificationReceiver.ACTION_PRE);
//        filter.addAction(NotificationReceiver.ACTION_NEXT);
//        filter.addAction(NotificationReceiver.ACTION_CLOSE);
//        context.registerReceiver(receiver, filter);
//    }
//
//    public void unRegisterListener() {
//        context.unregisterReceiver(receiver);
//        manager = null;
//        receiver = null;
//        remoteViews = null;
//        mInstance = null;
//    }
//
//    public void onPlayStateChanged(String tag, int position, int state) {
//        switch (state) {
//            case IPodPlayer.STATE_CONNECT:
//                break;
//            case IPodPlayer.STATE_PLAYING:
//                remoteViews.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_pause);
//                remoteViews.setTextViewText(R.id.mNMusicPlayTitleLabel, Playlist.getInstance(getApplicationContext()).getPlayPod().getTitle());
//                remoteViewsSmall.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_pause);
//                remoteViewsSmall.setTextViewText(R.id.mNMusicPlayTitleLabel, Playlist.getInstance(getApplicationContext()).getPlayPod().getTitle());
//                manager.notify(100, notificationBuilder.build());
//                break;
//            case IPodPlayer.STATE_PAUSED:
//            case IPodPlayer.STATE_STOPPED:
//            case IPodPlayer.STATE_ERROR:
//                remoteViews.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_play);
//                remoteViewsSmall.setImageViewResource(R.id.mNMusicPlayPlayImg, R.drawable.play_btn_play);
//                manager.notify(100, notificationBuilder.build());
//                break;
//            default:
//                break;
//        }
//    }
//
//    class NotificationReceiver extends BroadcastReceiver {
//        public static final String ACTION_PLAY = "android.intent.action.music.play";
//        public static final String ACTION_PRE = "android.intent.action.music.pre";
//        public static final String ACTION_NEXT = "android.intent.action.music.next";
//        public static final String ACTION_CLOSE = "android.intent.action.music.close";
//
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            if (intent == null) {
//                return;
//            }
//            switch (intent.getAction()) {
//                case ACTION_PLAY:
//                    switch (Playlist.getInstance(getApplicationContext()).getState()) {
//                        case IPodPlayer.STATE_IDLE:
//                            Playlist.getInstance(getApplicationContext()).play();
//                            break;
//                        case IPodPlayer.STATE_PLAYING:
//                            Playlist.getInstance(getApplicationContext()).pause();
//                            break;
//                        case IPodPlayer.STATE_PAUSED:
//                            Playlist.getInstance(getApplicationContext()).resume();
//                            break;
//                        default:
//                            break;
//                    }
//                    break;
//                case ACTION_PRE:
//                    Playlist.getInstance(getApplicationContext()).backward();
//                    break;
//                case ACTION_NEXT:
//                    Playlist.getInstance(getApplicationContext()).forward();
//                    break;
//                case ACTION_CLOSE:
//                    manager.cancel(100);
//                    break;
//                default:
//                    break;
//            }
//        }
//    }
//
//    private PendingIntent getBroadcastIntent(String action, int requestCode) {
//        Intent close = new Intent();
//        close.setAction(action);
//        return PendingIntent.getBroadcast(context, requestCode, close, PendingIntent.FLAG_UPDATE_CURRENT);
//    }
}