package com.wearports.social;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

public class MainActivity extends Activity {

    private static final String TAG = "SocialWear";
    private static final String PREFS_NAME = "social_prefs";
    private static final String KEY_SAVED_IS_SQUARE = "saved_is_square";

    // Zoom calibrado para 50% (idêntico ao DevTools desktop a 344px)
    // Stories, feed e barra inferior cabem perfeitamente sem scroll horizontal
    private static final int ZOOM_PERCENT = 50;
    private static final float SCALE = ZOOM_PERCENT / 100.0f; // 0.50f

    // Proporção do quadrado interno no círculo (SQUARE_FACTOR = 0.76f) ~328px em tela de 432px
    private static final float SQUARE_FACTOR = 0.76f;

    private static GeckoRuntime sGeckoRuntime;
    private GeckoSession geckoSession;
    private GeckoView geckoView;

    private FrameLayout rootContainer;
    private ScrollView scrollSocial;
    private FrameLayout browserContainer;
    private FrameLayout geckoBox;
    private ProgressBar progressBar;
    private LinearLayout bottomControlsBar;
    private FrameLayout btnBackHome;
    private FrameLayout btnToggleAspect;
    private TextView txtAspectIcon;

    private boolean isSquareMode = true;
    private boolean isBrowserActive = false;
    private boolean isMusicMode = false;
    private boolean isYouTubeVideoMode = false;

    private int screenWidthPx = 432;
    private int screenHeightPx = 432;

    // Controle de gesto para invocar botões arrastando de baixo para cima
    private float touchDownY = 0f;
    private float touchDownX = 0f;
    private boolean isTrackingBottomGesture = false;

    // Timer para auto-ocultar a barra de controles após 4 segundos de inatividade
    private final Handler controlsHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsRunnable = () -> hideControlsBar();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isSquareMode = prefs.getBoolean(KEY_SAVED_IS_SQUARE, true);

        // Detecta resolução da tela
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidthPx = size.x > 0 ? size.x : 432;
        screenHeightPx = size.y > 0 ? size.y : 432;

        rootContainer = findViewById(R.id.root_container);
        scrollSocial = findViewById(R.id.scroll_social);
        browserContainer = findViewById(R.id.browser_container);
        geckoBox = findViewById(R.id.gecko_box);
        geckoView = findViewById(R.id.geckoview);
        progressBar = findViewById(R.id.progress_loading);
        bottomControlsBar = findViewById(R.id.bottom_controls_bar);
        btnBackHome = findViewById(R.id.btn_back_home);
        btnToggleAspect = findViewById(R.id.btn_toggle_aspect);
        txtAspectIcon = findViewById(R.id.txt_aspect_icon);

        // 3 Linhas com acesso direto:
        // Linha 1: Facebook, Instagram
        findViewById(R.id.btn_facebook).setOnClickListener(v -> openSocialWeb("https://m.facebook.com", false));
        findViewById(R.id.btn_instagram).setOnClickListener(v -> openSocialWeb("https://www.instagram.com/accounts/login/", false));

        // Linha 2: LinkedIn, Reddit
        findViewById(R.id.btn_linkedin).setOnClickListener(v -> openSocialWeb("https://www.linkedin.com/feed", false));
        findViewById(R.id.btn_reddit).setOnClickListener(v -> openSocialWeb("https://www.reddit.com", false));

        // Linha 3: YouTube, YouTube Music
        findViewById(R.id.btn_youtube).setOnClickListener(v -> openSocialWeb("https://m.youtube.com", false));
        findViewById(R.id.btn_youtube_music).setOnClickListener(v -> openSocialWeb("https://music.youtube.com", true));

        btnBackHome.setOnClickListener(v -> closeBrowser());
        btnToggleAspect.setOnClickListener(v -> toggleAspectRatio());

        // Inicializa o motor GeckoView com cookies persistentes (para manter login ativo)
        if (sGeckoRuntime == null) {
            GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .consoleOutput(false)
                .build();
            sGeckoRuntime = GeckoRuntime.create(getApplicationContext(), runtimeSettings);
        }

        handleIntent(getIntent());
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra("open")) {
            String openVal = intent.getStringExtra("open");
            if ("0".equals(openVal)) openSocialWeb("https://m.facebook.com", false);
            else if ("1".equals(openVal)) openSocialWeb("https://www.instagram.com/accounts/login/", false);
            else if ("2".equals(openVal)) openSocialWeb("https://www.linkedin.com/feed", false);
            else if ("3".equals(openVal)) openSocialWeb("https://www.reddit.com", false);
            else if ("4".equals(openVal) || "yt".equalsIgnoreCase(openVal)) openSocialWeb("https://m.youtube.com", false);
            else if ("5".equals(openVal) || "music".equalsIgnoreCase(openVal)) openSocialWeb("https://music.youtube.com", true);
        }
    }

    private void openSocialWeb(String url, boolean isMusic) {
        this.isMusicMode = isMusic;
        this.isYouTubeVideoMode = !isMusic && url.contains("youtube.com");
        isBrowserActive = true;
        scrollSocial.setVisibility(View.GONE);
        browserContainer.setVisibility(View.VISIBLE);

        applyAspectRatioLayout();

        // Inicia com os controles recolhidos para tela 100% limpa
        if (bottomControlsBar != null) {
            bottomControlsBar.setVisibility(View.GONE);
            bottomControlsBar.setTranslationY(60f);
            bottomControlsBar.setAlpha(0f);
        }

        if (geckoSession == null) {
            GeckoSessionSettings sessionSettings = new GeckoSessionSettings.Builder()
                .usePrivateMode(false) // Salva login e senhas das contas
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .build();

            geckoSession = new GeckoSession(sessionSettings);
            geckoSession.open(sGeckoRuntime);

            geckoSession.setPromptDelegate(new GeckoSession.PromptDelegate() {
                @Nullable
                @Override
                public GeckoResult<PromptResponse> onAlertPrompt(@NonNull GeckoSession session, @NonNull AlertPrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                @Nullable
                @Override
                public GeckoResult<PromptResponse> onButtonPrompt(@NonNull GeckoSession session, @NonNull ButtonPrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }
            });

            geckoSession.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStart(@NonNull GeckoSession session, @NonNull String pageUrl) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(20);
                    applyZoomToWeb();
                }

                @Override
                public void onPageStop(@NonNull GeckoSession session, boolean success) {
                    progressBar.setVisibility(View.GONE);
                    applyZoomToWeb();
                    suppressInstallAppBanners();
                    if (isMusicMode) {
                        applyMusicAudioOnlyMode();
                    } else if (isYouTubeVideoMode) {
                        applyYouTubeVideoLayout();
                    }
                }

                @Override
                public void onProgressChange(@NonNull GeckoSession session, int progress) {
                    if (progress < 100) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(progress);
                        if (progress > 35) {
                            applyZoomToWeb();
                            suppressInstallAppBanners();
                            if (isMusicMode) {
                                applyMusicAudioOnlyMode();
                            } else if (isYouTubeVideoMode) {
                                applyYouTubeVideoLayout();
                            }
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            });

            geckoView.setSession(geckoSession);
        }

        applyZoomToWeb();
        geckoSession.loadUri(url);
        rootContainer.requestFocus();
    }

    /**
     * Intercepta o gesto de arrastar da base para cima para invocar o painel inferior de navegação
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isBrowserActive) {
            float y = ev.getRawY();
            float x = ev.getRawX();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Inicia tracking se o toque começou nos 110px inferiores da tela
                    if (y >= screenHeightPx - 110) {
                        touchDownY = y;
                        touchDownX = x;
                        isTrackingBottomGesture = true;
                    } else {
                        isTrackingBottomGesture = false;
                        // Se o dock inferior estiver aberto e o usuário tocar no conteúdo web acima, recolhe o dock
                        if (bottomControlsBar != null && bottomControlsBar.getVisibility() == View.VISIBLE) {
                            if (y < screenHeightPx - 120) {
                                hideControlsBar();
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isTrackingBottomGesture) {
                        float dy = touchDownY - y; // Positivo ao arrastar para cima
                        float dx = Math.abs(x - touchDownX);
                        if (dy > 18 && dy > dx) {
                            showControlsBar();
                            isTrackingBottomGesture = false;
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Se foi um toque direto na borda inferior extrema
                    if (isTrackingBottomGesture && y >= screenHeightPx - 50) {
                        showControlsBar();
                    }
                    isTrackingBottomGesture = false;
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Invoca o painel inferior completo deslizando suavemente de baixo para cima
     */
    private void showControlsBar() {
        if (bottomControlsBar == null) return;

        controlsHideHandler.removeCallbacks(hideControlsRunnable);

        float height = bottomControlsBar.getHeight() > 0 ? bottomControlsBar.getHeight() : 120f;
        if (bottomControlsBar.getVisibility() != View.VISIBLE) {
            bottomControlsBar.setVisibility(View.VISIBLE);
            bottomControlsBar.setAlpha(0f);
            bottomControlsBar.setTranslationY(height);
            bottomControlsBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        } else {
            bottomControlsBar.animate().alpha(1f).translationY(0f).setDuration(150).start();
        }

        // Fica disponível por 4.5 segundos e então recolhe suavemente
        controlsHideHandler.postDelayed(hideControlsRunnable, 4500);
    }

    /**
     * Recolhe o painel inferior de volta para fora da tela
     */
    private void hideControlsBar() {
        if (bottomControlsBar == null || bottomControlsBar.getVisibility() != View.VISIBLE) return;

        float hideY = bottomControlsBar.getHeight() > 0 ? bottomControlsBar.getHeight() : 120f;
        bottomControlsBar.animate()
            .translationY(hideY)
            .alpha(0f)
            .setDuration(240)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(() -> {
                if (bottomControlsBar != null) {
                    bottomControlsBar.setVisibility(View.GONE);
                }
            })
            .start();
    }

    private void toggleAspectRatio() {
        isSquareMode = !isSquareMode;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SAVED_IS_SQUARE, isSquareMode)
            .apply();

        applyAspectRatioLayout();
        applyZoomToWeb();
        showControlsBar();
    }

    private void applyAspectRatioLayout() {
        if (geckoBox == null) return;

        ViewGroup.LayoutParams lp = geckoBox.getLayoutParams();
        if (isSquareMode) {
            int squareSide = (int) (Math.min(screenWidthPx, screenHeightPx) * SQUARE_FACTOR);
            lp.width = squareSide;
            lp.height = squareSide;
            geckoBox.setLayoutParams(lp);

            txtAspectIcon.setText("◯"); // Clicar vai para redondo/tela cheia
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            geckoBox.setLayoutParams(lp);

            txtAspectIcon.setText("▢"); // Clicar volta para quadrado
        }
    }

    /**
     * Técnica comprovada de Zoom a 50%:
     * Aplica escala CSS (transform: scale(0.5)) combinada com largura proporcional 200%.
     * A largura CSS torna-se ~650px a 688px (idêntico ao DevTools desktop a 344px).
     * O Instagram/Facebook reconhece essa largura ampla para renderizar a coluna móvel completa,
     * stories inteiros e cabeçalho, escalando perfeitamente para dentro do visor do relógio SEM SCROLL HORIZONTAL!
     */
    private void applyZoomToWeb() {
        if (geckoSession == null || !isBrowserActive) return;

        float scale = SCALE; // 0.50f
        String widthPct = String.format(java.util.Locale.US, "%.2f%%", (100.0f / scale)); // 200.00%

        String js = "(function() {" +
            "  var scale = " + scale + ";" +
            "  var widthStr = '" + widthPct + "';" +
            "  var styleId = '__wearos_zoom_technique__';" +
            "  var st = document.getElementById(styleId);" +
            "  if (!st) {" +
            "    st = document.createElement('style');" +
            "    st.id = styleId;" +
            "    (document.head || document.documentElement).appendChild(st);" +
            "  }" +
            "  st.innerHTML = " +
            "    'html {' +" +
            "    '  transform: scale(' + scale + ') !important;' +" +
            "    '  transform-origin: top left !important;' +" +
            "    '  width: ' + widthStr + ' !important;' +" +
            "    '  min-width: ' + widthStr + ' !important;' +" +
            "    '  height: ' + widthStr + ' !important;' +" +
            "    '  overflow-x: hidden !important;' +" +
            "    '}' +" +
            "    'body {' +" +
            "    '  width: 100% !important;' +" +
            "    '  min-width: 100% !important;' +" +
            "    '  overflow-x: hidden !important;' +" +
            "    '  margin: 0 !important;' +" +
            "    '  padding: 0 !important;' +" +
            "    '}' +" +
            "    'main, article, section, [role=\"main\"], div[id*=\"mount\"] {' +" +
            "    '  max-width: 100% !important;' +" +
            "    '  box-sizing: border-box !important;' +" +
            "    '}';" +
            "  document.documentElement.style.transform = 'scale(' + scale + ')';" +
            "  document.documentElement.style.transformOrigin = 'top left';" +
            "  document.documentElement.style.width = widthStr;" +
            "  document.documentElement.style.minWidth = widthStr;" +
            "  document.documentElement.style.height = widthStr;" +
            "  document.documentElement.style.overflowX = 'hidden';" +
            "  document.body.style.width = '100%';" +
            "  document.body.style.minWidth = '100%';" +
            "  document.body.style.overflowX = 'hidden';" +
            "})();";

        geckoSession.loadUri("javascript:" + js);
    }

    /**
     * Otimização YouTube Music: Modo Somente Áudio (poupa GPU e bateria, sem travar reprodução)
     */
    private void applyMusicAudioOnlyMode() {
        if (geckoSession == null || !isBrowserActive) return;

        String musicCss =
            "video { opacity: 0 !important; width: 1px !important; height: 1px !important; position: absolute !important; left: -9999px !important; } " +
            "ytmusic-player { height: 0 !important; min-height: 0 !important; max-height: 0 !important; overflow: hidden !important; } " +
            "ytmusic-player-page #main-panel { display: none !important; } " +
            "ytmusic-player-bar { position: fixed !important; bottom: 0 !important; width: 100% !important; z-index: 99999 !important; background: #000000 !important; } " +
            "ytmusic-app-bar { height: 44px !important; } " +
            "ytmusic-upsell-dialog, ytd-popup-container, .yt-upsell-dialog-renderer { display: none !important; }";

        String script = "(function() {" +
            "  var styleId = '__wearos_music_mode_css__';" +
            "  var st = document.getElementById(styleId);" +
            "  if (!st) {" +
            "    st = document.createElement('style');" +
            "    st.id = styleId;" +
            "    (document.head || document.documentElement).appendChild(st);" +
            "  }" +
            "  st.innerHTML = `" + musicCss + "`;" +
            "  function enforceAudioOnly() {" +
            "    var vids = document.getElementsByTagName('video');" +
            "    for (var i = 0; i < vids.length; i++) {" +
            "      var v = vids[i];" +
            "      if (!v.__audioOnlyApplied) {" +
            "        v.__audioOnlyApplied = true;" +
            "        v.style.setProperty('width', '1px', 'important');" +
            "        v.style.setProperty('height', '1px', 'important');" +
            "        v.style.setProperty('opacity', '0', 'important');" +
            "      }" +
            "    }" +
            "  }" +
            "  enforceAudioOnly();" +
            "  if (!window.__wearos_music_observer) {" +
            "    window.__wearos_music_observer = true;" +
            "    var obs = new MutationObserver(enforceAudioOnly);" +
            "    obs.observe(document.body || document.documentElement, { childList: true, subtree: true });" +
            "  }" +
            "})();";

        geckoSession.loadUri("javascript:" + script);
    }

    /**
     * Otimização YouTube Vídeos
     */
    private void applyYouTubeVideoLayout() {
        if (geckoSession == null || !isBrowserActive) return;

        String ytCss =
            "#player-container-id, .player-container, #player { " +
            "  position: sticky !important; top: 0 !important; z-index: 99999 !important; " +
            "  width: 100% !important; max-height: 52vw !important; background: #000 !important; " +
            "} " +
            "video { width: 100% !important; height: auto !important; max-height: 52vw !important; object-fit: contain !important; } " +
            "header.mobile-topbar-header, ytm-header-bar { height: 42px !important; padding: 2px !important; } " +
            "ytm-mealbar-promo-renderer, ytm-upsell-dialog-renderer, .yt-upsell-dialog-renderer { display: none !important; }";

        String script = "(function() {" +
            "  var styleId = '__wearos_yt_video_css__';" +
            "  var st = document.getElementById(styleId);" +
            "  if (!st) {" +
            "    st = document.createElement('style');" +
            "    st.id = styleId;" +
            "    (document.head || document.documentElement).appendChild(st);" +
            "  }" +
            "  st.innerHTML = `" + ytCss + "`;" +
            "})();";

        geckoSession.loadUri("javascript:" + script);
    }

    /**
     * Remove banners intrusivos de "Baixar App / Abrir no App" comuns no Instagram, Reddit e Facebook mobile.
     */
    private void suppressInstallAppBanners() {
        if (geckoSession == null || !isBrowserActive) return;

        String css =
            "div[class*='app-banner'], div[id*='app-banner'], .smartbanner, .google-play-banner, " +
            "#mobile_login_install_prompt, div[data-sigil*='m-app-banner'], div[data-sigil*='app-banner'], " +
            "div[class*='install_app'], div[class*='upsell'], div[id*='upsell'], " +
            "div[class*='_a9-v'], div[class*='_a70d'], div[class*='x5ur3da'], " +
            "div[role='dialog'] div:has(button), " +
            "reddit-promoted-app, xpromo-app-selector, shreddit-async-loader[bundlename='bottom_bar_xpromo'], " +
            "div[class*='BottomBarXPromo'], div[class*='XPromo'], " +
            ".app-modal-layer, .join-form__cta-container, div[data-tracking-control-name*='app_banner'], " +
            "[aria-label*='app' i][role='dialog'], " +
            "div[data-nosnippet] " +
            "{ display: none !important; opacity: 0 !important; pointer-events: none !important; visibility: hidden !important; height: 0 !important; max-height: 0 !important; overflow: hidden !important; }";

        String script = "(function() {" +
            "  var styleId = '__wearos_adblock_css__';" +
            "  if (!document.getElementById(styleId)) {" +
            "    var st = document.createElement('style');" +
            "    st.id = styleId;" +
            "    st.innerHTML = `" + css + "`;" +
            "    (document.head || document.documentElement).appendChild(st);" +
            "  }" +
            "  try {" +
            "    localStorage.setItem('xpromo_dismissed', 'true');" +
            "    sessionStorage.setItem('xpromo_dismissed', 'true');" +
            "  } catch(e){}" +
            "  function sweepFooters() {" +
            "    var elements = document.querySelectorAll('div, footer, section, aside');" +
            "    for (var i = 0; i < elements.length; i++) {" +
            "      var el = elements[i];" +
            "      var style = window.getComputedStyle(el);" +
            "      if (style.position === 'fixed' || style.position === 'sticky') {" +
            "        var rect = el.getBoundingClientRect();" +
            "        if (rect.bottom >= window.innerHeight - 80 && rect.height < 220) {" +
            "          var txt = (el.innerText || '').toLowerCase();" +
            "          if (txt.includes('app') || txt.includes('instalar') || txt.includes('install') || " +
            "              txt.includes('obter') || txt.includes('abrir no') || txt.includes('open in') || " +
            "              txt.includes('get instagram') || txt.includes('use the app')) {" +
            "            el.style.setProperty('display', 'none', 'important');" +
            "            el.style.setProperty('height', '0px', 'important');" +
            "            el.remove();" +
            "          }" +
            "        }" +
            "      }" +
            "    }" +
            "  }" +
            "  sweepFooters();" +
            "  if (!window.__wearos_observer_attached) {" +
            "    window.__wearos_observer_attached = true;" +
            "    var obs = new MutationObserver(function() { sweepFooters(); });" +
            "    obs.observe(document.body || document.documentElement, { childList: true, subtree: true });" +
            "  }" +
            "})();";

        geckoSession.loadUri("javascript:" + script);
    }

    private void closeBrowser() {
        isBrowserActive = false;
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        if (bottomControlsBar != null) {
            bottomControlsBar.setVisibility(View.GONE);
        }
        browserContainer.setVisibility(View.GONE);
        scrollSocial.setVisibility(View.VISIBLE);
        rootContainer.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (isBrowserActive) {
            closeBrowser();
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
