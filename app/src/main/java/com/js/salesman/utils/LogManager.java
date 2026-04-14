package com.js.salesman.utils;

import android.content.Context;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogManager {
    private static final String LOG_FILE_NAME = "app_logs.txt";
    private static String lastRequest = "None";
    private static String lastResponse = "None";

    public static void log(Context context, String action, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = String.format("[%s] %s: %s\n", timestamp, action, message);
        
        Log.d("AppLog", logEntry);
        saveToFile(context, logEntry);
    }

    public static void logApi(Context context, String url, String request, String response) {
        lastRequest = request;
        lastResponse = response;
        log(context, "API_CALL", url);
    }

    public static String getLastRequest() { return lastRequest; }
    public static String getLastResponse() { return lastResponse; }

    private static void saveToFile(Context context, String entry) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
            bw.write(entry);
        } catch (IOException e) {
            Log.e("LogManager", "Error saving log", e);
        }
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    public static void clearLogs(Context context) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        if (logFile.exists()) logFile.delete();
    }
}
