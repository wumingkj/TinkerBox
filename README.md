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
  - **流畅性优化**：`LazyColumn` 惰性组合 + 进程行按 pid 作重组 key + GPU 进度条动画收敛（600ms→150ms）+ 轮询绑定页面可见性，切走即停，保证滚动与 3 秒采样不卡顿
- **电源监控**：电池电量、电流、电压（优先 BatteryManager 框架 API，回退 sysfs）
- **GPU 负载 + 网络流量**：GPU **型号/厂商/GL 版本/分辨率/刷新率**（公开 API：离屏 EGL + `glGetString` + `Display`，**普通模式即可读**，对齐 AIDA64 无 root）；GPU 实时负载 / 频率（sysfs 直读，SELinux 受限时走特权执行器兜底）；网络下行 / 上行实时速率 + 累计流量（`TrafficStats`，普通模式即可读）
- **数值智能单位**：字节按 B→KB→MB→GB→TB→PB→EB 动态进位（内存 / Swap / 网络总量与速率），电流超 1024mA 自动进位到 A

### 📦 应用管理
- 应用列表三 Tab：第三方 / 系统 / 已冻结
- 支持搜索，图标两阶段加载（列表秒出，后台补全图标）
- 冻结模式精简为：**挂起 (suspend) / 隐藏 (hide)**
- 行操作升级为**三点下拉菜单**：打开 / 详情 / 卸载（普通权限即可用，卸载跳系统界面；具冻结能力时另含解冻/挂起/隐藏）
- **已冻结列表纯实时扫描**：仅依赖系统权威标志（挂起 `FLAG_SUSPENDED` + 隐藏 `privateFlags` 最低位），不再依赖本地冻结记录，天然规避 ColorOS 智能停用误报

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
- **切换模式自动获取权限**：Root 自动触发 `su` 授权弹窗；Shizuku 无服务且持 root 时自动自启服务再弹授权框；约 12s 内授权成功即提交，失败自动退级到普通模式，无需手动去 root / Shizuku 里授权

### 🎨 关于页（二级菜单 + 版本更新检测）
- 中上部渲染**霓虹动态 Logo**（炫彩动效），下方依次展示 App 名称与版本号
- **检测 App 版本更新**：自动拉取 GitHub Release `latest` 与本地版本比较（`https://github.com/wumingkj/TinkerBox`）
- **去 GitHub 查看源码**、预留「暂时还没想到」占位卡片

### 🧭 导航与沉浸式（edge-to-edge）
- **多级页面过渡动画**：1/2/3 级页切换用 `AnimatedContent` 水平滑入/滑出，前进右滑入、后退左滑出，对齐系统返回手势
- **二级页全屏**：进入设置二级页时隐藏一级页底栏导航，返回时恢复
- **统一 edge-to-edge 容器**（`EdgeToEdgeScrollColumn` / `EdgeToEdgeLazyColumn`）：动态读取系统状态栏/手势条高度（随机型自适应），顶部首屏紧贴状态栏、向下滚动内容可一路滑到屏幕顶透过透明状态栏浮现；新增可滚动页面无需手动适配

### 🎨 主题体系（对齐 SukiSU 解耦架构）
> **说明**：miuix ≠「胶囊底栏 + 液态玻璃」，而是整套 MIUI 风格组件库（ui / icons / preference / nav / blur）。
> 「模糊 / 悬浮底栏 / 液态玻璃 / 导航栏角标 / 彩虹滑块 / 预见性返回 / 界面缩放」都是**独立可开关的解耦子功能**，
> 与「风格（miuix / material）」完全正交 —— material 风格下同样可独立开启这些效果。

- **双轨风格切换**：`StyleTheme`（MIUI_X / Material），根主题按 `isLiquidGlass` 分发到 `MiuixTheme` / `MaterialTheme`，两套配色由同一 ColorScheme（material-kolor 动态生成）派生，切换无缝
- **配色引擎**：`ColorMode`（跟随/浅色/深色 + Monet 动态壁纸 + OLED）+ `ColorTheme`（17 个主色 seed）+ 调色板风格 + colorSpec，实时动态生成 Material3 配色
- **桌面式横向滑动切页**：`HomeScreen` 用 `HorizontalPager` 横向拼接 5 个 tab，直接在页面左右拖动切页，与底栏选中态双向同步
- **子功能独立开关**：`FeatureLocals` 7 个独立 CompositionLocal（毛玻璃 / 浮底栏 / 液态玻璃 / 导航角标 / **彩虹滑块** / 预见返回 / 页面缩放），每项独立持久化
- **选态滑块**：普通滑块 `graphicsLayer` 放大渲染半径 + 大圆角，标签保持紧凑不折行；可选**彩虹渐变填充**（水平七色，亮底自动切深色前景）
- **设置页主题二级页**：配色风格卡片 + 「显示效果」子功能开关组，逐级返回上级

---

## 🧭 功能状态速览

| 模块 | 状态 |
|---|---|
| 权限模式体系 | ✅ 完成 |
| Root 检测（KSU/SukiSU/Magisk 分流） | ✅ 完成 |
| Shizuku 集成（官方 `rikka.shizuku`） | ✅ 完成 |
| 应用管理（挂起/隐藏 + 打开/详情/卸载） | ✅ 完成核心 |
| 已冻结实时扫描（去本地冻结记录） | ✅ 完成 |
| 监控台（CPU频率 / SoC / 温度 / 内存Swap / 进程 / 电源 / GPU+网络） | ✅ 完成核心 |
| 电源监控（电压/电流/功率/双电芯） | 🚧 核心完成，测量校准待收尾 |
| 设置页（模式 / 主题二级菜单 / 返回上级） | ✅ 完成 |
| 多级页面过渡动画（1/2/3 级水平滑动） | ✅ 完成 |
| 关于页（霓虹 Logo + 版本更新检测 + GitHub 跳转） | ✅ 完成 |
| 沉浸式 edge-to-edge 统一封装（状态栏动态避让 + 滚动穿透） | ✅ 完成 |
| 主题体系（双风格 + 配色引擎 + 7 子功能开关） | ✅ 开关/持久化 + 渲染消费完成（浮底栏/液态玻璃/角标/彩虹滑块），含桌面式横向滑动切页 + 放大渲染半径滑块 |
| 预留性返回手势开关 | ❌ 未修复（见 plan P5） |
| 场景 / 模块管理 | 🚧 规划中 |
| CPU / GPU 调校 | 🚧 规划中 |

详细的开发计划在 [`plan.md`](plan.md)，每次发布的版本更新内容见 [`CHANGELOG.md`](CHANGELOG.md)。

---

## 🛠 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3（`app` 显式 material3 1.5.0-alpha22，与 miuix/material-kolor 对齐防闪退）
- **架构**：单模块 + 原子化目录（`core/` 业务核心，`ui/` 页面视图）
- **数据持久化**：Jetpack DataStore（Preferences）
- **生命周期**：Lifecycle ViewModel / runtime-compose
- **权限增强**：官方 `dev.rikka.shizuku:api` + `core`
- **主题体系**：`top.yukonga.miuix.kmp`（miuix 0.9.4：ui / icons / preference / nav / blur，全套 MIUI 风格组件）+ `com.materialkolor:material-kolor` 5.0.1（Material You 动态配色）
- **隐藏 API 反射**：`org.lsposed.hiddenapibypass:hiddenapibypass`（预留性返回开关）
- **构建**：AGP 9.1.0 / Kotlin 2.4.20 / Gradle 9.5 / compileSdk 37 / Java 17
- **最低系统**：Android 8.0（API 26）

---

## 🔧 构建

```bash
# 编译 Debug
gradle :app:assembleDebug --no-daemon

# Release 已配置签名（release.jks；需先设环境变量，否则输出未签名包）
$env:TB_RELEASE_STORE_PASS = "<你的密码>"
$env:TB_RELEASE_KEY_PASS   = "<你的密码>"
gradle :app:assembleRelease --no-daemon
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Debug 产物：
```
app/build/outputs/apk/debug/app-debug.apk
```
Release 产物（配置了 `signingConfigs.release`，已签名）：
```
app/build/outputs/apk/release/app-release.apk
```

> Release 签名凭据走环境变量 `TB_RELEASE_STORE_PASS` / `TB_RELEASE_KEY_PASS`（keyAlias 兜底 `tinkerbox`），不写入代码仓库；`release.jks` 与 `.keystore` 一样在 `.gitignore` 内，绝不上传。

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
- **ColorOS 冻结语义**：挂起 (suspend) 图标立即置灰；隐藏 (hide) 图标消失
- **ColorOS 智能停用** 会把闲置应用标为 `enabled=false`，造成大量误报；已冻结判定改为**纯实时扫描系统权威标志**（`FLAG_SUSPENDED` + `privateFlags` 最低位），不再以 `enabled` 或本地冻结记录判定
- Android 11+ 需声明 `QUERY_ALL_PACKAGES`，并以 `MATCH_UNINSTALLED_PACKAGES` 读取已停用应用
- **material3 版本对齐（防闪退）**：miuix / material-kolor 传递依赖会把 material3 强制解析到 `1.5.0-alpha22`，`app` 必须显式声明该版本，且 `ExposedDropdownMenuBox` 用新 API `menuAnchor(ExposedDropdownMenuAnchorType.*)`；否则编译通过但运行时报 `NoSuchMethodError` 闪退
- **预留性返回**：运行时改 `ApplicationInfo.setEnableOnBackInvokedCallback` 是否真正产生系统动画仍待验证；该开关已归入主题二级页「显示效果」子功能组（见 plan P5）

---

## 📄 许可证

此项目尚未指定开源许可证，作者保留所有权利。使用前请联系作者确认。

---

📝 项目仍在持续开发中，功能与 API 可能随迭代调整。