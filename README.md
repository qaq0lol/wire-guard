# WireOpen Proxy (WireGuard & OpenVPN 双协议安卓代理软件)

[![Android 16 Ready](https://img.shields.io/badge/Android%2016+-API%2036-brightgreen)](https://developer.android.com)
[![WireGuard](https://img.shields.io/badge/Protocol-WireGuard-red)](https://www.wireguard.com/)
[![OpenVPN](https://img.shields.io/badge/Protocol-OpenVPN-orange)](https://openvpn.net/)
[![ippure.com](https://img.shields.io/badge/IP%20Pureness-ippure.com-blue)](https://ippure.com/)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://www.oracle.com/)

一款原生适配 **Android 16+ (API Level 36 - Baklava)**，同时向下兼容的主流 VPN 代理客户端。支持直接导入并无缝切换 **WireGuard (`.conf`)** 与 **OpenVPN (`.ovpn`)** 配置文件，提供低延迟隧道转发、网络分流、前台监控、实时流量统计，并深度集成 **Material 3 全面深浅色自适应** 与 **https://ippure.com/ IP 纯净度检测**。

---

## 🌟 核心特性

1. **双协议无缝兼容**：
   - **WireGuard**：完整支持标准 INI 格式 `.conf` 文件，包含 `[Interface]`（私钥、客户端 IP、DNS、MTU）与 `[Peer]`（公钥、预共享密钥、Endpoint 节点、AllowedIPs 全局/局部路由、Keepalive 保活）。
   - **OpenVPN**：完整支持标准 `.ovpn` 格式文件，包含内嵌证书标签（`<ca>`, `<cert>`, `<key>`, `<tls-auth>`, `<tls-crypt>`）及核心指令（`remote`, `proto udp/tcp`, `dev tun`, `cipher`, `auth`, `redirect-gateway`, `dhcp-option DNS`）。
2. **全面适配 Android 16+ (API 36)**：
   - 适配 Android 16 及以上最新的前台服务权限策略（`android:foregroundServiceType="specialUse"` 与 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`）。
   - 适配 Android 13+ / 16+ 严格通知权限（`POST_NOTIFICATIONS`）及低延迟通道通知。
   - 兼容 Android 16 的 16KB 内存页大小架构与 Edge-to-Edge 边到边沉浸式显示。
3. **Material 3 深度深浅色自适应 (Day & Night)**：
   - 系统级语义色彩：在 `values/colors.xml`（浅色模式）与 `values-night/colors.xml`（深色模式）中定义了系统级语义色盘，避免任何硬编码深色导致浅色模式发灰或对比度不良。
   - 主界面支持一键自由切换“☀️ 浅色 / 🌙 深色”模式，所有卡片、边框、输入框与状态指示自适应流转，符合 Material 3 现代规范。
4. **深度集成 ippure.com IP 纯净度与欺诈风险评估**：
   - 实时调用 `https://my.ippure.com/v1/info` 权威 API 进行出口 IP 质量探测。
   - 实时呈现当前出口 IP、国旗归属地、时区、ISP 运营商与 ASN。
   - 评估并展示欺诈风险得分（Fraud Score 0-100，低分优质绿/橙/红三色预警）以及 IP 属性（原生住宅 IP / 优质机房 IP / 原生广播）。
   - 提供“一键测验 IP 纯净度”与“直达 ippure.com 查看完整分析报告”便捷入口，并支持在 VPN 隧道成功建立后自动触发纯净度探测。
5. **实时监控与控制台**：
   - 快速连接开关：一键启动/断开 VPN 隧道。
   - 实时状态监控：显示上行/下行流量统计（KB/MB）、连接耗时与状态指示灯。
   - 运行日志控制台：内置轻量级实时日志控制台，方便排查连接与检测事件。

---

## 📁 目录结构

```text
代理软件/
├── WireOpenProxy-debug.apk               # 自动打包构建的最新 Debug 安装包
├── build.gradle.kts                      # 根工程构建脚本 (AGP 8.9.1, Gradle 8.11.1)
├── settings.gradle.kts                   # 仓库与模块配置
├── gradle.properties                     # JVM 与 AndroidX 配置
├── app/
│   ├── build.gradle.kts                  # compileSdk 36, targetSdk 36 (Android 16+)
│   ├── proguard-rules.pro                # 代码混淆保护规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # VpnService 声明与权限申请
│       │   ├── java/com/proxy/wireopen/
│       │   │   ├── MainActivity.java     # 主界面交互控制（深浅色切换、ippure.com 测验）
│       │   │   ├── model/                # 核心数据模型
│       │   │   ├── parser/               # 配置文件解析器
│       │   │   ├── service/              # Android VpnService 与网络探测
│       │   │   ├── engine/               # 协议引擎与底层套接字转发
│       │   │   ├── repository/           # 配置持久化与存储管理
│       │   │   └── util/
│       │   └── res/                      # UI 布局、配色与样式
│       └── test/java/com/proxy/wireopen/ # 单元测试套件
```

---

## 🛠️ 编译与构建

### 方式 1：使用 Android Studio 直接打开
1. 启动 **Android Studio**。
2. 选择 `Open` 打开本目录。
3. 等待 Gradle 同步依赖完成，点击 `Run 'app'` 即可部署至 Android 16 模拟器或真机。

### 方式 2：使用 Gradle 命令行构建与测试
```bash
./gradlew test
./gradlew assembleDebug
```

GitHub Actions 会在推送到 `main` 或 `fix/**` 分支及 Pull Request 时运行单元测试并构建 Debug APK。APK 以 Actions Artifact 形式上传，在对应的工作流运行页面下载。


<!-- APK build workflow trigger -->
