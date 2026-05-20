package sa.saaedpay.demo

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import sa.saaedpay.demo.databinding.ActivityMainBinding
import sa.saaedpay.demo.util.JsonFormatter
import sa.saaedpay.softpos.SaaedPay
import sa.saaedpay.softpos.api.callback.SaaedPayPurchaseCallback
import sa.saaedpay.softpos.api.callback.SaaedPaySetupCallback
import sa.saaedpay.softpos.api.request.SaaedPayPurchaseRequest
import sa.saaedpay.softpos.api.response.SaaedPayError
import sa.saaedpay.softpos.api.response.SaaedPayPurchaseResult
import sa.saaedpay.softpos.api.response.SaaedPaySetupResult
import sa.saaedpay.softpos.domain.error.SaaedPayErrorType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private enum class SdkState {
        NOT_INITIALIZED, LOADING, READY, PURCHASE_RUNNING, ERROR
    }

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupButtons()
        applyState(SdkState.NOT_INITIALIZED)
        log("SaaedPay SDK Demo — ready")
        log("Call Setup to configure a terminal.")
    }

    // ─── Button wiring ────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnSetup.setOnClickListener { runSetup() }
        binding.btnPurchase.setOnClickListener { runPurchase() }
        binding.btnReset.setOnClickListener { runReset() }
        binding.btnClearLogs.setOnClickListener {
            binding.tvLogs.text = ""
            showSnackbar("Logs cleared")
        }
    }

    // ─── Setup ───────────────────────────────────────────────────────────

    private fun runSetup() {
        val uuid = binding.inputTerminalUuid.editText?.text?.toString()?.trim().orEmpty()
        if (uuid.isBlank()) {
            binding.inputTerminalUuid.error = "Terminal UUID is required"
            return
        }
        binding.inputTerminalUuid.error = null
        hideKeyboard()

        applyState(SdkState.LOADING)
        logSection("SETUP")
        log("→ UUID: $uuid")

        SaaedPay.getInstance().setup(
            terminalUuid = uuid,
            callback = object : SaaedPaySetupCallback {
                override fun onSuccess(result: SaaedPaySetupResult) {
                    applyState(SdkState.READY)
                    log("✓ Setup complete${if (result.fromCache) " (from cache)" else ""}")
                    log("  Terminal : ${result.terminal.title}")
                    log("  TID      : ${result.terminal.tid}")
                    log("  Sandbox  : ${result.terminal.isSandbox}")
                    logDivider()
                    log(JsonFormatter.format(result))
                }

                override fun onFailure(error: SaaedPayError) {
                    applyState(SdkState.ERROR)
                    logError(error)
                }
            }
        )
    }

    // ─── Purchase ─────────────────────────────────────────────────────────

    private fun runPurchase() {
        val amountText = binding.inputAmount.editText?.text?.toString()?.trim().orEmpty()
        val amount = amountText.toLongOrNull()
        if (amount == null || amount <= 0L) {
            binding.inputAmount.error = "Enter a valid amount in halalas (e.g. 1000 = 10.00 SAR)"
            return
        }
        binding.inputAmount.error = null
        hideKeyboard()

        val request = SaaedPayPurchaseRequest(
            amount = amount,
            customerReferenceNumber = "ORD-${System.currentTimeMillis()}",
            enableReceiptUi = true,
            enableReversal = true,
            finishTimeOut = 60L,
            isUiDismissible = true
        )

        applyState(SdkState.PURCHASE_RUNNING)
        logSection("PURCHASE")
        log("→ Amount : $amount halalas (${"%.2f".format(amount / 100.0)} SAR)")
        log("→ Ref    : ${request.customerReferenceNumber}")

        SaaedPay.getInstance().purchase(
            request = request,
            callback = object : SaaedPayPurchaseCallback {
                override fun onSuccess(result: SaaedPayPurchaseResult) {
                    applyState(SdkState.READY)
                    log("✓ Payment approved")
                    log("  Status      : ${result.status}")
                    log("  Transaction : ${result.transactionId ?: "—"}")
                    log("  Approval    : ${result.approvalCode ?: "—"}")
                    log("  Amount      : ${result.amount} ${result.currency ?: ""}")
                    log("  Receipts    : ${result.receipts.size}")
                    logDivider()
                    log(JsonFormatter.format(result))
                    showSnackbar("Payment approved — ${result.approvalCode}")
                }

                override fun onFailure(error: SaaedPayError) {
                    val nextState = when (error.type) {
                        SaaedPayErrorType.AUTHENTICATION_FAILED,
                        SaaedPayErrorType.NOT_INITIALIZED -> SdkState.NOT_INITIALIZED
                        else -> SdkState.ERROR
                    }
                    applyState(nextState)
                    logError(error)
                    showSnackbar("Payment failed: ${error.message}", isError = true)
                }
            }
        )
    }

    // ─── Reset ────────────────────────────────────────────────────────────

    private fun runReset() {
        logSection("RESET")
        SaaedPay.getInstance().reset()
        applyState(SdkState.NOT_INITIALIZED)
        log("✓ SDK state cleared")
        log("  Call Setup again before next purchase.")
        showSnackbar("SDK reset")
    }

    // ─── State management ─────────────────────────────────────────────────

    private fun applyState(state: SdkState) {
        runOnUiThread {
            when (state) {
                SdkState.NOT_INITIALIZED -> {
                    setChip("NOT INITIALIZED", R.color.status_uninitialized)
                    setButtons(setup = true, purchase = false, reset = true, spinner = false)
                }
                SdkState.LOADING -> {
                    setChip("SETTING UP…", R.color.status_loading)
                    setButtons(setup = false, purchase = false, reset = false, spinner = true)
                }
                SdkState.READY -> {
                    setChip("READY", R.color.status_ready)
                    setButtons(setup = true, purchase = true, reset = true, spinner = false)
                }
                SdkState.PURCHASE_RUNNING -> {
                    setChip("PURCHASE RUNNING", R.color.status_purchase)
                    setButtons(setup = false, purchase = false, reset = false, spinner = true)
                }
                SdkState.ERROR -> {
                    setChip("ERROR", R.color.status_error)
                    setButtons(setup = true, purchase = false, reset = true, spinner = false)
                }
            }
        }
    }

    private fun setChip(label: String, colorRes: Int) {
        binding.chipStatus.text = label
        binding.chipStatus.setChipBackgroundColorResource(colorRes)
    }

    private fun setButtons(setup: Boolean, purchase: Boolean, reset: Boolean, spinner: Boolean) {
        binding.btnSetup.isEnabled = setup
        binding.btnPurchase.isEnabled = purchase
        binding.btnReset.isEnabled = reset
        binding.progressBar.visibility = if (spinner) View.VISIBLE else View.GONE
    }

    // ─── Logging helpers ──────────────────────────────────────────────────

    private fun log(message: String) {
        val timestamp = timeFormat.format(Date())
        runOnUiThread {
            binding.tvLogs.append("[$timestamp] $message\n")
            binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun logSection(title: String) {
        log("")
        log("━━━ $title ━━━")
    }

    private fun logDivider() {
        log("  ─── Full Response ───")
    }

    private fun logError(error: SaaedPayError) {
        log("✗ FAILED [${error.code}] ${error.type.name}")
        log("  ${error.message}")
    }

    // ─── UI helpers ───────────────────────────────────────────────────────

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
        if (isError) {
            snackbar.setBackgroundTint(getColor(R.color.status_error))
            snackbar.setTextColor(getColor(android.R.color.white))
        }
        snackbar.show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
