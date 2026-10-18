package com.example.btproject2

import com.example.btproject2.firebase.FirestoreHelper
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1 — locks in the decisions updatePerson makes before its single atomic batch write. */
class PersonUpdatePlanTest {

    private fun p(id: String, spouse: String? = null, marital: String = "Single", living: Boolean = true, tree: String = "T1",
                  deathDate: String = "", deathPlace: String = "") =
        Person(id = id, firstName = id, spouseId = spouse, maritalStatus = marital, isLiving = living, treeId = tree,
            deathDate = deathDate, deathPlace = deathPlace)

    @Test fun spouseSet_singleBecomesMarried() =
        assertEquals("Married", FirestoreHelper.planPersonUpdate(p("a", spouse = "b"), emptyList()).personToSave.maritalStatus)

    @Test fun spouseSet_divorcedStatusKept() =
        assertEquals("Divorced", FirestoreHelper.planPersonUpdate(p("a", spouse = "b", marital = "Divorced"), emptyList()).personToSave.maritalStatus)

    @Test fun vitalFields_livingClearsDeathData() {
        val v = FirestoreHelper.planPersonUpdate(p("a", deathDate = "1990"), emptyList()).vitalUpdates
        assertEquals(true, v["isLiving"]); assertEquals(true, v["living"]); assertEquals(false, v["isDeceased"])
        assertEquals("Living", v["living_status"]); assertEquals("", v["deathDate"]); assertEquals("", v["dateOfDeath"])
    }

    @Test fun vitalFields_deceasedWritesAllLegacyAliases() {
        val v = FirestoreHelper.planPersonUpdate(p("a", living = false, deathDate = "1990", deathPlace = "Cebu"), emptyList()).vitalUpdates
        assertEquals("Deceased", v["living_status"]); assertEquals("1990", v["dateOfDeath"]); assertEquals("Cebu", v["placeOfDeath"])
        assertEquals(8, v.size)
    }

    @Test fun deceasedPerson_livingSpouseBecomesWidowed() {
        val plan = FirestoreHelper.planPersonUpdate(p("a", spouse = "b", living = false), listOf(p("b", spouse = "a", marital = "Married")))
        assertEquals("b", plan.partnerStatusUpdate!!.first.id); assertEquals("Widowed", plan.partnerStatusUpdate!!.second)
    }

    @Test fun partnerFoundByReverseLink_whenSpouseIdMissing() {
        val plan = FirestoreHelper.planPersonUpdate(p("a", living = false), listOf(p("b", spouse = "a", marital = "Married")))
        assertEquals("Widowed", plan.partnerStatusUpdate!!.second); assertTrue(plan.hasExSpouse)
    }

    @Test fun noPartnerUpdate_whenStatusAlreadyCorrect() =
        assertNull(FirestoreHelper.planPersonUpdate(p("a", spouse = "b"), listOf(p("b", spouse = "a", marital = "Married"))).partnerStatusUpdate)

    @Test fun noPartnerUpdate_whenPartnerDeceased() =
        assertNull(FirestoreHelper.planPersonUpdate(p("a", spouse = "b", living = false), listOf(p("b", marital = "Married", living = false))).partnerStatusUpdate)

    @Test fun partnerInOtherTreeIgnored() {
        val plan = FirestoreHelper.planPersonUpdate(p("a", living = false), listOf(p("b", spouse = "a", tree = "T2")))
        assertNull(plan.partnerStatusUpdate); assertFalse(plan.hasExSpouse)
    }
}
