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
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new AriseBridge(), "AriseNative");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        SharedPreferences prefs = getSharedPreferences("arise_hud", MODE_PRIVATE);
        if (!prefs.getBoolean("overlay_prompt_seen", false)) {
            prefs.edit().putBoolean("overlay_prompt_seen", true).apply();
            new AlertDialog.Builder(this)
                .setTitle("Activate ARISE Floating HUD?")
                .setMessage("Allow ARISE to display its player panel over other apps. You can turn this off anytime in Android settings.")
                .setPositiveButton("ENABLE HUD", (dialog, which) -> requestOverlayPermission())
                .setNegativeButton("NOT NOW", null)
                .show();
        }
    }

    private void requestOverlayPermission() {
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
        Toast.makeText(this, "ARISE HUD activated.", Toast.LENGTH_SHORT).show();
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
            runOnUiThread(() -> {
                getSharedPreferences("arise_hud", MODE_PRIVATE).edit().putBoolean("hud_requested", true).apply();
                requestOverlayPermission();
            });
        }
        @JavascriptInterface
        public void openPlayerProfile() {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Player profile is available in ARISE.", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}