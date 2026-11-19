package com.sc55g.wifiguard.core;

public final class WifiPolicyConfig {
    public enum Security { OPEN, WPA2_PSK, WPA3_SAE }

    public final String ssid;
    public final String password;
    public final Security security;
    public final boolean enabled;

    public WifiPolicyConfig(String ssid, String password, Security security, boolean enabled) {
        this.ssid = ssid == null ? "" : ssid.trim();
        this.password = password == null ? "" : password;
        this.security = security == null ? Security.WPA2_PSK : security;
        this.enabled = enabled;
    }

    public boolean isValid() {
        if (ssid.isEmpty() || ssid.length() > 32) return false;
        if (security == Security.OPEN) return password.isEmpty();
        return password.length() >= 8 && password.length() <= 63;
    }
}
