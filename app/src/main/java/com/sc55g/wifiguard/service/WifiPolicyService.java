package com.sc55g.wifiguard.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.sc55g.wifiguard.R;
import com.sc55g.wifiguard.core.WifiPolicyController;
import com.sc55g.wifiguard.ui.MainActivity;

public final class WifiPolicyService extends Service {
    public static final String ACTION_ENFORCE = "com.sc55g.wifiguard.ENFORCE";
    private static final String CHANNEL_ID = "wifi_guard_status";
    private static final int NOTIFICATION_ID = 5501;
    private static final long CHECK_INTERVAL_MS = 30_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WifiPolicyController controller;
    private ConnectivityManager connectivity;
    private ConnectivityManager.NetworkCallback callback;

    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            enforceAndPublish();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    public static void start(Context context, String reason) {
        Intent intent = new Intent(context, WifiPolicyService.class)
                .setAction(ACTION_ENFORCE)
                .putExtra("reason", reason);
        context.startForegroundService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        controller = new WifiPolicyController(this);
        connectivity = getSystemService(ConnectivityManager.class);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("服务正在启动"));
        registerNetworkCallback();
        handler.post(periodic);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.post(this::enforceAndPublish);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (callback != null) connectivity.unregisterNetworkCallback(callback);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void registerNetworkCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { handler.postDelayed(WifiPolicyService.this::enforceAndPublish, 1500); }
            @Override public void onLost(Network network) { handler.postDelayed(WifiPolicyService.this::enforceAndPublish, 1000); }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                handler.postDelayed(WifiPolicyService.this::enforceAndPublish, 1000);
            }
        };
        connectivity.registerNetworkCallback(request, callback);
    }

    private void enforceAndPublish() {
        controller.grantManagedPermissions();
        controller.requestScan();
        WifiPolicyController.EnforcementResult result = controller.enforce();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(result.message));
        sendBroadcast(new Intent("com.sc55g.wifiguard.STATUS")
                .setPackage(getPackageName())
                .putExtra("success", result.success)
                .putExtra("message", result.message));
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_wifi_guard)
                .setContentTitle("SC55G Wi‑Fi 守卫")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Wi‑Fi 策略状态", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示指定 Wi‑Fi 策略的运行状态");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
