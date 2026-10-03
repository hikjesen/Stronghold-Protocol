package com.hiko.stronghold;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String GAME_URL = "http://127.0.0.1:3000/?board=2d";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private TextView status;
    private ProgressBar spinner;
    private WebView webView;
    private volatile boolean destroyed = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();
        buildUi();

        startService(new Intent(this, NodeService.class));

        worker.execute(() -> {
            try {
                GameInstaller.ensureInstalled(this, this::setStatus);
                waitForServer();
            } catch (Throwable t) {
                showFatal("启动失败\n" + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        });
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(13, 18, 16));
        setContentView(root);

        spinner = new ProgressBar(this);
        FrameLayout.LayoutParams spinParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        spinParams.gravity = Gravity.CENTER;
        spinParams.bottomMargin = 70;
        root.addView(spinner, spinParams);

        status = new TextView(this);
        status.setText("卫戍协议正在启动…");
        status.setTextColor(Color.rgb(216, 227, 222));
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        textParams.gravity = Gravity.CENTER;
        textParams.topMargin = 90;
        textParams.leftMargin = 40;
        textParams.rightMargin = 40;
        root.addView(status, textParams);
    }

    private void waitForServer() throws Exception {
        long deadline = System.currentTimeMillis() + 45_000;
        while (!destroyed && System.currentTimeMillis() < deadline) {
            String nodeError = NodeService.getLastError();
            if (nodeError != null) throw new IllegalStateException(nodeError);

            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:3000/healthz").openConnection();
                connection.setConnectTimeout(700);
                connection.setReadTimeout(700);
                connection.setUseCaches(false);
                int code = connection.getResponseCode();
                if (code == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line = reader.readLine();
                        if (line != null && line.contains("\"ok\":true")) {
                            main.post(this::showGame);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Node may still be booting.
            }

            setStatus("正在启动本地游戏服务器…");
            Thread.sleep(350);
        }
        throw new IllegalStateException("本地服务器启动超时");
    }

    private void showGame() {
        if (destroyed) return;

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 18, 16));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl(GAME_URL);

        root.removeAllViews();
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void setStatus(String text) {
        main.post(() -> {
            if (!destroyed && status != null) status.setText(text);
        });
    }

    private void showFatal(String text) {
        main.post(() -> {
            if (destroyed) return;
            if (spinner != null) spinner.setVisibility(View.GONE);
            if (status != null) {
                status.setText(text + "\n\n请截取此页面反馈。");
                status.setTextColor(Color.rgb(255, 160, 160));
            }
        });
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        worker.shutdownNow();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
