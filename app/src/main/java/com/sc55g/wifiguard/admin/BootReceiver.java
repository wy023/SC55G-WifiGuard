package com.sc55g.wifiguard.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.sc55g.wifiguard.core.SecureConfigStore;
import com.sc55g.wifiguard.service.WifiPolicyService;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        new SecureConfigStore(context).endTemporarySession();
        WifiPolicyService.start(context, "Boot/package event");
    }
}
