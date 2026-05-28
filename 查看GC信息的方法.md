# 查看 GC 信息的几种方法

## 方法1：使用 IDEA Profiler（最简单，推荐）

### 步骤：
1. **启动应用**（正常启动即可）
2. **打开 Profiler**：
   - 点击 IDEA 右上角的 **"运行"** 按钮旁边的下拉菜单
   - 选择 **"运行 'SmartPaiApplication' with Profiler"**
   - 或者：运行 → 运行 'SmartPaiApplication' with Profiler

3. **查看 GC 信息**：
   - 在 Profiler 窗口中，可以看到：
     - **Memory** 标签：实时内存使用情况
     - **CPU** 标签：CPU 使用情况
     - 点击 **"Force GC"** 按钮可以手动触发 GC
     - 查看内存曲线，可以看到 GC 发生时的内存下降

## 方法2：使用 REST API（已实现）

项目已经实现了 GC 监控接口，直接访问：

```powershell
# 文本格式（推荐）
curl http://localhost:8081/api/gc/info/text

# 或在浏览器打开
http://localhost:8081/api/gc/info/text
```

## 方法3：使用 jstat 命令（命令行）

```powershell
# 1. 找到 Java 进程 ID
jps -l

# 2. 查看 GC 信息（假设进程 ID 是 12345）
jstat -gc 12345 1000 10

# 每 1 秒打印一次，共 10 次
```

## 方法4：安装 VisualVM（需要单独下载）

### 下载地址：
https://visualvm.github.io/download.html

### 安装步骤：
1. 下载 `visualvm_xxx.zip`
2. 解压到 `D:\tools\visualvm`
3. 运行 `bin\visualvm.exe`
4. 在左侧找到你的应用并双击打开
5. 点击 **"监控"** 标签查看内存和 GC
6. 安装 **Visual GC** 插件查看详细 GC 信息

### 如果应用无法自动发现，添加 JMX 参数：

在 IDEA 运行配置的 VM options 中添加：
```
-Dcom.sun.management.jmxremote
-Dcom.sun.management.jmxremote.port=9999
-Dcom.sun.management.jmxremote.authenticate=false
-Dcom.sun.management.jmxremote.ssl=false
```

然后在 VisualVM 中：右键 "本地" → "添加 JMX 连接" → `localhost:9999`

## 方法5：在 IDEA 中配置 GC 日志

### 步骤：
1. **运行** → **编辑配置**
2. 找到你的运行配置
3. 在 **VM options** 中添加：
```
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:logs/gc.log
```

4. 运行应用后，GC 日志会输出到 `logs/gc.log`

## 推荐使用顺序：

1. **开发调试**：方法1（IDEA Profiler）或方法2（REST API）
2. **性能分析**：方法3（jstat）或方法4（VisualVM）
3. **问题排查**：方法5（GC 日志）
