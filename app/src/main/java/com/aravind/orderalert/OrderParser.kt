package com.aravind.orderalert

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object OrderParser {

    /**
     * Returns true if the given HTML looks like the login page rather than
     * the orders dashboard (i.e. the session cookie is missing/expired).
     */
    fun isLoginPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("welcome back") ||
            (lower.contains("type=\"password\"") && !lower.contains("order details"))
    }

    /**
     * Parses the Order Details table from the dashboard HTML.
     * Uses the header row text to figure out which column is which, so it
     * keeps working even if the site reorders columns.
     */
    fun parseOrders(html: String): List<Order> {
        val doc = Jsoup.parse(html)

        // Find the table that has a header row mentioning "Order ID"
        val table = doc.select("table").firstOrNull { table ->
            val headerText = table.select("thead th, tr:first-child th, tr:first-child td")
                .joinToString(" ") { it.text().lowercase() }
            headerText.contains(Constants.COL_ORDER_ID)
        } ?: return emptyList()

        val headerCells = table.select("thead th")
            .ifEmpty { table.select("tr").first()?.select("th, td") ?: emptyList() }

        val headerMap = mutableMapOf<String, Int>()
        headerCells.forEachIndexed { index, cell ->
            headerMap[cell.text().trim().lowercase()] = index
        }

        fun colIndex(name: String): Int? {
            // exact match first, then contains-match fallback
            headerMap[name]?.let { return it }
            return headerMap.entries.firstOrNull { it.key.contains(name) }?.value
        }

        val idxOrderId = colIndex(Constants.COL_ORDER_ID) ?: return emptyList()
        val idxCustomer = colIndex(Constants.COL_CUSTOMER_NAME)
        val idxGuest = colIndex(Constants.COL_GUEST_NAME)
        val idxPayment = colIndex(Constants.COL_PAYMENT_TYPE) ?: return emptyList()
        val idxStatus = colIndex(Constants.COL_STATUS) ?: return emptyList()
        val idxAmount = colIndex(Constants.COL_AMOUNT)

        val bodyRows: List<Element> = table.select("tbody tr").ifEmpty {
            // fall back to all rows minus header row
            table.select("tr").drop(1)
        }

        val orders = mutableListOf<Order>()
        for (row in bodyRows) {
            val cells = row.select("td")
            if (cells.isEmpty() || cells.size <= idxOrderId) continue

            fun cellText(idx: Int?): String {
                if (idx == null || idx >= cells.size) return ""
                return cells[idx].text().trim()
            }

            val orderId = cellText(idxOrderId)
            if (orderId.isBlank()) continue

            orders.add(
                Order(
                    orderId = orderId,
                    customerName = cellText(idxCustomer),
                    guestName = cellText(idxGuest),
                    paymentType = cellText(idxPayment),
                    status = cellText(idxStatus),
                    amount = cellText(idxAmount)
                )
            )
        }
        return orders
    }

    fun filterPaidPending(orders: List<Order>): List<Order> {
        return orders.filter {
            it.paymentType.trim().equals(Constants.TARGET_PAYMENT_TYPE, ignoreCase = true) &&
                it.status.trim().equals(Constants.TARGET_STATUS, ignoreCase = true)
        }
    }
}
