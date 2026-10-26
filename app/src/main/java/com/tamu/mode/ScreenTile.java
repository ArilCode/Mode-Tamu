package com.tamu.mode;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

public class ScreenTile extends TileService {

    private static final int NEVER = Integer.MAX_VALUE;
    private static final int NEVER_MIN = 600000000;

    @Override
    public void onStartListening() {
        sync();
        refresh();
    }

    @Override
    public void onClick() {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Izinkan dulu 'Ubah pengaturan sistem' untuk Mode ACE",
                    Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            launch(i);
            return;
        }

        SharedPreferences sp = getSharedPreferences("tamu", MODE_PRIVATE);
        ContentResolver cr = getContentResolver();
        boolean keep = sp.getBoolean("screen_keep", false);
        try {
            if (!keep) {
                int cur = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                if (cur < NEVER_MIN) sp.edit().putInt("screen_orig", cur).apply();
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, NEVER);
                sp.edit().putBoolean("screen_keep", true).apply();
            } else {
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT,
                        sp.getInt("screen_orig", 60000));
                sp.edit().putBoolean("screen_keep", false).apply();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal mengubah waktu tunggu layar", Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void launch(Intent i) {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 1, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    // Jika waktu tunggu diubah manual dari Pengaturan, tile ikut menyesuaikan
    private void sync() {
        SharedPreferences sp = getSharedPreferences("tamu", MODE_PRIVATE);
        if (sp.getBoolean("screen_keep", false)) {
            int actual = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 60000);
            if (actual < NEVER_MIN) sp.edit().putBoolean("screen_keep", false).apply();
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean keep = getSharedPreferences("tamu", MODE_PRIVATE).getBoolean("screen_keep", false);
        t.setLabel(keep ? "Layar On" : "Layar Off");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_screen));
        t.setState(keep ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(keep ? "Tidak tidur" : "Normal");
        t.updateTile();
    }
}
