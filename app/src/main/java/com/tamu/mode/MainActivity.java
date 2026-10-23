package com.tamu.mode;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("tamu", MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        if (sp.getString("pin", null) == null) showSetPin();
        else if (sp.getBoolean("guest", false)) showGate();
        else showMain();
    }

    private void showSetPin() {
        LinearLayout root = newRoot();
        root.addView(text("Buat PIN / Sandi", 24));
        root.addView(text("PIN ini dipakai untuk menonaktifkan mode tamu. Minimal 4 karakter.", 14));
        final EditText pin = pinField("PIN baru");
        final EditText pin2 = pinField("Ulangi PIN");
        root.addView(pin);
        root.addView(pin2);
        root.addView(button("Simpan PIN", v -> {
            String p = pin.getText().toString();
            if (p.length() < 4) { toast("Minimal 4 karakter"); return; }
            if (!p.equals(pin2.getText().toString())) { toast("PIN tidak sama"); return; }
            sp.edit().putString("pin", hash(p)).apply();
            render();
        }));
        setContentView(wrap(root));
    }

    private void showMain() {
        LinearLayout root = newRoot();
        root.addView(text("Mode Owner (semua aplikasi bisa dibuka)", 20));

        boolean on = isServiceOn();
        root.addView(text(on ? "Layanan penjaga: AKTIF" : "Layanan penjaga: BELUM AKTIF", 16));
        if (!on) {
            root.addView(text("Nyalakan dulu: Pengaturan Aksesibilitas > Aplikasi terinstal > Mode Tamu > Aktifkan.", 14));
            root.addView(button("Buka Pengaturan Aksesibilitas", v ->
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        }

        int n = sp.getStringSet("locked", new HashSet<>()).size();
        root.addView(text("Aplikasi yang dikunci di mode tamu: " + n, 16));
        root.addView(button("Pilih aplikasi yang dikunci", v -> showPicker()));

        root.addView(button("AKTIFKAN MODE TAMU", v -> {
            if (!isServiceOn()) { toast("Aktifkan layanan di Pengaturan Aksesibilitas dulu"); return; }
            sp.edit().putBoolean("guest", true).apply();
            render();
        }));
        setContentView(wrap(root));
    }

    private void showGate() {
        LinearLayout root = newRoot();
        root.addView(text("Sedang di mode tamu", 24));
        root.addView(text("Masukkan PIN untuk kembali ke mode owner.", 14));
        final EditText pin = pinField("PIN");
        root.addView(pin);
        root.addView(button("Nonaktifkan mode tamu", v -> {
            if (hash(pin.getText().toString()).equals(sp.getString("pin", ""))) {
                sp.edit().putBoolean("guest", false).apply();
                render();
            } else {
                toast("PIN salah");
            }
        }));
        setContentView(wrap(root));
    }

    private void showPicker() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(i, 0);
        Collections.sort(apps, (a, c) -> a.loadLabel(pm).toString()
                .compareToIgnoreCase(c.loadLabel(pm).toString()));

        final List<String> pkgs = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (ResolveInfo r : apps) {
            String p = r.activityInfo.packageName;
            if (p.equals(getPackageName())) continue;
            if (pkgs.contains(p)) continue;
            pkgs.add(p);
            labels.add(r.loadLabel(pm).toString());
        }

        final Set<String> locked = new HashSet<>(sp.getStringSet("locked", new HashSet<>()));

        LinearLayout root = newRoot();
        root.addView(text("Centang aplikasi yang DIKUNCI di mode tamu", 18));
        root.addView(button("Selesai", v -> render()));

        final ListView lv = new ListView(this);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        lv.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, labels));
        for (int k = 0; k < pkgs.size(); k++) lv.setItemChecked(k, locked.contains(pkgs.get(k)));
        lv.setOnItemClickListener((parent, view, pos, id) -> {
            if (lv.isItemChecked(pos)) locked.add(pkgs.get(pos));
            else locked.remove(pkgs.get(pos));
            sp.edit().putStringSet("locked", new HashSet<>(locked)).apply();
        });
        root.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private boolean isServiceOn() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private LinearLayout newRoot() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        l.setPadding(pad, pad * 3, pad, pad);
        return l;
    }

    private ScrollView wrap(LinearLayout l) {
        ScrollView sv = new ScrollView(this);
        sv.addView(l);
        return sv;
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setOnClickListener(l);
        return b;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
        }
