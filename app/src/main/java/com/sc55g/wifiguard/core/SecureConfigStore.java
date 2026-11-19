package com.sc55g.wifiguard.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureConfigStore {
    private static final String PREFS = "wifi_guard";
    private static final String KEY_ALIAS = "sc55g_wifi_guard_key_v1";
    private static final String K_SSID = "ssid";
    private static final String K_SECRET = "secret";
    private static final String K_SECURITY = "security";
    private static final String K_ENABLED = "enabled";
    private static final String K_PIN_HASH = "pin_hash";
    private static final String K_PIN_SALT = "pin_salt";
    private static final String K_TEMP_UNTIL = "temp_until";
    private static final String K_TEMP_TARGET_VISIBLE = "temp_target_visible";

    private final SharedPreferences prefs;

    public SecureConfigStore(Context context) {
        Context storage = context.createDeviceProtectedStorageContext();
        prefs = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public WifiPolicyConfig load() {
        String ssid = prefs.getString(K_SSID, "");
        String password = decrypt(prefs.getString(K_SECRET, ""));
        String securityName = prefs.getString(K_SECURITY, WifiPolicyConfig.Security.WPA2_PSK.name());
        WifiPolicyConfig.Security security;
        try { security = WifiPolicyConfig.Security.valueOf(securityName); }
        catch (IllegalArgumentException e) { security = WifiPolicyConfig.Security.WPA2_PSK; }
        return new WifiPolicyConfig(ssid, password, security, prefs.getBoolean(K_ENABLED, false));
    }

    public void save(WifiPolicyConfig config) {
        prefs.edit()
                .putString(K_SSID, config.ssid)
                .putString(K_SECRET, encrypt(config.password))
                .putString(K_SECURITY, config.security.name())
                .putBoolean(K_ENABLED, config.enabled)
                .apply();
    }

    public boolean hasPin() { return prefs.contains(K_PIN_HASH); }

    public void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        prefs.edit()
                .putString(K_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(K_PIN_HASH, Base64.encodeToString(hashPin(pin, salt), Base64.NO_WRAP))
                .apply();
    }

    public boolean verifyPin(String pin) {
        try {
            byte[] salt = Base64.decode(prefs.getString(K_PIN_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(prefs.getString(K_PIN_HASH, ""), Base64.NO_WRAP);
            return MessageDigest.isEqual(expected, hashPin(pin, salt));
        } catch (Exception e) { return false; }
    }

    public void beginTemporarySession(boolean targetVisible) {
        long until = System.currentTimeMillis() + 4L * 60L * 60L * 1000L;
        prefs.edit().putLong(K_TEMP_UNTIL, until).putBoolean(K_TEMP_TARGET_VISIBLE, targetVisible).apply();
    }

    public boolean isTemporarySessionActive() {
        return prefs.getLong(K_TEMP_UNTIL, 0L) > System.currentTimeMillis();
    }

    public boolean wasTargetVisibleAtTemporaryStart() {
        return prefs.getBoolean(K_TEMP_TARGET_VISIBLE, false);
    }

    public void endTemporarySession() {
        prefs.edit().remove(K_TEMP_UNTIL).remove(K_TEMP_TARGET_VISIBLE).apply();
    }

    private static byte[] hashPin(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] value = (pin == null ? "" : pin).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 120_000; i++) {
                digest.update(value);
                value = digest.digest();
                digest.reset();
                digest.update(salt);
            }
            return value;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[cipher.getIV().length + encrypted.length];
            System.arraycopy(cipher.getIV(), 0, joined, 0, cipher.getIV().length);
            System.arraycopy(encrypted, 0, joined, cipher.getIV().length, encrypted.length);
            return Base64.encodeToString(joined, Base64.NO_WRAP);
        } catch (Exception e) { throw new IllegalStateException("Unable to encrypt Wi-Fi password", e); }
    }

    private String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            byte[] joined = Base64.decode(encoded, Base64.NO_WRAP);
            byte[] iv = Arrays.copyOfRange(joined, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(joined, 12, joined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
