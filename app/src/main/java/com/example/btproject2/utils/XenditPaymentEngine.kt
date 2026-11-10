package com.example.btproject2.utils

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Xendit Payment Engine for KinTrace
 * Handles GCash invoice creation, status checking, and sandbox payment simulation.
 * Uses official Xendit Invoice API v2.
 */
object XenditPaymentEngine {

    private const val TAG = "XenditPaymentEngine"
    private const val XENDIT_API_URL = "https://api.xendit.co/v2/invoices"

    // User's Xendit Development Secret Key
    private const val XENDIT_SECRET_KEY = "xnd_development_NnDCYbaprpcPHUlyiPidiZ72UY0nJSPVuwg64WXFgMNSAU83fOsGV0lxbEDmp9"

    const val HERITAGE_PASS_PRICE_PHP = 99.0
    const val CLAN_BOOSTER_PRICE_PHP = 50.0
    const val HERITAGE_PASS_EXPANSION_SLOTS = 150

    enum class ProductType(val pricePhp: Double, val expansionSlots: Int, val title: String) {
        HERITAGE_PASS(99.0, 150, "KinTrace Heritage Pass (One-Time Lifetime)"),
        CLAN_BOOSTER(50.0, 150, "Clan Capacity Expansion Pack (+150 Slots)")
    }

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class XenditInvoice(
        val id: String = "",
        val externalId: String = "",
        val invoiceUrl: String = "",
        val status: String = "PENDING", // PENDING, PAID, EXPIRED
        val amount: Double = HERITAGE_PASS_PRICE_PHP,
        val payerEmail: String = "",
        val paymentMethod: String = "GCASH",
        val description: String = "",
        val merchantName: String = "KinTrace",
        val expiryDate: String = "",
        val created: String = ""
    ) {
        val isPaid: Boolean
            get() = status.equals("PAID", ignoreCase = true) || status.equals("SETTLED", ignoreCase = true)
    }

    private fun getBasicAuthHeader(): String {
        val credentials = "$XENDIT_SECRET_KEY:"
        val base64 = Base64.encodeToString(credentials.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "Basic $base64"
    }

    /**
     * Create an official GCash invoice via Xendit API v2.
     * Restricts payment_methods strictly to GCASH.
     */
    fun createGcashInvoice(
        treeId: String,
        treeName: String,
        userEmail: String,
        productType: ProductType = ProductType.HERITAGE_PASS,
        onSuccess: (XenditInvoice) -> Unit,
        onError: (String) -> Unit
    ) {
        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(XENDIT_API_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", getBasicAuthHeader())
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true

                val prefix = if (productType == ProductType.HERITAGE_PASS) "KT-PASS" else "KT-BOOST"
                val externalId = "$prefix-${treeId.take(8)}-${System.currentTimeMillis()}"
                val resolvedEmail = userEmail.ifBlank { "heritage.member@kintrace.app" }

                val payload = JSONObject().apply {
                    put("external_id", externalId)
                    put("amount", productType.pricePhp.toInt())
                    put("currency", "PHP")
                    put("description", "${productType.title} - $treeName")
                    put("payer_email", resolvedEmail)

                    val gcashOnlyArray = JSONArray()
                    gcashOnlyArray.put("GCASH")
                    put("payment_methods", gcashOnlyArray)

                    val customerObj = JSONObject().apply {
                        put("email", resolvedEmail)
                    }
                    put("customer", customerObj)

                    put("should_exclude_credit_card", true)
                    put("success_redirect_url", "https://kintrace.app/payment_success")
                    put("failure_redirect_url", "https://kintrace.app/payment_failure")
                }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

                Log.d(TAG, "Create Invoice Response [$responseCode]: $responseBody")

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val invoice = parseInvoiceJson(json)
                    mainHandler.post { onSuccess(invoice) }
                } else {
                    val errMsg = try {
                        val errJson = JSONObject(responseBody)
                        errJson.optString("message", "Payment gateway error ($responseCode)")
                    } catch (e: Exception) {
                        "Error ($responseCode): $responseBody"
                    }
                    mainHandler.post { onError(errMsg) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception creating invoice", e)
                mainHandler.post { onError("Connection error: ${e.localizedMessage ?: "Could not reach Xendit"}") }
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Poll or check invoice payment status from Xendit API.
     */
    fun checkInvoiceStatus(
        invoiceId: String,
        onSuccess: (XenditInvoice) -> Unit,
        onError: (String) -> Unit
    ) {
        if (invoiceId.isBlank()) {
            onError("Invoice ID is empty")
            return
        }

        executor.execute {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("$XENDIT_API_URL/$invoiceId")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", getBasicAuthHeader())
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 12000
                connection.readTimeout = 12000

                val responseCode = connection.responseCode
                val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseBody = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

                Log.d(TAG, "Check Invoice Status [$responseCode]: $responseBody")

                if (responseCode in 200..299) {
                    val json = JSONObject(responseBody)
                    val invoice = parseInvoiceJson(json)
                    mainHandler.post { onSuccess(invoice) }
                } else {
                    mainHandler.post { onError("Could not verify invoice status ($responseCode)") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception checking invoice status", e)
                mainHandler.post { onError("Status check error: ${e.localizedMessage ?: "Unknown"}") }
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Defense-Ready Sandbox Simulator:
     * Fast-forwards payment to PAID status for smooth capstone demonstration.
     */
    fun simulatePaidInvoice(
        existingInvoice: XenditInvoice? = null,
        treeId: String = "",
        productType: ProductType = ProductType.HERITAGE_PASS,
        onSuccess: (XenditInvoice) -> Unit
    ) {
        val prefix = if (productType == ProductType.HERITAGE_PASS) "KT-PASS" else "KT-BOOST"
        val inv = existingInvoice ?: XenditInvoice(
            id = "sim_${System.currentTimeMillis()}",
            externalId = "$prefix-${treeId.take(8)}-${System.currentTimeMillis()}",
            invoiceUrl = "https://checkout-staging.xendit.co/web/mock",
            status = "PAID",
            amount = productType.pricePhp,
            paymentMethod = "GCASH",
            description = "${productType.title} (Simulated Sandbox GCash)",
            merchantName = "KinTrace"
        )
        val paidInvoice = inv.copy(status = "PAID", amount = productType.pricePhp)
        mainHandler.post { onSuccess(paidInvoice) }
    }

    private fun parseInvoiceJson(json: JSONObject): XenditInvoice {
        return XenditInvoice(
            id = json.optString("id", ""),
            externalId = json.optString("external_id", ""),
            invoiceUrl = json.optString("invoice_url", ""),
            status = json.optString("status", "PENDING"),
            amount = json.optDouble("amount", HERITAGE_PASS_PRICE_PHP),
            payerEmail = json.optString("payer_email", ""),
            paymentMethod = "GCASH",
            description = json.optString("description", ""),
            merchantName = json.optString("merchant_name", "KinTrace"),
            expiryDate = json.optString("expiry_date", ""),
            created = json.optString("created", "")
        )
    }
}
