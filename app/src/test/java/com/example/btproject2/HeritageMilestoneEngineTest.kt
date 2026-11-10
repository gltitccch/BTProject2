package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.utils.HeritageMilestoneEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class HeritageMilestoneEngineTest {

    private fun createCalendar(year: Int, month: Int, day: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    @Test
    fun testFormatOrdinal() {
        assertEquals("1st", HeritageMilestoneEngine.formatOrdinal(1))
        assertEquals("2nd", HeritageMilestoneEngine.formatOrdinal(2))
        assertEquals("3rd", HeritageMilestoneEngine.formatOrdinal(3))
        assertEquals("4th", HeritageMilestoneEngine.formatOrdinal(4))
        assertEquals("11th", HeritageMilestoneEngine.formatOrdinal(11))
        assertEquals("12th", HeritageMilestoneEngine.formatOrdinal(12))
        assertEquals("13th", HeritageMilestoneEngine.formatOrdinal(13))
        assertEquals("21st", HeritageMilestoneEngine.formatOrdinal(21))
        assertEquals("22nd", HeritageMilestoneEngine.formatOrdinal(22))
        assertEquals("24th", HeritageMilestoneEngine.formatOrdinal(24))
        assertEquals("50th", HeritageMilestoneEngine.formatOrdinal(50))
    }

    @Test
    fun testLivingBirthdayDetection() {
        val carla = Person(
            id = "p_carla",
            firstName = "Carla",
            lastName = "Bugarin",
            birthDate = "2000-11-09",
            isLiving = true,
            lineageRole = "1st Cousin"
        )

        // Today is Nov 9, 2024
        val today = createCalendar(2024, Calendar.NOVEMBER, 9)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(carla), today)

        assertEquals(1, milestones.size)
        val m = milestones[0]
        assertEquals(HeritageMilestoneEngine.MilestoneCategory.BIRTHDAY, m.category)
        assertEquals("p_carla", m.personId)
        assertEquals(24, m.milestoneYears)
        assertTrue(m.eventTitle.contains("Carla's 24th Birthday"))
    }

    @Test
    fun testMilestoneDebut18thCelebration() {
        val debutante = Person(
            id = "p_ana",
            firstName = "Ana",
            lastName = "Bugarin",
            birthDate = "2006-11-09",
            isLiving = true
        )

        val today = createCalendar(2024, Calendar.NOVEMBER, 9)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(debutante), today)

        assertEquals(1, milestones.size)
        assertTrue(milestones[0].isMajorJubilee)
        assertTrue(milestones[0].eventSubtitle.contains("18th Debut"))
    }

    @Test
    fun testDeceasedPersonDoesNotTriggerBirthday() {
        val lolo = Person(
            id = "p_lolo",
            firstName = "Juan",
            lastName = "Bugarin",
            birthDate = "1938-11-09",
            deathDate = "2021-05-15",
            isLiving = false
        )

        val today = createCalendar(2024, Calendar.NOVEMBER, 9)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(lolo), today)

        // Birthday should be ignored for deceased ancestors
        assertEquals(0, milestones.size)
    }

    @Test
    fun testBabangLuksaFirstYearDeathAnniversary() {
        val lola = Person(
            id = "p_lola",
            firstName = "Elena",
            lastName = "Bugarin",
            birthDate = "1942-03-10",
            deathDate = "2023-11-09",
            isLiving = false
        )

        val today = createCalendar(2024, Calendar.NOVEMBER, 9)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(lola), today)

        assertEquals(1, milestones.size)
        val m = milestones[0]
        assertEquals(HeritageMilestoneEngine.MilestoneCategory.MEMORIAL, m.category)
        assertTrue(m.isBabangLuksa)
        assertTrue(m.isMajorJubilee)
        assertTrue(m.eventTitle.contains("Babang Luksa"))
    }

    @Test
    fun testFifthDeathAnniversaryMemorial() {
        val lolo = Person(
            id = "p_juan",
            firstName = "Juan",
            lastName = "Bugarin",
            deathDate = "2019-11-09",
            isLiving = false
        )

        val today = createCalendar(2024, Calendar.NOVEMBER, 9)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(lolo), today)

        assertEquals(1, milestones.size)
        val m = milestones[0]
        assertEquals(HeritageMilestoneEngine.MilestoneCategory.MEMORIAL, m.category)
        assertEquals(5, m.milestoneYears)
        assertFalse(m.isBabangLuksa)
        assertTrue(m.isMajorJubilee)
        assertTrue(m.eventTitle.contains("5th Death Anniversary"))
    }

    @Test
    fun testGoldenWeddingAnniversaryDeduplication() {
        val husband = Person(
            id = "p_pedro",
            firstName = "Pedro",
            lastName = "Santos",
            spouseId = "p_teresa",
            marriageDate = "1974-11-09",
            isLiving = true
        )
        val wife = Person(
            id = "p_teresa",
            firstName = "Teresa",
            lastName = "Santos",
            spouseId = "p_pedro",
            marriageDate = "1974-11-09",
            isLiving = true
        )

        val today = createCalendar(2024, Calendar.NOVEMBER, 9)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(husband, wife), today)

        // Must generate exactly 1 celebration for the couple, not 2!
        assertEquals(1, milestones.size)
        val m = milestones[0]
        assertEquals(HeritageMilestoneEngine.MilestoneCategory.WEDDING, m.category)
        assertEquals(50, m.milestoneYears)
        assertTrue(m.isMajorJubilee)
        assertTrue(m.eventTitle.contains("Golden 50th"))
        assertTrue(m.personName.contains("Pedro Santos & Teresa Santos"))
    }

    @Test
    fun testUndasAllSoulsRemembrance() {
        val deceasedAncestors = listOf(
            Person(id = "p1", firstName = "Pedro", isLiving = false, deathDate = "1980-01-01"),
            Person(id = "p2", firstName = "Maria", isLiving = false, deathDate = "1995-04-12")
        )

        val nov1 = createCalendar(2024, Calendar.NOVEMBER, 1)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(deceasedAncestors, nov1)

        assertEquals(1, milestones.size)
        assertEquals(HeritageMilestoneEngine.MilestoneCategory.UNDAS_REMEMBRANCE, milestones[0].category)
        assertTrue(milestones[0].eventSubtitle.contains("2 departed ancestors"))
    }

    @Test
    fun testUpcomingMilestonesWithin30Days() {
        val carlaToday = Person(id = "c1", firstName = "Carla", birthDate = "2000-11-09", isLiving = true)
        val juanInThreeDays = Person(id = "j1", firstName = "Juan", deathDate = "2020-11-12", isLiving = false)
        val marcoInNextMonth = Person(id = "m1", firstName = "Marco", birthDate = "2000-12-25", isLiving = true)

        val startCal = createCalendar(2024, Calendar.NOVEMBER, 9)
        val upcoming = HeritageMilestoneEngine.findUpcomingMilestones(
            listOf(carlaToday, juanInThreeDays, marcoInNextMonth),
            withinDays = 10,
            startCal = startCal
        )

        // Only Carla (today, day 0) and Juan (in 3 days) fall within 10 days
        assertEquals(2, upcoming.size)
        assertEquals(0, upcoming[0].daysUntil)
        assertEquals("Carla", upcoming[0].personName.trim())
        assertEquals(3, upcoming[1].daysUntil)
        assertEquals("Juan", upcoming[1].personName.trim())
    }

    @Test
    fun testLeapYearFeb29BirthdateCelebratedOnFeb28InNonLeapYear() {
        val leapBaby = Person(
            id = "p_leap",
            firstName = "Julian",
            lastName = "Bugarin",
            birthDate = "2000-02-29",
            isLiving = true
        )

        // 2023 is not a leap year. On Feb 28, 2023, Julian turns 23!
        val nonLeapFeb28 = createCalendar(2023, Calendar.FEBRUARY, 28)
        val milestones = HeritageMilestoneEngine.findMilestonesForDate(listOf(leapBaby), nonLeapFeb28)

        assertEquals(1, milestones.size)
        assertEquals(23, milestones[0].milestoneYears)
    }

    @Test
    fun testClanQuestMissingPuzzlePieceFallback() {
        val personWithBlankPlace = Person(
            id = "p_quest",
            firstName = "Mateo",
            lastName = "Bugarin",
            birthDate = "1990-05-20",
            birthPlace = "",
            isLiving = true
        )

        // Date where no birthday/memorial/wedding occurs
        val randomDay = createCalendar(2024, Calendar.SEPTEMBER, 15)

        // Without quest fallback -> 0 milestones
        val withoutFallback = HeritageMilestoneEngine.findMilestonesForDate(
            listOf(personWithBlankPlace),
            randomDay,
            allowQuestFallback = false
        )
        assertEquals(0, withoutFallback.size)

        // With quest fallback enabled -> generates Clan Quest
        val withFallback = HeritageMilestoneEngine.findMilestonesForDate(
            listOf(personWithBlankPlace),
            randomDay,
            allowQuestFallback = true
        )
        assertEquals(1, withFallback.size)
        val quest = withFallback[0]
        assertEquals(HeritageMilestoneEngine.MilestoneCategory.CLAN_QUEST, quest.category)
        assertEquals("p_quest", quest.personId)
        assertTrue(quest.eventTitle.contains("Clan Quest: Mateo's Story is Incomplete"))
        assertTrue(quest.eventSubtitle.contains("birthplace is still blank"))
    }
}
