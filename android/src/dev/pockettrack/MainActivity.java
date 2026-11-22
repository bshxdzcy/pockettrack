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
    private boolean active = false, dark = false, volumeOption = true;
    private TextView status;
    private String token = "";
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { stopMode(); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        token = getPreferences(0).getString("token", "");
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
    private void showSetup() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams attrs = getWindow().getAttributes(); attrs.screenBrightness = -1; getWindow().setAttributes(attrs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,22,30));
        int p=(int)(24*getResources().getDisplayMetrics().density); root.setPadding(p,p*2,p,p);
        root.addView(text("PocketTrack", 32));
        root.addView(text("手机触控，Mac 接收。", 20));
        status = text(token.isEmpty() ? "请在 Mac 运行连接脚本完成 USB 配对。" : "USB 已配对 · 点击下方开始", 15); root.addView(status);
        root.addView(text("单指移动 · 轻点点击 · 双指滚动\n双指轻点右键 · 单指长按拖动\n电源键 / 返回 / 切后台：退出", 16));
        CheckBox option = new CheckBox(this); option.setText("音量上键代替 Option（按住说话）"); option.setTextColor(Color.WHITE); option.setChecked(volumeOption); option.setOnCheckedChangeListener((v,c)->volumeOption=c); root.addView(option);
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
        WindowManager.LayoutParams attrs=getWindow().getAttributes(); attrs.screenBrightness=dark ? 0.01f : -1; getWindow().setAttributes(attrs);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(new Pad());
    }
    private void send(String value) { if(active && link != null) link.send(value); }
    private void stopMode() {
        active=false;
        Link old=link; link=null;
        if(old!=null) old.close();
        showSetup();
    }
    @Override protected void onPause() { stopMode(); super.onPause(); }
    @Override protected void onDestroy() { if(link!=null) link.close(); unregisterReceiver(screenOff); super.onDestroy(); }
    @Override public void onBackPressed() { if(active) stopMode(); else super.onBackPressed(); }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if(active && volumeOption && code==KeyEvent.KEYCODE_VOLUME_UP) { if(event.getRepeatCount()==0) send("OPTION 1"); return true; }
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
        long began;
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
        }
        float cx(MotionEvent e) {float n=0; for(int i=0;i<e.getPointerCount();i++) n+=e.getX(i); return n/e.getPointerCount()/density;}
        float cy(MotionEvent e) {float n=0; for(int i=0;i<e.getPointerCount();i++) n+=e.getY(i); return n/e.getPointerCount()/density;}
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(!active) return true;
            int action=e.getActionMasked();
            float nx=cx(e),ny=cy(e);
            switch(action) {
                case MotionEvent.ACTION_DOWN:
                    x=nx;y=ny;travel=0;maximum=1;drag=false;canceled=false;began=e.getEventTime();ui.postDelayed(hold,450);break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    ui.removeCallbacks(hold); maximum=Math.max(maximum,e.getPointerCount());
                    if(drag) {send("DRAG 0");drag=false;canceled=true;}
                    x=nx;y=ny;break;
                case MotionEvent.ACTION_MOVE:
                    float dx=nx-x,dy=ny-y; travel+=Math.hypot(dx,dy);
                    if(travel>10) ui.removeCallbacks(hold);
                    if(!canceled && maximum<=2 && (maximum==1 || e.getPointerCount()==2)) {
                        String cmd=e.getPointerCount()==2 ? "SCROLL" : "MOVE";
                        float scale=cmd.equals("MOVE") ? 1.6f : 1.1f;
                        dx=Math.max(-500,Math.min(500,dx*scale));dy=Math.max(-500,Math.min(500,dy*scale));
                        if(Math.abs(dx)+Math.abs(dy)>0.01) send(String.format(Locale.US,"%s %.2f %.2f",cmd,dx,dy));
                    }
                    x=nx;y=ny;break;
                case MotionEvent.ACTION_POINTER_UP:
                    ui.removeCallbacks(hold); break; // Do not convert the remaining finger into a new gesture.
                case MotionEvent.ACTION_UP:
                    ui.removeCallbacks(hold);
                    if(drag) send("DRAG 0");
                    else if(!canceled && travel<10 && e.getEventTime()-began<350) {
                        if(maximum==1) send("CLICK L"); else if(maximum==2) send("CLICK R");
                    }
                    drag=false;performClick();break;
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(hold); if(drag)send("DRAG 0"); drag=false;canceled=true;break;
            }
            return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override protected void onDetachedFromWindow(){ui.removeCallbacks(hold);super.onDetachedFromWindow();}
    }
    private final class Link extends Thread {
        final ArrayBlockingQueue<String> queue=new ArrayBlockingQueue<>(128);
        final String secret;
        volatile boolean running=true;
        volatile Socket socket;
        Link(String secret){super("PocketTrack-USB");this.secret=secret;}
        void send(String message){if(!queue.offer(message))close();}
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
                long lastPing=0;
                while(running) {
                    String message=queue.poll(50,TimeUnit.MILLISECONDS);
                    if(message!=null){out.write(message);out.write('\n');out.flush();}
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-lastPing>500){
                        out.write("PING\n");out.flush();
                        if(!"PONG".equals(in.readLine()))throw new IOException("Disconnected");
                        lastPing=now;
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
