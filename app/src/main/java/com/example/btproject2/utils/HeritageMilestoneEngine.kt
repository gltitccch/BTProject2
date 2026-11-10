package com.example.btproject2.utils

import com.example.btproject2.engine.FamilyLinkValidator
import com.example.btproject2.models.Person
import java.util.Calendar

/**
 * HeritageMilestoneEngine — Centralized milestone detection engine for KinTrace.
 *
 * Scans tree members to detect:
 * 1. Living Birthdays & Milestone Jubilees (18th Debut, 60th Senior, 100th Centenarian)
 * 2. Sacred Memorials & Babang Luksa (1st Death Anniversary & annual memorials)
 * 3. Clan Wedding Anniversaries (Silver 25th, Ruby 40th, Golden 50th)
 * 4. Undas / All Souls' Day Remembrance (Nov 1-2)
 *
 * Re-uses existing [Person.birthDate], [Person.deathDate], and [Person.marriageDate] records.
 */
object HeritageMilestoneEngine {

    enum class MilestoneCategory {
        BIRTHDAY,
        MEMORIAL,
        WEDDING,
        UNDAS_REMEMBRANCE,
        CLAN_QUEST
    }

    data class Milestone(
        val category: MilestoneCategory,
        val personId: String,
        val personName: String,
        val photoUri: String = "",
        val photoBase64: String = "",
        val relationshipSubtitle: String = "",
        val eventTitle: String,
        val eventSubtitle: String,
        val milestoneYears: Int,
        val isMajorJubilee: Boolean = false,
        val isBabangLuksa: Boolean = false,
        val secondaryPersonId: String? = null,
        val secondaryPersonName: String? = null,
        val daysUntil: Int = 0
    )

    /**
     * Formats an integer to its English ordinal string (1 -> 1st, 2 -> 2nd, 3 -> 3rd, 24 -> 24th).
     */
    fun formatOrdinal(n: Int): String {
        if (n <= 0) return "$n"
        val remainder100 = n % 100
        if (remainder100 in 11..13) return "${n}th"
        return when (n % 10) {
            1 -> "${n}st"
            2 -> "${n}nd"
            3 -> "${n}rd"
            else -> "${n}th"
        }
    }

    /**
     * Evaluates all milestones occurring on [targetCal] (defaults to current system date).
     */
    fun findMilestonesForDate(
        persons: List<Person>,
        targetCal: Calendar = Calendar.getInstance(),
        allowQuestFallback: Boolean = false
    ): List<Milestone> {
        val targetMonth = targetCal.get(Calendar.MONTH)
        val targetDay = targetCal.get(Calendar.DAY_OF_MONTH)
        val targetYear = targetCal.get(Calendar.YEAR)

        val milestones = mutableListOf<Milestone>()
        val personMap = persons.associateBy { it.id }

        // 1. Undas / All Souls' Day Clan-Wide Remembrance (Nov 1 & Nov 2)
        if (targetMonth == Calendar.NOVEMBER && (targetDay == 1 || targetDay == 2)) {
            val deceasedCount = persons.count { !it.isLiving || it.deathDate.isNotBlank() }
            if (deceasedCount > 0) {
                milestones.add(
                    Milestone(
                        category = MilestoneCategory.UNDAS_REMEMBRANCE,
                        personId = "clan_undas",
                        personName = "All Clan Ancestors",
                        eventTitle = "🕯️ Undas Clan Remembrance",
                        eventSubtitle = "Honoring the blessed memory of $deceasedCount departed ancestors today.",
                        milestoneYears = 0,
                        isMajorJubilee = true
                    )
                )
            }
        }

        // Process individual member dates
        for (person in persons) {
            val fullName = "${person.firstName} ${person.lastName}".trim()

            // A. Birthday Check (Living members)
            if (person.isLiving && person.birthDate.isNotBlank()) {
                val birthCal = FamilyLinkValidator.parseDateToCalendar(person.birthDate)
                if (birthCal != null && isSameDayAndMonth(birthCal, targetMonth, targetDay, targetCal)) {
                    val age = targetYear - birthCal.get(Calendar.YEAR)
                    if (age >= 0) {
                        val isJubilee = age in listOf(1, 18, 21, 50, 60, 75, 80, 90, 100)
                        val jubileeTag = when (age) {
                            18 -> " • 18th Debut Milestone"
                            60 -> " • Senior Citizen Jubilee"
                            100 -> " • Centenarian Milestone"
                            else -> if (isJubilee) " • Golden Milestone" else ""
                        }

                        milestones.add(
                            Milestone(
                                category = MilestoneCategory.BIRTHDAY,
                                personId = person.id,
                                personName = fullName,
                                photoUri = person.photoUri,
                                photoBase64 = person.photoBase64,
                                relationshipSubtitle = person.lineageRole.ifBlank { "Family Member" },
                                eventTitle = "🎉 Today is ${person.firstName}'s ${formatOrdinal(age)} Birthday!",
                                eventSubtitle = "Celebrate with $fullName who turns $age today$jubileeTag.",
                                milestoneYears = age,
                                isMajorJubilee = isJubilee
                            )
                        )
                    }
                }
            }

            // B. Death Anniversary Check (Deceased ancestors)
            val isDeceased = !person.isLiving || person.deathDate.isNotBlank()
            if (isDeceased && person.deathDate.isNotBlank()) {
                val deathCal = FamilyLinkValidator.parseDateToCalendar(person.deathDate)
                if (deathCal != null && isSameDayAndMonth(deathCal, targetMonth, targetDay, targetCal)) {
                    val yearsPassed = targetYear - deathCal.get(Calendar.YEAR)
                    if (yearsPassed >= 1) {
                        val isBabangLuksa = (yearsPassed == 1)
                        val title = if (isBabangLuksa) {
                            "🕊️ Babang Luksa: 1st Death Anniversary of $fullName"
                        } else {
                            "🕯️ In Loving Memory: ${formatOrdinal(yearsPassed)} Death Anniversary of $fullName"
                        }

                        val subtitle = if (isBabangLuksa) {
                            "One year of mourning is completed. Offering our family prayers and mass for $fullName."
                        } else {
                            "Remembering $fullName's enduring legacy and blessings across our clan."
                        }

                        milestones.add(
                            Milestone(
                                category = MilestoneCategory.MEMORIAL,
                                personId = person.id,
                                personName = fullName,
                                photoUri = person.photoUri,
                                photoBase64 = person.photoBase64,
                                relationshipSubtitle = person.lineageRole.ifBlank { "Ancestor" },
                                eventTitle = title,
                                eventSubtitle = subtitle,
                                milestoneYears = yearsPassed,
                                isMajorJubilee = yearsPassed % 5 == 0 || isBabangLuksa,
                                isBabangLuksa = isBabangLuksa
                            )
                        )
                    }
                }
            }

            // C. Wedding Anniversary Check
            val spouseId = person.spouseId
            if (person.marriageDate.isNotBlank() && !spouseId.isNullOrBlank()) {
                // Deduplicate pair by processing only if current person's ID is lexicographically smaller
                if (person.id < spouseId) {
                    val weddingCal = FamilyLinkValidator.parseDateToCalendar(person.marriageDate)
                    if (weddingCal != null && isSameDayAndMonth(weddingCal, targetMonth, targetDay, targetCal)) {
                        val yearsMarried = targetYear - weddingCal.get(Calendar.YEAR)
                        if (yearsMarried >= 1) {
                            val spouse = personMap[spouseId]
                            val spouseName = spouse?.let { "${it.firstName} ${it.lastName}".trim() } ?: "Spouse"
                            val coupleTitle = "$fullName & $spouseName"

                            val jubileeName = when (yearsMarried) {
                                25 -> "Silver 25th"
                                40 -> "Ruby 40th"
                                50 -> "Golden 50th"
                                60 -> "Diamond 60th"
                                else -> "${formatOrdinal(yearsMarried)}"
                            }

                            milestones.add(
                                Milestone(
                                    category = MilestoneCategory.WEDDING,
                                    personId = person.id,
                                    personName = coupleTitle,
                                    photoUri = person.photoUri,
                                    photoBase64 = person.photoBase64,
                                    relationshipSubtitle = "Married Couple",
                                    eventTitle = "💍 $jubileeName Wedding Anniversary!",
                                    eventSubtitle = "Celebrating $coupleTitle on $yearsMarried years of blessed marriage.",
                                    milestoneYears = yearsMarried,
                                    isMajorJubilee = yearsMarried in listOf(25, 40, 50, 60),
                                    secondaryPersonId = spouseId,
                                    secondaryPersonName = spouseName
                                )
                            )
                        }
                    }
                }
            }
        }

        // 4. Clan Quest: Missing Puzzle Piece Fallback (If no celebratory/memorial milestones today)
        if (milestones.isEmpty() && allowQuestFallback) {
            val incompletePerson = persons.firstOrNull { it.birthPlace.isBlank() }
            if (incompletePerson != null) {
                val fullName = "${incompletePerson.firstName} ${incompletePerson.lastName}".trim()
                milestones.add(
                    Milestone(
                        category = MilestoneCategory.CLAN_QUEST,
                        personId = incompletePerson.id,
                        personName = fullName,
                        photoUri = incompletePerson.photoUri,
                        photoBase64 = incompletePerson.photoBase64,
                        relationshipSubtitle = incompletePerson.lineageRole.ifBlank { "Family Member" },
                        eventTitle = "🧩 Clan Quest: ${incompletePerson.firstName}'s Story is Incomplete",
                        eventSubtitle = "Did you know? ${incompletePerson.firstName}'s birthplace is still blank. Ask your family next time you gather so we can complete their branch!",
                        milestoneYears = 0,
                        isMajorJubilee = false
                    )
                )
            }
        }

        return milestones
    }

    /**
     * Returns upcoming milestones within the next [withinDays] (e.g. 30 days) from [startCal],
     * sorted in ascending order of days remaining (0 = today, 1 = tomorrow, etc.).
     */
    fun findUpcomingMilestones(
        persons: List<Person>,
        withinDays: Int = 30,
        startCal: Calendar = Calendar.getInstance()
    ): List<Milestone> {
        val results = mutableListOf<Milestone>()
        val baseCal = startCal.clone() as Calendar
        baseCal.set(Calendar.HOUR_OF_DAY, 0)
        baseCal.set(Calendar.MINUTE, 0)
        baseCal.set(Calendar.SECOND, 0)
        baseCal.set(Calendar.MILLISECOND, 0)

        for (dayOffset in 0..withinDays) {
            val checkCal = baseCal.clone() as Calendar
            checkCal.add(Calendar.DAY_OF_YEAR, dayOffset)

            val dailyMilestones = findMilestonesForDate(persons, checkCal)
            for (m in dailyMilestones) {
                results.add(m.copy(daysUntil = dayOffset))
            }
        }

        return results
    }

    /**
     * Checks if two calendars match month and day-of-month, with leap-year accommodation.
     * (E.g. Feb 29 birthdate celebrated on Feb 28 on non-leap years).
     */
    private fun isSameDayAndMonth(
        eventCal: Calendar,
        targetMonth: Int,
        targetDay: Int,
        targetCal: Calendar
    ): Boolean {
        val evMonth = eventCal.get(Calendar.MONTH)
        val evDay = eventCal.get(Calendar.DAY_OF_MONTH)

        if (evMonth == targetMonth && evDay == targetDay) {
            return true
        }

        // Leap year handling: if born Feb 29 and target is Feb 28 on a non-leap year
        if (evMonth == Calendar.FEBRUARY && evDay == 29) {
            val isTargetLeapYear = isLeapYear(targetCal.get(Calendar.YEAR))
            if (!isTargetLeapYear && targetMonth == Calendar.FEBRUARY && targetDay == 28) {
                return true
            }
        }

        return false
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
}
