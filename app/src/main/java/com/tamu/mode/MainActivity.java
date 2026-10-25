package com.tamu.mode;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final List<String> TOGGLE_IDS = Arrays.asList(
            "data", "airplane", "wifi", "hotspot", "bluetooth", "location",
            "flashlight", "dnd", "rotate", "battery", "nfc", "screenrec",
            "cast", "dark", "sound", "datasaver", "qr", "all");

    private WebView web;
    private SharedPreferences sp;
    private volatile boolean vaultUnlocked = false;
    private final Map<String, String> iconCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("tamu", MODE_PRIVATE);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B1020);
        web.setLongClickable(false);
        web.setOnLongClickListener(v -> true);
        web.setWebViewClient(new WebViewClient());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "Ace");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.evaluateJavascript("window.refresh && window.refresh()", null);
    }

    @Override
    protected void onStop() {
        vaultUnlocked = false;
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBack ? window.onBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    private String salt() {
        String s = sp.getString("salt", null);
        if (s == null) {
            s = UUID.randomUUID().toString();
            sp.edit().putString("salt", s).apply();
        }
        return s;
    }

    private String h(String v) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt() + v).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private boolean check(String key, String value) {
        String saved = sp.getString(key, null);
        return saved != null && saved.equals(h(value == null ? "" : value));
    }

    private boolean guest() {
        return sp.getBoolean("guest", false);
    }

    private Set<String> set(String key) {
        return new HashSet<>(sp.getStringSet(key, new HashSet<>()));
    }

    private String ok() {
        return "{\"ok\":true}";
    }

    private String err(String msg) {
        try {
            return new JSONObject().put("ok", false).put("msg", msg).toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    private String homePackage() {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r = getPackageManager().resolveActivity(i, 0);
        return r != null ? r.activityInfo.packageName : "";
    }

    private String iconOf(String pkg) {
        String c = iconCache.get(pkg);
        if (c != null) return c;
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            int sz = 72;
            Bitmap bm = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            d.setBounds(0, 0, sz, sz);
            d.draw(cv);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, bo);
            c = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            c = "";
        }
        iconCache.put(pkg, c);
        return c;
    }

    private class Bridge {

        @JavascriptInterface
        public String state() {
            try {
                JSONObject o = new JSONObject();
                o.put("setup", sp.getString("pin", null) != null);
                o.put("guest", guest());
                o.put("service", GuestService.isEnabled(MainActivity.this));
                o.put("lockedCount", set("locked").size());
                o.put("toggles", new JSONArray(set("toggles")));
                o.put("custom", new JSONArray(set("custom_kw")));
                o.put("question", sp.getString("q", ""));
                o.put("vaultSet", sp.getString("vault_code", null) != null);
                o.put("vaultCount", set("vault_apps").size());
                o.put("vaultUnlocked", vaultUnlocked && !guest());
                o.put("vaultOpenUntil", sp.getLong("vault_open_until", 0));
                o.put("now", System.currentTimeMillis());
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String setup(String pin, String q, String a) {
            if (sp.getString("pin", null) != null) return err("Kode sudah dibuat");
            if (pin == null || pin.length() < 4) return err("Kode awal minimal 4 karakter");
            if (q == null || q.trim().length() < 3) return err("Pertanyaan terlalu pendek");
            if (a == null || a.trim().isEmpty()) return err("Jawaban tidak boleh kosong");
            sp.edit().putString("pin", h(pin)).putString("q", q.trim())
                    .putString("a", h(norm(a))).apply();
            return ok();
        }

        @JavascriptInterface
        public String apps() {
            try {
                PackageManager pm = getPackageManager();
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
                Collections.sort(list, (x, y) -> x.loadLabel(pm).toString()
                        .compareToIgnoreCase(y.loadLabel(pm).toString()));
                Set<String> locked = set("locked");
                Set<String> vault = set("vault_apps");
                Set<String> seen = new HashSet<>();
                String home = homePackage();
                JSONArray arr = new JSONArray();
                for (ResolveInfo r : list) {
                    String p = r.activityInfo.packageName;
                    if (p.equals(getPackageName()) || p.equals(home) || !seen.add(p)) continue;
                    JSONObject o = new JSONObject();
                    o.put("p", p);
                    o.put("n", r.loadLabel(pm).toString());
                    o.put("i", iconOf(p));
                    o.put("l", locked.contains(p));
                    o.put("v", vault.contains(p));
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String setLocked(String pkg, boolean on) {
            if (guest()) return err("Sedang di mode tamu");
            Set<String> s = set("locked");
            if (on) s.add(pkg); else s.remove(pkg);
            sp.edit().putStringSet("locked", s).apply();
            return ok();
        }

        @JavascriptInterface
        public String setToggle(String id, boolean on) {
            if (guest()) return err("Sedang di mode tamu");
            if (!TOGGLE_IDS.contains(id)) return err("Tidak dikenal");
            Set<String> s = set("toggles");
            if (on) s.add(id); else s.remove(id);
            sp.edit().putStringSet("toggles", s).apply();
            return ok();
        }

        @JavascriptInterface
        public String activate() {
            if (sp.getString("pin", null) == null) return err("Buat kode dulu");
            if (set("locked").isEmpty()) {
                try {
                    return new JSONObject().put("ok", false)
                            .put("msg", "Pilih minimal 1 aplikasi dulu").put("go", "apps").toString();
                } catch (Exception e) {
                    return err("Pilih minimal 1 aplikasi dulu");
                }
            }
            if (!GuestService.isEnabled(MainActivity.this)) {
                return err("Aktifkan layanan Mode ACE di Aksesibilitas dulu");
            }
            sp.edit().putBoolean("guest", true).apply();
            vaultUnlocked = false;
            return ok();
        }

        @JavascriptInterface
        public String deactivate(String pin) {
            if (!check("pin", pin)) return err("Kode salah");
            sp.edit().putBoolean("guest", false).apply();
            return ok();
        }

        @JavascriptInterface
        public String changePin(String oldPin, String newPin) {
            if (guest()) return err("Sedang di mode tamu");
            if (!check("pin", oldPin)) return err("Kode awal salah");
            if (newPin == null || newPin.length() < 4) return err("Kode baru minimal 4 karakter");
            sp.edit().putString("pin", h(newPin)).apply();
            return ok();
        }

        @JavascriptInterface
        public String forgotPin(String answer, String newPin) {
            if (!check("a", norm(answer))) return err("Jawaban salah");
            if (newPin == null || newPin.length() < 4) return err("Kode baru minimal 4 karakter");
            sp.edit().putString("pin", h(newPin)).putBoolean("guest", false).apply();
            return ok();
        }

        @JavascriptInterface
        public String vaultSetup(String code) {
            if (guest()) return err("Sedang di mode tamu");
            if (sp.getString("vault_code", null) != null) return err("Kode unik sudah dibuat");
            if (code == null || code.length() < 4) return err("Kode unik minimal 4 karakter");
            sp.edit().putString("vault_code", h(code)).apply();
            vaultUnlocked = true;
            return ok();
        }

        @JavascriptInterface
        public String vaultOpen(String code) {
            if (guest()) return err("Sedang di mode tamu");
            if (!check("vault_code", code)) return err("Kode unik salah");
            vaultUnlocked = true;
            return ok();
        }

        @JavascriptInterface
        public String vaultSetApp(String pkg, boolean on) {
            if (guest() || !vaultUnlocked) return err("Brankas terkunci");
            Set<String> s = set("vault_apps");
            if (on) s.add(pkg); else s.remove(pkg);
            sp.edit().putStringSet("vault_apps", s).apply();
            return ok();
        }

        @JavascriptInterface
        public String vaultGrant(int minutes) {
            if (guest() || !vaultUnlocked) return err("Brankas terkunci");
            long until = minutes > 0 ? System.currentTimeMillis() + minutes * 60000L : 0;
            sp.edit().putLong("vault_open_until", until).apply();
            return ok();
        }

        @JavascriptInterface
        public String vaultChange(String oldCode, String newCode) {
            if (guest()) return err("Sedang di mode tamu");
            if (!check("vault_code", oldCode)) return err("Kode unik lama salah");
            if (newCode == null || newCode.length() < 4) return err("Kode baru minimal 4 karakter");
            sp.edit().putString("vault_code", h(newCode)).apply();
            return ok();
        }

        @JavascriptInterface
        public String vaultForgot(String answer) {
            if (guest()) return err("Sedang di mode tamu");
            if (!check("a", norm(answer))) return err("Jawaban salah");
            sp.edit().remove("vault_code").remove("vault_apps").remove("vault_open_until").apply();
            vaultUnlocked = false;
            return ok();
        }

        @JavascriptInterface
        public String addCustom(String name) {
            if (guest()) return err("Sedang di mode tamu");
            String n = norm(name);
            if (n.length() < 2) return err("Nama terlalu pendek");
            Set<String> s = set("custom_kw");
            s.add(n);
            sp.edit().putStringSet("custom_kw", s).apply();
            return ok();
        }

        @JavascriptInterface
        public String removeCustom(String name) {
            if (guest()) return err("Sedang di mode tamu");
            Set<String> s = set("custom_kw");
            s.remove(norm(name));
            sp.edit().putStringSet("custom_kw", s).apply();
            return ok();
        }

        @JavascriptInterface
        public void openLink() {
            runOnUiThread(() -> startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://my-all-link.vercel.app/"))));
        }

        @JavascriptInterface
        public void openAccessibility() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        @JavascriptInterface
        public void addTile() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33) {