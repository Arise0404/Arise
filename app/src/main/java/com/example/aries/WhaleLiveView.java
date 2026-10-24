package com.example.aries;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WhaleLiveView extends View {
    private List<Part> parts = new ArrayList<>();
    private double S, X0, FEET;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private long startTime;

    // 🌟 缩放数据找回
    private float currentScale = 1.0f;
    private final float BASE_SCALE = 1.2f; // 初始基础大小

    public WhaleLiveView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
        startTime = SystemClock.uptimeMillis();
        new Thread(this::loadModel).start();
    }

    private void loadModel() {
        try {
            InputStream is = getContext().getAssets().open("coopanion/whale/model.json");
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            JSONObject json = new JSONObject(new String(buffer, "UTF-8"));

            JSONObject units = json.getJSONObject("units");
            S = units.getDouble("S");
            X0 = units.getDouble("X0");
            FEET = units.getDouble("FEET");

            JSONArray partsArr = json.getJSONArray("parts");
            for (int i = 0; i < partsArr.length(); i++) {
                JSONObject p = partsArr.getJSONObject(i);
                Part part = new Part();
                part.id = p.getString("id");
                part.tex = p.getString("tex");
                part.z = p.getDouble("z");
                JSONArray box = p.getJSONArray("box");

                part.x = (float) box.getDouble(0);
                part.y = (float) box.getDouble(1);
                part.w = (float) box.getDouble(2);
                part.h = (float) box.getDouble(3);

                if (part.id.equals("skirt_sit") || part.id.equals("waist_bow_sit_front")) continue;

                part.bmp = loadBitmap("coopanion/whale/tex/" + part.tex + ".png");
                parts.add(part);
            }

            JSONObject eyes = json.getJSONObject("feat").getJSONObject("eyes");
            JSONObject sprites = json.getJSONObject("feat").getJSONObject("sprites");

            addFeature("eyeL_ball", "coopanion/whale/feat/eyeL_ball.png", eyes.getJSONObject("eyeL").getJSONArray("ball"), 8.2);
            addFeature("eyeL_iris", "coopanion/whale/feat/eyeL_iris.png", eyes.getJSONObject("eyeL").getJSONArray("iris"), 8.3);
            addFeature("eyeL_lash", "coopanion/whale/feat/eyeL_lash.png", eyes.getJSONObject("eyeL").getJSONArray("lash"), 8.4);
            addFeature("eyeR_ball", "coopanion/whale/feat/eyeR_ball.png", eyes.getJSONObject("eyeR").getJSONArray("ball"), 8.2);
            addFeature("eyeR_iris", "coopanion/whale/feat/eyeR_iris.png", eyes.getJSONObject("eyeR").getJSONArray("iris"), 8.3);
            addFeature("eyeR_lash", "coopanion/whale/feat/eyeR_lash.png", eyes.getJSONObject("eyeR").getJSONArray("lash"), 8.4);
            addFeature("neutral_mouth", "coopanion/whale/feat/neutral_mouth.png", sprites.getJSONArray("neutral_mouth"), 8.7);

            Collections.sort(parts, (p1, p2) -> Double.compare(p1.z, p2.z));
            postInvalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addFeature(String id, String path, JSONArray box, double z) throws Exception {
        Part p = new Part();
        p.id = id;
        p.z = z;
        p.bmp = loadBitmap(path);

        float x0 = (float) box.getDouble(0);
        float y0 = (float) box.getDouble(1);
        float x1 = (float) box.getDouble(2);
        float y1 = (float) box.getDouble(3);

        p.x = (float) (128 + (x0 - X0) * S);
        p.y = (float) (256 - (FEET - y0) * S);
        p.w = (float) (128 + (x1 - X0) * S) - p.x;
        p.h = (float) (256 - (FEET - y1) * S) - p.y;

        parts.add(p);
    }

    private Bitmap loadBitmap(String path) {
        try {
            InputStream is = getContext().getAssets().open(path);
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            return bmp;
        } catch (Exception e) { return null; }
    }

    // 🌟 暴露给外部调用的缩放接口
    public void setScale(float scale) {
        this.currentScale = scale;
    }

    // 🌟 绝对贴合本体的像素判定
    public boolean isPointInsideBody(float x, float y) {
        if (parts.isEmpty()) return false;

        float finalScale = BASE_SCALE * currentScale;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float lx = (x - cx) / finalScale + 128f;
        float ly = (y - cy) / finalScale + 128f;

        // 死死卡住 60~195，让透明框彻底消失
        return lx > 60 && lx < 195 && ly > 50 && ly < 220;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0 || parts.isEmpty()) return;

        float t = (SystemClock.uptimeMillis() - startTime) / 1000f;
        float breath = (float) Math.sin(t * 2.5) * 2f;
        float tailSwing = (float) Math.sin(t * 4) * 8f;
        float ahogeSwing = (float) Math.sin(t * 3) * 5f;
        float blinkPhase = (t % 4.0f);
        boolean isBlinking = blinkPhase > 3.8f;

        canvas.save();

        // 🌟 响应缩放
        float finalScale = BASE_SCALE * currentScale;
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(finalScale, finalScale);
        canvas.translate(-128f, -128f);
        canvas.scale(1 + breath * 0.005f, 1f);

        for (Part p : parts) {
            if (p.bmp == null) continue;
            canvas.save();

            RectF destRect = new RectF(p.x, p.y, p.x + p.w, p.y + p.h);

            if (p.id.equals("tail")) {
                canvas.rotate(tailSwing, p.x + p.w, p.y + p.h);
            } else if (p.id.equals("ahoge")) {
                canvas.rotate(ahogeSwing, p.x + p.w / 2, p.y + p.h);
            } else if (p.id.startsWith("eye") && isBlinking) {
                canvas.scale(1f, 0.1f, p.x + p.w / 2, p.y + p.h / 2);
            }

            canvas.drawBitmap(p.bmp, null, destRect, paint);
            canvas.restore();
        }

        canvas.restore();
        postInvalidateOnAnimation();
    }

    static class Part {
        String id, tex;
        double z;
        float x, y, w, h;
        Bitmap bmp;
    }
}