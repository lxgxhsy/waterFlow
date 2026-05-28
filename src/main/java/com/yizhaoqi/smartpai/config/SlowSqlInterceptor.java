package com.yizhaoqi.smartpai.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢 SQL 拦截器
 * 监控并记录执行时间超过阈值的 SQL 语句
 */
@Component
public class SlowSqlInterceptor implements StatementInspector {
    
    private static final Logger logger = LoggerFactory.getLogger("com.yizhaoqi.smartpai.slowSql");
    private static final Logger performanceLogger = LoggerFactory.getLogger("com.yizhaoqi.smartpai.performance");
    
    // 慢 SQL 阈值（毫秒），默认 1000ms
    @Value("${app.slow-sql.threshold:1000}")
    private long slowSqlThreshold;
    
    // 记录 SQL 执行开始时间
    private final ThreadLocal<Long> sqlStartTime = new ThreadLocal<>();
    
    // SQL 执行统计
    private final ConcurrentHashMap<String, AtomicLong> sqlStats = new ConcurrentHashMap<>();
    
    @Override
    public String inspect(String sql) {
        // 记录 SQL 开始执行时间
        sqlStartTime.set(System.currentTimeMillis());
        return sql;
    }
    
    /**
     * 记录 SQL 执行完成
     * 这个方法需要在 SQL 执行后手动调用，或者通过其他方式触发
     */
    public void recordSqlExecution(String sql, long executionTime) {
        if (executionTime >= slowSqlThreshold) {
            // 记录慢 SQL
            logger.warn("【慢 SQL 检测】执行时间: {}ms, SQL: {}", executionTime, formatSql(sql));
            
            // 记录到性能日志
            performanceLogger.warn("SLOW_SQL | 执行时间: {}ms | SQL: {}", executionTime, formatSql(sql));
            
            // 更新统计信息
            sqlStats.computeIfAbsent(getSqlKey(sql), k -> new AtomicLong(0)).incrementAndGet();
        }
    }
    
    /**
     * 获取 SQL 的简化键（用于统计）
     */
    private String getSqlKey(String sql) {
        if (sql == null) return "unknown";
        // 提取 SQL 类型和表名
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT")) {
            // 尝试提取表名（简单处理）
            int fromIndex = upperSql.indexOf("FROM");
            if (fromIndex > 0) {
                String afterFrom = upperSql.substring(fromIndex + 4).trim();
                int spaceIndex = afterFrom.indexOf(" ");
                if (spaceIndex > 0) {
                    return "SELECT " + afterFrom.substring(0, spaceIndex);
                }
            }
            return "SELECT";
        } else if (upperSql.startsWith("INSERT")) {
            return "INSERT";
        } else if (upperSql.startsWith("UPDATE")) {
            return "UPDATE";
        } else if (upperSql.startsWith("DELETE")) {
            return "DELETE";
        }
        return "OTHER";
    }
    
    /**
     * 格式化 SQL（限制长度，避免日志过长）
     */
    private String formatSql(String sql) {
        if (sql == null) return "";
        // 移除多余空格
        sql = sql.replaceAll("\\s+", " ").trim();
        // 限制长度
        if (sql.length() > 500) {
            return sql.substring(0, 500) + "...";
        }
        return sql;
    }
    
    /**
     * 获取慢 SQL 统计信息
     */
    public ConcurrentHashMap<String, AtomicLong> getSlowSqlStats() {
        return sqlStats;
    }
    
    /**
     * 清除统计信息
     */
    public void clearStats() {
        sqlStats.clear();
    }
}
