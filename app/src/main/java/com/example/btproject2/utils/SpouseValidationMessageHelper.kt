package com.example.btproject2.utils

import com.example.btproject2.engine.MarriageValidationEngine
import com.example.btproject2.engine.MarriageValidationEngine.RelationshipValidationResult
import com.example.btproject2.engine.MarriageValidationEngine.SpouseValidationFailureReason
import com.example.btproject2.models.Person

/**
 * Helper to generate consistent, user-friendly error messages for spouse relationship validations
 * across all parts of the application (Interactive Tree, Pedigree View, Member Detail, Add/Edit Member,
 * Bulk Import, and Firestore gatekeeping).
 */
object SpouseValidationMessageHelper {

    /**
     * Formats a clear, user-facing error message based on the failed validations in [result].
     *
     * Adheres to the standardized message guidelines:
     * - "Unable to add this person as a spouse because both members do not meet the minimum age requirement."
     * - "Unable to add this person as a spouse because this relationship is not allowed under the current gender relationship rules."
     * - "Unable to add this person as a spouse because one member already has an active spouse relationship."
     * - "Unable to add this person as a spouse because they are directly related by blood."
     */
    fun formatErrorMessage(
        personA: Person,
        personB: Person,
        result: RelationshipValidationResult
    ): String {
        if (result.isAllowed) return ""

        val bulletPoints = mutableListOf<String>()

        for (reason in result.failedValidations) {
            when (reason) {
                SpouseValidationFailureReason.SELF_MARRIAGE -> {
                    bulletPoints.add("Unable to add this person as a spouse because a person cannot marry themselves.")
                }
                SpouseValidationFailureReason.MINIMUM_AGE -> {
                    val ageA = MarriageValidationEngine.calculateAge(personA)
                    val ageB = MarriageValidationEngine.calculateAge(personB)
                    val minAge = MarriageValidationEngine.DEFAULT_MINIMUM_SPOUSE_AGE
                    val isAUnderage = ageA != null && ageA < minAge
                    val isBUnderage = ageB != null && ageB < minAge

                    if (isAUnderage && isBUnderage) {
                        bulletPoints.add("Unable to add this person as a spouse because both members do not meet the minimum age requirement.")
                    } else if (isAUnderage) {
                        val nameA = "${personA.firstName} ${personA.lastName}".trim().ifEmpty { "Person A" }
                        bulletPoints.add("Unable to add this person as a spouse because $nameA does not meet the minimum age requirement.")
                    } else {
                        val nameB = "${personB.firstName} ${personB.lastName}".trim().ifEmpty { "Person B" }
                        bulletPoints.add("Unable to add this person as a spouse because $nameB does not meet the minimum age requirement.")
                    }
                }
                SpouseValidationFailureReason.GENDER_NOT_ALLOWED -> {
                    bulletPoints.add("Unable to add this person as a spouse because this relationship is not allowed under the current gender relationship rules.")
                }
                SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS -> {
                    bulletPoints.add("Unable to add this person as a spouse because one member already has an active spouse relationship.")
                }
                SpouseValidationFailureReason.LINEAL_CONSANGUINITY,
                SpouseValidationFailureReason.SIBLING_CONSANGUINITY,
                SpouseValidationFailureReason.COLLATERAL_CONSANGUINITY_4TH_DEGREE -> {
                    bulletPoints.add("Unable to add this person as a spouse because they are directly related by blood.")
                }
                SpouseValidationFailureReason.PARENT_SPOUSE_CONFLICT -> {
                    bulletPoints.add("Unable to add this person as a spouse because a person cannot be linked as both biological parent and spouse of the same person.")
                }
                SpouseValidationFailureReason.STEP_RELATIONSHIP -> {
                    bulletPoints.add("Unable to add this person as a spouse because step-parents and step-children cannot marry under Philippine Family Code Article 38(3).")
                }
                SpouseValidationFailureReason.AFFINITY_IN_LAW -> {
                    bulletPoints.add("Unable to add this person as a spouse because parents-in-law and children-in-law cannot marry under Philippine Family Code Article 38(4).")
                }
                SpouseValidationFailureReason.IMPOSSIBLE_CYCLE -> {
                    bulletPoints.add("Unable to add this person as a spouse because it would create an impossible cyclical relationship.")
                }
                SpouseValidationFailureReason.MARRIAGE_AFTER_DEATH -> {
                    bulletPoints.add("Unable to add this person as a spouse because the marriage date cannot occur after either spouse's date of death.")
                }
            }
        }

        // Fallback if no specific enum failure was added
        if (bulletPoints.isEmpty()) {
            bulletPoints.add("Unable to add this person as a spouse because: ${result.reason}")
        }

        val mainText = if (bulletPoints.size == 1) {
            bulletPoints.first()
        } else {
            bulletPoints.distinct().joinToString("\n• ", prefix = "• ")
        }

        val legal = if (result.legalBasis.isNotBlank()) "\n\nLegal Basis: ${result.legalBasis}" else ""
        val details = if (result.reason.isNotBlank() && !mainText.contains(result.reason)) "\n\nDetails: ${result.reason}" else ""
        val path = if (result.familyPath.isNotEmpty()) {
            "\n\nConnecting Path: " + result.familyPath.joinToString(" ➔ ") { "${it.firstName} ${it.lastName}" }
        } else ""

        return "$mainText$details$legal$path"
    }

    /**
     * Builds a single primary title/summary sentence for dialogs and toasts.
     */
    fun getPrimaryErrorMessage(
        personA: Person,
        personB: Person,
        result: RelationshipValidationResult
    ): String {
        val firstReason = result.failedValidations.firstOrNull() ?: return result.reason
        return when (firstReason) {
            SpouseValidationFailureReason.SELF_MARRIAGE ->
                "Unable to add this person as a spouse because a person cannot marry themselves."
            SpouseValidationFailureReason.MINIMUM_AGE -> {
                val ageA = MarriageValidationEngine.calculateAge(personA)
                val ageB = MarriageValidationEngine.calculateAge(personB)
                val minAge = MarriageValidationEngine.DEFAULT_MINIMUM_SPOUSE_AGE
                if ((ageA != null && ageA < minAge) && (ageB != null && ageB < minAge)) {
                    "Unable to add this person as a spouse because both members do not meet the minimum age requirement."
                } else {
                    "Unable to add this person as a spouse because a member does not meet the minimum age requirement."
                }
            }
            SpouseValidationFailureReason.GENDER_NOT_ALLOWED ->
                "Unable to add this person as a spouse because this relationship is not allowed under the current gender relationship rules."
            SpouseValidationFailureReason.ACTIVE_SPOUSE_EXISTS ->
                "Unable to add this person as a spouse because one member already has an active spouse relationship."
            SpouseValidationFailureReason.LINEAL_CONSANGUINITY,
            SpouseValidationFailureReason.SIBLING_CONSANGUINITY,
            SpouseValidationFailureReason.COLLATERAL_CONSANGUINITY_4TH_DEGREE ->
                "Unable to add this person as a spouse because they are directly related by blood."
            SpouseValidationFailureReason.PARENT_SPOUSE_CONFLICT ->
                "Unable to add this person as a spouse because a person cannot be linked as both biological parent and spouse of the same person."
            SpouseValidationFailureReason.STEP_RELATIONSHIP ->
                "Unable to add this person as a spouse because of a prohibited step-relationship."
            SpouseValidationFailureReason.AFFINITY_IN_LAW ->
                "Unable to add this person as a spouse because of a prohibited in-law relationship."
            SpouseValidationFailureReason.IMPOSSIBLE_CYCLE ->
                "Unable to add this person as a spouse because it creates an impossible cycle."
            SpouseValidationFailureReason.MARRIAGE_AFTER_DEATH ->
                "Unable to add this person as a spouse because the marriage date cannot occur after either spouse's date of death."
        }
    }
}

