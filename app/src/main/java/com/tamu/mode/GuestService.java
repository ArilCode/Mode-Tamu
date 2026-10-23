package com.tamu.mode;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

public class GuestService extends AccessibilityService {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout overlay;

    private final Runnable hideRunnable = this::hideOverlay;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (event.getPackageName() == null) return;

        SharedPreferences sp = getSharedPreferences("tamu", MODE_PRIVATE);
        if (!sp.getBoolean("guest", false)) return;

        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;

        Set<String> locked = new HashSet<>(sp.getStringSet("locked", new HashSet<>()));
        // Pengaturan HP selalu diblokir di mode tamu
        locked.add("com.android.settings");

        if (locked.contains(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            showOverlay();
        }
    }

    private void showOverlay() {
        handler.removeCallbacks(hideRunnable);
        if (overlay == null) {
            overlay = new FrameLayout(this);
            overlay.setBackgroundColor(0xEE000000);
            TextView tv = new TextView(this);
            tv.setText("Sedang di mode tamu");
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(26);
            tv.setGravity(Gravity.CENTER);
            overlay.addView(tv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(overlay, lp);
            } catch (Exception e) {
                overlay = null;
                return;
            }
        }
        handler.postDelayed(hideRunnable, 2000);
    }

    private void hideOverlay() {
        if (overlay != null) {
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
            } catch (Exception ignored) { }
            overlay = null;
        }
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }
}
