package com.safari.browser;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private FrameLayout webContainer;
    private EditText urlInput;
    private ImageButton btnBack, btnForward, btnReload, btnShare, btnTabs, btnBookmarks;
    private LinearLayout topBar, bottomBar, tabsPanel;
    private FrameLayout tabsGrid;
    private TextView tabsCountText, closeAllText;

    private final List<BrowserTab> tabs = new ArrayList<>();
    private int currentTabId = 1;
    private int nextTabId = 2;

    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    private boolean isBlocked = false;
    private boolean isFullscreen = false;

    private static final String HOME_URL = "https://www.google.com";
    private static final String SEARCH_URL = "https://www.google.com/search?q=";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupWebSettings();
        setupListeners();

        createNewTab(true);
        loadHome();
    }

    private void bindViews() {
        webContainer = findViewById(R.id.webContainer);
        urlInput = findViewById(R.id.urlInput);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);
        btnShare = findViewById(R.id.btnShare);
        btnTabs = findViewById(R.id.btnTabs);
        btnBookmarks = findViewById(R.id.btnBookmarks);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        tabsPanel = findViewById(R.id.tabsPanel);
        tabsGrid = findViewById(R.id.tabsGrid);
        tabsCountText = findViewById(R.id.tabsCountText);
        closeAllText = findViewById(R.id.closeAllText);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebSettings() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(null, true);
    }

    private void setupListeners() {
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(urlInput.getText().toString());
                hideKeyboard();
                urlInput.clearFocus();
                return true;
            }
            return false;
        });

        urlInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) urlInput.selectAll();
        });

        btnBack.setOnClickListener(v -> goBack());
        btnForward.setOnClickListener(v -> goForward());
        btnReload.setOnClickListener(v -> reload());
        btnShare.setOnClickListener(v -> share());
        btnTabs.setOnClickListener(v -> toggleTabsPanel());
        btnBookmarks.setOnClickListener(v ->
                Toast.makeText(this, "Закладки", Toast.LENGTH_SHORT).show());
    }

    private BrowserTab createNewTab(boolean makeActive) {
        BrowserTab tab = new BrowserTab(nextTabId++);
        tab.webView = createWebView();
        tabs.add(tab);
        if (makeActive) {
            switchTab(tab.id);
        }
        return tab;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setUserAgentString(
                s.getUserAgentString().replace("; wv", "")
        );
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false;
                }
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                isBlocked = false;
                BrowserTab tab = getTabByWebView(view);
                if (tab != null && tab.id == currentTabId) {
                    urlInput.setText(tab.getDomain());
                    updateNavButtons();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                BrowserTab tab = getTabByWebView(view);
                if (tab != null) {
                    tab.url = url;
                    String title = view.getTitle();
                    if (title != null && !title.isEmpty()) tab.title = title;
                }
                if (tab != null && tab.id == currentTabId) {
                    urlInput.setText(tab.getDomain());
                    updateNavButtons();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                BrowserTab tab = getTabByWebView(view);
                if (tab != null) tab.title = title;
            }
        });

        return webView;
    }

    private BrowserTab getTabByWebView(WebView view) {
        for (BrowserTab t : tabs) {
            if (t.webView == view) return t;
        }
        return null;
    }

    private BrowserTab getCurrentTab() {
        for (BrowserTab t : tabs) {
            if (t.id == currentTabId) return t;
        }
        return null;
    }

    private void switchTab(int id) {
        currentTabId = id;
        webContainer.removeAllViews();
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            if (tab.webView.getParent() != null) {
                ((FrameLayout) tab.webView.getParent()).removeView(tab.webView);
            }
            webContainer.addView(tab.webView);
            urlInput.setText(tab.getDomain());
            tab.webView.requestFocus();
        }
        updateNavButtons();
    }

    private void closeTab(int id) {
        if (tabs.size() <= 1) {
            BrowserTab tab = getCurrentTab();
            if (tab != null) {
                tab.webView.loadUrl("about:blank");
                tab.title = "Новая вкладка";
                tab.url = "";
            }
            loadHome();
            return;
        }
        BrowserTab toRemove = null;
        for (BrowserTab t : tabs) {
            if (t.id == id) { toRemove = t; break; }
        }
        if (toRemove != null) {
            webContainer.removeView(toRemove.webView);
            toRemove.webView.destroy();
            tabs.remove(toRemove);
            if (currentTabId == id) {
                switchTab(tabs.get(0).id);
            }
        }
        renderTabsPanel();
    }

    private void navigate(String input) {
        if (input == null) return;
        input = input.trim();
        if (input.isEmpty()) return;

        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.matches("^[\\w-]+(\\.[\\w-]+)+(/.*)?$") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = SEARCH_URL + Uri.encode(input);
        }

        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.loadUrl(url);
            tab.url = url;
        }

        history.add(url);
        historyIndex = history.size() - 1;
        updateNavButtons();
    }

    private void loadHome() {
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.loadUrl(HOME_URL);
        }
    }

    private void goBack() {
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoBack()) {
            tab.webView.goBack();
        }
    }

    private void goForward() {
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null && tab.webView.canGoForward()) {
            tab.webView.goForward();
        }
    }

    private void reload() {
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.reload();
        }
    }

    private void share() {
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.url != null && !tab.url.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, tab.url);
            startActivity(Intent.createChooser(i, "Поделиться"));
        }
    }

    private void updateNavButtons() {
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            btnBack.setEnabled(tab.webView.canGoBack());
            btnForward.setEnabled(tab.webView.canGoForward());
            btnBack.setAlpha(tab.webView.canGoBack() ? 1f : 0.3f);
            btnForward.setAlpha(tab.webView.canGoForward() ? 1f : 0.3f);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }

    private void toggleTabsPanel() {
        if (tabsPanel.getVisibility() == View.VISIBLE) {
            tabsPanel.setVisibility(View.GONE);
        } else {
            renderTabsPanel();
            tabsPanel.setVisibility(View.VISIBLE);
        }
    }

    private void renderTabsPanel() {
        int count = tabs.size();
        String word;
        if (count == 1) word = "вкладка";
        else if (count < 5) word = "вкладки";
        else word = "вкладок";
        if (tabsCountText != null) {
            tabsCountText.setText(count + " " + word);
        }
        if (closeAllText != null) {
            closeAllText.setText("Закрыть все (" + count + ")");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (tabsPanel.getVisibility() == View.VISIBLE) {
                tabsPanel.setVisibility(View.GONE);
                return true;
            }
            BrowserTab tab = getCurrentTab();
            if (tab != null && tab.webView != null && tab.webView.canGoBack()) {
                tab.webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        BrowserTab tab = getCurrentTab();
        if (tab != null && tab.webView != null) {
            tab.webView.restoreState(savedInstanceState);
        }
    }
}
