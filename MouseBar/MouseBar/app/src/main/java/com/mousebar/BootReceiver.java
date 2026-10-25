package com.mousebar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        boolean auto = context.getSharedPreferences("mousebar", Context.MODE_PRIVATE)
                .getBoolean("autostart", true);
        if (auto) {
            context.startService(new Intent(context, BarService.class));
        }
    }
}
