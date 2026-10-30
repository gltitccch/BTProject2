package com.example.btproject2.ui.activities

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.btproject2.R
import com.example.btproject2.firebase.AuthHelper
import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.UserProfile

class LoginActivity : AppCompatActivity() {

    private val authHelper = AuthHelper()
    private val firestoreHelper = FirestoreHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bypassAuthCheck = intent.getBooleanExtra("bypass_auth_check", false)
        if (bypassAuthCheck) {
            authHelper.logout()
            HomeActivity.resetThemeSessionFlags()
            com.example.btproject2.utils.TreePreferences.clear(this)
            com.example.btproject2.firebase.FirestoreHelper.clearCache()
            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()
        }

        val currentUser = authHelper.getCurrentUser()
        if (currentUser != null && !bypassAuthCheck) {
            if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(currentUser.email)) {
                goToHome()
                return
            }
            if (currentUser.isEmailVerified || com.example.btproject2.models.DemoAccountRepository.isDemoEmail(currentUser.email)) {
                goToHome()
            } else {
                startActivity(Intent(this, VerifyEmailActivity::class.java))
                finish()
            }
            return
        }

        setContentView(R.layout.activity_login)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnTogglePassword = findViewById<ImageView>(R.id.btnTogglePassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<TextView>(R.id.btnRegister)
        val btnForgotPassword = findViewById<TextView>(R.id.btnForgotPassword)

        // Setup password show/hide visibility toggle
        setupPasswordToggle(etPassword, btnTogglePassword)

        // Clear errors in real-time
        etEmail.doOnTextChanged { _, _, _, _ -> etEmail.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> etPassword.error = null }

        btnForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email address is required."
                etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                etPassword.error = "Password is required."
                etPassword.requestFocus()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            authHelper.login(email, password,
                onSuccess = { user ->
                    HomeActivity.resetThemeSessionFlags()
                    com.example.btproject2.utils.TreePreferences.clear(this)
                    com.example.btproject2.firebase.FirestoreHelper.clearCache()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

                    if (com.example.btproject2.models.DemoAccountRepository.isNewUserPreviewEmail(user.email)) {
                        com.example.btproject2.utils.TreePreferences.clearUserRememberedTree(this, user.uid)
                        com.example.btproject2.utils.OnboardingPreferences.resetOnboardingState(this, user.uid)
                        firestoreHelper.saveOnboardingState(
                            user.uid,
                            com.example.btproject2.models.UserOnboardingState(hasChosenInitialTheme = false)
                        )
                    }

                    if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(user.email) ||
                        user.isEmailVerified ||
                        com.example.btproject2.models.DemoAccountRepository.isDemoEmail(user.email)) {
                        goToHome()
                    } else {
                        val intent = Intent(this, VerifyEmailActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                },
                onFailure = { error ->
                    if (com.example.btproject2.models.DemoAccountRepository.isNewUserPreviewEmail(email)) {
                        registerAndSignInNewUserPreview(email, password, btnLogin)
                    } else {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"
                        showMessage("Login Failed", error.message ?: "Unknown error occurred.")
                    }
                }
            )
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        com.example.btproject2.models.DemoAccountRepository.ensureLoaded(this)
        val tvVaultDockCounter = findViewById<TextView>(R.id.tvVaultDockCounter)
        tvVaultDockCounter?.text = com.example.btproject2.models.DemoAccountRepository.getAllVaultAccounts().size.toString()

        val btnDemoDock = findViewById<android.view.View>(R.id.btnDemoDock)
        btnDemoDock?.setOnClickListener {
            val bottomSheet = com.example.btproject2.ui.dialogs.DemoVaultBottomSheet.newInstance { account ->
                tvVaultDockCounter?.text = com.example.btproject2.models.DemoAccountRepository.getAllVaultAccounts().size.toString()
                performDemoLogin(account, etEmail, etPassword, btnLogin)
            }
            bottomSheet.show(supportFragmentManager, com.example.btproject2.ui.dialogs.DemoVaultBottomSheet.TAG)
        }
    }

    private fun performDemoLogin(
        account: com.example.btproject2.models.DemoAccount,
        etEmail: EditText,
        etPassword: EditText,
        btnLogin: Button
    ) {
        etEmail.setText(account.email)
        etPassword.setText(account.password)
        etEmail.error = null
        etPassword.error = null

        btnLogin.isEnabled = false
        btnLogin.text = "Signing in as ${account.name}..."

        authHelper.login(account.email, account.password,
            onSuccess = { user ->
                HomeActivity.resetThemeSessionFlags()
                com.example.btproject2.utils.TreePreferences.clear(this)
                com.example.btproject2.firebase.FirestoreHelper.clearCache()
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

                if (com.example.btproject2.models.DemoAccountRepository.isNewUserPreviewEmail(account.email)) {
                    com.example.btproject2.utils.TreePreferences.clearUserRememberedTree(this, user.uid)
                    com.example.btproject2.utils.OnboardingPreferences.resetOnboardingState(this, user.uid)
                    firestoreHelper.saveOnboardingState(
                        user.uid,
                        com.example.btproject2.models.UserOnboardingState(hasChosenInitialTheme = false)
                    )
                }

                if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(user.email) ||
                    user.isEmailVerified ||
                    com.example.btproject2.models.DemoAccountRepository.isDemoEmail(user.email)
                ) {
                    goToHome()
                } else {
                    val verifyIntent = Intent(this, VerifyEmailActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(verifyIntent)
                    finish()
                }
            },
            onFailure = { error ->
                if (account.id.equals("system_admin", ignoreCase = true) ||
                    com.example.btproject2.models.DemoAccountRepository.isNewUserPreviewEmail(account.email)) {
                    authHelper.register(account.email, account.password,
                        onSuccess = { createdUser ->
                            authHelper.updateDisplayName(account.name, onSuccess = {}, onFailure = {})
                            HomeActivity.resetThemeSessionFlags()
                            com.example.btproject2.utils.TreePreferences.clear(this)
                            com.example.btproject2.utils.TreePreferences.clearUserRememberedTree(this, createdUser.uid)
                            com.example.btproject2.utils.OnboardingPreferences.resetOnboardingState(this, createdUser.uid)
                            com.example.btproject2.firebase.FirestoreHelper.clearCache()
                            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

                            val roleStr = if (account.id.equals("system_admin", ignoreCase = true)) "ADMIN" else "user"
                            val profile = UserProfile(
                                id = createdUser.uid,
                                displayName = account.name,
                                email = account.email,
                                role = roleStr
                            )
                            firestoreHelper.saveUserProfile(profile, onSuccess = {
                                goToHome()
                            }, onFailure = {
                                goToHome()
                            })
                        },
                        onFailure = { regErr ->
                            btnLogin.isEnabled = true
                            btnLogin.text = "Sign In to KinTrace ➔"
                            showMessage("Demo Sign-In Failed", regErr.message ?: error.message ?: "Could not authenticate demo account.")
                        }
                    )
                } else {
                    btnLogin.isEnabled = true
                    btnLogin.text = "Sign In to KinTrace ➔"
                    showMessage("Demo Sign-In Failed", error.message ?: "Could not authenticate demo account.")
                }
            }
        )
    }

    private fun registerAndSignInNewUserPreview(email: String, password: String, btnLogin: Button) {
        authHelper.register(email, password,
            onSuccess = { createdUser ->
                authHelper.updateDisplayName("New Member", onSuccess = {}, onFailure = {})
                HomeActivity.resetThemeSessionFlags()
                com.example.btproject2.utils.TreePreferences.clear(this)
                com.example.btproject2.utils.TreePreferences.clearUserRememberedTree(this, createdUser.uid)
                com.example.btproject2.utils.OnboardingPreferences.resetOnboardingState(this, createdUser.uid)
                com.example.btproject2.firebase.FirestoreHelper.clearCache()
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

                val profile = UserProfile(
                    id = createdUser.uid,
                    displayName = "New Member",
                    email = email,
                    role = "user",
                    currentTreeId = "",
                    tosAcceptedAt = System.currentTimeMillis()
                )
                firestoreHelper.saveUserProfile(profile,
                    onSuccess = { goToHome() },
                    onFailure = { goToHome() }
                )
            },
            onFailure = { err ->
                btnLogin.isEnabled = true
                btnLogin.text = "Login"
                showMessage("Login Failed", err.message ?: "Could not sign in as New Member.")
            }
        )
    }

    private fun setupPasswordToggle(editText: EditText, toggleButton: ImageView) {
        var isVisible = false
        toggleButton.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleButton.setImageResource(R.drawable.ic_visibility)
                toggleButton.contentDescription = "Hide password"
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleButton.setImageResource(R.drawable.ic_visibility_off)
                toggleButton.contentDescription = "Show password"
            }
            editText.setSelection(editText.text.length)
        }
    }

    private fun goToHome() {
        val user = authHelper.getCurrentUser()
        if (com.example.btproject2.models.DemoAccountRepository.isAdminEmail(user?.email)) {
            startActivity(Intent(this, AdminPortalActivity::class.java))
        } else {
            startActivity(Intent(this, HomeActivity::class.java))
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("bypass_auth_check", false)) {
            authHelper.logout()
            com.example.btproject2.utils.TreePreferences.clear(this)
            com.example.btproject2.firebase.FirestoreHelper.clearCache()
            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()
        }
    }

    private fun showMessage(title: String, message: String) {
        com.example.btproject2.utils.KinTraceModalDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}