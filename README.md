# 花笺 · 大学生活手记 App

本仓库包含「花笺 · 大学生活手记」的网页版和 Android 本地应用。

## 📱 Android APK
- `huajian.apk`：可直接安装到 HarmonyOS / Android 手机
- 支持本地数据存储、导出 JSON / 日历提醒、系统通知
- 安装时如提示未知来源，请允许安装

### 构建 Android 工程
需要 Android SDK、JDK 17、Gradle 8.7。

```bash
cd android
gradle assembleDebug
```

生成的 APK 位于：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## 🌐 网页版
- `web/index.html`：版本选择入口
- `web/花笺手机版.html`：手机版
- `web/花笺电脑版.html`：电脑版

直接打开对应 HTML 即可使用，数据保存在浏览器本地。

## 说明
- 网页版下载受浏览器限制，Android 本地应用可直接保存导出文件。
- 数据默认保存在本机，不会自动跨设备同步。
