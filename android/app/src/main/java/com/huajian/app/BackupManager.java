package com.huajian.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

public final class BackupManager {
    private static final String PREFIX = "huajian-backup-";
    private static final String MIME = "application/json";

    private BackupManager() {}

    private static boolean isBackupName(String name) {
        if (name == null) return false;
        String value = name.toLowerCase(Locale.ROOT);
        return value.startsWith(PREFIX) && value.endsWith(".json");
    }

    private static String safeName(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\/:*?\"<>|]", "_");
        if (value.isEmpty()) value = PREFIX + "manual.json";
        if (!value.toLowerCase(Locale.ROOT).startsWith(PREFIX)) value = PREFIX + value;
        if (!value.toLowerCase(Locale.ROOT).endsWith(".json")) value += ".json";
        return value;
    }

    public static synchronized boolean create(Context context, String name, String content) {
        String safe = safeName(name);
        byte[] data = (content == null ? "{}" : content).getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= 29) return createMediaStore(context, safe, data);
        return createLegacy(safe, data);
    }

    public static synchronized String list(Context context) {
        if (Build.VERSION.SDK_INT >= 29) return listMediaStore(context);
        return listLegacy();
    }

    public static synchronized String read(Context context, String name) {
        String safe = safeName(name);
        if (Build.VERSION.SDK_INT >= 29) return readMediaStore(context, safe);
        return readLegacy(safe);
    }

    public static synchronized boolean delete(Context context, String name) {
        String safe = safeName(name);
        if (Build.VERSION.SDK_INT >= 29) return deleteMediaStore(context, safe);
        return deleteLegacy(safe);
    }


    private static boolean createMediaStore(Context context, String name, byte[] data) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, MIME);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = resolver.insert(collection, values);
            if (uri == null) return false;
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) return false;
                out.write(data);
            }
            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return true;
        } catch (Exception e) {
            if (uri != null) {
                try { resolver.delete(uri, null, null); } catch (Exception ignored) {}
            }
            return false;
        }
    }

    private static String listMediaStore(Context context) {
        JSONArray array = new JSONArray();
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED
        };
        String selection = MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
        String[] args = { PREFIX + "%" };
        try (Cursor cursor = resolver.query(collection, projection, selection, args, MediaStore.Downloads.DATE_MODIFIED + " DESC")) {
            if (cursor == null) return array.toString();
            int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID);
            int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME);
            int sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE);
            int timeIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED);
            while (cursor.moveToNext()) {
                String name = cursor.getString(nameIndex);
                if (!isBackupName(name)) continue;
                JSONObject item = new JSONObject();
                item.put("name", name);
                item.put("size", cursor.getLong(sizeIndex));
                item.put("time", cursor.getLong(timeIndex) * 1000L);
                item.put("id", cursor.getLong(idIndex));
                array.put(item);
            }
        } catch (Exception ignored) {}
        return array.toString();
    }

    private static Uri findMediaStoreUri(Context context, String name) {
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Downloads._ID };
        String selection = MediaStore.Downloads.DISPLAY_NAME + " = ?";
        String[] args = { name };
        try (Cursor cursor = resolver.query(collection, projection, selection, args, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID));
                return ContentUris.withAppendedId(collection, id);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String readMediaStore(Context context, String name) {
        Uri uri = findMediaStoreUri(context, name);
        if (uri == null) return null;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean deleteMediaStore(Context context, String name) {
        Uri uri = findMediaStoreUri(context, name);
        if (uri == null) return false;
        try {
            return context.getContentResolver().delete(uri, null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static File downloadsDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    private static boolean createLegacy(String name, byte[] data) {
        try {
            File dir = downloadsDir();
            if (!dir.exists() && !dir.mkdirs()) return false;
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(data);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String listLegacy() {
        JSONArray array = new JSONArray();
        try {
            File[] files = downloadsDir().listFiles((d, n) -> isBackupName(n));
            if (files == null) return array.toString();
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (File file : files) {
                JSONObject item = new JSONObject();
                item.put("name", file.getName());
                item.put("size", file.length());
                item.put("time", file.lastModified());
                array.put(item);
            }
        } catch (Exception ignored) {}
        return array.toString();
    }

    private static String readLegacy(String name) {
        try {
            File file = new File(downloadsDir(), name);
            if (!file.exists()) return null;
            byte[] buffer = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int offset = 0;
                int read;
                while (offset < buffer.length && (read = in.read(buffer, offset, buffer.length - offset)) != -1) {
                    offset += read;
                }
            }
            return new String(buffer, 0, buffer.length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean deleteLegacy(String name) {
        try {
            File file = new File(downloadsDir(), name);
            return file.exists() && file.delete();
        } catch (Exception e) {
            return false;
        }
    }
}
