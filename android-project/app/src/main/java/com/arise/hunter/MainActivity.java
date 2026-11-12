package com.arise.hunter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int OVERLAY_REQUEST = 4102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 13, 29));
        getWindow().setNavigationBarColor(Color.rgb(5, 13, 29));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5, 10, 22));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        webView.addJavascriptInterface(new AriseBridge(), "AriseNative");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String syncScript = "(function(){try{var k='ariseFinal';var send=function(v){if(window.AriseNative&&AriseNative.syncPlayerData)AriseNative.syncPlayerData(v)};var old=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){old.call(this,key,value);if(key===k)send(value)};var current=localStorage.getItem(k);if(current)send(current)}catch(e){}})();";
                view.evaluateJavascript(syncScript, null);
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        SharedPreferences prefs = getSharedPreferences("arise_hud", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Activate ARISE Floating HUD?")
                .setMessage("Allow ARISE to display its player panel over other apps. You can turn this off anytime in Android settings.")
                .setPositiveButton("ENABLE HUD", (dialog, which) -> requestOverlayPermission())
                .setNegativeButton("NOT NOW", null)
                .show();
        }
    }

    private void requestOverlayPermission() {
        getSharedPreferences("arise_hud", MODE_PRIVATE).edit().putBoolean("hud_requested", true).apply();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
        } else {
            startHud();
        }
    }

    private void startHud() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps to activate ARISE HUD.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent service = new Intent(this, AriseHudService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) startHud();
            else Toast.makeText(this, "HUD permission was not enabled.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            SharedPreferences prefs = getSharedPreferences("arise_hud", MODE_PRIVATE);
            if (prefs.getBoolean("hud_requested", false)) startHud();
        }
    }

    private class AriseBridge {
        @JavascriptInterface
        public void enableFloatingHud() {
            runOnUiThread(() -> requestOverlayPermission());
        }

        @JavascriptInterface
        public void syncPlayerData(String json) {
            try {
                JSONObject data = new JSONObject(json);
                String name = data.optString("name", "PLAYER");
                int level = Math.max(1, data.optInt("level", 1));
                int xp = Math.max(0, data.optInt("xp", 0));
                String[] ranks = {"E-RANK", "D-RANK", "C-RANK", "B-RANK", "A-RANK", "S-RANK"};
                int[] levels = {1, 4, 7, 12, 20, 30};
                String rank = ranks[0];
                for (int i = 0; i < levels.length; i++) if (level >= levels[i]) rank = ranks[i];
                SharedPreferences prefs = getSharedPreferences("arise_hud", MODE_PRIVATE);
                prefs.edit().putString("player_name", name).putString("player_rank", rank)
                    .putInt("player_level", level).putInt("player_xp", xp).apply();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(MainActivity.this)) {
                    if (prefs.getBoolean("hud_requested", false)) {
                        Intent update = new Intent(MainActivity.this, AriseHudService.class);
                        update.setAction(AriseHudService.ACTION_REFRESH);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(update);
                        else startService(update);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}