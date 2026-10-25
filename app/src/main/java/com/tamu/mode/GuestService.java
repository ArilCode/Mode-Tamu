package com.tamu.mode;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuestService extends AccessibilityService {

    private static final String SYSTEMUI = "com.android.systemui";
    private static final String MSG_GUEST =
            "Mode tamu aktif, silahkan kasih tau pemilik hp untuk mengaksesnya";
    private static final String MSG_VAULT = "Aplikasi ini disembunyikan oleh pemilik hp";

    static final String[][] TOGGLES = {
            {"data", "data seluler", "data ponsel", "mobile data", "cellular data", "internet"},
            {"airplane", "mode pesawat", "airplane", "pesawat"},
            {"wifi", "wi-fi", "wifi"},
            {"hotspot", "hotspot", "tethering"},
            {"bluetooth", "bluetooth"},
            {"location", "lokasi", "location"},
            {"flashlight", "senter", "flashlight", "torch"},
            {"dnd", "jangan ganggu", "do not disturb", "dnd"},
            {"rotate", "rotasi", "putar otomatis", "auto-rotate", "auto rotate"},
            {"battery", "hemat baterai", "penghemat baterai", "battery saver"},
            {"nfc", "nfc"},
            {"screenrec", "perekam layar", "rekam layar", "screen record"},
            {"cast", "cast", "pantulan layar", "screen cast", "mirror"},
            {"dark", "mode gelap", "tema gelap", "dark mode", "dark theme"},
            {"sound", "senyap", "getar", "silent", "vibrate", "suara"},
            {"datasaver", "penghemat data", "data saver"},
            {"qr", "qr"}
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout overlay;
    private TextView overlayText;
    private final List<View> blockers = new ArrayList<>();
    private List<Rect> lastRects = new ArrayList<>();
    private boolean scanPending = false;

    private final Runnable hideRunnable = this::hideOverlay;
    private final Runnable scanRunnable = () -> { scanPending = false; scan(); };

    static boolean isEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("tamu", MODE_PRIVATE);
    }

    private boolean hasTileRules(SharedPreferences sp) {
        return !sp.getStringSet("toggles", new HashSet<>()).isEmpty()
                || !sp.getStringSet("custom_kw", new HashSet<>()).isEmpty();
    }

    private String homePackage() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(i, 0);
        return r != null ? r.activityInfo.packageName : "";
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;

        SharedPreferences sp = prefs();
        int type = e.getEventType();

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            int reason = blockReason(sp, pkg);
            if (reason != 0) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                showOverlay(reason == 1 ? MSG_GUEST : MSG_VAULT);
                clearBlockers();
                return;
            }
        }

        if (sp.getBoolean("guest", false) && pkg.equals(SYSTEMUI)) {
            if (hasTileRules(sp) && !scanPending) {
                scanPending = true;
                handler.postDelayed(scanRunnable, 150);
            }
        } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            clearBlockers();
        }
    }

    // 0 = tidak diblokir, 1 = karena mode tamu, 2 = karena brankas
    private int blockReason(SharedPreferences sp, String pkg) {
        if (pkg.equals(SYSTEMUI) || pkg.equals(homePackage())) return 0;
        if (sp.getBoolean("guest", false)) {
            Set<String> locked = sp.getStringSet("locked", new HashSet<>());
            if (locked.contains(pkg) || pkg.equals("com.android.settings")) return 1;
        }
        Set<String> vault = sp.getStringSet("vault_apps", new HashSet<>());
        if (vault.contains(pkg) && System.currentTimeMillis() > sp.getLong("vault_open_until", 0)) return 2;
        return 0;
    }

    private void scan() {
        SharedPreferences sp = prefs();
        if (!sp.getBoolean("guest", false) || !hasTileRules(sp)) { clearBlockers(); return; }

        Set<String> sel = sp.getStringSet("toggles", new HashSet<>());
        boolean all = sel.contains("all");

        List<String> kws = new ArrayList<>();
        for (String[] t : TOGGLES) {
            if (sel.contains(t[0])) for (int i = 1; i < t.length; i++) kws.add(t[i]);
        }
        for (String c : sp.getStringSet("custom_kw", new HashSet<>())) kws.add(c.toLowerCase());

        List<Rect> rects = new ArrayList<>();
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName() != null
                && SYSTEMUI.contentEquals(root.getPackageName())) {
            if (all) collectAll(root, rects, 0);
            if (!kws.isEmpty()) collect(root, kws, rects, 0);
        }

        if (!rects.equals(lastRects)) rebuildBlockers(rects);

        if (!rects.isEmpty() && !scanPending) {
            scanPending = true;
            handler.postDelayed(scanRunnable, 500);
        }
    }

    private void collectAll(AccessibilityNodeInfo n, List<Rect> out, int depth) {
        if (n == null || depth > 30) return;
        if (n.isClickable() && n.isVisibleToUser()) {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            boolean sizeOk = r.width() >= dm.widthPixels * 0.15f && r.width() <= dm.widthPixels * 0.6f
                    && r.height() >= dm.heightPixels * 0.05f && r.height() <= dm.heightPixels * 0.2f;
            if (sizeOk) {
                if (!out.contains(r)) out.add(r);
                return;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) collectAll(n.getChild(i), out, depth + 1);
    }

    private void collect(AccessibilityNodeInfo n, List<String> kws, List<Rect> out, int depth) {
        if (n == null || depth > 30) return;
        CharSequence tx = n.getText();
        CharSequence cd = n.getContentDescription();
        String s = ((tx == null ? "" : tx) + " " + (cd == null ? "" : cd)).toLowerCase();

        if (s.length() < 70 && n.isVisibleToUser()) {
            boolean match = false;
            for (String k : kws) {
                if (s.contains(k)) { match = true; break; }
            }
            if (match) {
                AccessibilityNodeInfo t = n;
                while (t != null && !t.isClickable()) t = t.getParent();
                Rect r = new Rect();
                (t != null ? t : n).getBoundsInScreen(r);
                DisplayMetrics dm = getResources().getDisplayMetrics();
                boolean tooBig = r.width() > dm.widthPixels * 0.9f
                        && r.height() > dm.heightPixels * 0.25f;
                if (r.width() > 0 && r.height() > 0 && !tooBig && !out.contains(r)) out.add(r);
                return;
            }
        }
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), kws, out, depth + 1);
    }

    private void rebuildBlockers(List<Rect> rects) {
        clearBlockers();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        for (Rect r : rects) {
            View v = new View(this);
            v.setBackgroundColor(0x33FF3B5C);
            v.setOnClickListener(x -> Toast.makeText(this, MSG_GUEST, Toast.LENGTH_SHORT).show());
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    r.width(), r.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = r.left;
            lp.y = r.top;
            try {
                wm.addView(v, lp);
                blockers.add(v);
            } catch (Exception ignored) { }
        }
        lastRects = new ArrayList<>(rects);
    }

    private void clearBlockers() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        for (View v : blockers) {
            try { wm.removeView(v); } catch (Exception ignored) { }
        }
        blockers.clear();
        lastRects = new ArrayList<>();
    }

    private void showOverlay(String msg) {
        handler.removeCallbacks(hideRunnable);
        if (overlay == null) {
            overlay = new FrameLayout(this);
            overlay.setBackgroundColor(0xF00B1020);
            overlayText = new TextView(this);
            overlayText.setTextColor(0xFFEAF0FF);
            overlayText.setTextSize(22);
            overlayText.setGravity(Gravity.CENTER);
            overlayText.setPadding(70, 0, 70, 0);
            overlay.addView(overlayText, new FrameLayout.LayoutParams(
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
        overlayText.setText(msg);
        handler.postDelayed(hideRunnable, 2500);
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
        clearBlockers();
        super.onDestroy();
    }
}