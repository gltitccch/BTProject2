package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * ConflictScanner — Scans entire tree for inconsistencies and broken links,
 * producing warm, everyday "Friendly Question" guidance and dynamic multi-choice
 * resolution options for the Step-by-Step Family Helper.
 */
class ConflictScanner {

    enum class ResolutionActionType {
        UNLINK,
        SET_ADOPTIVE,
        RECIPROCATE_MARRIAGE,
        SYNC_STATUS,
        BREAK_LOOP,
        EDIT_PROFILE
    }

    data class ResolutionOption(
        val id: String,
        val title: String,
        val tag: String,
        val tagType: String, // "safe", "legal", "edit"
        val description: String,
        val actionType: ResolutionActionType = ResolutionActionType.UNLINK,
        val targetField: String = "",
        val targetValue: String = ""
    )

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
        val canAutoFix: Boolean = false,
        val options: List<ResolutionOption> = emptyList()
    )

    fun scanAll(allPersons: List<Person>): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        val personById = allPersons.associateBy { it.id.trim().lowercase() }
        fun findPerson(id: String?): Person? =
            id?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()?.let { personById[it] }

        val checkedCoParentPairs = mutableMapOf<String, FamilyLinkValidator.ValidationResult>()
        val checkedSpousePairs = mutableSetOf<String>()

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
                if (findPerson(fid) == null) {
                    val q = "“Who is $first’s father?”"
                    val exp = "The father $first had before was deleted from the tree, so we aren’t sure who to show as $pronounPossessive dad right now."
                    val opts = listOf(
                        ResolutionOption(
                            id = "unlink_father",
                            title = "Leave Father Blank for Now",
                            tag = "Safe Fix",
                            tagType = "safe",
                            description = "Clears the broken father reference so the field is left open cleanly.",
                            actionType = ResolutionActionType.UNLINK,
                            targetField = "fatherId"
                        ),
                        ResolutionOption(
                            id = "pick_father",
                            title = "Pick Existing Member as Dad",
                            tag = "Connect Link",
                            tagType = "legal",
                            description = "Opens the member editor so you can select another clan member as dad.",
                            actionType = ResolutionActionType.EDIT_PROFILE
                        ),
                        ResolutionOption(
                            id = "add_father",
                            title = "Add New Profile as Father",
                            tag = "New Member",
                            tagType = "edit",
                            description = "Creates a brand new profile for $first's actual father.",
                            actionType = ResolutionActionType.EDIT_PROFILE
                        )
                    )
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
                            canAutoFix = true,
                            options = opts
                        )
                    )
                }
            }

            // 2. Broken mother link
            person.motherId?.takeIf { it.isNotBlank() }?.let { mid ->
                if (findPerson(mid) == null) {
                    val q = "“Who is $first’s mother?”"
                    val exp = "The mother $first had before was deleted from the tree, so we aren’t sure who to show as $pronounPossessive mom right now."
                    val opts = listOf(
                        ResolutionOption(
                            id = "unlink_mother",
                            title = "Leave Mother Blank for Now",
                            tag = "Safe Fix",
                            tagType = "safe",
                            description = "Clears the broken mother reference so the field is left open cleanly.",
                            actionType = ResolutionActionType.UNLINK,
                            targetField = "motherId"
                        ),
                        ResolutionOption(
                            id = "pick_mother",
                            title = "Pick Existing Member as Mom",
                            tag = "Connect Link",
                            tagType = "legal",
                            description = "Opens the member editor so you can select another clan member as mom.",
                            actionType = ResolutionActionType.EDIT_PROFILE
                        ),
                        ResolutionOption(
                            id = "add_mother",
                            title = "Add New Profile as Mother",
                            tag = "New Member",
                            tagType = "edit",
                            description = "Creates a brand new profile for $first's actual mother.",
                            actionType = ResolutionActionType.EDIT_PROFILE
                        )
                    )
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
                            canAutoFix = true,
                            options = opts
                        )
                    )
                }
            }

            // 3. Broken spouse link
            person.spouseId?.takeIf { it.isNotBlank() }?.let { sid ->
                if (findPerson(sid) == null) {
                    val q = "“Who is $first married to?”"
                    val exp = "The husband or wife $first had before was deleted from the tree, so we aren’t sure who $pronounSubject is married to."
                    val opts = listOf(
                        ResolutionOption(
                            id = "unlink_spouse",
                            title = "Clear Spouse & Set as Single",
                            tag = "Safe Fix",
                            tagType = "safe",
                            description = "Removes the missing spouse reference and sets marital status to Single.",
                            actionType = ResolutionActionType.UNLINK,
                            targetField = "spouseId"
                        ),
                        ResolutionOption(
                            id = "mark_widowed",
                            title = "Mark as Widowed",
                            tag = "Civil Status",
                            tagType = "legal",
                            description = "Records $first's civil status as Widowed if their spouse has passed away.",
                            actionType = ResolutionActionType.SYNC_STATUS,
                            targetField = "maritalStatus",
                            targetValue = "Widowed"
                        ),
                        ResolutionOption(
                            id = "pick_spouse",
                            title = "Pick Existing Member as Spouse",
                            tag = "Connect Link",
                            tagType = "edit",
                            description = "Opens the member editor to connect $first to an existing spouse profile.",
                            actionType = ResolutionActionType.EDIT_PROFILE
                        )
                    )
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
                            canAutoFix = true,
                            options = opts
                        )
                    )
                }
            }

            // 4. Non-reciprocal spouse (satisfied if spouse lists person as active OR former spouse)
            person.spouseId?.takeIf { it.isNotBlank() }?.let { sid ->
                val spouse = findPerson(sid)
                if (spouse != null && !spouse.hasSpouseId(person.id)) {
                    val spFirst = spouse.firstName.ifBlank { "their spouse" }
                    val q = "“Are $first and $spFirst married to each other?”"
                    val exp = "$first has $spFirst listed as $pronounPossessive spouse, but $spFirst’s page doesn’t show $first as spouse yet."
                    val opts = listOf(
                        ResolutionOption(
                            id = "reciprocate_marriage",
                            title = "Connect Both as Married",
                            tag = "Reciprocate",
                            tagType = "legal",
                            description = "Updates ${spFirst}’s profile to show $first as spouse so both records match in real time.",
                            actionType = ResolutionActionType.RECIPROCATE_MARRIAGE,
                            targetField = "spouseId",
                            targetValue = spouse.id
                        ),
                        ResolutionOption(
                            id = "unlink_spouse",
                            title = "Unlink $spFirst (Keep Single)",
                            tag = "Safe Fix",
                            tagType = "safe",
                            description = "Removes $spFirst from $first's spouse slot so neither is linked as married.",
                            actionType = ResolutionActionType.UNLINK,
                            targetField = "spouseId"
                        ),
                        ResolutionOption(
                            id = "former_spouse",
                            title = "Move to Former Spouse List",
                            tag = "Former Spouse",
                            tagType = "edit",
                            description = "Opens profile editor to record this as a prior marriage (divorced/annulled).",
                            actionType = ResolutionActionType.EDIT_PROFILE
                        )
                    )
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
                            canAutoFix = true,
                            options = opts
                        )
                    )
                }
            }

            // 5. Self-reference
            if (!person.fatherId.isNullOrBlank() && person.fatherId == person.id) {
                val q = "“Who is $first’s father?”"
                val exp = "$first was accidentally selected as $pronounPossessive own father."
                val opts = listOf(
                    ResolutionOption(
                        id = "clear_self_father",
                        title = "Clear Self-Reference Link",
                        tag = "Safe Clean",
                        tagType = "safe",
                        description = "Clears the father field so $first is no longer their own dad.",
                        actionType = ResolutionActionType.UNLINK,
                        targetField = "fatherId"
                    ),
                    ResolutionOption(
                        id = "pick_real_father",
                        title = "Pick Real Father from Tree",
                        tag = "Connect Link",
                        tagType = "legal",
                        description = "Opens member editor to choose another person from the clan tree.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    ),
                    ResolutionOption(
                        id = "edit_father_profile",
                        title = "Open Member Page to Review",
                        tag = "Edit Profile",
                        tagType = "edit",
                        description = "Review all details, generation rank, and branch lineage for $first.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    )
                )
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
                        canAutoFix = true,
                        options = opts
                    )
                )
            }
            if (!person.motherId.isNullOrBlank() && person.motherId == person.id) {
                val q = "“Who is $first’s mother?”"
                val exp = "$first was accidentally selected as $pronounPossessive own mother."
                val opts = listOf(
                    ResolutionOption(
                        id = "clear_self_mother",
                        title = "Clear Self-Reference Link",
                        tag = "Safe Clean",
                        tagType = "safe",
                        description = "Clears the mother field so $first is no longer their own mom.",
                        actionType = ResolutionActionType.UNLINK,
                        targetField = "motherId"
                    ),
                    ResolutionOption(
                        id = "pick_real_mother",
                        title = "Pick Real Mother from Tree",
                        tag = "Connect Link",
                        tagType = "legal",
                        description = "Opens member editor to choose another person from the clan tree.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    ),
                    ResolutionOption(
                        id = "edit_mother_profile",
                        title = "Open Member Page to Review",
                        tag = "Edit Profile",
                        tagType = "edit",
                        description = "Review all details, generation rank, and branch lineage for $first.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    )
                )
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
                        canAutoFix = true,
                        options = opts
                    )
                )
            }
            if (!person.spouseId.isNullOrBlank() && person.spouseId == person.id) {
                val q = "“Who is $first married to?”"
                val exp = "$first was accidentally selected as $pronounPossessive own spouse."
                val opts = listOf(
                    ResolutionOption(
                        id = "clear_self_spouse",
                        title = "Clear Self-Reference Link",
                        tag = "Safe Clean",
                        tagType = "safe",
                        description = "Clears the spouse field so $first is no longer their own spouse.",
                        actionType = ResolutionActionType.UNLINK,
                        targetField = "spouseId"
                    ),
                    ResolutionOption(
                        id = "pick_real_spouse",
                        title = "Pick Real Spouse from Tree",
                        tag = "Connect Link",
                        tagType = "legal",
                        description = "Opens member editor to choose another person from the clan tree.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    ),
                    ResolutionOption(
                        id = "edit_spouse_profile",
                        title = "Open Member Page to Review",
                        tag = "Edit Profile",
                        tagType = "edit",
                        description = "Review all details, generation rank, and branch lineage for $first.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    )
                )
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
                        canAutoFix = true,
                        options = opts
                    )
                )
            }

            // 6. Same father and mother
            if (!person.fatherId.isNullOrBlank() && person.fatherId == person.motherId) {
                val q = "“Who are $first’s parents?”"
                val exp = "The same person was accidentally selected as both $first’s father and mother."
                val opts = listOf(
                    ResolutionOption(
                        id = "keep_father_only",
                        title = "Keep as Father Only",
                        tag = "Safe Fix",
                        tagType = "safe",
                        description = "Preserves this person as father and clears the duplicate mother slot.",
                        actionType = ResolutionActionType.UNLINK,
                        targetField = "motherId"
                    ),
                    ResolutionOption(
                        id = "keep_mother_only",
                        title = "Keep as Mother Only",
                        tag = "Safe Fix",
                        tagType = "safe",
                        description = "Preserves this person as mother and clears the duplicate father slot.",
                        actionType = ResolutionActionType.UNLINK,
                        targetField = "fatherId"
                    ),
                    ResolutionOption(
                        id = "pick_other_parent",
                        title = "Pick Other Parent from Tree",
                        tag = "Edit Profile",
                        tagType = "edit",
                        description = "Opens the member editor to assign the correct second parent.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    )
                )
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
                        canAutoFix = true,
                        options = opts
                    )
                )
            }

            // 7. Parent younger than child
            person.fatherId?.takeIf { it.isNotBlank() }?.let { fid ->
                val father = findPerson(fid)
                if (father != null && father.birthDate.isNotEmpty() && person.birthDate.isNotEmpty()) {
                    val gap = yearsBetween(father.birthDate, person.birthDate)
                    if (gap < 0) {
                        val q = "“Can we check $first’s birth year?”"
                        val exp = "Right now $first’s birth year is earlier than $pronounPossessive dad ${father.firstName}’s birth year."
                        val opts = listOf(
                            ResolutionOption(
                                id = "set_adoptive_father",
                                title = "Mark as Adoptive Father",
                                tag = "Legal Adoption",
                                tagType = "legal",
                                description = "Preserves relationship under Philippine Adoption Law (RA 8552) without biological reproduction limits.",
                                actionType = ResolutionActionType.SET_ADOPTIVE,
                                targetField = "fatherRelationshipType",
                                targetValue = "Adoptive"
                            ),
                            ResolutionOption(
                                id = "fix_birth_year",
                                title = "Fix Birth Year Typo",
                                tag = "Edit Profile",
                                tagType = "edit",
                                description = "Opens the date editor to correct the mistyped birth years.",
                                actionType = ResolutionActionType.EDIT_PROFILE
                            ),
                            ResolutionOption(
                                id = "unlink_father",
                                title = "Unlink Father Link",
                                tag = "Safe Fix",
                                tagType = "safe",
                                description = "Removes the father link so the tree structure remains valid.",
                                actionType = ResolutionActionType.UNLINK,
                                targetField = "fatherId"
                            )
                        )
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
                                canAutoFix = false,
                                options = opts
                            )
                        )
                    }
                }
            }
            person.motherId?.takeIf { it.isNotBlank() }?.let { mid ->
                val mother = findPerson(mid)
                if (mother != null && mother.birthDate.isNotEmpty() && person.birthDate.isNotEmpty()) {
                    val gap = yearsBetween(mother.birthDate, person.birthDate)
                    if (gap < 0) {
                        val q = "“Can we check $first’s birth year?”"
                        val exp = "Right now $first’s birth year is earlier than $pronounPossessive mom ${mother.firstName}’s birth year."
                        val opts = listOf(
                            ResolutionOption(
                                id = "set_adoptive_mother",
                                title = "Mark as Adoptive Mother",
                                tag = "Legal Adoption",
                                tagType = "legal",
                                description = "Preserves relationship under Philippine Adoption Law (RA 8552) without biological reproduction limits.",
                                actionType = ResolutionActionType.SET_ADOPTIVE,
                                targetField = "motherRelationshipType",
                                targetValue = "Adoptive"
                            ),
                            ResolutionOption(
                                id = "fix_birth_year",
                                title = "Fix Birth Year Typo",
                                tag = "Edit Profile",
                                tagType = "edit",
                                description = "Opens the date editor to correct the mistyped birth years.",
                                actionType = ResolutionActionType.EDIT_PROFILE
                            ),
                            ResolutionOption(
                                id = "unlink_mother",
                                title = "Unlink Mother Link",
                                tag = "Safe Fix",
                                tagType = "safe",
                                description = "Removes the mother link so the tree structure remains valid.",
                                actionType = ResolutionActionType.UNLINK,
                                targetField = "motherId"
                            )
                        )
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
                                canAutoFix = false,
                                options = opts
                            )
                        )
                    }
                }
            }

            // 8. Status mismatch
            if (person.maritalStatus == "Married" && person.spouseId.isNullOrBlank()) {
                val q = "“Who is $first married to?”"
                val exp = "It says $first is Married, but $pronounPossessive wife or husband hasn’t been picked yet."
                val opts = listOf(
                    ResolutionOption(
                        id = "set_single",
                        title = "Update Status to Single",
                        tag = "Safe Fix",
                        tagType = "safe",
                        description = "Changes civil status to Single so it matches having no active spouse.",
                        actionType = ResolutionActionType.SYNC_STATUS,
                        targetField = "maritalStatus",
                        targetValue = "Single"
                    ),
                    ResolutionOption(
                        id = "pick_spouse",
                        title = "Pick Spouse from Tree",
                        tag = "Connect Link",
                        tagType = "legal",
                        description = "Select their husband or wife from the clan tree.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    ),
                    ResolutionOption(
                        id = "set_widowed",
                        title = "Mark as Widowed",
                        tag = "Civil Status",
                        tagType = "edit",
                        description = "If their spouse passed away, record civil status as Widowed.",
                        actionType = ResolutionActionType.SYNC_STATUS,
                        targetField = "maritalStatus",
                        targetValue = "Widowed"
                    )
                )
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
                        canAutoFix = true,
                        options = opts
                    )
                )
            }
            if (person.maritalStatus == "Single" && !person.spouseId.isNullOrBlank()) {
                val spouse = findPerson(person.spouseId)
                val spName = spouse?.firstName ?: "a spouse"
                val q = "“Is $first currently married to $spName?”"
                val exp = "$first has $spName picked as spouse, but $pronounPossessive civil status still says Single."
                val opts = listOf(
                    ResolutionOption(
                        id = "set_married",
                        title = "Update Status to Married",
                        tag = "Safe Fix",
                        tagType = "legal",
                        description = "Syncs civil status with the active spouse link so both show as Married.",
                        actionType = ResolutionActionType.SYNC_STATUS,
                        targetField = "maritalStatus",
                        targetValue = "Married"
                    ),
                    ResolutionOption(
                        id = "unlink_spouse",
                        title = "Unlink Spouse (Remain Single)",
                        tag = "Separate",
                        tagType = "safe",
                        description = "Removes the spouse connection and keeps civil status as Single.",
                        actionType = ResolutionActionType.UNLINK,
                        targetField = "spouseId"
                    ),
                    ResolutionOption(
                        id = "domestic_partner",
                        title = "Designate as Domestic Partner",
                        tag = "Co-habitation",
                        tagType = "edit",
                        description = "Opens member profile to record common-law / co-habitation without civil marriage.",
                        actionType = ResolutionActionType.EDIT_PROFILE
                    )
                )
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
                        canAutoFix = true,
                        options = opts
                    )
                )
            }

            // 9. Incestuous / Prohibited Co-Parents validation
            if (!person.fatherId.isNullOrEmpty() && !person.motherId.isNullOrEmpty()) {
                val father = findPerson(person.fatherId)
                val mother = findPerson(person.motherId)
                if (father != null && mother != null) {
                    val pairKey = "${father.id.trim().lowercase()}|${mother.id.trim().lowercase()}"
                    val coParentCheck = checkedCoParentPairs.getOrPut(pairKey) {
                        FamilyLinkValidator.validateCoParents(father, mother, allPersons)
                    }
                    if (!coParentCheck.isValid) {
                        val q = "“Are ${father.firstName} and ${mother.firstName} the right parents for $first?”"
                        val exp = "${father.firstName} and ${mother.firstName} are already close blood relatives in the tree, so they can’t be set as co-parents."
                        val opts = listOf(
                            ResolutionOption(
                                id = "unlink_coparent",
                                title = "Unlink ${mother.firstName} as Mother",
                                tag = "Enforce Law",
                                tagType = "safe",
                                description = "Clears one parent link to restore compliance with lineage laws.",
                                actionType = ResolutionActionType.UNLINK,
                                targetField = "motherId"
                            ),
                            ResolutionOption(
                                id = "set_adoptive_child",
                                title = "Designate as Adopted Child",
                                tag = "Legal Adoption",
                                tagType = "legal",
                                description = "Records $first as legally adopted rather than biological issue.",
                                actionType = ResolutionActionType.SET_ADOPTIVE,
                                targetField = "motherRelationshipType",
                                targetValue = "Adoptive"
                            ),
                            ResolutionOption(
                                id = "review_coparents",
                                title = "Open Pedigree to Review",
                                tag = "Inspect",
                                tagType = "edit",
                                description = "Opens tree editor to review parent connections.",
                                actionType = ResolutionActionType.EDIT_PROFILE
                            )
                        )
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
                                canAutoFix = true,
                                options = opts
                            )
                        )
                    }
                }
            }

            // 10. Prohibited Spouse validation (deduplicated per couple pair across active & former spouses)
            person.allSpouseIds().forEach { sid ->
                val spouse = findPerson(sid)
                if (spouse != null) {
                    val coupleKey = listOf(person.id.trim().lowercase(), spouse.id.trim().lowercase()).sorted().joinToString("|")
                    if (checkedSpousePairs.add(coupleKey)) {
                        val sCheck = FamilyLinkValidator.validateSpouse(person, spouse, allPersons)
                        if (!sCheck.isValid) {
                            val q = "“Can we double-check $first and ${spouse.firstName}’s marriage?”"
                            val exp = "$first and ${spouse.firstName} are listed as close blood relatives in the tree, so they shouldn’t be connected as spouses."
                            val opts = listOf(
                                ResolutionOption(
                                    id = "unlink_prohibited_spouse",
                                    title = "Sever Prohibited Marriage Link",
                                    tag = "Enforce Law",
                                    tagType = "safe",
                                    description = "Clears the prohibited marriage link to comply with Philippine Family Code (Art. 37/38).",
                                    actionType = ResolutionActionType.UNLINK,
                                    targetField = "spouseId"
                                ),
                                ResolutionOption(
                                    id = "annotate_foreign",
                                    title = "Mark as Foreign Valid Marriage",
                                    tag = "Foreign Law",
                                    tagType = "legal",
                                    description = "Annotate that marriage was validly celebrated abroad under foreign jurisdiction.",
                                    actionType = ResolutionActionType.EDIT_PROFILE
                                ),
                                ResolutionOption(
                                    id = "review_marriage_path",
                                    title = "Inspect Bloodline Path",
                                    tag = "Inspect",
                                    tagType = "edit",
                                    description = "Opens Relationship Path to verify whether they are actual blood relatives.",
                                    actionType = ResolutionActionType.EDIT_PROFILE
                                )
                            )
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
                                    canAutoFix = true,
                                    options = opts
                                )
                            )
                        }
                    }
                }
            }

            // 11. Parent-Child validity check (only if not already flagged by earlier specific checks)
            person.fatherId?.takeIf { it.isNotBlank() }?.let { fid ->
                val alreadyFlaggedFather = conflicts.any { it.personId == person.id && it.field == "fatherId" }
                val father = findPerson(fid)
                if (!alreadyFlaggedFather && father != null) {
                    val fCheck = FamilyLinkValidator.validateParentChild(
                        parent = father,
                        child = person,
                        role = "father",
                        allPersons = allPersons,
                        checkCoParents = false
                    )
                    if (!fCheck.isValid) {
                        val q = "“Is ${father.firstName} really $first’s father?”"
                        val exp = fCheck.errors.firstOrNull()?.message
                            ?: "${father.firstName}’s birth dates or family connection don’t match being $first’s dad."
                        val opts = listOf(
                            ResolutionOption(
                                id = "unlink_father",
                                title = "Unlink ${father.firstName} as Father",
                                tag = "Safe Fix",
                                tagType = "safe",
                                description = "Clears the father link so the family tree structure stays valid.",
                                actionType = ResolutionActionType.UNLINK,
                                targetField = "fatherId"
                            ),
                            ResolutionOption(
                                id = "set_adoptive_father",
                                title = "Mark as Adoptive Father",
                                tag = "Legal Adoption",
                                tagType = "legal",
                                description = "Preserves ${father.firstName} as father by recording $first as his adopted child (exempt from 12-yr gestation limits under RA 8552 / RA 11642).",
                                actionType = ResolutionActionType.SET_ADOPTIVE,
                                targetField = "fatherRelationshipType",
                                targetValue = "Adoptive"
                            ),
                            ResolutionOption(
                                id = "edit_father_or_child",
                                title = "Fix Birth Year Typo",
                                tag = "Edit Profile",
                                tagType = "edit",
                                description = "Opens the profile form to correct ${father.firstName} or $first's recorded birth year.",
                                actionType = ResolutionActionType.EDIT_PROFILE
                            )
                        )
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
                                canAutoFix = true,
                                options = opts
                            )
                        )
                    }
                }
            }
            person.motherId?.takeIf { it.isNotBlank() }?.let { mid ->
                val alreadyFlaggedMother = conflicts.any { it.personId == person.id && it.field == "motherId" }
                val mother = findPerson(mid)
                if (!alreadyFlaggedMother && mother != null) {
                    val mCheck = FamilyLinkValidator.validateParentChild(
                        parent = mother,
                        child = person,
                        role = "mother",
                        allPersons = allPersons,
                        checkCoParents = false
                    )
                    if (!mCheck.isValid) {
                        val q = "“Is ${mother.firstName} really $first’s mother?”"
                        val exp = mCheck.errors.firstOrNull()?.message
                            ?: "${mother.firstName}’s birth dates or family connection don’t match being $first’s mom."
                        val opts = listOf(
                            ResolutionOption(
                                id = "unlink_mother",
                                title = "Unlink ${mother.firstName} as Mother",
                                tag = "Safe Fix",
                                tagType = "safe",
                                description = "Clears the maternal link so the family tree structure stays valid.",
                                actionType = ResolutionActionType.UNLINK,
                                targetField = "motherId"
                            ),
                            ResolutionOption(
                                id = "set_adoptive_mother",
                                title = "Mark as Adoptive Mother",
                                tag = "Legal Adoption",
                                tagType = "legal",
                                description = "Preserves ${mother.firstName} as mother by recording $first as her adopted daughter (exempt from 12-yr gestation limits under RA 8552 / RA 11642).",
                                actionType = ResolutionActionType.SET_ADOPTIVE,
                                targetField = "motherRelationshipType",
                                targetValue = "Adoptive"
                            ),
                            ResolutionOption(
                                id = "edit_mother_or_child",
                                title = "Fix Birth Year Typo",
                                tag = "Edit Profile",
                                tagType = "edit",
                                description = "Opens the profile form to correct ${mother.firstName} or $first's recorded birth year.",
                                actionType = ResolutionActionType.EDIT_PROFILE
                            )
                        )
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
                                canAutoFix = true,
                                options = opts
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
