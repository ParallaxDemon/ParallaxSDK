package android.MetaCore

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

object AdvancedPopupHelper {

    private const val TAG = "AdvancedPopupHelper"

    private const val AUTO_CLOSE_SECONDS = 12
    private const val MAX_WIDTH_DP = 410
    private const val MAX_HEIGHT_DP = 660

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentDialog: WeakReference<Dialog>? = null

    @Volatile
    private var currentWebView: WeakReference<WebView>? = null

    private val showing = AtomicBoolean(false)

    // ------------------------------------------------------------------------
    // PUBLIC
    // ------------------------------------------------------------------------

    @JvmStatic
    fun showAuto() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAutoInternal()
        } else {
            mainHandler.post {
                showAutoInternal()
            }
        }
    }

    @JvmStatic
    fun dismiss() {
        mainHandler.post {
            dismissInternal()
        }
    }

    @JvmStatic
    fun isShowing(): Boolean {
        return try {
            currentDialog?.get()?.isShowing == true
        } catch (_: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------------------
    // ACTIVITY
    // ------------------------------------------------------------------------

    private fun showAutoInternal() {
        try {
            val activity = getTopActivity() ?: return

            if (activity.isFinishing) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (activity.isDestroyed) return
            }

            showPopup(activity)

        } catch (_: Throwable) {
            // Popup must never crash the host.
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getTopActivity(): Activity? {
        return try {

            val activityThreadClass =
                Class.forName("android.app.ActivityThread")

            val currentActivityThread =
                activityThreadClass
                    .getDeclaredMethod("currentActivityThread")
                    .apply {
                        isAccessible = true
                    }
                    .invoke(null)
                    ?: return null

            val activitiesField =
                activityThreadClass
                    .getDeclaredField("mActivities")
                    .apply {
                        isAccessible = true
                    }

            val activities =
                activitiesField.get(currentActivityThread) as? Map<*, *>
                    ?: return null

            var fallbackActivity: Activity? = null

            for (recordObject in activities.values) {

                val record = recordObject ?: continue

                val activity = getActivityFromRecord(record) ?: continue

                if (fallbackActivity == null) {
                    fallbackActivity = activity
                }

                val paused = readBooleanField(record, "paused")
                    ?: readBooleanField(record, "mPaused")
                    ?: false

                val stopped = readBooleanField(record, "stopped")
                    ?: readBooleanField(record, "mStopped")
                    ?: false

                if (!paused && !stopped) {
                    return activity
                }
            }

            fallbackActivity

        } catch (_: Throwable) {
            null
        }
    }

    private fun getActivityFromRecord(record: Any): Activity? {
        return try {

            val names = arrayOf(
                "activity",
                "mActivity"
            )

            for (name in names) {
                try {
                    val field = findField(record.javaClass, name)
                    if (field != null) {
                        field.isAccessible = true

                        val value = field.get(record)

                        if (value is Activity) {
                            return value
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            null

        } catch (_: Throwable) {
            null
        }
    }

    private fun readBooleanField(
        instance: Any,
        name: String
    ): Boolean? {

        return try {

            val field =
                findField(instance.javaClass, name)
                    ?: return null

            field.isAccessible = true

            when (val value = field.get(instance)) {
                is Boolean -> value
                else -> null
            }

        } catch (_: Throwable) {
            null
        }
    }

    private fun findField(
        clazz: Class<*>,
        name: String
    ): Field? {

        var current: Class<*>? = clazz

        while (current != null) {

            try {
                return current.getDeclaredField(name)
            } catch (_: Throwable) {
            }

            current = current.superclass
        }

        return null
    }

    // ------------------------------------------------------------------------
    // DIALOG
    // ------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun showPopup(activity: Activity) {

        mainHandler.post {

            if (!showing.compareAndSet(false, true)) {
                return@post
            }

            try {

                dismissInternal(resetFlag = false)

                val dialog =
                    Dialog(
                        activity,
                        android.R.style.Theme_Translucent_NoTitleBar
                    )

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)

                val webView = WebView(activity)

                currentDialog = WeakReference(dialog)
                currentWebView = WeakReference(webView)

                configureWebView(
                    webView = webView,
                    dialog = dialog,
                    activity = activity
                )

                webView.loadDataWithBaseURL(
                    "file:///android_res/drawable/",
                    HTML,
                    "text/html",
                    "UTF-8",
                    null
                )

                dialog.setContentView(webView)

                dialog.setOnDismissListener {

                    try {
                        cleanupWebView(webView)
                    } catch (_: Throwable) {
                    }

                    currentWebView = null
                    currentDialog = null

                    showing.set(false)
                }

                dialog.show()

                configureWindow(
                    activity = activity,
                    dialog = dialog
                )

            } catch (_: Throwable) {

                showing.set(false)

                try {
                    dismissInternal()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun configureWindow(
        activity: Activity,
        dialog: Dialog
    ) {

        val window =
            dialog.window ?: return

        try {

            val metrics =
                activity.resources.displayMetrics

            val horizontalMargin =
                dp(activity, 18)

            val verticalMargin =
                dp(activity, 32)

            val availableWidth =
                (metrics.widthPixels - horizontalMargin)
                    .coerceAtLeast(dp(activity, 280))

            val availableHeight =
                (metrics.heightPixels - verticalMargin)
                    .coerceAtLeast(dp(activity, 400))

            val targetWidth =
                min(
                    dp(activity, MAX_WIDTH_DP),
                    availableWidth
                )

            val targetHeight =
                min(
                    dp(activity, MAX_HEIGHT_DP),
                    availableHeight
                )

            window.setBackgroundDrawable(
                ColorDrawable(Color.TRANSPARENT)
            )

            window.setLayout(
                targetWidth,
                targetHeight
            )

            window.setGravity(Gravity.CENTER)

            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
            )

            window.addFlags(
                WindowManager.LayoutParams.FLAG_SECURE
            )

            window.setDimAmount(0.82f)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor =
                    Color.TRANSPARENT

                window.navigationBarColor =
                    Color.BLACK
            }

        } catch (_: Throwable) {
        }
    }

    // ------------------------------------------------------------------------
    // WEBVIEW
    // ------------------------------------------------------------------------

    @SuppressLint(
        "SetJavaScriptEnabled",
        "JavascriptInterface"
    )
    private fun configureWebView(
        webView: WebView,
        dialog: Dialog,
        activity: Activity
    ) {

        webView.setBackgroundColor(
            Color.TRANSPARENT
        )

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false

        webView.overScrollMode =
            View.OVER_SCROLL_NEVER

        webView.isLongClickable = false

        webView.setOnLongClickListener {
            true
        }

        try {
            webView.setLayerType(
                View.LAYER_TYPE_HARDWARE,
                null
            )
        } catch (_: Throwable) {
        }

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = false

            databaseEnabled = false

            allowContentAccess = false

            allowFileAccess = true

            blockNetworkLoads = true

            loadsImagesAutomatically = true

            cacheMode =
                WebSettings.LOAD_NO_CACHE

            defaultTextEncodingName =
                "UTF-8"

            javaScriptCanOpenWindowsAutomatically =
                false

            setSupportMultipleWindows(false)

            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP
            ) {

                mixedContentMode =
                    WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }

            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                safeBrowsingEnabled = true
            }
        }

        webView.webViewClient =
            object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?
                ): Boolean {
                    return true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(
                        view,
                        request,
                        error
                    )
                }
            }

        val deviceInfo =
            buildDeviceInfo(activity)

        val bridge =
            object {

                @JavascriptInterface
                fun close() {

                    mainHandler.post {

                        try {

                            if (dialog.isShowing) {
                                dialog.dismiss()
                            }

                        } catch (_: Throwable) {
                        }
                    }
                }

                @JavascriptInterface
                fun getDeviceInfo(): String {
                    return deviceInfo
                }

                @JavascriptInterface
                fun getAutoCloseSeconds(): Int {
                    return AUTO_CLOSE_SECONDS
                }
            }

        webView.addJavascriptInterface(
            bridge,
            "Android"
        )
    }

    private fun cleanupWebView(
        webView: WebView
    ) {

        try {
            webView.removeJavascriptInterface(
                "Android"
            )
        } catch (_: Throwable) {
        }

        try {
            webView.stopLoading()
        } catch (_: Throwable) {
        }

        try {
            webView.webChromeClient = null
        } catch (_: Throwable) {
        }

        try {
            webView.webViewClient =
                WebViewClient()
        } catch (_: Throwable) {
        }

        try {
            webView.clearHistory()
        } catch (_: Throwable) {
        }

        try {
            webView.clearCache(true)
        } catch (_: Throwable) {
        }

        try {
            webView.loadUrl(
                "about:blank"
            )
        } catch (_: Throwable) {
        }

        try {
            webView.removeAllViews()
        } catch (_: Throwable) {
        }

        try {
            webView.destroy()
        } catch (_: Throwable) {
        }
    }

    private fun dismissInternal(
        resetFlag: Boolean = true
    ) {

        try {

            currentDialog
                ?.get()
                ?.let {

                    if (it.isShowing) {
                        it.dismiss()
                    }
                }

        } catch (_: Throwable) {
        }

        currentDialog = null
        currentWebView = null

        if (resetFlag) {
            showing.set(false)
        }
    }

    // ------------------------------------------------------------------------
    // DEVICE INFO
    // ------------------------------------------------------------------------

    private fun buildDeviceInfo(
        activity: Activity
    ): String {

        return try {

            JSONObject().apply {

                put(
                    "manufacturer",
                    safeText(Build.MANUFACTURER)
                )

                put(
                    "model",
                    safeText(Build.MODEL)
                )

                put(
                    "device",
                    buildString {

                        append(
                            safeText(
                                Build.MANUFACTURER
                            )
                        )

                        if (!Build.MODEL.isNullOrBlank()) {

                            append(" ")

                            append(
                                safeText(
                                    Build.MODEL
                                )
                            )
                        }
                    }.trim()
                )

                put(
                    "android",
                    Build.VERSION.RELEASE
                        ?: "Unknown"
                )

                put(
                    "api",
                    Build.VERSION.SDK_INT
                )

                put(
                    "abi",
                    Build.SUPPORTED_ABIS
                        ?.firstOrNull()
                        ?: "Unknown"
                )

                put(
                    "brand",
                    safeText(Build.BRAND)
                )

                put(
                    "product",
                    safeText(Build.PRODUCT)
                )

                put(
                    "density",
                    activity.resources
                        .displayMetrics
                        .density
                )

            }.toString()

        } catch (_: Throwable) {

            """{
                "device":"Unknown",
                "android":"Unknown",
                "api":0,
                "abi":"Unknown"
            }""".trimIndent()
        }
    }

    private fun safeText(
        value: String?
    ): String {

        return value
            ?.trim()
            ?.takeIf {
                it.isNotEmpty()
            }
            ?: "Unknown"
    }

    private fun dp(
        activity: Activity,
        value: Int
    ): Int {

        return (
            value *
                activity.resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    // ------------------------------------------------------------------------
    // HTML
    // ------------------------------------------------------------------------

    private const val HTML = """
<!DOCTYPE html>

<html lang="en">

<head>

<meta charset="UTF-8">

<meta
    name="viewport"
    content="
        width=device-width,
        initial-scale=1,
        maximum-scale=1,
        user-scalable=no
    "
>

<meta
    name="color-scheme"
    content="dark"
>

<style>

:root {

    --bg: #040312;

    --surface: #090b1f;

    --surface2: #101432;

    --gold: #00f0ff;

    --gold2: #a855f7;

    --danger: #a855f7;

    --danger2: #3b82f6;

    --text: #f5f7ff;

    --muted: #9aa5c4;

    --border: rgba(0,240,255,.14);

    --goldBorder: rgba(0,240,255,.38);

    --dangerBorder: rgba(168,85,247,.34);
}

* {
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
}

html,
body {

    width: 100%;

    height: 100%;

    margin: 0;

    padding: 0;

    overflow: hidden;
}

body {

    display: flex;

    align-items: center;

    justify-content: center;

    background: transparent;

    color: var(--text);

    font-family:
        -apple-system,
        BlinkMacSystemFont,
        "Segoe UI",
        Roboto,
        Arial,
        sans-serif;

    -webkit-font-smoothing: antialiased;
}

.shell {

    position: relative;

    width: 100%;

    height: 100%;

    display: flex;

    align-items: center;

    justify-content: center;

    padding: 8px;
}

.card {

    position: relative;

    width: 100%;

    max-width: 382px;

    max-height: 100%;

    overflow: hidden;

    border:
        1px solid
        var(--goldBorder);

    border-radius: 30px;

    background:
        radial-gradient(
            circle at 50% -10%,
            rgba(168,85,247,.26),
            transparent 36%
        ),
        linear-gradient(
            150deg,
            rgba(13,15,38,.995),
            rgba(4,3,18,.998) 68%
        );

    box-shadow:
        0 32px 90px rgba(0,0,0,.82),
        0 0 0 1px rgba(255,255,255,.015),
        inset 0 1px rgba(255,255,255,.045);

    animation:
        cardEnter
        .62s
        cubic-bezier(.18,.92,.25,1)
        both;
}

.card::before {

    content: "";

    position: absolute;

    z-index: 0;

    width: 260px;

    height: 260px;

    left: 50%;

    top: -200px;

    transform:
        translateX(-50%);

    border-radius: 50%;

    background:
        rgba(0,240,255,.32);

    filter:
        blur(80px);

    pointer-events: none;
}

.card::after {

    content: "";

    position: absolute;

    z-index: 0;

    width: 180px;

    height: 180px;

    right: -100px;

    bottom: -100px;

    border-radius: 50%;

    background:
        rgba(168,85,247,.22);

    filter:
        blur(54px);

    pointer-events: none;
}

.inner {

    position: relative;

    z-index: 2;

    padding:
        20px
        19px
        18px;
}

/* --------------------------------------------------------- */
/* TOP BAR                                                   */
/* --------------------------------------------------------- */

.top {

    display: flex;

    align-items: center;

    justify-content: space-between;

    gap: 12px;
}

.identity {

    display: flex;

    align-items: center;

    gap: 12px;

    min-width: 0;
}

.logo {

    position: relative;

    width: 54px;

    height: 54px;

    min-width: 54px;

    display: grid;

    place-items: center;

    overflow: hidden;

    border:
        1px solid
        rgba(0,240,255,.48);

    border-radius: 17px;

    background:
        linear-gradient(
            145deg,
            #191d25,
            #090b10
        );

    box-shadow:
        0 10px 24px rgba(0,0,0,.45),
        inset 0 1px rgba(255,255,255,.05);
}

.logo::before {

    content: "";

    position: absolute;

    inset: -30%;

    background:
        conic-gradient(
            transparent,
            rgba(0,240,255,.85),
            transparent 28%
        );

    animation:
        rotateGlow
        5s
        linear
        infinite;
}

.logoInner {

    position: relative;

    z-index: 2;

    width: 46px;

    height: 46px;

    display: grid;

    place-items: center;

    border-radius: 14px;

    background:
        linear-gradient(
            145deg,
            #151922,
            #080a0f
        );
}

.logoMark {

    font-size: 22px;

    line-height: 1;

    font-weight: 950;

    color: var(--gold);

    letter-spacing: -1px;

    text-shadow:
        0 0 22px
        rgba(231,189,99,.34);
}

.copy {
    min-width: 0;
}

.eyebrow {

    color: var(--gold);

    font-size: 8px;

    font-weight: 900;

    letter-spacing: 2.2px;

    text-transform: uppercase;
}

.brand {

    margin-top: 4px;

    overflow: hidden;

    color: #f3f5f8;

    font-size: 17px;

    font-weight: 850;

    letter-spacing: .25px;

    text-overflow: ellipsis;

    white-space: nowrap;
}

.secure {

    display: flex;

    align-items: center;

    gap: 6px;

    padding:
        6px
        9px;

    border:
        1px solid
        rgba(102,227,158,.16);

    border-radius: 999px;

    background:
        rgba(102,227,158,.06);

    color: #91e9b8;

    font-size: 7px;

    font-weight: 850;

    letter-spacing: 1.1px;

    white-space: nowrap;
}

.secureDot {

    width: 5px;

    height: 5px;

    border-radius: 50%;

    background: #66e39e;

    box-shadow:
        0 0 9px
        #66e39e;
}

/* --------------------------------------------------------- */
/* HERO                                                      */
/* --------------------------------------------------------- */

.hero {

    position: relative;

    margin-top: 18px;

    padding:
        20px
        14px
        18px;

    overflow: hidden;

    text-align: center;

    border:
        1px solid
        var(--dangerBorder);

    border-radius: 23px;

    background:
        radial-gradient(
            circle at 50% 0,
            rgba(255,88,113,.11),
            transparent 55%
        ),
        rgba(255,255,255,.018);
}

.hero::after {

    content: "";

    position: absolute;

    width: 140px;

    height: 140px;

    left: 50%;

    top: 10px;

    transform:
        translateX(-50%);

    border-radius: 50%;

    background:
        rgba(255,88,113,.06);

    filter:
        blur(35px);

    pointer-events: none;
}

.lockOuter {

    position: relative;

    z-index: 2;

    width: 76px;

    height: 76px;

    margin:
        0
        auto
        15px;

    display: grid;

    place-items: center;

    border-radius: 50%;

    background:
        radial-gradient(
            circle,
            rgba(255,88,113,.17),
            rgba(255,88,113,.025)
        );

    box-shadow:
        0 0 0 8px rgba(255,88,113,.025),
        0 0 38px rgba(255,88,113,.09);

    animation:
        pulse
        2.3s
        ease-in-out
        infinite;
}

.lockInner {

    width: 56px;

    height: 56px;

    display: grid;

    place-items: center;

    border:
        1px solid
        rgba(255,88,113,.24);

    border-radius: 50%;

    background:
        linear-gradient(
            145deg,
            rgba(255,88,113,.13),
            rgba(255,255,255,.018)
        );
}

.lockInner svg {

    width: 27px;

    height: 27px;

    fill: none;

    stroke: var(--danger);

    stroke-width: 1.75;

    stroke-linecap: round;

    stroke-linejoin: round;

    filter:
        drop-shadow(
            0 0 8px
            rgba(255,88,113,.45)
        );
}

.heroTitle {

    position: relative;

    z-index: 2;

    margin: 0;

    color: #ffffff;

    font-size: 23px;

    font-weight: 900;

    letter-spacing: -.2px;
}

.heroText {

    position: relative;

    z-index: 2;

    max-width: 285px;

    margin:
        8px
        auto
        0;

    color: var(--muted);

    font-size: 11px;

    line-height: 1.62;
}

.status {

    position: relative;

    z-index: 2;

    display: inline-flex;

    align-items: center;

    gap: 7px;

    margin-top: 14px;

    padding:
        7px
        12px;

    border:
        1px solid
        rgba(255,88,113,.24);

    border-radius: 999px;

    background:
        rgba(255,88,113,.075);

    color: #ffc1ca;

    font-size: 8px;

    font-weight: 900;

    letter-spacing: 1.45px;

    text-transform: uppercase;
}

.statusDot {

    width: 6px;

    height: 6px;

    border-radius: 50%;

    background:
        var(--danger);

    box-shadow:
        0 0 10px
        var(--danger);

    animation:
        statusPulse
        1.3s
        ease-in-out
        infinite;
}

/* --------------------------------------------------------- */
/* INFO GRID                                                 */
/* --------------------------------------------------------- */

.sectionTitle {

    margin:
        16px
        1px
        8px;

    color: #697486;

    font-size: 7px;

    font-weight: 900;

    letter-spacing: 2px;

    text-transform: uppercase;
}

.grid {

    display: grid;

    grid-template-columns:
        1fr
        1fr;

    gap: 8px;
}

.info {

    position: relative;

    min-width: 0;

    padding:
        11px
        11px
        10px;

    overflow: hidden;

    border:
        1px solid
        var(--border);

    border-radius: 15px;

    background:
        linear-gradient(
            145deg,
            rgba(255,255,255,.042),
            rgba(255,255,255,.018)
        );
}

.info::before {

    content: "";

    position: absolute;

    width: 40px;

    height: 40px;

    right: -18px;

    top: -18px;

    border-radius: 50%;

    background:
        rgba(231,189,99,.07);
}

.info label {

    position: relative;

    z-index: 1;

    display: block;

    margin-bottom: 5px;

    color: #667184;

    font-size: 7px;

    font-weight: 850;

    letter-spacing: 1.15px;

    text-transform: uppercase;
}

.info strong {

    position: relative;

    z-index: 1;

    display: block;

    overflow: hidden;

    color: #e9edf2;

    font-size: 10px;

    font-weight: 750;

    letter-spacing: .1px;

    text-overflow: ellipsis;

    white-space: nowrap;
}

/* --------------------------------------------------------- */
/* NOTICE                                                    */
/* --------------------------------------------------------- */

.notice {

    display: flex;

    align-items: flex-start;

    gap: 11px;

    margin-top: 11px;

    padding:
        12px;

    border:
        1px solid
        rgba(231,189,99,.12);

    border-radius: 15px;

    background:
        linear-gradient(
            135deg,
            rgba(231,189,99,.085),
            rgba(231,189,99,.025)
        );
}

.noticeIcon {

    width: 30px;

    height: 30px;

    min-width: 30px;

    display: grid;

    place-items: center;

    border-radius: 10px;

    background:
        rgba(231,189,99,.075);
}

.noticeIcon svg {

    width: 17px;

    height: 17px;

    fill: none;

    stroke: var(--gold);

    stroke-width: 1.8;

    stroke-linecap: round;

    stroke-linejoin: round;
}

.noticeText {

    color: #aab3c1;

    font-size: 9px;

    line-height: 1.55;
}

.noticeText b {
    color: #d8dee8;
}

/* --------------------------------------------------------- */
/* BUTTON                                                    */
/* --------------------------------------------------------- */

.actions {
    margin-top: 13px;
}

.closeButton {

    position: relative;

    width: 100%;

    height: 50px;

    overflow: hidden;

    border: 0;

    border-radius: 16px;

    outline: none;

    background:
        linear-gradient(
            105deg,
            #9333ea,
            #3b82f6 48%,
            #00f0ff
        );

    color: #ffffff;
    text-shadow: 0 1px 8px rgba(4,3,18,.65);

    font-size: 10px;

    font-weight: 950;

    letter-spacing: 1.45px;

    text-transform: uppercase;

    box-shadow:
        0 12px 32px
        rgba(168,85,247,.26);
}

.closeButton::before {

    content: "";

    position: absolute;

    width: 60px;

    height: 160%;

    top: -30%;

    left: -100px;

    transform:
        rotate(20deg);

    background:
        linear-gradient(
            90deg,
            transparent,
            rgba(255,255,255,.38),
            transparent
        );

    animation:
        shine
        3.3s
        ease-in-out
        infinite;
}

.closeButton:active {

    transform:
        scale(.985);
}

/* --------------------------------------------------------- */
/* TIMER                                                     */
/* --------------------------------------------------------- */

.timerRow {

    display: flex;

    align-items: center;

    justify-content: center;

    gap: 5px;

    margin-top: 11px;

    color: #667183;

    font-size: 8px;

    letter-spacing: .45px;
}

.timerValue {

    min-width: 16px;

    color: var(--gold);

    font-weight: 900;
}

.progress {

    position: relative;

    width: 100%;

    height: 3px;

    margin-top: 9px;

    overflow: hidden;

    border-radius: 99px;

    background:
        rgba(255,255,255,.065);
}

.progressFill {

    width: 100%;

    height: 100%;

    transform-origin: left;

    border-radius: inherit;

    background:
        linear-gradient(
            90deg,
            #a855f7,
            #3b82f6,
            #00f0ff
        );

    box-shadow:
        0 0 10px
        rgba(231,189,99,.26);
}

.footer {

    margin-top: 11px;

    color: #414a57;

    text-align: center;

    font-size: 7px;

    font-weight: 750;

    letter-spacing: 1.35px;

    text-transform: uppercase;
}

/* --------------------------------------------------------- */
/* ANIMATION                                                 */
/* --------------------------------------------------------- */

@keyframes cardEnter {

    0% {
        opacity: 0;

        transform:
            translateY(24px)
            scale(.94);
    }

    70% {
        opacity: 1;

        transform:
            translateY(-2px)
            scale(1.005);
    }

    100% {
        opacity: 1;

        transform:
            translateY(0)
            scale(1);
    }
}

@keyframes rotateGlow {

    from {
        transform:
            rotate(0deg);
    }

    to {
        transform:
            rotate(360deg);
    }
}

@keyframes pulse {

    0%,
    100% {
        transform:
            scale(1);
    }

    50% {
        transform:
            scale(1.045);

        box-shadow:
            0 0 0 12px rgba(255,88,113,.018),
            0 0 43px rgba(255,88,113,.11);
    }
}

@keyframes statusPulse {

    0%,
    100% {
        opacity: 1;
    }

    50% {
        opacity: .45;
    }
}

@keyframes shine {

    0%,
    60% {
        left: -100px;
    }

    100% {
        left: calc(100% + 80px);
    }
}

/* Small displays */

@media (max-height: 560px) {

    .inner {
        padding: 14px;
    }

    .hero {
        margin-top: 12px;
        padding: 14px 10px;
    }

    .lockOuter {
        width: 58px;
        height: 58px;
        margin-bottom: 9px;
    }

    .lockInner {
        width: 44px;
        height: 44px;
    }

    .heroTitle {
        font-size: 19px;
    }

    .heroText {
        font-size: 9px;
        line-height: 1.45;
    }

    .sectionTitle {
        margin-top: 11px;
    }

    .notice {
        padding: 9px;
    }

    .closeButton {
        height: 44px;
    }
}

</style>

</head>

<body>

<div class="shell">

<main
    class="card"
    role="dialog"
    aria-label="License expired"
>

<div class="inner">

    <header class="top">

        <div class="identity">

            <div class="logo">

                <div class="logoInner" style="padding:0;overflow:hidden;background:#040312;">

                    <img src="data:image/png;base64,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" alt="TeamDarkCore" style="width:100%;height:100%;object-fit:cover;border-radius:14px;display:block;" />

                </div>

            </div>

            <div class="copy">

                <div class="eyebrow">
                    TeamDarkCore / Core
                </div>

                <div class="brand">
                    Runtime Security
                </div>

            </div>

        </div>

        <div class="secure">

            <span class="secureDot"></span>

            SECURE

        </div>

    </header>


    <section class="hero">

        <div class="lockOuter">

            <div class="lockInner">

                <svg
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                >

                    <rect
                        x="5"
                        y="10"
                        width="14"
                        height="11"
                        rx="3"
                    />

                    <path
                        d="
                            M8 10V7
                            a4 4 0 0 1 8 0
                            v3
                        "
                    />

                    <path
                        d="M12 14v3"
                    />

                </svg>

            </div>

        </div>


        <h1 class="heroTitle">
            License expired
        </h1>


        <p class="heroText">

            This protected runtime session
            is no longer authorized.

            Renew your activation key
            before continuing.

        </p>


        <div class="status">

            <span class="statusDot"></span>

            Access revoked

        </div>

    </section>


    <div class="sectionTitle">
        Runtime environment
    </div>


    <section class="grid">

        <div class="info">

            <label>
                Device
            </label>

            <strong id="device">
                Detecting...
            </strong>

        </div>


        <div class="info">

            <label>
                Android
            </label>

            <strong id="android">
                Detecting...
            </strong>

        </div>


        <div class="info">

            <label>
                Runtime API
            </label>

            <strong id="api">
                Detecting...
            </strong>

        </div>


        <div class="info">

            <label>
                Architecture
            </label>

            <strong id="abi">
                Detecting...
            </strong>

        </div>

    </section>


    <section class="notice">

        <div class="noticeIcon">

            <svg viewBox="0 0 24 24">

                <path
                    d="
                        M12 3
                        l7 3
                        v5
                        c0 4.6-2.9 8-7 10
                        -4.1-2
                        -7-5.4
                        -7-10
                        V6
                        l7-3z
                    "
                />

                <path
                    d="M9 12l2 2 4-4"
                />

            </svg>

        </div>


        <div class="noticeText">

            <b>
                TeamDarkCore
            </b>

            supports Android API 24–36.

            Contact your authorized
            provider to renew access.

        </div>

    </section>


    <section class="actions">

        <button
            class="closeButton"
            type="button"
            onclick="closeSecurely()"
        >

            Close securely

        </button>


        <div class="timerRow">

            Closing automatically in

            <span
                id="timer"
                class="timerValue"
            >
                --
            </span>

            seconds

        </div>


        <div class="progress">

            <div
                id="progressFill"
                class="progressFill"
            ></div>

        </div>


        <div class="footer">
            TeamDarkCore protected runtime
        </div>

    </section>

</div>

</main>

</div>


<script>

(function () {

    "use strict";

    var closed = false;

    function safeAndroidCall(
        callback
    ) {

        try {

            if (
                typeof Android !== "undefined"
            ) {

                callback(Android);
            }

        } catch (ignore) {
        }
    }


    function setText(
        id,
        value
    ) {

        try {

            var element =
                document.getElementById(id);

            if (!element) {
                return;
            }

            if (
                value === null ||
                value === undefined ||
                value === ""
            ) {

                value = "Unknown";
            }

            element.textContent =
                String(value);

        } catch (ignore) {
        }
    }


    function loadDeviceInfo() {

        safeAndroidCall(
            function (bridge) {

                try {

                    var raw =
                        bridge.getDeviceInfo();

                    var info =
                        JSON.parse(raw);

                    setText(
                        "device",
                        info.device
                    );

                    setText(
                        "android",
                        "Android " +
                        String(
                            info.android || "Unknown"
                        )
                    );

                    setText(
                        "api",
                        "API " +
                        String(
                            info.api || "Unknown"
                        )
                    );

                    setText(
                        "abi",
                        info.abi
                    );

                } catch (ignore) {
                }
            }
        );
    }


    function closeSecurely() {

        if (closed) {
            return;
        }

        closed = true;

        var card =
            document.querySelector(
                ".card"
            );

        if (card) {

            card.style.transition =
                "opacity .18s ease, transform .18s ease";

            card.style.opacity =
                "0";

            card.style.transform =
                "scale(.96)";
        }

        setTimeout(
            function () {

                safeAndroidCall(
                    function (bridge) {
                        bridge.close();
                    }
                );

            },
            170
        );
    }


    window.closeSecurely =
        closeSecurely;


    var duration =
        12;

    safeAndroidCall(
        function (bridge) {

            try {

                var customDuration =
                    Number(
                        bridge
                            .getAutoCloseSeconds()
                    );

                if (
                    isFinite(customDuration) &&
                    customDuration > 0
                ) {

                    duration =
                        customDuration;
                }

            } catch (ignore) {
            }
        }
    );


    var remaining =
        duration;

    var timer =
        document.getElementById(
            "timer"
        );

    var progress =
        document.getElementById(
            "progressFill"
        );


    if (timer) {
        timer.textContent =
            String(remaining);
    }


    if (progress) {

        progress.style.transition =
            "transform " +
            String(duration) +
            "s linear";

        progress.style.transform =
            "scaleX(1)";

        requestAnimationFrame(
            function () {

                requestAnimationFrame(
                    function () {

                        progress.style.transform =
                            "scaleX(0)";
                    }
                );
            }
        );
    }


    loadDeviceInfo();


    var interval =
        setInterval(
            function () {

                remaining -= 1;

                if (remaining < 0) {
                    remaining = 0;
                }

                if (timer) {
                    timer.textContent =
                        String(remaining);
                }

                if (remaining <= 0) {

                    clearInterval(
                        interval
                    );

                    closeSecurely();
                }

            },
            1000
        );


})();

</script>

</body>

</html>
"""
}
