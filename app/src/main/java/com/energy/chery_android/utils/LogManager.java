package com.energy.chery_android.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日志管理器
 * 提供统一的日志管理功能，包括文件日志、控制台日志等
 */
public class LogManager {
    private static final String TAG = "LogManager";
    private static final String LOG_FILE_NAME = "app_log.txt";
    
    private static LogManager instance;
    private Context context;
    private boolean enableFileLog = true;
    private boolean enableConsoleLog = true;
    private LogLevel minLogLevel = LogLevel.DEBUG;
    
    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        VERBOSE(0, "V"),
        DEBUG(1, "D"),
        INFO(2, "I"),
        WARN(3, "W"),
        ERROR(4, "E");
        
        private final int level;
        private final String tag;
        
        LogLevel(int level, String tag) {
            this.level = level;
            this.tag = tag;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getTag() {
            return tag;
        }
    }
    
    /**
     * 日志配置类
     */
    public static class LogConfig {
        public boolean enableFileLog = true;
        public boolean enableConsoleLog = true;
        public LogLevel minLogLevel = LogLevel.DEBUG;
        public String logFileName = "app_log.txt";
        public int maxFileSize = 5 * 1024 * 1024; // 5MB
        public int maxFileCount = 5;
        
        public LogConfig() {}
    }
    
    private LogConfig config;
    
    private LogManager(Context context) {
        this.context = context.getApplicationContext();
        this.config = new LogConfig();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized LogManager getInstance(Context context) {
        if (instance == null) {
            instance = new LogManager(context);
        }
        return instance;
    }
    
    /**
     * 设置日志配置
     */
    public void setConfig(LogConfig config) {
        this.config = config;
        this.enableFileLog = config.enableFileLog;
        this.enableConsoleLog = config.enableConsoleLog;
        this.minLogLevel = config.minLogLevel;
    }
    
    /**
     * 记录调试日志
     */
    public void d(String tag, String message) {
        log(LogLevel.DEBUG, tag, message, null);
    }
    
    /**
     * 记录调试日志
     */
    public void d(String message) {
        log(LogLevel.DEBUG, TAG, message, null);
    }
    
    /**
     * 记录信息日志
     */
    public void i(String tag, String message) {
        log(LogLevel.INFO, tag, message, null);
    }
    
    /**
     * 记录信息日志
     */
    public void i(String message) {
        log(LogLevel.INFO, TAG, message, null);
    }
    
    /**
     * 记录警告日志
     */
    public void w(String tag, String message) {
        log(LogLevel.WARN, tag, message, null);
    }
    
    /**
     * 记录警告日志
     */
    public void w(String message) {
        log(LogLevel.WARN, TAG, message, null);
    }
    
    /**
     * 记录错误日志
     */
    public void e(String tag, String message) {
        log(LogLevel.ERROR, tag, message, null);
    }
    
    /**
     * 记录错误日志
     */
    public void e(String message) {
        log(LogLevel.ERROR, TAG, message, null);
    }
    
    /**
     * 记录错误日志（带异常）
     */
    public void e(String tag, String message, Throwable throwable) {
        log(LogLevel.ERROR, tag, message, throwable);
    }
    
    /**
     * 记录错误日志（带异常）
     */
    public void e(String message, Throwable throwable) {
        log(LogLevel.ERROR, TAG, message, throwable);
    }
    
    /**
     * 记录详细日志
     */
    public void v(String tag, String message) {
        log(LogLevel.VERBOSE, tag, message, null);
    }
    
    /**
     * 记录详细日志
     */
    public void v(String message) {
        log(LogLevel.VERBOSE, TAG, message, null);
    }
    
    /**
     * 核心日志记录方法
     */
    private void log(LogLevel level, String tag, String message, Throwable throwable) {
        if (level.getLevel() < minLogLevel.getLevel()) {
            return;
        }
        
        String logMessage = formatLogMessage(level, tag, message, throwable);
        
        // 控制台日志
        if (enableConsoleLog) {
            writeToConsole(level, tag, logMessage);
        }
        
        // 文件日志
        if (enableFileLog) {
            writeToFile(logMessage);
        }
    }
    
    /**
     * 格式化日志消息
     */
    private String formatLogMessage(LogLevel level, String tag, String message, Throwable throwable) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(" ");
        sb.append(level.getTag()).append("/");
        sb.append(tag).append(": ");
        sb.append(message);
        
        if (throwable != null) {
            sb.append("\n").append(Log.getStackTraceString(throwable));
        }
        
        return sb.toString();
    }
    
    /**
     * 写入控制台
     */
    private void writeToConsole(LogLevel level, String tag, String message) {
        switch (level) {
            case VERBOSE:
                Log.v(tag, message);
                break;
            case DEBUG:
                Log.d(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case WARN:
                Log.w(tag, message);
                break;
            case ERROR:
                Log.e(tag, message);
                break;
        }
    }
    
    /**
     * 写入文件
     */
    private void writeToFile(String message) {
        try {
            File logFile = getLogFile();
            if (logFile != null) {
                // 检查文件大小，如果超过限制则轮转
                if (logFile.length() > config.maxFileSize) {
                    rotateLogFiles();
                }
                
                FileWriter writer = new FileWriter(logFile, true);
                writer.append(message).append("\n");
                writer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to log file", e);
        }
    }
    
    /**
     * 获取日志文件
     */
    private File getLogFile() {
        try {
            File logDir = new File(context.getExternalFilesDir(null), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            return new File(logDir, config.logFileName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get log file", e);
            return null;
        }
    }
    
    /**
     * 轮转日志文件
     */
    private void rotateLogFiles() {
        try {
            File logFile = getLogFile();
            if (logFile != null && logFile.exists()) {
                // 删除最旧的日志文件
                File oldFile = new File(logFile.getParent(), config.logFileName + "." + config.maxFileCount);
                if (oldFile.exists()) {
                    oldFile.delete();
                }
                
                // 重命名现有日志文件
                for (int i = config.maxFileCount - 1; i > 0; i--) {
                    File currentFile = new File(logFile.getParent(), config.logFileName + "." + i);
                    File nextFile = new File(logFile.getParent(), config.logFileName + "." + (i + 1));
                    if (currentFile.exists()) {
                        currentFile.renameTo(nextFile);
                    }
                }
                
                // 重命名当前日志文件
                File firstBackup = new File(logFile.getParent(), config.logFileName + ".1");
                logFile.renameTo(firstBackup);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to rotate log files", e);
        }
    }
    
    /**
     * 清除所有日志文件
     */
    public void clearLogs() {
        try {
            File logDir = new File(context.getExternalFilesDir(null), "logs");
            if (logDir.exists()) {
                File[] files = logDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().startsWith(config.logFileName)) {
                            file.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear logs", e);
        }
    }
    
    /**
     * 获取日志文件路径
     */
    public String getLogFilePath() {
        File logFile = getLogFile();
        return logFile != null ? logFile.getAbsolutePath() : null;
    }
    
    /**
     * 检查日志文件是否存在
     */
    public boolean isLogFileExists() {
        File logFile = getLogFile();
        return logFile != null && logFile.exists();
    }
    
    /**
     * 获取日志文件大小
     */
    public long getLogFileSize() {
        File logFile = getLogFile();
        return logFile != null && logFile.exists() ? logFile.length() : 0;
    }
    
    /**
     * 设置最小日志级别
     */
    public void setMinLogLevel(LogLevel level) {
        this.minLogLevel = level;
    }
    
    /**
     * 启用/禁用文件日志
     */
    public void setFileLogEnabled(boolean enabled) {
        this.enableFileLog = enabled;
    }
    
    /**
     * 启用/禁用控制台日志
     */
    public void setConsoleLogEnabled(boolean enabled) {
        this.enableConsoleLog = enabled;
    }
    
    /**
     * 记录应用启动日志
     */
    public void logAppStart() {
        i("App started at " + new Date().toString());
        i("Device: " + CommonUtils.DeviceUtils.getDeviceInfo());
        i("Android Version: " + CommonUtils.DeviceUtils.getAndroidVersion());
        i("API Level: " + CommonUtils.DeviceUtils.getApiLevel());
    }
    
    /**
     * 记录应用停止日志
     */
    public void logAppStop() {
        i("App stopped at " + new Date().toString());
    }
    
    /**
     * 记录状态栏变化日志
     */
    public void logStatusBarChange(boolean isImmersive, int statusBarColor, boolean isLightText) {
        i(String.format("Status bar changed: immersive=%s, color=%s, lightText=%s", 
            isImmersive, CommonUtils.ColorUtils.toHexString(statusBarColor), isLightText));
    }
    
    /**
     * 记录WebView加载日志
     */
    public void logWebViewLoad(String url) {
        i("WebView loading: " + url);
    }
    
    /**
     * 记录错误日志
     */
    public void logError(String component, String operation, Throwable throwable) {
        e(String.format("Error in %s during %s", component, operation), throwable);
    }
}
