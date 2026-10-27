package com.example.btproject2.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthHelper {

    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    fun register(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.let { onSuccess(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun login(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                result.user?.let { onSuccess(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun sendPasswordResetEmail(
        email: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateDisplayName(
        displayName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onFailure(Exception("No authenticated user"))
            return
        }
        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun updatePassword(
        newPassword: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onFailure(Exception("No authenticated user"))
            return
        }
        user.updatePassword(newPassword)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun sendEmailVerification(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onFailure(Exception("No authenticated user"))
            return
        }
        user.sendEmailVerification()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun reloadUser(
        onComplete: (FirebaseUser?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(null)
            return
        }
        user.reload().addOnCompleteListener {
            onComplete(auth.currentUser)
        }
    }

    fun isEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified == true
    }

    fun logout() {
        auth.signOut()
    }

    /**
     * Unified single source of truth for completely ending a user session globally.
     * Clears local tree preferences, flushes Firestore in-memory cache, halts active
     * real-time synchronizer listeners, resets testing buses, signs out Firebase Auth,
     * and cleanly navigates to LoginActivity with NEW_TASK/CLEAR_TASK flags and bypass_auth_check.
     */
    fun performGlobalLogout(activity: android.app.Activity) {
        logout()
        com.example.btproject2.utils.TreePreferences.clear(activity)
        com.example.btproject2.firebase.FirestoreHelper.clearCache()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().stopRealtimeListener()
        com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().resetForTesting()

        val intent = android.content.Intent(activity, com.example.btproject2.ui.activities.LoginActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("bypass_auth_check", true)
        }
        activity.startActivity(intent)
        activity.finishAffinity()
    }

    /**
     * Permanently deletes the current Firebase Auth user credentials.
     */
    fun deleteCurrentUser(
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val user = auth.currentUser
        if (user != null) {
            user.delete()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        } else {
            onSuccess()
        }
    }

    /**
     * Silently inspects the current Firebase OAuth 2.0 / RS256 JWT token, decodes its
     * cryptographic claims via [com.example.btproject2.utils.JwtSessionVerifier], and
     * automatically rotates the token in the background if fewer than 5 minutes remain.
     */
    fun verifyActiveJwtSession(
        forceRefresh: Boolean = false,
        onResult: (com.example.btproject2.utils.JwtTokenSummary) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(com.example.btproject2.utils.JwtSessionVerifier.verifyAndDecodeJwt(""))
            return
        }
        user.getIdToken(forceRefresh)
            .addOnSuccessListener { result ->
                val rawToken = result.token.orEmpty()
                val summary = com.example.btproject2.utils.JwtSessionVerifier.verifyAndDecodeJwt(rawToken)
                if (!forceRefresh && summary.shouldRotateSoon) {
                    user.getIdToken(true)
                        .addOnSuccessListener { refreshed ->
                            onResult(
                                com.example.btproject2.utils.JwtSessionVerifier.verifyAndDecodeJwt(
                                    refreshed.token.orEmpty()
                                )
                            )
                        }
                        .addOnFailureListener { onResult(summary) }
                } else {
                    onResult(summary)
                }
            }
            .addOnFailureListener {
                onResult(com.example.btproject2.utils.JwtSessionVerifier.verifyAndDecodeJwt(""))
            }
    }

    /**
     * Verifies whether an account has already been created in KinTrace ("Create Account"),
     * whether its password is valid, and whether its email address is verified before allowing
     * it to be saved as a 1-Tap Vault Keycard.
     */
    fun verifyAccountForKeycard(
        holderName: String,
        email: String,
        password: String,
        onResult: (com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome) -> Unit
    ) {
        val cleanEmail = email.trim()
        auth.signInWithEmailAndPassword(cleanEmail, password)
            .addOnSuccessListener { result ->
                val signedInUser = result.user
                if (signedInUser == null) {
                    auth.signOut()
                    onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered(cleanEmail))
                    return@addOnSuccessListener
                }

                signedInUser.reload().addOnCompleteListener {
                    val refreshedUser = auth.currentUser ?: signedInUser
                    val isVerified = refreshedUser.isEmailVerified ||
                        com.example.btproject2.models.DemoAccountRepository.isDemoEmail(cleanEmail) ||
                        com.example.btproject2.models.DemoAccountRepository.isAdminEmail(cleanEmail)
                    val resolvedName = holderName.trim().ifEmpty {
                        refreshedUser.displayName?.takeIf { it.isNotBlank() } ?: cleanEmail.substringBefore("@")
                    }
                    // Sign out probe session so LoginActivity remains clean until 1-Tap Login is clicked
                    auth.signOut()

                    if (isVerified) {
                        onResult(
                            com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.Verified(
                                resolvedName = resolvedName,
                                email = cleanEmail
                            )
                        )
                    } else {
                        onResult(
                            com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.EmailNotVerified(
                                email = cleanEmail
                            )
                        )
                    }
                }
            }
            .addOnFailureListener { error ->
                if (error is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                    onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered(cleanEmail))
                    return@addOnFailureListener
                }

                val msg = error.message?.lowercase().orEmpty()
                if (msg.contains("wrong password") || msg.contains("invalid_login_credentials")) {
                    // Check Firestore 'users' collection to see if the email was registered in 'Create Account'
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .whereEqualTo("email", cleanEmail)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) {
                                onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.WrongPassword(cleanEmail))
                            } else {
                                onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered(cleanEmail))
                            }
                        }
                        .addOnFailureListener {
                            onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered(cleanEmail))
                        }
                } else {
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .whereEqualTo("email", cleanEmail)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) {
                                onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.WrongPassword(cleanEmail))
                            } else {
                                onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered(cleanEmail))
                            }
                        }
                        .addOnFailureListener {
                            onResult(com.example.btproject2.utils.KeycardAccountVerifier.VerificationOutcome.NotRegistered(cleanEmail))
                        }
                }
            }
    }
}
