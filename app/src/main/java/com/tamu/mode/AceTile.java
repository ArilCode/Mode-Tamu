package com.tamu.mode;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.HashSet;

public class AceTile extends TileService {

    @Override
    public void onStartListening() {
        refresh();
    }

    @Override
    public void onClick() {
        SharedPreferences sp = getSharedPreferences("tamu", MODE_PRIVATE);
        boolean guest = sp.getBoolean("guest", false);

        if (guest) {
            openApp();
            return;
        }
        if (sp.getString("pin", null) == null
                || sp.getStringSet("locked", new HashSet<>()).isEmpty()
                || !GuestService.isEnabled(this)) {
            Toast.makeText(this, "Atur dulu di aplikasi Mode ACE", Toast.LENGTH_LONG).show();
            openApp();
            return;
        }
        sp.edit().putBoolean("guest", true).apply();
        refresh();
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean guest = getSharedPreferences("tamu", MODE_PRIVATE).getBoolean("guest", false);
        t.setLabel("Mode ACE");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        t.setState(guest ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(guest ? "Mode tamu" : "Mode owner");
        t.updateTile();
    }
}