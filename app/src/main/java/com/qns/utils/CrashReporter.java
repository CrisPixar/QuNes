package com.qns.utils;

import android.content.Context;
import android.content.pm.PackageInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashReporter {
    private static final String FILE_NAME = "last_crash_report.log";
    private static CrashReporter instance;
    private final Context context;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashReporter(Context context) {
        this.context = context.getApplicationContext();
        this.previous = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static synchronized CrashReporter install(Context context) {
        if (instance != null) return instance;
        instance = new CrashReporter(context);
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            instance.write(thread, error);
            if (instance.previous != null) instance.previous.uncaughtException(thread, error);
        });
        return instance;
    }

    public boolean hasReport() { return reportFile().isFile() && reportFile().length() > 0; }
    public String read() {
        try { return new String(java.nio.file.Files.readAllBytes(reportFile().toPath()), StandardCharsets.UTF_8); }
        catch (Exception ignored) { return "Crash report is unavailable"; }
    }
    public void clear() { if (reportFile().exists()) reportFile().delete(); }
    public File reportFile() { return new File(context.getFilesDir(), FILE_NAME); }

    private synchronized void write(Thread thread, Throwable error) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            String report = "timestamp=" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date())
                + "\npackage=" + context.getPackageName()
                + "\nversion=" + info.versionName
                + "\nbuild=" + (com.qns.BuildConfig.DEBUG ? "debug" : "release")
                + "\ndevice=" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\nandroid=" + android.os.Build.VERSION.RELEASE
                + "\nthread=" + thread.getName()
                + "\nerror=" + error;
            try (FileOutputStream output = new FileOutputStream(reportFile(), false)) {
                output.write(report.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }
}
