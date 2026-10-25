package com.example.aries;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌宠服务（重写版）：两层窗口 + WebView 画层 + AI 回复自动驱动。
 *
 * 和你原来那版的区别，也是你两个症状的根因：
 *  1) 画层设成 FLAG_NOT_TOUCHABLE，全宽贴屏幕底边。它不挡任何操作，所以可以放心给大，
 *     模型不会再被窗口边裁掉——你原来是在固定窗口里缩画布内容，Canvas 一定被 View 边界裁。
 *  2) 交互靠另一个贴在身体外的小触摸窗（只有身体那么大），框外照样穿透到下层 app。
 *     你原来的 onTouch return false 并不会把触摸交给下面的 app，Android 是把它丢掉。
 *  3) 渲染直接用形象包自己的渲染层（kit/body.js + kit/rig.js + whale/figure.js），
 *     所以走跑跳坐睡、19 表情 18 动作、被拎甩出、口型、弹簧、8 配色 ×3 服装全都是现成的。
 *
 * 需要放进 assets 的文件见 INTEGRATION.md。targetSdk 30 下前台服务类型已在 Manifest 声明，够用。
 */
public class PetService extends Service {

    /** 画层里跑的是 assets/coopanion/overlay.html。 */
    private static final String PAGE = "coopanion/overlay.html";   // 与 src/ kit/ whale/ 同级
    /** 身体 237 逻辑单位 + 跳起 + 鞋底留白：368 × S + 22，S 由窗口高度反推。 */
    private static final int STRIP_DP = 360;
    private static final int MAX_SAY_CHARS = 220;
    private static final float TYPE_MS_PER_CHAR = 70f;

    private FloatingPetWindow petWindow;
    private WebView render;
    private PetAssetServer assetServer;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Map<String, StringBuilder> streaming = new HashMap<>();
    private long lastProactiveAt = 0L;                 // 上次主动开口的时刻
    private volatile String petRequestedTopic;         // 桌宠自己发起的请求还在跑，避免插话撞车

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(2, buildNotification());
        startServerAndWindow();
        registerAiReceiver();
    }

    // ── 资源服务：WebView 要用 http 源才认 ES module ─────────────────────────
    private void startServerAndWindow() {
        try {
            assetServer = new PetAssetServer(new PetAssetServer.Source() {
                @Override public byte[] read(String rel) { return readAsset(rel); }
            });
            int port = assetServer.start();

            render = new WebView(this);
            render.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            WebSettings ws = render.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);                      // sound.js 的开关存 localStorage
            ws.setAllowFileAccess(false);                       // 只走本机 http，不开文件访问
            ws.setMediaPlaybackRequiresUserGesture(false);
            render.addJavascriptInterface(new Bridge(), "Android");

            petWindow = new FloatingPetWindow(this);
            petWindow.setRenderTopFromBottom(true);
            if (!petWindow.show(render, dp(STRIP_DP))) {        // 没权限会引导去设置并返回 false
                stopSelf();
                return;
            }
            petWindow.setTouchForwarder(new FloatingPetWindow.TouchForwarder() {
                @Override public void onPointer(String type, float x, float y) {
                    callJs("pointer", "{\"type\":\"" + type + "\",\"x\":" + (int) x + ",\"y\":" + (int) y + "}");
                }
            });
            render.loadUrl("http://127.0.0.1:" + port + "/" + PAGE + "?scale=" + scaleForStrip());
        } catch (IOException e) {
            stopSelf();
        }
    }

    /** 想要身体约 250dp 高：S = 250dp 换算成像素 / 237，再回推组件的 scale。 */
    private float scaleForStrip() {
        float density = getResources().getDisplayMetrics().density;
        float s = dp(250f) / 237f;                              // 每逻辑单位要多少像素
        float stripH = dp(STRIP_DP);
        float auto = 0.42f * Math.min(1.4f, stripH / 420f);     // 组件自带的比例公式
        return Math.max(0.4f, s / auto);
    }

    private byte[] readAsset(String rel) {
        InputStream in = null;
        try {
            in = getAssets().open(rel);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
    }

    // ── AI 回复 → 桌宠动作 ────────────────────────────────────────────────
    private void registerAiReceiver() {
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                String topicId = intent.getStringExtra(AIChatService.EXTRA_TOPIC_ID);
                if (topicId == null || "REFRESH_TITLE".equals(topicId)) return;
                if (AIChatService.ACTION_UPDATE.equals(intent.getAction())) {
                    String chunk = intent.getStringExtra(AIChatService.EXTRA_CHUNK);
                    if (chunk == null) return;
                    StringBuilder sb = streaming.get(topicId);
                    if (sb == null) { sb = new StringBuilder(); streaming.put(topicId, sb); }
                    sb.append(chunk);
                } else if (AIChatService.ACTION_FINISH.equals(intent.getAction())) {
                    if (topicId.equals(petRequestedTopic)) petRequestedTopic = null;
                    StringBuilder sb = streaming.remove(topicId);
                    if (sb == null) return;
                    if (intent.getStringExtra(AIChatService.EXTRA_ERROR) != null) return;
                    react(sb.toString());
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(AIChatService.ACTION_UPDATE);
        f.addAction(AIChatService.ACTION_FINISH);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(r, f);
        }
    }

    /** 一句回复拆成 表情/动作 + 分段正文，按打字速度排开演。 */
    private void react(String raw) {
        List<PetScript.Event> events = PetScript.parse(raw);
        // 模型没写【】标记时，按文本线索兜一个表情；兜不出来就不做，别乱做
        if (!hasWord(events)) {
            String mood = PetMood.pick(raw);
            if (mood != null) events = withLeading(events, mood);
        }
        float atMs = 0f;
        for (PetScript.Event e : events) {
            if (e.isWord()) {
                schedule(atMs, "do", "\"" + e.value + "\"");
            } else {
                String text = e.value.length() > MAX_SAY_CHARS ? e.value.substring(0, MAX_SAY_CHARS) + "…" : e.value;
                schedule(atMs, "say", quote(text));
                atMs += text.length() * TYPE_MS_PER_CHAR + 400f;
            }
        }
    }

    private static boolean hasWord(List<PetScript.Event> events) {
        for (PetScript.Event e : events) if (e.isWord()) return true;
        return false;
    }

    private static List<PetScript.Event> withLeading(List<PetScript.Event> events, String moodId) {
        List<PetScript.Event> out = new ArrayList<>();
        out.add(new PetScript.Event(PetScript.Kind.WORD, moodId));
        out.addAll(events);
        return out;
    }

    private void schedule(float delayMs, String method, String jsonArg) {
        ui.postDelayed(() -> callJs(method, jsonArg), (long) delayMs);
    }

    private void callJs(String method, String jsonArg) {
        final String js = "window.petCall && window.petCall('" + method + "'," + jsonArg + ")";
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (render != null) render.evaluateJavascript(js, null);
        } else {
            ui.post(() -> { if (render != null) render.evaluateJavascript(js, null); });
        }
    }

    /** 正文里可能有引号、反斜杠、换行，转义交给 JSONObject.quote，别自己拼。 */
    private static String quote(String s) {
        try { return JSONObject.quote(s); } catch (Exception e) { return "\"\""; }
    }

    /** JS 侧往上报的两个口子：身体框与事件。 */
    class Bridge {
        @JavascriptInterface
        public void setBox(String json) {
            try {
                JSONObject o = new JSONObject(json);
                if (petWindow != null) {
                    petWindow.setBodyBox((float) o.getDouble("x"), (float) o.getDouble("y"),
                            (float) o.getDouble("w"), (float) o.getDouble("h"));
                }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void onEvent(String json) {
            try {
                JSONObject o = new JSONObject(json);
                if (!"touch".equals(o.optString("kind"))) return;
                // detail 是嵌套对象：{kind:"touch", detail:{kind:"poke"}}
                // 直接 optString("detail") 会拿到整个对象的文本，六种触感文案会退化成同一条
                JSONObject det = o.optJSONObject("detail");
                final String touchKind = det == null ? "" : det.optString("kind");
                ui.post(() -> maybeSpeakProactively(touchKind));
            } catch (Exception ignored) { }
        }
    }

    /**
     * 主人戳它时按节流主动接一句。走当前话题，所以它说的话也会进聊天记录；
     * 不想污染聊天就改用固定的桌宠话题 id（见 INTEGRATION.md 第 6 节）。
     */
    private void maybeSpeakProactively(String touchKind) {
        if (petRequestedTopic != null) return;                       // 有一轮还在跑，别插话
        long now = android.os.SystemClock.elapsedRealtime();
        if (!PetTalk.allow(now, lastProactiveAt, PetTalk.COOLDOWN_MS)) return;
        String topicId = TopicStore.getCurrentId(this);
        Topic topic = TopicStore.findById(this, topicId);
        if (topic == null) return;
        String prompt = PetTalk.pokePrompt(Prefs.getAiName(this), touchKind);
        try {
            org.json.JSONObject user = new org.json.JSONObject();
            user.put("role", "user");
            user.put("content", prompt);
            topic.messages.put(user);
            TopicStore.update(this, topic);
        } catch (Exception e) {
            return;
        }
        lastProactiveAt = now;
        petRequestedTopic = topicId;
        android.content.Intent i = new android.content.Intent(this, AIChatService.class);
        i.putExtra("topicId", topicId);
        i.putExtra("userMsg", prompt);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private Notification buildNotification() {
        String name = Prefs.getAiName(this);
        if (name == null || name.isEmpty()) name = "Aries";
        return new Notification.Builder(this, "PetChannel")
                .setContentTitle(name + " 正在陪伴主人…")
                .setContentText("点它可以拎起来甩一甩")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("PetChannel", "桌宠服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (petWindow != null) petWindow.hide();
        if (render != null) { render.destroy(); render = null; }
        if (assetServer != null) try { assetServer.stop(); } catch (IOException ignored) { }
        super.onDestroy();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) { return null; }
}
