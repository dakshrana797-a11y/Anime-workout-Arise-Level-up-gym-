package com.arise.hunter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AriseHudService extends Service {
    private static final String CHANNEL_ID = "arise_hud_channel";
    private static final int NOTIFICATION_ID = 731;
    private WindowManager windowManager;
    private View hudView;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int startX, startY;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        showHud();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "ARISE Floating HUD",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the user-enabled ARISE floating player HUD available.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ARISE System is active")
                .setContentText("Your floating Player HUD is ready.")
                .setContentIntent(pending).setOngoing(true).build();
        }
        return new Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ARISE System is active")
            .setContentText("Your floating Player HUD is ready.")
            .setContentIntent(pending).setOngoing(true).build();
    }

    private void showHud() {
        if (hudView != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[] { Color.rgb(8, 27, 51), Color.rgb(3, 10, 24) });
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(38, 166, 255));
        panel.setBackground(bg);
        panel.setElevation(dp(12));

        TextView title = new TextView(this);
        title.setText("✦  A R I S E     //     SYSTEM");
        title.setTextColor(Color.rgb(93, 203, 255));
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        TextView status = new TextView(this);
        status.setText("PLAYER STATUS     •     ONLINE\nE-RANK     |     LEVEL 01     |     0 XP");
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setLineSpacing(dp(4), 1f);
        status.setPadding(0, dp(10), 0, dp(8));
        panel.addView(status);

        TextView quest = new TextView(this);
        quest.setText("DAILY QUEST  //  Awaiting player input");
        quest.setTextColor(Color.rgb(150, 194, 224));
        quest.setTextSize(11);
        panel.addView(quest);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button open = makeButton("OPEN ARISE");
        Button close = makeButton("HIDE HUD");
        actions.addView(open, new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        closeLp.leftMargin = dp(8);
        actions.addView(close, closeLp);
        panel.addView(actions);

        open.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        });
        close.setOnClickListener(v -> stopSelf());

        title.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    startX = params.x; startY = params.y; return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + (int)(event.getRawX() - downX);
                    params.y = startY + (int)(event.getRawY() - downY);
                    windowManager.updateViewLayout(hudView, params); return true;
                default: return false;
            }
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(dp(270), WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(12); params.y = dp(120);
        hudView = panel;
        try { windowManager.addView(hudView, params); }
        catch (Exception e) { hudView = null; stopSelf(); }
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextSize(10); b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(16, 91, 145)));
        return b;
    }

    private int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && hudView != null) {
            try { windowManager.removeView(hudView); } catch (Exception ignored) {}
        }
        hudView = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}