package com.aravind.orderalert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var btnStartMonitoring: Button
    private lateinit var btnTestNow: Button

    private var isLoggedIn = false
    private var pendingCapture: Triple<String, List<Pair<String, String>>, Map<String, String>>? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Notifications are disabled. You won't get order alerts until you enable them.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.createChannel(this)
        requestNotificationPermissionIfNeeded()

        webView = findViewById(R.id.webView)
        statusText = findViewById(R.id.statusText)
        btnStartMonitoring = findViewById(R.id.btnStartMonitoring)
        btnTestNow = findViewById(R.id.btnTestNow)

        setupWebView()

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        isLoggedIn = prefs.getBoolean(Constants.PREF_LOGGED_IN, false)
        updateStatus()

        btnStartMonitoring.setOnClickListener {
            if (!isLoggedIn) {
                Toast.makeText(this, "Please log in first using the page below.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            WorkScheduler.schedule(this)
            prefs.edit().putBoolean(Constants.PREF_LOGGED_IN, true).apply()
            statusText.text = "Monitoring active. Checking every ${Constants.CHECK_INTERVAL_MINUTES} minutes " +
                "for orders that are paid but still processing."
            Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        }

        btnTestNow.setOnClickListener {
            if (!isLoggedIn) {
                Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = OneTimeWorkRequestBuilder<OrderCheckWorker>()
                .setInputData(workDataOf("manual" to true))
                .build()
            WorkManager.getInstance(this).enqueue(request)
            Toast.makeText(
                this,
                "Checking now... watch your notifications for the result in a few seconds.",
                Toast.LENGTH_LONG
            ).show()
        }

        webView.loadUrl(Constants.LOGIN_URL)
    }

    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false

        // Pretend to be a desktop Chrome browser so the site serves/renders
        // its normal desktop layout instead of the mobile view.
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        webView.addJavascriptInterface(
            LoginCaptureInterface { actionUrl, fields, types ->
                runOnUiThread {
                    pendingCapture = Triple(actionUrl, fields, types)
                }
            },
            "AndroidLoginCapture"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return

                // Force the page's CSS to treat the viewport as desktop-width,
                // in case its layout relies on actual viewport size rather
                // than user agent sniffing.
                view?.evaluateJavascript(
                    """
                    (function() {
                        var meta = document.querySelector('meta[name="viewport"]');
                        if (!meta) {
                            meta = document.createElement('meta');
                            meta.name = 'viewport';
                            document.getElementsByTagName('head')[0].appendChild(meta);
                        }
                        meta.setAttribute('content', 'width=1280, initial-scale=0.3');
                    })();
                    """.trimIndent(),
                    null
                )

                val onLoginPage = url.contains("login.php")

                if (onLoginPage) {
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var form = document.querySelector('form');
                            if (!form || form.__orderAlertHooked) return;
                            form.__orderAlertHooked = true;
                            form.addEventListener('submit', function() {
                                try {
                                    var data = { action: form.action, fields: [] };
                                    var inputs = form.querySelectorAll('input, select, textarea');
                                    inputs.forEach(function(el) {
                                        if (!el.name) return;
                                        if ((el.type === 'checkbox' || el.type === 'radio') && !el.checked) return;
                                        if (el.type === 'button') return;
                                        data.fields.push({ name: el.name, type: el.type || 'text', value: el.value });
                                    });
                                    AndroidLoginCapture.onFormSubmit(JSON.stringify(data));
                                } catch (e) {}
                            });
                        })();
                        """.trimIndent(),
                        null
                    )
                }

                if (!onLoginPage && url.startsWith(Constants.BASE_URL)) {
                    val wasLoggedIn = isLoggedIn
                    isLoggedIn = true
                    cookieManager.flush()
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(Constants.PREF_LOGGED_IN, true).apply()
                    updateStatus()

                    // Now that login is confirmed successful, persist whatever
                    // form data was captured at submit time for auto re-login later.
                    pendingCapture?.let { (actionUrl, fields, types) ->
                        val passwordEntry = fields.firstOrNull { types[it.first] == "password" }
                        val emailEntry = fields.firstOrNull {
                            types[it.first] != "password" &&
                                (it.first.contains("email", true) ||
                                    it.first.contains("user", true) ||
                                    it.first.contains("login", true))
                        } ?: fields.firstOrNull { types[it.first] != "password" && types[it.first] != "hidden" }

                        if (passwordEntry != null && emailEntry != null) {
                            val others = fields
                                .filter { it.first != passwordEntry.first && it.first != emailEntry.first }
                                .associate { it.first to it.second }
                            CredentialStore.save(
                                this@MainActivity,
                                SavedLogin(
                                    actionUrl = actionUrl,
                                    emailField = emailEntry.first,
                                    emailValue = emailEntry.second,
                                    passwordField = passwordEntry.first,
                                    passwordValue = passwordEntry.second,
                                    otherFields = others
                                )
                            )
                        }
                        pendingCapture = null
                    }

                    if (!wasLoggedIn) {
                        Toast.makeText(
                            this@MainActivity,
                            "Logged in. Tap 'Start Monitoring' to begin.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun updateStatus() {
        statusText.text = if (isLoggedIn) {
            "Logged in. Tap 'Start Monitoring' to receive alerts for paid + processing orders " +
                "every ${Constants.CHECK_INTERVAL_MINUTES} minutes."
        } else {
            "Not logged in. Please log in on the page below with your asraaz.com account."
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
