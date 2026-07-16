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

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var btnStartMonitoring: Button
    private lateinit var btnTestNow: Button

    private var isLoggedIn = false

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
                "for orders that are paid but still pending."
            Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        }

        btnTestNow.setOnClickListener {
            if (!isLoggedIn) {
                Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val request = OneTimeWorkRequestBuilder<OrderCheckWorker>().build()
            WorkManager.getInstance(this).enqueue(request)
            Toast.makeText(this, "Checking now...", Toast.LENGTH_SHORT).show()
        }

        webView.loadUrl(Constants.LOGIN_URL)
    }

    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url == null) return

                val onLoginPage = url.contains("login.php")
                if (!onLoginPage && url.startsWith(Constants.BASE_URL)) {
                    val wasLoggedIn = isLoggedIn
                    isLoggedIn = true
                    cookieManager.flush()
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean(Constants.PREF_LOGGED_IN, true).apply()
                    updateStatus()
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
            "Logged in. Tap 'Start Monitoring' to receive alerts for paid + pending orders " +
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
