package dev.pockettrack;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.security.*;
import java.util.*;

/** Foreground BLE text peripheral; never accesses the microphone, IME internals, or clipboard. */
public final class VoiceBridge {
    public interface Listener {void status(String value);void delivered();}
    private static UUID uuid(int part){return UUID.fromString(String.format(Locale.US,"b7d3a%03x-45b6-4ba2-81d5-4f912cf2db20",part));}
    static final UUID SERVICE=uuid(0),HELLO=uuid(1),AUTH=uuid(2),DATA=uuid(3),ACK=uuid(4);
    private static final UUID CCC=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final Context context;private final Listener listener;private final byte[] key;
    private final Handler main=new Handler(Looper.getMainLooper());private final SecureRandom random=new SecureRandom();
    private BluetoothGattServer server;private BluetoothLeAdvertiser advertiser;private BluetoothGattCharacteristic data;
    private BluetoothDevice peer;private byte[] session=new byte[16],pendingId;
    private boolean closed=false,authorized=false,subscribed=false,sending=false;
    private int mtu=23,badProofs=0;
    private final ArrayDeque<byte[]> frames=new ArrayDeque<>();
    private final Runnable sendTimeout=()->{pendingId=null;frames.clear();sending=false;note("Mac 未确认收到，文字已保留；请检查文字接收器后重试。");};
    public VoiceBridge(Context context,String token,Listener listener){this.context=context;this.key=TextEnvelope.key(token);this.listener=listener;}
    private boolean allowed(){return Build.VERSION.SDK_INT<31 || (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
    private void note(String value){if(!closed)listener.status(value);}
    public boolean sending(){return pendingId!=null;}
    public boolean ready(){return !closed && authorized && subscribed && peer!=null;}
    public void start(){
        try {
            if(!allowed()){note("请先允许附近设备权限。");return;}
            BluetoothManager manager=context.getSystemService(BluetoothManager.class);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null || !adapter.isEnabled()){note("请先打开手机蓝牙。");return;}
            advertiser=adapter.getBluetoothLeAdvertiser();if(advertiser==null){note("手机未提供蓝牙文字通道。");return;}
            server=manager.openGattServer(context,callback);if(server==null){note("无法启动蓝牙文字通道。");return;}
            BluetoothGattService service=new BluetoothGattService(SERVICE,BluetoothGattService.SERVICE_TYPE_PRIMARY);
            service.addCharacteristic(new BluetoothGattCharacteristic(HELLO,BluetoothGattCharacteristic.PROPERTY_READ,BluetoothGattCharacteristic.PERMISSION_READ));
            service.addCharacteristic(new BluetoothGattCharacteristic(AUTH,BluetoothGattCharacteristic.PROPERTY_READ|BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE));
            data=new BluetoothGattCharacteristic(DATA,BluetoothGattCharacteristic.PROPERTY_NOTIFY,0);
            data.addDescriptor(new BluetoothGattDescriptor(CCC,BluetoothGattDescriptor.PERMISSION_READ|BluetoothGattDescriptor.PERMISSION_WRITE));service.addCharacteristic(data);
            service.addCharacteristic(new BluetoothGattCharacteristic(ACK,BluetoothGattCharacteristic.PROPERTY_WRITE,BluetoothGattCharacteristic.PERMISSION_WRITE));
            if(!server.addService(service))note("无法创建文字服务，请重试。");else note("等待 Mac 文字接收器连接…");
        } catch(RuntimeException error){note("蓝牙文字通道不可用，请检查附近设备权限。");}
    }
    public boolean send(String text){
        if(!ready()){note("文字通道尚未连通，请在 Mac 打开 PocketTrack 文字接收器。");return false;}
        if(pendingId!=null){note("正在发送，请等待 Mac 确认。");return false;}
        try {
            byte[] id=new byte[16];random.nextBytes(id);byte[] packet=TextEnvelope.seal(key,session,id,text);
            int size=Math.min(180,Math.max(16,mtu-7)),total=(packet.length+size-1)/size;
            for(int index=0;index<total;index++){
                int begin=index*size,n=Math.min(size,packet.length-begin);byte[] frame=new byte[4+n];
                frame[0]=(byte)(index>>8);frame[1]=(byte)index;frame[2]=(byte)(total>>8);frame[3]=(byte)total;System.arraycopy(packet,begin,frame,4,n);frames.add(frame);
            }
            pendingId=id;note("正在发送文字…");main.postDelayed(sendTimeout,45000);pump();return true;
        } catch(GeneralSecurityException | IllegalArgumentException error){note("文字为空、过长或包含不支持的控制字符；请调整后发送。");return false;}
    }
    private void pump(){
        if(closed || sending || frames.isEmpty() || !ready())return;
        try {
            byte[] frame=frames.remove();sending=true;boolean accepted;
            if(Build.VERSION.SDK_INT>=33)accepted=server.notifyCharacteristicChanged(peer,data,false,frame)==BluetoothStatusCodes.SUCCESS;
            else {data.setValue(frame);accepted=server.notifyCharacteristicChanged(peer,data,false);}
            if(!accepted)failSend();
        } catch(RuntimeException error){failSend();}
    }
    private void failSend(){main.removeCallbacks(sendTimeout);pendingId=null;frames.clear();sending=false;note("蓝牙发送中断，文字已保留；请重新连接。");}
    private void response(BluetoothDevice d,int id,int code,int offset,byte[] value){try{if(server!=null && allowed())server.sendResponse(d,id,code,offset,value);}catch(RuntimeException ignored){}}
    private boolean owns(BluetoothDevice d){return !closed && peer!=null && peer.equals(d);}
    private final AdvertiseCallback advertising=new AdvertiseCallback(){
        @Override public void onStartFailure(int code){main.post(()->note("蓝牙广播未启动，请关闭其他蓝牙外设应用后重试。"));}
    };
    private final BluetoothGattServerCallback callback=new BluetoothGattServerCallback(){
        @Override public void onServiceAdded(int status,BluetoothGattService service){main.post(()->{
            if(closed)return;
            if(status!=BluetoothGatt.GATT_SUCCESS){note("文字服务启动失败。");return;}
            try{advertiser.startAdvertising(new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).setTimeout(0).build(),new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVICE)).build(),advertising);}
            catch(RuntimeException error){note("无法广播文字服务，请检查附近设备权限。");}
        });}
        @Override public void onConnectionStateChange(BluetoothDevice d,int status,int state){main.post(()->{
            if(closed)return;
            if(state==BluetoothProfile.STATE_CONNECTED){
                if(peer!=null && !peer.equals(d)){try{server.cancelConnection(d);}catch(RuntimeException ignored){}return;}
                peer=d;random.nextBytes(session);authorized=false;subscribed=false;mtu=23;badProofs=0;note("正在验证 Mac 配对…");
            } else if(state==BluetoothProfile.STATE_DISCONNECTED && owns(d)){
                peer=null;authorized=false;subscribed=false;failSend();note("文字通道已断开，文字仍保留。返回后重新打开手机语音可重连。");
            }
        });}
        @Override public void onMtuChanged(BluetoothDevice d,int value){main.post(()->{if(owns(d))mtu=value;});}
        @Override public void onCharacteristicReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattCharacteristic characteristic){main.post(()->{
            if(!owns(d)){response(d,id,BluetoothGatt.GATT_FAILURE,offset,null);return;}
            byte[] value=null;
            try{if(characteristic.getUuid().equals(HELLO))value=session;else if(characteristic.getUuid().equals(AUTH) && authorized)value=TextEnvelope.proof(key,session,"phone");}catch(GeneralSecurityException ignored){}
            if(value==null || offset<0 || offset>value.length)response(d,id,BluetoothGatt.GATT_FAILURE,offset,null);
            else response(d,id,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(value,offset,value.length));
        });}
        @Override public void onCharacteristicWriteRequest(BluetoothDevice d,int id,BluetoothGattCharacteristic characteristic,boolean prepared,boolean responseNeeded,int offset,byte[] value){
            byte[] copy=value.clone();main.post(()->{
                int result=BluetoothGatt.GATT_FAILURE;boolean acked=false;
                if(owns(d) && !prepared && offset==0 && copy.length==16){
                    try{
                        if(characteristic.getUuid().equals(AUTH) && badProofs<3){
                            if(MessageDigest.isEqual(copy,TextEnvelope.proof(key,session,"mac"))){authorized=true;result=BluetoothGatt.GATT_SUCCESS;}
                            else badProofs++;
                        } else if(characteristic.getUuid().equals(ACK) && authorized && pendingId!=null && MessageDigest.isEqual(copy,pendingId)){
                            result=BluetoothGatt.GATT_SUCCESS;pendingId=null;main.removeCallbacks(sendTimeout);frames.clear();sending=false;acked=true;
                        }
                    }catch(GeneralSecurityException ignored){}
                }
                if(responseNeeded)response(d,id,result,offset,null);
                if(acked){note("Mac 已收到文字。");listener.delivered();}
                if(badProofs>=3){note("配对验证失败，请通过 USB 重新配对文字接收器。");try{server.cancelConnection(d);}catch(RuntimeException ignored){}}
            });
        }
        @Override public void onDescriptorWriteRequest(BluetoothDevice d,int id,BluetoothGattDescriptor descriptor,boolean prepared,boolean responseNeeded,int offset,byte[] value){
            byte[] copy=value.clone();main.post(()->{
                int result=BluetoothGatt.GATT_FAILURE;
                if(owns(d) && authorized && !prepared && offset==0 && descriptor.getUuid().equals(CCC) && (Arrays.equals(copy,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)||Arrays.equals(copy,BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))){
                    subscribed=Arrays.equals(copy,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);result=BluetoothGatt.GATT_SUCCESS;
                }
                if(responseNeeded)response(d,id,result,offset,null);
                if(ready())note("文字通道已连接。点输入框，用豆包语音说话。");
            });
        }
        @Override public void onDescriptorReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattDescriptor descriptor){main.post(()->response(d,id,owns(d)&&offset==0?BluetoothGatt.GATT_SUCCESS:BluetoothGatt.GATT_FAILURE,offset,subscribed?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE));}
        @Override public void onNotificationSent(BluetoothDevice d,int status){main.post(()->{if(!owns(d))return;sending=false;if(status==BluetoothGatt.GATT_SUCCESS)pump();else failSend();});}
    };
    public void close(){
        if(closed)return;closed=true;main.removeCallbacks(sendTimeout);frames.clear();pendingId=null;
        try{if(advertiser!=null && allowed())advertiser.stopAdvertising(advertising);}catch(RuntimeException ignored){}
        try{if(server!=null){if(peer!=null && allowed())server.cancelConnection(peer);server.close();}}catch(RuntimeException ignored){}
        server=null;peer=null;authorized=false;subscribed=false;
    }
}
