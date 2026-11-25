# PocketTrack

当前版本：Android 0.3.1 / Mac USB 接收器 0.1.1 / Mac 文字接收器 0.3.0 开发原型。

把 USB 或蓝牙连接的 Android 手机用作 Mac 触控板。原生 Android + Swift，无第三方运行时依赖。MIT 开源。

**当前是开发原型。纯黑模式不是真正息屏：显示窗口保持活动，画面全黑、亮度降至最低，触摸继续工作。** 真正关屏后的触摸取决于手机硬件、驱动与系统权限，本项目不声称已实现。

## 使用方式

### 蓝牙（Android 9+，不需要 Mac 接收程序）

1. 安装 Android APK，打开 PocketTrack，保持「使用蓝牙」开启，点击「连接蓝牙 Mac」。
2. 允许「附近设备」，按系统提示开启蓝牙。选择已配对的 Mac，或「配对新的 Mac」。
3. 首次配对时，允许手机被发现 120 秒；在 Mac「系统设置 → 蓝牙」连接这部手机，确认两端配对提示。手机可能以系统蓝牙名称显示。保持 PocketTrack 在前台。
4. 页面显示「蓝牙已连接」后，点击「开始触控」或「开始纯黑触控」。此时不需要 USB，也不需要 Mac 辅助功能授权。
5. 触控时从左或右边缘向内滑，回到首页；前台蓝牙连接保留，输入状态和旧手势队列清空。锁屏、切后台或退出应用会断开控制，不会自动恢复触控。

采用 Android 系统 Bluetooth HID Device，让 Mac 将手机识别为鼠标和键盘。鼠标移动、滚轮和右 Option 可传输；它不是 Apple 原生多点触控板，不能提供完整系统多指手势。蓝牙滚轮以步进单位传输，手感与 USB 的像素滚动不同。**蓝牙连接和手机文字发送已完成一次真机验证；手感尚未达到 Mac 原生触控板，纯黑、边缘退出和触控内语音仍按验证记录逐项验收。**

### USB（Android 8+）

在手机关闭「使用蓝牙」，然后：

1. 用 USB 连接手机，在手机上允许这台 Mac 的 USB 调试。
2. 编译两端，执行 `python3 scripts/connect-usb.py`。
3. Mac 弹出 PocketTrack 窗口，在系统设置的「隐私与安全性 → 辅助功能」中允许 PocketTrack。
4. 手机点击「开始触控」或「开始纯黑触控」。
5. 单指移动、轻点左键、双指滚动、双指轻点右键、单指停住约半秒后拖动。
6. 从屏幕左或右边缘向内滑动退出触控；电源键锁屏、切后台、断线也会停止。重新解锁不会自动恢复控制。

Mac 的实体 Option 键照常使用。手机默认用音量上键展开手机语音面板；关闭「触控时用手机语音」后，可启用备用「音量上键映射右 Option」：按下发送右 Option，松开发送释放（USB 为 Mac 键码 61，蓝牙为 HID 右 Alt 修饰位）。它是否能唤起豆包取决于豆包当前的快捷键配置及其是否接收合成修饰键事件，需要在你的机器上验证。本项目不修改豆包设置。

备用右 Option 方式使用 Mac 豆包和 Mac 麦克风。下方的手机语音方式使用手机输入法收音，PocketTrack 本身不录音。

## 触控中使用手机豆包语音

1. Mac 打开 `PocketTrackText.app`（文字接收器），点击「开始接收手机文字」，首次允许系统蓝牙提示。它不需要辅助功能授权；旧 USB 接收器保持不变。
2. 若从未完成 USB 配对，连接 USB 并运行 `python3 scripts/pair-text.py`。核对电脑脚本与手机弹窗的校验码后确认。已有配对信息沿用，不重复授权；此后可以拔掉 USB。
3. 手机连上蓝牙 Mac，进入普通或纯黑触控。按一次音量上键，在底部展开输入面板及当前输入法，上方仍是触控区域。
4. 点豆包键盘的语音按钮，说话后确认文字，点击「发送到 Mac，继续触控」。电脑收到后，手机发送固定的 Command+V 粘贴；不自动发送 Return。
5. 成功后面板和键盘收起，恢复原来的触控状态。纯黑模式会在输入时临时恢复亮度，成功后重新全黑。音量上键或「收起」也能关闭面板，未发送草稿保留在本次应用内存中。

发送会**替换 Mac 剪贴板并粘贴到当前光标处**，请先选好目标输入框。接收器不读取原剪贴板，也不保存文字历史。传输失败保留草稿，不自动重发或自动提交给 Agent。

PocketTrack 只调用系统输入框和当前输入法，不访问豆包私有数据，不申请手机麦克风权限。当前仍需手动点输入法的语音按钮；不声称可以通过音量键直接启动豆包录音。需要手机已安装并选用豆包输入法，也可以使用其他输入法。

手机与 Mac 通过独立的 BLE 文字通道通信：256 位共享密钥、每次连接随机挑战、双向 HMAC 验证、AES-GCM 加密，Mac 确认收到后才触发固定粘贴快捷键。文字上限为 UTF-8 8192 字节，输入框另限 4000 个 UTF-16 单元。该通道不传声音，不接收任意命令。

## 构建

需要 macOS 13+、Xcode Command Line Tools、JDK 17+、Android SDK Platform 36 / Build Tools 36.0.0、ADB。手机需要 Android 8+（原型面向已连接 Android 16 设备验证）。

```bash
export JAVA_HOME=/path/to/jdk/Contents/Home
export ANDROID_HOME=/path/to/android-sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
./scripts/build-android.sh
./scripts/build-mac.sh
./scripts/build-mac-text.sh
python3 scripts/connect-usb.py
```

ADB 不在 PATH 时可设置 `ADB=/path/to/adb`。连接多部手机时显式设置 `ANDROID_SERIAL`。构建输出位于 `build/`。本地 APK 使用开发签名；Mac 应用为临时签名，尚未公证，不代表正式发布版本。

### 小米等设备拦截 USB 安装时

如果出现 `INSTALL_FAILED_USER_RESTRICTED`，可以手动安装，不必绕过系统限制：

```bash
adb push build/PocketTrack.apk /sdcard/Download/PocketTrack-v0.3.1.apk
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

- USB 使用 `INTERNET` 普通权限，经 `adb reverse` 建立本地 TCP 连接。
- 蓝牙在 Android 12+ 按需申请 `BLUETOOTH_CONNECT` 与 `BLUETOOTH_ADVERTISE`（附近设备）；旧系统声明仅适用至 Android 11 的 `BLUETOOTH` / `BLUETOOTH_ADMIN`。不申请扫描或定位权限。
- 仅在用户点击连接后列出已配对设备供选择；不扫描附近设备，不保存或上传设备列表。新配对由系统处理，手机可发现状态限时 120 秒。
- 蓝牙输入仅在应用前台、已连接且用户点击开始后发送；没有后台服务。退出时发送按键释放并断开连接。
- 不请求手机麦克风、相册、相机、联系人、短信、通知、悬浮窗、无障碍或存储权限。
- 不读取手机剪贴板或其他应用内容，不录制屏幕，不上传遥测；只发送你在本应用输入框里主动确认的文字。
- Mac 接收端只绑定 `127.0.0.1:49671`，不向局域网开放。
- 256 位随机配对密钥；手机端存放于应用私有设置，关闭备份；Mac 密钥文件权限为 0600。
- HID 仅发送固定的鼠标、右 Option 和 Command+V；无任意按键、远程 shell 或文件接口。BLE 只接收经验证的文字帧。
- 断线与心跳超时释放本应用模拟的拖动和 Option 状态；手机退出模式后不会自动重连。
- USB 传输没有另加 TLS，依赖本机/已授权 ADB 通道；不要把端口对外暴露。

## 验证

`tests/run-mac-tests.py` 在独立端口启动无界面的测试接收端，不注入鼠标或键盘输入。协议测试要求 `OK DRY` 握手，防止误向正常接收端发送测试输入。

```bash
python3 tests/run-mac-tests.py
mkdir -p build/java-tests
"$JAVA_HOME/bin/javac" -d build/java-tests android/src/dev/pockettrack/MotionBatch.java tests/MotionBatchTest.java android/src/dev/pockettrack/MotionDynamics.java tests/MotionDynamicsTest.java android/src/dev/pockettrack/HidReports.java tests/HidReportsTest.java
"$JAVA_HOME/bin/java" -cp build/java-tests MotionBatchTest
"$JAVA_HOME/bin/java" -cp build/java-tests MotionDynamicsTest
"$JAVA_HOME/bin/java" -cp build/java-tests HidReportsTest
./tests/run-text-tests.sh
```

具体已验证内容和未验证边界见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。

## 0.3.1 触控与手机语音

- 解除固定竖屏，使用传感器横竖屏；旋转时释放输入并重新建立触控坐标。
- 蓝牙只应用基础灵敏度，由 Mac 处理鼠标加速，避免手机与 Mac 叠加加速；USB 保留独立速度曲线。
- 使用系统返回回调，并保留左右 24 dp 的边缘滑动退出区域；边缘手势不转换为鼠标点击或位移。
- 退出触控保留前台蓝牙连接，旧队列失效，防止退出后重新按下拖动或修饰键。
- 普通与纯黑触控内都能通过音量上键展开手机输入法面板，发送后恢复触控。
- Mac 文字接收器独立运行，避免重签名旧 USB 接收器和反复申请辅助功能权限。

## 0.2.0 蓝牙与连接提示

新增前台蓝牙鼠标/键盘通道，USB 保留。首页重排为连接、开始触控和快捷操作；速度、惯性和语音开关移入设置面板。加入自适应应用图标，普通触控区采用暗色点阵，纯黑模式仍不绘制内容。连接失败会在设置页显示原因；尚未连接时点击纯黑触控会提示先连接。USB 转发在拔插后可能消失，重新运行连接脚本即可恢复。

Mac 应用保持 0.1.1，没有重新签名，避免让已授权用户重复处理辅助功能设置。

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

蓝牙实现依据 Android 官方 [HID Device API](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice) 与[蓝牙权限说明](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)。系统要求 HID 应用在前台，切后台会取消注册。
