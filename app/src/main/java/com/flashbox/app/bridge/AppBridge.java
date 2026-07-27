package com.flashbox.app.bridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.flashbox.app.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Main JS <-> native bridge exposed as `window.FlashBox`.
 *
 * - Persistent KV (SharedPreferences)
 * - Clipboard
 * - Open external URL / share
 * - File download to public dir
 * - System info
 * - Request storage permission / SAF
 */
public class AppBridge {
    private static final String TAG = "AppBridge";

    private final Activity activity;
    private final WebView webView;
    private final android.content.SharedPreferences prefs;

    public AppBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.prefs = activity.getSharedPreferences("flashbox", Context.MODE_PRIVATE);
    }

    // --- Persistent KV --------------------------------------------------------

    @android.webkit.JavascriptInterface
    public String kvGet(String key, String def) {
        return prefs.getString(key, def == null ? "" : def);
    }

    @android.webkit.JavascriptInterface
    public void kvSet(String key, String value) {
        prefs.edit().putString(key, value == null ? "" : value).apply();
    }

    @android.webkit.JavascriptInterface
    public String kvAll() {
        // Return a JSON-ish string (manual, so we don't add a dep)
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(esc(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof String) sb.append('"').append(esc((String) v)).append('"');
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(esc(String.valueOf(v))).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    @android.webkit.JavascriptInterface
    public void kvRemove(String key) { prefs.edit().remove(key).apply(); }

    // --- System info ----------------------------------------------------------

    @android.webkit.JavascriptInterface
    public String appVersion() {
        try {
            PackageInfo pi = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            return pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception e) { return "unknown"; }
    }

    @android.webkit.JavascriptInterface
    public String deviceInfo() {
        return Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE;
    }

    @android.webkit.JavascriptInterface
    public boolean hasAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return true;
    }

    // --- Permissions / SAF ---------------------------------------------------

    @android.webkit.JavascriptInterface
    public void requestStoragePermission() {
        activity.runOnUiThread(() -> {
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).requestStoragePermission();
            }
        });
    }

    // --- Clipboard ------------------------------------------------------------

    @android.webkit.JavascriptInterface
    public void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("FlashBox", text));
        }
    }

    @android.webkit.JavascriptInterface
    public String readFromClipboard() {
        ClipboardManager cm = (ClipboardManager)
                activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.getPrimaryClip() != null) {
            return cm.getPrimaryClip().getItemAt(0).coerceToText(activity).toString();
        }
        return "";
    }

    // --- Open external / share ------------------------------------------------

    @android.webkit.JavascriptInterface
    public void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Exception e) {
            toast("无法打开链接: " + e.getMessage());
        }
    }

    @android.webkit.JavascriptInterface
    public void shareText(String text, String subject) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            if (subject != null) i.putExtra(Intent.EXTRA_SUBJECT, subject);
            activity.startActivity(Intent.createChooser(i, "分享"));
        } catch (Exception e) { toast("分享失败: " + e.getMessage()); }
    }

    @android.webkit.JavascriptInterface
    public void shareFile(String path, String mime) {
        try {
            File f = new File(path);
            Uri u = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime == null ? "*/*" : mime);
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(i, "分享文件"));
        } catch (Exception e) { toast("分享文件失败: " + e.getMessage()); }
    }

    // --- File download to public dir -----------------------------------------

    @android.webkit.JavascriptInterface
    public void downloadUrlToDownloads(String url, String filename, String mime) {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c =
                        (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.connect();
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, filename);
                try (InputStream in = c.getInputStream();
                     FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                activity.runOnUiThread(() ->
                    toast("已下载: " + out.getAbsolutePath()));
            } catch (Exception e) {
                activity.runOnUiThread(() ->
                    toast("下载失败: " + e.getMessage()));
            }
        }, "fb-dl").start();
    }

    // --- App lifecycle helpers ------------------------------------------------

    @android.webkit.JavascriptInterface
    public void finishApp() {
        activity.runOnUiThread(activity::finish);
    }

    @android.webkit.JavascriptInterface
    public void reloadPage() {
        activity.runOnUiThread(() -> {
            if (webView != null) webView.reload();
        });
    }

    @android.webkit.JavascriptInterface
    public void clearWebCache() {
        activity.runOnUiThread(() -> {
            if (webView != null) {
                webView.clearCache(true);
                webView.clearHistory();
                webView.clearFormData();
            }
        });
    }

    @android.webkit.JavascriptInterface
    public void toast(String msg) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    // --- Util -----------------------------------------------------------------

    @android.webkit.JavascriptInterface
    public void log(String tag, String msg) {
        android.util.Log.d("FBJS/" + tag, msg);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
