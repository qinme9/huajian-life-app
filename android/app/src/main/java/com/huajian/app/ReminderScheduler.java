package com.huajian.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import java.util.Calendar;

public final class ReminderScheduler {
    public static final String PREFS = "huajian_reminders";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_TIME = "time";
    public static final String KEY_ALARM = "alarm";
    public static final String KEY_TITLE = "title";
    public static final String KEY_BODY = "body";
    public static final String KEY_BADGE = "badge";

    public static final String CHANNEL_NORMAL = "huajian_reminders_v2";
    public static final String CHANNEL_ALARM = "huajian_alarm_v2";
    public static final String ACTION_DAILY = "com.huajian.app.DAILY_REMINDER";
    public static final int ALARM_REQUEST = 1101;
    public static final int NOTIFICATION_ID = 2101;

    private ReminderScheduler() {}

    public static void createChannels(Context context) {
        ensureChannel(context, false);
        ensureChannel(context, true);
    }

    private static void ensureChannel(Context context, boolean alarm) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String id = alarm ? CHANNEL_ALARM : CHANNEL_NORMAL;
        NotificationChannel channel = new NotificationChannel(
                id,
                alarm ? "花笺闹铃提醒" : "花笺提醒",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(alarm ? "重要提醒，使用闹铃声音" : "待办、习惯和重要日期提醒");
        channel.enableVibration(true);
        channel.setShowBadge(true);
        if (alarm) {
            channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            );
        } else {
            channel.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            );
        }
        manager.createNotificationChannel(channel);
    }

    public static void setSchedule(Context context, String time, boolean alarm, String title, String body, int badgeCount) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_TIME, normalizeTime(time))
                .putBoolean(KEY_ALARM, alarm)
                .putString(KEY_TITLE, title == null ? "花笺提醒" : title)
                .putString(KEY_BODY, body == null ? "" : body)
                .putInt(KEY_BADGE, Math.max(0, badgeCount))
                .apply();
        schedule(context);
    }

    public static void cancelAndDisable(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_ENABLED, false).apply();
        cancel(context);
        clearNotification(context);
    }

    public static void clearNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    public static void schedule(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean enabled = sp.getBoolean(KEY_ENABLED, true);
        if (!enabled) {
            cancel(context);
            return;
        }
        String time = normalizeTime(sp.getString(KEY_TIME, "09:00"));
        Calendar next = nextTrigger(time);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_DAILY);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pendingIntent);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pendingIntent);
            }
        } catch (SecurityException e) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pendingIntent);
        }
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_DAILY);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        alarmManager.cancel(pendingIntent);
    }

    public static void notifyNow(Context context, String title, String body, boolean alarm, int badgeCount) {
        ensureChannel(context, alarm);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, alarm ? CHANNEL_ALARM : CHANNEL_NORMAL)
                : new Notification.Builder(context);
        builder.setContentTitle(title == null ? "花笺提醒" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);
        if (badgeCount > 0) {
            builder.setNumber(badgeCount);
            if (Build.VERSION.SDK_INT >= 26) builder.setBadgeIconType(Notification.BADGE_ICON_SMALL);
        }
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_HIGH);
            builder.setDefaults(Notification.DEFAULT_ALL);
            if (alarm) builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        }
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static String normalizeTime(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) return "09:00";
        String[] parts = time.split(":");
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return "09:00";
            return String.format("%02d:%02d", hour, minute);
        } catch (Exception e) {
            return "09:00";
        }
    }

    private static Calendar nextTrigger(String time) {
        String normalized = normalizeTime(time);
        String[] parts = normalized.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar;
    }
}
