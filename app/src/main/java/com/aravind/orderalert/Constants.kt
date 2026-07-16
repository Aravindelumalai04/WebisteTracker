package com.aravind.orderalert

object Constants {
    const val BASE_URL = "https://asraaz.com"
    const val LOGIN_URL = "https://asraaz.com/login.php"
    const val ORDERS_URL = "https://asraaz.com/signup/generate/dashboard/orders.php"

    const val PREFS_NAME = "order_alert_prefs"
    const val PREF_NOTIFIED_IDS = "notified_order_ids"
    const val PREF_LOGGED_IN = "logged_in"
    const val PREF_SESSION_EXPIRED_NOTIFIED = "session_expired_notified"

    const val WORK_NAME = "order_check_work"
    const val CHANNEL_ID = "order_alert_channel"
    const val CHANNEL_NAME = "Paid & Pending Orders"

    // Column headers as they appear on the orders page (case-insensitive match).
    // If the site changes header text, update these.
    const val COL_ORDER_ID = "order id"
    const val COL_CUSTOMER_NAME = "customer name"
    const val COL_GUEST_NAME = "guest name"
    const val COL_PAYMENT_TYPE = "payment type"
    const val COL_STATUS = "status"
    const val COL_AMOUNT = "amount"

    const val TARGET_PAYMENT_TYPE = "paid"
    const val TARGET_STATUS = "processing"

    const val CHECK_INTERVAL_MINUTES = 15L
}
