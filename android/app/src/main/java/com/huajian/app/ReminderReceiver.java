package com.huajian.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.schedule(context);
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(ReminderScheduler.KEY_ENABLED, true)) return;
        String title = sp.getString(ReminderScheduler.KEY_TITLE, "花笺提醒");
        String body = sp.getString(ReminderScheduler.KEY_BODY, "打开花笺看看今天的安排吧");
        boolean alarm = sp.getBoolean(ReminderScheduler.KEY_ALARM, false);
        int badge = sp.getInt(ReminderScheduler.KEY_BADGE, 0);
        ReminderScheduler.notifyNow(context, title, body, alarm, badge);
        ReminderScheduler.schedule(context);
    }
}
