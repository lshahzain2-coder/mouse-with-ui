package com.mousebar;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Random;

public class BarService extends Service {

    public static final int PORT = 8080;

    private WindowManager wm;
    private AudioManager am;
    private LinearLayout bar;
    private WindowManager.LayoutParams lp;
    private TextView toggle;
    private TextView muteBtn;
    private RemoteServer remote;
    private final ArrayList<View> actions = new ArrayList<View>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean muted;
    private boolean collapsed;

    // ---- helpers used by MainActivity ----

    public static String pin(Context c) {
        SharedPreferences p = c.getSharedPreferences("mousebar", MODE_PRIVATE);
        String pin = p.getString("pin", null);
        if (pin == null) {
            pin = String.valueOf(1000 + new Random().nextInt(9000));
            p.edit().putString("pin", pin).commit();
        }
        return pin;
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                Enumeration<InetAddress> ad = ni.getInetAddresses();
                while (ad.hasMoreElements()) {
                    InetAddress a = ad.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    // ---- service lifecycle ----

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (bar == null) createBar();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (remote != null) {
            remote.stop();
            remote = null;
        }
        if (bar != null) {
            try {
                wm.removeView(bar);
            } catch (Exception e) {
                // already gone
            }
            bar = null;
        }
        if (am != null && muted) {
            am.setStreamMute(AudioManager.STREAM_MUSIC, false);
        }
    }

    // ---- UI building ----

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView button(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        StateListDrawable sd = new StateListDrawable();
        sd.addState(new int[] { android.R.attr.state_pressed },
                round(Color.rgb(90, 120, 170), 12));
        sd.addState(new int[] {}, round(Color.rgb(52, 68, 100), 12));
        t.setBackgroundDrawable(sd);
        t.setClickable(true);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(p);
        return t;
    }

    private void addAction(TextView t) {
        actions.add(t);
        bar.addView(t);
    }

    private void createBar() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.setBackgroundDrawable(round(Color.argb(220, 16, 24, 43), 18));

        addAction(button("Home", new View.OnClickListener() {
            public void onClick(View v) {
                runCommand("home");
            }
        }));
        addAction(button("Settings", new View.OnClickListener() {
            public void onClick(View v) {
                runCommand("settings");
            }
        }));
        addAction(button("Vol -", new View.OnClickListener() {
            public void onClick(View v) {
                runCommand("voldown");
            }
        }));
        addAction(button("Vol +", new View.OnClickListener() {
            public void onClick(View v) {
                runCommand("volup");
            }
        }));
        muteBtn = button("Mute", new View.OnClickListener() {
            public void onClick(View v) {
                runCommand("mute");
            }
        });
        addAction(muteBtn);

        toggle = button("Hide", new View.OnClickListener() {
            public void onClick(View v) {
                setCollapsed(!collapsed);
            }
        });
        bar.addView(toggle);

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(16);

        try {
            wm.addView(bar, lp);
        } catch (Exception e) {
            Toast.makeText(this, "Mouse Bar could not be shown", Toast.LENGTH_LONG).show();
            bar = null;
        }

        // The phone remote runs even if the overlay could not be shown.
        remote = new RemoteServer(getAssets(), PORT, pin(this),
                new RemoteServer.CommandHandler() {
                    public String run(String cmd) {
                        try {
                            return runCommand(cmd);
                        } catch (Exception e) {
                            return "Error: " + e;
                        }
                    }
                });
        remote.start();
    }

    private void setCollapsed(boolean c) {
        collapsed = c;
        for (View v : actions) {
            v.setVisibility(c ? View.GONE : View.VISIBLE);
        }
        toggle.setText(c ? "Menu" : "Hide");
        wm.updateViewLayout(bar, lp);
    }

    // ---- commands (bar buttons and phone remote share these) ----

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            // nothing to open
        }
    }

    private void toggleMute() {
        muted = !muted;
        am.setStreamMute(AudioManager.STREAM_MUSIC, muted);
        ui.post(new Runnable() {
            public void run() {
                if (muteBtn != null) muteBtn.setText(muted ? "Unmute" : "Mute");
            }
        });
    }

    private String runCommand(String cmd) {
        if (cmd.equals("home")) {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            open(i);
            return "Home";
        }
        if (cmd.equals("settings")) {
            open(new Intent(Settings.ACTION_SETTINGS));
            return "Settings";
        }
        if (cmd.equals("volup")) return changeVolume(1);
        if (cmd.equals("voldown")) return changeVolume(-1);
        if (cmd.equals("mute")) {
            toggleMute();
            return muted ? "Muted" : "Unmuted";
        }

        int key = -1;
        if (cmd.equals("up")) key = KeyEvent.KEYCODE_DPAD_UP;
        else if (cmd.equals("down")) key = KeyEvent.KEYCODE_DPAD_DOWN;
        else if (cmd.equals("left")) key = KeyEvent.KEYCODE_DPAD_LEFT;
        else if (cmd.equals("right")) key = KeyEvent.KEYCODE_DPAD_RIGHT;
        else if (cmd.equals("ok")) key = KeyEvent.KEYCODE_DPAD_CENTER;
        else if (cmd.equals("back")) key = KeyEvent.KEYCODE_BACK;
        else if (cmd.equals("menu")) key = KeyEvent.KEYCODE_MENU;
        if (key < 0) return "Unknown button";

        if (sendKey(key)) return "OK";
        // Other apps block key presses, so hand the key to the MStar Home launcher.
        Intent k = new Intent("com.mousebar.REMOTE_KEY");
        k.putExtra("key", key);
        sendBroadcast(k);
        return "Sent to the launcher. This only works while MStar Home is on screen.";
    }

    // Sets the volume one step and reports what Android says it is now,
    // so the phone shows whether the change really happened.
    private String changeVolume(int dir) {
        int stream = AudioManager.STREAM_MUSIC;
        int before = am.getStreamVolume(stream);
        int max = am.getStreamMaxVolume(stream);
        int target = Math.max(0, Math.min(max, before + dir));
        am.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI);
        int after = am.getStreamVolume(stream);
        if (after == before) {
            // Android did not change it; try the real volume key if allowed.
            sendKey(dir > 0 ? KeyEvent.KEYCODE_VOLUME_UP : KeyEvent.KEYCODE_VOLUME_DOWN);
            return "Volume stayed at " + after + " of " + max;
        }
        return "Volume " + after + " of " + max;
    }

    // Key presses for other apps need system permission. This works only when
    // the TV firmware allows it (for example a rooted TV); otherwise it fails safely.
    private boolean sendKey(int code) {
        String[][] attempts = {
                { "su", "-c", "input keyevent " + code },
                { "sh", "-c", "input keyevent " + code }
        };
        for (String[] cmd : attempts) {
            try {
                Process p = Runtime.getRuntime().exec(cmd);
                if (p.waitFor() == 0) return true;
            } catch (Exception e) {
                // try the next way
            }
        }
        return false;
    }
}
