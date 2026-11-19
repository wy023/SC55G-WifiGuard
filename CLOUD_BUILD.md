# 上传后自动云编译 APK

本项目已配置 GitHub Actions。无需在本地安装 Android Studio，也不需要填写任何密码或 Secret；把项目上传到 GitHub 后，每次提交都会自动测试并生成一个**已使用 Android debug 证书签名、可直接安装**的 APK。

## 方法一：网页上传

1. 在 GitHub 新建一个 **Private repository**，不要勾选自动创建 README、`.gitignore` 或 License。
2. 解压项目压缩包。
3. 将 `SC55G-WifiGuard` 文件夹**里面的全部内容**上传到仓库根目录。必须保留隐藏目录 `.github/workflows/`。
4. 打开仓库的 **Actions** 页面。如果 GitHub 要求启用 Actions，点击启用。
5. 选择 **Build installable APK**。首次上传会自动触发；也可以点击 **Run workflow** 手动触发。
6. 构建成功后进入该次运行，在页面底部 **Artifacts** 下载 `SC55G-WifiGuard-debug-运行编号`。
7. 解压 Artifact，得到：
   - `SC55G-WifiGuard-v1.0.0-debug.apk`
   - `SC55G-WifiGuard-v1.0.0-debug.apk.sha256`

> 不要只把 ZIP 文件本身放进仓库。GitHub Actions 需要看到仓库根目录中的 `settings.gradle.kts` 和 `.github/workflows/build-apk.yml`。

## 方法二：Git 命令上传

```bash
git init
git add .
git commit -m "Initial SC55G Wi-Fi Guard cloud build"
git branch -M main
git remote add origin https://github.com/你的账号/你的仓库.git
git push -u origin main
```

推送完成后，到 GitHub 的 **Actions** 页面下载构建产物。

## 云端实际执行内容

构建环境固定为：

- Ubuntu 24.04
- JDK 17
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Android SDK Platform 35、Build Tools 35.0.0 和 Platform Tools（工作流显式安装）

执行命令：

```bash
bash scripts/cloud-build.sh
```

脚本会依次执行：

```text
clean → testDebugUnitTest → assembleDebug → APK ZIP 完整性检查
→ 检查 AndroidManifest.xml → 检查 classes.dex → 生成 SHA-256
```

任何一步失败，工作流都会显示红色并且不会发布 APK Artifact。

## APK 安装及 Device Owner 配置

下载并解压 Artifact 后：

```bash
adb install -r SC55G-WifiGuard-v1.0.0-debug.apk
adb shell dpm set-device-owner \
  com.sc55g.wifiguard/.admin.WifiDeviceAdminReceiver
adb shell dpm list-owners
```

`set-device-owner` 要求设备已恢复出厂，并且尚未添加账号或其他设备管理程序。

## 安全说明

- 云构建生成的是 **debug APK**，适合 SC55G 试点和真机验证。
- Wi-Fi SSID、密码和管理员 PIN 不在源码或云构建配置中，应在安装后通过 App 输入。
- GitHub Artifact 默认保留 30 天。
- 量产发布应改用企业私有签名证书；不要把 `.jks` 文件或签名密码提交到仓库。
