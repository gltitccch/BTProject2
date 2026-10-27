package com.example.btproject2.utils

import com.example.btproject2.models.DemoAccount

/**
 * Single source of truth for validating and verifying an account before it can be
 * minted into a 1-Tap Vault Keycard inside a binder folder.
 *
 * Enforces:
 * 1. App registration input restrictions (valid email, no disposable burner domains,
 *    valid holder label, password minimum length, no duplicate email in the same folder).
 * 2. Database & Firebase Auth registration verification (must already be created in "Create Account").
 * 3. Email verification enforcement (must have verified email inbox before saving to keycard).
 */
object KeycardAccountVerifier {

    sealed class VerificationOutcome {
        /**
         * Account is registered in KinTrace, password is correct, and email is verified.
         */
        data class Verified(
            val resolvedName: String,
            val email: String
        ) : VerificationOutcome()

        /**
         * Account has NOT been created in KinTrace ("Create Account") yet.
         */
        data class NotRegistered(
            val email: String,
            val title: String = "Account Not Registered in KinTrace",
            val message: String = "The account \"$email\" has not been created in KinTrace yet.\n\nPlease create this account first in \"Create Account\" and verify your email address before adding it as a 1-Tap Keycard."
        ) : VerificationOutcome()

        /**
         * Account was created in "Create Account", but the user has not verified their email link yet.
         */
        data class EmailNotVerified(
            val email: String,
            val title: String = "Account Email Not Verified Yet",
            val message: String = "The account \"$email\" is registered in KinTrace, but its email address has not been verified yet.\n\nPlease open your email inbox and click the verification link first before adding this account to a 1-Tap Keycard."
        ) : VerificationOutcome()

        /**
         * Account exists in KinTrace, but the password entered does not match.
         */
        data class WrongPassword(
            val email: String,
            val title: String = "Incorrect Account Password",
            val message: String = "The password entered for \"$email\" does not match the registered account password. Please enter the correct password for this account."
        ) : VerificationOutcome()

        /**
         * Input failed pre-flight validation (e.g. invalid email, burner domain, duplicate in folder).
         */
        data class InvalidInput(
            val field: String,
            val errorMessage: String
        ) : VerificationOutcome()
    }

    /**
     * Synchronous pre-flight validation checking app account creation restrictions
     * before querying Firebase Auth & Cloud Firestore.
     */
    fun validatePreFlightInputs(
        holderName: String,
        email: String,
        password: String,
        existingFolderAccounts: List<DemoAccount>
    ): VerificationOutcome.InvalidInput? {
        val cleanName = holderName.trim()
        if (cleanName.isEmpty() || cleanName.length < 2) {
            return VerificationOutcome.InvalidInput(
                field = "name",
                errorMessage = "Please enter an Account Label or Holder Name (at least 2 characters)."
            )
        }

        val emailValidation = RegistrationValidator.validateEmail(email)
        if (emailValidation is RegistrationValidator.FieldResult.Invalid) {
            return VerificationOutcome.InvalidInput(
                field = "email",
                errorMessage = emailValidation.errorMessage
            )
        }

        val cleanEmail = email.trim()
        if (existingFolderAccounts.any { it.email.equals(cleanEmail, ignoreCase = true) }) {
            return VerificationOutcome.InvalidInput(
                field = "email",
                errorMessage = "A keycard for \"$cleanEmail\" already exists inside this folder."
            )
        }

        if (password.isEmpty()) {
            return VerificationOutcome.InvalidInput(
                field = "password",
                errorMessage = "Account password is required."
            )
        }

        if (password.length < 6) {
            return VerificationOutcome.InvalidInput(
                field = "password",
                errorMessage = "Password must be at least 6 characters."
            )
        }

        return null
    }
}
