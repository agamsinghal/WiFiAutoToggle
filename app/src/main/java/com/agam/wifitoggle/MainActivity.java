package com.agam.wifitoggle;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {

    private WifiManager wifiManager;
    private String targetSSID = "";
    private TextView ssidTextView, savedSSIDView, monitorStatusView;
    private Button monitorButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 100, 50, 50);

        ssidTextView = new TextView(this);
        ssidTextView.setTextSize(20f);
        ssidTextView.setPadding(0, 0, 0, 40);

        savedSSIDView = new TextView(this);
        savedSSIDView.setTextSize(16f);
        savedSSIDView.setPadding(0, 0, 0, 40);

        monitorStatusView = new TextView(this);
        monitorStatusView.setTextSize(16f);
        monitorStatusView.setPadding(0, 30, 0, 30);

        monitorButton = new Button(this);
        monitorButton.setText("Start Monitoring This Wi-Fi");
        monitorButton.setTextSize(16f);
        monitorButton.setBackgroundColor(Color.parseColor("#6200EE"));
        monitorButton.setTextColor(Color.WHITE);

        layout.addView(ssidTextView);
        layout.addView(monitorButton);
        layout.addView(savedSSIDView);
        layout.addView(monitorStatusView);

        setContentView(layout);

        // Permissions
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
        }, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2);
        }

        // Initialize
        prefs = getSharedPreferences("WifiPrefs", MODE_PRIVATE);
        wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        String currentSSID = getCurrentSSID();
        targetSSID = prefs.getString("targetSSID", "");
        ssidTextView.setText("Connected to: " + currentSSID);
        savedSSIDView.setText(!targetSSID.isEmpty() ? "Saved SSID: " + targetSSID : "No SSID saved.");

        monitorButton.setOnClickListener(v -> {
            String ssid = getCurrentSSID();
            if (ssid.equals("<unknown ssid>")) {
                Toast.makeText(this, "Please turn ON location for SSID detection", Toast.LENGTH_LONG).show();
                return;
            }

            // Save and show
            targetSSID = ssid;
            prefs.edit().putString("targetSSID", targetSSID).apply();
            savedSSIDView.setText("Saved SSID: " + targetSSID);
            monitorStatusView.setText("✅ Monitoring started for: " + targetSSID);

            // Start monitoring service
            Intent intent = new Intent(MainActivity.this, MonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        });
    }

    private String getCurrentSSID() {
        WifiInfo info = wifiManager.getConnectionInfo();
        return (info != null) ? info.getSSID().replace("\"", "") : "<unknown ssid>";
    }
}