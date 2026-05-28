package com.yizhaoqi.smartpai.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GC 监控工具类
 * 用于监控 JVM 垃圾回收情况
 */
@Component
public class GcMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(GcMonitor.class);
    
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    /**
     * 获取 GC 信息
     * @return GC 信息 Map
     */
    public Map<String, Object> getGcInfo() {
        Map<String, Object> gcInfo = new HashMap<>();
        
        // 内存使用情况
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        Map<String, Object> heap = new HashMap<>();
        heap.put("used", formatBytes(heapUsage.getUsed()));
        heap.put("committed", formatBytes(heapUsage.getCommitted()));
        heap.put("max", formatBytes(heapUsage.getMax()));
        heap.put("init", formatBytes(heapUsage.getInit()));
        heap.put("usagePercent", String.format("%.2f%%", 
            (double) heapUsage.getUsed() / heapUsage.getMax() * 100));
        
        Map<String, Object> nonHeap = new HashMap<>();
        nonHeap.put("used", formatBytes(nonHeapUsage.getUsed()));
        nonHeap.put("committed", formatBytes(nonHeapUsage.getCommitted()));
        nonHeap.put("max", formatBytes(nonHeapUsage.getMax()));
        nonHeap.put("init", formatBytes(nonHeapUsage.getInit()));
        
        gcInfo.put("heap", heap);
        gcInfo.put("nonHeap", nonHeap);
        
        // GC 统计信息
        List<Map<String, Object>> gcStats = new java.util.ArrayList<>();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            Map<String, Object> gcStat = new HashMap<>();
            gcStat.put("name", gcBean.getName());
            gcStat.put("collectionCount", gcBean.getCollectionCount());
            gcStat.put("collectionTime", gcBean.getCollectionTime() + " ms");
            gcStats.add(gcStat);
        }
        gcInfo.put("gcStats", gcStats);
        
        return gcInfo;
    }
    
    /**
     * 获取格式化的 GC 信息字符串
     * @return 格式化的字符串
     */
    public String getGcInfoString() {
        StringBuilder sb = new StringBuilder();
        sb.append("========== GC 监控信息 ==========\n");
        
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        
        sb.append("\n【堆内存】\n");
        sb.append(String.format("  已使用: %s / %s (%.2f%%)\n", 
            formatBytes(heapUsage.getUsed()),
            formatBytes(heapUsage.getMax()),
            (double) heapUsage.getUsed() / heapUsage.getMax() * 100));
        sb.append(String.format("  已提交: %s\n", formatBytes(heapUsage.getCommitted())));
        sb.append(String.format("  初始: %s\n", formatBytes(heapUsage.getInit())));
        
        sb.append("\n【非堆内存】\n");
        sb.append(String.format("  已使用: %s\n", formatBytes(nonHeapUsage.getUsed())));
        sb.append(String.format("  已提交: %s\n", formatBytes(nonHeapUsage.getCommitted())));
        sb.append(String.format("  最大: %s\n", 
            nonHeapUsage.getMax() == -1 ? "无限制" : formatBytes(nonHeapUsage.getMax())));
        
        sb.append("\n【GC 统计】\n");
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            sb.append(String.format("  %s:\n", gcBean.getName()));
            sb.append(String.format("    回收次数: %d\n", gcBean.getCollectionCount()));
            sb.append(String.format("    回收耗时: %d ms\n", gcBean.getCollectionTime()));
        }
        
        sb.append("================================\n");
        return sb.toString();
    }
    
    /**
     * 定时打印 GC 信息（每 5 分钟）
     */
    @Scheduled(fixedRate = 300000) // 5 分钟
    public void logGcInfo() {
        logger.info("\n{}", getGcInfoString());
    }
    
    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}
