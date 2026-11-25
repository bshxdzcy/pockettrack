import AppKit
import CoreBluetooth
import CryptoKit

func uuid(_ part: Int) -> CBUUID { CBUUID(string:String(format:"b7d3a%03x-45b6-4ba2-81d5-4f912cf2db20",part)) }
let serviceID=uuid(0),helloID=uuid(1),authID=uuid(2),dataID=uuid(3),ackID=uuid(4)

final class TextReceiver: NSObject,CBCentralManagerDelegate,CBPeripheralDelegate {
    var update: ((String)->Void)?
    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var characteristics: [CBUUID:CBCharacteristic]=[:]
    private var key: SymmetricKey?
    private var session=Data(),frames=TextFrames(),seen=[Data]()
    private var rejected=Set<UUID>()
    private var enabled=false,authenticated=false
    private var timeout: Timer?
    private var receivedCount=0
    func start() {
        stop();rejected.removeAll()
        let url=FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("PocketTrack/pairing-token")
        guard let hex=try? String(contentsOf:url,encoding:.utf8),let key=TextCrypto.key(hex) else {update?("尚未配对。请先用 USB 运行文字配对脚本。");return}
        self.key=key;enabled=true
        update?("正在准备蓝牙…")
        if central==nil {central=CBCentralManager(delegate:self,queue:.main)} else {centralManagerDidUpdateState(central)}
    }
    func wake(){if enabled{start()}}
    func stop() {
        enabled=false;timeout?.invalidate();timeout=nil;central?.stopScan()
        if let peripheral {central?.cancelPeripheralConnection(peripheral)}
        peripheral=nil;characteristics.removeAll();authenticated=false;frames.reset();session.removeAll();key=nil
        update?("接收已停止")
    }
    private func scan(){guard enabled,central.state == .poweredOn else{return};central.scanForPeripherals(withServices:[serviceID],options:[CBCentralManagerScanOptionAllowDuplicatesKey:false]);update?("自动接收已开启，等待手机进入触控页。")}
    private func armTimeout(_ seconds: Double){timeout?.invalidate();timeout=Timer.scheduledTimer(withTimeInterval:seconds,repeats:false){[weak self] _ in self?.fail("连接超时，正在等待手机文字通道。",reject:false)}}
    private func fail(_ reason: String,reject: Bool=true){
        guard enabled else{return};timeout?.invalidate();timeout=nil
        if let p=peripheral {if reject{rejected.insert(p.identifier)};central.cancelPeripheralConnection(p)}
        peripheral=nil;authenticated=false;characteristics.removeAll();frames.reset();session.removeAll()
        update?(reason);central.scanForPeripherals(withServices:[serviceID],options:nil)
    }
    func centralManagerDidUpdateState(_ central: CBCentralManager){
        guard enabled else{return}
        switch central.state {
        case .poweredOn:scan()
        case .unauthorized:update?("请在系统设置 → 隐私与安全性 → 蓝牙，允许 PocketTrack 文字接收器。")
        case .poweredOff:update?("Mac 蓝牙未开启。")
        default:update?("Mac 蓝牙暂不可用。")
        }
    }
    func centralManager(_ central: CBCentralManager,didDiscover p: CBPeripheral,advertisementData: [String:Any],rssi RSSI: NSNumber){
        guard enabled,peripheral==nil,!rejected.contains(p.identifier) else{return}
        peripheral=p;p.delegate=self;central.stopScan();central.connect(p);armTimeout(15);update?("正在连接手机文字通道…")
    }
    func centralManager(_ central: CBCentralManager,didConnect p: CBPeripheral){guard enabled,p===peripheral else{return};p.discoverServices([serviceID])}
    func centralManager(_ central: CBCentralManager,didFailToConnect p: CBPeripheral,error: Error?){guard p===peripheral else{return};fail("未能连接手机，正在等待手机文字通道。",reject:false)}
    func centralManager(_ central: CBCentralManager,didDisconnectPeripheral p: CBPeripheral,error: Error?){
        guard enabled,p===peripheral else{return};peripheral=nil;authenticated=false;characteristics.removeAll();frames.reset();timeout?.invalidate();scan()
    }
    func peripheral(_ p: CBPeripheral,didDiscoverServices error: Error?){
        guard p===peripheral,enabled else{return}
        guard error==nil,let service=p.services?.first(where:{$0.uuid==serviceID}) else{fail("未找到文字服务。");return};p.discoverCharacteristics([helloID,authID,dataID,ackID],for:service)
    }
    func peripheral(_ p: CBPeripheral,didDiscoverCharacteristicsFor service: CBService,error: Error?){
        guard p===peripheral,enabled else{return}
        guard error==nil else{fail("文字服务不可用。");return}
        for c in service.characteristics ?? [] {characteristics[c.uuid]=c}
        guard characteristics.count==4,let hello=characteristics[helloID] else{fail("文字服务版本不兼容。");return};p.readValue(for:hello)
    }
    func peripheral(_ p: CBPeripheral,didWriteValueFor c: CBCharacteristic,error: Error?){
        guard p===peripheral,enabled else{return}
        guard error==nil else{fail("配对或接收确认失败，请重新连接。");return}
        if c.uuid==authID {p.readValue(for:c)}
    }
    func peripheral(_ p: CBPeripheral,didUpdateNotificationStateFor c: CBCharacteristic,error: Error?){
        guard p===peripheral,enabled,c.uuid==dataID else{return}
        guard error==nil,c.isNotifying,authenticated else{fail("无法订阅手机文字。");return}
        timeout?.invalidate();timeout=nil;update?("手机文字已连接。发送时写入 Mac 剪贴板，由手机执行粘贴；不会自动回车。")
    }
    func peripheral(_ p: CBPeripheral,didUpdateValueFor c: CBCharacteristic,error: Error?){
        guard p===peripheral,enabled else{return}
        guard error==nil,let value=c.value,let key else{fail("读取文字通道失败。");return}
        if c.uuid==helloID {
            guard value.count==16,let auth=characteristics[authID] else{fail("配对数据无效。");return}
            session=value;p.writeValue(TextCrypto.proof(key,session,"mac"),for:auth,type:.withResponse)
        } else if c.uuid==authID {
            guard TextCrypto.equal(value,TextCrypto.proof(key,session,"phone")),let data=characteristics[dataID] else{fail("手机配对密钥不匹配，请用 USB 重新配对。");return}
            authenticated=true;p.setNotifyValue(true,for:data)
        } else if c.uuid==dataID {
            guard authenticated else{fail("未验证的文字通道。");return}
            do {
                armTimeout(45)
                if let packet=try frames.add(value) {
                    let (id,text)=try TextCrypto.open(packet,session,key)
                    guard let ack=characteristics[ackID] else{throw TextCrypto.Failure.invalid}
                    if !seen.contains(id) {
                        NSPasteboard.general.clearContents()
                        guard NSPasteboard.general.setString(text,forType:.string) else{fail("无法写入剪贴板，未触发粘贴。",reject:false);return}
                        seen.append(id);if seen.count>32{seen.removeFirst()};receivedCount+=1
                    }
                    timeout?.invalidate();timeout=nil
                    p.writeValue(id,for:ack,type:.withResponse)
                    update?("已收到第 \(receivedCount) 段文字（\(text.count) 字符）。手机会在当前光标处粘贴，不会按回车。")
                }
            } catch {fail("文字校验失败，未写入剪贴板。")}
        }
    }
}

final class AppDelegate: NSObject,NSApplicationDelegate {
    let receiver=TextReceiver()
    var window: NSWindow!,label: NSTextField!,testWindow: NSWindow?
    var statusItem: NSStatusItem!
    var wakeObserver: NSObjectProtocol?
    func applicationDidFinishLaunching(_ notification: Notification){
        let menu=NSMenu(),item=NSMenuItem();menu.addItem(item);let sub=NSMenu();sub.addItem(withTitle:"退出",action:#selector(NSApplication.terminate(_:)),keyEquivalent:"q");item.submenu=sub;NSApp.mainMenu=menu
        window=NSWindow(contentRect:NSRect(x:0,y:0,width:520,height:360),styleMask:[.titled,.closable,.miniaturizable],backing:.buffered,defer:false);window.title="PocketTrack · 手机文字"
        let stack=NSStackView();stack.orientation = .vertical;stack.alignment = .leading;stack.spacing=18;stack.translatesAutoresizingMaskIntoConstraints=false;window.contentView!.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo:window.contentView!.leadingAnchor,constant:28),stack.trailingAnchor.constraint(equalTo:window.contentView!.trailingAnchor,constant:-28),stack.topAnchor.constraint(equalTo:window.contentView!.topAnchor,constant:28)])
        let title=NSTextField(labelWithString:"在手机说话，在 Mac 输入。");title.font = .systemFont(ofSize:24,weight:.semibold);stack.addArrangedSubview(title)
        label=NSTextField(wrappingLabelWithString:"正在自动连接手机…");stack.addArrangedSubview(label)
        stack.addArrangedSubview(NSTextField(wrappingLabelWithString:"只接收配对手机主动发送的文字。\n发送会替换 Mac 剪贴板；不读取原剪贴板，不录音、不保存文字历史。\n关闭窗口仍会接收，可在菜单栏暂停或退出。"))
        stack.addArrangedSubview(NSButton(title:"重新连接",target:self,action:#selector(start)))
        stack.addArrangedSubview(NSButton(title:"停止接收",target:self,action:#selector(stop)))
        stack.addArrangedSubview(NSButton(title:"打开测试输入框",target:self,action:#selector(test)))
        receiver.update = {[weak self] value in self?.label.stringValue=value}
        statusItem=NSStatusBar.system.statusItem(withLength:NSStatusItem.variableLength)
        statusItem.button?.title="PT";statusItem.button?.toolTip="PocketTrack 手机文字"
        let statusMenu=NSMenu()
        statusMenu.addItem(withTitle:"打开接收状态",action:#selector(showWindow),keyEquivalent:"").target=self
        statusMenu.addItem(withTitle:"重新连接",action:#selector(start),keyEquivalent:"").target=self
        statusMenu.addItem(withTitle:"暂停接收",action:#selector(stop),keyEquivalent:"").target=self
        statusMenu.addItem(NSMenuItem.separator())
        statusMenu.addItem(withTitle:"退出 PocketTrack 文字",action:#selector(NSApplication.terminate(_:)),keyEquivalent:"q")
        statusItem.menu=statusMenu
        wakeObserver=NSWorkspace.shared.notificationCenter.addObserver(forName:NSWorkspace.didWakeNotification,object:nil,queue:.main){[weak self] _ in self?.receiver.wake()}
        window.isReleasedWhenClosed=false;window.center()
        receiver.start()
        if !CommandLine.arguments.contains("--background"){showWindow()}
    }
    @objc func showWindow(){window.makeKeyAndOrderFront(nil);NSApp.activate(ignoringOtherApps:true)}
    func applicationShouldHandleReopen(_ sender:NSApplication,hasVisibleWindows:Bool)->Bool{showWindow();return true}
    @objc func start(){receiver.start()}
    @objc func stop(){receiver.stop()}
    @objc func test(){
        let w=NSWindow(contentRect:NSRect(x:0,y:0,width:640,height:380),styleMask:[.titled,.closable,.resizable],backing:.buffered,defer:false);w.title="PocketTrack · 测试输入框";w.isReleasedWhenClosed=false
        let scroll=NSScrollView(frame:w.contentView!.bounds);scroll.autoresizingMask=[.width,.height];scroll.hasVerticalScroller=true
        let text=NSTextView(frame:scroll.bounds);text.font = .systemFont(ofSize:20);text.isRichText=false;text.autoresizingMask=[.width];text.isVerticallyResizable=true;text.textContainer?.widthTracksTextView=true
        scroll.documentView=text;w.contentView!.addSubview(scroll);testWindow=w;w.center();w.makeKeyAndOrderFront(nil);w.makeFirstResponder(text)
    }
    func applicationWillTerminate(_ notification: Notification){receiver.stop();if let wakeObserver{NSWorkspace.shared.notificationCenter.removeObserver(wakeObserver)}}
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication)->Bool {false}
}
let app=NSApplication.shared,delegate=AppDelegate();app.delegate=delegate;app.setActivationPolicy(.accessory);app.run()
