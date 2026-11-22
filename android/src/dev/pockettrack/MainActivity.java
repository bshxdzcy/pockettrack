package dev.pockettrack;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private final Handler ui = new Handler();
    private Link link;
    private BluetoothController bluetooth;
    private boolean bluetoothMode=true, externalBluetoothDialog=false;
    private static final int NEARBY=41, ENABLE_BT=42, DISCOVER_BT=43;
    private BluetoothDevice selectedMac;
    private Pad activePad;
    private float pointerSpeed=4.0f, scrollSpeed=6.0f;
    private boolean momentum=true;
    private boolean active = false, dark = false, volumeOption = true;
    private TextView status;
    private Button normalStart,darkStart;
    private String token = "";
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { stopMode(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        token = getPreferences(0).getString("token", "");
        pointerSpeed=getPreferences(0).getFloat("pointer_speed",4.0f);
        scrollSpeed=getPreferences(0).getFloat("scroll_speed",6.0f);
        momentum=getPreferences(0).getBoolean("momentum",true);
        volumeOption=getPreferences(0).getBoolean("volume_option",true);
        bluetoothMode=getPreferences(0).getBoolean("bluetooth_mode",true);
        acceptToken(getIntent());
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        showSetup();
    }
    private void acceptToken(Intent intent) {
        String supplied = intent.getStringExtra("token");
        if (supplied != null && supplied.matches("[0-9a-f]{64}")) {
            token = supplied;
            getPreferences(0).edit().putString("token", token).apply();
            intent.removeExtra("token");
        }
    }
    private TextView text(String value,int sp){return Ui.label(this,value,sp,Ui.TEXT,false);}
    private Button button(String label,Runnable action){return Ui.button(this,label,false,action);}
    private int dp(float value){return Ui.dp(this,value);}
    private void fullButton(LinearLayout root,Button button){root.addView(button,new LinearLayout.LayoutParams(-1,-2));}
    private void hint(LinearLayout root,String title,String detail){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=Ui.label(this,title,14,Ui.TEXT,false);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView value=Ui.label(this,detail,14,Ui.MUTED,false);value.setGravity(Gravity.END);row.addView(value);
        root.addView(row);
    }
    private void speedSetting(LinearLayout root, String label, boolean pointer) {
        float value=pointer?pointerSpeed:scrollSpeed;
        TextView caption=text(String.format(Locale.US,"%s %.1f",label,value),15);root.addView(caption);
        Ui.gap(root,12);
        SeekBar slider=new SeekBar(this);slider.setMax(pointer?75:150);slider.setProgress(Math.round((value-(pointer?0.5f:2.0f))*10));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                float speed=(pointer?0.5f:2.0f)+progress/10.0f;
                if(pointer)pointerSpeed=speed;else scrollSpeed=speed;
                caption.setText(String.format(Locale.US,"%s %.1f",label,speed));
                if(fromUser)getPreferences(0).edit().putFloat(pointer?"pointer_speed":"scroll_speed",speed).apply();
            }
        });root.addView(slider);Ui.gap(root,24);
    }
    private void showSetup() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs=getWindow().getAttributes();attrs.screenBrightness=-1;attrs.preferredRefreshRate=0;getWindow().setAttributes(attrs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root=Ui.column(this);root.setPadding(dp(24),dp(16),dp(24),dp(20));root.setBackgroundColor(Ui.BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=Ui.column(this);titles.addView(Ui.label(this,"手机触控板",28,Ui.TEXT,true));Ui.gap(titles,7);titles.addView(Ui.label(this,"PocketTrack",13,Ui.MUTED,false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("设置",()->showSettings());header.addView(settings,new LinearLayout.LayoutParams(dp(72),dp(48)));root.addView(header);Ui.gap(root,28);

        LinearLayout connection=Ui.column(this);connection.setPadding(dp(18),dp(18),dp(18),dp(18));connection.setBackground(Ui.shape(this,Ui.SURFACE,22,true));
        connection.addView(Ui.label(this,"连接到 Mac",18,Ui.TEXT,true));Ui.gap(connection,16);
        LinearLayout modes=new LinearLayout(this);modes.setPadding(dp(4),dp(4),dp(4),dp(4));modes.setBackground(Ui.shape(this,Ui.BG,14,false));
        for(boolean wireless:new boolean[]{true,false}){
            Button choice=Ui.button(this,wireless?"蓝牙":"USB",false,()->{if(bluetoothMode!=wireless){bluetoothMode=wireless;getPreferences(0).edit().putBoolean("bluetooth_mode",wireless).apply();stopMode();}});
            choice.setMinHeight(dp(44));choice.setMinimumHeight(dp(44));choice.setTextSize(14);
            choice.setTextColor(bluetoothMode==wireless?Ui.TEXT:Ui.MUTED);
            choice.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227E9982),Ui.shape(this,bluetoothMode==wireless?Ui.BORDER:Ui.BG,10,false),null));
            choice.setSelected(bluetoothMode==wireless);choice.setContentDescription((wireless?"蓝牙":"USB")+(bluetoothMode==wireless?"，已选择":""));
            modes.addView(choice,new LinearLayout.LayoutParams(0,-2,1));
        }
        connection.addView(modes);Ui.gap(connection,16);
        String state=bluetoothMode?(bluetooth!=null && bluetooth.isConnected()?"蓝牙已连接，可以开始触控。":"无线连接，无需 USB 线。"):(token.isEmpty()?"先在 Mac 完成 USB 配对。":"USB 配对已保存，开始时连接。");
        status=Ui.label(this,state,14,Ui.MUTED,false);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);connection.addView(status);
        if(bluetoothMode){Ui.gap(connection,14);Button connect=button("连接蓝牙 Mac",()->beginBluetooth());connect.setTextColor(Ui.ACCENT);fullButton(connection,connect);}
        root.addView(connection);Ui.gap(root,24);

        LinearLayout actions=Ui.column(this);actions.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.Mark mark=new Ui.Mark(this);actions.addView(mark,new LinearLayout.LayoutParams(dp(76),dp(76)));Ui.gap(actions,10);
        TextView ready=Ui.label(this,"把手机，变成触控板",21,Ui.TEXT,true);ready.setGravity(Gravity.CENTER);actions.addView(ready);Ui.gap(actions,7);
        TextView sub=Ui.label(this,"单指移动 · 双指滚动",14,Ui.MUTED,false);sub.setGravity(Gravity.CENTER);actions.addView(sub);Ui.gap(actions,24);
        normalStart=Ui.button(this,"开始触控",true,()->startMode(false));fullButton(actions,normalStart);Ui.gap(actions,10);
        darkStart=button("纯黑触控",()->startMode(true));fullButton(actions,darkStart);Ui.gap(actions,10);
        TextView blackHint=Ui.label(this,"全黑画面，触摸保持可用",12,Ui.MUTED,false);blackHint.setGravity(Gravity.CENTER);actions.addView(blackHint);
        root.addView(actions);Ui.gap(root,24);Ui.line(root);
        hint(root,"语音输入",volumeOption?"音量上键 → 右 Option":"已关闭，可在设置开启");Ui.gap(root,16);hint(root,"退出触控","按一下电源键");Ui.gap(root,24);
        TextView footer=Ui.label(this,"只传手势与按键，不读取手机内容",12,Ui.MUTED,false);footer.setGravity(Gravity.CENTER);root.addView(footer);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Ui.BG);scroll.setClipToPadding(false);scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });setContentView(scroll);scroll.requestApplyInsets();
    }
    private void showSettings(){
        LinearLayout body=Ui.column(this);body.setPadding(dp(24),dp(24),dp(24),dp(28));
        body.addView(Ui.label(this,"调成顺手的感觉",23,Ui.TEXT,true));Ui.gap(body,8);body.addView(Ui.label(this,"设置会自动保存在这部手机",13,Ui.MUTED,false));Ui.gap(body,28);
        speedSetting(body,"鼠标速度",true);speedSetting(body,"滚动速度",false);
        android.widget.Switch inertia=new android.widget.Switch(this);inertia.setText("惯性滚动");inertia.setTextColor(Ui.TEXT);inertia.setTextSize(16);inertia.setMinHeight(dp(48));inertia.setChecked(momentum);
        inertia.setOnCheckedChangeListener((v,c)->{momentum=c;getPreferences(0).edit().putBoolean("momentum",c).apply();});body.addView(inertia);
        body.addView(Ui.label(this,"松手继续滚动，再次触摸即停",13,Ui.MUTED,false));Ui.line(body);
        android.widget.Switch option=new android.widget.Switch(this);option.setText("音量上键唤起语音");option.setTextColor(Ui.TEXT);option.setTextSize(16);option.setMinHeight(dp(48));option.setChecked(volumeOption);
        option.setOnCheckedChangeListener((v,c)->{volumeOption=c;getPreferences(0).edit().putBoolean("volume_option",c).apply();});body.addView(option);
        body.addView(Ui.label(this,"映射 Mac 右 Option，使用 Mac 麦克风",13,Ui.MUTED,false));Ui.line(body);
        body.addView(Ui.label(this,"轻点点击 · 双指轻点右键\n长按拖动 · 返回或切后台退出",14,Ui.MUTED,false));Ui.gap(body,22);
        body.addView(Ui.label(this,"纯黑不是关屏。蓝牙仅使用附近设备权限，不读取照片、联系人、定位或麦克风。",13,Ui.MUTED,false));Ui.gap(body,12);
        body.addView(Ui.label(this,"PocketTrack 0.2.0",12,Ui.MUTED,false));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setView(scroll).setPositiveButton("完成",null).create();dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(Ui.shape(this,Ui.SURFACE,24,false));dialog.getWindow().setLayout(-1,-2);dialog.getWindow().setGravity(Gravity.BOTTOM);}
    }
    private void startMode(boolean useDark) {
        if(bluetoothMode){
            if(bluetooth==null || !bluetooth.isConnected()){status.setText("请先点“连接蓝牙 Mac”，连接成功后再开始触控。");return;}
            dark=useDark;activatePad();return;
        }
        if(link!=null)return;
        if(token.isEmpty()) { status.setText("请先在 Mac 运行连接脚本。"); return; }
        dark=useDark;
        status.setText("正在连接 Mac…");
        normalStart.setEnabled(false);darkStart.setEnabled(false);
        if(link != null) link.close();
        Link next = new Link(token);
        link=next;
        next.start();
    }
    private void enterPad(Link current) {
        if(link != current || isFinishing()) return;
        activatePad();
    }
    private void activatePad() {
        active=true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs=getWindow().getAttributes(); attrs.screenBrightness=dark ? 0.01f : -1; attrs.preferredRefreshRate=120; getWindow().setAttributes(attrs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        activePad=new Pad();setContentView(activePad);
    }
    private void send(String value) {
        if(!active)return;
        if(bluetoothMode){if(bluetooth!=null)bluetooth.send(value);}
        else if(link!=null)link.send(value);
    }
    private void stopMode() {
        active=false;
        if(activePad!=null){activePad.stopFling();activePad=null;}
        Link old=link; link=null;
        if(old!=null) old.close();
        BluetoothController previous=bluetooth;bluetooth=null;if(previous!=null)previous.close();
        showSetup();
    }
    @Override protected void onPause() { if(active || link!=null)stopMode(); super.onPause(); }
    @Override protected void onStop() { if(!externalBluetoothDialog && bluetooth!=null)stopMode(); super.onStop(); }
    @Override protected void onResume() { super.onResume();if(bluetooth!=null)bluetooth.ensureRegistered(); }
    @Override protected void onDestroy() { if(link!=null)link.close();if(bluetooth!=null)bluetooth.close();unregisterReceiver(screenOff);super.onDestroy(); }
    @Override public void onBackPressed() { if(active) stopMode(); else super.onBackPressed(); }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if(active && volumeOption && code==KeyEvent.KEYCODE_VOLUME_UP) { if(activePad!=null)activePad.stopFling(); if(event.getRepeatCount()==0) send("OPTION 1"); return true; }
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if(active && volumeOption && code==KeyEvent.KEYCODE_VOLUME_UP) { send("OPTION 0"); return true; }
        return super.onKeyUp(code,event);
    }
    private BluetoothAdapter adapter(){
        BluetoothManager manager=getSystemService(BluetoothManager.class);return manager==null?null:manager.getAdapter();
    }
    private boolean nearbyGranted(){
        return Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);
    }
    private void beginBluetooth(){
        if(Build.VERSION.SDK_INT<28){status.setText("蓝牙键鼠需要 Android 9 或更新版本；本机可以使用 USB。");return;}
        if(bluetooth!=null){status.setText(bluetooth.isConnected()?"蓝牙已连接，可以开始触控。":"正在连接，请完成 Mac 上的配对；退出再进入可重试。");return;}
        if(!nearbyGranted()){
            new AlertDialog.Builder(this).setTitle("允许附近设备")
                .setMessage("用于让 Mac 发现并连接这部手机，接收鼠标和右 Option 按键。不扫描周围设备，不读取定位、照片、联系人或麦克风。")
                .setNegativeButton("取消",null).setPositiveButton("继续",(d,w)->{
                    externalBluetoothDialog=true;
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},NEARBY);
                }).show();return;
        }
        try {
            BluetoothAdapter a=adapter();
            if(a==null){status.setText("这部手机没有可用的蓝牙适配器。");return;}
            if(!a.isEnabled()){
                externalBluetoothDialog=true;startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE_BT);return;
            }
            chooseMac();
        } catch(SecurityException error){status.setText("附近设备权限已变化，请重新点击连接。");}
          catch(ActivityNotFoundException error){externalBluetoothDialog=false;status.setText("无法打开系统蓝牙界面，请手动开启蓝牙后重试。");}
    }
    private void chooseMac(){
        java.util.ArrayList<BluetoothDevice> devices=new java.util.ArrayList<>(adapter().getBondedDevices());
        String[] labels=new String[devices.size()+1];
        for(int i=0;i<devices.size();i++){String name=devices.get(i).getName();labels[i]=(name==null?"已配对设备 "+(i+1):name);}
        labels[devices.size()]="配对新的 Mac（让手机可被发现 120 秒）";
        new AlertDialog.Builder(this).setTitle("选择你的 Mac").setItems(labels,(d,index)->{
            if(index<devices.size()){selectedMac=devices.get(index);startBluetooth();}
            else {
                selectedMac=null;
                try {
                    externalBluetoothDialog=true;
                    Intent request=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);request.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,120);
                    startActivityForResult(request,DISCOVER_BT);
                } catch(SecurityException | ActivityNotFoundException error){externalBluetoothDialog=false;status.setText("无法开启可发现状态，请检查附近设备权限和系统蓝牙设置。");}
            }
        }).setNegativeButton("取消",null).show();
    }
    private void startBluetooth(){
        if(isFinishing() || !bluetoothMode)return;
        status.setText("正在准备蓝牙键鼠…");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        BluetoothController next=new BluetoothController(this,new BluetoothController.Listener(){
            public void ready(BluetoothController who){
                if(bluetooth!=who)return;
                if(selectedMac!=null){status.setText("正在连接所选 Mac…");who.connect(selectedMac);}
                else {status.setText("请在 Mac“系统设置 → 蓝牙”连接这部手机，并确认两端配对提示。请保持本页面打开。");who.waitForPairing();}
            }
            public void connected(BluetoothController who,BluetoothDevice device){
                if(bluetooth!=who)return;
                status.setText("蓝牙已连接 · 现在可以开始触控或纯黑触控，USB 可以拔掉。");
            }
            public void stopped(BluetoothController who,String reason){
                if(bluetooth!=who)return;
                bluetooth=null;stopMode();status.setText(reason);
            }
        });
        bluetooth=next;next.start();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request!=NEARBY)return;externalBluetoothDialog=false;
        if(nearbyGranted()){beginBluetooth();return;}
        status.setText("未获得附近设备权限；仍可切换到 USB 使用。");
        boolean blocked=false;
        for(String permission:permissions)if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED && !shouldShowRequestPermissionRationale(permission))blocked=true;
        if(blocked)new AlertDialog.Builder(this).setTitle("蓝牙权限未开启").setMessage("如需蓝牙，可以在应用设置中手动允许“附近设备”。")
            .setNegativeButton("暂不",null).setPositiveButton("应用设置",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())))).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==ENABLE_BT || request==DISCOVER_BT)externalBluetoothDialog=false;
        if(request==ENABLE_BT){if(result==RESULT_OK)beginBluetooth();else status.setText("手机蓝牙未开启。");}
        if(request==DISCOVER_BT){if(result>0)startBluetooth();else status.setText("已取消蓝牙配对。可以重试或切换 USB。");}
    }
    private final class Pad extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density=getResources().getDisplayMetrics().density;
        float x,y,travel;
        long began,lastMotion,lastScroll,flingAt;
        final MotionDynamics dynamics=new MotionDynamics();
        final Runnable fling=new Runnable(){public void run(){
            if(!active || !momentum || !dynamics.hasMomentum())return;
            long now=android.os.SystemClock.uptimeMillis(),dt=now-flingAt;flingAt=now;
            if(dt>80){stopFling();return;}
            double[] delta=dynamics.step(dt);sendMotion("SCROLL",(float)delta[0],(float)delta[1]);
            postOnAnimation(this);
        }};
        void stopFling(){removeCallbacks(fling);dynamics.reset();}
        void sendMotion(String cmd,float dx,float dy){
            int parts=Math.max(1,(int)Math.ceil(Math.max(Math.abs(dx),Math.abs(dy))/500));
            if(Math.abs(dx)+Math.abs(dy)>0.001)for(int i=0;i<parts;i++)send(cmd+" "+(dx/parts)+" "+(dy/parts));
        }
        int maximum;
        boolean drag, canceled;
        final Runnable hold=()-> { if(active && maximum==1 && travel<10 && !canceled) { drag=true; send("DRAG 1"); } };
        Pad(){ super(MainActivity.this); setBackgroundColor(dark?Color.BLACK:Ui.BG); setContentDescription("PocketTrack 触控区域"); }
        @Override protected void onDraw(Canvas canvas) {
            if(dark) return;
            paint.setColor(Ui.MUTED);paint.setTextSize(13*density);
            canvas.drawText(bluetoothMode?"蓝牙已连接":"USB 已连接",24*density,64*density,paint);
            paint.setColor(Ui.TEXT);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(26*density);
            canvas.drawText("触控中",24*density,105*density,paint);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(14*density);paint.setColor(Ui.MUTED);
            canvas.drawText("在任意位置滑动",24*density,137*density,paint);
            paint.setColor(Ui.BORDER);
            for(float px=24*density;px<getWidth()-16*density;px+=24*density)for(float py=184*density;py<getHeight()-140*density;py+=24*density)canvas.drawCircle(px,py,density,paint);
            paint.setColor(Ui.MUTED);paint.setTextSize(13*density);
            canvas.drawText("双指滚动 · 音量上键唤起语音",24*density,getHeight()-90*density,paint);
            canvas.drawText("按一下电源键退出",24*density,getHeight()-60*density,paint);
            if(link!=null && link.roundTripMs>=0){paint.setTextSize(11*density);canvas.drawText("USB 往返 "+link.roundTripMs+" ms",24*density,getHeight()-34*density,paint);}
            postInvalidateDelayed(500);
        }
        float cx(MotionEvent e) {float n=0; for(int i=0;i<e.getPointerCount();i++) n+=e.getX(i); return n/e.getPointerCount()/density;}
        float cy(MotionEvent e) {float n=0; for(int i=0;i<e.getPointerCount();i++) n+=e.getY(i); return n/e.getPointerCount()/density;}
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(!active) return true;
            if(link!=null)link.touchLag.accumulateAndGet((int)Math.max(0,android.os.SystemClock.uptimeMillis()-e.getEventTime()),Math::max);
            int action=e.getActionMasked();
            float nx=cx(e),ny=cy(e);
            switch(action) {
                case MotionEvent.ACTION_DOWN:
                    stopFling();lastMotion=e.getEventTime();lastScroll=0;
                    x=nx;y=ny;travel=0;maximum=1;drag=false;canceled=false;began=e.getEventTime();ui.postDelayed(hold,450);break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    ui.removeCallbacks(hold); maximum=Math.max(maximum,e.getPointerCount());
                    if(drag) {send("DRAG 0");drag=false;canceled=true;}
                    dynamics.reset();lastMotion=e.getEventTime();x=nx;y=ny;break;
                case MotionEvent.ACTION_MOVE:
                    float dx=nx-x,dy=ny-y; travel+=Math.hypot(dx,dy);
                    if(travel>10) ui.removeCallbacks(hold);
                    if(!canceled && maximum<=2 && (maximum==1 || e.getPointerCount()==2)) {
                        String cmd=e.getPointerCount()==2 ? "SCROLL" : "MOVE";
                        long dt=Math.max(1,e.getEventTime()-lastMotion);
                        if(cmd.equals("MOVE")) {
                            float gain=dynamics.pointerGain((float)Math.hypot(dx,dy),dt,pointerSpeed);
                            sendMotion(cmd,dx*gain,dy*gain);
                        } else {
                            dx*=scrollSpeed;dy*=scrollSpeed;
                            dynamics.recordScroll(dx,dy,dt);lastScroll=e.getEventTime();sendMotion(cmd,dx,dy);
                        }
                    }
                    lastMotion=e.getEventTime();x=nx;y=ny;break;
                case MotionEvent.ACTION_POINTER_UP:
                    ui.removeCallbacks(hold); break; // Do not convert the remaining finger into a new gesture.
                case MotionEvent.ACTION_UP:
                    ui.removeCallbacks(hold);
                    if(drag) send("DRAG 0");
                    else if(!canceled && travel<10 && e.getEventTime()-began<350) {
                        if(maximum==1) send("CLICK L"); else if(maximum==2) send("CLICK R");
                    }
                    if(!drag && !canceled && maximum==2 && travel>=10 && momentum && e.getEventTime()-lastScroll<80 && dynamics.shouldFling()){
                        flingAt=android.os.SystemClock.uptimeMillis();postOnAnimation(fling);
                    }
                    drag=false;performClick();break;
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(hold);stopFling(); if(drag)send("DRAG 0"); drag=false;canceled=true;break;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDetachedFromWindow(){ui.removeCallbacks(hold);stopFling();super.onDetachedFromWindow();}
    }
    private final class Link extends Thread {
        final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(128);
        final String secret;
        volatile boolean running=true;
        String failure="连接已停止。请检查 USB 和 Mac 接收端。";
        volatile Socket socket;
        volatile long lastReply, pingSent;
        volatile long roundTripMs = -1;
        final java.util.concurrent.atomic.AtomicInteger queueHigh=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger touchLag=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger batches=new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger writeLag=new java.util.concurrent.atomic.AtomicInteger();
        Link(String secret){super("PocketTrack-USB");this.secret=secret;}
        void send(String message){if(!queue.offer(message))close();queueHigh.accumulateAndGet(queue.size(),Math::max);}
        void close(){running=false;queue.clear();Socket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}interrupt();}
        @Override public void run() {
            try {
                Socket s=new Socket();socket=s;
                if(!running){s.close();return;}
                s.connect(new InetSocketAddress("127.0.0.1",49671),2000);s.setTcpNoDelay(true);s.setSoTimeout(2000);
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8));
                BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
                out.write("AUTH "+secret+"\n");out.flush();
                if(!"OK".equals(in.readLine()))throw new IOException("Pairing failed");
                if(!running)return;
                ui.post(()->enterPad(this));
                lastReply=android.os.SystemClock.elapsedRealtime();
                Thread reader = new Thread(() -> {
                    try {
                        long reported=0;
                        while(running) {
                            if(!"PONG".equals(in.readLine()))throw new IOException("Disconnected");
                            lastReply=android.os.SystemClock.elapsedRealtime();
                            roundTripMs=Math.max(0,lastReply-pingSent);
                            if(lastReply-reported>=2000){
                                android.util.Log.i("PocketTrackPerf","rtt_ms="+roundTripMs+" queue_max="+queueHigh.getAndSet(0)+" touch_lag_ms="+touchLag.getAndSet(0)+" write_max_ms="+writeLag.getAndSet(0)+" batches="+batches.getAndSet(0));
                                reported=lastReply;
                            }
                        }
                    } catch(Exception ignored) { close(); }
                }, "PocketTrack-Heartbeat");
                reader.setDaemon(true);reader.start();
                long lastPing=0;
                while(running) {
                    String message=queue.poll(8,TimeUnit.MILLISECONDS);
                    if(message!=null){
                        MotionBatch batch=new MotionBatch();batch.add(message);
                        for(int i=0;i<127;i++) { String next=queue.poll();if(next==null)break;batch.add(next); }
                        long writeStart=android.os.SystemClock.uptimeMillis();
                        out.write(batch.finish());out.flush();batches.incrementAndGet();
                        writeLag.accumulateAndGet((int)(android.os.SystemClock.uptimeMillis()-writeStart),Math::max);
                    }
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-lastReply>2000)throw new IOException("Heartbeat timeout");
                    if(now-lastPing>500){
                        pingSent=now;out.write("PING\n");out.flush();lastPing=now;
                    }
                }
            } catch(Exception error) {
                if(error instanceof ConnectException)failure="USB 通道不可用。重新插线后，需要在 Mac 恢复 USB 连接。";
                else if(error instanceof SocketTimeoutException)failure="Mac 响应超时，请检查接收端是否正在运行。";
                // Do not log payloads, keys, or exception text containing connection details.
            } finally {
                close();
                ui.post(()->{if(link==this){
                    if(active)stopMode();else link=null;
                    normalStart.setEnabled(true);darkStart.setEnabled(true);status.setText(failure);
                }});
            }
        }
    }
}
