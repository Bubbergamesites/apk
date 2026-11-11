package com.emos.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.*;

public class MainActivity extends Activity {
    // Page is served from a real https origin so localStorage/IndexedDB/getUserMedia behave normally.
    static final String HOST = "appassets.androidplatform.net";
    static final String HOME = "https://" + HOST + "/index.html";
    // Set true only if you need SharedArrayBuffer (e.g. the threaded PPSSPP core).
    // Warning: cross-origin isolation can block YouTube embeds.
    static final boolean ISOLATE = false;
    // Zoom level: 1.0 = normal, 0.75 = zoomed out (more screen area visible), 0.5 = very zoomed out.
    static final float ZOOM = 0.75f;

    FrameLayout root;
    WebView web;
    ValueCallback<Uri[]> fileCb;
    PermissionRequest pendingPerm;
    View customView;
    WebChromeClient.CustomViewCallback customCb;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF050816);
        setContentView(root);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setBackgroundColor(0xFF050816);

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String p = u.getPath();
                if ("/".equals(p) || "/index.html".equals(p)) {
                    try {
                        Map<String, String> h = new HashMap<>();
                        h.put("Cache-Control", "no-store");
                        if (ISOLATE) {
                            h.put("Cross-Origin-Opener-Policy", "same-origin");
                            h.put("Cross-Origin-Embedder-Policy", "credentialless");
                        }
                        return new WebResourceResponse("text/html", "utf-8", 200, "OK", h, openApp());
                    } catch (IOException e) { return null; }
                }
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                        new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                Toast.makeText(MainActivity.this, d.didCrash()
                        ? "WebView crashed - restarting"
                        : "Android closed the WebView (out of memory) - restarting", Toast.LENGTH_LONG).show();
                recreate();
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost()) || !r.isForMainFrame()) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            long lastToast;
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e("EmOS", m.message() + " @" + m.sourceId() + ":" + m.lineNumber());
                    long now = System.currentTimeMillis();
                    if (now - lastToast > 2500) {
                        lastToast = now;
                        String t = m.message();
                        if (t.length() > 160) t = t.substring(0, 160);
                        Toast.makeText(MainActivity.this, "JS error: " + t, Toast.LENGTH_LONG).show();
                    }
                }
                return false;
            }
            @Override public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(() -> {
                    List<String> missing = new ArrayList<>();
                    for (String r : req.getResources()) {
                        String p = null;
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) p = Manifest.permission.CAMERA;
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) p = Manifest.permission.RECORD_AUDIO;
                        if (p != null && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
                    }
                    if (missing.isEmpty()) { req.grant(req.getResources()); return; }
                    pendingPerm = req;
                    requestPermissions(missing.toArray(new String[0]), 1);
                });
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
                cb.invoke(origin, true, false);
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try { startActivityForResult(p.createIntent(), 10); }
                catch (Exception e) { fileCb = null; return false; }
                return true;
            }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = v; customCb = cb;
                web.setVisibility(View.GONE);
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCb != null) customCb.onCustomViewHidden();
            }
        });

        web.loadUrl(HOME);
    }

    /** The HTML is stored as emos_000.bin, emos_001.bin, ... in assets and streamed back-to-back. */
    InputStream openApp() throws IOException {
        String[] all = getAssets().list("");
        List<String> parts = new ArrayList<>();
        if (all != null) for (String n : all) if (n.startsWith("emos_") && n.endsWith(".bin")) parts.add(n);
        if (parts.isEmpty()) throw new IOException("No app chunks in assets");
        Collections.sort(parts);
        Vector<InputStream> v = new Vector<>();
        for (String n : parts) v.add(getAssets().open(n, android.content.res.AssetManager.ACCESS_STREAMING));
        InputStream in = new SequenceInputStream(v.elements());
        // The page's <meta viewport> sits in the first few hundred bytes: rewrite it to apply ZOOM.
        byte[] head = new byte[8192];
        int n = 0, r;
        while (n < head.length && (r = in.read(head, n, head.length - n)) > 0) n += r;
        String h = new String(head, 0, n, "ISO-8859-1");
        h = h.replace("width=device-width,initial-scale=1,viewport-fit=cover",
                "width=device-width,initial-scale=" + ZOOM + ",minimum-scale=" + ZOOM + ",viewport-fit=cover");
        return new SequenceInputStream(new ByteArrayInputStream(h.getBytes("ISO-8859-1")), in);
    }

    @Override protected void onActivityResult(int code, int res, Intent data) {
        if (code == 10 && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        } else super.onActivityResult(code, res, data);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] grants) {
        if (code == 1 && pendingPerm != null) {
            boolean ok = grants.length > 0;
            for (int g : grants) if (g != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) pendingPerm.grant(pendingPerm.getResources()); else pendingPerm.deny();
            pendingPerm = null;
        }
    }

    @Override public void onBackPressed() {
        if (customView != null) web.getWebChromeClient().onHideCustomView();
        else if (web.canGoBack()) web.goBack();
        else moveTaskToBack(true);
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { root.removeAllViews(); web.destroy(); super.onDestroy(); }
}
