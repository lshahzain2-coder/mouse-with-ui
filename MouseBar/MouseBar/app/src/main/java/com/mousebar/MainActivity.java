package com.mousebar;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView address;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String ip = BarService.localIp();
        if (ip == null) {
            address.setText("Phone remote: the TV is not connected to a network.");
        } else {
            address.setText("Phone remote: on your phone (same Wi-Fi) open\n"
                    + "http://" + ip + ":" + BarService.PORT + "/"
                    + BarService.pin(this) + "/");
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final SharedPreferences prefs = getSharedPreferences("mousebar", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16, 24, 43));
        root.setPadding(dp(48), dp(40), dp(48), dp(40));

        TextView title = new TextView(this);
        title.setText("Mouse Bar");
        title.setTextColor(Color.WHITE);
        title.setTextSize(40);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("A floating button bar for mouse control.\n"
                + "It stays on top of every app: Home, Settings, Volume -, Volume +, Mute.\n"
                + "Use the Hide button on the bar to shrink it out of the way.");
        info.setTextColor(Color.rgb(159, 179, 200));
        info.setTextSize(22);
        info.setPadding(0, dp(16), 0, dp(24));
        root.addView(info);

        address = new TextView(this);
        address.setTextColor(Color.rgb(79, 195, 247));
        address.setTextSize(24);
        address.setPadding(0, 0, 0, dp(24));
        root.addView(address);

        Button show = new Button(this);
        show.setText("Show bar");
        show.setTextSize(24);
        show.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startService(new Intent(MainActivity.this, BarService.class));
            }
        });
        root.addView(show);

        Button hide = new Button(this);
        hide.setText("Stop bar");
        hide.setTextSize(24);
        hide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, BarService.class));
            }
        });
        root.addView(hide);

        CheckBox boot = new CheckBox(this);
        boot.setText("Start the bar automatically when the TV turns on");
        boot.setTextColor(Color.WHITE);
        boot.setTextSize(22);
        boot.setChecked(prefs.getBoolean("autostart", true));
        boot.setPadding(dp(8), dp(24), 0, 0);
        boot.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton c, boolean checked) {
                prefs.edit().putBoolean("autostart", checked).commit();
            }
        });
        root.addView(boot);

        setContentView(root);

        // Show the bar as soon as the app opens.
        startService(new Intent(this, BarService.class));
    }
}
