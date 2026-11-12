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
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.tts.TextToSpeech;
import android.content.pm.PackageManager;
import android.Manifest;
import java.util.ArrayList;
import java.util.Locale;
import android.widget.Toast;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int OVERLAY_REQUEST = 4102;
    private static final int AUDIO_REQUEST = 4103;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private boolean ttsReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(5, 13, 29));
        getWindow().setNavigationBarColor(Color.rgb(5, 13, 29));

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("en", "IN"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.92f);
                textToSpeech.setPitch(0.82f);
                ttsReady = true;
            }
        });
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
        public void startVoiceListening() {
            runOnUiThread(() -> beginVoiceListening());
        }

        @JavascriptInterface
        public void speak(String message) {
            runOnUiThread(() -> speakSystem(message));
        }

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


    private void beginVoiceListening() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_REQUEST);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendVoiceEvent("error", "Speech recognition is not available on this device.");
            return;
        }
        try {
            if (speechRecognizer != null) speechRecognizer.destroy();
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle params) { sendVoiceEvent("status", "LISTENING..."); }
                public void onBeginningOfSpeech() { sendVoiceEvent("status", "HEARING YOU..."); }
                public void onRmsChanged(float rmsdB) {}
                public void onBufferReceived(byte[] buffer) {}
                public void onEndOfSpeech() { sendVoiceEvent("status", "PROCESSING..."); }
                public void onError(int error) { sendVoiceEvent("error", "I couldn't hear that. Tap the mic and try again."); }
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) sendVoiceEvent("result", matches.get(0));
                    else sendVoiceEvent("error", "I didn't catch that. Try again.");
                }
                public void onPartialResults(Bundle partial) {}
                public void onEvent(int eventType, Bundle params) {}
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "ARISE is listening...");
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            sendVoiceEvent("error", "Voice input couldn't start. Check microphone permission.");
        }
    }

    private void speakSystem(String message) {
        if (message == null || message.trim().isEmpty()) return;
        if (ttsReady && textToSpeech != null) {
            if (Build.VERSION.SDK_INT >= 21) textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "ARISE_SYSTEM");
            else textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null);
        } else {
            sendVoiceEvent("status", "Voice engine is starting. Try again in a moment.");
        }
    }

    private void sendVoiceEvent(String type, String value) {
        if (webView == null) return;
        String safe = JSONObject.quote(value == null ? "" : value);
        runOnUiThread(() -> webView.evaluateJavascript("window.ariseNativeVoiceEvent && window.ariseNativeVoiceEvent('" + type + "'," + safe + ")", null));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) beginVoiceListening();
            else sendVoiceEvent("error", "Microphone permission is needed for voice commands.");
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); textToSpeech = null; }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}