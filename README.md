# SC55G Wi‑Fi 守卫

面向 SPEEDATA SC55G（Android 12/13）的 Device Owner 应用。目标是：

- 指定 Wi‑Fi 自动连接；
- 其他 Wi‑Fi 只能由用户从 App 发起“临时手动连接”；
- 临时网络断线、设备重启、会话手动结束、会话超时，或原先不可见的指定 Wi‑Fi 再次出现后，清除其他已保存网络并回到指定 Wi‑Fi；
- Wi‑Fi 密码用 Android Keystore AES‑GCM 加密保存；管理配置由 PIN 保护。

## 重要边界

这是 Android 公共 Device Owner API 能实现的“临时网络 + 会话后删除”方案，不是 ROM 级自动连接开关。Android 没有公开 API 可对任意用户保存网络单独设置“永不自动连接但始终保留”。因此，本应用会在临时会话结束后**删除**非指定网络，防止系统以后自动连接。下次临时使用时可能需要重新输入该网络密码。

SC55G 的定制 ROM 可能改变已弃用但对 Device Owner 明确保留的 `WifiManager` 行为，因此必须先做真机验收。

## 最简单：上传 GitHub 自动生成 APK

项目已经包含 `.github/workflows/build-apk.yml`，无需本地安装 Android Studio：

1. 新建一个 GitHub 私有仓库；
2. 解压项目，把项目内部所有文件上传到仓库根目录，同时保留 `.github` 隐藏目录；
3. 打开 **Actions → Build installable APK**；
4. 等待构建成功，在 **Artifacts** 下载 `SC55G-WifiGuard-debug-运行编号`；
5. 解压后得到可安装的 `SC55G-WifiGuard-v1.0.0-debug.apk` 和 SHA-256 文件。

首次上传以及以后每次提交都会自动执行单元测试、编译、APK 结构检查和校验值生成。详细步骤见 `CLOUD_BUILD.md`。

## 本地构建

要求：JDK 17、Android SDK 35、Gradle 8.9。项目不携带第三方 Gradle 二进制或 wrapper JAR；云端工作流会安装固定版本的 Gradle。

### Android Studio（推荐）

1. 用 Android Studio 打开项目根目录。
2. 在 SDK Manager 安装 Android SDK Platform 35。
3. 等待 Gradle Sync 完成。
4. 先运行单元测试，再选择 **Build → Build APK(s)**。

### 命令行

```bash
gradle test assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。量产时请使用公司保管的正式签名证书生成 release APK，不要分发 debug 签名版本。

## 首次部署（会清空设备）

1. 恢复出厂设置。首次开机时不要添加 Google/企业账号，也不要先装其他 MDM。
2. 开启开发者选项和 USB 调试。
3. 安装 APK：

```bash
adb install -r app-debug.apk
```

4. 设置为 Device Owner：

```bash
adb shell dpm set-device-owner com.sc55g.wifiguard/.admin.WifiDeviceAdminReceiver
```

5. 启动 App，授予“附近 Wi‑Fi/位置/通知”权限。
6. 输入指定 SSID、安全类型、密码和至少 6 位管理员 PIN，打开策略并保存。

检查状态：

```bash
adb shell dpm list-owners
adb shell dumpsys device_policy | grep -A 8 "Device Owner"
```

如果 `set-device-owner` 报已有账号、已完成设置或已有管理员，请再次恢复出厂后从第 2 步开始。

## 验收测试

1. **开机恢复**：保存一个其他 Wi‑Fi 后重启；设备应删除该配置并连接指定 Wi‑Fi。
2. **临时连接**：先离开指定 Wi‑Fi 覆盖，App 内点“临时手动连接”，连接热点；热点应维持连接。
3. **目标恢复**：让指定 Wi‑Fi 再次可见；30 秒内应切回并删除热点配置。
4. **断线清理**：临时热点关闭后，应清理热点配置并尝试指定网络。
5. **同区手动例外**：若开始临时会话时指定 Wi‑Fi 已可见，App 不会立刻抢回；点击“结束临时连接”或等待 4 小时后恢复策略。
6. **权限/ROM**：若状态提示“ROM 拒绝”，导出 `adb logcat -s WifiPolicyController` 供适配。

## 安全与生产建议

- 当前为首版，可用于单机/小批量试点；量产前请更换正式包名、签名证书并保管 keystore。
- 不应把正式 Wi‑Fi 密码写入源代码或 ADB 脚本。
- 生产环境建议把 APK 加入 SPEEDATA 系统白名单，关闭电池优化，并在 5–10 台不同 ROM build 上做 48 小时漫游/重启测试。
- 如公司已有 Workspace ONE 等 MDM，必须确认没有第二个 Device Owner；Android 同一设备只能有一个 Device Owner。
