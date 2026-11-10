package com.example.btproject2.utils

import com.example.btproject2.models.Person
import com.example.btproject2.models.TriviaQuestion
import java.util.UUID

/**
 * ClanTriviaEngine:
 * Procedurally generates crash-proof trivia question decks from active family tree records
 * for the KinClash multiplayer party game.
 *
 * Anti-Crash Safety Features:
 * 1. Safe Silhouette Fallback: If an ancestor has no photo, [TriviaQuestion.isSilhouetteMode]
 *    is enabled with 3 progressive clues instead of missing image icons.
 * 2. Small Tree Guard: Trees with fewer than 4 members safely pad with curated heritage
 *    milestone questions instead of throwing IndexOutOfBoundsException.
 * 3. 100% Option Invariant: Every question always has exactly 4 options and a valid [correctOptionIndex].
 */
object ClanTriviaEngine {

    private val PHILIPPINE_HERITAGE_PROVINCES = listOf(
        "Batangas", "Ilocos Sur", "Cebu", "Leyte", "Pangasinan", "Iloilo", "Bulacan", "Laguna"
    )

    private val MONTH_NAMES = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    /**
     * Generates a complete trivia deck of [maxQuestions] by combining live tree [members]
     * with the 220+ Philippine Heritage & Kinship library, prioritizing any [customQuestions]
     * added by the host and applying a true random (RNG) draw with zero duplicates.
     */
    fun generateQuestionDeck(
        members: List<Person>,
        maxQuestions: Int = 10,
        customQuestions: List<TriviaQuestion> = emptyList()
    ): List<TriviaQuestion> {
        val pool = mutableListOf<TriviaQuestion>()

        val cleanMembers = members.filter { it.firstName.isNotBlank() || it.lastName.isNotBlank() }

        // 1. Procedural tree questions
        pool.addAll(generateKinshipQuestions(cleanMembers))
        pool.addAll(generateBirthdayQuestions(cleanMembers))
        pool.addAll(generateRootsQuestions(cleanMembers))
        pool.addAll(generatePortraitOrSilhouetteQuestions(cleanMembers))

        // 2. Curated 220+ Philippine Heritage & Kinship questions
        pool.addAll(PhilippineHeritageQuestionBank.QUESTIONS)

        // 3. Deduplicate questions by prompt text and shuffle via RNG
        val uniquePool = pool.distinctBy { it.prompt.trim().lowercase() }.shuffled()

        // 4. Guaranteed Deck Assembly:
        // Priority: customQuestions (inside jokes) first
        val finalDeck = mutableListOf<TriviaQuestion>()
        finalDeck.addAll(customQuestions)

        val neededRandom = maxQuestions - customQuestions.size
        if (neededRandom > 0) {
            val drawn = uniquePool.filter { q ->
                customQuestions.none { it.prompt.equals(q.prompt, ignoreCase = true) }
            }.take(neededRandom)
            finalDeck.addAll(drawn)
        }

        // Small Tree Guard: If still under maxQuestions, pad with fallback milestones
        var padIndex = 1
        while (finalDeck.size < maxQuestions) {
            finalDeck.add(generateFallbackMilestoneQuestion(cleanMembers, padIndex++))
        }

        return finalDeck.take(max(maxQuestions, customQuestions.size))
    }

    private fun max(a: Int, b: Int): Int = if (a > b) a else b

    private fun generateKinshipQuestions(members: List<Person>): List<TriviaQuestion> {
        val questions = mutableListOf<TriviaQuestion>()
        if (members.size < 2) return questions

        val memberMap = members.associateBy { it.id }

        // Question: "Who is the parent of [Child]?"
        for (child in members) {
            val father = child.fatherId?.let { memberMap[it] }
            val mother = child.motherId?.let { memberMap[it] }
            val parent = father ?: mother ?: continue

            val correctName = parent.displayName()
            val distractors = members
                .filter { it.id != parent.id && it.id != child.id }
                .map { it.displayName() }
                .distinct()
                .shuffled()

            val options = assembleFourOptions(correctName, distractors)
            val correctIdx = options.indexOf(correctName)

            questions.add(
                TriviaQuestion(
                    id = UUID.randomUUID().toString(),
                    prompt = "Who is recorded as the parent of ${child.displayName()} in our clan tree?",
                    category = TriviaQuestion.CATEGORY_KINSHIP,
                    options = options,
                    correctOptionIndex = correctIdx,
                    proofMemberId = parent.id,
                    proofFact = "${parent.displayName()} is recorded as the parent of ${child.displayName()}."
                )
            )
            if (questions.size >= 15) break
        }

        return questions
    }

    private fun generateBirthdayQuestions(members: List<Person>): List<TriviaQuestion> {
        val questions = mutableListOf<TriviaQuestion>()
        if (members.size < 2) return questions

        // Group members by birth month
        val monthGroups = mutableMapOf<Int, MutableList<Person>>()
        for (m in members) {
            val month = parseBirthMonth(m.birthDate) ?: continue
            monthGroups.getOrPut(month) { mutableListOf() }.add(m)
        }

        for ((monthNum, group) in monthGroups) {
            if (group.size >= 2) {
                val p1 = group[0]
                val p2 = group[1]
                val monthName = MONTH_NAMES.getOrElse(monthNum - 1) { "this month" }

                val correctPair = "${p1.displayName()} & ${p2.displayName()}"

                val otherMembers = members.filter { it.id != p1.id && it.id != p2.id }.shuffled()
                val distractor1 = if (otherMembers.size >= 2) "${otherMembers[0].displayName()} & ${otherMembers[1].displayName()}" else "Tito Roberto & Tita Elena"
                val distractor2 = if (otherMembers.size >= 4) "${otherMembers[2].displayName()} & ${otherMembers[3].displayName()}" else "Kuya Noel & Ate Valerie"
                val distractor3 = "Lolo Jose & Lola Carmen"

                val options = assembleFourOptions(correctPair, listOf(distractor1, distractor2, distractor3))

                questions.add(
                    TriviaQuestion(
                        id = UUID.randomUUID().toString(),
                        prompt = "Which two relatives share their birthday month in $monthName?",
                        category = TriviaQuestion.CATEGORY_SHARED_BIRTHDAY,
                        options = options,
                        correctOptionIndex = options.indexOf(correctPair),
                        proofMemberId = p1.id,
                        proofFact = "${p1.displayName()} and ${p2.displayName()} were both born in $monthName."
                    )
                )
                if (questions.size >= 5) break
            }
        }

        return questions
    }

    private fun generateRootsQuestions(members: List<Person>): List<TriviaQuestion> {
        val questions = mutableListOf<TriviaQuestion>()
        val membersWithPlace = members.filter { it.birthPlace.isNotBlank() }

        for (m in membersWithPlace) {
            val correctPlace = m.birthPlace.trim()
            val distractors = (PHILIPPINE_HERITAGE_PROVINCES + membersWithPlace.map { it.birthPlace })
                .filter { !it.equals(correctPlace, ignoreCase = true) }
                .distinct()
                .shuffled()

            val options = assembleFourOptions(correctPlace, distractors)

            questions.add(
                TriviaQuestion(
                    id = UUID.randomUUID().toString(),
                    prompt = "According to our clan records, where was ${m.displayName()} born?",
                    category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
                    options = options,
                    correctOptionIndex = options.indexOf(correctPlace),
                    proofMemberId = m.id,
                    proofFact = "${m.displayName()} was born in $correctPlace."
                )
            )
            if (questions.size >= 15) break
        }

        return questions
    }

    private fun generatePortraitOrSilhouetteQuestions(members: List<Person>): List<TriviaQuestion> {
        val questions = mutableListOf<TriviaQuestion>()
        if (members.isEmpty()) return questions

        for (m in members.shuffled()) {
            val hasPhoto = m.photoUri.isNotBlank() || m.photoBase64.isNotBlank()
            val correctName = m.displayName()
            val distractors = members.filter { it.id != m.id }.map { it.displayName() }.distinct().shuffled()
            val options = assembleFourOptions(correctName, distractors)

            if (hasPhoto) {
                // Photo Mode
                questions.add(
                    TriviaQuestion(
                        id = UUID.randomUUID().toString(),
                        prompt = "Who is the family member shown in this portrait?",
                        category = TriviaQuestion.CATEGORY_VINTAGE_PHOTO,
                        options = options,
                        correctOptionIndex = options.indexOf(correctName),
                        proofMemberId = m.id,
                        proofFact = "${m.displayName()} is verified in the family records.",
                        photoUrl = m.photoUri.ifBlank { m.photoBase64 },
                        isSilhouetteMode = false
                    )
                )
            } else {
                // Safe Silhouette Fallback with 3 progressive clues
                val birthYear = extractYear(m.birthDate)
                val place = m.birthPlace.ifBlank { "the ancestral clan province" }
                val clue1 = if (birthYear != null) "Born in year $birthYear" else "Elder of Branch 1"
                val clue2 = "Recorded roots in $place"
                val clue3 = if (m.isLiving) "Currently living clan member" else "Honored ancestor preserved in our tree"

                questions.add(
                    TriviaQuestion(
                        id = UUID.randomUUID().toString(),
                        prompt = "Who Am I? (Clan Silhouette Challenge)",
                        category = TriviaQuestion.CATEGORY_VINTAGE_PHOTO,
                        options = options,
                        correctOptionIndex = options.indexOf(correctName),
                        proofMemberId = m.id,
                        proofFact = "${m.displayName()} matches all 3 silhouette clues.",
                        fallbackClues = listOf(clue1, clue2, clue3),
                        isSilhouetteMode = true
                    )
                )
            }
            if (questions.size >= 8) break
        }

        return questions
    }

    private fun generateFallbackMilestoneQuestion(members: List<Person>, index: Int): TriviaQuestion {
        val founderName = members.firstOrNull()?.displayName() ?: "Clan Patriarch"
        val totalMembers = members.size

        return when (index % 3) {
            1 -> {
                val correct = "$totalMembers member${if (totalMembers == 1) "" else "s"}"
                val options = assembleFourOptions(correct, listOf("12 members", "25 members", "50 members"))
                TriviaQuestion(
                    id = UUID.randomUUID().toString(),
                    prompt = "How many total members are currently recorded in our KinTrace tree?",
                    category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
                    options = options,
                    correctOptionIndex = options.indexOf(correct),
                    proofMemberId = "",
                    proofFact = "Our tree currently preserves records for $totalMembers clan member(s)."
                )
            }
            2 -> {
                val correct = founderName
                val options = assembleFourOptions(correct, listOf("Dr. Jose Rizal", "Lolo Carlos", "Tito Roberto"))
                TriviaQuestion(
                    id = UUID.randomUUID().toString(),
                    prompt = "Who is the first registered ancestor or profile in this family tree?",
                    category = TriviaQuestion.CATEGORY_KINSHIP,
                    options = options,
                    correctOptionIndex = options.indexOf(correct),
                    proofMemberId = "",
                    proofFact = "$founderName is the root anchor of this family branch."
                )
            }
            else -> {
                val correct = "Preserve clan heritage across generations"
                val options = assembleFourOptions(
                    correct,
                    listOf("Play mobile games online", "Share viral social memes", "Order groceries online")
                )
                TriviaQuestion(
                    id = UUID.randomUUID().toString(),
                    prompt = "What is the primary mission of our KinTrace clan records?",
                    category = TriviaQuestion.CATEGORY_ANCESTOR_ROOTS,
                    options = options,
                    correctOptionIndex = options.indexOf(correct),
                    proofMemberId = "",
                    proofFact = "KinTrace preserves direct ancestral lineage and heritage for future generations."
                )
            }
        }
    }

    /**
     * Assembles a strictly 4-item list containing [correctAnswer] and 3 distinct [distractors],
     * randomly shuffled so the correct answer is not always in the same position.
     */
    private fun assembleFourOptions(correctAnswer: String, distractors: List<String>): List<String> {
        val uniqueDistractors = distractors
            .filter { !it.equals(correctAnswer, ignoreCase = true) }
            .distinct()
            .toMutableList()

        val fallbackFillers = listOf("Lolo Jose Bugarin", "Tita Carmen Santos", "Kuya Noel Millare", "Ate Valerie Ramos")
        for (filler in fallbackFillers) {
            if (uniqueDistractors.size >= 3) break
            if (!filler.equals(correctAnswer, ignoreCase = true) && !uniqueDistractors.contains(filler)) {
                uniqueDistractors.add(filler)
            }
        }

        val fourList = mutableListOf(correctAnswer)
        fourList.addAll(uniqueDistractors.take(3))

        // In case there are still fewer than 4 items
        while (fourList.size < 4) {
            fourList.add("Relative #${fourList.size + 1}")
        }

        return fourList.shuffled()
    }

    private fun parseBirthMonth(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        val parts = dateStr.split("-", "/", " ")
        if (parts.size >= 2) {
            val month = parts[1].toIntOrNull()
            if (month != null && month in 1..12) return month
        }
        return null
    }

    private fun extractYear(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        val parts = dateStr.split("-", "/", " ")
        for (part in parts) {
            if (part.length == 4 && part.toIntOrNull() != null) return part
        }
        return null
    }

    private fun Person.displayName(): String {
        val parts = listOf(firstName, lastName, suffix).filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) parts.joinToString(" ") else "Family Relative"
    }
}
