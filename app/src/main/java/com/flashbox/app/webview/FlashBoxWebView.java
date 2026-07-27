package com.flashbox.app.webview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * WebView subclass that:
 *  - Disables nested scrolling (single-page app, the JS handles its own scroll).
 *  - Forwards focus / blur for keyboard control.
 *  - Suppresses text-selection UI unless the user explicitly long-presses.
 */
public class FlashBoxWebView extends WebView {

    private boolean verticalScrollEnabled = true;
    private boolean horizontalScrollEnabled = false;

    public FlashBoxWebView(Context context) { super(context); init(); }
    public FlashBoxWebView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public FlashBoxWebView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setLongClickable(true);
        // Allow JS / DOM storage (configured again in WebViewConfigurator)
        getSettings().setJavaScriptEnabled(true);
    }

    public void setVerticalScrollEnabled(boolean enabled) {
        this.verticalScrollEnabled = enabled;
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        // Prevent over-scroll bouncing for game-canvas pages
        if (!verticalScrollEnabled) {
            super.scrollTo(0, 0);
        } else {
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Disable parent intercept when the user drags a virtual gamepad
        getParent().requestDisallowInterceptTouchEvent(true);
        return super.onTouchEvent(ev);
    }
}
