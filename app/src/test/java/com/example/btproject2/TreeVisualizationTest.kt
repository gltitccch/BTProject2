package com.example.btproject2

import com.example.btproject2.engine.BloodlineTracer
import com.example.btproject2.models.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeVisualizationTest {

    @Test
    fun testPedigreeAncestorExtraction() {
        // Setup direct pedigree ancestry line
        val pGf = Person(id = "pgf", firstName = "Paternal Grandfather")
        val mGm = Person(id = "mgm", firstName = "Maternal Grandmother")
        val father = Person(id = "f", firstName = "Father", fatherId = "pgf")
        val mother = Person(id = "m", firstName = "Mother", motherId = "mgm")
        val child = Person(id = "c", firstName = "Focal Child", fatherId = "f", motherId = "m")

        // Unrelated outsider and sibling of parent
        val outsider = Person(id = "out", firstName = "Outsider")
        val aunt = Person(id = "aunt", firstName = "Aunt", motherId = "mgm")

        val allPersons = listOf(pGf, mGm, father, mother, child, outsider, aunt)
        val byId = allPersons.associateBy { it.id }

        // BFS Pedigree algorithm (same logic used in FamilyTreeView)
        val ancestorSet = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add("c")

        while (queue.isNotEmpty()) {
            val currId = queue.removeFirst()
            if (ancestorSet.add(currId)) {
                val p = byId[currId] ?: continue
                p.fatherId?.let { if (it.isNotEmpty()) queue.add(it) }
                p.motherId?.let { if (it.isNotEmpty()) queue.add(it) }
            }
        }

        // Must include child, father, mother, pgf, mgm
        assertEquals(5, ancestorSet.size)
        assertTrue(ancestorSet.contains("c"))
        assertTrue(ancestorSet.contains("f"))
        assertTrue(ancestorSet.contains("m"))
        assertTrue(ancestorSet.contains("pgf"))
        assertTrue(ancestorSet.contains("mgm"))

        // Must NOT include non-direct ancestors (aunt, outsider)
        assertFalse(ancestorSet.contains("out"))
        assertFalse(ancestorSet.contains("aunt"))
    }

    @Test
    fun testTraceHighlightedPathConstruction() {
        val tracer = BloodlineTracer()

        val grandfather = Person(id = "gf", firstName = "Grandfather")
        val father = Person(id = "f", firstName = "Father", fatherId = "gf")
        val uncle = Person(id = "u", firstName = "Uncle", fatherId = "gf")
        val cousinA = Person(id = "ca", firstName = "Cousin A", fatherId = "f")
        val cousinB = Person(id = "cb", firstName = "Cousin B", fatherId = "u")

        val map = mapOf(
            "gf" to grandfather,
            "f" to father,
            "u" to uncle,
            "ca" to cousinA,
            "cb" to cousinB
        )

        val result = tracer.trace("ca", "cb", map)
        assertTrue(result.isRelated)
        assertEquals("gf", result.commonAncestor?.id)

        // Construct highlight path IDs
        val pathIds = mutableSetOf<String>()
        pathIds.addAll(result.pathFromA.map { it.id })
        result.commonAncestor?.id?.let { pathIds.add(it) }
        pathIds.addAll(result.pathFromB.map { it.id })

        // Full path should contain: ca, f, gf, u, cb
        assertEquals(5, pathIds.size)
        assertTrue(pathIds.contains("ca"))
        assertTrue(pathIds.contains("f"))
        assertTrue(pathIds.contains("gf"))
        assertTrue(pathIds.contains("u"))
        assertTrue(pathIds.contains("cb"))
    }

    @Test
    fun testPedigreeAscendingGenerationTiers() {
        val root = Person(id = "c", firstName = "Child", fatherId = "f", motherId = "m")
        val father = Person(id = "f", firstName = "Father", fatherId = "gf")
        val mother = Person(id = "m", firstName = "Mother")
        val gf = Person(id = "gf", firstName = "Grandfather")

        val map = mapOf("c" to root, "f" to father, "m" to mother, "gf" to gf)

        // Calculate ascending tiers from child (0) upwards
        val genMap = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add("c" to 0)

        while (queue.isNotEmpty()) {
            val (id, gen) = queue.removeFirst()
            if (genMap.containsKey(id)) continue
            genMap[id] = gen
            val p = map[id] ?: continue
            p.fatherId?.let { if (map.containsKey(it)) queue.add(it to gen + 1) }
            p.motherId?.let { if (map.containsKey(it)) queue.add(it to gen + 1) }
        }

        assertEquals(0, genMap["c"])
        assertEquals(1, genMap["f"])
        assertEquals(1, genMap["m"])
        assertEquals(2, genMap["gf"])
    }

    @Test
    fun testTreeDownwardLayoutDeduplicationAndCyclePrevention() {
        data class TestFamilyUnit(
            val id: String,
            val spouseId: String?,
            val generation: Int,
            val children: MutableList<TestFamilyUnit> = mutableListOf()
        )

        fun wouldCauseCycle(parent: TestFamilyUnit, child: TestFamilyUnit): Boolean {
            if (parent == child) return true
            val visited = mutableSetOf<TestFamilyUnit>()
            val queue = ArrayDeque<TestFamilyUnit>()
            queue.add(child)
            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                if (curr == parent) return true
                if (visited.add(curr)) {
                    queue.addAll(curr.children)
                }
            }
            return false
        }

        // Parent coupled unit (Father + Mother)
        val coupledUnit = TestFamilyUnit("father", "mother", 0)
        // Separate single parent unit (e.g. conflicting parent unit)
        val singleUnit = TestFamilyUnit("father", null, 0)
        // Child unit
        val childUnit = TestFamilyUnit("child", null, 1)

        val allUnits = listOf(coupledUnit, singleUnit, childUnit)
        val claimedChildUnits = mutableSetOf<TestFamilyUnit>()

        // Pass 1: Coupled parent unit claims child
        allUnits.filter { it.spouseId != null }.forEach { unit ->
            if (childUnit !in claimedChildUnits && childUnit.generation > unit.generation && !wouldCauseCycle(unit, childUnit)) {
                unit.children.add(childUnit)
                claimedChildUnits.add(childUnit)
            }
        }

        // Pass 2: Single unit tries to claim child
        allUnits.filter { it.spouseId == null }.forEach { unit ->
            if (childUnit !in claimedChildUnits && childUnit.generation > unit.generation && !wouldCauseCycle(unit, childUnit)) {
                unit.children.add(childUnit)
                claimedChildUnits.add(childUnit)
            }
        }

        // Child unit must only be claimed by coupledUnit, NOT duplicated in singleUnit
        assertEquals(1, coupledUnit.children.size)
        assertEquals(0, singleUnit.children.size)
        assertTrue(claimedChildUnits.contains(childUnit))

        // Cycle test: Erroneous cyclic link where parent tries to become child of child
        assertFalse(
            "Child cannot claim parent as child (cycle prevented)",
            childUnit.generation > coupledUnit.generation && !wouldCauseCycle(childUnit, coupledUnit)
        )
    }

    @Test
    fun testComputeGenerationMapBothPedroAndClaraInGeneration1() {
        // Setup Pedro and Clara as spouses and roots (no parents in tree)
        val pedro = Person(id = "pedro", firstName = "Pedro", lastName = "B.", spouseId = "clara")
        val clara = Person(id = "clara", firstName = "Clara", lastName = "B.", spouseId = "pedro")

        // Children of Pedro and Clara
        val renzy = Person(id = "renzy", firstName = "Renzy", fatherId = "pedro", motherId = "clara")
        val luz = Person(id = "luz", firstName = "Luz", fatherId = "pedro", motherId = "clara")
        val johnD = Person(id = "johnD", firstName = "John D", fatherId = "pedro", motherId = "clara")

        // Grandchild
        val ghillain = Person(id = "ghillain", firstName = "Ghillain", motherId = "luz")

        val persons = listOf(pedro, clara, renzy, luz, johnD, ghillain)
        val genMap = com.example.btproject2.ui.activities.FamilyTreeActivity.computeGenerationMap(persons)

        // Both Pedro and Clara MUST be in Generation 1
        assertEquals("Pedro must be Generation 1", 1, genMap["pedro"])
        assertEquals("Clara must be Generation 1", 1, genMap["clara"])

        // Children must be Generation 2
        assertEquals("Renzy must be Generation 2", 2, genMap["renzy"])
        assertEquals("Luz must be Generation 2", 2, genMap["luz"])
        assertEquals("John D must be Generation 2", 2, genMap["johnD"])

        // Grandchild must be Generation 3
        assertEquals("Ghillain must be Generation 3", 3, genMap["ghillain"])
    }

    @Test
    fun testComputeGenerationMapSpouseAlignmentForDescendants() {
        // Pedro & Clara in Gen 1
        val pedro = Person(id = "p1", firstName = "Pedro")
        val clara = Person(id = "p2", firstName = "Clara", spouseId = "p1")

        // Renzy in Gen 2
        val renzy = Person(id = "p3", firstName = "Renzy", fatherId = "p1", motherId = "p2", spouseId = "p4")

        // Renzy's spouse Jane Doe has no parents in tree
        val jane = Person(id = "p4", firstName = "Jane", spouseId = "p3")

        val genMap = com.example.btproject2.ui.activities.FamilyTreeActivity.computeGenerationMap(
            listOf(pedro, clara, renzy, jane)
        )

        assertEquals("Pedro is Gen 1", 1, genMap["p1"])
        assertEquals("Clara is Gen 1", 1, genMap["p2"])
        assertEquals("Renzy is Gen 2", 2, genMap["p3"])
        assertEquals("Jane aligns to spouse Renzy in Gen 2", 2, genMap["p4"])
    }

    @Test
    fun testJoseAsParentOfClaraAndPedroPlacesJoseAboveThem() {
        // Jose Bugarin (Parent of Clara and Pedro)
        val jose = Person(id = "jose", firstName = "Jose", lastName = "Bugarin")

        // Clara and Pedro (Children of Jose, spouses of each other)
        val clara = Person(id = "clara", firstName = "Clara", lastName = "Bugarin", fatherId = "jose", spouseId = "pedro")
        val pedro = Person(id = "pedro", firstName = "Pedro", lastName = "Bugarin", fatherId = "jose", spouseId = "clara")

        // Renzy and Luz (Children of Clara and Pedro)
        val renzy = Person(id = "renzy", firstName = "Renzy", lastName = "Bugarin", fatherId = "pedro", motherId = "clara")
        val luz = Person(id = "luz", firstName = "Luz", lastName = "Bugarin", fatherId = "pedro", motherId = "clara")

        // Ghillain (Child of Luz)
        val ghillain = Person(id = "ghillain", firstName = "Ghillain", lastName = "Bugarin", motherId = "luz")

        val family = listOf(jose, clara, pedro, renzy, luz, ghillain)

        // 1. Interactive Tree 0-based generation mapping
        val gen0Map = com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations0Based(family)

        // Jose MUST be above Clara and Pedro
        assertTrue("Jose must have smaller generation index than Clara", gen0Map["jose"]!! < gen0Map["clara"]!!)
        assertTrue("Jose must have smaller generation index than Pedro", gen0Map["jose"]!! < gen0Map["pedro"]!!)
        assertEquals("Jose must be at generation 0 (highest visual level)", 0, gen0Map["jose"])
        assertEquals("Clara must be at generation 1", 1, gen0Map["clara"])
        assertEquals("Pedro must be at generation 1", 1, gen0Map["pedro"])
        assertEquals("Renzy must be at generation 2", 2, gen0Map["renzy"])
        assertEquals("Luz must be at generation 2", 2, gen0Map["luz"])
        assertEquals("Ghillain must be at generation 3", 3, gen0Map["ghillain"])

        // Siblings Clara and Pedro must share same generation
        assertEquals(gen0Map["clara"], gen0Map["pedro"])
        // Siblings Renzy and Luz must share same generation
        assertEquals(gen0Map["renzy"], gen0Map["luz"])

        // 2. Validate hierarchy rule: parentGeneration < childGeneration for all parent-child relationships
        val isValid0Based = com.example.btproject2.engine.GenerationHierarchyEngine.validateHierarchy(family, gen0Map)
        assertTrue("All parent-child relationships must satisfy parentGeneration < childGeneration", isValid0Based)

        // 3. 1-based generation mapping (used by Insights and FamilyTreeActivity)
        val gen1Map = com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations1Based(family)
        assertEquals(1, gen1Map["jose"])
        assertEquals(2, gen1Map["clara"])
        assertEquals(2, gen1Map["pedro"])
        assertEquals(3, gen1Map["renzy"])
        assertEquals(3, gen1Map["luz"])
        assertEquals(4, gen1Map["ghillain"])

        val isValid1Based = com.example.btproject2.engine.GenerationHierarchyEngine.validateHierarchy(family, gen1Map)
        assertTrue(isValid1Based)
    }

    @Test
    fun testDynamicParentRegistrationReorganizesTreeHierarchyGlobally() {
        // Initially, Clara and Pedro are roots (Gen 0)
        val clara = Person(id = "c", firstName = "Clara", spouseId = "p")
        val pedro = Person(id = "p", firstName = "Pedro", spouseId = "c")
        val renzy = Person(id = "r", firstName = "Renzy", fatherId = "p", motherId = "c")

        val initialTree = listOf(clara, pedro, renzy)
        val initialGenMap = com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations0Based(initialTree)

        assertEquals("Initially Clara is Gen 0", 0, initialGenMap["c"])
        assertEquals("Initially Pedro is Gen 0", 0, initialGenMap["p"])
        assertEquals("Initially Renzy is Gen 1", 1, initialGenMap["r"])

        // Now, Jose is registered as the parent of Clara and Pedro
        val jose = Person(id = "j", firstName = "Jose")
        val updatedClara = clara.copy(fatherId = "j")
        val updatedPedro = pedro.copy(fatherId = "j")

        val updatedTree = listOf(jose, updatedClara, updatedPedro, renzy)
        val updatedGenMap = com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations0Based(updatedTree)

        // System automatically reorganizes: Jose becomes Gen 0, Clara & Pedro shift to Gen 1, Renzy shifts to Gen 2
        assertEquals("Jose becomes Gen 0", 0, updatedGenMap["j"])
        assertEquals("Clara shifts down to Gen 1 (below Jose)", 1, updatedGenMap["c"])
        assertEquals("Pedro shifts down to Gen 1 (below Jose)", 1, updatedGenMap["p"])
        assertEquals("Renzy shifts down to Gen 2 (below Clara & Pedro)", 2, updatedGenMap["r"])

        assertTrue(com.example.btproject2.engine.GenerationHierarchyEngine.validateHierarchy(updatedTree, updatedGenMap))
    }

    @Test
    fun testValidateHierarchyRejectsInvertedGenerations() {
        val parent = Person(id = "parent", firstName = "Parent")
        val child = Person(id = "child", firstName = "Child", fatherId = "parent")
        val persons = listOf(parent, child)

        // Flawed inverted map: parent at 2, child at 1
        val invertedMap = mapOf("parent" to 2, "child" to 1)
        assertFalse("Hierarchy validator must reject when parent is below child",
            com.example.btproject2.engine.GenerationHierarchyEngine.validateHierarchy(persons, invertedMap))

        // Same generation map: parent at 1, child at 1
        val sameLevelMap = mapOf("parent" to 1, "child" to 1)
        assertFalse("Hierarchy validator must reject when parent is on same level as child",
            com.example.btproject2.engine.GenerationHierarchyEngine.validateHierarchy(persons, sameLevelMap))
    }

    @Test
    fun testClarifiedBugarinFamilyHierarchyJoseAboveSiblingsClaraAndRenzy() {
        val jose = Person(id = "jose", firstName = "Jose", lastName = "Bugarin", gender = "Male")
        val clara = Person(id = "clara", firstName = "Clara", lastName = "Bugarin", gender = "Female", fatherId = "jose", spouseId = "pedro", maritalStatus = "Married")
        val pedro = Person(id = "pedro", firstName = "Pedro", lastName = "Penduko", gender = "Male", spouseId = "clara", maritalStatus = "Married")
        val renzy = Person(id = "renzy", firstName = "Renzy", lastName = "Bugarin", gender = "Male", fatherId = "jose")

        val family = listOf(jose, clara, pedro, renzy)
        val genMap = com.example.btproject2.engine.GenerationHierarchyEngine.computeGenerations0Based(family)

        // Jose is Generation 0 at the top
        assertEquals("Jose must be at the top level Gen 0", 0, genMap["jose"])

        // Clara, Pedro, and Renzy are all Generation 1 (below Jose)
        assertEquals("Clara must be Gen 1 below Jose", 1, genMap["clara"])
        assertEquals("Pedro must be Gen 1 aligned with Clara", 1, genMap["pedro"])
        assertEquals("Renzy must be Gen 1 as Clara's brother sharing father Jose", 1, genMap["renzy"])

        // Validate hierarchy
        assertTrue(com.example.btproject2.engine.GenerationHierarchyEngine.validateHierarchy(family, genMap))
    }
}


