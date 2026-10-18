package com.example.btproject2.engine

import com.example.btproject2.models.Person
import java.util.UUID

/**
 * MergeSimulationEngine
 *
 * Simulates cross-tree synthesis completely in-memory prior to committing changes to Firestore.
 * Performs deep cloning, ID remapping, anchor bridge creation, ancestral parentage migration,
 * and pre-flight conflict scanning through ConflictScanner.
 */
class MergeSimulationEngine(
    private val bridgeValidator: MergeBridgeValidator = MergeBridgeValidator(),
    private val conflictScanner: ConflictScanner = ConflictScanner()
) {

    data class BridgeConfig(
        val mode: MergeBridgeValidator.BridgeMode,
        val personAId: String,
        val personBId: String,
        val masterTreeId: String = UUID.randomUUID().toString(),
        val currentUserId: String = ""
    )

    data class SimulationResult(
        val isSafe: Boolean,
        val bridgeDescription: String,
        val simulatedMembers: List<Person>,
        val validationResult: MergeBridgeValidator.ValidationResult,
        val criticalConflicts: List<ConflictScanner.Conflict>,
        val warningConflicts: List<ConflictScanner.Conflict>
    ) {
        val totalConflictsCount: Int get() = criticalConflicts.size + warningConflicts.size

        val blockReason: String?
            get() = when {
                !validationResult.isAllowed -> validationResult.reason
                criticalConflicts.isNotEmpty() -> criticalConflicts.first().description
                else -> null
            }
    }

    /**
     * Executes an in-memory pre-flight simulation of combining [tree1Members] and [tree2Members]
     * using the specified [config].
     */
    fun simulate(
        tree1Members: List<Person>,
        tree2Members: List<Person>,
        config: BridgeConfig
    ): SimulationResult {
        val personA = tree1Members.find { FamilyLinkValidator.isSameId(it.id, config.personAId) }
        val personB = tree2Members.find { FamilyLinkValidator.isSameId(it.id, config.personBId) }

        if (personA == null || personB == null) {
            val failure = MergeBridgeValidator.ValidationResult.prohibited(
                "Selected anchor members could not be found in the source trees."
            )
            return SimulationResult(
                isSafe = false,
                bridgeDescription = "",
                simulatedMembers = emptyList(),
                validationResult = failure,
                criticalConflicts = emptyList(),
                warningConflicts = emptyList()
            )
        }

        val combinedInputMap = (tree1Members + tree2Members).associateBy { it.id.trim().lowercase() }

        // 1. Run domain validation for the requested bridge mode
        val valResult = when (config.mode) {
            MergeBridgeValidator.BridgeMode.SPOUSAL_UNION ->
                bridgeValidator.validateSpousalUnion(personA, personB, combinedInputMap)
            MergeBridgeValidator.BridgeMode.PARENT_CHILD ->
                bridgeValidator.validateParentChildLink(personA, personB, combinedInputMap)
            MergeBridgeValidator.BridgeMode.SHARED_ANCESTOR ->
                bridgeValidator.validateSharedAncestor(personA, personB, combinedInputMap)
            MergeBridgeValidator.BridgeMode.SIBLING_LINEAGE ->
                bridgeValidator.validateSiblingBridge(personA, personB, combinedInputMap)
        }

        if (!valResult.isAllowed) {
            return SimulationResult(
                isSafe = false,
                bridgeDescription = "",
                simulatedMembers = emptyList(),
                validationResult = valResult,
                criticalConflicts = emptyList(),
                warningConflicts = emptyList()
            )
        }

        // 2. Deep-Cloning with UUID translation dictionary
        val idMap = mutableMapOf<String, String>()
        for (p in tree1Members + tree2Members) {
            idMap[p.id] = UUID.randomUUID().toString()
        }

        fun clonePerson(p: Person): Person {
            return p.copy(
                id = idMap[p.id]!!,
                treeId = config.masterTreeId,
                fatherId = idMap[p.fatherId] ?: p.fatherId,
                motherId = idMap[p.motherId] ?: p.motherId,
                spouseId = idMap[p.spouseId] ?: p.spouseId,
                createdBy = config.currentUserId,
                createdAt = System.currentTimeMillis()
            )
        }

        val clonedList = mutableListOf<Person>()
        for (p in tree1Members) clonedList.add(clonePerson(p))
        for (p in tree2Members) clonedList.add(clonePerson(p))

        // 3. Apply the requested Bridge in memory
        val idxA = clonedList.indexOfFirst { it.id == idMap[personA.id] }
        val idxB = clonedList.indexOfFirst { it.id == idMap[personB.id] }

        var bridgeDesc = ""

        if (idxA != -1 && idxB != -1) {
            val clonedA = clonedList[idxA]
            val clonedB = clonedList[idxB]

            when (config.mode) {
                MergeBridgeValidator.BridgeMode.SPOUSAL_UNION -> {
                    clonedList[idxA] = clonedA.copy(spouseId = clonedB.id, maritalStatus = "Married")
                    clonedList[idxB] = clonedB.copy(spouseId = clonedA.id, maritalStatus = "Married")
                    bridgeDesc = "💍 ${clonedA.firstName} ${clonedA.lastName} married to ${clonedB.firstName} ${clonedB.lastName}"
                }

                MergeBridgeValidator.BridgeMode.PARENT_CHILD -> {
                    val isFemale = clonedA.gender.equals("Female", ignoreCase = true)
                    if (isFemale) {
                        clonedList[idxB] = clonedB.copy(motherId = clonedA.id, motherRelationshipType = "Biological")
                    } else {
                        clonedList[idxB] = clonedB.copy(fatherId = clonedA.id, fatherRelationshipType = "Biological")
                    }
                    val role = if (isFemale) "Mother" else "Father"
                    bridgeDesc = "👶 ${clonedA.firstName} ${clonedA.lastName} is $role of ${clonedB.firstName} ${clonedB.lastName}"
                }

                MergeBridgeValidator.BridgeMode.SHARED_ANCESTOR -> {
                    // Remap all children and spouse pointers pointing to clonedB so they point to clonedA
                    for (i in clonedList.indices) {
                        var item = clonedList[i]
                        if (item.fatherId == clonedB.id) item = item.copy(fatherId = clonedA.id)
                        if (item.motherId == clonedB.id) item = item.copy(motherId = clonedA.id)
                        if (item.spouseId == clonedB.id) item = item.copy(spouseId = clonedA.id)
                        clonedList[i] = item
                    }

                    // Intelligent Ancestral Lineage Migration: Preserve upstream parents if clonedA lacks them
                    var mergedA = clonedList[idxA]
                    if (mergedA.fatherId.isNullOrEmpty() && !clonedB.fatherId.isNullOrEmpty()) {
                        mergedA = mergedA.copy(fatherId = clonedB.fatherId, fatherRelationshipType = clonedB.fatherRelationshipType)
                    }
                    if (mergedA.motherId.isNullOrEmpty() && !clonedB.motherId.isNullOrEmpty()) {
                        mergedA = mergedA.copy(motherId = clonedB.motherId, motherRelationshipType = clonedB.motherRelationshipType)
                    }
                    if (mergedA.spouseId.isNullOrEmpty() && !clonedB.spouseId.isNullOrEmpty()) {
                        mergedA = mergedA.copy(spouseId = clonedB.spouseId, maritalStatus = clonedB.maritalStatus)
                    }
                    clonedList[idxA] = mergedA

                    // Remove redundant duplicate node B
                    clonedList.removeAt(idxB)
                    bridgeDesc = "👥 Shared Ancestor: ${clonedA.firstName} ${clonedA.lastName}"
                }

                MergeBridgeValidator.BridgeMode.SIBLING_LINEAGE -> {
                    var updatedA = clonedList[idxA]
                    var updatedB = clonedList[idxB]

                    val aHasFather = !updatedA.fatherId.isNullOrEmpty()
                    val aHasMother = !updatedA.motherId.isNullOrEmpty()
                    val bHasFather = !updatedB.fatherId.isNullOrEmpty()
                    val bHasMother = !updatedB.motherId.isNullOrEmpty()

                    if (!aHasFather && !aHasMother && !bHasFather && !bHasMother) {
                        // Neither has parents: create shared parents in clan tree to anchor sibling bridge
                        val sharedSurname = if (updatedA.lastName.isNotBlank()) updatedA.lastName else updatedB.lastName
                        val sharedFatherId = UUID.randomUUID().toString()
                        val sharedMotherId = UUID.randomUUID().toString()

                        val sharedFather = Person(
                            id = sharedFatherId,
                            treeId = config.masterTreeId,
                            firstName = "Shared Patriarch",
                            lastName = sharedSurname,
                            gender = "Male",
                            spouseId = sharedMotherId,
                            maritalStatus = "Married",
                            biography = "Shared Patriarch linking family lines as siblings."
                        )
                        val sharedMother = Person(
                            id = sharedMotherId,
                            treeId = config.masterTreeId,
                            firstName = "Shared Matriarch",
                            lastName = sharedSurname,
                            gender = "Female",
                            spouseId = sharedFatherId,
                            maritalStatus = "Married",
                            biography = "Shared Matriarch linking family lines as siblings."
                        )
                        clonedList.add(sharedFather)
                        clonedList.add(sharedMother)

                        updatedA = updatedA.copy(
                            fatherId = sharedFatherId,
                            motherId = sharedMotherId,
                            fatherRelationshipType = "Biological",
                            motherRelationshipType = "Biological"
                        )
                        updatedB = updatedB.copy(
                            fatherId = sharedFatherId,
                            motherId = sharedMotherId,
                            fatherRelationshipType = "Biological",
                            motherRelationshipType = "Biological"
                        )
                        clonedList[idxA] = updatedA
                        clonedList[idxB] = updatedB
                    } else {
                        val sharedFatherId = updatedA.fatherId?.takeIf { it.isNotEmpty() } ?: updatedB.fatherId?.takeIf { it.isNotEmpty() }
                        val sharedMotherId = updatedA.motherId?.takeIf { it.isNotEmpty() } ?: updatedB.motherId?.takeIf { it.isNotEmpty() }

                        if (updatedA.fatherId.isNullOrEmpty() && sharedFatherId != null) {
                            updatedA = updatedA.copy(fatherId = sharedFatherId, fatherRelationshipType = "Biological")
                        }
                        if (updatedA.motherId.isNullOrEmpty() && sharedMotherId != null) {
                            updatedA = updatedA.copy(motherId = sharedMotherId, motherRelationshipType = "Biological")
                        }
                        if (updatedB.fatherId.isNullOrEmpty() && sharedFatherId != null) {
                            updatedB = updatedB.copy(fatherId = sharedFatherId, fatherRelationshipType = "Biological")
                        }
                        if (updatedB.motherId.isNullOrEmpty() && sharedMotherId != null) {
                            updatedB = updatedB.copy(motherId = sharedMotherId, motherRelationshipType = "Biological")
                        }
                        clonedList[idxA] = updatedA
                        clonedList[idxB] = updatedB
                    }
                    bridgeDesc = "👥 Sibling Lineage: ${updatedA.firstName} and ${updatedB.firstName} connected as siblings"
                }
            }
        }

        // 4. Pre-Flight Graph Conflict Scan
        val allConflicts = conflictScanner.scanAll(clonedList)
        val critical = allConflicts.filter { it.severity.equals("error", ignoreCase = true) }
        val warnings = allConflicts.filter { !it.severity.equals("error", ignoreCase = true) }

        val isSafe = valResult.isAllowed && critical.isEmpty()

        return SimulationResult(
            isSafe = isSafe,
            bridgeDescription = bridgeDesc,
            simulatedMembers = clonedList,
            validationResult = valResult,
            criticalConflicts = critical,
            warningConflicts = warnings
        )
    }
}
