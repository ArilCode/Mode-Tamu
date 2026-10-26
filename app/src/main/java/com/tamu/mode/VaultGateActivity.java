package com.tamu.mode;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.security.MessageDigest;

public class VaultGateActivity extends Activity {

    private SharedPreferences sp;
    private String pkg = "";
    private EditText input;
    private TextView sub, err;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("tamu", MODE_PRIVATE);
        build();
        load(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        load(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        input.requestFocus();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();
    }

    @Override
    public void onBackPressed() {
        goHome();
    }

    private void load(Intent i) {
        pkg = i.getStringExtra("pkg");
        if (pkg == null) pkg = "";
        String label = pkg;
        try {
            label = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) { }
        sub.setText("Masukkan kode unik untuk membuka " + label);
        input.setText("");
        err.setText("");
    }

    private float dp(int v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = (int) dp(topDp);
        return p;
    }

    private void build() {
        int pad = (int) dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0B1020);
        root.setPadding(pad, pad, pad, pad);

        TextView icon = new TextView(this);
        icon.setText("🔒");
        icon.setTextSize(56);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon, lp(0));

        TextView title = new TextView(this);
        title.setText("Aplikasi terkunci");
        title.setTextColor(0xFFEAF0FF);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(8));

        sub = new TextView(this);
        sub.setTextColor(0xFF8A94B8);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(6));

        input = new EditText(this);
        input.setHint("Kode unik");
        input.setHintTextColor(0xFF8A94B8);
        input.setTextColor(0xFFEAF0FF);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackground(rounded(0x22FFFFFF, 14));
        int ip = (int) dp(16);
        input.setPadding(ip, ip, ip, ip);
        input.setOnEditorActionListener((v, a, e) -> { check(); return true; });
        root.addView(input, lp(24));

        err = new TextView(this);
        err.setTextColor(0xFFFF5C7A);
        err.setTextSize(13);
        err.setGravity(Gravity.CENTER);
        root.addView(err, lp(8));

        Button open = new Button(this);
        open.setText("Buka");
        open.setTextColor(0xFFFFFFFF);
        open.setAllCaps(false);
        open.setBackground(rounded(0xFF5B6CFF, 16));
        open.setOnClickListener(v -> check());
        root.addView(open, lp(10));

        Button back = new Button(this);
        back.setText("Kembali");
        back.setTextColor(0xFF4FD1FF);
        back.setAllCaps(false);
        back.setBackgroundColor(0x00000000);
        back.setOnClickListener(v -> goHome());
        root.addView(back, lp(4));

        setContentView(root);
    }

    private void check() {
        String saved = sp.getString("vault_code", null);
        if (saved == null || saved.equals(hash(input.getText().toString()))) {
            sp.edit().putString("vault_ok", pkg).apply();
            finish();
        } else {
            err.setText("Kode unik salah");
            input.setText("");
        }
    }

    private void goHome() {
        Intent h = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(h);
        finish();
    }

    private String hash(String v) {
        try {
            String salt = sp.getString("salt", "");
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + v).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
