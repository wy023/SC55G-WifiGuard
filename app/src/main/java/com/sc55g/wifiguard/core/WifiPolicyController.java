package com.sc55g.wifiguard.core;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.sc55g.wifiguard.admin.WifiDeviceAdminReceiver;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("deprecation")
public final class WifiPolicyController {
    private static final String TAG = "WifiPolicyController";
    private final Context context;
    private final WifiManager wifi;
    private final ConnectivityManager connectivity;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;
    private final SecureConfigStore store;

    public WifiPolicyController(Context context) {
        this.context = context.getApplicationContext();
        this.wifi = this.context.getSystemService(WifiManager.class);
        this.connectivity = this.context.getSystemService(ConnectivityManager.class);
        this.dpm = this.context.getSystemService(DevicePolicyManager.class);
        this.admin = new ComponentName(this.context, WifiDeviceAdminReceiver.class);
        this.store = new SecureConfigStore(this.context);
    }

    public boolean isDeviceOwner() {
        return dpm != null && dpm.isDeviceOwnerApp(context.getPackageName());
    }

    public void grantManagedPermissions() {
        if (!isDeviceOwner()) return;
        String[] permissions = Build.VERSION.SDK_INT >= 33
                ? new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS }
                : new String[] { Manifest.permission.ACCESS_FINE_LOCATION };
        for (String permission : permissions) {
            try {
                dpm.setPermissionGrantState(admin, context.getPackageName(), permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            } catch (SecurityException e) {
                Log.w(TAG, "ROM refused managed permission: " + permission, e);
            }
        }
    }

    public void requestScan() {
        if (!hasWifiReadPermission()) return;
        try {
            wifi.startScan();
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot request Wi-Fi scan", e);
        }
    }

    public String currentSsid() {
        if (!hasWifiReadPermission()) return "";
        try {
            Network active = connectivity.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && caps.getTransportInfo() instanceof WifiInfo) {
                return SsidUtil.normalize(((WifiInfo) caps.getTransportInfo()).getSSID());
            }
            WifiInfo info = wifi.getConnectionInfo();
            return info == null ? "" : SsidUtil.normalize(info.getSSID());
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot read current SSID", e);
            return "";
        }
    }

    public boolean isTargetVisible(WifiPolicyConfig config) {
        if (!hasWifiReadPermission()) return false;
        try {
            List<ScanResult> results = wifi.getScanResults();
            for (ScanResult result : results) {
                String ssid = Build.VERSION.SDK_INT >= 33 && result.getWifiSsid() != null
                        ? result.getWifiSsid().toString() : result.SSID;
                if (SsidUtil.same(ssid, config.ssid)) return true;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot read scan results", e);
        }
        return false;
    }

    public EnforcementResult enforce() {
        WifiPolicyConfig config = store.load();
        if (!config.enabled) return new EnforcementResult(false, "策略未启用");
        if (!config.isValid()) return new EnforcementResult(false, "配置无效");
        if (!isDeviceOwner()) return new EnforcementResult(false, "App 不是 Device Owner，不能可靠执行策略");

        String current = currentSsid();
        boolean targetVisible = isTargetVisible(config);
        PolicyDecision.Action action = PolicyDecision.decide(
                current, config.ssid, store.isTemporarySessionActive(),
                store.wasTargetVisibleAtTemporaryStart(), targetVisible);

        switch (action) {
            case NONE:
                cleanupNonTargetNetworks(config.ssid, "");
                return new EnforcementResult(true, "已连接指定 Wi‑Fi：" + config.ssid);
            case KEEP_TEMPORARY:
                return new EnforcementResult(true, "临时连接中：" + displaySsid(current));
            case END_TEMPORARY_AND_CONNECT_TARGET:
                store.endTemporarySession();
                cleanupNonTargetNetworks(config.ssid, current);
                connectTarget(config);
                cleanupNonTargetNetworks(config.ssid, "");
                return new EnforcementResult(true, "指定 Wi‑Fi 已恢复，正在切回：" + config.ssid);
            case CLEAN_AND_CONNECT_TARGET:
            default:
                store.endTemporarySession();
                cleanupNonTargetNetworks(config.ssid, "");
                boolean initiated = connectTarget(config);
                return new EnforcementResult(initiated,
                        initiated ? "正在连接指定 Wi‑Fi：" + config.ssid : "尚未发现或无法连接：" + config.ssid);
        }
    }

    public void beginTemporarySession() {
        WifiPolicyConfig config = store.load();
        store.beginTemporarySession(isTargetVisible(config));
    }

    public EnforcementResult endTemporarySessionAndEnforce() {
        store.endTemporarySession();
        return enforce();
    }

    public int cleanupNonTargetNetworks(String targetSsid, String temporarilyProtectedSsid) {
        if (!isDeviceOwner()) return 0;
        int removed = 0;
        try {
            List<WifiConfiguration> configured = wifi.getConfiguredNetworks();
            if (configured == null) configured = Collections.emptyList();
            for (WifiConfiguration network : configured) {
                String ssid = SsidUtil.normalize(network.SSID);
                if (SsidUtil.same(ssid, targetSsid) || SsidUtil.same(ssid, temporarilyProtectedSsid)) continue;
                if (wifi.removeNetwork(network.networkId)) removed++;
            }
            if (removed > 0) wifi.saveConfiguration();
        } catch (SecurityException e) {
            Log.e(TAG, "Device-owner Wi-Fi cleanup rejected by ROM", e);
        }
        return removed;
    }

    private boolean connectTarget(WifiPolicyConfig config) {
        try {
            WifiConfiguration existing = findConfigured(config.ssid);
            int networkId;
            if (existing == null) {
                WifiConfiguration created = buildWifiConfiguration(config);
                networkId = wifi.addNetwork(created);
            } else {
                WifiConfiguration updated = buildWifiConfiguration(config);
                updated.networkId = existing.networkId;
                networkId = wifi.updateNetwork(updated);
                if (networkId < 0) networkId = existing.networkId;
            }
            if (networkId < 0) return false;
            wifi.saveConfiguration();
            return wifi.enableNetwork(networkId, true) && wifi.reconnect();
        } catch (SecurityException e) {
            Log.e(TAG, "Device-owner Wi-Fi connection rejected by ROM", e);
            return false;
        }
    }

    private WifiConfiguration findConfigured(String ssid) {
        List<WifiConfiguration> networks = wifi.getConfiguredNetworks();
        if (networks == null) return null;
        for (WifiConfiguration network : networks) {
            if (SsidUtil.same(network.SSID, ssid)) return network;
        }
        return null;
    }

    private static WifiConfiguration buildWifiConfiguration(WifiPolicyConfig config) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = SsidUtil.quote(config.ssid);
        wc.hiddenSSID = false;
        wc.status = WifiConfiguration.Status.ENABLED;
        switch (config.security) {
            case OPEN:
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
            case WPA3_SAE:
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                wc.preSharedKey = SsidUtil.quote(config.password);
                wc.requirePmf = true;
                break;
            case WPA2_PSK:
            default:
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wc.preSharedKey = SsidUtil.quote(config.password);
                break;
        }
        return wc;
    }

    private boolean hasWifiReadPermission() {
        boolean fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean nearby = Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        return fine && nearby;
    }

    private static String displaySsid(String ssid) { return ssid.isEmpty() ? "未知网络" : ssid; }

    public static final class EnforcementResult {
        public final boolean success;
        public final String message;
        public EnforcementResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
