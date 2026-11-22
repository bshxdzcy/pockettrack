# PocketTrack

当前版本：Android 0.1.2 / Mac 0.1.1 开发原型。

把 USB 连接的 Android 手机用作 Mac 触控板。原生 Android + Swift，无第三方运行时依赖。MIT 开源。

**当前是开发原型。纯黑模式不是真正息屏：显示窗口保持活动，画面全黑、亮度降至最低，触摸继续工作。** 真正关屏后的触摸取决于手机硬件、驱动与系统权限，本项目不声称已实现。

## 使用方式

1. 用 USB 连接手机，在手机上允许这台 Mac 的 USB 调试。
2. 编译两端，执行 `python3 scripts/connect-usb.py`。
3. Mac 弹出 PocketTrack 窗口，在系统设置的「隐私与安全性 → 辅助功能」中允许 PocketTrack。
4. 手机点击「开始触控」或「开始纯黑触控」。
5. 单指移动、轻点左键、双指滚动、双指轻点右键、单指停住约半秒后拖动。
6. 按一下手机电源键即停止控制并正常锁屏。切后台、返回、断线也会停止。重新解锁不会自动恢复控制。

Mac 的实体 Option 键照常使用。手机默认启用「音量上键代替右 Option」：按下发送右 Option（键码 61）按下，松开发送释放。它是否能唤起豆包取决于豆包当前的快捷键配置及其是否接收合成修饰键事件，需要在你的机器上验证。本项目不修改豆包设置。

语音采集由现有 Mac 豆包负责，手机端不录音。站得离 Mac 很远时，Mac 麦克风的收音距离仍是限制。

## 构建

需要 macOS 13+、Xcode Command Line Tools、JDK 17+、Android SDK Platform 36 / Build Tools 36.0.0、ADB。手机需要 Android 8+（原型面向已连接 Android 16 设备验证）。

```bash
export JAVA_HOME=/path/to/jdk/Contents/Home
export ANDROID_HOME=/path/to/android-sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./scripts/build-android.sh
./scripts/build-mac.sh
python3 scripts/connect-usb.py
```

ADB 不在 PATH 时可设置 `ADB=/path/to/adb`。连接多部手机时显式设置 `ANDROID_SERIAL`。构建输出位于 `build/`。本地 APK 使用开发签名；Mac 应用为临时签名，尚未公证，不代表正式发布版本。

### 小米等设备拦截 USB 安装时

如果出现 `INSTALL_FAILED_USER_RESTRICTED`，可以手动安装，不必绕过系统限制：

```bash
adb push build/PocketTrack.apk /sdcard/Download/PocketTrack-v0.1.2.apk
# 在手机文件管理器的 Download / 下载目录点开 APK，按系统提示安装
python3 scripts/connect-usb.py --skip-install
```

若 Mac 设置里已开启辅助功能，但应用仍显示未授权，先退出并重开应用；开发版重新编译后可能需要在辅助功能列表移除旧项，再添加当前 `build/PocketTrack.app`。

## 停止与卸载

```bash
./scripts/disconnect-usb.sh
adb uninstall dev.pockettrack  # 只删除本应用及其配对信息
```

退出 Mac 应用并删除 `build/PocketTrack.app` 即可删除接收端。Mac 配对密钥位于 `~/Library/Application Support/PocketTrack/pairing-token`。脚本只移除本项目的 49671 转发，不影响其他 ADB 项目。

## 隐私与连接

- APK 仅声明 `INTERNET` 普通权限，用于经 `adb reverse` 建立本地 TCP 连接；没有敏感运行时权限。
- 不请求手机麦克风、相册、相机、联系人、短信、通知、悬浮窗、无障碍或存储权限。
- 不读取手机剪贴板、不同步内容、不录制屏幕、不上传遥测。
- Mac 接收端只绑定 `127.0.0.1:49671`，不向局域网开放。
- 256 位随机配对密钥；手机端存放于应用私有设置，关闭备份；Mac 密钥文件权限为 0600。
- 仅接收固定的鼠标/Option 命令，无远程 shell、任意按键或文件接口。
- 断线与心跳超时释放本应用模拟的拖动和 Option 状态；手机退出模式后不会自动重连。
- USB 传输没有另加 TLS，依赖本机/已授权 ADB 通道；不要把端口对外暴露。

## 验证

`tests/run-mac-tests.py` 在独立端口启动无界面的测试接收端，不注入鼠标或键盘输入。协议测试要求 `OK DRY` 握手，防止误向正常接收端发送测试输入。

```bash
python3 tests/run-mac-tests.py
mkdir -p build/java-tests
"$JAVA_HOME/bin/javac" -d build/java-tests android/src/dev/pockettrack/MotionBatch.java tests/MotionBatchTest.java android/src/dev/pockettrack/MotionDynamics.java tests/MotionDynamicsTest.java
"$JAVA_HOME/bin/java" -cp build/java-tests MotionBatchTest
"$JAVA_HOME/bin/java" -cp build/java-tests MotionDynamicsTest
```

具体已验证内容和未验证边界见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。

## 0.1.2 手感调节（仅更新 Android）

手机设置页可分别调节鼠标和双指滚动速度，设置保存在应用私有数据中。默认鼠标基础倍率 4，随滑动速度平滑加速，最大约 11.2；默认滚动倍率 6。开启惯性滚动后松手继续减速滚动，再次触摸、按音量键或退出模式会停止惯性。

应用窗口请求最高 120 Hz 刷新偏好，是否生效由设备决定。保持系统触摸批处理与重采样，不强制使用可能引入抖动的无缓冲输入模式。

开发原型每两秒输出一次 `PocketTrackPerf` 性能日志，只包含心跳 RTT、队列长度、触摸分发耗时、socket 写入耗时与批次数，不包含坐标、按键值或用户内容。诊断时只读取本应用 PID 的该日志标签。

## 0.1.1 触控修复

- 连续移动累计目标坐标，避免一批事件重复读取旧光标位置而丢位移。
- 权限及显示器信息定时刷新，不再在每条移动命令上同步查询。
- 心跳接收独立于发送线程，双端关闭 Nagle 延迟，合并相邻移动命令但保持点击和按键顺序。
- 保留小数滚动量，避免微小的横向滚动被截断为零。
- 手机正常触控界面显示 USB 心跳往返时间；它不是包含显示刷新在内的端到端触控延迟。
- 音量上键发送右 Option，电源键/后台/断线退出逻辑保留。

## 类似开源项目

调研过 [Beckon](https://github.com/omsingh02/beckon)、[Sidecast](https://github.com/achreftlili/sidecast) 和 [KDE Connect](https://github.com/KDE/kdeconnect-kde)。它们覆盖无线触控、显示扩展或设备互联等方向。本项目为了 USB、最小手机权限和电源键退出这条流程独立实现，没有复制上述项目代码，也不是它们的分支。

Android [保持屏幕活动](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on) 和 [交互状态说明](https://developer.android.com/reference/android/os/PowerManager#isInteractive()) 解释了黑画面和系统休眠的区别。[scrcpy 的显示控制](https://github.com/Genymobile/scrcpy/blob/master/doc/device.md) 可在部分设备上控制屏幕电源，但并不保证手机自身触摸仍可用。
