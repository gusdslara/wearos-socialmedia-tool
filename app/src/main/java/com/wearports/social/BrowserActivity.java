package com.wearports.social;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.InputDeviceCompat;
import androidx.core.view.MotionEventCompat;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public class BrowserActivity extends Activity {

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";

    // Zoom inicial confortável para telas circulares do Galaxy Watch
    private static final float INITIAL_ZOOM = 1.30f;
    private static final float MIN_ZOOM = 0.8f;
    private static final float MAX_ZOOM = 2.5f;
    private static final float ZOOM_STEP = 0.10f;

    private static GeckoRuntime sGeckoRuntime;
    private GeckoSession geckoSession;
    private GeckoView geckoView;
    private ProgressBar progressBar;
    private TextView txtZoomIndicator;
    private float currentZoom = INITIAL_ZOOM;

    private final Handler hideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideZoomRunnable = () -> {
        if (txtZoomIndicator != null) {
            txtZoomIndicator.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        geckoView = findViewById(R.id.geckoview);
        progressBar = findViewById(R.id.progress_loading);
        txtZoomIndicator = findViewById(R.id.txt_zoom_indicator);

        String targetUrl = getIntent().getStringExtra(EXTRA_URL);
        if (targetUrl == null) {
            targetUrl = "https://m.facebook.com";
        }

        // Inicializa o GeckoRuntime compartilhado com cookies e persistência total
        if (sGeckoRuntime == null) {
            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .consoleOutput(true)
                .build();
            sGeckoRuntime = GeckoRuntime.create(getApplicationContext(), runtimeSettings);
        }

        // Configuração da Sessão GeckoView (User Agent Móvel moderno)
        GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
            .usePrivateMode(false) // Modo normal para salvar cookies, sessão e logins
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .build();

        geckoSession = new GeckoSession(sessionSettings);
        geckoSession.open(sGeckoRuntime);

        // Barra de progresso de carregamento
        geckoSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(@NonNull GeckoSession session, @NonNull String url) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(15);
            }

            @Override
            public void onPageStop(@NonNull GeckoSession session, boolean success) {
                progressBar.setVisibility(View.GONE);
                applyZoom();
            }

            @Override
            public void onProgressChange(@NonNull GeckoSession session, int progress) {
                if (progress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // Vincula a sessão à view
        geckoView.setSession(geckoSession);

        // Suporte à Coroa Rotativa do Galaxy Watch (Rotary Encoder)
        View container = findViewById(R.id.gecko_container);
        container.requestFocus();
        container.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL &&
                event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
                
                float axisValue = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL);
                if (axisValue > 0) {
                    adjustZoom(ZOOM_STEP);
                } else if (axisValue < 0) {
                    adjustZoom(-ZOOM_STEP);
                }
                return true;
            }
            return false;
        });

        geckoSession.loadUri(targetUrl);
    }

    private void adjustZoom(float delta) {
        float newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, currentZoom + delta));
        if (Math.abs(newZoom - currentZoom) > 0.01f) {
            currentZoom = newZoom;
            applyZoom();
            int percent = Math.round(currentZoom * 100);
            showZoomFeedback("Zoom: " + percent + "%");
        }
    }

    private void applyZoom() {
        if (geckoSession != null) {
            // Injeta zoom CSS responsivo compatível com o motor Gecko
            String js = "document.body.style.zoom = '" + currentZoom + "';";
            geckoSession.loadUri("javascript:(function(){" + js + "})()");
        }
    }

    private void showZoomFeedback(String text) {
        if (txtZoomIndicator != null) {
            txtZoomIndicator.setText(text);
            txtZoomIndicator.setVisibility(View.VISIBLE);
            hideHandler.removeCallbacks(hideZoomRunnable);
            hideHandler.postDelayed(hideZoomRunnable, 1200);
        }
    }

    @Override
    public void onBackPressed() {
        if (geckoSession != null) {
            geckoSession.getHistoryDelegate();
            // Volta para a lista de redes
            super.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (geckoSession != null) {
            geckoSession.close();
        }
    }
}
