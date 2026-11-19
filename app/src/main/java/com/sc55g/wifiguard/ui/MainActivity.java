package com.sc55g.wifiguard.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.sc55g.wifiguard.R;
import com.sc55g.wifiguard.core.SecureConfigStore;
import com.sc55g.wifiguard.core.WifiPolicyConfig;
import com.sc55g.wifiguard.core.WifiPolicyController;
import com.sc55g.wifiguard.service.WifiPolicyService;

public final class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 41;
    private SecureConfigStore store;
    private WifiPolicyController controller;
    private EditText ssidInput;
    private EditText passwordInput;
    private EditText pinInput;
    private Spinner securitySpinner;
    private Switch enabledSwitch;
    private TextView deviceOwnerStatus;
    private TextView runtimeStatus;
    private boolean unlocked;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            runtimeStatus.setText(intent.getStringExtra("message"));
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        store = new SecureConfigStore(this);
        controller = new WifiPolicyController(this);
        bindViews();
        loadConfig();
        requestRuntimePermissions();
        if (store.hasPin()) promptForPin(); else setUnlocked(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, new IntentFilter("com.sc55g.wifiguard.STATUS"), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, new IntentFilter("com.sc55g.wifiguard.STATUS"));
        }
        refreshOwnerStatus();
        if (controller.isDeviceOwner()) {
            controller.grantManagedPermissions();
            WifiPolicyService.start(this, "Activity resumed");
        }
    }

    @Override protected void onPause() {
        unregisterReceiver(statusReceiver);
        super.onPause();
    }

    private void bindViews() {
        ssidInput = findViewById(R.id.ssidInput);
        passwordInput = findViewById(R.id.passwordInput);
        pinInput = findViewById(R.id.pinInput);
        securitySpinner = findViewById(R.id.securitySpinner);
        enabledSwitch = findViewById(R.id.enabledSwitch);
        deviceOwnerStatus = findViewById(R.id.deviceOwnerStatus);
        runtimeStatus = findViewById(R.id.runtimeStatus);

        String[] choices = {"WPA2-PSK", "WPA3-SAE", "开放网络"};
        securitySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));

        findViewById(R.id.saveButton).setOnClickListener(v -> saveAndApply());
        findViewById(R.id.applyNowButton).setOnClickListener(v -> {
            WifiPolicyService.start(this, "Manual enforcement");
            show(controller.enforce().message);
        });
        findViewById(R.id.tempWifiButton).setOnClickListener(v -> beginTemporaryWifi());
        findViewById(R.id.endTempButton).setOnClickListener(v -> {
            WifiPolicyController.EnforcementResult result = controller.endTemporarySessionAndEnforce();
            WifiPolicyService.start(this, "Temporary session ended");
            show(result.message);
        });
    }

    private void loadConfig() {
        WifiPolicyConfig config = store.load();
        ssidInput.setText(config.ssid);
        passwordInput.setText(config.password);
        securitySpinner.setSelection(config.security == WifiPolicyConfig.Security.WPA3_SAE ? 1
                : config.security == WifiPolicyConfig.Security.OPEN ? 2 : 0);
        enabledSwitch.setChecked(config.enabled);
    }

    private void saveAndApply() {
        if (!unlocked) { promptForPin(); return; }
        WifiPolicyConfig.Security security = securitySpinner.getSelectedItemPosition() == 1
                ? WifiPolicyConfig.Security.WPA3_SAE
                : securitySpinner.getSelectedItemPosition() == 2
                ? WifiPolicyConfig.Security.OPEN : WifiPolicyConfig.Security.WPA2_PSK;
        WifiPolicyConfig config = new WifiPolicyConfig(
                ssidInput.getText().toString(), passwordInput.getText().toString(),
                security, enabledSwitch.isChecked());
        if (!config.isValid()) {
            show("请检查：SSID 1–32 字符；WPA 密码 8–63 字符；开放网络密码必须留空。");
            return;
        }
        String pin = pinInput.getText().toString();
        if (!store.hasPin() && pin.length() < 6) {
            show("首次设置必须填写至少 6 位管理员 PIN。");
            return;
        }
        if (!pin.isEmpty()) store.setPin(pin);
        store.save(config);
        pinInput.setText("");
        WifiPolicyService.start(this, "Configuration saved");
        show(controller.enforce().message);
    }

    private void beginTemporaryWifi() {
        if (!store.load().enabled) { show("请先保存并启用策略。"); return; }
        controller.beginTemporarySession();
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK));
        }
        show("临时会话已开启；请选择其他 Wi‑Fi。会话最长 4 小时。");
    }

    private void promptForPin() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("管理员 PIN");
        new AlertDialog.Builder(this)
                .setTitle("解锁管理界面")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("解锁", (dialog, which) -> {
                    if (store.verifyPin(input.getText().toString())) setUnlocked(true);
                    else { show("PIN 不正确"); setUnlocked(false); }
                })
                .setNegativeButton("仅查看", (dialog, which) -> setUnlocked(false))
                .show();
    }

    private void setUnlocked(boolean value) {
        unlocked = value;
        ssidInput.setEnabled(value);
        passwordInput.setEnabled(value);
        pinInput.setEnabled(value);
        securitySpinner.setEnabled(value);
        enabledSwitch.setEnabled(value);
        findViewById(R.id.saveButton).setEnabled(value);
    }

    private void refreshOwnerStatus() {
        boolean owner = controller.isDeviceOwner();
        deviceOwnerStatus.setText(owner
                ? "✓ Device Owner 已激活，可执行 Wi‑Fi 策略"
                : "⚠ 尚未设为 Device Owner。请按部署说明使用 ADB 配置。当前仅可查看/保存，不能可靠管控 Wi‑Fi。" );
        deviceOwnerStatus.setTextColor(Color.parseColor(owner ? "#067647" : "#B54708"));
    }

    private void requestRuntimePermissions() {
        java.util.ArrayList<String> needed = new java.util.ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    private void show(String message) {
        runtimeStatus.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
