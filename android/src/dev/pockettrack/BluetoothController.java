package dev.pockettrack;

import android.Manifest;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Foreground-only Bluetooth HID; uses only devices selected or paired by the user. */
public final class BluetoothController {
    public interface Listener {
        void ready(BluetoothController who);
        void connected(BluetoothController who,BluetoothDevice device);
        void stopped(BluetoothController who,String reason);
    }
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final HidReports reports=new HidReports();
    private static final class Command {final String text;final int epoch;Command(String text,int epoch){this.text=text;this.epoch=epoch;}}
    private final ArrayBlockingQueue<Command> queue=new ArrayBlockingQueue<>(128);
    private volatile int epoch=0;
    private volatile BluetoothHidDevice hid;
    private volatile BluetoothDevice peer;
    private BluetoothDevice requested;
    private volatile boolean closed=false;
    private boolean registered=false,registering=false;
    private Thread writer;
    private final Object outputLock=new Object();
    private final Runnable timeout=()->fail("蓝牙连接超时，请在 Mac 蓝牙设置中重新连接手机。");
    public BluetoothController(Context context,Listener listener){
        this.context=context;this.listener=listener;
        BluetoothManager manager=context.getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
    }
    private boolean allowed(){return Build.VERSION.SDK_INT<31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    public boolean isConnected(){return !closed && peer!=null && registered;}
    public void start(){
        if(!allowed()){fail("需要附近设备权限才能使用蓝牙。");return;}
        try {
            if(adapter==null || !adapter.isEnabled()){fail("请先打开手机蓝牙。");return;}
            main.postDelayed(timeout,12000);
            if(!adapter.getProfileProxy(context,new BluetoothProfile.ServiceListener(){
                @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
                    if(closed){adapter.closeProfileProxy(profile,proxy);return;}
                    hid=(BluetoothHidDevice)proxy;ensureRegistered();
                }
                @Override public void onServiceDisconnected(int profile){hid=null;if(!closed)fail("手机蓝牙服务已断开。");}
            },BluetoothProfile.HID_DEVICE))fail("系统没有提供蓝牙鼠标/键盘服务。");
        } catch(SecurityException error){fail("附近设备权限已被撤销。");}
    }
    public void ensureRegistered(){
        if(closed || hid==null || registered || registering)return;
        if(!allowed()){fail("需要附近设备权限。");return;}
        try {
            registering=true;
            BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings("PocketTrack","Phone mouse and right Option key","PocketTrack",BluetoothHidDevice.SUBCLASS1_COMBO,HidReports.DESCRIPTOR);
            if(!hid.registerApp(sdp,null,null,command->main.post(command),callback)){registering=false;fail("蓝牙输入设备注册失败，请退出其他蓝牙键鼠应用后重试。");}
        } catch(SecurityException error){registering=false;fail("附近设备权限已被撤销。");}
    }
    public void connect(BluetoothDevice device){
        if(closed || !registered || hid==null || !allowed())return;
        requested=device;
        try {
            main.removeCallbacks(timeout);main.postDelayed(timeout,20000);
            if(!hid.connect(device))fail("无法连接此 Mac，请检查 Mac 蓝牙设置。");
        } catch(SecurityException error){fail("附近设备权限已被撤销。");}
    }
    public void waitForPairing(){main.removeCallbacks(timeout);main.postDelayed(timeout,130000);}
    public void send(String command){if(!isConnected())return;if(!queue.offer(new Command(command,epoch)))fail("蓝牙发送队列拥堵，已停止控制。请重新连接。");}
    public void releaseInputs(){
        synchronized(outputLock){
            epoch++;queue.clear();
            try {if(!closed && hid!=null && peer!=null && allowed())for(HidReports.Report report:reports.apply("RESET"))hid.sendReport(peer,report.id,report.data);}
            catch(RuntimeException error){fail("释放按键时蓝牙已断开。");}
        }
    }
    private void startWriter(){
        writer=new Thread(()->{
            try {
                while(!closed && peer!=null){
                    Command command=queue.poll(100,TimeUnit.MILLISECONDS);if(command==null || command.epoch!=epoch)continue;
                    MotionBatch batch=new MotionBatch();batch.add(command.text);
                    for(int i=0;i<127;i++){Command next=queue.peek();if(next==null || next.epoch!=command.epoch)break;queue.poll();batch.add(next.text);}
                    for(String line:batch.finish().split("\n")){
                        synchronized(outputLock){
                            if(closed)return;
                            if(command.epoch!=epoch)break;
                            if(!allowed())throw new SecurityException();
                            for(HidReports.Report report:reports.apply(line)){
                                BluetoothHidDevice service=hid;BluetoothDevice target=peer;
                                if(service==null || target==null || !service.sendReport(target,report.id,report.data))throw new IllegalStateException();
                            }
                        }
                    }
                }
            } catch(InterruptedException ignored) {
            } catch(Exception error){if(!closed)fail("蓝牙输入发送失败，已停止控制。请重新连接。");}
        },"PocketTrack-Bluetooth");writer.start();
    }
    private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback(){
        @Override public void onAppStatusChanged(BluetoothDevice pluggedDevice,boolean value){
            if(closed)return;registering=false;registered=value;
            if(value){main.removeCallbacks(timeout);listener.ready(BluetoothController.this);}
            else {fail("蓝牙输入已停止；请保持 PocketTrack 在前台后重新连接。");}
        }
        @Override public void onConnectionStateChanged(BluetoothDevice device,int state){
            if(closed)return;
            try {
                if(state==BluetoothProfile.STATE_CONNECTED){
                    if(peer!=null && peer.equals(device))return;
                    if(peer!=null || (requested!=null && !requested.equals(device))){if(allowed() && hid!=null)hid.disconnect(device);return;}
                    main.removeCallbacks(timeout);peer=device;
                    synchronized(outputLock){if(hid!=null && allowed())for(HidReports.Report report:reports.apply("RESET"))hid.sendReport(device,report.id,report.data);}
                    startWriter();listener.connected(BluetoothController.this,device);
                } else if(state==BluetoothProfile.STATE_DISCONNECTED && (peer!=null && peer.equals(device))){peer=null;fail("Mac 蓝牙已断开。请重新连接。");}
            } catch(SecurityException error){fail("附近设备权限已被撤销。");}
        }
        @Override public void onGetReport(BluetoothDevice device,byte type,byte id,int bufferSize){
            if(closed || hid==null || !allowed())return;
            try {
                byte[] data=reports.current(id);
                if(type==BluetoothHidDevice.REPORT_TYPE_INPUT && data!=null && (bufferSize==0 || bufferSize>=data.length))hid.replyReport(device,type,id,data);
                else hid.reportError(device,BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ);
            } catch(SecurityException error){fail("附近设备权限已被撤销。");}
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice device){if(!closed)fail("Mac 已移除蓝牙连接。");}
    };
    private void fail(String reason){main.post(()->{if(closed)return;close();listener.stopped(this,reason);});}
    public void close(){
        if(closed)return;closed=true;main.removeCallbacks(timeout);queue.clear();if(writer!=null)writer.interrupt();
        synchronized(outputLock){
        BluetoothHidDevice service=hid;BluetoothDevice target=peer;
        if(service!=null){
            try {
                if(allowed()){
                    if(target!=null){for(HidReports.Report report:reports.apply("RESET"))service.sendReport(target,report.id,report.data);service.disconnect(target);}
                    service.unregisterApp();
                }
            } catch(SecurityException ignored) {} finally {try{adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,service);}catch(SecurityException ignored){}}
        }
        peer=null;hid=null;registered=false;
        }
    }
}
