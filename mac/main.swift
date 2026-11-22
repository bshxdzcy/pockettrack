import AppKit
import Network
import ApplicationServices
import Security

let port: UInt16 = 49671
let dryRun = CommandLine.arguments.contains("--dry-run") || CommandLine.arguments.contains("--self-test")
let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("PocketTrack")
try FileManager.default.createDirectory(at: support, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
let tokenURL = support.appendingPathComponent("pairing-token")
let token: String
if let existing = try? String(contentsOf: tokenURL, encoding: .utf8), existing.count == 64 {
    token = existing
} else {
    var bytes = [UInt8](repeating: 0, count: 32)
    guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { fatalError("Random source failed") }
    token = bytes.map { String(format: "%02x", $0) }.joined()
    try token.write(to: tokenURL, atomically: true, encoding: .utf8)
    try FileManager.default.setAttributes([.posixPermissions: 0o600], ofItemAtPath: tokenURL.path)
}

final class Input {
    var option = false
    var dragging = false
    var count = 0
    var lastClick = 0.0
    var clicks: Int64 = 0
    let source = CGEventSource(stateID: .privateState)
    func post(_ event: CGEvent?) {
        guard !dryRun, AXIsProcessTrusted(), let event else { return }
        // Preserve physical modifiers without observing the user's keystrokes.
        var flags = CGEventSource.flagsState(.hidSystemState)
        if event.type == .flagsChanged { flags.remove(.maskAlternate) }
        if option { flags.insert(.maskAlternate) }
        event.flags = flags
        event.post(tap: .cghidEventTap)
    }
    func mouse(_ type: CGEventType, _ button: CGMouseButton = .left) {
        let event = CGEvent(mouseEventSource: source, mouseType: type, mouseCursorPosition: CGEvent(source: nil)?.location ?? .zero, mouseButton: button)
        event?.setIntegerValueField(.mouseEventClickState, value: clicks)
        post(event)
    }
    func setOption(_ down: Bool) {
        guard option != down else { return }
        option = down
        let event = CGEvent(keyboardEventSource: source, virtualKey: 58, keyDown: down)
        event?.type = .flagsChanged
        post(event)
    }
    func release() {
        if dragging { mouse(.leftMouseUp); dragging = false }
        setOption(false)
    }
    func handle(_ parts: [Substring]) -> Bool {
        guard let command = parts.first else { return false }
        switch command {
        case "PING": return parts.count == 1
        case "RESET": guard parts.count == 1 else { return false }; release()
        case "OPTION":
            guard parts.count == 2, parts[1] == "0" || parts[1] == "1" else { return false }
            setOption(parts[1] == "1")
        case "MOVE", "SCROLL":
            guard parts.count == 3, let x = Double(parts[1]), let y = Double(parts[2]), x.isFinite, y.isFinite, abs(x) <= 500, abs(y) <= 500 else { return false }
            if command == "MOVE" {
                var p = CGEvent(source: nil)?.location ?? .zero
                p.x += x; p.y += y
                var displayCount: UInt32 = 0
                CGGetActiveDisplayList(0, nil, &displayCount)
                var displays = [CGDirectDisplayID](repeating: 0, count: Int(displayCount))
                CGGetActiveDisplayList(displayCount, &displays, &displayCount)
                let bounds = displays.map { CGDisplayBounds($0) }
                if !bounds.contains(where: { $0.contains(p) }), let nearest = bounds.map({ CGRect -> CGPoint in
                    CGPoint(x: min(max(p.x, CGRect.minX), CGRect.maxX - 1), y: min(max(p.y, CGRect.minY), CGRect.maxY - 1))
                }).min(by: { hypot($0.x-p.x, $0.y-p.y) < hypot($1.x-p.x, $1.y-p.y) }) { p = nearest }
                post(CGEvent(mouseEventSource: source, mouseType: dragging ? .leftMouseDragged : .mouseMoved, mouseCursorPosition: p, mouseButton: .left))
            } else {
                post(CGEvent(scrollWheelEvent2Source: source, units: .pixel, wheelCount: 2, wheel1: Int32(y), wheel2: Int32(x), wheel3: 0))
            }
        case "CLICK":
            guard parts.count == 2, parts[1] == "L" || parts[1] == "R" else { return false }
            let now = ProcessInfo.processInfo.systemUptime
            clicks = now-lastClick < NSEvent.doubleClickInterval ? min(clicks+1, 2) : 1
            lastClick = now
            let right = parts[1] == "R"
            mouse(right ? .rightMouseDown : .leftMouseDown, right ? .right : .left)
            mouse(right ? .rightMouseUp : .leftMouseUp, right ? .right : .left)
        case "DRAG":
            guard parts.count == 2, parts[1] == "0" || parts[1] == "1" else { return false }
            let down = parts[1] == "1"
            if dragging != down { clicks = 1; mouse(down ? .leftMouseDown : .leftMouseUp); dragging = down }
        default: return false
        }
        count += 1
        return true
    }
}

final class Server {
    let input = Input()
    var listener: NWListener?
    var client: NWConnection?
    var buffer = Data()
    var authenticated = false
    var lastSeen = 0.0
    var status = "等待 USB 连接"
    var timer: Timer?
    func start() throws {
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        let listener = try NWListener(using: parameters)
        self.listener = listener
        listener.stateUpdateHandler = { [weak self] state in
            if case .failed(let error) = state { self?.status = "启动失败：\(error.localizedDescription)" }
        }
        listener.newConnectionHandler = { [weak self] c in
            guard let self else { c.cancel(); return }
            guard self.client == nil else { c.cancel(); return }
            self.client = c; self.buffer.removeAll(); self.authenticated = false
            self.lastSeen = ProcessInfo.processInfo.systemUptime
            c.stateUpdateHandler = { [weak self, weak c] state in
                guard let self, let c, self.client === c else { return }
                switch state {
                case .ready: self.receive(c)
                case .failed, .cancelled: self.disconnect()
                default: break
                }
            }
            c.start(queue: .main)
        }
        listener.start(queue: .main)
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self else { return }
            if self.client != nil && ProcessInfo.processInfo.systemUptime - self.lastSeen > 2 { self.disconnect() }
        }
    }
    func disconnect() {
        let old = client; client = nil; authenticated = false; buffer.removeAll()
        input.release(); old?.cancel(); status = "已停止 · 等待 USB 连接"
    }
    func receive(_ c: NWConnection) {
        c.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self, weak c] data, _, done, error in
            guard let self, let c, self.client === c else { return }
            if let data {
                self.buffer.append(data)
                guard self.buffer.count <= 8192 else { self.disconnect(); return }
                while let end = self.buffer.firstIndex(of: 10) {
                    let lineData = self.buffer.prefix(upTo: end)
                    self.buffer.removeSubrange(...end)
                    guard lineData.count <= 160, let line = String(data: lineData, encoding: .utf8) else { self.disconnect(); return }
                    if !self.authenticated {
                        guard line == "AUTH \(token)" else { self.disconnect(); return }
                        self.authenticated = true; self.status = "USB 已连接"
                        c.send(content: Data("OK\n".utf8), completion: .contentProcessed { _ in })
                    } else {
                        guard self.input.handle(line.split(separator: " ")) else { self.disconnect(); return }
                        if line == "PING" { c.send(content: Data("PONG\n".utf8), completion: .contentProcessed { _ in }) }
                    }
                    self.lastSeen = ProcessInfo.processInfo.systemUptime
                }
            }
            if done || error != nil { self.disconnect() } else { self.receive(c) }
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    let server = Server()
    var window: NSWindow!
    var statusLabel: NSTextField!
    var timer: Timer?
    func applicationDidFinishLaunching(_ notification: Notification) {
        let menu = NSMenu(); let item = NSMenuItem(); menu.addItem(item)
        let sub = NSMenu(); sub.addItem(withTitle: "退出 PocketTrack", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"); item.submenu = sub
        NSApp.mainMenu = menu
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 490, height: 345), styleMask: [.titled, .closable, .miniaturizable], backing: .buffered, defer: false)
        window.title = "PocketTrack · USB 触控板"
        let stack = NSStackView(); stack.orientation = .vertical; stack.alignment = .leading; stack.spacing = 18
        stack.translatesAutoresizingMaskIntoConstraints = false
        window.contentView!.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: window.contentView!.leadingAnchor, constant: 28), stack.trailingAnchor.constraint(equalTo: window.contentView!.trailingAnchor, constant: -28), stack.topAnchor.constraint(equalTo: window.contentView!.topAnchor, constant: 28)])
        let title = NSTextField(labelWithString: "把手机，变成手边的触控板。")
        title.font = .systemFont(ofSize: 23, weight: .semibold); stack.addArrangedSubview(title)
        statusLabel = NSTextField(wrappingLabelWithString: "启动中"); stack.addArrangedSubview(statusLabel)
        let details = NSTextField(wrappingLabelWithString: "USB 本地传输 · 不读取手机内容\n单指移动 / 轻点点击 / 双指滚动\nMac Option 键继续照常使用\n手机按电源键或切到后台，立即停止。")
        details.font = .systemFont(ofSize: 14); stack.addArrangedSubview(details)
        let permission = NSButton(title: "打开辅助功能授权", target: self, action: #selector(openPermission)); stack.addArrangedSubview(permission)
        stack.addArrangedSubview(NSButton(title: "立即断开并释放按键", target: self, action: #selector(stop)))
        window.center(); window.makeKeyAndOrderFront(nil); NSApp.activate(ignoringOtherApps: true)
        do { try server.start() } catch { server.status = "端口不可用：\(error.localizedDescription)" }
        timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self else { return }
            let access = dryRun ? "测试模式 · 不发送输入" : (AXIsProcessTrusted() ? "辅助功能已授权" : "需要辅助功能授权才能控制 Mac")
            self.statusLabel.stringValue = "\(self.server.status)\n\(access) · 已接收 \(self.server.input.count) 个事件"
        }
    }
    @objc func openPermission() {
        let options = [kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(options)
        NSWorkspace.shared.open(URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!)
    }
    @objc func stop() { server.disconnect() }
    func applicationWillTerminate(_ notification: Notification) { server.disconnect() }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}
if CommandLine.arguments.contains("--self-test") {
    let input = Input()
    precondition(input.handle(["OPTION", "1"]) && input.option)
    precondition(input.handle(["DRAG", "1"]) && input.dragging)
    input.release()
    precondition(!input.option && !input.dragging)
    precondition(!input.handle(["MOVE", "nan", "2"]))
    precondition(!input.handle(["MOVE", "501", "0"]))
    precondition(!input.handle(["OPTION", "2"]))
    precondition(!input.handle(["CLICK", "X"]))
    precondition(!input.handle(["EXEC", "bad"]))
    precondition(input.handle(["MOVE", "3", "-2"]))
    precondition(input.handle(["SCROLL", "1", "2"]))
    print("PASS: input validation, modifier release, drag release, move and scroll")
    exit(0)
}
let app = NSApplication.shared
let delegate = AppDelegate()
app.delegate = delegate
app.setActivationPolicy(.regular)
// Graceful terminal termination also releases any input held by this process.
signal(SIGTERM, SIG_IGN)
signal(SIGINT, SIG_IGN)
let termination = DispatchSource.makeSignalSource(signal: SIGTERM, queue: .main)
let interruption = DispatchSource.makeSignalSource(signal: SIGINT, queue: .main)
termination.setEventHandler { NSApp.terminate(nil) }
interruption.setEventHandler { NSApp.terminate(nil) }
termination.resume()
interruption.resume()
app.run()
