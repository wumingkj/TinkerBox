# 🧰 TinkerBox

一个基于 **KernelSU（含 SukiSU）/ Magisk** 的 Android 玩机 / 系统调优工具箱。

对标 Scene 类工具，采用 **Jetpack Compose** 原子化 UI 与液体玻璃（毛玻璃）设计语言，通过统一的 Compose 界面按**权限模式**灰度能力，无需为不同 Root 环境维护多套 UI。

> 目标设备：一加 Ace 2 Pro（SukiSU LKM / Android 15 / Kernel 5.15）
>
> 包名：`com.wuming.tinkerbox`

---

## ✨ 功能特性

### 🖥 系统监控（监控台）
- **SoC 卡片**：自动识别 SoC 型号、ABI、核心数（跨骁龙 / 天机 / 展锐 / 瑞芯微）
- **CPU 频率条**：按 max 频率自动分簇（如 1+3+4 拓扑），`ensureFrequencyBar` 带 600ms 缓冲动画避免每秒跳变突兀
- **温度徽章**：自动遍历 `thermal_zone` 取有效区间，不依赖传感器命名
- **内存 + Swap**：解析 `/proc/meminfo`，普通模式即可查看
- **进程列表**：top 风格，按权限模式分级采样
  - 普通模式：`/proc` 直读（受 SELinux `hidepid` 隔离，仅自身进程可用）
  - Shizuku / Root：解析系统 `top -b -n 1`，以 `TIME+` 列为锚点精确定位 PID / %CPU，并映射为应用名 + 图标
  - 支持按 CPU / 应用名排序，点击进程可「停止进程」
- **电源监控**：电池电量、电流、电压（优先 BatteryManager 框架 API，回退 sysfs）

### 📦 应用管理
- 应用列表三 Tab：第三方 / 系统 / 已冻结
- 支持搜索，图标两阶段加载（列表秒出，后台补全图标）
- 完整冻结模式：**停用 (disable-user) / 禁用 (disable) / 隐藏 (hide) / 挂起 (suspend)**
- 一键解冻、批量操作，透明记录持久化到 DataStore
- 冻结状态识别对齐 Hail：挂起 / 隐藏走系统权威标志；停用 / 禁用以「用户主动冻结记录」白名单约束，规避 ColorOS 智能停用的大量误报

### 🛡 权限模式体系（能力矩阵，非三套 UI）
同一套 Compose 界面，按权限模式灰度能力：

| 模式 | 能力范围 | 所需权限 |
|---|---|---|
| 普通 Normal | 只读监控 + 轻量调校 | 无 |
| Shizuku (ADB) | 补足应用冻结与系统设置 | Shizuku 服务 |
| Root | 内核调优、温控、场景守护 | KSU / Magisk |

- 首次启动引导选择，选择界面实时展示各模式检测状态徽章
- 已选模式持久化于 DataStore，每次启动校验权限，失效自动回退到引导页
- 设置页随时切换模式，即时生效并持久化

### 🎨 液体玻璃 UI
- 悬浮胶囊底栏：半透明渐变背景、高光描边、选中态药丸浮起动画，主题自适配明暗
- Material 3 主题，支持深色 / 浅色 / 跟随系统

---

## 🧭 功能状态速览

| 模块 | 状态 |
|---|---|
| 权限模式体系 | ✅ 完成 |
| Root 检测（KSU/SukiSU/Magisk 分流） | ✅ 完成 |
| Shizuku 集成（官方 `rikka.shizuku`） | ✅ 完成 |
| 应用管理 | ✅ 完成核心 |
| 监控台 | ✅ 完成核心 |
| 设置页（模式 / 主题切换） | ✅ 完成 |
| 场景 / 模块管理 | 🚧 规划中 |
| CPU / GPU 调校 | 🚧 规划中 |

详细的开发计划与功能状态维护在 [`plan.md`](plan.md)。

---

## 🛠 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3（Compose BOM 2024.12.01）
- **架构**：单模块 + 原子化目录（`core/` 业务核心，`ui/` 页面视图）
- **数据持久化**：Jetpack DataStore（Preferences）
- **生命周期**：Lifecycle ViewModel / runtime-compose
- **权限增强**：官方 `dev.rikka.shizuku:api` + `core`
- **构建**：AGP 8.13.2 / Kotlin 2.2.20 / Gradle 9.5 / Java 17
- **最低系统**：Android 8.0（API 26）

---

## 🔧 构建

```bash
# 编译 Debug
gradle :app:assembleDebug --no-daemon

# Release（当前未启用混淆，可按需开启）
gradle :app:assembleRelease --no-daemon
```

Debug 产物：
```
app/build/outputs/apk/debug/app-debug.apk
```

### 无线安装（可选，针对 adb 多入口设备）
```bash
adb -s 192.168.1.143:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

> Debug 版本号带 `-debug` 后缀用于区分，包名固定为 `com.wuming.tinkerbox`（便于覆盖升级）。若你的设备同时存在 TCP/IP 与 mDNS 两个 adb 入口，请务必加 `-s <ip:port>` 指定目标。

---

## 📦 发布说明

`.gitignore` 已配置过滤，提交前请确认以下内容**不会**进入仓库：
- `local.properties`（本地 SDK 路径）
- `*.jks` / `*.keystore` / `*.p12` / 签名配置（绝对不要上传签名密钥）
- `app/build`、`.gradle` 等构建产物
- `.idea` / `.vscode` 等 IDE 配置

## 🧪 兼容性约定与已知坑

- Root 检测**不能**靠 `File.exists()` 探测 `/data/adb`（无权限 App 无法读取该目录，必是假阴性）
- SukiSU 是 KernelSU 分支，Root 后端统一沿用 KernelSU 处理，不单独建分支
- **ColorOS 冻结语义**：挂起 (suspend) 图标立即置灰；停用 (disable-user) 图标保留但不可点、延迟移除；禁用 (disable) / 隐藏 (hide) 图标消失
- **ColorOS 智能停用** 会把闲置应用标记 `enabled=false`，造成大量误报；必须以「用户主动冻结记录」白名单约束确认
- Android 11+ 需声明 `QUERY_ALL_PACKAGES`，并以 `MATCH_UNINSTALLED_PACKAGES` 读取已停用应用

---

## 📄 许可证

此项目尚未指定开源许可证，作者保留所有权利。使用前请联系作者确认。

---

📝 项目仍在持续开发中，功能与 API 可能随迭代调整。