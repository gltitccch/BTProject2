package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * ConflictScanner — Scans entire tree for inconsistencies and broken links,
 * producing warm, everyday "Friendly Question" guidance for the Step-by-Step Family Helper.
 */
class ConflictScanner {

    data class Conflict(
        val type: String,      // "broken_link", "non_reciprocal", "cycle", "age_conflict", "status_mismatch", "prohibited_coparents", "prohibited_spouse", "invalid_parent_child"
        val personId: String,
        val personName: String,
        val description: String,
        val severity: String,  // "error" or "warning"
        val field: String = "",
        val relatedPersonId: String = "",
        val questionHeadline: String = "“Can we check $personName’s family details?”",
        val simpleExplanation: String = description,
        val helperPrompt: String = "💡 What would you like to do for $personName?",
        val primaryActionLabel: String = "✨ Clean up this detail for now",
        val secondaryActionLabel: String = "✎ Open $personName’s page to edit",
        val canAutoFix: Boolean = false
    )

    fun scanAll(allPersons: List<Person>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()

        allPersons.forEach { person ->
            val first = person.firstName.trim().ifBlank { "this member" }
            val name = "${person.firstName} ${person.lastName}".trim().ifBlank { first }
            val pronounPossessive = when {
                person.gender.equals("Male", ignoreCase = true) -> "his"
                person.gender.equals("Female", ignoreCase = true) -> "her"
                else -> "their"
            }
            val pronounSubject = when {
                person.gender.equals("Male", ignoreCase = true) -> "he"
                person.gender.equals("Female", ignoreCase = true) -> "she"
                else -> "they"
            }

            // 1. Broken father link
            person.fatherId?.takeIf { it.isNotBlank() }?.let { fid ->
                if (FamilyLinkValidator.findPersonInList(fid, allPersons) == null) {
                    val q = "“Who is $first’s father?”"
                    val exp = "The father $first had before was deleted from the tree, so we aren’t sure who to show as $pronounPossessive dad right now."
                    conflicts.add(
                        Conflict(
                            type = "broken_link",
                            personId = person.id,
                            personName = name,
                            description = "$q $exp",
                            severity = "error",
                            field = "fatherId",
                            relatedPersonId = fid,
                            questionHeadline = q,
                            simpleExplanation = exp,
                            helperPrompt = "💡 What would you like to do for $first?",
                            primaryActionLabel = "✨ Leave father blank for now (Clean up)",
                            secondaryActionLabel = "✎ Open $first’s page so I can pick $pronounPossessive dad",
                            canAutoFix = true
                        )
                    )
                }
            }

            // 2. Broken mother link
            person.motherId?.takeIf { it.isNotBlank() }?.let { mid ->
                if (FamilyLinkValidator.findPersonInList(mid, allPersons) == null) {
                    val q = "“Who is $first’s mother?”"
                    val exp = "The mother $first had before was deleted from the tree, so we aren’t sure who to show as $pronounPossessive mom right now."
                    conflicts.add(
                        Conflict(
                            type = "broken_link",
                            personId = person.id,
                            personName = name,
                            description = "$q $exp",
                            severity = "error",
                            field = "motherId",
                            relatedPersonId = mid,
                            questionHeadline = q,
                            simpleExplanation = exp,
                            helperPrompt = "💡 What would you like to do for $first?",
                            primaryActionLabel = "✨ Leave mother blank for now (Clean up)",
                            secondaryActionLabel = "✎ Open $first’s page so I can pick $pronounPossessive mom",
                            canAutoFix = true
                        )
                    )
                }
            }

            // 3. Broken spouse link
            person.spouseId?.takeIf { it.isNotBlank() }?.let { sid ->
                if (FamilyLinkValidator.findPersonInList(sid, allPersons) == null) {
                    val q = "“Who is $first married to?”"
                    val exp = "The husband or wife $first had before was deleted from the tree, so we aren’t sure who $pronounSubject is married to."
                    conflicts.add(
                        Conflict(
                            type = "broken_link",
                            personId = person.id,
                            personName = name,
                            description = "$q $exp",
                            severity = "error",
                            field = "spouseId",
                            relatedPersonId = sid,
                            questionHeadline = q,
                            simpleExplanation = exp,
                            helperPrompt = "💡 What would you like to do for $first?",
                            primaryActionLabel = "✨ Leave spouse blank for now (Clean up)",
                            secondaryActionLabel = "✎ Open $first’s page so I can pick $pronounPossessive spouse",
                            canAutoFix = true
                        )
                    )
                }
            }

            // 4. Non-reciprocal spouse
            person.spouseId?.takeIf { it.isNotBlank() }?.let { sid ->
                val spouse = FamilyLinkValidator.findPersonInList(sid, allPersons)
                if (spouse != null && !FamilyLinkValidator.isSameId(spouse.spouseId, person.id)) {
                    val spFirst = spouse.firstName.ifBlank { "their spouse" }
                    val q = "“Are $first and $spFirst married to each other?”"
                    val exp = "$first has $spFirst listed as $pronounPossessive spouse, but $spFirst’s page doesn’t show $first as spouse yet."
                    conflicts.add(
                        Conflict(
                            type = "non_reciprocal",
                            personId = person.id,
                            personName = name,
                            description = "$q $exp",
                            severity = "warning",
                            field = "spouseId",
                            relatedPersonId = spouse.id,
                            questionHeadline = q,
                            simpleExplanation = exp,
                            helperPrompt = "💡 Would you like to connect both of them as married?",
                            primaryActionLabel = "✨ Yes, connect $first and $spFirst as married",
                            secondaryActionLabel = "✎ Open $first’s page to review",
                            canAutoFix = true
                        )
                    )
                }
            }

            // 5. Self-reference
            if (!person.fatherId.isNullOrBlank() && person.fatherId == person.id) {
                val q = "“Who is $first’s father?”"
                val exp = "$first was accidentally selected as $pronounPossessive own father."
                conflicts.add(
                    Conflict(
                        type = "cycle",
                        personId = person.id,
                        personName = name,
                        description = "$q $exp",
                        severity = "error",
                        field = "fatherId",
                        questionHeadline = q,
                        simpleExplanation = exp,
                        helperPrompt = "💡 Let’s clear this mix-up for $first:",
                        primaryActionLabel = "✨ Clear father spot for now",
                        secondaryActionLabel = "✎ Open $first’s page to pick $pronounPossessive dad",
                        canAutoFix = true
                    )
                )
            }
            if (!person.motherId.isNullOrBlank() && person.motherId == person.id) {
                val q = "“Who is $first’s mother?”"
                val exp = "$first was accidentally selected as $pronounPossessive own mother."
                conflicts.add(
                    Conflict(
                        type = "cycle",
                        personId = person.id,
                        personName = name,
                        description = "$q $exp",
                        severity = "error",
                        field = "motherId",
                        questionHeadline = q,
                        simpleExplanation = exp,
                        helperPrompt = "💡 Let’s clear this mix-up for $first:",
                        primaryActionLabel = "✨ Clear mother spot for now",
                        secondaryActionLabel = "✎ Open $first’s page to pick $pronounPossessive mom",
                        canAutoFix = true
                    )
                )
            }
            if (!person.spouseId.isNullOrBlank() && person.spouseId == person.id) {
                val q = "“Who is $first married to?”"
                val exp = "$first was accidentally selected as $pronounPossessive own spouse."
                conflicts.add(
                    Conflict(
                        type = "cycle",
                        personId = person.id,
                        personName = name,
                        description = "$q $exp",
                        severity = "error",
                        field = "spouseId",
                        questionHeadline = q,
                        simpleExplanation = exp,
                        helperPrompt = "💡 Let’s clear this mix-up for $first:",
                        primaryActionLabel = "✨ Clear spouse spot for now",
                        secondaryActionLabel = "✎ Open $first’s page to pick $pronounPossessive spouse",
                        canAutoFix = true
                    )
                )
            }

            // 6. Same father and mother
            if (!person.fatherId.isNullOrBlank() && person.fatherId == person.motherId) {
                val q = "“Who are $first’s parents?”"
                val exp = "The same person was accidentally selected as both $first’s father and mother."
                conflicts.add(
                    Conflict(
                        type = "cycle",
                        personId = person.id,
                        personName = name,
                        description = "$q $exp",
                        severity = "error",
                        field = "motherId",
                        questionHeadline = q,
                        simpleExplanation = exp,
                        helperPrompt = "💡 Let’s clear the duplicate parent spot:",
                        primaryActionLabel = "✨ Clear duplicate mother spot",
                        secondaryActionLabel = "✎ Open $first’s page to choose parents",
                        canAutoFix = true
                    )
                )
            }

            // 7. Parent younger than child
            person.fatherId?.takeIf { it.isNotBlank() }?.let { fid ->
                val father = FamilyLinkValidator.findPersonInList(fid, allPersons)
                if (father != null && father.birthDate.isNotEmpty() && person.birthDate.isNotEmpty()) {
                    val gap = yearsBetween(father.birthDate, person.birthDate)
                    if (gap < 0) {
                        val q = "“Can we check $first’s birth year?”"
                        val exp = "Right now $first’s birth year is earlier than $pronounPossessive dad ${father.firstName}’s birth year."
                        conflicts.add(
                            Conflict(
                                type = "age_conflict",
                                personId = person.id,
                                personName = name,
                                description = "$q $exp",
                                severity = "error",
                                field = "birthDate",
                                relatedPersonId = father.id,
                                questionHeadline = q,
                                simpleExplanation = exp,
                                helperPrompt = "💡 Would you like to check $first’s birth date?",
                                primaryActionLabel = "✎ Open $first’s page to fix birth year",
                                secondaryActionLabel = "✨ Unlink father ${father.firstName} instead",
                                canAutoFix = false
                            )
                        )
                    }
                }
            }
            person.motherId?.takeIf { it.isNotBlank() }?.let { mid ->
                val mother = FamilyLinkValidator.findPersonInList(mid, allPersons)
                if (mother != null && mother.birthDate.isNotEmpty() && person.birthDate.isNotEmpty()) {
                    val gap = yearsBetween(mother.birthDate, person.birthDate)
                    if (gap < 0) {
                        val q = "“Can we check $first’s birth year?”"
                        val exp = "Right now $first’s birth year is earlier than $pronounPossessive mom ${mother.firstName}’s birth year."
                        conflicts.add(
                            Conflict(
                                type = "age_conflict",
                                personId = person.id,
                                personName = name,
                                description = "$q $exp",
                                severity = "error",
                                field = "birthDate",
                                relatedPersonId = mother.id,
                                questionHeadline = q,
                                simpleExplanation = exp,
                                helperPrompt = "💡 Would you like to check $first’s birth date?",
                                primaryActionLabel = "✎ Open $first’s page to fix birth year",
                                secondaryActionLabel = "✨ Unlink mother ${mother.firstName} instead",
                                canAutoFix = false
                            )
                        )
                    }
                }
            }

            // 8. Status mismatch
            if (person.maritalStatus == "Married" && person.spouseId.isNullOrBlank()) {
                val q = "“Who is $first married to?”"
                val exp = "It says $first is Married, but $pronounPossessive wife or husband hasn’t been picked yet."
                conflicts.add(
                    Conflict(
                        type = "status_mismatch",
                        personId = person.id,
                        personName = name,
                        description = "$q $exp",
                        severity = "warning",
                        field = "maritalStatus",
                        questionHeadline = q,
                        simpleExplanation = exp,
                        helperPrompt = "💡 How would you like to answer this?",
                        primaryActionLabel = "✨ Change $first to Single for now",
                        secondaryActionLabel = "✎ Open $first’s page so I can pick $pronounPossessive spouse",
                        canAutoFix = true
                    )
                )
            }
            if (person.maritalStatus == "Single" && !person.spouseId.isNullOrBlank()) {
                val spouse = FamilyLinkValidator.findPersonInList(person.spouseId, allPersons)
                val spName = spouse?.firstName ?: "a spouse"
                val q = "“Is $first currently married to $spName?”"
                val exp = "$first has $spName picked as spouse, but $pronounPossessive civil status still says Single."
                conflicts.add(
                    Conflict(
                        type = "status_mismatch",
                        personId = person.id,
                        personName = name,
                        description = "$q $exp",
                        severity = "warning",
                        field = "maritalStatus",
                        relatedPersonId = person.spouseId.orEmpty(),
                        questionHeadline = q,
                        simpleExplanation = exp,
                        helperPrompt = "💡 How would you like to update $first?",
                        primaryActionLabel = "✨ Update $first’s status to Married",
                        secondaryActionLabel = "✎ Open $first’s page to edit",
                        canAutoFix = true
                    )
                )
            }

            // 9. Incestuous / Prohibited Co-Parents validation
            if (!person.fatherId.isNullOrEmpty() && !person.motherId.isNullOrEmpty()) {
                val father = FamilyLinkValidator.findPersonInList(person.fatherId, allPersons)
                val mother = FamilyLinkValidator.findPersonInList(person.motherId, allPersons)
                if (father != null && mother != null) {
                    val coParentCheck = FamilyLinkValidator.validateCoParents(father, mother, allPersons)
                    if (!coParentCheck.isValid) {
                        val q = "“Are ${father.firstName} and ${mother.firstName} the right parents for $first?”"
                        val exp = "${father.firstName} and ${mother.firstName} are already close blood relatives in the tree, so they can’t be set as co-parents."
                        conflicts.add(
                            Conflict(
                                type = "prohibited_coparents",
                                personId = person.id,
                                personName = name,
                                description = "$q $exp",
                                severity = "error",
                                field = "motherId",
                                relatedPersonId = mother.id,
                                questionHeadline = q,
                                simpleExplanation = exp,
                                helperPrompt = "💡 How would you like to fix $first’s parents?",
                                primaryActionLabel = "✨ Unlink ${mother.firstName} for now",
                                secondaryActionLabel = "✎ Open $first’s page to choose parents",
                                canAutoFix = true
                            )
                        )
                    }
                }
            }

            // 10. Prohibited Spouse validation (deduplicated per couple pair)
            person.spouseId?.takeIf { it.isNotBlank() }?.let { sid ->
                val spouse = FamilyLinkValidator.findPersonInList(sid, allPersons)
                if (spouse != null) {
                    val alreadyHasCoupleConflict = conflicts.any {
                        it.type == "prohibited_spouse" &&
                            ((it.personId == person.id && it.relatedPersonId == spouse.id) ||
                             (it.personId == spouse.id && it.relatedPersonId == person.id))
                    }
                    if (!alreadyHasCoupleConflict) {
                        val sCheck = FamilyLinkValidator.validateSpouse(person, spouse, allPersons)
                        if (!sCheck.isValid) {
                            val q = "“Can we double-check $first and ${spouse.firstName}’s marriage?”"
                            val exp = "$first and ${spouse.firstName} are listed as close blood relatives in the tree, so they shouldn’t be connected as spouses."
                            conflicts.add(
                                Conflict(
                                    type = "prohibited_spouse",
                                    personId = person.id,
                                    personName = name,
                                    description = "$q $exp",
                                    severity = "error",
                                    field = "spouseId",
                                    relatedPersonId = spouse.id,
                                    questionHeadline = q,
                                    simpleExplanation = exp,
                                    helperPrompt = "💡 How would you like to fix this?",
                                    primaryActionLabel = "✨ Unlink spouse ${spouse.firstName} for now",
                                    secondaryActionLabel = "✎ Open $first’s page to review",
                                    canAutoFix = true
                                )
                            )
                        }
                    }
                }
            }

            // 11. Parent-Child validity check (only if not already flagged by earlier specific checks)
            person.fatherId?.takeIf { it.isNotBlank() }?.let { fid ->
                val alreadyFlaggedFather = conflicts.any { it.personId == person.id && it.field == "fatherId" }
                val father = FamilyLinkValidator.findPersonInList(fid, allPersons)
                if (!alreadyFlaggedFather && father != null) {
                    val fCheck = FamilyLinkValidator.validateParentChild(parent = father, child = person, role = "father", allPersons = allPersons)
                    if (!fCheck.isValid) {
                        val q = "“Is ${father.firstName} really $first’s father?”"
                        val exp = fCheck.errors.firstOrNull()?.message
                            ?: "${father.firstName}’s birth dates or family connection don’t match being $first’s dad."
                        conflicts.add(
                            Conflict(
                                type = "invalid_parent_child",
                                personId = person.id,
                                personName = name,
                                description = "$q $exp",
                                severity = "error",
                                field = "fatherId",
                                relatedPersonId = father.id,
                                questionHeadline = q,
                                simpleExplanation = exp,
                                helperPrompt = "💡 How would you like to handle $first’s father?",
                                primaryActionLabel = "✨ Unlink ${father.firstName} as father for now",
                                secondaryActionLabel = "✎ Open $first’s page to edit",
                                canAutoFix = true
                            )
                        )
                    }
                }
            }
            person.motherId?.takeIf { it.isNotBlank() }?.let { mid ->
                val alreadyFlaggedMother = conflicts.any { it.personId == person.id && it.field == "motherId" }
                val mother = FamilyLinkValidator.findPersonInList(mid, allPersons)
                if (!alreadyFlaggedMother && mother != null) {
                    val mCheck = FamilyLinkValidator.validateParentChild(parent = mother, child = person, role = "mother", allPersons = allPersons)
                    if (!mCheck.isValid) {
                        val q = "“Is ${mother.firstName} really $first’s mother?”"
                        val exp = mCheck.errors.firstOrNull()?.message
                            ?: "${mother.firstName}’s birth dates or family connection don’t match being $first’s mom."
                        conflicts.add(
                            Conflict(
                                type = "invalid_parent_child",
                                personId = person.id,
                                personName = name,
                                description = "$q $exp",
                                severity = "error",
                                field = "motherId",
                                relatedPersonId = mother.id,
                                questionHeadline = q,
                                simpleExplanation = exp,
                                helperPrompt = "💡 How would you like to handle $first’s mother?",
                                primaryActionLabel = "✨ Unlink ${mother.firstName} as mother for now",
                                secondaryActionLabel = "✎ Open $first’s page to edit",
                                canAutoFix = true
                            )
                        )
                    }
                }
            }
        }

        return conflicts.distinctBy { "${it.personId}-${it.type}-${it.field}-${it.description}" }
    }

    private fun yearsBetween(earlier: String, later: String): Int {
        val eYr = FamilyLinkValidator.extractYear(earlier)
        val lYr = FamilyLinkValidator.extractYear(later)
        return if (eYr != null && lYr != null) lYr - eYr else 99
    }
}
