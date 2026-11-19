package com.sc55g.wifiguard.core;

public final class SsidUtil {
    private SsidUtil() {}

    public static String normalize(String ssid) {
        if (ssid == null) return "";
        String value = ssid.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    public static String quote(String value) {
        String normalized = normalize(value).replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + normalized + "\"";
    }
}
