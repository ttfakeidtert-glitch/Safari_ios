package com.safari.browser;

import android.webkit.WebView;

public class BrowserTab {
    public int id;
    public String title;
    public String url;
    public WebView webView;
    public boolean isActive;

    public BrowserTab(int id) {
        this.id = id;
        this.title = "Новая вкладка";
        this.url = "";
        this.isActive = false;
    }

    public String getDisplayTitle() {
        if (title == null || title.isEmpty()) return "Новая вкладка";
        if (title.length() > 30) return title.substring(0, 27) + "...";
        return title;
    }

    public String getDomain() {
        if (url == null || url.isEmpty()) return "";
        try {
            String host = android.net.Uri.parse(url).getHost();
            if (host == null) return "";
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception e) {
            return url;
        }
    }
}
