package com.flashbox.app.bridge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bridge for accessing local files (SWF / folders) through the
 * Storage Access Framework. Exposed as `window.FlashBoxFile`.
 *
 * Capabilities:
 *   - List contents of a SAF tree (recursive scan of swf files)
 *   - Read raw bytes of a file (returned as base64 for JS to blob: URL)
 *   - Persist granted tree URIs so the user only picks once
 *   - Add a single SWF file
 *   - Add a folder (whole tree scan)
 */
public class LocalFileBridge {

    private final Activity activity;
    private final WebView webView;
    private final android.content.SharedPreferences prefs;
    /** SAF tree root URIs the user has granted (persist between sessions). */
    private final java.util.List<String> roots;

    public LocalFileBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        this.prefs = activity.getSharedPreferences("flashbox_files", Context.MODE_PRIVATE);
        // Load previously granted roots
        this.roots = new ArrayList<>();
        for (String u : prefs.getString("roots", "").split("\\|")) {
            if (!u.isEmpty()) roots.add(u);
        }
    }

    // ---- Tree management ----------------------------------------------------

    @android.webkit.JavascriptInterface
    public String listRoots() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < roots.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(roots.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    @android.webkit.JavascriptInterface
    public void removeRoot(String uri) {
        try {
            activity.getContentResolver().releasePersistableUriPermission(
                    Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {}
        roots.remove(uri);
        persistRoots();
        emit("roots-changed", "[]");
    }

    /**
     * List SWF files under a SAF tree URI, recursively. Returns a JSON
     * string [{path,name,size,mtime},...].
     */
    @android.webkit.JavascriptInterface
    public String listSwfUnderTree(String treeUri) {
        List<SwfEntry> out = new ArrayList<>();
        try {
            Uri uri = Uri.parse(treeUri);
            DocumentFile tree = DocumentFile.fromTreeUri(activity, uri);
            if (tree == null) return "[]";
            // Try persistable
            try {
                activity.getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception ignored) {}
            walk(tree, out, "");
        } catch (Exception e) {
            return "[]";
        }
        // Build JSON
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append(',');
            SwfEntry s = out.get(i);
            sb.append("{\"path\":\"").append(esc(s.path)).append("\",")
              .append("\"name\":\"").append(esc(s.name)).append("\",")
              .append("\"size\":").append(s.size).append(',')
              .append("\"mtime\":").append(s.mtime).append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private void walk(DocumentFile dir, List<SwfEntry> out, String path) {
        if (dir == null || !dir.isDirectory()) return;
        for (DocumentFile child : dir.listFiles()) {
            String childPath = path.isEmpty() ? child.getName() : path + "/" + child.getName();
            if (child.isDirectory()) {
                walk(child, out, childPath);
            } else if (child.isFile()) {
                String n = child.getName();
                if (n != null && n.toLowerCase().endsWith(".swf")) {
                    SwfEntry s = new SwfEntry();
                    s.path = childPath;
                    s.name = n;
                    s.size = child.length();
                    s.mtime = child.lastModified();
                    out.add(s);
                }
            }
        }
    }

    private static class SwfEntry {
        String path, name;
        long size, mtime;
    }

    // ---- File read ----------------------------------------------------------

    /**
     * Read file content as base64. JS will turn it into a Blob URL.
     * For a SAF tree file, `path` is the relative path inside the root;
     * for a content:// URI, `path` IS the URI.
     */
    @android.webkit.JavascriptInterface
    public String readFileAsBase64(String path) {
        try {
            Uri uri;
            if (path.startsWith("content://") || path.startsWith("file://")) {
                uri = Uri.parse(path);
            } else {
                // Treat as relative to first root
                if (roots.isEmpty()) return "";
                String root = roots.get(0);
                DocumentFile tree = DocumentFile.fromTreeUri(activity, Uri.parse(root));
                if (tree == null) return "";
                DocumentFile f = findByPath(tree, path);
                if (f == null) return "";
                uri = f.getUri();
            }
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) return "";
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                return android.util.Base64.encodeToString(bos.toByteArray(),
                        android.util.Base64.NO_WRAP);
            }
        } catch (Exception e) {
            return "";
        }
    }

    private DocumentFile findByPath(DocumentFile dir, String relPath) {
        String[] parts = relPath.split("/");
        DocumentFile cur = dir;
        for (String p : parts) {
            if (cur == null) return null;
            cur = cur.findFile(p);
        }
        return cur;
    }

    /**
     * Read file as ArrayBuffer slice. Returns hex-encoded bytes.
     * Used for SWF header sniffing when user wants to "open as".
     */
    @android.webkit.JavascriptInterface
    public String readFileHeader(String path, int maxBytes) {
        try {
            Uri uri;
            if (path.startsWith("content://") || path.startsWith("file://")) {
                uri = Uri.parse(path);
            } else {
                if (roots.isEmpty()) return "";
                DocumentFile tree = DocumentFile.fromTreeUri(activity, Uri.parse(roots.get(0)));
                DocumentFile f = findByPath(tree, path);
                if (f == null) return "";
                uri = f.getUri();
            }
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                if (in == null) return "";
                byte[] buf = new byte[Math.min(maxBytes, 1024 * 1024)];
                int total = 0, n;
                while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) {
                    total += n;
                }
                StringBuilder hex = new StringBuilder(total * 2);
                for (int i = 0; i < total; i++) {
                    hex.append(String.format("%02x", buf[i] & 0xff));
                }
                return hex.toString();
            }
        } catch (Exception e) { return ""; }
    }

    // ---- Tree add helpers ---------------------------------------------------

    @android.webkit.JavascriptInterface
    public void addRoot(String treeUri) {
        if (treeUri == null || treeUri.isEmpty()) return;
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    Uri.parse(treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            toast("授权失败: " + e.getMessage());
            return;
        }
        if (!roots.contains(treeUri)) roots.add(treeUri);
        persistRoots();
        emit("roots-changed", listRoots());
    }

    private void persistRoots() {
        prefs.edit().putString("roots", String.join("|", roots)).apply();
    }

    // ---- Emit helpers -------------------------------------------------------

    private void emit(String name, String data) {
        if (webView == null) return;
        final String js = "window.dispatchEvent(new CustomEvent('" + name + "',{detail:" + data + "}));";
        activity.runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private void toast(String msg) {
        activity.runOnUiThread(() ->
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
