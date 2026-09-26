package com.example.btproject2.engine

import com.example.btproject2.models.Person

/**
 * Universal Family Tree Generation & Hierarchy Engine.
 *
 * Enforces global hierarchy rules across ALL family trees in the system:
 * 1. Strict Parent-Child Hierarchy: Every parent is positioned on a higher level
 *    (lower generation index, closer to the top of the canvas) than all of their children:
 *      parentGeneration < childGeneration
 * 2. Spouses and co-parents who share children or are registered as spouses appear
 *    on the exact same generation level:
 *      generation(A) == generation(B)
 * 3. Siblings sharing parent(s) are placed on the same generation level.
 * 4. Automatic Re-organization: Whenever members or relationships are added, updated,
 *    or deleted, the hierarchy is recalculated globally so newly linked parents move above
 *    their children and newly linked children move below their parents.
 * 5. Pre-Render Validation & Recalculation:
 *    Validates every parent-child pair. If parentGeneration >= childGeneration is ever detected,
 *    it recalculates and pushes descendants down before the tree renders.
 */
object GenerationHierarchyEngine {

    /**
     * Computes 0-based generation indices for the Interactive Family Tree canvas.
     * Generation 0 = topmost founding ancestors / roots.
     * Generation 1 = children of roots.
     * Generation 2 = grandchildren, etc.
     */
    fun computeGenerations0Based(
        persons: List<Person>,
        explicitSpouseMap: Map<String, String> = emptyMap()
    ): Map<String, Int> {
        val gen1Map = computeGenerations1Based(persons, explicitSpouseMap)
        val minGen = gen1Map.values.minOrNull() ?: 1
        return gen1Map.mapValues { (_, g) -> maxOf(0, g - minGen) }
    }

    /**
     * Computes 1-based generation indices for pedigree, fan canvas, insights, and statistics.
     * Generation 1 = topmost founding ancestors / roots.
     * Generation 2 = children of roots.
     * Generation 3 = grandchildren, etc.
     */
    fun computeGenerations1Based(
        persons: List<Person>,
        explicitSpouseMap: Map<String, String> = emptyMap()
    ): Map<String, Int> {
        if (persons.isEmpty()) return emptyMap()

        fun canon(id: String?): String = id?.trim()?.lowercase().orEmpty()

        val byCanonId = persons.associateBy { canon(it.id) }

        // Comprehensive spouse and co-parent mapping
        val spouseMap = mutableMapOf<String, String>()
        explicitSpouseMap.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                val ck = canon(k)
                val cv = canon(v)
                spouseMap[ck] = cv
                spouseMap[cv] = ck
            }
        }
        persons.forEach { p ->
            val pId = canon(p.id)
            val sId = canon(p.spouseId)
            if (sId.isNotEmpty() && byCanonId.containsKey(sId)) {
                spouseMap[pId] = sId
                spouseMap[sId] = pId
            }
            val fId = canon(p.fatherId)
            val mId = canon(p.motherId)
            if (fId.isNotEmpty() && mId.isNotEmpty() && byCanonId.containsKey(fId) && byCanonId.containsKey(mId)) {
                if (!spouseMap.containsKey(fId) && !spouseMap.containsKey(mId)) {
                    spouseMap[fId] = mId
                    spouseMap[mId] = fId
                }
            }
        }

        val genMap = mutableMapOf<String, Int>()
        val memo = mutableMapOf<String, Int>()

        // 1. Initial Topological Pass: Longest ancestor path from root (Memoized O(V+E))
        fun computeDepth(pId: String, visited: MutableSet<String>): Int {
            memo[pId]?.let { return it }
            if (!visited.add(pId)) return 1 // Cycle guard
            val p = byCanonId[pId] ?: return 1
            var maxParentDepth = 0
            val f = p.fatherId?.let { canon(it) }
            val m = p.motherId?.let { canon(it) }
            if (!f.isNullOrEmpty() && byCanonId.containsKey(f)) {
                val d = computeDepth(f, visited)
                if (d > maxParentDepth) maxParentDepth = d
            }
            if (!m.isNullOrEmpty() && byCanonId.containsKey(m)) {
                val d = computeDepth(m, visited)
                if (d > maxParentDepth) maxParentDepth = d
            }
            visited.remove(pId)
            val result = if (maxParentDepth > 0) maxParentDepth + 1 else 1
            memo[pId] = result
            return result
        }

        persons.forEach { p ->
            val pid = canon(p.id)
            genMap[pid] = computeDepth(pid, mutableSetOf())
        }

        // 2. Iterative Relaxation Pass: Couple alignment & Child push-down
        var changed = true
        var iteration = 0
        val maxIterations = minOf(persons.size, 10)

        while (changed && iteration++ < maxIterations) {
            changed = false

            // a. Couple alignment: Married spouses or co-parents must share the exact same generation
            persons.forEach { p ->
                val pId = canon(p.id)
                val spId = spouseMap[pId]
                if (spId != null && byCanonId.containsKey(spId)) {
                    val g1 = genMap[pId] ?: 1
                    val g2 = genMap[spId] ?: 1
                    val maxG = maxOf(g1, g2)
                    if (g1 != maxG) {
                        genMap[pId] = maxG
                        changed = true
                    }
                    if (g2 != maxG) {
                        genMap[spId] = maxG
                        changed = true
                    }
                }
            }

            // b. Child push-down: For every child, gen(child) >= max(parent generations) + 1
            persons.forEach { child ->
                val cId = canon(child.id)
                val fGen = child.fatherId?.let { canon(it) }?.let { if (byCanonId.containsKey(it)) genMap[it] else null }
                val mGen = child.motherId?.let { canon(it) }?.let { if (byCanonId.containsKey(it)) genMap[it] else null }
                val highestParentGen = listOfNotNull(fGen, mGen).maxOrNull()
                if (highestParentGen != null) {
                    val requiredGen = highestParentGen + 1
                    val currentGen = genMap[cId] ?: 1
                    if (currentGen < requiredGen) {
                        genMap[cId] = requiredGen
                        changed = true
                    }
                }
            }
        }

        // 3. Global Hierarchy Validation Rule:
        // Validate for every parent-child relationship: parentGeneration < childGeneration
        // If layout detects parentGeneration >= childGeneration, recalculates and pushes down.
        var violationFound = true
        var validationGuard = 0
        val maxValidationGuard = minOf(persons.size, 5)
        while (violationFound && validationGuard++ < maxValidationGuard) {
            violationFound = false
            persons.forEach { child ->
                val cId = canon(child.id)
                val fGen = child.fatherId?.let { canon(it) }?.let { if (byCanonId.containsKey(it)) genMap[it] else null }
                val mGen = child.motherId?.let { canon(it) }?.let { if (byCanonId.containsKey(it)) genMap[it] else null }
                val highestParentGen = listOfNotNull(fGen, mGen).maxOrNull()
                if (highestParentGen != null) {
                    val currentGen = genMap[cId] ?: 1
                    if (highestParentGen >= currentGen) {
                        genMap[cId] = highestParentGen + 1
                        violationFound = true
                    }
                }
            }
            // Re-align spouses if any child was pushed down
            if (violationFound) {
                persons.forEach { p ->
                    val pId = canon(p.id)
                    val spId = spouseMap[pId]
                    if (spId != null && byCanonId.containsKey(spId)) {
                        val g1 = genMap[pId] ?: 1
                        val g2 = genMap[spId] ?: 1
                        val maxG = maxOf(g1, g2)
                        genMap[pId] = maxG
                        genMap[spId] = maxG
                    }
                }
            }
        }

        // Fallback: any unresolved person is placed below max
        val maxGen = genMap.values.maxOrNull() ?: 1
        persons.forEach { p ->
            val pId = canon(p.id)
            if (!genMap.containsKey(pId)) {
                genMap[pId] = maxGen + 1
            }
        }

        val resultMap = mutableMapOf<String, Int>()
        persons.forEach { p ->
            val g = genMap[canon(p.id)] ?: 1
            resultMap[p.id] = g
            resultMap[canon(p.id)] = g
        }
        return resultMap
    }

    /**
     * Validates whether all parent-child relationships in [persons] satisfy parentGeneration < childGeneration.
     * Returns true if valid, false if any hierarchy inversion exists.
     */
    fun validateHierarchy(
        persons: List<Person>,
        generationMap: Map<String, Int>
    ): Boolean {
        fun canon(id: String?): String = id?.trim()?.lowercase().orEmpty()
        val byCanonId = persons.associateBy { canon(it.id) }
        persons.forEach { child ->
            val cId = canon(child.id)
            val cGen = generationMap[child.id] ?: generationMap[cId] ?: return false
            child.fatherId?.let { canon(it) }?.let { fId ->
                if (fId.isNotEmpty() && byCanonId.containsKey(fId)) {
                    val fGen = generationMap[child.fatherId!!] ?: generationMap[fId] ?: return false
                    if (fGen >= cGen) return false
                }
            }
            child.motherId?.let { canon(it) }?.let { mId ->
                if (mId.isNotEmpty() && byCanonId.containsKey(mId)) {
                    val mGen = generationMap[child.motherId!!] ?: generationMap[mId] ?: return false
                    if (mGen >= cGen) return false
                }
            }
        }
        return true
    }
}

