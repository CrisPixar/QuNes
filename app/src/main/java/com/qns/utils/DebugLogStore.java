package com.qns.utils;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DebugLogStore {
    private DebugLogStore() {}

    public static String collect(Context context) {
        if (!com.qns.BuildConfig.DEBUG) return "Debug log viewer is disabled in release builds.";
        StringBuilder output = new StringBuilder();
        output.append("QNS debug log\n");
        output.append("time=").append(new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())).append('\n');
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "-t", "2000"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(redact(line)).append('\n');
            }
            process.destroy();
        } catch (Exception error) {
            output.append("logcat unavailable: ").append(error.getClass().getSimpleName()).append('\n');
        }
        return output.toString();
    }

    public static String fileName() {
        return "logcat_" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date()) + ".log";
    }

    /** Сохраняет лог в файл в app-specific external dir; возвращает файл или null. */
    public static File save(Context context, String logs) {
        if (!com.qns.BuildConfig.DEBUG) return null;
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir == null) dir = context.getFilesDir();
            File file = new File(dir, fileName());
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(logs.getBytes(StandardCharsets.UTF_8));
            }
            return file;
        } catch (Exception error) {
            return null;
        }
    }

    private static String redact(String value) {
        return value.replaceAll("Bearer\\s+[A-Za-z0-9._-]+", "Bearer [redacted]")
            .replaceAll("(?i)(password|token|secret|privateKey)\\s*[=:]\\s*[^\\s,}]+", "$1=[redacted]");
    }
}
