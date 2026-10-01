package com.example.btproject2.utils

import java.util.regex.Pattern

/**
 * Pure Kotlin validation utility for account registration inputs.
 * Free of Android framework dependencies, ensuring fast and robust unit testability.
 */
object RegistrationValidator {

    private val EMAIL_PATTERN: Pattern = Pattern.compile(
        "^[A-Za-z0-9_%+-]+(?:\\.[A-Za-z0-9_%+-]+)*@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$"
    )

    private val NAME_PATTERN: Pattern = Pattern.compile(
        "^[A-Za-zÀ-ÖØ-öø-ÿ' -]+$"
    )

    // Known disposable / temporary email domains to reject burner accounts offline
    private val DISPOSABLE_DOMAINS = setOf(
        "mailinator.com",
        "tempmail.com",
        "10minutemail.com",
        "guerrillamail.com",
        "trashmail.com",
        "sharklasers.com",
        "yopmail.com",
        "getairmail.com",
        "dispostable.com",
        "temp-mail.org",
        "fakeinbox.com",
        "nada.ltd"
    )

    sealed class FieldResult {
        object Valid : FieldResult()
        data class Invalid(val errorMessage: String) : FieldResult()
    }

    data class RegistrationValidationResult(
        val fullNameResult: FieldResult,
        val emailResult: FieldResult,
        val passwordResult: FieldResult,
        val confirmPasswordResult: FieldResult
    ) {
        val isValid: Boolean
            get() = fullNameResult is FieldResult.Valid &&
                    emailResult is FieldResult.Valid &&
                    passwordResult is FieldResult.Valid &&
                    confirmPasswordResult is FieldResult.Valid
    }

    fun validateFullName(fullName: String): FieldResult {
        val trimmed = fullName.trim()
        return when {
            trimmed.isEmpty() -> FieldResult.Invalid("Full name is required.")
            trimmed.length < 2 -> FieldResult.Invalid("Full name must be at least 2 characters.")
            !NAME_PATTERN.matcher(trimmed).matches() -> FieldResult.Invalid("Please enter a valid name (letters and spaces only).")
            else -> FieldResult.Valid
        }
    }

    fun validateEmail(email: String): FieldResult {
        val trimmed = email.trim().lowercase()
        if (trimmed.isEmpty()) {
            return FieldResult.Invalid("Email address is required.")
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return FieldResult.Invalid("Please enter a valid email address.")
        }
        val domain = trimmed.substringAfter("@")
        if (DISPOSABLE_DOMAINS.contains(domain)) {
            return FieldResult.Invalid("Disposable email addresses are not permitted.")
        }
        return FieldResult.Valid
    }

    fun validatePassword(password: String): FieldResult {
        return when {
            password.isEmpty() -> FieldResult.Invalid("Password is required.")
            password.length < 6 -> FieldResult.Invalid("Password must be at least 6 characters.")
            else -> FieldResult.Valid
        }
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): FieldResult {
        return when {
            confirmPassword.isEmpty() -> FieldResult.Invalid("Please confirm your password.")
            password != confirmPassword -> FieldResult.Invalid("Passwords do not match.")
            else -> FieldResult.Valid
        }
    }

    fun validateForm(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): RegistrationValidationResult {
        return RegistrationValidationResult(
            fullNameResult = validateFullName(fullName),
            emailResult = validateEmail(email),
            passwordResult = validatePassword(password),
            confirmPasswordResult = validateConfirmPassword(password, confirmPassword)
        )
    }
}
