package com.voidwave.player;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

public final class MainActivity extends Activity {
    private static final String DEFAULT_URL =
            "https://ice1.somafm.com/groovesalad-128-mp3";

    private static final int BG = Color.rgb(8, 11, 16);
    private static final int PANEL = Color.rgb(17, 23, 32);
    private static final int CYAN = Color.rgb(112, 228, 255);
    private static final int MUTED = Color.rgb(139, 157, 175);
    private static final int WHITE = Color.rgb(238, 245, 250);

    private EditText urlInput;
    private TextView status;
    private TextView playState;
    private Button pauseButton;

    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private SharedPreferences preferences;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void addSpace(LinearLayout parent, int height) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private Button button(String label, int background, int foreground) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(shape(background, 14));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        return b;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        preferences = getSharedPreferences("voidwave", MODE_PRIVATE);
        buildInterface();
        connectController();
        requestNotificationPermission();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView logo = text("V/", 30, CYAN, true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(text("VOIDWAVE", 23, WHITE, true));
        names.addView(text("PERSONAL AUDIO RECEIVER", 10, MUTED, true));
        header.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        TextView version = text("V 0.1", 11, CYAN, true);
        header.addView(version);
        root.addView(header);

        addSpace(root, 34);

        TextView overline = text("01   /   RECEIVER STATUS", 12, CYAN, true);
        root.addView(overline);

        addSpace(root, 12);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        statusCard.setBackground(shape(PANEL, 20));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = text("●", 18, CYAN, true);
        statusRow.addView(dot);

        TextView live = text("  SIGNAL RECEIVER", 13, WHITE, true);
        statusRow.addView(live, new LinearLayout.LayoutParams(0, -2, 1));

        statusRow.addView(text("READY", 11, CYAN, true));
        statusCard.addView(statusRow);

        addSpace(statusCard, 18);

        playState = text("Awaiting signal", 23, WHITE, true);
        statusCard.addView(playState);

        addSpace(statusCard, 6);

        status = text("Enter a stream URL to tune in.", 13, MUTED, false);
        statusCard.addView(status);

        addSpace(statusCard, 18);

        LinearLayout bars = new LinearLayout(this);
        bars.setGravity(Gravity.CENTER_VERTICAL);
        int[] heights = {8, 13, 10, 20, 12, 25, 15, 9, 19, 12, 7, 16, 10, 23, 12, 8, 18, 11, 6, 15};
        for (int h : heights) {
            View bar = new View(this);
            bar.setBackground(shape(CYAN, 3));
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, dp(h), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bars.addView(bar, lp);
        }
        statusCard.addView(bars);
        root.addView(statusCard);

        addSpace(root, 30);

        root.addView(text("02   /   TUNE SOURCE", 12, CYAN, true));
        addSpace(root, 12);

        TextView label = text("STREAM ADDRESS", 11, MUTED, true);
        root.addView(label);
        addSpace(root, 8);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(13);
        urlInput.setTextColor(WHITE);
        urlInput.setHintTextColor(MUTED);
        urlInput.setHint("https://your-audio-stream...");
        urlInput.setSelectAllOnFocus(true);
        urlInput.setInputType(0x00000011);
        urlInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        urlInput.setBackground(shape(PANEL, 14));

        String lastUrl = preferences.getString("last_url", DEFAULT_URL);
        urlInput.setText(lastUrl);

        root.addView(urlInput,
                new LinearLayout.LayoutParams(-1, dp(54)));

        addSpace(root, 12);

        Button tune = button("  ▶    TUNE IN", CYAN, BG);
        root.addView(tune, new LinearLayout.LayoutParams(-1, dp(52)));

        tune.setOnClickListener(v -> tuneIn());

        addSpace(root, 10);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        pauseButton = button("Ⅱ   Pause", PANEL, WHITE);
        Button stop = button("■   Stop", PANEL, WHITE);

        LinearLayout.LayoutParams half =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        half.setMargins(0, 0, dp(6), 0);
        controls.addView(pauseButton, half);

        LinearLayout.LayoutParams half2 =
                new LinearLayout.LayoutParams(0, dp(48), 1);
        half2.setMargins(dp(6), 0, 0, 0);
        controls.addView(stop, half2);
        root.addView(controls);

        pauseButton.setOnClickListener(v -> {
            if (controller == null) {
                setStatus("Receiver is still initializing.");
                return;
            }

            if (controller.isPlaying()) {
                controller.pause();
                pauseButton.setText("▶   Resume");
                playState.setText("Paused");
                setStatus("Stream paused.");
            } else if (controller.getMediaItemCount() > 0) {
                controller.play();
                pauseButton.setText("Ⅱ   Pause");
                playState.setText("Receiving audio");
                setStatus("Playback resumed.");
            } else {
                tuneIn();
            }
        });

        stop.setOnClickListener(v -> {
            if (controller != null) controller.stop();
            playState.setText("Standby");
            pauseButton.setText("Ⅱ   Pause");
            setStatus("Playback stopped.");
        });

        addSpace(root, 28);
        root.addView(text("03   /   SYSTEM", 12, CYAN, true));
        addSpace(root, 10);
        root.addView(text(
                "BACKGROUND AUDIO ENABLED\n"
                + "VISUALS ARE UI-BOUND\n"
                + "LOCAL PLAYLISTS: NEXT BUILD",
                11, MUTED, false));

        addSpace(root, 24);
        TextView footer = text("VOIDWAVE  •  QUIET SIGNAL, CLEAR SOUND",
                10, MUTED, true);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        setContentView(scroll);
    }

    private void connectController() {
        SessionToken token = new SessionToken(
                this, new ComponentName(this, PlaybackService.class));

        controllerFuture = new MediaController.Builder(this, token)
                .buildAsync();

        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                setStatus("Receiver ready. Select a stream.");
            } catch (Exception e) {
                setStatus("Could not initialize audio receiver.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void tuneIn() {
        if (controller == null) {
            setStatus("Receiver is initializing. Try again shortly.");
            return;
        }

        String address = urlInput.getText().toString().trim();
        Uri uri = Uri.parse(address);
        String scheme = uri.getScheme();

        if (address.isEmpty()
                || scheme == null
                || !(scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            setStatus("Enter a valid HTTP or HTTPS stream URL.");
            return;
        }

        preferences.edit().putString("last_url", address).apply();

        try {
            ((InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        } catch (Exception ignored) {
        }

        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle("VOIDWAVE Radio")
                .setArtist(uri.getHost())
                .build();

        MediaItem item = new MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .build();

        controller.setMediaItem(item);
        controller.prepare();
        controller.play();

        playState.setText("Connecting...");
        pauseButton.setText("Ⅱ   Pause");
        setStatus("Connecting to " + uri.getHost());
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42);
        }
    }

    @Override
    protected void onDestroy() {
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
        }
        controller = null;
        super.onDestroy();
    }
}
