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
}