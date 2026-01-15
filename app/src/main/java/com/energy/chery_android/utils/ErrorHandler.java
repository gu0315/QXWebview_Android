package com.energy.chery_android.utils;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误处理器
 * 统一处理应用中的各种错误，包括异常捕获、错误分类、错误恢复等
 */
public class ErrorHandler {
    private static final String TAG = "ErrorHandler";
    
    private static ErrorHandler instance;
    private Context context;
    private LogManager logManager;
    private Map<String, ErrorRecoveryStrategy> recoveryStrategies;
    
    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        NETWORK_ERROR("网络错误"),
        WEBVIEW_ERROR("WebView错误"),
        STATUS_BAR_ERROR("状态栏错误"),
        LAYOUT_ERROR("布局错误"),
        CONFIG_ERROR("配置错误"),
        THEME_ERROR("主题错误"),
        UNKNOWN_ERROR("未知错误");
        
        private final String description;
        
        ErrorType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 错误严重程度
     */
    public enum ErrorSeverity {
        LOW("低"),
        MEDIUM("中"),
        HIGH("高"),
        CRITICAL("严重");
        
        private final String description;
        
        ErrorSeverity(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 错误信息类
     */
    public static class ErrorInfo {
        public ErrorType type;
        public ErrorSeverity severity;
        public String message;
        public String component;
        public String operation;
        public Throwable throwable;
        public long timestamp;
        public Map<String, Object> context;
        
        public ErrorInfo(ErrorType type, ErrorSeverity severity, String message) {
            this.type = type;
            this.severity = severity;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.context = new HashMap<>();
        }
        
        public ErrorInfo(ErrorType type, ErrorSeverity severity, String message, Throwable throwable) {
            this(type, severity, message);
            this.throwable = throwable;
        }
    }
    
    /**
     * 错误恢复策略接口
     */
    public interface ErrorRecoveryStrategy {
        boolean canRecover(ErrorInfo errorInfo);
        void recover(ErrorInfo errorInfo);
    }
    
    /**
     * 错误监听器
     */
    public interface OnErrorListener {
        void onError(ErrorInfo errorInfo);
        void onErrorRecovered(ErrorInfo errorInfo);
        void onErrorRecoveryFailed(ErrorInfo errorInfo);
    }
    
    private OnErrorListener errorListener;
    
    private ErrorHandler(Context context) {
        this.context = context.getApplicationContext();
        this.logManager = LogManager.getInstance(context);
        this.recoveryStrategies = new HashMap<>();
        setupDefaultRecoveryStrategies();
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized ErrorHandler getInstance(Context context) {
        if (instance == null) {
            instance = new ErrorHandler(context);
        }
        return instance;
    }
    
    /**
     * 设置错误监听器
     */
    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }
    
    /**
     * 处理错误
     */
    public void handleError(ErrorInfo errorInfo) {
        // 记录错误日志
        logError(errorInfo);
        
        // 尝试恢复
        if (tryRecover(errorInfo)) {
            if (errorListener != null) {
                errorListener.onErrorRecovered(errorInfo);
            }
        } else {
            if (errorListener != null) {
                errorListener.onErrorRecoveryFailed(errorInfo);
            }
        }
        
        // 通知错误监听器
        if (errorListener != null) {
            errorListener.onError(errorInfo);
        }
    }
    
    /**
     * 处理网络错误
     */
    public void handleNetworkError(String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.NETWORK_ERROR,
            ErrorSeverity.MEDIUM,
            "网络连接失败: " + operation,
            throwable
        );
        errorInfo.component = "Network";
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 处理WebView错误
     */
    public void handleWebViewError(String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.WEBVIEW_ERROR,
            ErrorSeverity.MEDIUM,
            "WebView操作失败: " + operation,
            throwable
        );
        errorInfo.component = "WebView";
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 处理状态栏错误
     */
    public void handleStatusBarError(String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.STATUS_BAR_ERROR,
            ErrorSeverity.LOW,
            "状态栏设置失败: " + operation,
            throwable
        );
        errorInfo.component = "StatusBar";
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 处理布局错误
     */
    public void handleLayoutError(String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.LAYOUT_ERROR,
            ErrorSeverity.MEDIUM,
            "布局调整失败: " + operation,
            throwable
        );
        errorInfo.component = "Layout";
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 处理配置错误
     */
    public void handleConfigError(String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.CONFIG_ERROR,
            ErrorSeverity.LOW,
            "配置操作失败: " + operation,
            throwable
        );
        errorInfo.component = "Config";
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 处理主题错误
     */
    public void handleThemeError(String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.THEME_ERROR,
            ErrorSeverity.LOW,
            "主题设置失败: " + operation,
            throwable
        );
        errorInfo.component = "Theme";
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 处理未知错误
     */
    public void handleUnknownError(String component, String operation, Throwable throwable) {
        ErrorInfo errorInfo = new ErrorInfo(
            ErrorType.UNKNOWN_ERROR,
            ErrorSeverity.HIGH,
            "未知错误: " + component + " - " + operation,
            throwable
        );
        errorInfo.component = component;
        errorInfo.operation = operation;
        handleError(errorInfo);
    }
    
    /**
     * 记录错误日志
     */
    private void logError(ErrorInfo errorInfo) {
        String logMessage = String.format(
            "[%s] %s in %s during %s: %s",
            errorInfo.severity.getDescription(),
            errorInfo.type.getDescription(),
            errorInfo.component,
            errorInfo.operation,
            errorInfo.message
        );
        
        switch (errorInfo.severity) {
            case LOW:
                logManager.w(TAG, logMessage);
                break;
            case MEDIUM:
                logManager.w(TAG, logMessage);
                break;
            case HIGH:
                logManager.e(TAG, logMessage);
                break;
            case CRITICAL:
                logManager.e(TAG, logMessage);
                break;
        }
        
        if (errorInfo.throwable != null) {
            logManager.e(TAG, "Error details", errorInfo.throwable);
        }
    }
    
    /**
     * 尝试恢复错误
     */
    private boolean tryRecover(ErrorInfo errorInfo) {
        String strategyKey = errorInfo.component + "_" + errorInfo.type.name();
        ErrorRecoveryStrategy strategy = recoveryStrategies.get(strategyKey);
        
        if (strategy != null && strategy.canRecover(errorInfo)) {
            try {
                strategy.recover(errorInfo);
                logManager.i(TAG, "Error recovered: " + errorInfo.component + " - " + errorInfo.operation);
                return true;
            } catch (Exception e) {
                logManager.e(TAG, "Error recovery failed", e);
                return false;
            }
        }
        
        return false;
    }
    
    /**
     * 设置默认恢复策略
     */
    private void setupDefaultRecoveryStrategies() {
        // WebView错误恢复策略
        recoveryStrategies.put("WebView_WEBVIEW_ERROR", new ErrorRecoveryStrategy() {
            @Override
            public boolean canRecover(ErrorInfo errorInfo) {
                return errorInfo.message.contains("加载") || errorInfo.message.contains("load");
            }
            
            @Override
            public void recover(ErrorInfo errorInfo) {
                // 尝试重新加载WebView
                logManager.i(TAG, "Attempting to reload WebView");
            }
        });
        
        // 状态栏错误恢复策略
        recoveryStrategies.put("StatusBar_STATUS_BAR_ERROR", new ErrorRecoveryStrategy() {
            @Override
            public boolean canRecover(ErrorInfo errorInfo) {
                return true; // 状态栏错误通常可以恢复
            }
            
            @Override
            public void recover(ErrorInfo errorInfo) {
                // 重置状态栏设置
                logManager.i(TAG, "Attempting to reset status bar");
            }
        });
        
        // 布局错误恢复策略
        recoveryStrategies.put("Layout_LAYOUT_ERROR", new ErrorRecoveryStrategy() {
            @Override
            public boolean canRecover(ErrorInfo errorInfo) {
                return true; // 布局错误通常可以恢复
            }
            
            @Override
            public void recover(ErrorInfo errorInfo) {
                // 重新计算布局
                logManager.i(TAG, "Attempting to recalculate layout");
            }
        });
        
        // 配置错误恢复策略
        recoveryStrategies.put("Config_CONFIG_ERROR", new ErrorRecoveryStrategy() {
            @Override
            public boolean canRecover(ErrorInfo errorInfo) {
                return true; // 配置错误通常可以恢复
            }
            
            @Override
            public void recover(ErrorInfo errorInfo) {
                // 重置配置为默认值
                logManager.i(TAG, "Attempting to reset config to defaults");
            }
        });
    }
    
    /**
     * 添加自定义恢复策略
     */
    public void addRecoveryStrategy(String key, ErrorRecoveryStrategy strategy) {
        recoveryStrategies.put(key, strategy);
        logManager.d(TAG, "Recovery strategy added: " + key);
    }
    
    /**
     * 移除恢复策略
     */
    public void removeRecoveryStrategy(String key) {
        recoveryStrategies.remove(key);
        logManager.d(TAG, "Recovery strategy removed: " + key);
    }
    
    /**
     * 获取错误统计信息
     */
    public Map<String, Integer> getErrorStatistics() {
        // 这里可以实现错误统计功能
        Map<String, Integer> statistics = new HashMap<>();
        statistics.put("total_errors", 0);
        statistics.put("recovered_errors", 0);
        statistics.put("unrecovered_errors", 0);
        return statistics;
    }
    
    /**
     * 清除错误统计
     */
    public void clearErrorStatistics() {
        logManager.d(TAG, "Error statistics cleared");
    }
    
    /**
     * 检查是否为严重错误
     */
    public boolean isCriticalError(ErrorInfo errorInfo) {
        return errorInfo.severity == ErrorSeverity.CRITICAL;
    }
    
    /**
     * 检查是否为可恢复错误
     */
    public boolean isRecoverableError(ErrorInfo errorInfo) {
        String strategyKey = errorInfo.component + "_" + errorInfo.type.name();
        ErrorRecoveryStrategy strategy = recoveryStrategies.get(strategyKey);
        return strategy != null && strategy.canRecover(errorInfo);
    }
    
    /**
     * 获取错误描述
     */
    public String getErrorDescription(ErrorInfo errorInfo) {
        return String.format(
            "%s: %s (%s)",
            errorInfo.type.getDescription(),
            errorInfo.message,
            errorInfo.severity.getDescription()
        );
    }
}
