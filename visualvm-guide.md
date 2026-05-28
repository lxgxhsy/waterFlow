# VisualVM 使用指南

## 1. 下载和安装 VisualVM

### 下载地址
- 官网：https://visualvm.github.io/
- 直接下载：https://github.com/oracle/visualvm/releases

### 安装步骤
1. 下载 `visualvm_xxx.zip`（Windows 版本）
2. 解压到任意目录，例如：`D:\tools\visualvm`
3. 运行 `bin\visualvm.exe`

## 2. 连接到运行中的 Java 应用

### 方法 A：本地应用（自动发现）
1. 启动你的 Spring Boot 应用
2. 打开 VisualVM
3. 在左侧 "应用程序" 面板中，找到 `com.yizhaoqi.smartpai.SmartPaiApplication`
4. 双击打开

### 方法 B：通过 JMX 连接（远程/本地）
如果自动发现失败，可以手动连接：

1. **启动应用时添加 JMX 参数**（在 IDEA 的 VM options 中）：
```bash
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

2. **在 VisualVM 中连接**：
   - 右键 "本地" → "添加 JMX 连接"
   - 连接：`localhost:9999`
   - 点击 "确定"

## 3. 查看 GC 信息

### 3.1 监控标签页
- 打开应用后，点击 **"监控"** 标签
- 可以看到：
  - **堆内存使用情况**（实时图表）
  - **Metaspace 使用情况**
  - **类加载数量**
  - **线程数量**

### 3.2 查看 GC 活动
1. 在 "监控" 标签页中，点击 **"执行垃圾回收"** 按钮
2. 观察内存使用曲线的变化
3. 查看 GC 前后的内存对比

### 3.3 安装 Visual GC 插件（推荐）
1. 工具 → 插件
2. 在 "可用插件" 中搜索 "Visual GC"
3. 勾选并点击 "安装"
4. 安装后重启 VisualVM
5. 重新打开应用，会看到 **"Visual GC"** 标签页
6. 可以实时查看：
   - Eden 区、Survivor 区、Old 区的使用情况
   - GC 频率和耗时
   - 各代内存的详细统计

### 3.4 采样器标签页
- 点击 **"采样器"** 标签
- 可以查看：
  - CPU 使用情况
  - 内存使用情况（按对象类型）
  - 线程活动情况

## 4. 常用功能

### 生成堆转储（Heap Dump）
1. 右键应用 → "堆转储"
2. 可以分析内存中的对象分布
3. 查看哪些对象占用内存最多

### 生成线程转储（Thread Dump）
1. 右键应用 → "线程转储"
2. 可以查看所有线程的状态
3. 排查死锁和线程阻塞问题

### 性能分析
1. 点击 **"分析器"** 标签
2. 可以分析 CPU 和内存性能
3. 查看方法调用热点

## 5. 注意事项

- VisualVM 需要 Java 8+ 运行环境
- 如果应用运行在 Docker 中，需要配置端口映射
- 生产环境建议使用 JMX 连接，并启用认证和 SSL
