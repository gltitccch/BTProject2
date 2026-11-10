package com.example.btproject2.ui.dialogs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.utils.KinTraceAlert
import com.example.btproject2.utils.XenditPaymentEngine
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * HeritagePassBottomSheetDialog:
 * Express Bottom-Sheet for KinTrace Heritage Pass (₱99 Lifetime License via GCash).
 * Automatically unlocks +150 member capacity expansion immediately upon payment confirmation.
 */
class HeritagePassBottomSheetDialog : BottomSheetDialogFragment() {

    private var treeId: String = ""
    private var treeName: String = ""
    private var currentQuota: Int = FamilyTree.DEFAULT_FREEMIUM_QUOTA
    private var currentCount: Int = 0
    private var isPremium: Boolean = false

    var onPassUnlocked: ((newQuota: Int) -> Unit)? = null

    private lateinit var firestoreHelper: FirestoreHelper
    private lateinit var authHelper: AuthHelper

    // Views
    private lateinit var layoutPassInitial: LinearLayout
    private lateinit var layoutPassLoading: LinearLayout
    private lateinit var layoutPassWaiting: LinearLayout
    private lateinit var layoutPassSuccess: LinearLayout

    private lateinit var tvPassSheetTitle: TextView
    private lateinit var tvPassSheetSubtitle: TextView
    private lateinit var tvClanName: TextView
    private lateinit var tvCurrentQuotaPill: TextView
    private lateinit var tvTargetCapacity: TextView
    private lateinit var tvPriceTitle: TextView
    private lateinit var tvPriceSubtitle: TextView
    private lateinit var tvPriceAmount: TextView
    private lateinit var tvPassLoadingStatus: TextView
    private lateinit var tvWaitingRef: TextView
    private lateinit var tvSuccessDetails: TextView
    private lateinit var tvSuccessRef: TextView

    private lateinit var btnPayGcash: Button
    private lateinit var btnFastSandboxPay: TextView
    private lateinit var btnCheckPaymentStatus: Button
    private lateinit var btnReopenCheckout: Button
    private lateinit var btnDoneSuccess: Button

    private var activeInvoice: XenditPaymentEngine.XenditInvoice? = null

    companion object {
        private const val ARG_TREE_ID = "arg_tree_id"
        private const val ARG_TREE_NAME = "arg_tree_name"
        private const val ARG_CURRENT_QUOTA = "arg_current_quota"
        private const val ARG_CURRENT_COUNT = "arg_current_count"
        private const val ARG_IS_PREMIUM = "arg_is_premium"

        fun newInstance(
            treeId: String,
            treeName: String,
            currentQuota: Int = FamilyTree.DEFAULT_FREEMIUM_QUOTA,
            currentCount: Int = 0,
            isPremium: Boolean = false
        ): HeritagePassBottomSheetDialog {
            val dialog = HeritagePassBottomSheetDialog()
            val args = Bundle().apply {
                putString(ARG_TREE_ID, treeId)
                putString(ARG_TREE_NAME, treeName)
                putInt(ARG_CURRENT_QUOTA, currentQuota)
                putInt(ARG_CURRENT_COUNT, currentCount)
                putBoolean(ARG_IS_PREMIUM, isPremium)
            }
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        treeId = arguments?.getString(ARG_TREE_ID).orEmpty()
        treeName = arguments?.getString(ARG_TREE_NAME).orEmpty()
        currentQuota = arguments?.getInt(ARG_CURRENT_QUOTA, FamilyTree.DEFAULT_FREEMIUM_QUOTA) ?: FamilyTree.DEFAULT_FREEMIUM_QUOTA
        currentCount = arguments?.getInt(ARG_CURRENT_COUNT, 0) ?: 0
        isPremium = arguments?.getBoolean(ARG_IS_PREMIUM, false) ?: false

        firestoreHelper = FirestoreHelper()
        authHelper = AuthHelper()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_heritage_pass_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupContent()
        setupListeners()
    }

    private fun bindViews(view: View) {
        layoutPassInitial = view.findViewById(R.id.layoutPassInitial)
        layoutPassLoading = view.findViewById(R.id.layoutPassLoading)
        layoutPassWaiting = view.findViewById(R.id.layoutPassWaiting)
        layoutPassSuccess = view.findViewById(R.id.layoutPassSuccess)

        tvPassSheetTitle = view.findViewById(R.id.tvPassSheetTitle)
        tvPassSheetSubtitle = view.findViewById(R.id.tvPassSheetSubtitle)
        tvClanName = view.findViewById(R.id.tvClanName)
        tvCurrentQuotaPill = view.findViewById(R.id.tvCurrentQuotaPill)
        tvTargetCapacity = view.findViewById(R.id.tvTargetCapacity)
        tvPriceTitle = view.findViewById(R.id.tvPriceTitle)
        tvPriceSubtitle = view.findViewById(R.id.tvPriceSubtitle)
        tvPriceAmount = view.findViewById(R.id.tvPriceAmount)
        tvPassLoadingStatus = view.findViewById(R.id.tvPassLoadingStatus)
        tvWaitingRef = view.findViewById(R.id.tvWaitingRef)
        tvSuccessDetails = view.findViewById(R.id.tvSuccessDetails)
        tvSuccessRef = view.findViewById(R.id.tvSuccessRef)

        btnPayGcash = view.findViewById(R.id.btnPayGcash)
        btnFastSandboxPay = view.findViewById(R.id.btnFastSandboxPay)
        btnCheckPaymentStatus = view.findViewById(R.id.btnCheckPaymentStatus)
        btnReopenCheckout = view.findViewById(R.id.btnReopenCheckout)
        btnDoneSuccess = view.findViewById(R.id.btnDoneSuccess)

        view.findViewById<Button?>(R.id.btnWaitingFastPay)?.setOnClickListener {
            simulateSandboxPayment()
        }

        view.findViewById<TextView>(R.id.btnPassSheetClose).setOnClickListener {
            dismiss()
        }
    }

    private fun setupContent() {
        tvClanName.text = treeName.ifBlank { "Family Tree" }
        tvCurrentQuotaPill.text = "CURRENT: $currentQuota CAP"

        if (!isPremium) {
            // Tier 1: One-Time Lifetime Heritage Pass (₱99)
            val nextCap = when {
                currentQuota < 200 -> 200
                else -> currentQuota + FamilyTree.EXPANSION_STEP
            }
            val addedSlots = nextCap - currentQuota

            tvPassSheetTitle.text = "KinTrace Heritage Pass"
            tvPassSheetSubtitle.text = "One-Time Lifetime License • ₱99.00 via GCash"
            tvTargetCapacity.text = "⚡ Unlocks $nextCap Members (+$addedSlots Slots Instantly)"
            tvPriceTitle.text = "One-Time Lifetime Fee"
            tvPriceSubtitle.text = "Official GCash Checkout • No Subscriptions"
            tvPriceAmount.text = "₱99.00"
            btnPayGcash.text = "⚡ Pay ₱99.00 with GCash"
        } else {
            // Tier 2: Clan Capacity Expansion Booster (₱50)
            val nextCap = currentQuota + FamilyTree.EXPANSION_STEP
            tvPassSheetTitle.text = "Clan Expansion Pack"
            tvPassSheetSubtitle.text = "+150 Member Slots • ₱50.00 via GCash"
            tvTargetCapacity.text = "⚡ Expands to $nextCap Members (+150 Slots Instantly)"
            tvPriceTitle.text = "Clan Capacity Expansion Pack"
            tvPriceSubtitle.text = "Pure Storage Add-On • Heritage Pass Retained"
            tvPriceAmount.text = "₱50.00"
            btnPayGcash.text = "⚡ Pay ₱50.00 with GCash"
        }

        showState(State.INITIAL)
    }

    private fun setupListeners() {
        // 1. Pay with GCash button
        btnPayGcash.setOnClickListener {
            initiateGcashPayment()
        }

        // 2. Fast Sandbox Verification (for Defense Demo)
        btnFastSandboxPay.setOnClickListener {
            simulateSandboxPayment()
        }

        // 3. Check Payment Status
        btnCheckPaymentStatus.setOnClickListener {
            verifyPaymentStatus()
        }

        // 4. Re-open checkout URL in browser
        btnReopenCheckout.setOnClickListener {
            activeInvoice?.invoiceUrl?.let { url ->
                if (url.isNotBlank()) {
                    openBrowserUrl(url)
                }
            }
        }

        // 5. Done / Continue
        btnDoneSuccess.setOnClickListener {
            dismiss()
        }
    }

    private fun initiateGcashPayment() {
        if (treeId.isBlank()) {
            context?.let { KinTraceAlert.warning(it, "Error", "Invalid family tree ID.") }
            return
        }

        showState(State.LOADING)
        tvPassLoadingStatus.text = "Connecting to GCash Gateway via Xendit..."

        val userEmail = authHelper.getCurrentUser()?.email.orEmpty()
        val product = if (isPremium) XenditPaymentEngine.ProductType.CLAN_BOOSTER else XenditPaymentEngine.ProductType.HERITAGE_PASS

        XenditPaymentEngine.createGcashInvoice(
            treeId = treeId,
            treeName = treeName,
            userEmail = userEmail,
            productType = product,
            onSuccess = { invoice ->
                if (!isAdded || isDetached) return@createGcashInvoice
                activeInvoice = invoice

                // Launch browser with the official Xendit GCash checkout page
                openBrowserUrl(invoice.invoiceUrl)

                // Update sheet to waiting state
                showState(State.WAITING)
                tvWaitingRef.text = "Ref: ${invoice.externalId}"
            },
            onError = { err ->
                if (!isAdded || isDetached) return@createGcashInvoice
                showState(State.INITIAL)
                context?.let {
                    KinTraceAlert.warning(it, "Payment Gateway Error", err)
                }
            }
        )
    }

    private fun verifyPaymentStatus() {
        val invoice = activeInvoice ?: return
        showState(State.LOADING)
        tvPassLoadingStatus.text = "Verifying GCash transaction with Xendit..."

        XenditPaymentEngine.checkInvoiceStatus(
            invoiceId = invoice.id,
            onSuccess = { updatedInvoice ->
                if (!isAdded || isDetached) return@checkInvoiceStatus
                activeInvoice = updatedInvoice

                if (updatedInvoice.isPaid) {
                    finalizeUnlockInFirestore(updatedInvoice)
                } else {
                    showState(State.WAITING)
                    context?.let {
                        KinTraceAlert.security(
                            it,
                            "Payment Pending",
                            "GCash authorization is still pending. Please complete payment in your browser, then tap 'Check Payment Status'.",
                            "WAITING"
                        )
                    }
                }
            },
            onError = { err ->
                if (!isAdded || isDetached) return@checkInvoiceStatus
                showState(State.WAITING)
                context?.let {
                    KinTraceAlert.warning(it, "Status Check Failed", err)
                }
            }
        )
    }

    private fun simulateSandboxPayment() {
        showState(State.LOADING)
        tvPassLoadingStatus.text = "Authorizing simulated sandbox GCash payment..."

        val product = if (isPremium) XenditPaymentEngine.ProductType.CLAN_BOOSTER else XenditPaymentEngine.ProductType.HERITAGE_PASS

        XenditPaymentEngine.simulatePaidInvoice(
            existingInvoice = activeInvoice,
            treeId = treeId,
            productType = product,
            onSuccess = { paidInvoice ->
                if (!isAdded || isDetached) return@simulatePaidInvoice
                finalizeUnlockInFirestore(paidInvoice)
            }
        )
    }

    private fun finalizeUnlockInFirestore(paidInvoice: XenditPaymentEngine.XenditInvoice) {
        showState(State.LOADING)
        tvPassLoadingStatus.text = "Expanding family tree capacity in cloud..."

        if (!isPremium) {
            // Tier 1: One-Time Lifetime Heritage Pass (₱99)
            firestoreHelper.unlockHeritagePassWithGcash(
                treeId = treeId,
                treeName = treeName,
                invoiceId = paidInvoice.id,
                externalId = paidInvoice.externalId,
                amount = paidInvoice.amount,
                onSuccess = { newQuota ->
                    if (!isAdded || isDetached) return@unlockHeritagePassWithGcash
                    isPremium = true
                    showState(State.SUCCESS)

                    val added = newQuota - currentQuota
                    tvSuccessDetails.text = "👑 Heritage Pass Unlocked! Capacity expanded to $newQuota members (+${added} slots) with unlimited oral history and VIP Crown!"
                    tvSuccessRef.text = "Ref: ${paidInvoice.externalId}"

                    onPassUnlocked?.invoke(newQuota)
                },
                onFailure = { err ->
                    if (!isAdded || isDetached) return@unlockHeritagePassWithGcash
                    showState(State.WAITING)
                    context?.let {
                        KinTraceAlert.warning(it, "Cloud Sync Error", err.localizedMessage ?: "Failed to save upgrade.")
                    }
                }
            )
        } else {
            // Tier 2: Clan Capacity Booster (₱50)
            firestoreHelper.purchaseClanExpansionBooster(
                treeId = treeId,
                treeName = treeName,
                invoiceId = paidInvoice.id,
                externalId = paidInvoice.externalId,
                amount = paidInvoice.amount,
                onSuccess = { newQuota ->
                    if (!isAdded || isDetached) return@purchaseClanExpansionBooster
                    showState(State.SUCCESS)

                    tvSuccessDetails.text = "⚡ +150 Member Slots Added! Total limit is now $newQuota members. Your Heritage Pass and VIP Crown remain 100% active."
                    tvSuccessRef.text = "Ref: ${paidInvoice.externalId}"

                    onPassUnlocked?.invoke(newQuota)
                },
                onFailure = { err ->
                    if (!isAdded || isDetached) return@purchaseClanExpansionBooster
                    showState(State.WAITING)
                    context?.let {
                        KinTraceAlert.warning(it, "Cloud Sync Error", err.localizedMessage ?: "Failed to save booster.")
                    }
                }
            )
        }
    }

    private fun openBrowserUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            context?.let {
                KinTraceAlert.warning(it, "Browser Error", "Could not open browser for payment: ${e.localizedMessage}")
            }
        }
    }

    private enum class State {
        INITIAL,
        LOADING,
        WAITING,
        SUCCESS
    }

    private fun showState(state: State) {
        layoutPassInitial.visibility = if (state == State.INITIAL) View.VISIBLE else View.GONE
        layoutPassLoading.visibility = if (state == State.LOADING) View.VISIBLE else View.GONE
        layoutPassWaiting.visibility = if (state == State.WAITING) View.VISIBLE else View.GONE
        layoutPassSuccess.visibility = if (state == State.SUCCESS) View.VISIBLE else View.GONE
    }
}
