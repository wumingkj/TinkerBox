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
- [x] App 图标（SVG → 自适应图标：前景 + 深蓝渐变背景）
- [x] 无线安装 / 覆盖安装到 192.168.1.143:5555 验证
- [x] **AGP 9.1.0 + Kotlin 2.4.20 + Gradle 9.5 + compileSdk 37 迁移**（今日）
  - AGP 9 硬依赖 compileSdk 37（Cinnamon Bun Preview）+ build-tools 36.0.0 + Kotlin 2.4.20
  - 升级 cmdline-tools 至 23.0（新版 `android sdk` CLI）才能安装 `platforms;android-37.0`
  - AGP 9 强制 edge-to-edge：内容容器加 `.safeDrawingPadding()`、底栏加 `.navigationBarsPadding()` 规避顶栏被覆盖/底部缺块
  - 清理 `C:\Android\Sdk` 历史残留（旧 cmdline-tools 12.0、旧 build-tools 34/35、旧 platforms 34/35/36），占用从 ~1.3GB 降至 444MB

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
- [x] 冻结模式精简为「挂起(suspend) / 隐藏(hide)」两项（移除停用/禁用，避免与 ColorOS 智能停用冲突）
- [x] 行操作升级为三点下拉菜单 `AppActionsMenu`：**打开 / 详情 / 卸载**（普通权限也可执行，卸载跳系统界面）+（具冻结能力时）解冻 / 挂起 / 隐藏
- [x] 一键解冻、卸载静默（root 能力时）
- [x] **已冻结完全改为实时扫描**（今日）：移除本地 `FrozenRepository` 持久化记录
  - 冻结判定仅依赖系统权威标志：`FLAG_SUSPENDED`（挂起）+ `privateFlags` 最低位（隐藏）
  - 不再用 `enabled==false` 判定冻结，规避 ColorOS 智能停用闲置应用误报
- [x] 普通模式只读列表（冻结按钮灰），Shizuku / Root 模式可实际冻结；打开/详情/卸载普通模式即可用
- [x] 权限按当前模式（普通 / Shizuku / Root）自动灰度：挂起/隐藏需授权，打开/详情/卸载始终可用
- [x] 命令日志与 logcat 双写（`CommandLog`），日志界面实时展示
- [x] Root 模式下 `pm` 命令执行（`AppFreezer`，含静默卸载 `pm uninstall`）

### Shizuku 集成
- [x] 接入官方库 `dev.rikka.shizuku:api` + `core`
- [x] Manifest 注册 `ShizukuProvider`
- [x] `ShizukuManager`：连接状态 / 权限检查与请求 / 授权监听
- [x] 授权成功后走 Shizuku binder 执行 shell（`AppFreezer.forShizuku()`）

### 设置页（部分完成）
- [x] 运行模式切换（普通 / Shizuku / Root），即时生效并持久化
- [x] 当前能力状态展示、关于占位
- [x] **主题改为二级菜单**（今日）：主界面仅显示「主题设置」入口，点击进入二级页（`mutableStateListOf<SettingsSubPage>` 页内导航栈）
- [x] **多级界面返回上级**（今日）：`BackHandler(enabled = stack.isNotEmpty())` 拦截系统返回键，非空时 pop 栈顶而非退出 Activity；二级/三级界面逐级返回上级
- [x] **底部导航返回策略**（今日）：主页非监控台 tab 时按返回先切回监控台，再按才退出（`HomeScreen` 的 `BackHandler`）
- [x] **切换模式自动获取权限**（今日）：`ModeViewModel.selectMode` 在权限未就绪时自动发起授权而非直接退级
  - Root → 自动触发一次 `su` 授权弹窗（KSU / Magisk 管理器），轮询等待授权结果
  - Shizuku → 服务未启动且持有 root 时先用 root 执行官方 `start.sh` 自启服务，就绪后自动弹出系统授权框
  - 轮询约 12s 内授权成功则提交并持久化，否则自动退级到始终可用的普通模式（无需手动去 root / Shizuku 应用里点授权）

### 主题体系重构（今日，对齐 SukiSU 解耦架构）
> **miuix 概念澄清**：miuix ≠「胶囊底栏 + 液态玻璃」，而是整套 MIUI 风格组件库（ui/icons/preference/nav/blur）。
> 「模糊 / 悬浮底栏 / 液态玻璃 / 导航栏角标 / 彩虹滑块 / 预见性返回 / 界面缩放」都是**独立可开关的解耦子功能**，
> 与「风格（miuix / material）」完全正交 —— material 风格下同样可独立开启这些效果，二者并非附属关系。

- [x] **三层正交解耦模块**：
  - 配色 `AppThemeState`（明暗 + 主色 seed + 调色板风格 + colorSpec），经 `ThemeController`（material-kolor 5.0.1）动态生成 Material3 ColorScheme
  - 皮肤 `StyleTheme`（MIUI_X / MATERIAL），根组合 `TinkerBoxTheme` 按 `isLiquidGlass` 分发到 `MiuixTheme` / `MaterialTheme`，两套配色由同一 ColorScheme 派生，切换无缝
  - 子功能 `FeatureLocals`（7 个独立 `staticCompositionLocalOf`：blur / 浮底栏 / 液态玻璃 / 导航角标 / 预见返回 / 彩虹滑块 / 页面缩放），`TinkerBoxApp` 每项独立 `StateFlow` + 独立持久化键 + 独立 setter
- [x] **`ColorMode`**（取代旧 ThemeMode）：跟随/浅色/深色 + Monet(动态壁纸) + OLED，7 种正交组合
- [x] **`ColorTheme`**：17 个预设主色 seed（星海蓝默认 + 系统取色色系），不再硬编码明暗 primary/secondary
- [x] **`MiuixThemeController`**：把 `AppThemeState` 映射为 miuix `ThemeController`，保证 miuix/material 双轨色彩一致
- [x] **设置页主题二级页根目录**：配色风格卡片（主题模式/风格/主色下拉）+「显示效果」子功能开关组（毛玻璃/悬浮底栏/液态玻璃/导航角标/彩虹滑块/预见返回/界面缩放滑块）

- [x] **miuix 全套组件依赖接入**（今日）：`miuix-ui` / `miuix-icons` / `miuix-preference` / `miuix-nav` / `miuix-blur`（0.9.4）+ `material-kolor` 5.0.1
- [x] **设置页闪退修复**（今日）：`NoSuchMethodError: ExposedDropdownMenuBox` 根因是 miuix/material-kolor 传递依赖把 material3 强制解析到 `1.5.0-alpha22`（经 `material3-window-size-class` atomic group 约束），app 代码仍用 1.3.1 老 API。修复 = 显式升 material3 1.5.0-alpha22 并对齐代码 `menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)`（javap 反查确认枚举），重建 `:app:compileDebugKotlin` / `:app:assembleDebug` 通过

### 底部导航 / 滑块（今日迭代）
> 依据「桌面式横向拼接」构想，进一步重构底部导航体验与选态滑块渲染。

- [x] **页面切换改为桌面式横向滑动**（`HomeScreen` + `HorizontalPager` + `rememberPagerState`）：5 个 tab 页面横向拼接，直接在页面左右拖动即可切页，与底栏双向同步（滑动→更新底栏选中态，点底栏→`animateScrollToPage`），返回键先切回监控台
- [x] **移除液态水滴镜头**（`GlassNavigationBar`）：删除 `WaterDropLensOverlay` 及其阴影 / 高光 / 凸面造型与横向拖拽手势，回归纯胶囊 + 普通选态滑块
- [x] **普通选态滑块渲染半径放大**：item 保持紧凑内边距 `8×4`（标签不折行），选中滑块 `matchParentSize()` 垫底 + `graphicsLayer` 绘制放大 + 大圆角（悬浮 26dp / 贴底 14dp），稳定不挤压标签、不变更布局约束
- [x] **彩虹滑块子功能**（新独立开关，`LocalEnableRainbowSlider`）：开启后普通滑块用水平七色 `Brush.horizontalGradient` 填充（彩虹亮底时前景自动切深色保证可读），关闭则回退主题主色；与风格正交，MIUI_X / material 均可独立开启
- [x] **修复滑块负内边距崩闪退**：`matchParentSize()` + 负 `padding()` 会超出父容器约束，布局阶段抛异常；关闭液态玻璃即触发且已持久化导致重启进不去。改为 `graphicsLayer` 缩放放大，彻底规避

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
- [x] **GPU 负载 + 网络流量监控卡片**（今日）：
  - `GpuMonitor`：GPU 当前负载率与频率（sysfs 直读优先，SELinux 受限时走 `execRaw` 特权执行器兜底）
  - **GPU 静态属性 = 公开 API 读取**（今日 `GpuInfoReader`）：离屏 EGL context + `glGetString(GL_RENDERER/GL_VENDOR/GL_VERSION)` 读型号/厂商/GL 版本，`DisplayMetrics` + `Display.getRefreshRate()` 读分辨率/刷新率（取整）。**普通模式即可见，对齐 AIDA64 无 root 效果**；结果只读一次缓存，避免每秒重复开 EGL
  - **实时频率单位修复**（今日）：kgsl `gpuclk` 多数设备返回 kHz，但骁龙 8Gen2 直接返回 Hz（220000000）曾被当 kHz 爆成 220000MHz；按量级启发式（≥1e6 视为 Hz→kHz）归一，`min/max_gpuclk` 同理
  - `NetMonitor`：下行 / 上行实时速率 + 累计流量（`TrafficStats` 累计计数，普通模式即可读，规避 ColorOS/A15 对 `/proc/net/dev` 的 SELinux 拦截）
  - 网络卡片两列布局修复：累计接收 / 发送改为 `NetMetric` 列式组件（此前嵌套两个 `fillMaxWidth` 的 `InfoRow` 在 Row 内相互抢宽而挤堆）
- [x] **数值单位动态进位**（今日）：字节统一 `fmtBytes`（B→KB→MB→GB→TB→PB→EB）；电流超 1024mA 进位到 A——内存 / Swap / 网络总量、速率、电流均按量级选档

### 电源监控（P1，已完成核心）
- [x] 电池电量 / 电压 / 电流 / 功率卡片（优先 BatteryManager 框架 API）
- [x] 电流与功率修正（此前普通模式显示 -0.8/0ma、功率 -- 异常）：
  - 电流单位 µA↔A 换算修正，双电芯串联场景读数
  - 存在微小电流漂移，lsp 模块与 app 读数存在差异（-800ma vs -0.8a）——测量链路仍在对照校准中
- [x] 双电芯识别与显示：串联双电芯时展示电芯 0 / 电芯 1 两个独立电压（对齐工程模式）。
  - ⚠️ 部分读数（电流瞬时值 / 功率）在普通模式与 root 模式间仍存在偏差与滞后问题，测量校准待收尾

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
- [x] ~~主题切换（深色 / 浅色 / 跟随系统）~~ → 已升级为完整主题体系（明暗 + Monet + OLED + 双风格 + 主色 + 调色板），见「主题体系重构」
- [x] **子功能实际渲染消费**：浮底栏 / 液态玻璃（`isGlass` 决定性渲染） / 导航角标 / 彩虹滑块 已全部消费到底栏组件；页面缩放 / 预见返回 仍待对应组件消费
- [x] **关于页完善（今日）**：霓虹动态 Logo + App 名称 + 版本号 + 「检测 App 版本更新」（拉取 GitHub Release latest 比较）/「去 GitHub 查看源码」/ 占位卡片
- [x] **多级页面过渡动画（今日）**：1/2/3 级页 `AnimatedContent` 水平滑入/滑出，消除硬切硬退；二级页全屏隐藏底栏
- [x] **沉浸式 edge-to-edge 统一封装（今日）**：`EdgeToEdgeScrollColumn` / `EdgeToEdgeLazyColumn` 动态读取状态栏/手势条高度，顶部紧贴、滚动穿透透明状态栏；统一各界面状态栏避让，消除「写死/离顶远」差异
- [x] **监控台卡顿根治（今日）**：`LazyColumn` 惰性组合 + 进程行按 pid 重组 key + 惰性嵌套（外卡片/内进程行）+ GPU 动画收敛（600ms→150ms）+ 轮询绑定页面可见性切走即停
- [ ] 液态玻璃底栏视觉细化（`isGlass` 半透明的胶囊玻璃感已接入 miuix-blur，待进一步的折射 / 高光打磨；液滴镜头已按需求移除）

### P5｜预留性返回手势（未修复）
- [ ] **预留性返回开关无法真正产生系统预测性返回动画**（今日实测无效）
  - 已实现：设置开关 + 运行时反射 `ApplicationInfo.setEnableOnBackInvokedCallback`（HiddenApiBypass + Application.onCreate 同步时序，对齐 SukiSU `KernelSUApplication`）
  - 现象：logcat 显示豁免与反射调用均成功，但真机无动画；修改的是当前进程的 ApplicationInfo 副本，系统 Activity 启动时已缓存旧 flags，运行时反射对该对象无效
  - 待验证方向：
    - 目标设备 SDK 35 预测返回仍需系统开发者选项门控？→ 用户实测开发者选项关闭后 SukiSU 仍有效，故排除
    - 对照 SukiSU：其 `setEnableOnBackInvokedCallback(context.applicationInfo, ...)` 在 `ColorPaletteScreen` 切换时也调用，说明可运行时生效；需核实本工程 Activity 是否使用 `OnBackInvokedDispatcher`/Compose `BackHandler` 与系统 flag 协调
    - 试验方案：改为 Manifest 静态 `android:enableOnBackInvokedCallback="true"` 验证动画是否出现，反向定位是 flag 未生效还是系统动画门控问题

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

### Release 签名（今日新增）
- 已生成根目录 `release.jks`（keytool：RSA 4096 / validity 10000 / alias `tinkerbox`），**绝不上传 Git**。
- `app/build.gradle.kts` 新增 `signingConfigs.release`：`storePassword`/`keyPassword` 从环境变量 `TB_RELEASE_STORE_PASS`/`TB_RELEASE_KEY_PASS` 读取，不走仓库明文；`keyAlias` 兜底 `tinkerbox`。`release{}` 已引用该签名，`assembleRelease` 直接产出 `app-release.apk`（不再 unsigned）。
- 构建前须设环境变量，否则 storePassword 为空 → 仍产出未签名包：

```bash
# PowerShell 一次性设置后构建
$env:TB_RELEASE_STORE_PASS = "<你的密码>"
$env:TB_RELEASE_KEY_PASS   = "<你的密码>"
gradle :app:assembleRelease --no-daemon
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

---

## 五、已知约定 & 坑

- Root 检测**不能**靠 `File.exists()` 探测 `/data/adb`（普通 App 无权限读该目录，必假）。
- SukiSU 是 KSU 分支，`RootBackend` 不单独建分支，沿用 KernelSU 处理。
- **ColorOS 冻结语义**：挂起(suspend) 图标立即变灰；隐藏(hide) 图标消失。
- **ColorOS 智能停用**：系统会把长期未打开的闲置应用标记为 `enabled=false`，造成大量误报。已冻结判定改为**纯实时扫描系统权威标志**（`FLAG_SUSPENDED` + `privateFlags` 最低位），不再以 `enabled` 或本地冻结记录判定，天然规避智能停用误报。
- **包可见性**：Android 11+ 需声明 `QUERY_ALL_PACKAGES`，且 `getInstalledApplications` 用 `MATCH_UNINSTALLED_PACKAGES`（与 Hail 一致），既读回被停用应用又不过度拉取系统深层组件。
- 内置 `kotlinx-serialization` 等按需添加；Compose BOM 用 2024.12.01。
- 数据只在需要时持久化；模式与冻结记录存 DataStore，高阶调优配置后续存入同源。
- **miuix 0.9.4 依赖**：`miuix-blur` / `miuix-ui` / `miuix-icons` / `miuix-preference` / `miuix-nav` 声明 minSdk 33，比项目 minSdk 26 高；用 `<uses-sdk tools:overrideLibrary="top.yukonga.miuix.kmp.miuix-blur-android"/>`（及 ui/icons/preference/nav 各 -android 模块）覆盖强制兼容。
- **material3 必须与 miuix 对齐（防闪退）**：miuix/material-kolor 的传递依赖会把 material3 强制解析到 `1.5.0-alpha22`；若 app 代码仍写旧 `menuAnchor()` 无参 API，编译通过但运行时报 `NoSuchMethodError`（设置页闪退）。必须 `libs.versions.toml` 显式声明 `material3 = 1.5.0-alpha22`，并用新 API `menuAnchor(ExposedDropdownMenuAnchorType.*)`。
- **material-kolor 5.0.1**：Monet 化调色板仅 `TonalSpot/Neutral/Vibrant/Expressive` 支持 Spec2025，其余风格回退 Spec2021（见 `MiuixThemeController.effectiveFor`）。
- **AGP 9 / compileSdk 37**：`gradle.properties` 加 `android.suppressUnsupportedCompileSdk=37.0` 消除未测试警告；Kotlin 必须 ≥ 2.4.20 才能读取 AGP 9 内置 stdlib 2.4.x 元数据。
- **预留性返回**：运行时改 `ApplicationInfo.setEnableOnBackInvokedCallback` 仅改当前进程副本，Activity 已按启动时 flags 建立返回调度器，动态切换是否生效待验证（见 P5）。