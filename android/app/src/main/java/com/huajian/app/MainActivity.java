package com.huajian.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String CHANNEL_ID = "huajian_reminders";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestRuntimePermissions();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 102);
        }
    }

    private void createNotificationChannel() {
        ReminderScheduler.createChannels(this);
    }

    private Uri saveToDownloads(String filename, String content, String mime) throws Exception {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("无法创建下载文件");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("无法写入下载文件");
                os.write(data);
            }
            return uri;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建下载目录");
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
            }
            return Uri.fromFile(file);
        }
    }

    private String pendingNotificationTitle;
    private String pendingNotificationBody;
    private boolean pendingNotificationAlarm = false;
    private int pendingNotificationBadge = 0;

    private void showNotification(String title, String body, boolean alarm, int badgeCount) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotificationTitle = title;
            pendingNotificationBody = body;
            pendingNotificationAlarm = alarm;
            pendingNotificationBadge = badgeCount;
            requestRuntimePermissions();
            return;
        }
        ReminderScheduler.notifyNow(this, title, body, alarm, badgeCount);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingNotificationTitle != null) {
                String title = pendingNotificationTitle;
                String body = pendingNotificationBody;
                boolean alarm = pendingNotificationAlarm;
                int badge = pendingNotificationBadge;
                pendingNotificationTitle = null;
                pendingNotificationBody = null;
                showNotification(title, body == null ? "" : body, alarm, badge);
            } else if (!granted) {
                pendingNotificationTitle = null;
                pendingNotificationBody = null;
                Toast.makeText(this, "通知权限未开启，可在系统设置中开启", Toast.LENGTH_LONG).show();
            }
        }
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void saveFile(String filename, String content) {
            try {
                saveToDownloads(filename, content, guessMime(filename));
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "已保存到下载目录：" + filename, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public void shareFile(String filename, String content, String mime) {
            try {
                Uri uri = saveToDownloads(filename, content, mime);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "分享 " + filename));
            } catch (Exception e) {
                saveFile(filename, content);
            }
        }

        @JavascriptInterface
        public void notify(String title, String body) {
            runOnUiThread(() -> showNotification(title == null ? "花笺提醒" : title, body == null ? "" : body, false, 0));
        }

        @JavascriptInterface
        public void notifyWithOptions(String title, String body, boolean alarm, int badgeCount) {
            runOnUiThread(() -> showNotification(title == null ? "花笺提醒" : title, body == null ? "" : body, alarm, Math.max(0, badgeCount)));
        }

        @JavascriptInterface
        public boolean hasNotificationPermission() {
            return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void scheduleDailyReminder(String time, boolean alarm, String title, String body, int badgeCount) {
            runOnUiThread(() -> ReminderScheduler.setSchedule(MainActivity.this, time, alarm, title, body, Math.max(0, badgeCount)));
        }

        @JavascriptInterface
        public void cancelDailyReminder() {
            runOnUiThread(() -> ReminderScheduler.cancelAndDisable(MainActivity.this));
        }

        @JavascriptInterface
        public void requestNotification() {
            runOnUiThread(MainActivity.this::requestRuntimePermissions);
        }
    }

    private String guessMime(String filename) {
        if (filename == null) return "text/plain";
        if (filename.endsWith(".json")) return "application/json";
        if (filename.endsWith(".ics")) return "text/calendar";
        return "text/plain";
    }

    @Override
    protected void onResume() {
        super.onResume();
        ReminderScheduler.clearNotification(this);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
