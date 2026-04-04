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

    fun logout() {
        auth.signOut()
    }
}