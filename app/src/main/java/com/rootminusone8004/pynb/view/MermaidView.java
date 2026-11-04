package com.rootminusone8004.pynb.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.LruCache;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

/**
 * Renders one Mermaid diagram with the bundled, offline {@code assets/mermaid/mermaid.min.js}.
 * The view sizes itself to the diagram's height; tapping it hands a snapshot to the click listener.
 */
@SuppressLint("ViewConstructor")
public class MermaidView extends WebView {

    public interface OnDiagramClickListener {
        void onDiagramClick(@NonNull Bitmap snapshot);
    }

    private static final String BASE_URL = "file:///android_asset/mermaid/";
    private static final int PLACEHOLDER_HEIGHT_DP = 120;
    /** Last measured height (CSS px == dp) per diagram, so re-binds don't jump while re-rendering. */
    private static final LruCache<String, Integer> HEIGHTS = new LruCache<>(128);
    private static final Gson GSON = new Gson();

    private final String cacheKey;
    private final boolean dark;
    @Nullable
    private OnDiagramClickListener clickListener;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public MermaidView(Context context, String diagram, boolean dark) {
        super(context);
        this.dark = dark;
        this.cacheKey = (dark ? "d:" : "l:") + diagram;

        setBackgroundColor(Color.TRANSPARENT);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);      // bundled assets still load via file:///android_asset
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);     // fully offline; diagrams can't fetch anything
        s.setSupportZoom(false);

        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // never navigate away from the diagram
            }
        });
        addJavascriptInterface(new Bridge(), "PynbMermaid");

        Integer cached = HEIGHTS.get(cacheKey);
        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(cached != null ? cached : PLACEHOLDER_HEIGHT_DP)));

        loadDataWithBaseURL(BASE_URL, buildHtml(diagram, dark), "text/html", "utf-8", null);
    }

    public void setOnDiagramClickListener(@Nullable OnDiagramClickListener listener) {
        this.clickListener = listener;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void applyHeight(int cssPx) {
        if (cssPx <= 0) return;
        HEIGHTS.put(cacheKey, cssPx);
        ViewGroup.LayoutParams lp = getLayoutParams();
        int px = dp(cssPx);
        if (lp != null && lp.height != px) {
            lp.height = px;
            setLayoutParams(lp);
        }
    }

    @Nullable
    private Bitmap snapshot() {
        if (getWidth() <= 0 || getHeight() <= 0) return null;
        try {
            Bitmap bmp = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            canvas.drawColor(dark ? Color.rgb(0x1E, 0x1E, 0x1E) : Color.WHITE);
            draw(canvas);
            return bmp;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static String buildHtml(String diagram, boolean dark) {
        // Gson escapes <, > and & as \\u003c etc., so the diagram can't close the <script> tag.
        String src = GSON.toJson(diagram);
        String errColor = dark ? "#FF8A80" : "#C62828";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:transparent;}"
                + "#d{display:flex;justify-content:center;padding:4px 0;}"
                + "#d svg{max-width:100%;height:auto;}"
                + ".err{color:" + errColor + ";font:12px monospace;white-space:pre-wrap;padding:4px;}</style>"
                + "<script src='mermaid.min.js'></script></head><body><div id='d'></div><script>"
                + "var src=" + src + ";"
                + "function report(){requestAnimationFrame(function(){"
                + "PynbMermaid.onHeight(Math.ceil(document.getElementById('d').getBoundingClientRect().height));});}"
                + "function fail(msg){document.querySelectorAll('body>:not(#d):not(script)').forEach(function(n){n.remove();});"
                + "var e=document.createElement('div');e.className='err';"
                + "e.textContent='Mermaid error: '+msg;var d=document.getElementById('d');d.innerHTML='';d.appendChild(e);report();}"
                + "function go(){if(window.innerWidth===0){setTimeout(go,50);return;}"
                + "if(typeof mermaid==='undefined'){fail('renderer not available');return;}"
                + "try{mermaid.initialize({startOnLoad:false,suppressErrorRendering:true,securityLevel:'strict',theme:'" + (dark ? "dark" : "default") + "'});"
                + "mermaid.render('m',src).then(function(r){document.getElementById('d').innerHTML=r.svg;report();})"
                + ".catch(function(e){fail(e&&e.message?e.message:String(e));});}"
                + "catch(e){fail(e&&e.message?e.message:String(e));}}"
                + "window.addEventListener('resize',report);"
                + "document.addEventListener('click',function(){PynbMermaid.onClick();});"
                + "go();</script></body></html>";
    }

    /** Called from JavaScript on a binder thread; hop to the UI thread before touching views. */
    private class Bridge {
        @JavascriptInterface
        public void onHeight(int cssPx) {
            post(() -> applyHeight(cssPx));
        }

        @JavascriptInterface
        public void onClick() {
            post(() -> {
                if (clickListener == null) return;
                Bitmap bmp = snapshot();
                if (bmp != null) clickListener.onDiagramClick(bmp);
            });
        }
    }
}
