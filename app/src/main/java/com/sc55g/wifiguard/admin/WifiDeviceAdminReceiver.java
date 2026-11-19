package com.sc55g.wifiguard.admin;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import com.sc55g.wifiguard.service.WifiPolicyService;

public final class WifiDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        WifiPolicyService.start(context, "Device admin enabled");
    }
}
