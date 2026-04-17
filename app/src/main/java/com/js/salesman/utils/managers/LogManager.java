package com.js.salesman.utils.managers;

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
    private static final String API_LOG_FILE_NAME = "api_logs.txt";
    private static String lastRequest = "None";
    private static String lastResponse = "None";
    private static String lastActivity = "None";
    private static String lastSystem = "None";

    public static void log(Context context, String action, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = String.format("[%s] %s: %s\n", timestamp, action, message);
        
        lastActivity = String.format("[%s] %s: %s", timestamp, action, message);
        Log.d("AppLog", logEntry);
        saveToFile(context, LOG_FILE_NAME, logEntry);
    }

    public static void logSystem(Context context, String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = String.format("[%s] SYSTEM: %s\n", timestamp, message);
        
        lastSystem = String.format("[%s] SYSTEM: %s", timestamp, message);
        Log.i("SystemLog", logEntry);
        saveToFile(context, LOG_FILE_NAME, logEntry);
    }

    public static void logError(Context context, String tag, String message, Throwable throwable) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = String.format("[%s] ERROR [%s]: %s\n", timestamp, tag, message + (throwable != null ? " - " + throwable.getMessage() : ""));
        
        Log.e(tag, logEntry, throwable);
        saveToFile(context, LOG_FILE_NAME, logEntry);
    }

    public static void logApi(Context context, String url, String request, String response) {
        lastRequest = request;
        lastResponse = response;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String apiLog = String.format("[%s] API: %s\nRequest: %s\nResponse: %s\n\n", timestamp, url, request, response);
        saveToFile(context, API_LOG_FILE_NAME, apiLog);
        log(context, "API_CALL", url);
    }

    public static String getLastRequest() { return lastRequest; }
    public static String getLastResponse() { return lastResponse; }
    public static String getLastActivity() { return lastActivity; }
    public static String getLastSystem() { return lastSystem; }

    private static void saveToFile(Context context, String fileName, String entry) {
        File logFile = new File(context.getFilesDir(), fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(logFile, true))) {
            bw.write(entry);
        } catch (IOException e) {
            Log.e("LogManager", "Error saving log to " + fileName, e);
        }
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILE_NAME);
    }

    public static File getApiLogFile(Context context) {
        return new File(context.getFilesDir(), API_LOG_FILE_NAME);
    }

    public static void clearLogs(Context context) {
        File logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        if (logFile.exists()) logFile.delete();
        File apiLogFile = new File(context.getFilesDir(), API_LOG_FILE_NAME);
        if (apiLogFile.exists()) apiLogFile.delete();
        lastRequest = "None";
        lastResponse = "None";
        lastActivity = "None";
        lastSystem = "None";
    }
}
