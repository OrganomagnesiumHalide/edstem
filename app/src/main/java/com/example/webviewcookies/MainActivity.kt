package com.example.webviewcookies

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        swipeRefreshLayout.setOnRefreshListener {
            webView.reload()
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Add JavascriptInterface to detect SPA scrolling
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun setAtTop(isAtTop: Boolean) {
                runOnUiThread {
                    swipeRefreshLayout.isEnabled = isAtTop
                }
            }
        }, "AndroidScroll")

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false
                
                // Inject JS to monitor scroll on all elements
                view?.evaluateJavascript("""
                    function checkScroll(e) {
                        var top = 0;
                        if (e && e.target && e.target !== document) {
                            top = e.target.scrollTop;
                        } else {
                            top = window.scrollY || document.documentElement.scrollTop;
                        }
                        AndroidScroll.setAtTop(top === 0);
                    }
                    document.addEventListener('scroll', checkScroll, true);
                    checkScroll();
                """.trimIndent(), null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                val host = url.host ?: return false

                // Allow edstem.org and its subdomains to load inside the app
                if (host == "edstem.org" || host.endsWith(".edstem.org")) {
                    return false
                }

                // Open everything else in the user's default external browser
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, url)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Ignore if no browser is found
                }
                
                return true // Prevent WebView from loading the external URL
            }
        }
        webView.webChromeClient = WebChromeClient()

        if (savedInstanceState == null) {
            val intentUrl = intent?.data?.toString()
            if (intentUrl != null) {
                webView.loadUrl(intentUrl)
            } else {
                webView.loadUrl("https://edstem.org")
            }
        }

        // Handle back button / swipe back gesture
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val intentUrl = intent?.data?.toString()
        if (intentUrl != null) {
            webView.loadUrl(intentUrl)
        }
    }
}
