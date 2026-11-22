package dev.pockettrack;

import android.app.Activity;
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
    private Pad activePad;
    private float pointerSpeed=4.0f, scrollSpeed=6.0f;
    private boolean momentum=true;
    private boolean active = false, dark = false, volumeOption = true;
    private TextView status;
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
    private TextView text(String value, int sp) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(Color.WHITE); v.setPadding(0, 16, 0, 16); return v;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setOnClickListener(v -> action.run()); return b;
    }
    private void speedSetting(LinearLayout root, String label, boolean pointer) {
        float value=pointer?pointerSpeed:scrollSpeed;
        TextView caption=text(String.format(Locale.US,"%s %.1f",label,value),15);root.addView(caption);
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
        });root.addView(slider);
    }
    private void showSetup() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs = getWindow().getAttributes(); attrs.screenBrightness = -1; attrs.preferredRefreshRate=0; getWindow().setAttributes(attrs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,22,30));
        int p=(int)(24*getResources().getDisplayMetrics().density); root.setPadding(p,p*2,p,p);
        root.addView(text("PocketTrack 0.1.2", 32));
        root.addView(text("手机触控，Mac 接收。", 20));
        status = text(token.isEmpty() ? "请在 Mac 运行连接脚本完成 USB 配对。" : "USB 已配对 · 点击下方开始", 15); root.addView(status);
        root.addView(text("单指移动 · 轻点点击 · 双指滚动\n双指轻点右键 · 单指长按拖动\n电源键 / 返回 / 切后台：退出", 16));
        CheckBox option = new CheckBox(this); option.setText("音量上键代替右 Option（按住说话）"); option.setTextColor(Color.WHITE); option.setChecked(volumeOption); option.setOnCheckedChangeListener((v,c)->volumeOption=c); root.addView(option);
        speedSetting(root,"鼠标速度（慢滑精确，快滑加速）",true);
        speedSetting(root,"双指滚动速度",false);
        CheckBox inertia=new CheckBox(this);inertia.setText("惯性滚动（再次触摸立即停止）");inertia.setTextColor(Color.WHITE);inertia.setChecked(momentum);
        inertia.setOnCheckedChangeListener((v,c)->{momentum=c;getPreferences(0).edit().putBoolean("momentum",c).apply();});root.addView(inertia);
        root.addView(button("开始触控", () -> startMode(false)));
        root.addView(button("开始纯黑触控", () -> startMode(true)));
        root.addView(text("纯黑模式保留触摸，不是真正关屏。\n只经 USB 传输手势与按键；不访问手机麦克风、照片、联系人或通知。", 13));
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }
    private void startMode(boolean useDark) {
        if(token.isEmpty()) { status.setText("请先在 Mac 运行连接脚本。"); return; }
        dark=useDark;
        status.setText("正在连接 Mac…");
        if(link != null) link.close();
        Link next = new Link(token);
        link=next;
        next.start();
    }
    private void enterPad(Link current) {
        if(link != current || isFinishing()) return;
        active=true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs=getWindow().getAttributes(); attrs.screenBrightness=dark ? 0.01f : -1; attrs.preferredRefreshRate=120; getWindow().setAttributes(attrs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        activePad=new Pad();setContentView(activePad);
    }
    private void send(String value) { if(active && link != null) link.send(value); }
    private void stopMode() {
        active=false;
        if(activePad!=null){activePad.stopFling();activePad=null;}
        Link old=link; link=null;
        if(old!=null) old.close();
        showSetup();
    }
    @Override protected void onPause() { stopMode(); super.onPause(); }
    @Override protected void onDestroy() { if(link!=null) link.close(); unregisterReceiver(screenOff); super.onDestroy(); }
    @Override public void onBackPressed() { if(active) stopMode(); else super.onBackPressed(); }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if(active && volumeOption && code==KeyEvent.KEYCODE_VOLUME_UP) { if(activePad!=null)activePad.stopFling(); if(event.getRepeatCount()==0) send("OPTION 1"); return true; }
        return super.onKeyDown(code,event);
    }
    @Override public boolean onKeyUp(int code, KeyEvent event) {
        if(active && volumeOption && code==KeyEvent.KEYCODE_VOLUME_UP) { send("OPTION 0"); return true; }
        return super.onKeyUp(code,event);
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
        Pad(){ super(MainActivity.this); setBackgroundColor(Color.BLACK); setContentDescription("PocketTrack 触控区域"); }
        @Override protected void onDraw(Canvas canvas) {
            if(dark) return;
            paint.setColor(Color.rgb(125,145,170)); paint.setTextSize(18*density);
            canvas.drawText("PocketTrack · 触控中",24*density,80*density,paint);
            paint.setTextSize(13*density);
            canvas.drawText("电源键退出 · 单指移动 · 双指滚动",24*density,114*density,paint);
            if(link!=null && link.roundTripMs>=0) canvas.drawText("USB 往返 " + link.roundTripMs + " ms · 音量上键 = 右 Option",24*density,144*density,paint);
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
            } catch(Exception ignored) {
                // No payloads, key values, or user data are logged.
            } finally {
                close();
                ui.post(()->{if(link==this){stopMode();status.setText("连接已停止。请检查 USB 和 Mac 接收端后重新开始。");}});
            }
        }
    }
}
