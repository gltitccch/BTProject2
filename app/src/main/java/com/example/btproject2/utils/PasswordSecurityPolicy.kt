package com.example.btproject2.utils

/**
 * Single source of truth for password security policy and real-time entropy scoring across KinTrace.
 * Enforces defense-ready multi-factor password standards to safeguard multi-generational family records.
 */
data class PasswordEvaluation(
    val hasMinLength: Boolean,
    val hasUppercase: Boolean,
    val hasDigit: Boolean,
    val hasSpecialChar: Boolean,
    val score: Int, // 0..4
    val strengthLevel: StrengthLevel,
    val label: String,
    val colorHex: String,
    val isSecured: Boolean,
    val missingCriteriaMessage: String?
) {
    enum class StrengthLevel {
        EMPTY,
        VERY_WEAK,
        WEAK,
        FAIR,
        STRONG,
        IMPENETRABLE
    }
}

object PasswordSecurityPolicy {
    const val MIN_LENGTH = 8

    /**
     * Evaluates real-time criteria and entropy score (0 to 4) for a given password string.
     */
    fun evaluate(password: String): PasswordEvaluation {
        if (password.isEmpty()) {
            return PasswordEvaluation(
                hasMinLength = false,
                hasUppercase = false,
                hasDigit = false,
                hasSpecialChar = false,
                score = 0,
                strengthLevel = PasswordEvaluation.StrengthLevel.EMPTY,
                label = "Empty",
                colorHex = "#94A3B8",
                isSecured = false,
                missingCriteriaMessage = "Password is required."
            )
        }

        val hasMinLength = password.length >= MIN_LENGTH
        val hasUppercase = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        var score = 0
        if (hasMinLength) score++
        if (hasUppercase) score++
        if (hasDigit) score++
        if (hasSpecialChar) score++

        val (level, label, colorHex) = when (score) {
            4 -> Triple(PasswordEvaluation.StrengthLevel.IMPENETRABLE, "🛡️ Impenetrable", "#D4AF37")
            3 -> Triple(PasswordEvaluation.StrengthLevel.STRONG, "Strong", "#22C55E")
            2 -> Triple(PasswordEvaluation.StrengthLevel.FAIR, "Fair", "#EAB308")
            1 -> Triple(PasswordEvaluation.StrengthLevel.WEAK, "Weak", "#F59E0B")
            else -> Triple(PasswordEvaluation.StrengthLevel.VERY_WEAK, "Very Weak", "#EF4444")
        }

        val missingMsg = when {
            !hasMinLength -> "Password must be at least $MIN_LENGTH characters."
            !hasUppercase -> "Password must contain at least one uppercase letter (A-Z)."
            !hasDigit -> "Password must contain at least one number (0-9)."
            !hasSpecialChar -> "Password must contain at least one special symbol (!@#$%)."
            else -> null
        }

        return PasswordEvaluation(
            hasMinLength = hasMinLength,
            hasUppercase = hasUppercase,
            hasDigit = hasDigit,
            hasSpecialChar = hasSpecialChar,
            score = score,
            strengthLevel = level,
            label = label,
            colorHex = colorHex,
            isSecured = (score == 4),
            missingCriteriaMessage = missingMsg
        )
    }

    /**
     * Returns a valid FieldResult only if all 4 security criteria are fully satisfied.
     */
    fun validate(password: String): RegistrationValidator.FieldResult {
        if (password.isEmpty()) {
            return RegistrationValidator.FieldResult.Invalid("Password is required.")
        }
        val evaluation = evaluate(password)
        return if (evaluation.isSecured) {
            RegistrationValidator.FieldResult.Valid
        } else {
            RegistrationValidator.FieldResult.Invalid(evaluation.missingCriteriaMessage ?: "Password does not meet security requirements.")
        }
    }

    /**
     * Confirms that the password and its confirmation match.
     */
    fun validateConfirm(password: String, confirmPassword: String): RegistrationValidator.FieldResult {
        return when {
            confirmPassword.isEmpty() -> RegistrationValidator.FieldResult.Invalid("Please confirm your password.")
            password != confirmPassword -> RegistrationValidator.FieldResult.Invalid("Passwords do not match.")
            else -> RegistrationValidator.FieldResult.Valid
        }
    }
}
