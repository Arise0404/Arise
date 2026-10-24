package com.example.aries;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class PetService extends Service {

    private WindowManager windowManager;
    private FrameLayout petContainer;
    private WindowManager.LayoutParams params;
    private WhaleLiveView liveView;

    // 🌟 找回缩放探测器
    private ScaleGestureDetector scaleDetector;
    private float currentScale = 1.0f;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(2, buildNotification());
        initPetView();
    }

    private void initPetView() {
        petContainer = new FrameLayout(this);
        petContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        liveView = new WhaleLiveView(this);

        // 窗口给 250dp 以防放大后被切边
        int size = (int) (250 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(size, size);
        flp.gravity = Gravity.CENTER;
        petContainer.addView(liveView, flp);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 神盾：允许底层触摸和外部触摸
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        params = new WindowManager.LayoutParams(
                size, size,
                layoutFlag,
                flags,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 200;
        params.y = 500;

        setupGestures();
        windowManager.addView(petContainer, params);
    }

    private void setupGestures() {
        // 🌟 重新接管双指缩放
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                currentScale *= detector.getScaleFactor();
                currentScale = Math.max(0.5f, Math.min(currentScale, 3.0f));
                liveView.setScale(currentScale);
                return true;
            }
        });

        petContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private long touchStartTime = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 丢给缩放器
                scaleDetector.onTouchEvent(event);

                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (!liveView.isPointInsideBody(event.getX(), event.getY())) {
                        return false; // 穿透
                    }
                }

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // 超过一根手指（双指缩放），不执行拖拽
                        if (event.getPointerCount() > 1) return true;

                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;

                        if (System.currentTimeMillis() - touchStartTime > 150) {
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                                isDragging = true;
                            }
                            if (isDragging) {
                                params.x = initialX + (int) dx;
                                params.y = initialY + (int) dy;
                                clampPosition(250);
                                windowManager.updateViewLayout(petContainer, params);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        clampPosition(250);
                        windowManager.updateViewLayout(petContainer, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void clampPosition(int size) {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        if (params.x < 0) params.x = 0;
        if (params.y < 0) params.y = 0;
        if (params.x > screenWidth - size) params.x = screenWidth - size;
        if (params.y > screenHeight - size) params.y = screenHeight - size;
    }

    private Notification buildNotification() {
        String petName = Prefs.getAiName(this);
        if (petName == null || petName.isEmpty()) petName = "Aries";
        return new Notification.Builder(this, "PetChannel")
                .setContentTitle(petName + " 正在陪伴主人...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("PetChannel", "桌宠服务", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (petContainer != null && windowManager != null) {
            windowManager.removeView(petContainer);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}