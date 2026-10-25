package com.example.aries;

// 悬浮宠物的两层窗口管理：一层只管画（永远不吃触摸，所以不会挡你操作手机），
// 一层贴在身体外面只管收触摸（所以窗口很小，不会把模型裁掉）。
//
// 纯 Java（JAVA_8 语法）。我这边的验证状态：对着 42 个 android/json API 桩 javac --release 8
// 编译通过、无错无警；真 SDK 上还没编过，请在 CodeAssist 里编译。
// 配套 JS 侧（overlay.html 的 petCall('pointer') 与 Android.setBox）已用真实 Chrome 测过（tests/overlay.test.mjs）。
//
// 用法一（WebView 画，拿到全套动画，推荐）：
//   FloatingPetWindow pw = new FloatingPetWindow(context);
//   pw.setRenderTopFromBottom(true);            // 画层贴屏幕底边，宠物沿底边走
//   WebView web = new WebView(context); …配置透明… web.loadUrl(assets 里的 overlay.html);
//   pw.show(web, dp(300));                      // 300dp 高的透明画层，覆盖身体 + 跳 + 气泡
//   pw.setTouchForwarder((type, x, y) ->        // x/y 已是画层内的 CSS 像素
//       web.evaluateJavascript("window.petCall('pointer',{type:'" + type + "',x:" + x + ",y:" + y + "})", null));
//   // overlay.html 里 Android.setBox(json) 回调时：
//   pw.setBodyBox(left, top, width, height);    // 单位：画层内 CSS 像素
//
// 用法二（你现在的 WhaleLiveView 原生画）：
//   pw.show(liveView, dp(300));                 // 画层装你的 View
//   pw.setTouchForwarder((type, x, y) -> liveView.onPetPointer(type, x, y));  // 自己做命中与拎起
//   // 在 onDraw 里算出身体并集的屏幕矩形，隔几帧调一次 pw.setBodyBox(...)

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class FloatingPetWindow {

    public interface TouchForwarder {
        /** type: down / move / up / leave；x、y 是画层（render view）内的 CSS 像素或视图像素 */
        void onPointer(String type, float x, float y);
    }

    private final Context ctx;
    private final WindowManager wm;
    private final float density;

    private View renderView;
    private View touchView;
    private WindowManager.LayoutParams rp;
    private WindowManager.LayoutParams tp;

    private boolean drawFromBottom = true;      // 画层贴底边（桌宠默认沿屏幕底边走）
    private int stripHeightPx = 0;              // 画层高度
    private float renderLeftPx = 0f, renderTopPx = 0f;  // 画层在屏幕上的位置，换算触摸坐标用

    private final RectF bodyPx = new RectF();   // 身体框（画层内坐标）
    private long lastLayoutAt = 0L;
    private TouchForwarder forwarder;

    public FloatingPetWindow(Context context) {
        this.ctx = context;
        this.wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.density = context.getResources().getDisplayMetrics().density;   // 字段初始化顺序：ctx 赋值之后再取
    }

    /** true = 画层贴在屏幕底边往上长；false = 画层占满全屏。 */
    public void setRenderTopFromBottom(boolean fromBottom) { this.drawFromBottom = fromBottom; }

    public void setTouchForwarder(TouchForwarder f) { this.forwarder = f; }

    public boolean hasPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Settings.canDrawOverlays(ctx);
    }

    /** 没权限时引导用户去系统设置；返回 false 表示现在不能显示。 */
    public boolean show(View render, int heightPx) {
        if (!hasPermission()) {
            ctx.startActivity(new android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName()))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            return false;
        }
        stripHeightPx = heightPx;
        if (renderView != null) hide();

        // ── 画层：不吃任何触摸。它大一点也没关系，反正不会挡操作，模型也就不会被裁 ──
        renderView = render;
        renderView.setBackgroundColor(Color.TRANSPARENT);
        rp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, stripHeightPx,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE    // ★ 关键：整层穿透
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,  // 服务里的窗口不然硬件加速，WebGL 起不来
                PixelFormat.TRANSLUCENT);
        rp.gravity = drawFromBottom ? (Gravity.BOTTOM | Gravity.START) : (Gravity.TOP | Gravity.START);
        wm.addView(renderView, rp);
        renderView.post(this::measureRenderOrigin);

        // ── 触摸层：一开始给个小方块，setBodyBox 之后会贴住身体 ──
        touchView = new View(ctx) {
            @Override public boolean onTouchEvent(MotionEvent e) { return handleTouch(e); }
        };
        touchView.setClickable(true);
        touchView.setBackgroundColor(Color.TRANSPARENT);
        tp = new WindowManager.LayoutParams(
                dp(120), dp(160), overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL   // 方框之外仍然穿透到下层 app
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        tp.gravity = Gravity.TOP | Gravity.START;
        tp.x = dp(40); tp.y = dp(40);
        wm.addView(touchView, tp);
        return true;
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void measureRenderOrigin() {
        if (renderView == null) return;
        int[] loc = new int[2];
        renderView.getLocationOnScreen(loc);
        renderLeftPx = loc[0];
        renderTopPx = loc[1];
    }

    /** 身体框，坐标在画层内（JS 的 onBox / 原生 onDraw 算出来的那个矩形）。 */
    public void setBodyBox(float left, float top, float width, float height) {
        if (width <= 0 || height <= 0) return;
        boolean moved = Math.abs(left - bodyPx.left) + Math.abs(top - bodyPx.top)
                + Math.abs(width - bodyPx.width()) + Math.abs(height - bodyPx.height()) > dp(3);
        bodyPx.set(left, top, left + width, top + height);
        if (!moved) return;                        // 抖动太小不动窗口，省算力也防抖
        long now = System.currentTimeMillis();
        if (now - lastLayoutAt < 70) return;       // 最快约 14 次/秒
        lastLayoutAt = now;
        followBody();
    }

    private void followBody() {
        if (touchView == null || tp == null) return;
        float pad = dp(6);
        int w = (int) (bodyPx.width() + pad * 2);
        int h = (int) (bodyPx.height() + pad * 2);
        int screenW = wm.getDefaultDisplay().getWidth();
        int screenH = wm.getDefaultDisplay().getHeight();
        int x = clamp((int) (renderLeftPx + bodyPx.left - pad), 0, screenW - w);
        int y = clamp((int) (renderTopPx + bodyPx.top - pad), 0, screenH - h);
        tp.width = Math.max(dp(40), w);
        tp.height = Math.max(dp(40), h);
        tp.x = x; tp.y = y;
        try { wm.updateViewLayout(touchView, tp); } catch (IllegalArgumentException ignored) { /* 已移除 */ }
    }

    private boolean handleTouch(MotionEvent e) {
        if (forwarder == null) return false;
        // 换算：rawX/rawY 是屏幕坐标，减掉画层在屏幕上的位置就是画层内坐标
        if (renderWidth() == 0) measureRenderOrigin();
        float x = e.getRawX() - renderLeftPx;
        float y = e.getRawY() - renderTopPx;
        String type;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: type = "down"; break;
            case MotionEvent.ACTION_MOVE: type = "move"; break;
            case MotionEvent.ACTION_UP: type = "up"; break;
            case MotionEvent.ACTION_CANCEL: type = "up"; break;
            default: return true;
        }
        forwarder.onPointer(type, x, y);
        return true;                                  // 吃掉这条线上的触摸，其余区域照旧穿透
    }

    /** 转屏或分屏后重新算画层位置和窗口尺寸。 */
    public void relayout() {
        if (renderView == null) return;
        renderView.post(this::measureRenderOrigin);
        followBody();
    }

    public void hide() {
        if (renderView != null) { try { wm.removeView(renderView); } catch (Exception ignored) { } renderView = null; }
        if (touchView != null) { try { wm.removeView(touchView); } catch (Exception ignored) { } touchView = null; }
        bodyPx.set(0, 0, 0, 0);
    }

    private int renderWidth() {
        return renderView == null ? 0 : renderView.getWidth();
    }

    private int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private int dp(float v) { return (int) (v * density + 0.5f); }
}
