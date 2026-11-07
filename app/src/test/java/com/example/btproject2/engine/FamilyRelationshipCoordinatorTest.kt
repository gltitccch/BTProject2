package com.example.btproject2.engine

import com.example.btproject2.models.Person
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FamilyRelationshipCoordinatorTest {

    private lateinit var coordinator: FamilyRelationshipCoordinator
    private lateinit var thomas: Person
    private lateinit var sarah: Person
    private lateinit var dorothy: Person
    private lateinit var testMembers: List<Person>

    @Before
    fun setup() {
        coordinator = FamilyRelationshipCoordinator()
        thomas = Person(
            id = "thomas_1",
            firstName = "Thomas",
            lastName = "Malone",
            gender = "Male",
            spouseId = "sarah_1"
        )
        sarah = Person(
            id = "sarah_1",
            firstName = "Sarah",
            lastName = "Jenkins",
            gender = "Female",
            spouseId = "thomas_1"
        )
        dorothy = Person(
            id = "dorothy_1",
            firstName = "Dorothy",
            lastName = "Miller",
            gender = "Female"
        )
        testMembers = listOf(thomas, sarah, dorothy)
        runBlocking {
            coordinator.indexTreeMembers(testMembers)
        }
    }

    @Test
    fun testResolveEligibleMothersFastPrioritizesSpouse() {
        val newChild = Person(id = "", firstName = "Child", lastName = "Malone")
        val mothers = coordinator.resolveEligibleMothersFast(newChild, currentFather = thomas)

        assertEquals(2, mothers.size)
        assertEquals("sarah_1", mothers.first().id) // Linked spouse prioritized first
        assertEquals("dorothy_1", mothers[1].id)
    }

    @Test
    fun testResolveEligibleFathersFastPrioritizesSpouse() {
        val newChild = Person(id = "", firstName = "Child", lastName = "Jenkins")
        val fathers = coordinator.resolveEligibleFathersFast(newChild, currentMother = sarah)

        assertEquals(1, fathers.size)
        assertEquals("thomas_1", fathers.first().id)
    }

    @Test
    fun testExcludesSiblingCoParents() {
        val brother = Person(id = "b1", firstName = "Brother", gender = "Male", fatherId = "dad1", motherId = "mom1")
        val sister = Person(id = "s1", firstName = "Sister", gender = "Female", fatherId = "dad1", motherId = "mom1")
        val unrelatedWoman = Person(id = "w1", firstName = "Alice", gender = "Female")

        runBlocking {
            coordinator.indexTreeMembers(listOf(brother, sister, unrelatedWoman))
        }

        val child = Person(id = "")
        val mothers = coordinator.resolveEligibleMothersFast(child, currentFather = brother)

        assertEquals(1, mothers.size)
        assertEquals("w1", mothers.first().id)
        assertFalse(mothers.any { it.id == "s1" }) // Sister excluded from co-parents
    }

    @Test
    fun testFastPerformanceUnderLargePopulation() {
        val largePopulation = (1..200).map { i ->
            Person(
                id = "p_$i",
                firstName = "Person$i",
                lastName = "Clan",
                gender = if (i % 2 == 0) "Male" else "Female"
            )
        }

        runBlocking {
            coordinator.indexTreeMembers(largePopulation)
        }

        val child = Person(id = "")
        val sampleFather = largePopulation.first { it.gender == "Male" }
        // Warmup JVM JIT before measuring wall-clock duration under parallel Gradle load
        coordinator.resolveEligibleMothersFast(child, currentFather = sampleFather)

        val startTime = System.currentTimeMillis()
        val mothers = coordinator.resolveEligibleMothersFast(child, currentFather = sampleFather)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Resolution must take less than 100ms even for 200 members (was ${elapsed}ms)", elapsed < 100)
        assertEquals(100, mothers.size) // 100 females
    }
}
