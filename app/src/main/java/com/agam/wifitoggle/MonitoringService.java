package com.agam.wifitoggle;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class MonitoringService extends Service {

    private static final String CHANNEL_ID = "WifiToggleMonitor";
    private WifiManager wifiManager;
    private SharedPreferences prefs;
    private String targetSSID = "", lastKnownSSID = "";
    private Handler handler = new Handler();

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d("MonitoringService", "Service starting...");

        // STEP 1: Notify ASAP (required for Android 8+)
        createNotificationChannel();
        startForeground(1, createStatusNotification("Monitoring started").build());
        Log.d("MonitoringService", "Foreground notification shown.");

        // STEP 2: Now continue safe setup
        try {
            wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            prefs = getSharedPreferences("WifiPrefs", MODE_PRIVATE);
            targetSSID = prefs.getString("targetSSID", "");
            Log.d("MonitoringService", "Target SSID: " + targetSSID);

            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();

            cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    handler.postDelayed(() -> verifyConnection(), 1500);
                }

                @Override
                public void onLost(Network network) {
                    handler.postDelayed(() -> verifyConnection(), 1500);
                }
            });

        } catch (Exception e) {
            Log.e("MonitoringService", "Init error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void verifyConnection() {
        String ssid = getCurrentSSID();
        if (ssid.equals(lastKnownSSID)) return;
        lastKnownSSID = ssid;

        if (ssid.equals(targetSSID)) {
            toggleMobileData(false);
            toggleLocation(false);
            showNotification("✅ Connected to " + ssid, "Turning OFF mobile data & location");
        } else {
            toggleMobileData(true);
            toggleLocation(true);
            showNotification("⚠️ Disconnected from " + targetSSID, "Turning ON mobile data & location");
        }
    }

    private String getCurrentSSID() {
        try {
            WifiInfo info = wifiManager.getConnectionInfo();
            return (info != null) ? info.getSSID().replace("\"", "") : "<unknown ssid>";
        } catch (Exception e) {
            e.printStackTrace();
            return "<error ssid>";
        }
    }

    private void toggleMobileData(boolean enable) {
        try {
            Settings.Global.putInt(getContentResolver(), "mobile_data", enable ? 1 : 0);
        } catch (Exception e) {
            Log.e("MonitoringService", "Mobile data toggle failed");
        }
    }

    private void toggleLocation(boolean enable) {
        try {
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.LOCATION_MODE,
                    enable ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : Settings.Secure.LOCATION_MODE_OFF);
        } catch (Exception e) {
            Log.e("MonitoringService", "Location toggle failed");
        }
    }

    private NotificationCompat.Builder createStatusNotification(String title) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText("Wi-Fi monitoring is active.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pi);
    }

    private void showNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Wi-Fi Toggle Monitor", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Monitors Wi-Fi and toggles data/location");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}