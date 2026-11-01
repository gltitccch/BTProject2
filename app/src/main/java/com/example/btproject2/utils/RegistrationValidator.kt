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

    // Curated list of known disposable and temporary email domains to reject burner accounts
    private val DISPOSABLE_DOMAINS = setOf(
        "temp.ph",
        "mailinator.com",
        "tempmail.com",
        "tempmail.net",
        "temp-mail.org",
        "10minutemail.com",
        "guerrillamail.com",
        "guerrillamailblock.com",
        "trashmail.com",
        "sharklasers.com",
        "yopmail.com",
        "getairmail.com",
        "dispostable.com",
        "fakeinbox.com",
        "nada.ltd",
        "burnermail.io",
        "burner.com",
        "dropmail.me",
        "mohmal.com",
        "crazymailing.com",
        "inboxkitten.com",
        "throwawaymail.com",
        "mytemp.email",
        "generator.email",
        "armyspy.com",
        "cuvox.de",
        "dayrep.com",
        "fleckens.hu",
        "gustr.com",
        "jourrapide.com",
        "rhyta.com",
        "superrito.com",
        "teleworm.us",
        "tinemail.com"
    )

    private val EXACT_BURNER_LABELS = setOf(
        "temp", "burner", "fake", "trash", "throwaway", "disposable",
        "dispostable", "fakeinbox", "guerrilla", "mailinator", "yopmail"
    )

    private val COMPOUND_BURNER_KEYWORDS = listOf(
        "tempmail", "temp-mail", "temporarymail", "tempinbox",
        "burnermail", "burner-mail", "fakemail", "fake-mail",
        "trashmail", "trash-mail", "throwawaymail", "throwaway-mail",
        "disposablemail", "disposable-mail", "10minutemail", "10min",
        "guerrillamail", "sharklasers", "inboxkitten", "dropmail",
        "mohmal", "crazymailing", "getnada", "getairmail"
    )

    val RECOMMENDED_DOMAINS = listOf("@gmail.com", "@yahoo.com", "@outlook.com", "@icloud.com")

    /**
     * Checks if a domain is a known burner/disposable service or contains suspicious burner heuristics.
     */
    fun isDisposableDomain(domain: String): Boolean {
        val cleanDomain = domain.trim().lowercase()
        if (cleanDomain.isEmpty()) return false

        // 1. Direct domain or subdomain blacklist match (e.g. "temp.ph", "xyz.mailinator.com")
        if (DISPOSABLE_DOMAINS.any { cleanDomain == it || cleanDomain.endsWith(".$it") }) {
            return true
        }

        // 2. Structural label analysis (checks each dot-separated part of the domain)
        val labels = cleanDomain.split(".")
        return labels.any { label ->
            EXACT_BURNER_LABELS.contains(label) ||
            label.startsWith("temp-") || label.endsWith("-temp") ||
            COMPOUND_BURNER_KEYWORDS.any { compound -> label.contains(compound) }
        }
    }

    sealed class FieldResult {
        object Valid : FieldResult()
        data class Invalid(val errorMessage: String) : FieldResult()
    }

    data class RegistrationValidationResult(
        val fullNameResult: FieldResult,
        val emailResult: FieldResult,
        val passwordResult: FieldResult,
        val confirmPasswordResult: FieldResult,
        val tosResult: FieldResult = FieldResult.Valid
    ) {
        val isValid: Boolean
            get() = fullNameResult is FieldResult.Valid &&
                    emailResult is FieldResult.Valid &&
                    passwordResult is FieldResult.Valid &&
                    confirmPasswordResult is FieldResult.Valid &&
                    tosResult is FieldResult.Valid
    }

    fun validateFullName(fullName: String): FieldResult {
        val trimmed = fullName.trim().replace("\\s+".toRegex(), " ")
        if (trimmed.isEmpty()) {
            return FieldResult.Invalid("Full name is required.")
        }
        if (trimmed.length < 4) {
            return FieldResult.Invalid("Full name must be at least 4 characters.")
        }
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            return FieldResult.Invalid("Please enter a valid name (letters and spaces only).")
        }
        val parts = trimmed.split(" ").filter { it.isNotBlank() }
        if (parts.size < 2) {
            return FieldResult.Invalid("Please enter both your first and last name.")
        }
        if (parts.any { it.length < 2 }) {
            return FieldResult.Invalid("Each name must be at least 2 characters.")
        }
        return FieldResult.Valid
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
        if (isDisposableDomain(domain)) {
            return FieldResult.Invalid("Disposable email addresses are not permitted.")
        }
        return FieldResult.Valid
    }

    fun validatePassword(password: String): FieldResult {
        return PasswordSecurityPolicy.validate(password)
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): FieldResult {
        return PasswordSecurityPolicy.validateConfirm(password, confirmPassword)
    }

    fun validateTermsOfService(accepted: Boolean): FieldResult {
        return if (accepted) {
            FieldResult.Valid
        } else {
            FieldResult.Invalid("You must read and agree to the Terms of Service and Clan Stewardship Policy.")
        }
    }

    fun validateForm(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        tosAccepted: Boolean = true
    ): RegistrationValidationResult {
        return RegistrationValidationResult(
            fullNameResult = validateFullName(fullName),
            emailResult = validateEmail(email),
            passwordResult = validatePassword(password),
            confirmPasswordResult = validateConfirmPassword(password, confirmPassword),
            tosResult = validateTermsOfService(tosAccepted)
        )
    }
}
