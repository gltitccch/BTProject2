package com.example.btproject2

import com.example.btproject2.engine.KinshipTitleHelper
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class KinshipTitleTest {

    private val grandfather = Person(id = "gf", firstName = "Pedro", lastName = "Bugarin", gender = "Male", spouseId = "gm")
    private val grandmother = Person(id = "gm", firstName = "Maria", lastName = "Bugarin", gender = "Female", spouseId = "gf")

    private val father = Person(id = "f", firstName = "Juan", lastName = "Bugarin", gender = "Male", fatherId = "gf", motherId = "gm", spouseId = "m")
    private val mother = Person(id = "m", firstName = "Elena", lastName = "Santos", gender = "Female", spouseId = "f")
    private val uncle = Person(id = "u", firstName = "Carlos", lastName = "Bugarin", gender = "Male", fatherId = "gf", motherId = "gm")
    private val aunt = Person(id = "a", firstName = "Rosa", lastName = "Bugarin", gender = "Female", fatherId = "gf", motherId = "gm")

    private val self = Person(id = "self", firstName = "Renzy", lastName = "Bugarin", gender = "Male", fatherId = "f", motherId = "m", spouseId = "w")
    private val brother = Person(id = "bro", firstName = "Mark", lastName = "Bugarin", gender = "Male", fatherId = "f", motherId = "m")
    private val sister = Person(id = "sis", firstName = "Anna", lastName = "Bugarin", gender = "Female", fatherId = "f", motherId = "m")

    // Half-siblings (sharing only father Juan or mother Elena)
    private val secondWifeOfFather = Person(id = "w2_father", firstName = "Carla", lastName = "Reyes", gender = "Female", spouseId = "f")
    private val halfBrother = Person(id = "half_bro", firstName = "Joshua", lastName = "Bugarin", gender = "Male", fatherId = "f", motherId = "w2_father")
    private val halfSister = Person(id = "half_sis", firstName = "Grace", lastName = "Bugarin", gender = "Female", fatherId = "f", motherId = "w2_father")

    // Step-family
    private val stepmother = Person(id = "step_mom", firstName = "Lorna", lastName = "Tolentino", gender = "Female", spouseId = "f")
    private val stepfather = Person(id = "step_dad", firstName = "Ramon", lastName = "Bautista", gender = "Male", spouseId = "m")
    private val stepbrother = Person(id = "step_bro", firstName = "Paulo", lastName = "Tolentino", gender = "Male", fatherId = "unknown_dad", motherId = "step_mom")
    private val stepsister = Person(id = "step_sis", firstName = "Bea", lastName = "Tolentino", gender = "Female", fatherId = "unknown_dad", motherId = "step_mom")

    private val wife = Person(id = "w", firstName = "Sarah", lastName = "Bugarin", gender = "Female", spouseId = "self")

    private val son = Person(id = "s", firstName = "Leo", lastName = "Bugarin", gender = "Male", fatherId = "self", motherId = "w")
    private val daughter = Person(id = "d", firstName = "Mia", lastName = "Bugarin", gender = "Female", fatherId = "self", motherId = "w")
    private val grandson = Person(id = "gs", firstName = "Lucas", lastName = "Bugarin", gender = "Male", fatherId = "s")

    private val stepson = Person(id = "step_son", firstName = "Ken", lastName = "Dizon", gender = "Male", motherId = "w", fatherId = "ex_husband")

    private val cousin = Person(id = "c", firstName = "David", lastName = "Bugarin", gender = "Male", fatherId = "u")
    private val nephew = Person(id = "n", firstName = "Ethan", lastName = "Bugarin", gender = "Male", fatherId = "bro")

    private val allMembers = listOf(
        grandfather, grandmother, father, mother, uncle, aunt,
        self, brother, sister,
        secondWifeOfFather, halfBrother, halfSister,
        stepmother, stepfather, stepbrother, stepsister,
        wife, son, daughter, grandson, stepson, cousin, nephew
    )

    @Test
    fun testSelf() {
        assertEquals("SELF", KinshipTitleHelper.resolveTitle(self, self, allMembers))
    }

    @Test
    fun testParents() {
        assertEquals("FATHER", KinshipTitleHelper.resolveTitle(father, self, allMembers))
        assertEquals("MOTHER", KinshipTitleHelper.resolveTitle(mother, self, allMembers))
    }

    @Test
    fun testGrandparents() {
        assertEquals("GRANDFATHER", KinshipTitleHelper.resolveTitle(grandfather, self, allMembers))
        assertEquals("GRANDMOTHER", KinshipTitleHelper.resolveTitle(grandmother, self, allMembers))
    }

    @Test
    fun testChildren() {
        assertEquals("SON", KinshipTitleHelper.resolveTitle(son, self, allMembers))
        assertEquals("DAUGHTER", KinshipTitleHelper.resolveTitle(daughter, self, allMembers))
    }

    @Test
    fun testGrandchildren() {
        assertEquals("GRANDSON", KinshipTitleHelper.resolveTitle(grandson, self, allMembers))
    }

    @Test
    fun testFullSiblings() {
        // Rule 1: Share both biological parents -> Full Siblings
        assertEquals("FULL BROTHER", KinshipTitleHelper.resolveTitle(brother, self, allMembers))
        assertEquals("FULL SISTER", KinshipTitleHelper.resolveTitle(sister, self, allMembers))
    }

    @Test
    fun testHalfSiblings() {
        // Rule 2: Share only one biological parent -> Half-Siblings
        assertEquals("HALF-BROTHER", KinshipTitleHelper.resolveTitle(halfBrother, self, allMembers))
        assertEquals("HALF-SISTER", KinshipTitleHelper.resolveTitle(halfSister, self, allMembers))
    }

    @Test
    fun testStepSiblings() {
        // Rule 3: Share no biological parent, but parents are spouses -> Step-Siblings
        // Father Juan is married to Lorna (stepmother). Lorna is Paulo's mother.
        val membersWithStepMomMarriedToFather = allMembers.map {
            if (it.id == "f") it.copy(spouseId = "step_mom") else it
        }
        assertEquals("STEPBROTHER", KinshipTitleHelper.resolveTitle(stepbrother, self, membersWithStepMomMarriedToFather))
        assertEquals("STEPSISTER", KinshipTitleHelper.resolveTitle(stepsister, self, membersWithStepMomMarriedToFather))
    }

    @Test
    fun testStepParents() {
        // Rule 4: Spouse of biological parent -> Step-Parent
        val membersWithFatherMarriedToLorna = allMembers.map {
            if (it.id == "f") it.copy(spouseId = "step_mom") else it
        }
        assertEquals("STEPMOTHER", KinshipTitleHelper.resolveTitle(stepmother, self, membersWithFatherMarriedToLorna))

        val membersWithMotherMarriedToRamon = allMembers.map {
            if (it.id == "m") it.copy(spouseId = "step_dad") else it
        }
        assertEquals("STEPFATHER", KinshipTitleHelper.resolveTitle(stepfather, self, membersWithMotherMarriedToRamon))
    }

    @Test
    fun testStepChildren() {
        assertEquals("STEPSON", KinshipTitleHelper.resolveTitle(stepson, self, allMembers))
    }

    @Test
    fun testAuntAndUncle() {
        assertEquals("UNCLE", KinshipTitleHelper.resolveTitle(uncle, self, allMembers))
        assertEquals("AUNT", KinshipTitleHelper.resolveTitle(aunt, self, allMembers))
    }

    @Test
    fun testNephewAndCousin() {
        assertEquals("NEPHEW", KinshipTitleHelper.resolveTitle(nephew, self, allMembers))
        assertEquals("COUSIN", KinshipTitleHelper.resolveTitle(cousin, self, allMembers))
    }

    @Test
    fun testSpouse() {
        assertEquals("WIFE", KinshipTitleHelper.resolveTitle(wife, self, allMembers))
        assertEquals("HUSBAND", KinshipTitleHelper.resolveTitle(self, wife, allMembers))
    }

    @Test
    fun testFounderFallbackWhenNoFocal() {
        assertEquals("PATRIARCH", KinshipTitleHelper.resolveTitle(grandfather, null, allMembers))
        assertEquals("MATRIARCH", KinshipTitleHelper.resolveTitle(grandmother, null, allMembers))
    }

    @Test
    fun testYoungerAuntAndOlderNieceKinshipResolution() {
        // Grandparents Lucas & Sofia
        val gpFather = Person(id = "gp_f", firstName = "Lucas", lastName = "Santos", gender = "Male", birthDate = "1965-03-15", spouseId = "gp_m")
        val gpMother = Person(id = "gp_m", firstName = "Sofia", lastName = "Santos", gender = "Female", birthDate = "1968-07-20", spouseId = "gp_f")

        // Firstborn daughter Rowena (b. 1986)
        val rowena = Person(id = "rowena", firstName = "Rowena", lastName = "Santos", gender = "Female", birthDate = "1986-04-10", fatherId = "gp_f", motherId = "gp_m", spouseId = "diego")
        val diego = Person(id = "diego", firstName = "Diego", lastName = "Del Rosario", gender = "Male", birthDate = "1984-06-15", spouseId = "rowena")

        // Late-born baby aunt Clarita (b. 2016, age 10) & late-born baby uncle Leo (b. 2018, age 8)
        val claritaAunt = Person(id = "clarita_aunt", firstName = "Clarita", lastName = "Santos", gender = "Female", birthDate = "2016-04-12", fatherId = "gp_f", motherId = "gp_m")
        val leoUncle = Person(id = "leo_uncle", firstName = "Leo", lastName = "Santos", gender = "Male", birthDate = "2018-09-05", fatherId = "gp_f", motherId = "gp_m")

        // Older niece Danielle (b. 2004, age 22) & older nephew Marco (b. 2008, age 18)
        val danielleNiece = Person(id = "danielle_niece", firstName = "Danielle", lastName = "Del Rosario", gender = "Female", birthDate = "2004-06-18", fatherId = "diego", motherId = "rowena")
        val marcoNephew = Person(id = "marco_nephew", firstName = "Marco", lastName = "Del Rosario", gender = "Male", birthDate = "2008-03-24", fatherId = "diego", motherId = "rowena")

        val clan = listOf(gpFather, gpMother, rowena, diego, claritaAunt, leoUncle, danielleNiece, marcoNephew)

        // 1. From Danielle's viewpoint (22yo niece inspecting her 10yo aunt & 8yo uncle)
        assertEquals("AUNT", KinshipTitleHelper.resolveTitle(claritaAunt, danielleNiece, clan))
        assertEquals("UNCLE", KinshipTitleHelper.resolveTitle(leoUncle, danielleNiece, clan))

        // 2. From Clarita's viewpoint (10yo aunt inspecting her 22yo niece & 18yo nephew)
        assertEquals("NIECE", KinshipTitleHelper.resolveTitle(danielleNiece, claritaAunt, clan))
        assertEquals("NEPHEW", KinshipTitleHelper.resolveTitle(marcoNephew, claritaAunt, clan))

        // 3. From Leo's viewpoint (8yo uncle inspecting his 22yo niece & 18yo nephew)
        assertEquals("NIECE", KinshipTitleHelper.resolveTitle(danielleNiece, leoUncle, clan))
        assertEquals("NEPHEW", KinshipTitleHelper.resolveTitle(marcoNephew, leoUncle, clan))
    }
}
