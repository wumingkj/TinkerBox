# TinkerBox 开发计划

一个类似 Scene / 玩机工具箱的 Root 调优工具，适配 KernelSU（含 SukiSU）与 Magisk。
技术栈：Kotlin + Jetpack Compose 原子化 UI，风格参考 SukiSU 液态玻璃。

**目标设备**：一加 Ace 2 Pro（SukiSU LKM 模式、Android 15、Kernel 5.15）
**包名**：`com.wuming.tinkerbox`

---

## 一、核心设计

### 权限模式（能力矩阵，非三套 UI）
同一套 Compose 界面，按权限模式灰度能力：

| 模式 | 能力范围 | 所需权限 |
|---|---|---|
| 普通 Normal | 只读监控 + 轻量调校 | 无 |
| Shizuku (ADB) | 补足应用冻结与系统设置 | Shizuku 服务 |
| Root | 内核调优、温控、场景守护 | KSU / Magisk |

### 模式切换与回退
- 首次启动（无存档）→ `ModeSelectionScreen` 引导选择。
- 选择界面实时展示各模式检测状态徽章（Root 是否可用 / Shizuku 是否连接）。
- 已选模式持久化于 DataStore（`core/mode/ModeRepository.kt`）。
- 每次启动校验已选模式权限；失效则回退到模式选择界面（普通模式永不过期）。
- 设置页可随时切换模式，切换即时生效并持久化（`SettingsScreen` + `ModeViewModel`）。

### 系统访问分层
- JNI 热路径：高频 sysfs 读（频率 / 温度 / 负载）。
- LibSu / Shell 冷路径：写入、复杂操作、root 版本识别。
- Shizuku 冷路径：冻结应用、改系统设置（已集成官方库 `rikka.shizuku`）。

---

## 二、已完成 ✅

### 基础框架
- [x] 项目大框架（Kotlin + Compose，原子化目录）
- [x] AGP 8.13.2 + Kotlin 2.2.20 + Gradle 9.5 构建适配（阿里云镜像优先）
- [x] App 图标（SVG → 自适应图标：前景 + 深蓝渐变背景）
- [x] 无线安装 / 覆盖安装到 192.168.1.143:5555 验证

### 权限模式体系（已完成）
- [x] `PermissionMode`（Normal / Shizuku / Root 能力矩阵）
- [x] `ModeRepository`（DataStore 持久化）
- [x] `PermissionChecker`（各模式检测 + Root 后端识别）
- [x] `ModeViewModel`（启动校验 + 权限失效回退 + 模式切换）
- [x] `MainScreen`（loading → 模式引导 / 主界面）
- [x] `ModeSelectionScreen`（带实时检测徽章的引导页）

### Root 检测
- [x] `su -c id` 验证 uid=0 + `su -v` 版本串分流失（KSU 系 / Magisk）
- [x] 实测识别 SukiSU：KernelSU `4.2.1-2-gdfae9237` ✓

### 底部导航骨架（完成）
- [x] 底部导航：监控台 / 应用管理 / 场景 / 模块 / 设置
- [x] `HomeScreen` 作为导航容器，各 tab 按模式接入（`ModeSelectionScreen` / 应用管理 / 设置已完成，监控 / 场景 / 模块为占位）
- [x] tab 更名：「调校」→「应用管理」（贴合其承载的应用冻结/停用功能，避免与未来 CPU「调校/tune」混淆）

### 应用管理（应用管理 tab，已完成核心）
- [x] 应用列表：第三方 / 系统 / 已冻结 三 Tab 分类
- [x] 搜索、应用图标（两阶段加载：无图标秒出列表，后台补图标）
- [x] 完整冻结模式：停用(disable-user) / 禁用(disable) / 隐藏(hide) / 挂起(suspend)
- [x] 一键解冻、批量操作、数据层 `FrozenRepository` 持久化透明记录
- [x] 冻结状态识别：SDK 直读 `ApplicationInfo`（enabled / FLAG_SUSPENDED / privateFlags 反射），对齐 Hail
  - 挂起 / 隐藏为系统权威标志，无条件识别
  - 停用/禁用受「用户主动冻结记录」白名单约束，过滤 ColorOS 智能停用闲置应用的大量误报（192→个位数）
- [x] 普通模式只读列表（按钮灰），Shizuku / Root 模式可实际冻结
- [x] 命令日志与 logcat 双写（`CommandLog`），日志界面实时展示
- [x] Root 模式下 `pm` 命令执行（`AppFreezer`）

### Shizuku 集成
- [x] 接入官方库 `dev.rikka.shizuku:api` + `core`
- [x] Manifest 注册 `ShizukuProvider`
- [x] `ShizukuManager`：连接状态 / 权限检查与请求 / 授权监听
- [x] 授权成功后走 Shizuku binder 执行 shell（`AppFreezer.forShizuku()`）

### 设置页（部分完成）
- [x] 运行模式切换（普通 / Shizuku / Root），即时生效并持久化
- [x] 当前能力状态展示、关于占位

### 监控台（P1，已完成核心）
- [x] CPU 各簇频率条（Ace 2 Pro：1+3+4 拓扑），只读 sysfs 轮询
  - `CpuMonitor.readClusters()`：按 max 频率自动分簇（0/64/mid + 1/72/large），跨平台兼容骁龙 / 天机 / 展锐 / 瑞芯微
  - `FreqBar`：频率条带 600ms 缓冲动画（`animateFloatAsState`），避免每秒采样跳变突兀
- [x] 设备 SoC 卡片：型号（`Build.SOC_MODEL` / cpuinfo 硬件名 / ABI / 核心数），跨平台兼容
- [x] 温度传感器徽章（`ThermalBadge` + `ThermalMonitor` 按有效范围遍历 thermal_zone，不依赖 type 命名）
- [x] 内存 + Swap 进度条（`MemoryMonitor` 解析 `/proc/meminfo`，普通模式即可读，无需 Root）
- [x] 进程列表（top 风格，按权限模式分级采样）
  - 普通模式：`/proc` 直读 / 受 SELinux 限制（`hidepid` 隔离，仅自身进程）
  - Shizuku / Root：解析系统 `top -b -n 1`，以 `TIME+`（m:ss.cc）列为锚点定位 PID / %CPU / 进程名，规避 toybox 表头与数据列错位
  - 进程名 → 应用名 + 包名 + 图标映射（`uid → getPackagesForUid → label/icon`），系统进程保留原始名
- [x] 排序切换：按 CPU / 按应用名
- [x] 点击进程 → 操作弹窗：仅「停止进程」（`am force-stop`，Shizuku / Root）；普通模式提示无权限
  - 推断：进程操作与应用管理解耦，**监控台不提供冻结/停用**（避免误停应用/掉图标）
- [x] 滚动条：固定高度 320dp 内滚动展示更多进程（当前读取 top 80 条）

---

## 三、待办（按优先级）

### P2｜监控台收尾
- [ ] 守护进程可用事件驱动（轮询而非 inotify，因 sysfs 不触发 inotify）
- [ ] 进程/温度历史曲线、负载图

### P3｜调校 / 场景 / 模块
- [ ] CPU / GPU 调校（需 Root：gov、频率表、调度），届时再评估是否单列「调校/tune」tab
- [ ] 场景配置（游戏 / 均衡 / 省电）+ 场景守护
- [ ] 模块管理（KSU / Magisk 模块增删启停）
- [ ] 应用管理优化：批量冻结 / 批量解冻全选、按需过滤系统预禁用内部组件

### P4｜设置扩展
- [ ] 主题切换（深色 / 浅色 / 跟随系统）
- [ ] 关于页完善（版本号、开源许可、更新渠道）

---

## 四、构建 & 部署备忘

```bash
# 编译
gradle :app:assembleDebug --no-daemon

# 无线安装（当前设备出现两个入口，用 -s 指定）
adb -s 192.168.1.143:5555 install -r app/build/outputs/apk/debug/app-debug.apk

# 启动（debug 版 applicationIdSuffix = .debug）
adb -s 192.168.1.143:5555 shell am start -n \
    com.wuming.tinkerbox.debug/com.wuming.tinkerbox.MainActivity
```

> 注意：adb 同时存在 TCP/IP 与 mDNS 两个入口指向同一台设备，命令必须加 `-s 192.168.1.143:5555`。
> 包名固定 `com.wuming.tinkerbox`（不带 `-debug`），版本号带 `-debug` 后缀，便于覆盖升级。

---

## 五、已知约定 & 坑

- Root 检测**不能**靠 `File.exists()` 探测 `/data/adb`（普通 App 无权限读该目录，必假）。
- SukiSU 是 KSU 分支，`RootBackend` 不单独建分支，沿用 KernelSU 处理。
- **ColorOS 冻结语义**：挂起(suspend) 图标立即变灰；停用(disable-user) 图标先保留不可点、延迟移除；禁用(disable)/隐藏(hide) 图标消失。
- **ColorOS 智能停用**：系统会把长期未打开的闲置应用标记为 `enabled=false`，大量误报为冻结。必须以「用户主动冻结记录」白名单约束 `enabled` 判定，挂起/隐藏不受影响。
- **包可见性**：Android 11+ 需声明 `QUERY_ALL_PACKAGES`，且 `getInstalledApplications` 用 `MATCH_UNINSTALLED_PACKAGES`（与 Hail 一致），既读回被停用应用又不过度拉取系统深层组件。
- 内置 `kotlinx-serialization` 等按需添加；Compose BOM 用 2024.12.01。
- 数据只在需要时持久化；模式与冻结记录存 DataStore，高阶调优配置后续存入同源。