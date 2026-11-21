package com.enddream.dnc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ActivationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        long time = intent.getLongExtra("time", System.currentTimeMillis());
        SharedPreferences sp = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        sp.edit().putLong(MainActivity.KEY_LAST_LOADED, time).commit();
    }
}
