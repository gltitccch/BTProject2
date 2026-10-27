package com.example.btproject2.firebase

import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.AdminAuditLog
import com.example.btproject2.models.ArchivedMemberRecord
import com.example.btproject2.models.ClanMergeRequest
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.InviteCodeRecord
import com.example.btproject2.models.MergeCollisionRecord
import com.example.btproject2.models.MergeRollbackSnapshot
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.ReunionPassRecord
import com.example.btproject2.models.SystemAnnouncement
import com.example.btproject2.models.Person
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.models.TreeMember
import com.example.btproject2.models.UserProfile
import com.example.btproject2.models.UserOnboardingState
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.ParentRole
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.TreePreferences
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

class FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()
    private val centralFamilyService = FamilyRelationshipService()

    companion object {
        @Volatile
        private var inMemoryPersonsCache: List<Person>? = null

        @Volatile
        var lastActiveMergeSnapshot: MergeRollbackSnapshot? = null

        fun getCachedPersons(treeId: String? = null): List<Person>? {
            val list = inMemoryPersonsCache ?: return null
            if (treeId == null) return list
            if (treeId.isEmpty()) return emptyList()
            return if (treeId == "default_tree") {
                list.filter { it.treeId.isEmpty() || it.treeId == "default_tree" }
            } else {
                list.filter { it.treeId == treeId }
            }
        }

        fun documentToPerson(doc: com.google.firebase.firestore.DocumentSnapshot): Person? {
            val p = doc.toObject(Person::class.java) ?: return null
            val data = doc.data ?: return p.copy(id = doc.id)

            // Robust parsing for vital status across all historical and current schema variations
            val isLivingVal: Boolean = when {
                data.containsKey("isLiving") -> {
                    val v = data["isLiving"]
                    if (v is Boolean) v else v?.toString()?.toBooleanStrictOrNull() ?: p.isLiving
                }
                data.containsKey("living") -> {
                    val v = data["living"]
                    if (v is Boolean) v else v?.toString()?.toBooleanStrictOrNull() ?: p.isLiving
                }
                data.containsKey("isDeceased") -> {
                    val v = data["isDeceased"]
                    val b = if (v is Boolean) v else v?.toString()?.toBooleanStrictOrNull() ?: false
                    !b
                }
                data.containsKey("living_status") -> {
                    val s = data["living_status"]?.toString()?.trim()?.lowercase()
                    s != "deceased"
                }
                else -> p.isLiving
            }

            val dDate = (data["deathDate"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["dateOfDeath"] as? String)?.takeIf { it.isNotBlank() }
                ?: p.deathDate

            val dPlace = (data["deathPlace"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["placeOfDeath"] as? String)?.takeIf { it.isNotBlank() }
                ?: p.deathPlace

            val conflict = (data["hasTimelineConflict"] as? Boolean) ?: p.hasTimelineConflict
            val createdAtVal = (data["createdAt"] as? Number)?.toLong() ?: 0L

            val rawPerson = p.copy(
                id = doc.id,
                createdAt = createdAtVal,
                isLiving = isLivingVal,
                deathDate = if (isLivingVal) "" else dDate,
                deathPlace = if (isLivingVal) "" else dPlace,
                hasTimelineConflict = conflict
            )
            return com.example.btproject2.utils.KinTraceCryptoEngine.decryptPersonFromCloud(rawPerson)
        }

        fun setCachedPersons(
            persons: List<Person>,
            authoritativeTreeId: String? = null,
            isAuthoritativeServerSnapshot: Boolean = false
        ) {
            val currentMap = inMemoryPersonsCache.orEmpty().associateBy { it.id.trim().lowercase() }
            val serverIds = persons.map { it.id.trim().lowercase() }.toSet()
            val mergedServer = persons.map { serverPerson ->
                val cached = currentMap[serverPerson.id.trim().lowercase()]
                if (cached != null) {
                    val serverIsAuthoritative = isAuthoritativeServerSnapshot &&
                        (serverPerson.updatedAt >= cached.updatedAt || cached.updatedAt == 0L)
                    val isLivingEffective = if (cached.updatedAt > serverPerson.updatedAt && cached.updatedAt > 0) cached.isLiving else serverPerson.isLiving
                    val dDateEffective = if (!isLivingEffective) {
                        if (serverPerson.deathDate.isNotEmpty() && cached.updatedAt <= serverPerson.updatedAt) serverPerson.deathDate else cached.deathDate.ifEmpty { serverPerson.deathDate }
                    } else ""
                    val dPlaceEffective = if (!isLivingEffective) {
                        if (serverPerson.deathPlace.isNotEmpty() && cached.updatedAt <= serverPerson.updatedAt) serverPerson.deathPlace else cached.deathPlace.ifEmpty { serverPerson.deathPlace }
                    } else ""

                    serverPerson.copy(
                        isLiving = isLivingEffective,
                        deathDate = dDateEffective,
                        deathPlace = dPlaceEffective,
                        spouseId = if (!serverIsAuthoritative && !cached.spouseId.isNullOrEmpty() && serverPerson.spouseId.isNullOrEmpty()) cached.spouseId else serverPerson.spouseId,
                        maritalStatus = if (!serverIsAuthoritative && (cached.maritalStatus == "Married" || !cached.spouseId.isNullOrEmpty()) && (serverPerson.maritalStatus.isEmpty() || serverPerson.maritalStatus == "Single")) "Married" else serverPerson.maritalStatus,
                        marriageDate = if (!serverIsAuthoritative && cached.marriageDate.isNotEmpty() && serverPerson.marriageDate.isEmpty()) cached.marriageDate else serverPerson.marriageDate,
                        fatherId = if (!serverIsAuthoritative && !cached.fatherId.isNullOrEmpty() && serverPerson.fatherId.isNullOrEmpty()) cached.fatherId else serverPerson.fatherId,
                        motherId = if (!serverIsAuthoritative && !cached.motherId.isNullOrEmpty() && serverPerson.motherId.isNullOrEmpty()) cached.motherId else serverPerson.motherId
                    )
                } else {
                    serverPerson
                }
            }
            val pendingLocal = inMemoryPersonsCache.orEmpty().filter { local ->
                val idKey = local.id.trim().lowercase()
                if (idKey in serverIds) return@filter false
                if (!isAuthoritativeServerSnapshot) return@filter true
                // When snapshot is authoritative for a specific tree, evict missing records from that tree (zombie deletion fix),
                // while preserving cached records from other trees.
                when {
                    authoritativeTreeId == null -> false
                    authoritativeTreeId == "default_tree" -> !(local.treeId.isEmpty() || local.treeId == "default_tree")
                    authoritativeTreeId.isNotBlank() -> local.treeId != authoritativeTreeId
                    else -> true
                }
            }
            val combined = (mergedServer + pendingLocal).distinctBy { it.id.trim().lowercase() }

            // Reciprocal spouse normalization across all persons (tree-scoped)
            val byId = combined.associateBy { it.id.trim().lowercase() }.toMutableMap()
            combined.forEach { p ->
                val sId = p.spouseId?.trim()?.lowercase()
                if (!sId.isNullOrEmpty()) {
                    val partner = byId[sId]
                    if (partner != null && (partner.treeId.isEmpty() || p.treeId.isEmpty() || partner.treeId == p.treeId)) {
                        var updatedPartner = partner
                        var partnerChanged = false
                        if (partner.spouseId.isNullOrEmpty() || !com.example.btproject2.engine.FamilyLinkValidator.isSameId(partner.spouseId, p.id)) {
                            updatedPartner = updatedPartner.copy(spouseId = p.id)
                            partnerChanged = true
                        }
                        if (updatedPartner.maritalStatus != "Married") {
                            updatedPartner = updatedPartner.copy(maritalStatus = "Married")
                            partnerChanged = true
                        }
                        if (p.marriageDate.isNotEmpty() && updatedPartner.marriageDate.isEmpty()) {
                            updatedPartner = updatedPartner.copy(marriageDate = p.marriageDate)
                            partnerChanged = true
                        }
                        if (partnerChanged) {
                            byId[sId] = updatedPartner
                        }
                    }
                }
            }

            combined.forEach { p ->
                val pId = p.id.trim().lowercase()
                val partnerPointingToMe = byId.values.find {
                    (it.treeId.isEmpty() || p.treeId.isEmpty() || it.treeId == p.treeId) &&
                    com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, p.id)
                }
                if (partnerPointingToMe != null) {
                    val current = byId[pId] ?: p
                    if (current.spouseId.isNullOrEmpty() || current.maritalStatus != "Married") {
                        val mDate = if (current.marriageDate.isNotEmpty()) current.marriageDate else partnerPointingToMe.marriageDate
                        byId[pId] = current.copy(spouseId = partnerPointingToMe.id, maritalStatus = "Married", marriageDate = mDate)
                    }
                }
            }

            inMemoryPersonsCache = sanitizeTreeRecords(byId.values.toList())
        }

        fun sanitizeTreeRecords(persons: List<Person>): List<Person> {
            if (persons.isEmpty()) return persons
            val byId = persons.associateBy { it.id.trim().lowercase() }.toMutableMap()
            val dirtyIds = mutableSetOf<String>()

            // Universal Kinship & Structural Integrity Rule Enforcement across all persons in tree:
            // Disconnect any prohibited co-parenting or direct grandparent-as-parent links
            byId.values.toList().forEach { p ->
                val fId = p.fatherId?.trim()
                val mId = p.motherId?.trim()
                var fixedP = p
                var changed = false

                // Restore historical passing dates for seeded Malone/Vance clan elders if wiped by a prior quick vital-status toggle
                if (!fixedP.isLiving && fixedP.deathDate.isBlank()) {
                    val fullName = "${fixedP.firstName.trim()} ${fixedP.lastName.trim()}".lowercase()
                    val restoredDeathDateAndPlace = when {
                        fullName == "arthur malone" && fixedP.birthDate.startsWith("1935") ->
                            "2015-08-19" to fixedP.birthPlace.ifBlank { "Chicago, Illinois" }
                        fullName == "grace vance" && fixedP.birthDate.startsWith("1963") ->
                            "2021-05-14" to fixedP.birthPlace
                        fullName == "mark gallagher" && fixedP.birthDate.startsWith("1961") ->
                            "2018-11-03" to fixedP.birthPlace
                        else -> null
                    }
                    if (restoredDeathDateAndPlace != null) {
                        fixedP = fixedP.copy(
                            deathDate = restoredDeathDateAndPlace.first,
                            deathPlace = fixedP.deathPlace.ifBlank { restoredDeathDateAndPlace.second }
                        )
                        changed = true
                    }
                }

                val father = if (!fId.isNullOrBlank()) byId[fId.lowercase()]?.takeIf { it.treeId.isEmpty() || p.treeId.isEmpty() || it.treeId == p.treeId } else null
                val mother = if (!mId.isNullOrBlank()) byId[mId.lowercase()]?.takeIf { it.treeId.isEmpty() || p.treeId.isEmpty() || it.treeId == p.treeId } else null

                // Check co-parents validity (cycles, incestuous direct parent-child or full siblings)
                if (father != null && mother != null) {
                    val isSameParent = com.example.btproject2.engine.FamilyLinkValidator.isSameId(father.id, mother.id)
                    val isParentChild = com.example.btproject2.engine.FamilyLinkValidator.isSameId(father.id, mother.fatherId) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(father.id, mother.motherId) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(mother.id, father.fatherId) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(mother.id, father.motherId)
                    val isMotherAncestorOfFather = com.example.btproject2.engine.FamilyLinkValidator.isAncestorOf(mother.id, father.id, byId) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(mother.id, father.motherId)
                    val isFatherAncestorOfMother = com.example.btproject2.engine.FamilyLinkValidator.isAncestorOf(father.id, mother.id, byId) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(father.id, mother.fatherId)

                    if (isSameParent || isParentChild || isMotherAncestorOfFather || isFatherAncestorOfMother) {
                        if (isMotherAncestorOfFather) {
                            fixedP = fixedP.copy(motherId = null, motherRelationshipType = "")
                            changed = true
                        } else if (isFatherAncestorOfMother) {
                            fixedP = fixedP.copy(fatherId = null, fatherRelationshipType = "")
                            changed = true
                        } else {
                            fixedP = fixedP.copy(fatherId = null, fatherRelationshipType = "")
                            changed = true
                        }
                    }
                }

                // Check grandparent-as-parent / circular ancestor violation
                if (fixedP.motherId != null) {
                    val m = byId[fixedP.motherId!!.trim().lowercase()]?.takeIf { it.treeId.isEmpty() || fixedP.treeId.isEmpty() || it.treeId == fixedP.treeId }
                    if (m != null) {
                        val isSelf = com.example.btproject2.engine.FamilyLinkValidator.isSameId(m.id, fixedP.id)
                        val isCycle = com.example.btproject2.engine.FamilyLinkValidator.isAncestorOf(fixedP.id, m.id, byId)
                        if (isSelf || isCycle) {
                            fixedP = fixedP.copy(motherId = null, motherRelationshipType = "")
                            changed = true
                        }
                    }
                }
                if (fixedP.fatherId != null) {
                    val f = byId[fixedP.fatherId!!.trim().lowercase()]?.takeIf { it.treeId.isEmpty() || fixedP.treeId.isEmpty() || it.treeId == fixedP.treeId }
                    if (f != null) {
                        val isSelf = com.example.btproject2.engine.FamilyLinkValidator.isSameId(f.id, fixedP.id)
                        val isCycle = com.example.btproject2.engine.FamilyLinkValidator.isAncestorOf(fixedP.id, f.id, byId)
                        if (isSelf || isCycle) {
                            fixedP = fixedP.copy(fatherId = null, fatherRelationshipType = "")
                            changed = true
                        }
                    }
                }

                if (changed) {
                    byId[p.id.trim().lowercase()] = fixedP
                    dirtyIds.add(p.id)
                }
            }

                // Asynchronously sync any fixed records to Firestore at most once per unique state signature per session
                if (dirtyIds.isNotEmpty()) {
                    try {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        dirtyIds.forEach { id ->
                            val fixed = byId[id.lowercase()]
                            if (fixed != null && fixed.id.isNotBlank()) {
                                val signature = "${fixed.id.trim().lowercase()}|${fixed.fatherId.orEmpty()}|${fixed.motherId.orEmpty()}|${fixed.spouseId.orEmpty()}|${fixed.maritalStatus}|${fixed.deathDate}|${fixed.deathPlace}"
                                if (sanitizedRecordSignatures.add(signature)) {
                                    val docRef = db.collection("persons").document(fixed.id)
                                    val map = mutableMapOf<String, Any?>()
                                    map["fatherId"] = fixed.fatherId
                                    map["motherId"] = fixed.motherId
                                    map["spouseId"] = fixed.spouseId
                                    map["maritalStatus"] = fixed.maritalStatus
                                    map["fatherRelationshipType"] = fixed.fatherRelationshipType
                                    map["motherRelationshipType"] = fixed.motherRelationshipType
                                    if (!fixed.isLiving && fixed.deathDate.isNotBlank()) {
                                        map["deathDate"] = fixed.deathDate
                                        map["dateOfDeath"] = fixed.deathDate
                                    }
                                    if (!fixed.isLiving && fixed.deathPlace.isNotBlank()) {
                                        map["deathPlace"] = fixed.deathPlace
                                        map["placeOfDeath"] = fixed.deathPlace
                                    }
                                    docRef.set(map, com.google.firebase.firestore.SetOptions.merge())
                                        .addOnSuccessListener {
                                            CentralTreeSynchronizer.getInstance().notifyRelationshipCorrected(
                                                primaryPerson = fixed,
                                                issueDescription = "Sanitized invalid relationship in tree",
                                                sourceScreen = "sanitizeTreeRecords"
                                            )
                                        }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                return byId.values.toList()
            }

            private val sanitizedRecordSignatures = java.util.Collections.synchronizedSet(mutableSetOf<String>())
            private val rememberedPassingDetails = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

            fun rememberPassingDetails(personId: String, deathDate: String, deathPlace: String) {
                if (personId.isNotBlank() && (deathDate.isNotBlank() || deathPlace.isNotBlank())) {
                    rememberedPassingDetails[personId.trim().lowercase()] = deathDate.trim() to deathPlace.trim()
                }
            }

            fun getRememberedPassingDetails(personId: String): Pair<String, String>? {
                return rememberedPassingDetails[personId.trim().lowercase()]
            }

            fun invalidateCache() {
                inMemoryPersonsCache = null
            }

        fun updatePersonInCache(updated: Person, allowBlankOverwrite: Boolean = false) {
            val current = inMemoryPersonsCache.orEmpty()
            var found = false
            val isFullProfileUpdate = allowBlankOverwrite ||
                updated.updatedAt > 0L ||
                (updated.firstName.isNotEmpty() && updated.lastName.isNotEmpty())
            inMemoryPersonsCache = current.map { existing ->
                if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(existing.id, updated.id)) {
                    found = true
                    existing.copy(
                        firstName = if (updated.firstName.isNotEmpty()) updated.firstName else existing.firstName,
                        middleName = if (isFullProfileUpdate || updated.middleName.isNotEmpty()) updated.middleName else existing.middleName,
                        lastName = if (updated.lastName.isNotEmpty()) updated.lastName else existing.lastName,
                        suffix = if (isFullProfileUpdate || updated.suffix.isNotEmpty()) updated.suffix else existing.suffix,
                        gender = if (updated.gender.isNotEmpty()) updated.gender else existing.gender,
                        birthDate = if (isFullProfileUpdate || updated.birthDate.isNotEmpty()) updated.birthDate else existing.birthDate,
                        birthPlace = if (isFullProfileUpdate || updated.birthPlace.isNotEmpty()) updated.birthPlace else existing.birthPlace,
                        deathDate = if (updated.isLiving) "" else if (isFullProfileUpdate || updated.deathDate.isNotEmpty()) updated.deathDate else existing.deathDate,
                        deathPlace = if (updated.isLiving) "" else if (isFullProfileUpdate || updated.deathPlace.isNotEmpty()) updated.deathPlace else existing.deathPlace,
                        isLiving = updated.isLiving,
                        isPrivate = if (isFullProfileUpdate) updated.isPrivate else existing.isPrivate,
                        spouseId = if (updated.spouseId != null || updated.firstName.isNotEmpty() || updated.maritalStatus == "Single") updated.spouseId else existing.spouseId,
                        maritalStatus = if (updated.maritalStatus.isNotEmpty()) updated.maritalStatus else existing.maritalStatus,
                        marriageDate = if (isFullProfileUpdate || updated.marriageDate.isNotEmpty()) updated.marriageDate else existing.marriageDate,
                        fatherId = if (updated.fatherId != null || updated.firstName.isNotEmpty()) updated.fatherId else existing.fatherId,
                        fatherRelationshipType = if (updated.fatherRelationshipType.isNotEmpty()) updated.fatherRelationshipType else existing.fatherRelationshipType,
                        motherId = if (updated.motherId != null || updated.firstName.isNotEmpty()) updated.motherId else existing.motherId,
                        motherRelationshipType = if (updated.motherRelationshipType.isNotEmpty()) updated.motherRelationshipType else existing.motherRelationshipType,
                        photoUri = if (isFullProfileUpdate || updated.photoUri.isNotEmpty()) updated.photoUri else existing.photoUri,
                        photoBase64 = if (isFullProfileUpdate || updated.photoBase64.isNotEmpty()) updated.photoBase64 else existing.photoBase64,
                        biography = if (isFullProfileUpdate || updated.biography.isNotEmpty()) updated.biography else existing.biography,
                        treeId = if (updated.treeId.isNotEmpty()) updated.treeId else existing.treeId,
                        documents = if (isFullProfileUpdate || updated.documents.isNotEmpty()) updated.documents else existing.documents,
                        updatedAt = if (updated.updatedAt > 0L) updated.updatedAt else existing.updatedAt,
                        hasTimelineConflict = updated.hasTimelineConflict,
                        lineageRole = if (isFullProfileUpdate || updated.lineageRole.isNotEmpty()) updated.lineageRole else existing.lineageRole
                    )
                } else existing
            }
            if (!found) {
                inMemoryPersonsCache = (inMemoryPersonsCache.orEmpty() + updated)
            }
        }

        fun removePersonFromCache(personId: String) {
            inMemoryPersonsCache = inMemoryPersonsCache?.filter { 
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) 
            }
        }

        fun addPersonToCache(person: Person) {
            val current = inMemoryPersonsCache.orEmpty()
            inMemoryPersonsCache = (current.filter { 
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.id) 
            } + person)
        }

        fun clearCache() {
            inMemoryPersonsCache = null
        }

        /**
         * Updates [inMemoryPersonsCache] after [mergePersons] succeeds:
         * evicts [deletedPersonId], updates [mergedPerson], reparents [affectedChildren],
         * re-links spouse, and broadcasts synchronizer events.
         */
        fun applyMergePersonsToCache(
            mergedPerson: Person,
            deletedPersonId: String,
            affectedChildren: List<Person>
        ) {
            removePersonFromCache(deletedPersonId)
            updatePersonInCache(mergedPerson, allowBlankOverwrite = true)

            inMemoryPersonsCache = inMemoryPersonsCache?.map { existing ->
                var updated = existing
                if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(existing.fatherId, deletedPersonId)) {
                    updated = updated.copy(fatherId = mergedPerson.id)
                }
                if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(existing.motherId, deletedPersonId)) {
                    updated = updated.copy(motherId = mergedPerson.id)
                }
                if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(existing.spouseId, deletedPersonId) ||
                    (!mergedPerson.spouseId.isNullOrBlank() && com.example.btproject2.engine.FamilyLinkValidator.isSameId(existing.id, mergedPerson.spouseId))
                ) {
                    updated = updated.copy(spouseId = mergedPerson.id, maritalStatus = "Married")
                }
                updated
            }

            affectedChildren.forEach { child ->
                val newFather = if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(child.fatherId, deletedPersonId)) mergedPerson.id else child.fatherId
                val newMother = if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(child.motherId, deletedPersonId)) mergedPerson.id else child.motherId
                updatePersonInCache(child.copy(fatherId = newFather, motherId = newMother))
            }

            CentralTreeSynchronizer.getInstance().notifyMemberDeleted(
                deletedPersonId = deletedPersonId,
                treeId = mergedPerson.treeId,
                affectedRelatives = affectedChildren.map { it.id }.toSet(),
                sourceScreen = "mergePersons"
            )
            CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                updatedPerson = mergedPerson,
                sourceScreen = "mergePersons"
            )
        }

        /**
         * Captures a complete [ArchivedMemberRecord] snapshot including all children that had
         * [person] as father or mother and any reciprocal spouse link BEFORE references are cleared.
         */
        fun buildArchivedMemberSnapshot(
            person: Person,
            treeMembers: List<Person>,
            treeName: String = "Family Tree",
            deletedBy: String = "user",
            deletedByName: String = "Editor"
        ): ArchivedMemberRecord {
            val fatherChildIds = treeMembers
                .filter { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, person.id) }
                .map { it.id }
            val motherChildIds = treeMembers
                .filter { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, person.id) }
                .map { it.id }
            val reciprocalSpouseId = person.spouseId?.takeIf { it.isNotBlank() }
                ?: treeMembers.firstOrNull { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, person.id) }?.id

            return ArchivedMemberRecord(
                id = person.id,
                treeId = person.treeId,
                treeName = treeName,
                person = person,
                deletedBy = deletedBy,
                deletedByName = deletedByName,
                deletedAt = System.currentTimeMillis(),
                originalFatherId = person.fatherId,
                originalMotherId = person.motherId,
                originalSpouseId = reciprocalSpouseId,
                formerFatherChildIds = fatherChildIds,
                formerMotherChildIds = motherChildIds,
                isRestored = false
            )
        }

        /**
         * Restores [archive] into [inMemoryPersonsCache] and re-links its former children
         * (`fatherId`/`motherId`) and former spouse (`spouseId`/`maritalStatus`).
         */
        fun applyRestoreArchivedMemberToCache(archive: ArchivedMemberRecord): Person {
            val personToRestore = archive.person.copy(
                id = archive.id.ifBlank { archive.person.id },
                treeId = archive.treeId.ifBlank { archive.person.treeId },
                fatherId = archive.originalFatherId,
                motherId = archive.originalMotherId,
                spouseId = archive.originalSpouseId,
                maritalStatus = if (!archive.originalSpouseId.isNullOrBlank()) "Married" else archive.person.maritalStatus,
                updatedAt = System.currentTimeMillis()
            )
            val fatherSet = archive.formerFatherChildIds.map { it.trim().lowercase() }.toSet()
            val motherSet = archive.formerMotherChildIds.map { it.trim().lowercase() }.toSet()
            val spouseKey = archive.originalSpouseId?.trim()?.lowercase()

            val updatedCache = (inMemoryPersonsCache ?: emptyList())
                .filterNot { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personToRestore.id) }
                .map { member ->
                    val key = member.id.trim().lowercase()
                    var m = member
                    if (key in fatherSet) {
                        m = m.copy(fatherId = personToRestore.id)
                    }
                    if (key in motherSet) {
                        m = m.copy(motherId = personToRestore.id)
                    }
                    if (!spouseKey.isNullOrEmpty() && key == spouseKey) {
                        m = m.copy(
                            spouseId = personToRestore.id,
                            maritalStatus = if (!personToRestore.isLiving && m.isLiving) "Widowed" else "Married"
                        )
                    }
                    m
                } + personToRestore

            inMemoryPersonsCache = sanitizeTreeRecords(updatedCache)
            return personToRestore
        }

        fun hasPersonDiverged(op: Person, np: Person): Boolean {
            return op.firstName != np.firstName ||
                op.middleName != np.middleName ||
                op.lastName != np.lastName ||
                op.suffix != np.suffix ||
                op.gender != np.gender ||
                op.birthDate != np.birthDate ||
                op.birthPlace != np.birthPlace ||
                op.motherId != np.motherId ||
                op.fatherId != np.fatherId ||
                op.spouseId != np.spouseId ||
                op.treeId != np.treeId ||
                op.createdBy != np.createdBy ||
                op.maritalStatus != np.maritalStatus ||
                op.isLiving != np.isLiving ||
                op.deathDate != np.deathDate ||
                op.deathPlace != np.deathPlace ||
                op.biography != np.biography ||
                op.isPrivate != np.isPrivate ||
                op.marriageDate != np.marriageDate ||
                op.fatherRelationshipType != np.fatherRelationshipType ||
                op.motherRelationshipType != np.motherRelationshipType ||
                op.photoUri != np.photoUri ||
                op.photoBase64 != np.photoBase64 ||
                op.documents != np.documents ||
                op.hasTimelineConflict != np.hasTimelineConflict ||
                op.lineageRole != np.lineageRole
        }

        fun hasListDiverged(oldList: List<Person>?, newList: List<Person>): Boolean {
            if (oldList == null) return true
            if (oldList.size != newList.size) return true
            val oldMap = oldList.associateBy { it.id.trim().lowercase() }
            for (np in newList) {
                val op = oldMap[np.id.trim().lowercase()] ?: return true
                if (hasPersonDiverged(op, np)) {
                    return true
                }
            }
            return false
        }

        /** Phase 1: everything [updatePerson] will write, computed up front (pure, no Firebase). */
        data class PersonUpdatePlan(
            val personToSave: Person,
            val vitalUpdates: Map<String, Any>,
            /** Living partner whose maritalStatus must change (Widowed/Married), or null. */
            val partnerStatusUpdate: Pair<Person, String>?,
            /** True when no spouseId is set but someone in the tree still points to this person as spouse. */
            val hasExSpouse: Boolean
        )

        fun planPersonUpdate(person: Person, cache: List<Person>): PersonUpdatePlan {
            val effectiveMaritalStatus = if (!person.spouseId.isNullOrBlank()) {
                if (person.maritalStatus.isBlank() || person.maritalStatus.equals("Single", ignoreCase = true)) "Married" else person.maritalStatus
            } else person.maritalStatus
            val p = person.copy(maritalStatus = effectiveMaritalStatus)

            val vitalUpdates = mapOf<String, Any>(
                "isLiving" to p.isLiving,
                "living" to p.isLiving,
                "isDeceased" to !p.isLiving,
                "living_status" to if (p.isLiving) "Living" else "Deceased",
                "deathDate" to if (p.isLiving) "" else p.deathDate,
                "deathPlace" to if (p.isLiving) "" else p.deathPlace,
                "dateOfDeath" to if (p.isLiving) "" else p.deathDate,
                "placeOfDeath" to if (p.isLiving) "" else p.deathPlace
            )

            fun sameTree(o: Person) = o.treeId == p.treeId || (o.treeId.isEmpty() && p.treeId.isEmpty())
            val pointsToMe = cache.find { sameTree(it) && com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, p.id) }

            val partnerId = p.spouseId ?: pointsToMe?.id
            val partnerUpdate = if (!partnerId.isNullOrBlank()) {
                val partner = cache.find { sameTree(it) && com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, partnerId) }
                val target = if (!p.isLiving) "Widowed" else "Married"
                if (partner != null && partner.isLiving && !partner.maritalStatus.equals(target, ignoreCase = true)) partner to target else null
            } else null

            return PersonUpdatePlan(p, vitalUpdates, partnerUpdate, p.spouseId.isNullOrBlank() && pointsToMe != null)
        }

        fun generateInviteCode(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..6).map { chars.random() }.joinToString("")
        }

        fun generateMergeInviteCode(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return "M" + (1..5).map { chars.random() }.joinToString("")
        }

        fun documentToFamilyTree(doc: com.google.firebase.firestore.DocumentSnapshot): FamilyTree? {
            if (!doc.exists()) return null
            val data = doc.data ?: return doc.toObject(FamilyTree::class.java)?.copy(id = doc.id)

            val name = (data["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["treeName"] as? String)?.takeIf { it.isNotBlank() }
                ?: "Family Tree"
            val ownerId = (data["ownerId"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["createdBy"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["userId"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""
            val ownerName = (data["ownerName"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["creatorName"] as? String)?.takeIf { it.isNotBlank() }
                ?: "Owner"
            val ownerEmail = (data["ownerEmail"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["email"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""
            val inviteCode = (data["inviteCode"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["code"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""
            val isPrivate = data["isPrivate"] as? Boolean
                ?: data["private"] as? Boolean
                ?: false
            val memberCount = (data["memberCount"] as? Number)?.toInt() ?: 1
            val memberQuota = ((data["memberQuota"] as? Number)?.toInt() ?: FamilyTree.DEFAULT_FREEMIUM_QUOTA)
                .takeIf { it > 0 } ?: FamilyTree.DEFAULT_FREEMIUM_QUOTA
            val quotaExpansionRequested = data["quotaExpansionRequested"] as? Boolean ?: false
            val quotaRequestedAt = (data["quotaRequestedAt"] as? Number)?.toLong() ?: 0L
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val treeType = (data["treeType"] as? String)?.takeIf { it.isNotBlank() } ?: FamilyTree.TREE_TYPE_PERSONAL
            val bridgeDescription = (data["bridgeDescription"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val mergeInviteCode = (data["mergeInviteCode"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["mergeCode"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""
            val sourceTree1Id = (data["sourceTree1Id"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val sourceTree2Id = (data["sourceTree2Id"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val coOwnerIds = (data["coOwnerIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

            return FamilyTree(
                id = doc.id,
                name = name,
                ownerId = ownerId,
                ownerName = ownerName,
                ownerEmail = ownerEmail,
                inviteCode = inviteCode.replace(" ", "").trim().uppercase(),
                isPrivate = isPrivate,
                memberCount = memberCount,
                memberQuota = memberQuota,
                quotaExpansionRequested = quotaExpansionRequested,
                quotaRequestedAt = quotaRequestedAt,
                createdAt = createdAt,
                treeType = treeType,
                bridgeDescription = bridgeDescription,
                mergeInviteCode = mergeInviteCode.replace(" ", "").trim().uppercase(),
                sourceTree1Id = sourceTree1Id,
                sourceTree2Id = sourceTree2Id,
                coOwnerIds = coOwnerIds
            )
        }

        fun documentToClanMergeRequest(doc: com.google.firebase.firestore.DocumentSnapshot): ClanMergeRequest? {
            if (!doc.exists()) return null
            val data = doc.data ?: return doc.toObject(ClanMergeRequest::class.java)?.copy(id = doc.id)

            val id = doc.id
            val code = (data["code"] as? String)?.trim()?.uppercase() ?: ""
            val requesterTreeId = (data["requesterTreeId"] as? String) ?: ""
            val requesterTreeName = (data["requesterTreeName"] as? String) ?: "Requesting Tree"
            val requesterOwnerId = (data["requesterOwnerId"] as? String) ?: ""
            val requesterOwnerName = (data["requesterOwnerName"] as? String) ?: "Requester"
            val targetTreeId = (data["targetTreeId"] as? String) ?: ""
            val targetTreeName = (data["targetTreeName"] as? String) ?: "Target Tree"
            val targetOwnerId = (data["targetOwnerId"] as? String) ?: ""
            val targetOwnerName = (data["targetOwnerName"] as? String) ?: "Owner"
            val status = (data["status"] as? String) ?: ClanMergeRequest.STATUS_PENDING
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val expiresAt = (data["expiresAt"] as? Number)?.toLong() ?: (createdAt + ClanMergeRequest.DEFAULT_EXPIRY_MILLIS)
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt
            val createdMasterTreeId = (data["createdMasterTreeId"] as? String) ?: ""

            return ClanMergeRequest(
                id = id,
                code = code,
                requesterTreeId = requesterTreeId,
                requesterTreeName = requesterTreeName,
                requesterOwnerId = requesterOwnerId,
                requesterOwnerName = requesterOwnerName,
                targetTreeId = targetTreeId,
                targetTreeName = targetTreeName,
                targetOwnerId = targetOwnerId,
                targetOwnerName = targetOwnerName,
                status = status,
                createdAt = createdAt,
                expiresAt = expiresAt,
                updatedAt = updatedAt,
                createdMasterTreeId = createdMasterTreeId
            )
        }
    }

    fun addPerson(
        person: Person,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val all = getCachedPersons(person.treeId).orEmpty()
        val tempMap = all.associateBy { it.id }.toMutableMap()
        tempMap[person.id] = person

        // 1. Authoritative Central Service Validation for Father
        if (!person.fatherId.isNullOrBlank()) {
            val fType = if (person.fatherRelationshipType.equals("Adoptive", ignoreCase = true))
                ProposedRelationshipType.ADOPTIVE_PARENT
            else
                ProposedRelationshipType.BIOLOGICAL_PARENT
            val fValidation = centralFamilyService.checkProposedRelationship(
                type = fType,
                primaryPersonId = person.id,
                secondaryPersonId = person.fatherId,
                tree = tempMap,
                role = ParentRole.FATHER
            )
            if (!fValidation.isAllowed) {
                onFailure(IllegalArgumentException(fValidation.reason))
                return
            }
        }

        // 2. Authoritative Central Service Validation for Mother
        if (!person.motherId.isNullOrBlank()) {
            val mType = if (person.motherRelationshipType.equals("Adoptive", ignoreCase = true))
                ProposedRelationshipType.ADOPTIVE_PARENT
            else
                ProposedRelationshipType.BIOLOGICAL_PARENT
            val mValidation = centralFamilyService.checkProposedRelationship(
                type = mType,
                primaryPersonId = person.id,
                secondaryPersonId = person.motherId,
                tree = tempMap,
                role = ParentRole.MOTHER
            )
            if (!mValidation.isAllowed) {
                onFailure(IllegalArgumentException(mValidation.reason))
                return
            }
        }

        // 3. Authoritative Central Service Validation for Spouse
        if (!person.spouseId.isNullOrBlank()) {
            val sValidation = centralFamilyService.checkProposedRelationship(
                type = ProposedRelationshipType.SPOUSE,
                primaryPersonId = person.id,
                secondaryPersonId = person.spouseId,
                tree = tempMap
            )
            if (!sValidation.isAllowed) {
                val spouseCandidate = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.spouseId) } ?: Person(id = person.spouseId)
                val msg = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(person, spouseCandidate, sValidation)
                onFailure(IllegalArgumentException(msg))
                return
            }
        }

        val father = if (!person.fatherId.isNullOrBlank()) all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.fatherId) } else null
        val mother = if (!person.motherId.isNullOrBlank()) all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, person.motherId) } else null

        if (father != null && mother != null) {
            val coCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(father, mother, all)
            if (!coCheck.isValid) {
                onFailure(IllegalArgumentException(coCheck.errors.first().message))
                return
            }
        }
        if (father != null) {
            val fCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(father, person, "father", all)
            if (!fCheck.isValid) {
                onFailure(IllegalArgumentException(fCheck.errors.first().message))
                return
            }
        }
        if (mother != null) {
            val mCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(mother, person, "mother", all)
            if (!mCheck.isValid) {
                onFailure(IllegalArgumentException(mCheck.errors.first().message))
                return
            }
        }

        val docRef = db.collection("persons").document()
        val personWithId = person.copy(id = docRef.id)
        val encryptedForCloud = com.example.btproject2.utils.KinTraceCryptoEngine.encryptPersonForCloud(personWithId)
        val vitalUpdates = mapOf<String, Any>(
            "isLiving" to personWithId.isLiving,
            "living" to personWithId.isLiving,
            "isDeceased" to !personWithId.isLiving,
            "living_status" to if (personWithId.isLiving) "Living" else "Deceased",
            "deathDate" to if (personWithId.isLiving) "" else personWithId.deathDate,
            "deathPlace" to if (personWithId.isLiving) "" else encryptedForCloud.deathPlace,
            "dateOfDeath" to if (personWithId.isLiving) "" else personWithId.deathDate,
            "placeOfDeath" to if (personWithId.isLiving) "" else encryptedForCloud.deathPlace
        )
        docRef.set(encryptedForCloud)
            .addOnSuccessListener {
                docRef.update(vitalUpdates)
                addPersonToCache(personWithId)
                CentralTreeSynchronizer.getInstance().notifyMemberAdded(personWithId, sourceScreen = "AddMember")
                if (!personWithId.spouseId.isNullOrBlank()) {
                    updateSpouse(
                        personWithId.id,
                        personWithId.spouseId,
                        personWithId.maritalStatus,
                        marriageDate = personWithId.marriageDate,
                        onSuccess = {},
                        onFailure = {}
                    )
                }
                onSuccess(docRef.id)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPerson(
        personId: String,
        onSuccess: (Person?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (personId.isBlank()) {
            onSuccess(null)
            return
        }
        // Check in-memory cache first (0ms)
        inMemoryPersonsCache?.let { cached ->
            val sanitized = sanitizeTreeRecords(cached)
            sanitized.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId) }?.let {
                onSuccess(it)
                return
            }
        }
        db.collection("persons").document(personId).get()
            .addOnSuccessListener { doc ->
                val p = documentToPerson(doc)
                if (p != null) {
                    val partner = inMemoryPersonsCache?.find {
                        (it.treeId == p.treeId || (it.treeId.isEmpty() && p.treeId.isEmpty())) &&
                        com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, p.id)
                    }
                    val effective = if (partner != null && (p.spouseId.isNullOrEmpty() || p.maritalStatus != "Married")) {
                        p.copy(spouseId = partner.id, maritalStatus = "Married", marriageDate = if (p.marriageDate.isNotEmpty()) p.marriageDate else partner.marriageDate)
                    } else p
                    val allSanitized = sanitizeTreeRecords(inMemoryPersonsCache.orEmpty() + effective)
                    val sanitizedPerson = allSanitized.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, p.id) } ?: effective
                    updatePersonInCache(sanitizedPerson)
                    onSuccess(sanitizedPerson)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    private fun hasListDiverged(oldList: List<Person>?, newList: List<Person>): Boolean =
        Companion.hasListDiverged(oldList, newList)

    fun getPersonsByTree(
        treeId: String,
        onSuccess: (List<Person>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        fun resolveFiltered(all: List<Person>): List<Person> {
            if (treeId.isEmpty()) {
                return emptyList()
            }
            return if (treeId == "default_tree") {
                all.filter { it.treeId.isEmpty() || it.treeId == "default_tree" }
            } else {
                all.filter { it.treeId == treeId }
            }
        }

        var dispatched = false

        // 1. Instant in-memory cache dispatch (0ms)
        inMemoryPersonsCache?.let { cached ->
            val scopedCached = resolveFiltered(cached)
            if (scopedCached.isNotEmpty()) {
                dispatched = true
                onSuccess(scopedCached)
            }
        }

        val query: com.google.firebase.firestore.Query = if (treeId.isNotBlank() && treeId != "default_tree") {
            db.collection("persons").whereEqualTo("treeId", treeId)
        } else {
            db.collection("persons")
        }

        // 2. Fast local disk cache dispatch (< 10ms)
        if (!dispatched) {
            query.get(Source.CACHE)
                .addOnSuccessListener { cacheResult ->
                    if (!cacheResult.isEmpty) {
                        val diskCached = cacheResult.mapNotNull { doc ->
                            documentToPerson(doc)
                        }
                        if (diskCached.isNotEmpty()) {
                            setCachedPersons(diskCached)
                            dispatched = true
                            onSuccess(resolveFiltered(inMemoryPersonsCache ?: diskCached))
                        }
                    }
                }
                .addOnFailureListener { /* Disk cache miss is handled by server query */ }
        }

        // 3. Server sync in background (scoped by treeId + authoritative zombie cleanup)
        query.get()
            .addOnSuccessListener { result ->
                val serverPersons = result.mapNotNull { doc ->
                    documentToPerson(doc)
                }
                val previousScoped = getCachedPersons(treeId)
                val filteredServer = resolveFiltered(serverPersons)
                val diverged = hasListDiverged(previousScoped, filteredServer)
                setCachedPersons(
                    filteredServer,
                    authoritativeTreeId = treeId,
                    isAuthoritativeServerSnapshot = true
                )
                if (!dispatched || diverged) {
                    onSuccess(resolveFiltered(inMemoryPersonsCache ?: filteredServer))
                }
            }
            .addOnFailureListener {
                if (inMemoryPersonsCache != null) {
                    if (!dispatched) onSuccess(resolveFiltered(inMemoryPersonsCache!!))
                } else {
                    onFailure(it)
                }
            }
    }

    /**
     * FIX: Load ALL persons regardless of treeId.
     * Used for Privacy Controls and Family Records to ensure all members are shown.
     */
    fun getAllPersons(
        onSuccess: (List<Person>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        var dispatched = false

        // 1. Instant in-memory cache dispatch (0ms)
        inMemoryPersonsCache?.let { cached ->
            if (cached.isNotEmpty()) {
                dispatched = true
                onSuccess(cached)
            }
        }

        // 2. Fast local disk cache dispatch (< 10ms)
        if (!dispatched) {
            db.collection("persons")
                .get(Source.CACHE)
                .addOnSuccessListener { cacheResult ->
                    if (!cacheResult.isEmpty) {
                        val diskCached = cacheResult.mapNotNull { doc ->
                            documentToPerson(doc)
                        }
                        if (diskCached.isNotEmpty()) {
                            setCachedPersons(diskCached)
                            dispatched = true
                            onSuccess(inMemoryPersonsCache ?: diskCached)
                        }
                    }
                }
                .addOnFailureListener { /* Disk cache miss handled by server query */ }
        }

        // 3. Server sync in background
        db.collection("persons")
            .get()
            .addOnSuccessListener { result ->
                val persons = result.mapNotNull { doc ->
                    documentToPerson(doc)
                }
                val diverged = hasListDiverged(inMemoryPersonsCache, persons)
                setCachedPersons(
                    persons,
                    authoritativeTreeId = null,
                    isAuthoritativeServerSnapshot = true
                )
                if (!dispatched || diverged) {
                    onSuccess(inMemoryPersonsCache ?: persons)
                }
            }
            .addOnFailureListener {
                if (inMemoryPersonsCache != null) {
                    if (!dispatched) onSuccess(inMemoryPersonsCache!!)
                } else {
                    onFailure(it)
                }
            }
    }

    /**
     * Attaches an active real-time Firestore collection snapshot listener to observe
     * member records continuously and broadcast updates.
     */
    fun listenToTreePersons(
        treeId: String? = null,
        onUpdate: (List<Person>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): com.google.firebase.firestore.ListenerRegistration {
        val query: com.google.firebase.firestore.Query = if (!treeId.isNullOrBlank() && treeId != "default_tree") {
            db.collection("persons").whereEqualTo("treeId", treeId)
        } else {
            db.collection("persons")
        }
        return query
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val persons = snapshot.documents.mapNotNull { doc ->
                        documentToPerson(doc)
                    }
                    val filtered = when {
                        treeId.isNullOrBlank() -> emptyList()
                        treeId == "default_tree" -> persons.filter { it.treeId.isNullOrBlank() || it.treeId == "default_tree" }
                        else -> persons.filter { it.treeId == treeId }
                    }
                    val sanitized = sanitizeTreeRecords(filtered)
                    // Notify listener FIRST so CentralTreeSynchronizer can read oldList before cache is overwritten
                    onUpdate(sanitized)
                    setCachedPersons(
                        sanitized,
                        authoritativeTreeId = treeId,
                        isAuthoritativeServerSnapshot = true
                    )
                }
            }
    }

    fun updateParents(
        personId: String,
        motherId: String?,
        fatherId: String?,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedPid = personId.trim()
        val trimmedMid = motherId?.trim()?.ifEmpty { null }
        val trimmedFid = fatherId?.trim()?.ifEmpty { null }
        val targetPerson = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) } ?: Person(id = trimmedPid)
        val all = getCachedPersons(targetPerson.treeId.ifBlank { null }).orEmpty()

        val treeMap = all.associateBy { it.id }.toMutableMap()
        treeMap[targetPerson.id] = targetPerson

        if (trimmedFid != null) {
            val fCheck = centralFamilyService.checkProposedRelationship(
                type = ProposedRelationshipType.BIOLOGICAL_PARENT,
                primaryPersonId = targetPerson.id,
                secondaryPersonId = trimmedFid,
                tree = treeMap,
                role = ParentRole.FATHER
            )
            if (!fCheck.isAllowed) {
                onFailure(IllegalArgumentException(fCheck.reason))
                return
            }
        }

        if (trimmedMid != null) {
            val mCheck = centralFamilyService.checkProposedRelationship(
                type = ProposedRelationshipType.BIOLOGICAL_PARENT,
                primaryPersonId = targetPerson.id,
                secondaryPersonId = trimmedMid,
                tree = treeMap,
                role = ParentRole.MOTHER
            )
            if (!mCheck.isAllowed) {
                onFailure(IllegalArgumentException(mCheck.reason))
                return
            }
        }

        val father = if (trimmedFid != null) all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedFid) } else null
        val mother = if (trimmedMid != null) all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedMid) } else null

        if (father != null && mother != null) {
            val coCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(father, mother, all)
            if (!coCheck.isValid) {
                onFailure(IllegalArgumentException(coCheck.errors.first().message))
                return
            }
        }
        if (father != null) {
            val fCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(father, targetPerson, "father", all)
            if (!fCheck.isValid) {
                onFailure(IllegalArgumentException(fCheck.errors.first().message))
                return
            }
        }
        if (mother != null) {
            val mCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(mother, targetPerson, "mother", all)
            if (!mCheck.isValid) {
                onFailure(IllegalArgumentException(mCheck.errors.first().message))
                return
            }
        }

        val previousInCache = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }
        val updated = targetPerson.copy(motherId = trimmedMid, fatherId = trimmedFid)
        updatePersonInCache(updated)

        db.collection("persons").document(trimmedPid)
            .set(mapOf("motherId" to trimmedMid, "fatherId" to trimmedFid), SetOptions.merge())
            .addOnSuccessListener {
                updatePersonInCache(updated)
                val allPersons = getCachedPersons(targetPerson.treeId.ifBlank { null }).orEmpty()
                val fObj = trimmedFid?.let { fid -> allPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, fid) } }
                val mObj = trimmedMid?.let { mid -> allPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, mid) } }
                if (fObj != null) {
                    CentralTreeSynchronizer.getInstance().notifyParentChildChanged(
                        child = updated,
                        parent = fObj,
                        role = "father",
                        sourceScreen = "updateParents"
                    )
                }
                if (mObj != null) {
                    CentralTreeSynchronizer.getInstance().notifyParentChildChanged(
                        child = updated,
                        parent = mObj,
                        role = "mother",
                        sourceScreen = "updateParents"
                    )
                }
                onSuccess()
            }
            .addOnFailureListener {
                if (previousInCache != null) {
                    updatePersonInCache(previousInCache, allowBlankOverwrite = true)
                } else {
                    removePersonFromCache(trimmedPid)
                }
                onFailure(it)
            }
    }

    fun updateFather(
        personId: String,
        fatherId: String?,
        relationshipType: String = "Biological",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedPid = personId.trim()
        val trimmedFid = fatherId?.trim()?.ifEmpty { null }
        val existing = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }
        val all = getCachedPersons(existing?.treeId?.ifBlank { null }).orEmpty()

        if (trimmedFid != null && existing != null) {
            val treeMap = all.associateBy { it.id }.toMutableMap()
            treeMap[existing.id] = existing
            val type = if (relationshipType.equals("Adoptive", ignoreCase = true))
                ProposedRelationshipType.ADOPTIVE_PARENT
            else
                ProposedRelationshipType.BIOLOGICAL_PARENT
            val serviceCheck = centralFamilyService.checkProposedRelationship(
                type = type,
                primaryPersonId = existing.id,
                secondaryPersonId = trimmedFid,
                tree = treeMap,
                role = ParentRole.FATHER
            )
            if (!serviceCheck.isAllowed) {
                onFailure(IllegalArgumentException(serviceCheck.reason))
                return
            }

            val father = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedFid) }
            val mother = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, existing.motherId) }
            if (father != null && mother != null) {
                val coCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(father, mother, all)
                if (!coCheck.isValid) {
                    onFailure(IllegalArgumentException(coCheck.errors.first().message))
                    return
                }
            }
            if (father != null) {
                val pCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(father, existing, "father", all)
                if (!pCheck.isValid) {
                    onFailure(IllegalArgumentException(pCheck.errors.first().message))
                    return
                }
            }
        }

        val updated = (existing ?: Person(id = trimmedPid)).copy(fatherId = trimmedFid, fatherRelationshipType = relationshipType)
        updatePersonInCache(updated)

        val updates = mapOf(
            "fatherId" to trimmedFid,
            "fatherRelationshipType" to relationshipType
        )
        db.collection("persons").document(trimmedPid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updatePersonInCache(updated)
                val fObj = trimmedFid?.let { fid -> all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, fid) } }
                if (relationshipType.equals("Adoptive", ignoreCase = true)) {
                    CentralTreeSynchronizer.getInstance().notifyAdoptiveChanged(
                        child = updated,
                        parent = fObj,
                        role = "father",
                        isRemoval = (trimmedFid == null),
                        sourceScreen = "updateFather"
                    )
                } else {
                    CentralTreeSynchronizer.getInstance().notifyParentChildChanged(
                        child = updated,
                        parent = fObj,
                        role = "father",
                        isRemoval = (trimmedFid == null),
                        sourceScreen = "updateFather"
                    )
                }
                onSuccess()
            }
            .addOnFailureListener {
                if (existing != null) {
                    updatePersonInCache(existing, allowBlankOverwrite = true)
                } else {
                    removePersonFromCache(trimmedPid)
                }
                onFailure(it)
            }
    }

    fun updateMother(
        personId: String,
        motherId: String?,
        relationshipType: String = "Biological",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedPid = personId.trim()
        val trimmedMid = motherId?.trim()?.ifEmpty { null }
        val existing = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }
        val all = getCachedPersons(existing?.treeId?.ifBlank { null }).orEmpty()

        if (trimmedMid != null && existing != null) {
            val treeMap = all.associateBy { it.id }.toMutableMap()
            treeMap[existing.id] = existing
            val type = if (relationshipType.equals("Adoptive", ignoreCase = true))
                ProposedRelationshipType.ADOPTIVE_PARENT
            else
                ProposedRelationshipType.BIOLOGICAL_PARENT
            val serviceCheck = centralFamilyService.checkProposedRelationship(
                type = type,
                primaryPersonId = existing.id,
                secondaryPersonId = trimmedMid,
                tree = treeMap,
                role = ParentRole.MOTHER
            )
            if (!serviceCheck.isAllowed) {
                onFailure(IllegalArgumentException(serviceCheck.reason))
                return
            }

            val mother = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedMid) }
            val father = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, existing.fatherId) }
            if (father != null && mother != null) {
                val coCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(father, mother, all)
                if (!coCheck.isValid) {
                    onFailure(IllegalArgumentException(coCheck.errors.first().message))
                    return
                }
            }
            if (mother != null) {
                val pCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(mother, existing, "mother", all)
                if (!pCheck.isValid) {
                    onFailure(IllegalArgumentException(pCheck.errors.first().message))
                    return
                }
            }
        }

        val updated = (existing ?: Person(id = trimmedPid)).copy(motherId = trimmedMid, motherRelationshipType = relationshipType)
        updatePersonInCache(updated)

        val updates = mapOf(
            "motherId" to trimmedMid,
            "motherRelationshipType" to relationshipType
        )
        db.collection("persons").document(trimmedPid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updatePersonInCache(updated)
                val mObj = trimmedMid?.let { mid -> all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, mid) } }
                if (relationshipType.equals("Adoptive", ignoreCase = true)) {
                    CentralTreeSynchronizer.getInstance().notifyAdoptiveChanged(
                        child = updated,
                        parent = mObj,
                        role = "mother",
                        isRemoval = (trimmedMid == null),
                        sourceScreen = "updateMother"
                    )
                } else {
                    CentralTreeSynchronizer.getInstance().notifyParentChildChanged(
                        child = updated,
                        parent = mObj,
                        role = "mother",
                        isRemoval = (trimmedMid == null),
                        sourceScreen = "updateMother"
                    )
                }
                onSuccess()
            }
            .addOnFailureListener {
                if (existing != null) {
                    updatePersonInCache(existing, allowBlankOverwrite = true)
                } else {
                    removePersonFromCache(trimmedPid)
                }
                onFailure(it)
            }
    }

    fun loadEntireTree(
        treeId: String,
        onSuccess: (Map<String, Person>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // 1. Instant in-memory cache dispatch (0ms)
        inMemoryPersonsCache?.let { cached ->
            if (cached.isNotEmpty()) {
                val filtered = if (treeId.isEmpty()) {
                    emptyList()
                } else if (treeId == "default_tree") {
                    cached.filter { it.treeId.isEmpty() || it.treeId == "default_tree" }
                } else {
                    cached.filter { it.treeId == treeId }
                }
                onSuccess(filtered.associateBy { it.id })
                return
            }
        }

        db.collection("persons")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                val personMap = mutableMapOf<String, Person>()
                for (doc in result) {
                    val person = documentToPerson(doc)
                    if (person != null) {
                        personMap[doc.id] = person
                    }
                }
                onSuccess(personMap)
            }
            .addOnFailureListener {
                val fallbackCached = getCachedPersons(treeId).orEmpty()
                if (fallbackCached.isNotEmpty()) {
                    onSuccess(fallbackCached.associateBy { it.id })
                    return@addOnFailureListener
                }
                onFailure(it)
            }
    }

    fun updatePerson(
        person: Person,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val plan = planPersonUpdate(person, inMemoryPersonsCache.orEmpty())
        val personToSave = plan.personToSave
        val effectiveMaritalStatus = personToSave.maritalStatus

        // Pre-flight Central Validation Service Check for Spouse
        val all = getCachedPersons(personToSave.treeId.ifBlank { null }).orEmpty()
        if (!personToSave.spouseId.isNullOrBlank()) {
            val treeMap = all.associateBy { it.id }.toMutableMap()
            treeMap[personToSave.id] = personToSave
            val sValidation = centralFamilyService.checkProposedRelationship(
                type = ProposedRelationshipType.SPOUSE,
                primaryPersonId = personToSave.id,
                secondaryPersonId = personToSave.spouseId,
                tree = treeMap
            )
            if (!sValidation.isAllowed) {
                val spouseCandidate = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personToSave.spouseId) } ?: Person(id = personToSave.spouseId)
                val msg = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(personToSave, spouseCandidate, sValidation)
                onFailure(IllegalArgumentException(msg))
                return
            }
        }

        // Phase 1: single atomic batch (profile + legacy vital fields + surviving spouse status).
        val personRef = db.collection("persons").document(personToSave.id)
        val encryptedForCloud = com.example.btproject2.utils.KinTraceCryptoEngine.encryptPersonForCloud(personToSave)
        val cloudVitalUpdates = plan.vitalUpdates.toMutableMap().apply {
            if (!personToSave.isLiving) {
                put("deathPlace", encryptedForCloud.deathPlace)
                put("placeOfDeath", encryptedForCloud.deathPlace)
            }
        }
        val batch = db.batch()
        batch.set(personRef, encryptedForCloud)
        batch.update(personRef, cloudVitalUpdates)
        plan.partnerStatusUpdate?.let { (partner, status) ->
            batch.update(db.collection("persons").document(partner.id), "maritalStatus", status)
        }

        batch.commit()
            .addOnSuccessListener {
                updatePersonInCache(personToSave)
                CentralTreeSynchronizer.getInstance().notifyProfileEdited(personToSave, sourceScreen = "updatePerson")

                plan.partnerStatusUpdate?.let { (partner, status) ->
                    val updatedPartner = partner.copy(maritalStatus = status)
                    updatePersonInCache(updatedPartner)
                    CentralTreeSynchronizer.getInstance().notifyRelationshipMetadataChanged(
                        personA = updatedPartner,
                        personB = personToSave,
                        metadataFields = setOf("maritalStatus"),
                        sourceScreen = "updatePerson"
                    )
                }

                if (!personToSave.spouseId.isNullOrBlank()) {
                    updateSpouse(
                        personToSave.id,
                        personToSave.spouseId,
                        effectiveMaritalStatus,
                        marriageDate = personToSave.marriageDate,
                        onSuccess = onSuccess,
                        onFailure = onFailure
                    )
                } else if (plan.hasExSpouse) {
                    updateSpouse(personToSave.id, null, "Single", marriageDate = "", onSuccess = onSuccess, onFailure = onFailure)
                } else {
                    onSuccess()
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Atomically updates a person's vital status (isLiving, deathDate, deathPlace),
     * auto-updates surviving spouse marital status, persists to Cloud Firestore,
     * refreshes local cache, and notifies CentralTreeSynchronizer.
     */
    fun updateVitalStatus(
        personId: String,
        isLiving: Boolean,
        deathDate: String = "",
        deathPlace: String = "",
        hasTimelineConflict: Boolean = false,
        onSuccess: (Person) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val trimmedId = personId.trim()
        val existing = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedId) }
        if (existing != null && (existing.deathDate.isNotBlank() || existing.deathPlace.isNotBlank())) {
            rememberPassingDetails(existing.id, existing.deathDate, existing.deathPlace)
        }
        val all = getCachedPersons(existing?.treeId?.ifBlank { null }).orEmpty()
        val effectiveDeathDate = if (isLiving) "" else deathDate.trim()
        val effectiveDeathPlace = if (isLiving) "" else deathPlace.trim()
        if (!isLiving && (effectiveDeathDate.isNotBlank() || effectiveDeathPlace.isNotBlank())) {
            rememberPassingDetails(trimmedId, effectiveDeathDate, effectiveDeathPlace)
        }

        val batch = db.batch()
        val personRef = db.collection("persons").document(trimmedId)
        val personUpdates = mutableMapOf<String, Any>(
            "isLiving" to isLiving,
            "living" to isLiving,
            "isDeceased" to !isLiving,
            "living_status" to if (isLiving) "Living" else "Deceased",
            "deathDate" to effectiveDeathDate,
            "deathPlace" to effectiveDeathPlace,
            "dateOfDeath" to effectiveDeathDate,
            "placeOfDeath" to effectiveDeathPlace,
            "hasTimelineConflict" to hasTimelineConflict,
            "updatedAt" to System.currentTimeMillis()
        )
        batch.set(personRef, personUpdates, com.google.firebase.firestore.SetOptions.merge())

        val updatedPerson = (existing ?: Person(id = trimmedId)).copy(
            isLiving = isLiving,
            deathDate = effectiveDeathDate,
            deathPlace = effectiveDeathPlace,
            hasTimelineConflict = hasTimelineConflict,
            updatedAt = System.currentTimeMillis()
        )

        val cacheUpdates = mutableListOf(updatedPerson)
        var spouseToNotify: Pair<Person, Person>? = null

        // Surviving spouse automatic status handling
        val spouseId = updatedPerson.spouseId ?: all.find {
            com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, trimmedId)
        }?.id

        if (!spouseId.isNullOrBlank()) {
            val spouse = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, spouseId) }
            if (spouse != null && spouse.isLiving) {
                val targetMaritalStatus = if (!isLiving) "Widowed" else "Married"
                if (!spouse.maritalStatus.equals(targetMaritalStatus, ignoreCase = true)) {
                    val spouseRef = db.collection("persons").document(spouse.id)
                    batch.set(spouseRef, mapOf(
                        "maritalStatus" to targetMaritalStatus,
                        "updatedAt" to System.currentTimeMillis()
                    ), com.google.firebase.firestore.SetOptions.merge())

                    val updatedSpouse = spouse.copy(maritalStatus = targetMaritalStatus, updatedAt = System.currentTimeMillis())
                    cacheUpdates.add(updatedSpouse)
                    spouseToNotify = Pair(updatedSpouse, updatedPerson)
                }
            }
        }

        batch.commit()
            .addOnSuccessListener {
                for (p in cacheUpdates) {
                    updatePersonInCache(p)
                }
                spouseToNotify?.let { (sp, pri) ->
                    CentralTreeSynchronizer.getInstance().notifyRelationshipMetadataChanged(
                        personA = sp,
                        personB = pri,
                        metadataFields = setOf("maritalStatus"),
                        sourceScreen = "updateVitalStatus"
                    )
                }
                CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                    updatedPerson,
                    previousPerson = existing,
                    sourceScreen = "updateVitalStatus"
                )
                onSuccess(updatedPerson)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun updatePersonPhoto(
        personId: String,
        photoBase64: String,
        photoUri: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val updates = mapOf(
            "photoBase64" to photoBase64,
            "photoUri" to photoUri
        )
        db.collection("persons").document(personId)
            .update(updates)
            .addOnSuccessListener {
                inMemoryPersonsCache = inMemoryPersonsCache?.map {
                    if (it.id == personId) it.copy(photoBase64 = photoBase64, photoUri = photoUri) else it
                }
                val p = inMemoryPersonsCache?.find { it.id == personId } ?: Person(id = personId, photoBase64 = photoBase64, photoUri = photoUri)
                CentralTreeSynchronizer.getInstance().notifyProfileEdited(p, sourceScreen = "updatePersonPhoto")
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun attachDocument(
        personId: String,
        document: com.example.btproject2.models.AttachedDocument,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getPerson(personId,
            onSuccess = { p ->
                if (p == null) {
                    onFailure(Exception("Person not found"))
                    return@getPerson
                }
                val existingBase64Len = p.documents.sumOf { it.fileBase64.length }
                val safeDoc = if (document.fileBase64.isNotEmpty() &&
                    (document.fileBase64.length > 260_000 || existingBase64Len + document.fileBase64.length > 450_000)
                ) {
                    document.copy(fileBase64 = "")
                } else {
                    document
                }
                val updatedDocs = p.documents + safeDoc
                val now = System.currentTimeMillis()
                db.collection("persons").document(personId)
                    .update(mapOf("documents" to updatedDocs, "updatedAt" to now))
                    .addOnSuccessListener {
                        val updatedPerson = p.copy(documents = updatedDocs, updatedAt = now)
                        inMemoryPersonsCache = inMemoryPersonsCache?.map {
                            if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId)) updatedPerson else it
                        }
                        CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                            updatedPerson = updatedPerson,
                            previousPerson = p,
                            sourceScreen = "attachDocument"
                        )
                        onSuccess()
                    }
                    .addOnFailureListener { onFailure(it) }
            },
            onFailure = { onFailure(it) }
        )
    }

    fun removeDocument(
        personId: String,
        documentId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getPerson(personId,
            onSuccess = { p ->
                if (p == null) {
                    onFailure(Exception("Person not found"))
                    return@getPerson
                }
                val updatedDocs = p.documents.filter { it.id != documentId }
                val now = System.currentTimeMillis()
                db.collection("persons").document(personId)
                    .update(mapOf("documents" to updatedDocs, "updatedAt" to now))
                    .addOnSuccessListener {
                        val updatedPerson = p.copy(documents = updatedDocs, updatedAt = now)
                        inMemoryPersonsCache = inMemoryPersonsCache?.map {
                            if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, personId)) updatedPerson else it
                        }
                        CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                            updatedPerson = updatedPerson,
                            previousPerson = p,
                            sourceScreen = "removeDocument"
                        )
                        onSuccess()
                    }
                    .addOnFailureListener { onFailure(it) }
            },
            onFailure = { onFailure(it) }
        )
    }

    fun saveBiography(
        personId: String,
        biography: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("persons").document(personId)
            .update("biography", biography)
            .addOnSuccessListener {
                inMemoryPersonsCache = inMemoryPersonsCache?.map {
                    if (it.id == personId) it.copy(biography = biography) else it
                }
                val p = inMemoryPersonsCache?.find { it.id == personId } ?: Person(id = personId, biography = biography)
                CentralTreeSynchronizer.getInstance().notifyProfileEdited(p, sourceScreen = "saveBiography")
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * NEW: Set or clear the spouse for a person.
     * Also updates the other person's spouseId reciprocally.
     */
    /**
     * Set or clear the spouse for a person atomically.
     * Updates both spouses reciprocally, clears any ex-spouses, and synchronizes
     * in-memory cache and Firestore simultaneously.
     */
    fun updateSpouse(
        personId: String,
        spouseId: String?,
        maritalStatus: String = "Married",
        marriageDate: String? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedPersonId = personId.trim()
        val trimmedSpouseId = spouseId?.trim()?.ifEmpty { null }

        val personA = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPersonId) }
        val personB = if (trimmedSpouseId != null) {
            inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedSpouseId) }
        } else null
        val treeScope = personA?.treeId?.ifBlank { personB?.treeId }?.ifBlank { null }
        val allPersons = getCachedPersons(treeScope).orEmpty()

        val batch = db.batch()
        val cacheUpdates = mutableListOf<Person>()

        if (trimmedSpouseId != null) {
            val treeMap = allPersons.associateBy { it.id }.toMutableMap()
            if (personA != null) treeMap[personA.id] = personA
            if (personB != null) treeMap[personB.id] = personB
            val sCheck = centralFamilyService.checkProposedRelationship(
                type = ProposedRelationshipType.SPOUSE,
                primaryPersonId = trimmedPersonId,
                secondaryPersonId = trimmedSpouseId,
                tree = treeMap
            )
            if (!sCheck.isAllowed) {
                val pA = personA ?: Person(id = trimmedPersonId)
                val pB = personB ?: Person(id = trimmedSpouseId)
                val msg = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(pA, pB, sCheck)
                onFailure(IllegalArgumentException(msg))
                return
            }

            val status = if (maritalStatus.isBlank() || maritalStatus.equals("Single", ignoreCase = true)) "Married" else maritalStatus
            val effectiveMarriageDate = marriageDate?.takeIf { it.isNotBlank() }
                ?: personA?.marriageDate?.takeIf { it.isNotBlank() }
                ?: personB?.marriageDate.orEmpty()
            val resolvedMarriageDate = marriageDate ?: effectiveMarriageDate

            val refA = db.collection("persons").document(trimmedPersonId)
            val refB = db.collection("persons").document(trimmedSpouseId)

            val mapA = mutableMapOf<String, Any?>(
                "spouseId" to trimmedSpouseId,
                "maritalStatus" to status
            )
            val mapB = mutableMapOf<String, Any?>(
                "spouseId" to trimmedPersonId,
                "maritalStatus" to status
            )
            if (marriageDate != null || effectiveMarriageDate.isNotBlank()) {
                mapA["marriageDate"] = resolvedMarriageDate
                mapB["marriageDate"] = resolvedMarriageDate
            }

            batch.set(refA, mapA, SetOptions.merge())
            batch.set(refB, mapB, SetOptions.merge())

            val aObj = (personA ?: Person(id = trimmedPersonId)).copy(
                spouseId = trimmedSpouseId,
                maritalStatus = status,
                marriageDate = resolvedMarriageDate
            )
            cacheUpdates.add(aObj)
            val bObj = (personB ?: Person(id = trimmedSpouseId)).copy(
                spouseId = trimmedPersonId,
                maritalStatus = status,
                marriageDate = resolvedMarriageDate
            )
            cacheUpdates.add(bObj)

            // Clear any old ex-spouses of personA (excluding personB)
            allPersons.filter { 
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPersonId) &&
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedSpouseId) &&
                (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, trimmedPersonId) ||
                 com.example.btproject2.engine.FamilyLinkValidator.isSameId(personA?.spouseId, it.id))
            }.forEach { ex ->
                val exRef = db.collection("persons").document(ex.id)
                batch.set(exRef, mapOf(
                    "spouseId" to null,
                    "maritalStatus" to "Single",
                    "marriageDate" to ""
                ), SetOptions.merge())
                cacheUpdates.add(ex.copy(spouseId = null, maritalStatus = "Single", marriageDate = ""))
            }

            // Clear any old ex-spouses of personB (excluding personA)
            allPersons.filter { 
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPersonId) &&
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedSpouseId) &&
                (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, trimmedSpouseId) ||
                 com.example.btproject2.engine.FamilyLinkValidator.isSameId(personB?.spouseId, it.id))
            }.forEach { ex ->
                val exRef = db.collection("persons").document(ex.id)
                batch.set(exRef, mapOf(
                    "spouseId" to null,
                    "maritalStatus" to "Single",
                    "marriageDate" to ""
                ), SetOptions.merge())
                cacheUpdates.add(ex.copy(spouseId = null, maritalStatus = "Single", marriageDate = ""))
            }
        } else {
            // Removing spouse
            val refA = db.collection("persons").document(trimmedPersonId)
            batch.set(refA, mapOf(
                "spouseId" to null,
                "maritalStatus" to "Single",
                "marriageDate" to ""
            ), SetOptions.merge())
            if (personA != null) {
                cacheUpdates.add(personA.copy(spouseId = null, maritalStatus = "Single", marriageDate = ""))
            } else {
                cacheUpdates.add(Person(id = trimmedPersonId, spouseId = null, maritalStatus = "Single", marriageDate = ""))
            }

            // Clear anyone pointing to personA as spouse
            allPersons.filter { 
                !com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPersonId) &&
                (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, trimmedPersonId) ||
                 com.example.btproject2.engine.FamilyLinkValidator.isSameId(personA?.spouseId, it.id))
            }.forEach { ex ->
                val exRef = db.collection("persons").document(ex.id)
                batch.set(exRef, mapOf(
                    "spouseId" to null,
                    "maritalStatus" to "Single",
                    "marriageDate" to ""
                ), SetOptions.merge())
                cacheUpdates.add(ex.copy(spouseId = null, maritalStatus = "Single", marriageDate = ""))
            }
        }

        // Snapshot cache before optimistic update so we can roll back if Firestore fails
        val preMutationCacheSnapshot = inMemoryPersonsCache
        // Apply to in-memory cache synchronously so any immediate reads see the new relationship
        cacheUpdates.forEach { updatePersonInCache(it) }

        batch.commit()
            .addOnSuccessListener {
                cacheUpdates.forEach { updatePersonInCache(it) }
                val pA = cacheUpdates.find { it.id == trimmedPersonId } ?: (personA ?: Person(id = trimmedPersonId))
                val pB = if (trimmedSpouseId != null) cacheUpdates.find { it.id == trimmedSpouseId } ?: (personB ?: Person(id = trimmedSpouseId)) else null
                val exRelatives = cacheUpdates.filter { it.id != trimmedPersonId && it.id != trimmedSpouseId }.map { it.id }.toSet()
                CentralTreeSynchronizer.getInstance().notifySpouseChanged(
                    personA = pA,
                    personB = pB,
                    isRemoval = (trimmedSpouseId == null),
                    extraAffectedRelatives = exRelatives,
                    sourceScreen = "updateSpouse"
                )
                onSuccess()
            }
            .addOnFailureListener { batchErr ->
                // Fallback: If batch fails, attempt direct updates for both individuals, but only succeed if writes actually succeed
                val status = if (maritalStatus.isBlank() || maritalStatus.equals("Single", ignoreCase = true)) "Married" else maritalStatus
                val aUpdates = mapOf("spouseId" to trimmedSpouseId, "maritalStatus" to (if (trimmedSpouseId != null) status else "Single"))
                db.collection("persons").document(trimmedPersonId)
                    .set(aUpdates, SetOptions.merge())
                    .addOnSuccessListener {
                        if (trimmedSpouseId != null) {
                            val bUpdates = mapOf("spouseId" to trimmedPersonId, "maritalStatus" to status)
                            db.collection("persons").document(trimmedSpouseId)
                                .set(bUpdates, SetOptions.merge())
                                .addOnSuccessListener {
                                    cacheUpdates.forEach { updatePersonInCache(it) }
                                    val pA = cacheUpdates.find { it.id == trimmedPersonId } ?: (personA ?: Person(id = trimmedPersonId))
                                    val pB = cacheUpdates.find { it.id == trimmedSpouseId } ?: (personB ?: Person(id = trimmedSpouseId))
                                    CentralTreeSynchronizer.getInstance().notifySpouseChanged(
                                        personA = pA,
                                        personB = pB,
                                        isRemoval = false,
                                        sourceScreen = "updateSpouse"
                                    )
                                    onSuccess()
                                }
                                .addOnFailureListener { err ->
                                    inMemoryPersonsCache = preMutationCacheSnapshot
                                    onFailure(err)
                                }
                        } else {
                            cacheUpdates.forEach { updatePersonInCache(it) }
                            val pA = cacheUpdates.find { it.id == trimmedPersonId } ?: (personA ?: Person(id = trimmedPersonId))
                            CentralTreeSynchronizer.getInstance().notifySpouseChanged(
                                personA = pA,
                                personB = null,
                                isRemoval = true,
                                sourceScreen = "updateSpouse"
                            )
                            onSuccess()
                        }
                    }
                    .addOnFailureListener {
                        inMemoryPersonsCache = preMutationCacheSnapshot
                        onFailure(batchErr)
                    }
            }
    }

    /**
     * Update marriage date for both spouses atomically.
     */
    fun updateMarriageDate(
        personId: String,
        marriageDate: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedId = personId.trim()
        val allPersons = inMemoryPersonsCache.orEmpty()
        val person = allPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedId) }
        val spouseId = person?.spouseId?.trim()

        val batch = db.batch()
        batch.set(db.collection("persons").document(trimmedId), mapOf("marriageDate" to marriageDate), SetOptions.merge())
        if (!spouseId.isNullOrEmpty()) {
            batch.set(db.collection("persons").document(spouseId), mapOf("marriageDate" to marriageDate), SetOptions.merge())
        }

        batch.commit()
            .addOnSuccessListener {
                val pA = if (person != null) {
                    val up = person.copy(marriageDate = marriageDate)
                    updatePersonInCache(up)
                    up
                } else Person(id = trimmedId, marriageDate = marriageDate)
                val pB = if (!spouseId.isNullOrEmpty()) {
                    allPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, spouseId) }?.let { sp ->
                        val upSp = sp.copy(marriageDate = marriageDate)
                        updatePersonInCache(upSp)
                        upSp
                    }
                } else null
                CentralTreeSynchronizer.getInstance().notifyRelationshipMetadataChanged(
                    personA = pA,
                    personB = pB,
                    metadataFields = setOf("marriageDate"),
                    sourceScreen = "updateMarriageDate"
                )
                onSuccess()
            }
            .addOnFailureListener {
                db.collection("persons").document(trimmedId)
                    .set(mapOf("marriageDate" to marriageDate), SetOptions.merge())
                    .addOnSuccessListener {
                        if (person != null) updatePersonInCache(person.copy(marriageDate = marriageDate))
                        onSuccess()
                    }
                    .addOnFailureListener { onFailure(it) }
            }
    }

    /**
     * NEW: Set a child's parent (add child relationship).
     * Sets the appropriate parent field on the child document.
     */
    fun setChildParent(
        childId: String,
        parentId: String,
        parentGender: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedCid = childId.trim()
        val trimmedPid = parentId.trim()
        val isFemale = parentGender.lowercase() == "female"
        val existing = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedCid) }
        val child = existing ?: Person(id = trimmedCid)
        val parent = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }
        val treeScope = child.treeId.ifBlank { parent?.treeId.orEmpty() }.ifBlank { null }
        val all = getCachedPersons(treeScope).orEmpty()

        if (parent != null) {
            val treeMap = all.associateBy { it.id }.toMutableMap()
            treeMap[child.id] = child
            treeMap[parent.id] = parent
            val roleEnum = if (isFemale) ParentRole.MOTHER else ParentRole.FATHER
            val serviceCheck = centralFamilyService.checkProposedRelationship(
                type = ProposedRelationshipType.BIOLOGICAL_PARENT,
                primaryPersonId = child.id,
                secondaryPersonId = parent.id,
                tree = treeMap,
                role = roleEnum
            )
            if (!serviceCheck.isAllowed) {
                onFailure(IllegalArgumentException(serviceCheck.reason))
                return
            }

            val role = if (isFemale) "mother" else "father"
            val pCheck = com.example.btproject2.engine.FamilyLinkValidator.validateParentChild(parent, child, role, all)
            if (!pCheck.isValid) {
                onFailure(IllegalArgumentException(pCheck.errors.first().message))
                return
            }

            val otherParentId = if (isFemale) child.fatherId else child.motherId
            if (!otherParentId.isNullOrBlank()) {
                val otherParent = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, otherParentId) }
                if (otherParent != null) {
                    val f = if (isFemale) otherParent else parent
                    val m = if (isFemale) parent else otherParent
                    val coCheck = com.example.btproject2.engine.FamilyLinkValidator.validateCoParents(f, m, all)
                    if (!coCheck.isValid) {
                        onFailure(IllegalArgumentException(coCheck.errors.first().message))
                        return
                    }
                }
            }
        }

        val field = if (isFemale) "motherId" else "fatherId"
        val relField = if (isFemale) "motherRelationshipType" else "fatherRelationshipType"
        val updates = mapOf(
            field to trimmedPid,
            relField to "Biological"
        )
        val base = existing ?: Person(id = trimmedCid)
        val updated = if (isFemale) {
            base.copy(motherId = trimmedPid, motherRelationshipType = "Biological")
        } else {
            base.copy(fatherId = trimmedPid, fatherRelationshipType = "Biological")
        }
        updatePersonInCache(updated)

        db.collection("persons").document(trimmedCid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updatePersonInCache(updated)
                val parentObj = parent ?: all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }
                CentralTreeSynchronizer.getInstance().notifyParentChildChanged(
                    child = updated,
                    parent = parentObj,
                    role = if (isFemale) "mother" else "father",
                    isRemoval = false,
                    sourceScreen = "setChildParent"
                )
                onSuccess()
            }
            .addOnFailureListener {
                if (existing != null) {
                    updatePersonInCache(existing, allowBlankOverwrite = true)
                } else {
                    removePersonFromCache(trimmedCid)
                }
                onFailure(it)
            }
    }

    fun getChildren(
        personId: String,
        treeId: String,
        onSuccess: (List<Person>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedPid = personId.trim()
        val cached = inMemoryPersonsCache
        if (!cached.isNullOrEmpty()) {
            val sanitized = sanitizeTreeRecords(cached)
            val cachedChildren = sanitized.filter {
                (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, trimmedPid) ||
                 com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, trimmedPid)) &&
                (if (treeId.isEmpty()) true else if (treeId == "default_tree") (it.treeId.isEmpty() || it.treeId == "default_tree") else it.treeId == treeId)
            }.sortedBy { it.birthDate }
            onSuccess(cachedChildren)
        }

        db.collection("persons")
            .whereEqualTo("fatherId", trimmedPid)
            .get()
            .addOnSuccessListener { fatherResult ->
                val fatherChildren = fatherResult.mapNotNull { doc ->
                    documentToPerson(doc)
                }
                db.collection("persons")
                    .whereEqualTo("motherId", trimmedPid)
                    .get()
                    .addOnSuccessListener { motherResult ->
                        val motherChildren = motherResult.mapNotNull { doc ->
                            documentToPerson(doc)
                        }
                        val treeFilter: (Person) -> Boolean = { p ->
                            if (treeId.isEmpty()) {
                                true
                            } else if (treeId == "default_tree") {
                                p.treeId.isEmpty() || p.treeId == "default_tree"
                            } else {
                                p.treeId == treeId
                            }
                        }
                        val serverChildren = (fatherChildren + motherChildren)
                            .distinctBy { it.id }
                            .filter(treeFilter)

                        val currentCached = inMemoryPersonsCache.orEmpty().filter {
                            (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, trimmedPid) ||
                             com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, trimmedPid)) &&
                            treeFilter(it)
                        }
                        val combined = (serverChildren + currentCached).distinctBy { it.id.trim().lowercase() }.sortedBy { it.birthDate }
                        val sanitizedTree = sanitizeTreeRecords(getCachedPersons(treeId).orEmpty() + combined)
                        val finalChildren = sanitizedTree.filter {
                            (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, trimmedPid) ||
                             com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, trimmedPid)) &&
                            treeFilter(it)
                        }.sortedBy { it.birthDate }
                        onSuccess(finalChildren)
                    }
                    .addOnFailureListener {
                        if (!cached.isNullOrEmpty()) {
                            // Already dispatched cached children
                        } else {
                            onFailure(it)
                        }
                    }
            }
            .addOnFailureListener {
                if (!cached.isNullOrEmpty()) {
                    // Already dispatched cached children
                } else {
                    onFailure(it)
                }
            }
    }

    fun updateMemberPrivacy(
        personId: String,
        isPrivate: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("persons").document(personId)
            .update("isPrivate", isPrivate)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun savePrivacySettings(
        settings: PrivacySettings,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (settings.treeId.isBlank()) {
            onFailure(IllegalArgumentException("Tree ID cannot be blank"))
            return
        }
        val privacyMap = mapOf(
            "treeId" to settings.treeId,
            "isPrivateTree" to settings.isPrivateTree,
            "privateTree" to settings.isPrivateTree,
            "hideBirthDates" to settings.hideBirthDates,
            "hideNotes" to settings.hideNotes,
            "hideDeceasedStatus" to settings.hideDeceasedStatus,
            "publicTree" to settings.publicTree,
            "requireLogin" to settings.requireLogin
        )
        db.collection("privacy").document(settings.treeId)
            .set(privacyMap)
            .addOnSuccessListener {
                db.collection("trees").document(settings.treeId)
                    .set(
                        mapOf("isPrivate" to settings.isPrivateTree, "private" to settings.isPrivateTree),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPrivacySettings(
        treeId: String,
        onSuccess: (PrivacySettings) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("privacy").document(treeId).get()
            .addOnSuccessListener { doc ->
                val raw = doc.toObject(PrivacySettings::class.java)
                val isPriv = doc.getBoolean("isPrivateTree")
                    ?: doc.getBoolean("privateTree")
                    ?: raw?.isPrivateTree
                    ?: false
                val settings = (raw ?: PrivacySettings(treeId = treeId, requireLogin = true)).copy(
                    treeId = treeId,
                    isPrivateTree = isPriv
                )
                onSuccess(settings)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getRecentCount(
        treeId: String,
        daysCutoff: Int = 7,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val cutoffMs = System.currentTimeMillis() - daysCutoff.toLong() * 24 * 60 * 60 * 1000
        // Query by treeId only and filter createdAt client-side so Firestore never fails with FAILED_PRECONDITION (missing composite index)
        db.collection("persons")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                val recentCount = result.documents.count { doc ->
                    val createdAt = (doc.get("createdAt") as? Number)?.toLong() ?: 0L
                    createdAt > cutoffMs
                }
                onSuccess(recentCount)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getBranchCount(
        userId: String,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (userId.isBlank()) {
            onSuccess(1)
            return
        }
        db.collection("trees")
            .whereEqualTo("ownerId", userId)
            .get()
            .addOnSuccessListener { ownerResult ->
                val ids = ownerResult.documents.map { it.id }.toMutableSet()
                db.collection("trees")
                    .whereEqualTo("createdBy", userId)
                    .get()
                    .addOnSuccessListener { createdResult ->
                        createdResult.documents.forEach { ids.add(it.id) }
                        onSuccess(if (ids.isEmpty()) 1 else ids.size)
                    }
                    .addOnFailureListener {
                        onSuccess(if (ids.isEmpty()) 1 else ids.size)
                    }
            }
            .addOnFailureListener { onSuccess(1) }
    }

    fun deletePerson(
        personId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (personId.isBlank()) {
            onFailure(IllegalArgumentException("Member ID cannot be empty"))
            return
        }

        // 1. Fetch member document first to preserve complete soft-delete archive (including child & spouse links)
        db.collection("persons").document(personId).get()
            .addOnSuccessListener { docSnapshot ->
                val person = documentToPerson(docSnapshot) ?: docSnapshot.toObject(Person::class.java)?.copy(id = personId)
                val targetTreeId = person?.treeId.orEmpty()
                val cachedTreeMembers = getCachedPersons(targetTreeId.ifBlank { null }).orEmpty()

                val performSoftDeleteArchive: (String, List<Person>) -> Unit = { treeName, treeMembers ->
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val archivedRecord = buildArchivedMemberSnapshot(
                        person = person ?: Person(id = personId),
                        treeMembers = treeMembers,
                        treeName = treeName,
                        deletedBy = currentUser?.email ?: "user",
                        deletedByName = currentUser?.displayName ?: "Editor"
                    )

                    // Write soft-delete archive into Firestore
                    db.collection("archived_deleted_members").document(personId)
                        .set(archivedRecord)
                        .addOnCompleteListener {
                            // Log deletion activity for Recent Activities
                            val memberName = "${person?.firstName.orEmpty()} ${person?.lastName.orEmpty()}".trim().ifBlank { "Family Member" }
                            val activity = ActivityRecord(
                                id = java.util.UUID.randomUUID().toString(),
                                treeId = targetTreeId,
                                userId = currentUser?.uid.orEmpty(),
                                userName = currentUser?.displayName ?: currentUser?.email ?: "Editor",
                                type = "MEMBER_DELETED",
                                description = "Deleted $memberName from $treeName. Tap to restore via Ops & Recovery Console.",
                                targetId = personId,
                                targetName = memberName,
                                timestamp = System.currentTimeMillis()
                            )
                            logActivity(activity, onSuccess = {}, onFailure = {})

                            // Now delete from active persons collection
                            db.collection("persons").document(personId)
                                .delete()
                                .addOnSuccessListener {
                                    removePersonFromCache(personId)
                                    cleanupRelativeReferences(personId)
                                    CentralTreeSynchronizer.getInstance().notifyMemberDeleted(
                                        deletedPersonId = personId,
                                        treeId = targetTreeId,
                                        affectedRelatives = (archivedRecord.formerFatherChildIds + archivedRecord.formerMotherChildIds + listOfNotNull(archivedRecord.originalSpouseId)).toSet(),
                                        sourceScreen = "deletePerson"
                                    )
                                    onSuccess()
                                }
                                .addOnFailureListener { onFailure(it) }
                        }
                }

                val resolveTreeAndArchive: (List<Person>) -> Unit = { treeMembers ->
                    if (targetTreeId.isNotBlank()) {
                        getTree(targetTreeId, onSuccess = { tree ->
                            performSoftDeleteArchive(tree?.name ?: "Family Tree", treeMembers)
                        }, onFailure = {
                            performSoftDeleteArchive("Family Tree", treeMembers)
                        })
                    } else {
                        performSoftDeleteArchive("Family Tree", treeMembers)
                    }
                }

                if (cachedTreeMembers.isNotEmpty()) {
                    resolveTreeAndArchive(cachedTreeMembers)
                } else if (targetTreeId.isNotBlank()) {
                    db.collection("persons").whereEqualTo("treeId", targetTreeId).get()
                        .addOnSuccessListener { snap ->
                            val members = snap.documents.mapNotNull { documentToPerson(it) }
                            resolveTreeAndArchive(members)
                        }
                        .addOnFailureListener {
                            resolveTreeAndArchive(emptyList())
                        }
                } else {
                    resolveTreeAndArchive(emptyList())
                }
            }
            .addOnFailureListener {
                // If fetch fails (e.g. offline/error), fallback to direct delete
                db.collection("persons").document(personId)
                    .delete()
                    .addOnSuccessListener {
                        removePersonFromCache(personId)
                        cleanupRelativeReferences(personId)
                        CentralTreeSynchronizer.getInstance().notifyMemberDeleted(
                            deletedPersonId = personId,
                            treeId = "",
                            affectedRelatives = emptySet(),
                            sourceScreen = "deletePerson"
                        )
                        onSuccess()
                    }
                    .addOnFailureListener { onFailure(it) }
            }
    }

    private fun cleanupRelativeReferences(personId: String) {
        val trimmedId = personId.trim()
        inMemoryPersonsCache = inMemoryPersonsCache?.map {
            var updated = it
            if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.fatherId, trimmedId)) updated = updated.copy(fatherId = null)
            if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.motherId, trimmedId)) updated = updated.copy(motherId = null)
            if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, trimmedId)) updated = updated.copy(spouseId = null, maritalStatus = "Single", marriageDate = "")
            updated
        }

        db.collection("persons").whereEqualTo("fatherId", personId).get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.update("fatherId", null)
                }
            }
        db.collection("persons").whereEqualTo("motherId", personId).get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.update("motherId", null)
                }
            }
        db.collection("persons").whereEqualTo("spouseId", personId).get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.update(mapOf("spouseId" to null, "maritalStatus" to "Single", "marriageDate" to ""))
                }
            }
    }

    // ══════════════════════════════════════════════════════════════
    // USER PROFILE & ACCOUNT MANAGEMENT (Module 1)
    // ══════════════════════════════════════════════════════════════

    fun saveUserProfile(
        profile: UserProfile,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users").document(profile.id)
            .set(profile)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getUserProfile(
        userId: String,
        onSuccess: (UserProfile?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                onSuccess(doc.toObject(UserProfile::class.java))
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Executes the Clan Stewardship & Safe Account Deletion Pipeline:
     * 1. Erases the user's private profile in `users` collection.
     * 2. Checks each tree the user owns or belongs to:
     *    - If solo unshared tree (0 other collaborators, memberCount <= 1):
     *      safely purges the personal tree using deleteTree(treeId).
     *    - If collaborative / shared clan tree:
     *      - Detaches user from tree_members.
     *      - Updates tree creator to "Former Clan Contributor" if tree.createdBy == userId.
     *      - Removes userId from owners list if present.
     *      - Updates persons.createdBy to "Former Clan Contributor" so ancestral nodes
     *        remain intact without dangling user references.
     * 3. Clears local preferences and notifies CentralTreeSynchronizer.
     */
    fun deleteUserAccount(
        userId: String,
        context: Context? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (userId.isBlank()) {
            onFailure(IllegalArgumentException("User ID cannot be empty"))
            return
        }

        // 1. Fetch all trees associated with the user
        getUserTrees(userId, onSuccess = { trees ->
            val soloTrees = mutableListOf<FamilyTree>()
            val sharedTrees = mutableListOf<FamilyTree>()

            for (tree in trees) {
                if (tree.ownerId == userId && tree.memberCount <= 1 && tree.coOwnerIds.isEmpty()) {
                    soloTrees.add(tree)
                } else {
                    sharedTrees.add(tree)
                }
            }

            // Purge solo trees
            for (solo in soloTrees) {
                deleteTree(solo.id, context = context, onSuccess = {}, onFailure = {})
            }

            // Transition shared trees to Clan Stewardship
            for (shared in sharedTrees) {
                if (shared.ownerId == userId) {
                    val nextOwner = shared.coOwnerIds.firstOrNull() ?: "Former Clan Contributor"
                    val updatedCoOwners = shared.coOwnerIds.filter { it != nextOwner && it != userId }
                    val updates = mutableMapOf<String, Any>(
                        "ownerId" to nextOwner,
                        "ownerName" to if (nextOwner == "Former Clan Contributor") "Former Clan Contributor" else "Clan Co-Owner",
                        "coOwnerIds" to updatedCoOwners
                    )
                    db.collection("trees").document(shared.id).update(updates)
                } else if (shared.coOwnerIds.contains(userId)) {
                    val updatedCoOwners = shared.coOwnerIds.filter { it != userId }
                    db.collection("trees").document(shared.id)
                        .update("coOwnerIds", updatedCoOwners)
                }
                // Detach from tree_members
                db.collection("tree_members")
                    .whereEqualTo("treeId", shared.id)
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { snaps ->
                        for (doc in snaps.documents) {
                            doc.reference.delete()
                        }
                    }
                // Update person records created by this user in shared trees
                db.collection("persons")
                    .whereEqualTo("treeId", shared.id)
                    .whereEqualTo("createdBy", userId)
                    .get()
                    .addOnSuccessListener { pSnaps ->
                        for (pDoc in pSnaps.documents) {
                            val isLiving = pDoc.getBoolean("isLiving") ?: true
                            val deathDate = pDoc.getString("deathDate").orEmpty()
                            val isActuallyLiving = isLiving && deathDate.isBlank()

                            val pUpdates = mutableMapOf<String, Any>(
                                "createdBy" to "Former Clan Contributor"
                            )
                            if (isActuallyLiving) {
                                pUpdates["isPrivate"] = true
                                pUpdates["firstName"] = "Private"
                                pUpdates["lastName"] = "Living Relative"
                                pUpdates["middleName"] = ""
                                pUpdates["biography"] = "[Redacted per Clan Stewardship & RA 10173]"
                            }
                            pDoc.reference.update(pUpdates)
                        }
                    }
            }

            // 2. Delete user profile record in 'users' collection
            db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener {
                    if (context != null) {
                        TreePreferences.clear(context)
                    }
                    clearCache()
                    CentralTreeSynchronizer.getInstance().stopRealtimeListener()
                    CentralTreeSynchronizer.getInstance().resetForTesting()
                    onSuccess()
                }
                .addOnFailureListener { onFailure(it) }

        }, onFailure = { err ->
            // If fetching trees fails, still delete the user profile document
            db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener {
                    if (context != null) {
                        TreePreferences.clear(context)
                    }
                    clearCache()
                    onSuccess()
                }
                .addOnFailureListener { onFailure(err) }
        })
    }

    fun saveOnboardingState(
        userId: String,
        state: UserOnboardingState,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (userId.isEmpty()) return
        db.collection("users").document(userId)
            .set(mapOf("onboardingState" to state), SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getOnboardingState(
        userId: String,
        onSuccess: (UserOnboardingState?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        if (userId.isEmpty()) {
            onSuccess(null)
            return
        }
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val state = doc.get("onboardingState", UserOnboardingState::class.java)
                onSuccess(state)
            }
            .addOnFailureListener { onFailure(it) }
    }

    // ══════════════════════════════════════════════════════════════
    // TREE OWNERSHIP & 6-CHARACTER INVITE CODE (Module 1, Fig 4 & 5)
    // ══════════════════════════════════════════════════════════════

    fun createTree(
        tree: FamilyTree,
        onSuccess: (FamilyTree) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val docRef = if (tree.id.isNotEmpty()) db.collection("trees").document(tree.id)
                     else db.collection("trees").document()
        val inviteCode = if (tree.inviteCode.isNotBlank()) tree.inviteCode.replace(" ", "").trim().uppercase()
                         else generateInviteCode()
        val resolvedEmail = tree.ownerEmail.ifBlank { FirebaseAuth.getInstance().currentUser?.email.orEmpty() }
        val treeWithId = tree.copy(
            id = docRef.id,
            inviteCode = inviteCode,
            ownerEmail = resolvedEmail,
            memberQuota = tree.effectiveQuota
        )

        docRef.set(treeWithId)
            .addOnSuccessListener {
                // Ensure invite code is saved in dedicated inviteCodes collection
                val inviteRecord = InviteCodeRecord(
                    code = inviteCode,
                    treeId = treeWithId.id,
                    treeName = treeWithId.name,
                    createdBy = treeWithId.ownerId,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = 0L,
                    usageLimit = 0,
                    usageCount = 0,
                    status = InviteCodeRecord.STATUS_ACTIVE
                )
                saveInviteCodeRecord(inviteRecord)

                // Automatically add creator as Owner
                val memberDoc = db.collection("tree_members").document()
                val ownerMember = TreeMember(
                    id = memberDoc.id,
                    treeId = treeWithId.id,
                    userId = treeWithId.ownerId,
                    userName = treeWithId.ownerName,
                    role = "Owner",
                    status = "Approved"
                )
                memberDoc.set(ownerMember)
                    .addOnSuccessListener { onSuccess(treeWithId) }
                    .addOnFailureListener { onSuccess(treeWithId) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getTree(
        treeId: String,
        onSuccess: (FamilyTree?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (treeId.isBlank()) {
            onSuccess(null)
            return
        }
        db.collection("trees").document(treeId).get()
            .addOnSuccessListener { doc ->
                onSuccess(documentToFamilyTree(doc))
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun saveInviteCodeRecord(
        record: InviteCodeRecord,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val normalizedCode = record.code.replace(" ", "").trim().uppercase()
        if (normalizedCode.isBlank()) return
        val recordToSave = record.copy(code = normalizedCode)
        db.collection("inviteCodes").document(normalizedCode)
            .set(recordToSave, SetOptions.merge())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getInviteCodeRecord(
        code: String,
        onSuccess: (InviteCodeRecord?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val normalizedCode = code.replace(" ", "").trim().uppercase()
        if (normalizedCode.isBlank()) {
            onSuccess(null)
            return
        }

        // 1. Direct document ID lookup in inviteCodes collection (O(1))
        db.collection("inviteCodes").document(normalizedCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val record = doc.toObject(InviteCodeRecord::class.java)
                    if (record != null) {
                        onSuccess(record)
                        return@addOnSuccessListener
                    }
                }

                // 2. Query lookup by field "code"
                db.collection("inviteCodes")
                    .whereEqualTo("code", normalizedCode)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            val record = querySnapshot.documents.first().toObject(InviteCodeRecord::class.java)
                            if (record != null) {
                                onSuccess(record)
                                return@addOnSuccessListener
                            }
                        }

                        // 3. Fallback: Search trees collection for legacy inviteCode
                        db.collection("trees")
                            .whereEqualTo("inviteCode", normalizedCode)
                            .get()
                            .addOnSuccessListener { treeSnapshot ->
                                if (!treeSnapshot.isEmpty) {
                                    val treeDoc = treeSnapshot.documents.first()
                                    val tree = documentToFamilyTree(treeDoc)
                                    if (tree != null) {
                                        val backfilled = InviteCodeRecord(
                                            code = normalizedCode,
                                            treeId = tree.id,
                                            treeName = tree.name,
                                            createdBy = tree.ownerId,
                                            createdAt = System.currentTimeMillis(),
                                            expiresAt = 0L,
                                            usageLimit = 0,
                                            usageCount = 0,
                                            status = InviteCodeRecord.STATUS_ACTIVE
                                        )
                                        saveInviteCodeRecord(backfilled)
                                        onSuccess(backfilled)
                                        return@addOnSuccessListener
                                    }
                                }

                                // 4. Fallback: Check all trees
                                db.collection("trees").get()
                                    .addOnSuccessListener { allTrees ->
                                        val matched = allTrees.documents
                                            .mapNotNull { documentToFamilyTree(it) }
                                            .firstOrNull { it.inviteCode.equals(normalizedCode, ignoreCase = true) }
                                        if (matched != null) {
                                            val backfilled = InviteCodeRecord(
                                                code = normalizedCode,
                                                treeId = matched.id,
                                                treeName = matched.name,
                                                createdBy = matched.ownerId,
                                                createdAt = System.currentTimeMillis(),
                                                expiresAt = 0L,
                                                usageLimit = 0,
                                                usageCount = 0,
                                                status = InviteCodeRecord.STATUS_ACTIVE
                                            )
                                            saveInviteCodeRecord(backfilled)
                                            updateTreeInviteCode(matched.id, normalizedCode, matched.name, matched.ownerId)
                                            onSuccess(backfilled)
                                        } else {
                                            onSuccess(null)
                                        }
                                    }
                                    .addOnFailureListener {
                                        onSuccess(null)
                                    }
                            }
                            .addOnFailureListener {
                                onSuccess(null)
                            }
                    }
                    .addOnFailureListener {
                        onSuccess(null)
                    }
            }
            .addOnFailureListener { primaryEx ->
                android.util.Log.w("FirestoreHelper", "Direct inviteCodes lookup failed: ${primaryEx.message}")
                // Fallback attempt with query lookup in case client has cached query data or doc lookup was blocked
                db.collection("inviteCodes")
                    .whereEqualTo("code", normalizedCode)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (!querySnapshot.isEmpty) {
                            val record = querySnapshot.documents.first().toObject(InviteCodeRecord::class.java)
                            if (record != null) {
                                onSuccess(record)
                                return@addOnSuccessListener
                            }
                        }
                        onFailure(primaryEx)
                    }
                    .addOnFailureListener {
                        onFailure(primaryEx)
                    }
            }
    }

    fun incrementInviteCodeUsage(
        code: String,
        currentUsage: Int,
        limit: Int,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val normalizedCode = code.replace(" ", "").trim().uppercase()
        if (normalizedCode.isBlank()) return
        val newCount = currentUsage + 1
        val newStatus = if (limit > 0 && newCount >= limit) InviteCodeRecord.STATUS_USED else InviteCodeRecord.STATUS_ACTIVE
        val updates = mapOf(
            "usageCount" to newCount,
            "status" to newStatus
        )
        db.collection("inviteCodes").document(normalizedCode)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateTreeInviteCode(
        treeId: String,
        inviteCode: String,
        treeName: String = "",
        createdBy: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (treeId.isBlank()) return
        val normalizedCode = inviteCode.replace(" ", "").trim().uppercase()
        val data = mapOf(
            "inviteCode" to normalizedCode,
            "id" to treeId
        )
        db.collection("trees").document(treeId)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                val inviteRecord = InviteCodeRecord(
                    code = normalizedCode,
                    treeId = treeId,
                    treeName = treeName,
                    createdBy = createdBy,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = 0L,
                    usageLimit = 0,
                    usageCount = 0,
                    status = InviteCodeRecord.STATUS_ACTIVE
                )
                saveInviteCodeRecord(inviteRecord, onSuccess = onSuccess, onFailure = onFailure)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getTreeByInviteCode(
        inviteCode: String,
        onSuccess: (FamilyTree?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val normalizedCode = inviteCode.replace(" ", "").trim().uppercase()
        getInviteCodeRecord(normalizedCode,
            onSuccess = { record ->
                if (record != null && record.treeId.isNotBlank()) {
                    getTree(record.treeId,
                        onSuccess = { tree ->
                            if (tree != null) {
                                onSuccess(tree)
                            } else {
                                searchTreesByInviteCode(normalizedCode, onSuccess, onFailure)
                            }
                        },
                        onFailure = {
                            searchTreesByInviteCode(normalizedCode, onSuccess, onFailure)
                        }
                    )
                } else {
                    searchTreesByInviteCode(normalizedCode, onSuccess, onFailure)
                }
            },
            onFailure = {
                searchTreesByInviteCode(normalizedCode, onSuccess, onFailure)
            }
        )
    }

    private fun searchTreesByInviteCode(
        normalizedCode: String,
        onSuccess: (FamilyTree?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("trees")
            .whereEqualTo("inviteCode", normalizedCode)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val firstDoc = result.documents.first()
                    onSuccess(documentToFamilyTree(firstDoc))
                } else {
                    db.collection("trees").get()
                        .addOnSuccessListener { allTrees ->
                            val matched = allTrees.documents
                                .mapNotNull { documentToFamilyTree(it) }
                                .firstOrNull { it.inviteCode.equals(normalizedCode, ignoreCase = true) }
                            if (matched != null) {
                                updateTreeInviteCode(matched.id, normalizedCode, matched.name, matched.ownerId)
                                onSuccess(matched.copy(inviteCode = normalizedCode))
                            } else {
                                onSuccess(null)
                            }
                        }
                        .addOnFailureListener {
                            onSuccess(null)
                        }
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Retrieves or generates an independent unique Clan Merge Code for the specified tree.
     * This code is exclusively authorized for non-destructive Master Tree C synthesis
     * and is completely isolated from general member join codes.
     */
    fun getOrCreateTreeMergeInviteCode(
        treeId: String,
        treeName: String,
        ownerId: String,
        ownerName: String = "Owner",
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (treeId.isBlank()) {
            onFailure(IllegalArgumentException("Tree ID cannot be blank"))
            return
        }
        getTree(treeId,
            onSuccess = { tree ->
                if (tree != null && tree.mergeInviteCode.isNotBlank()) {
                    val code = tree.mergeInviteCode.replace(" ", "").trim().uppercase()
                    val mergeDocRef = db.collection("mergeInviteCodes").document(code)
                    mergeDocRef.get().addOnSuccessListener { snap ->
                        if (!snap.exists()) {
                            val data = mapOf(
                                "code" to code,
                                "treeId" to tree.id,
                                "treeName" to tree.name,
                                "ownerId" to (tree.ownerId.ifBlank { ownerId }),
                                "ownerName" to (tree.ownerName.ifBlank { ownerName }),
                                "createdAt" to System.currentTimeMillis(),
                                "status" to "ACTIVE"
                            )
                            mergeDocRef.set(data)
                        }
                        onSuccess(code)
                    }.addOnFailureListener {
                        onSuccess(code)
                    }
                } else {
                    val generated = generateMergeInviteCode()
                    val treeRef = db.collection("trees").document(treeId)
                    val mergeDocRef = db.collection("mergeInviteCodes").document(generated)
                    val data = mapOf(
                        "code" to generated,
                        "treeId" to treeId,
                        "treeName" to (tree?.name?.ifBlank { treeName } ?: treeName),
                        "ownerId" to (tree?.ownerId?.ifBlank { ownerId } ?: ownerId),
                        "ownerName" to (tree?.ownerName?.ifBlank { ownerName } ?: ownerName),
                        "createdAt" to System.currentTimeMillis(),
                        "status" to "ACTIVE"
                    )
                    db.runBatch { batch ->
                        batch.update(treeRef, "mergeInviteCode", generated)
                        batch.set(mergeDocRef, data)
                    }.addOnSuccessListener {
                        onSuccess(generated)
                    }.addOnFailureListener {
                        treeRef.update("mergeInviteCode", generated)
                        mergeDocRef.set(data)
                            .addOnSuccessListener { onSuccess(generated) }
                            .addOnFailureListener { onFailure(it) }
                    }
                }
            },
            onFailure = { onFailure(it) }
        )
    }

    /**
     * Regenerates a fresh independent Clan Merge Code, invalidating any previous merge code.
     */
    fun regenerateTreeMergeInviteCode(
        treeId: String,
        treeName: String,
        ownerId: String,
        ownerName: String = "Owner",
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (treeId.isBlank()) {
            onFailure(IllegalArgumentException("Tree ID cannot be blank"))
            return
        }
        val newCode = generateMergeInviteCode()
        getTree(treeId,
            onSuccess = { tree ->
                val oldCode = tree?.mergeInviteCode?.trim()?.uppercase().orEmpty()
                val treeRef = db.collection("trees").document(treeId)
                val newDocRef = db.collection("mergeInviteCodes").document(newCode)
                val data = mapOf(
                    "code" to newCode,
                    "treeId" to treeId,
                    "treeName" to (tree?.name?.ifBlank { treeName } ?: treeName),
                    "ownerId" to (tree?.ownerId?.ifBlank { ownerId } ?: ownerId),
                    "ownerName" to (tree?.ownerName?.ifBlank { ownerName } ?: ownerName),
                    "createdAt" to System.currentTimeMillis(),
                    "status" to "ACTIVE"
                )
                db.runBatch { batch ->
                    if (oldCode.isNotBlank()) {
                        val oldDocRef = db.collection("mergeInviteCodes").document(oldCode)
                        batch.delete(oldDocRef)
                    }
                    batch.update(treeRef, "mergeInviteCode", newCode)
                    batch.set(newDocRef, data)
                }.addOnSuccessListener {
                    onSuccess(newCode)
                }.addOnFailureListener {
                    treeRef.update("mergeInviteCode", newCode)
                    newDocRef.set(data)
                        .addOnSuccessListener { onSuccess(newCode) }
                        .addOnFailureListener { onFailure(it) }
                }
            },
            onFailure = { onFailure(it) }
        )
    }

    /**
     * Looks up a Family Tree exclusively by its independent Clan Merge Code.
     * Searches the dedicated 'mergeInviteCodes' collection first, with fallback to 'trees.mergeInviteCode'.
     */
    fun findTreeByMergeInviteCode(
        code: String,
        onSuccess: (FamilyTree?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val normalizedCode = code.replace(" ", "").trim().uppercase()
        if (normalizedCode.isBlank()) {
            onSuccess(null)
            return
        }
        db.collection("mergeInviteCodes").document(normalizedCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getString("status") == "ACTIVE") {
                    val targetTreeId = doc.getString("treeId").orEmpty()
                    if (targetTreeId.isNotBlank()) {
                        getTree(targetTreeId, onSuccess = onSuccess, onFailure = onFailure)
                    } else {
                        onSuccess(null)
                    }
                } else {
                    db.collection("trees")
                        .whereEqualTo("mergeInviteCode", normalizedCode)
                        .get()
                        .addOnSuccessListener { res ->
                            if (!res.isEmpty) {
                                onSuccess(documentToFamilyTree(res.documents.first()))
                            } else {
                                // Graceful backward fallback to general invite code if entered
                                getTreeByInviteCode(normalizedCode, onSuccess = onSuccess, onFailure = onFailure)
                            }
                        }
                        .addOnFailureListener { onFailure(it) }
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // ══════════════════════════════════════════════════════════════
    // CLAN MERGE REQUEST & APPROVAL PIPELINE
    // ══════════════════════════════════════════════════════════════

    /**
     * Creates a new pending clan merge request if an active pending one doesn't already exist.
     * Prevents duplicate pending requests and notifies target tree owner.
     */
    fun createClanMergeRequest(
        request: ClanMergeRequest,
        onSuccess: (ClanMergeRequest) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (request.requesterTreeId.isBlank() || request.targetTreeId.isBlank() || request.requesterOwnerId.isBlank()) {
            onFailure(IllegalArgumentException("Tree IDs and Requester ID cannot be blank"))
            return
        }

        // Check if an active pending request already exists for this pair
        db.collection("clan_merge_requests")
            .whereEqualTo("requesterTreeId", request.requesterTreeId)
            .whereEqualTo("targetTreeId", request.targetTreeId)
            .whereEqualTo("requesterOwnerId", request.requesterOwnerId)
            .whereEqualTo("status", ClanMergeRequest.STATUS_PENDING)
            .get()
            .addOnSuccessListener { snap ->
                val existing = snap.documents.mapNotNull { documentToClanMergeRequest(it) }
                    .firstOrNull { !it.isExpired() }

                if (existing != null) {
                    onSuccess(existing)
                    return@addOnSuccessListener
                }

                val docRef = db.collection("clan_merge_requests").document()
                val finalRequest = request.copy(
                    id = docRef.id,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    status = ClanMergeRequest.STATUS_PENDING
                )

                docRef.set(finalRequest)
                    .addOnSuccessListener {
                        // Dispatch notification to target tree owner
                        if (finalRequest.targetOwnerId.isNotBlank()) {
                            val notif = NotificationRecord(
                                treeId = finalRequest.targetTreeId,
                                userId = finalRequest.targetOwnerId,
                                title = "Clan Merge Request Received",
                                message = "${finalRequest.requesterOwnerName} requested to merge '${finalRequest.requesterTreeName}' with your tree '${finalRequest.targetTreeName}'.",
                                type = "CLAN_MERGE_REQUEST",
                                targetId = finalRequest.id,
                                timestamp = System.currentTimeMillis()
                            )
                            addNotification(notif)
                        }
                        CentralTreeSynchronizer.getInstance().notifyClanMergeRequestUpdated(finalRequest)
                        onSuccess(finalRequest)
                    }
                    .addOnFailureListener { onFailure(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Looks up any active (pending or approved but uncompleted) merge request for a requester and target tree.
     */
    fun findActiveClanMergeRequest(
        requesterOwnerId: String,
        requesterTreeId: String,
        targetTreeId: String,
        onSuccess: (ClanMergeRequest?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (requesterOwnerId.isBlank() || requesterTreeId.isBlank() || targetTreeId.isBlank()) {
            onSuccess(null)
            return
        }

        db.collection("clan_merge_requests")
            .whereEqualTo("requesterOwnerId", requesterOwnerId)
            .whereEqualTo("requesterTreeId", requesterTreeId)
            .whereEqualTo("targetTreeId", targetTreeId)
            .get()
            .addOnSuccessListener { snap ->
                val requests = snap.documents.mapNotNull { documentToClanMergeRequest(it) }
                    .sortedByDescending { it.createdAt }

                // Prefer approved request if not yet completed/consumed, else pending if not expired
                val active = requests.firstOrNull { it.canUnlockSynthesis() }
                    ?: requests.firstOrNull { it.status == ClanMergeRequest.STATUS_PENDING && !it.isExpired() }
                    ?: requests.firstOrNull()

                onSuccess(active)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Real-time listener for pending clan merge requests targeted at this owner's trees.
     */
    fun listenToPendingClanMergeRequests(
        ownerId: String,
        onUpdate: (List<ClanMergeRequest>) -> Unit
    ): ListenerRegistration {
        return db.collection("clan_merge_requests")
            .whereEqualTo("targetOwnerId", ownerId)
            .whereEqualTo("status", ClanMergeRequest.STATUS_PENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { documentToClanMergeRequest(it) }
                    .filter { !it.isExpired() }
                    .sortedByDescending { it.createdAt }
                onUpdate(list)
            }
    }

    /**
     * Real-time listener for a specific clan merge request.
     */
    fun listenToClanMergeRequest(
        requestId: String,
        onUpdate: (ClanMergeRequest?) -> Unit
    ): ListenerRegistration {
        return db.collection("clan_merge_requests").document(requestId)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                onUpdate(documentToClanMergeRequest(snap))
            }
    }

    /**
     * Updates the status of a ClanMergeRequest (e.g. APPROVED, REJECTED, CANCELLED, COMPLETED, FAILED).
     * When completed, stores createdMasterTreeId to prevent duplicate execution.
     */
    fun updateClanMergeRequestStatus(
        requestId: String,
        newStatus: String,
        createdMasterTreeId: String = "",
        masterTreeId: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (requestId.isBlank()) {
            onFailure(IllegalArgumentException("Request ID cannot be blank"))
            return
        }

        val effectiveTreeId = if (masterTreeId.isNotBlank()) masterTreeId else createdMasterTreeId
        val docRef = db.collection("clan_merge_requests").document(requestId)
        docRef.get().addOnSuccessListener { snap ->
            val req = documentToClanMergeRequest(snap)
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "updatedAt" to System.currentTimeMillis()
            )
            if (effectiveTreeId.isNotBlank()) {
                updates["createdMasterTreeId"] = effectiveTreeId
            }

            docRef.update(updates)
                .addOnSuccessListener {
                    if (req != null) {
                        val updatedReq = req.copy(
                            status = newStatus,
                            updatedAt = System.currentTimeMillis(),
                            createdMasterTreeId = if (effectiveTreeId.isNotBlank()) effectiveTreeId else req.createdMasterTreeId
                        )

                        // If approved, notify requester
                        if (newStatus == ClanMergeRequest.STATUS_APPROVED && req.requesterOwnerId.isNotBlank()) {
                            val notif = NotificationRecord(
                                treeId = req.requesterTreeId,
                                userId = req.requesterOwnerId,
                                title = "Clan Merge Request Approved",
                                message = "Your request to merge '${req.requesterTreeName}' with '${req.targetTreeName}' was approved! You can now complete the synthesis in Merged Clan Space.",
                                type = "CLAN_MERGE_REQUEST",
                                targetId = requestId,
                                timestamp = System.currentTimeMillis()
                            )
                            addNotification(notif)
                        } else if (newStatus == ClanMergeRequest.STATUS_REJECTED && req.requesterOwnerId.isNotBlank()) {
                            val notif = NotificationRecord(
                                treeId = req.requesterTreeId,
                                userId = req.requesterOwnerId,
                                title = "Clan Merge Request Declined",
                                message = "Your request to merge '${req.requesterTreeName}' with '${req.targetTreeName}' was declined by the tree owner.",
                                type = "CLAN_MERGE_REQUEST",
                                targetId = requestId,
                                timestamp = System.currentTimeMillis()
                            )
                            addNotification(notif)
                        }

                        CentralTreeSynchronizer.getInstance().notifyClanMergeRequestUpdated(updatedReq)
                    }
                    onSuccess()
                }
                .addOnFailureListener { onFailure(it) }
        }.addOnFailureListener { onFailure(it) }
    }

    fun addTreeMember(
        treeMember: TreeMember,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val docRef = db.collection("tree_members").document()
        val memberWithId = treeMember.copy(id = docRef.id)
        docRef.set(memberWithId)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getTreeMembers(
        treeId: String,
        onSuccess: (List<TreeMember>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("tree_members")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                val members = result.map { it.toObject(TreeMember::class.java) }
                onSuccess(members)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateTreeMemberRole(
        memberId: String,
        role: String,
        status: String = "Approved",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("tree_members").document(memberId)
            .update(mapOf("role" to role, "status" to status))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deleteTreeMember(
        memberId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("tree_members").document(memberId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getUserMembership(
        userId: String,
        treeId: String,
        onSuccess: (TreeMember?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("tree_members")
            .whereEqualTo("userId", userId)
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    onSuccess(result.documents.first().toObject(TreeMember::class.java))
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getUserTrees(
        userId: String,
        includeMergedClan: Boolean = false,
        onSuccess: (List<FamilyTree>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (userId.isBlank()) {
            onSuccess(emptyList())
            return
        }
        db.collection("trees")
            .whereEqualTo("ownerId", userId)
            .get()
            .addOnSuccessListener { result ->
                val ownedTrees = result.documents.mapNotNull { documentToFamilyTree(it) }.toMutableList()
                // Also check if older trees used createdBy instead of ownerId
                db.collection("trees")
                    .whereEqualTo("createdBy", userId)
                    .get()
                    .addOnSuccessListener { createdByResult ->
                        val createdTrees = createdByResult.documents.mapNotNull { documentToFamilyTree(it) }
                        for (ct in createdTrees) {
                            if (ownedTrees.none { it.id == ct.id }) {
                                ownedTrees.add(ct)
                            }
                        }

                        // Also query trees where user is in coOwnerIds array
                        db.collection("trees")
                            .whereArrayContains("coOwnerIds", userId)
                            .get()
                            .addOnSuccessListener { coOwnersResult ->
                                val coOwnerTrees = coOwnersResult.documents.mapNotNull { documentToFamilyTree(it) }
                                for (cot in coOwnerTrees) {
                                    if (ownedTrees.none { it.id == cot.id }) {
                                        ownedTrees.add(cot)
                                    }
                                }

                                queryApprovedTreeMembers(userId, ownedTrees, includeMergedClan, onSuccess)
                            }
                            .addOnFailureListener {
                                queryApprovedTreeMembers(userId, ownedTrees, includeMergedClan, onSuccess)
                            }
                    }
                    .addOnFailureListener {
                        val filtered = if (includeMergedClan) ownedTrees else ownedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                        onSuccess(filtered.distinctBy { it.id })
                    }
            }
            .addOnFailureListener { onFailure(it) }
    }

    private fun queryApprovedTreeMembers(
        userId: String,
        ownedTrees: MutableList<FamilyTree>,
        includeMergedClan: Boolean,
        onSuccess: (List<FamilyTree>) -> Unit
    ) {
        db.collection("tree_members")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "Approved")
            .get()
            .addOnSuccessListener { memberResult ->
                val memberTreeIds = memberResult.documents
                    .mapNotNull { it.getString("treeId") }
                    .filter { it.isNotBlank() && ownedTrees.none { t -> t.id == it } }
                    .distinct()

                if (memberTreeIds.isEmpty()) {
                    val filtered = if (includeMergedClan) ownedTrees else ownedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                    onSuccess(filtered.distinctBy { it.id })
                } else {
                    val combinedTrees = ownedTrees.toMutableList()
                    var remaining = memberTreeIds.size
                    for (mTreeId in memberTreeIds) {
                        getTree(mTreeId,
                            onSuccess = { fetchedTree ->
                                if (fetchedTree != null && combinedTrees.none { it.id == fetchedTree.id }) {
                                    combinedTrees.add(fetchedTree)
                                }
                                remaining--
                                if (remaining <= 0) {
                                    val filtered = if (includeMergedClan) combinedTrees else combinedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                                    onSuccess(filtered.distinctBy { it.id })
                                }
                            },
                            onFailure = {
                                remaining--
                                if (remaining <= 0) {
                                    val filtered = if (includeMergedClan) combinedTrees else combinedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                                    onSuccess(filtered.distinctBy { it.id })
                                }
                            }
                        )
                    }
                }
            }
            .addOnFailureListener {
                val filtered = if (includeMergedClan) ownedTrees else ownedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                onSuccess(filtered.distinctBy { it.id })
            }
    }

    /**
     * Backward-compatible overload for getUserTrees without includeMergedClan parameter.
     */
    fun getUserTrees(
        userId: String,
        onSuccess: (List<FamilyTree>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getUserTrees(userId, includeMergedClan = false, onSuccess = onSuccess, onFailure = onFailure)
    }

    /**
     * Retrieves all merged clan trees (Master Tree C) owned by or shared with the user.
     * Incorporates automatic auto-discovery and self-repair for existing clan trees.
     */
    fun getMergedClanTrees(
        userId: String,
        onSuccess: (List<FamilyTree>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getUserTrees(userId, includeMergedClan = true,
            onSuccess = { allTrees ->
                val clanTrees = allTrees.filter { it.treeType == FamilyTree.TREE_TYPE_MERGED_CLAN }.distinctBy { it.id }.toMutableList()

                // Auto-discovery & auto-repair pass:
                // Scan MERGED_CLAN trees in case an existing clan tree was synthesized before multi-owner
                // registration, or where this user's personal tree is one of the merged families.
                db.collection("trees")
                    .whereEqualTo("treeType", FamilyTree.TREE_TYPE_MERGED_CLAN)
                    .get()
                    .addOnSuccessListener { clanDocs ->
                        val potentialTrees = clanDocs.documents.mapNotNull { documentToFamilyTree(it) }
                        val personalTrees = allTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                        val personalTreeIds = personalTrees.map { it.id }.filter { it.isNotBlank() }.toSet()
                        val personalTreeNames = personalTrees.map { it.name.lowercase().replace(" family tree", "").replace(" tree", "").trim() }
                            .filter { it.length >= 3 }
                            .toSet()

                        for (pt in potentialTrees) {
                            if (clanTrees.none { it.id == pt.id }) {
                                val matchesSourceTree = (pt.sourceTree1Id.isNotBlank() && pt.sourceTree1Id in personalTreeIds) ||
                                                        (pt.sourceTree2Id.isNotBlank() && pt.sourceTree2Id in personalTreeIds)
                                val matchesCoOwner = pt.coOwnerIds.contains(userId)
                                val matchesName = personalTreeNames.any { nameSnippet -> pt.name.lowercase().contains(nameSnippet) }

                                if (matchesSourceTree || matchesCoOwner || matchesName) {
                                    clanTrees.add(pt)

                                    // Auto-repair Firestore co-ownership record in background
                                    if (userId.isNotBlank()) {
                                        db.collection("trees").document(pt.id)
                                            .update("coOwnerIds", com.google.firebase.firestore.FieldValue.arrayUnion(userId))

                                        db.collection("tree_members")
                                            .whereEqualTo("treeId", pt.id)
                                            .whereEqualTo("userId", userId)
                                            .get()
                                            .addOnSuccessListener { memSnap ->
                                                if (memSnap.isEmpty) {
                                                    val memDoc = db.collection("tree_members").document()
                                                    val repairMember = TreeMember(
                                                        id = memDoc.id,
                                                        treeId = pt.id,
                                                        userId = userId,
                                                        userName = "Owner",
                                                        role = "Owner",
                                                        status = "Approved"
                                                    )
                                                    memDoc.set(repairMember)
                                                }
                                            }
                                    }
                                }
                            }
                        }
                        onSuccess(clanTrees.distinctBy { it.id })
                    }
                    .addOnFailureListener {
                        onSuccess(clanTrees.distinctBy { it.id })
                    }
            },
            onFailure = onFailure
        )
    }

    /**
     * Synthesizes and saves a brand-new Master Clan Tree C along with its cloned members
     * in an atomic Firestore batch. Original trees are untouched and strictly read-only.
     * Dual ownership is granted to both tree owners, ensuring equal visibility and permissions.
     */
    fun saveMasterTreeAndMembers(
        masterTree: FamilyTree,
        members: List<Person>,
        partnerTreeOwnerId: String? = null,
        partnerTreeOwnerName: String? = null,
        onSuccess: (FamilyTree) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val treeDocRef = if (masterTree.id.isNotEmpty()) db.collection("trees").document(masterTree.id)
                         else db.collection("trees").document()
        val inviteCode = if (masterTree.inviteCode.isNotBlank()) masterTree.inviteCode.replace(" ", "").trim().uppercase()
                         else generateInviteCode()
        val mergeCode = if (masterTree.mergeInviteCode.isNotBlank()) masterTree.mergeInviteCode.replace(" ", "").trim().uppercase()
                        else generateMergeInviteCode()

        val allCoOwners = (masterTree.coOwnerIds + listOfNotNull(masterTree.ownerId, partnerTreeOwnerId))
            .filter { it.isNotBlank() }
            .distinct()

        val finalTree = masterTree.copy(
            id = treeDocRef.id,
            inviteCode = inviteCode,
            memberCount = members.size,
            treeType = FamilyTree.TREE_TYPE_MERGED_CLAN,
            mergeInviteCode = mergeCode,
            coOwnerIds = allCoOwners
        )

        val batch = db.batch()
        batch.set(treeDocRef, finalTree)

        // Save merge code record in dedicated mergeInviteCodes collection
        val mergeDocRef = db.collection("mergeInviteCodes").document(mergeCode)
        val mergeData = mapOf(
            "code" to mergeCode,
            "treeId" to finalTree.id,
            "treeName" to finalTree.name,
            "ownerId" to finalTree.ownerId,
            "ownerName" to finalTree.ownerName,
            "createdAt" to System.currentTimeMillis(),
            "status" to "ACTIVE"
        )
        batch.set(mergeDocRef, mergeData)

        // Save invite code record in dedicated collection
        val inviteRecord = InviteCodeRecord(
            code = inviteCode,
            treeId = finalTree.id,
            treeName = finalTree.name,
            createdBy = finalTree.ownerId,
            createdAt = System.currentTimeMillis(),
            expiresAt = 0L,
            usageLimit = 0,
            usageCount = 0,
            status = InviteCodeRecord.STATUS_ACTIVE
        )
        val inviteDocRef = db.collection("inviteCodes").document(inviteCode)
        batch.set(inviteDocRef, inviteRecord)

        // Add creator as Owner in tree_members
        val memberDoc = db.collection("tree_members").document()
        val ownerMember = TreeMember(
            id = memberDoc.id,
            treeId = finalTree.id,
            userId = finalTree.ownerId,
            userName = finalTree.ownerName,
            role = "Owner",
            status = "Approved"
        )
        batch.set(memberDoc, ownerMember)

        // Add partner tree owner as Owner in tree_members
        if (!partnerTreeOwnerId.isNullOrBlank() && partnerTreeOwnerId != finalTree.ownerId) {
            val partnerMemberDoc = db.collection("tree_members").document()
            val partnerMember = TreeMember(
                id = partnerMemberDoc.id,
                treeId = finalTree.id,
                userId = partnerTreeOwnerId,
                userName = partnerTreeOwnerName?.ifBlank { "Co-Owner" } ?: "Co-Owner",
                role = "Owner",
                status = "Approved"
            )
            batch.set(partnerMemberDoc, partnerMember)
        }

        // Set all cloned persons in persons collection, chunked by 200 to prevent Firestore 500-op batch crash
        val chunks = members.chunked(MergeRollbackSnapshot.MAX_FIRESTORE_BATCH_CHUNK)
        val firstChunk = chunks.firstOrNull().orEmpty()
        for (person in firstChunk) {
            val personDocRef = db.collection("persons").document(person.id)
            batch.set(personDocRef, person.copy(treeId = finalTree.id))
        }

        batch.commit()
            .addOnSuccessListener {
                val remainingChunks = chunks.drop(1)
                if (remainingChunks.isEmpty()) {
                    com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyTreeCreated(finalTree)
                    onSuccess(finalTree)
                } else {
                    val extraTasks = remainingChunks.map { extraChunk ->
                        val extraBatch = db.batch()
                        for (person in extraChunk) {
                            val personDocRef = db.collection("persons").document(person.id)
                            extraBatch.set(personDocRef, person.copy(treeId = finalTree.id))
                        }
                        extraBatch.commit()
                    }
                    com.google.android.gms.tasks.Tasks.whenAllComplete(extraTasks)
                        .addOnSuccessListener {
                            com.example.btproject2.sync.CentralTreeSynchronizer.getInstance().notifyTreeCreated(finalTree)
                            onSuccess(finalTree)
                        }
                        .addOnFailureListener { onFailure(it) }
                }
                if (false)
                onSuccess(finalTree)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Backward-compatible overload for saveMasterTreeAndMembers without partner owner params.
     */
    fun saveMasterTreeAndMembers(
        masterTree: FamilyTree,
        members: List<Person>,
        onSuccess: (FamilyTree) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        saveMasterTreeAndMembers(masterTree, members, null, null, onSuccess, onFailure)
    }

    // =========================================================================
    // PRE-MERGE SNAPSHOT & 1-TAP UNDO ROLLBACK ENGINE (PILLAR 2 - ITEM 7)
    // =========================================================================

    fun saveMergeRollbackSnapshot(
        snapshot: MergeRollbackSnapshot,
        onSuccess: (MergeRollbackSnapshot) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val docRef = if (snapshot.id.isNotBlank()) db.collection("merge_snapshots").document(snapshot.id)
                     else db.collection("merge_snapshots").document()
        val finalSnapshot = snapshot.copy(id = docRef.id, rolledBack = false)
        lastActiveMergeSnapshot = finalSnapshot
        docRef.set(finalSnapshot)
            .addOnSuccessListener { onSuccess(finalSnapshot) }
            .addOnFailureListener { onFailure(it) }
    }

    fun fetchLatestActiveMergeSnapshot(
        ownerId: String,
        onSuccess: (MergeRollbackSnapshot?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val cached = lastActiveMergeSnapshot
        if (cached != null && !cached.rolledBack && (ownerId.isBlank() || cached.ownerId == ownerId)) {
            onSuccess(cached)
        }
        if (ownerId.isBlank()) {
            if (cached == null) onSuccess(null)
            return
        }
        db.collection("merge_snapshots")
            .whereEqualTo("ownerId", ownerId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val active = querySnapshot.documents
                    .mapNotNull { doc ->
                        val obj = doc.toObject(MergeRollbackSnapshot::class.java)
                        val rb = (doc.getBoolean("rolledBack")) ?: obj?.rolledBack ?: false
                        obj?.copy(id = doc.id, rolledBack = rb)
                    }
                    .filter { !it.rolledBack }
                    .maxByOrNull { it.createdAt }
                lastActiveMergeSnapshot = active
                onSuccess(active)
            }
            .addOnFailureListener { err ->
                if (cached == null) onFailure(err)
            }
    }

    fun executeMergeRollback(
        snapshot: MergeRollbackSnapshot,
        context: Context? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        fun markSnapshotRolledBackAndFinish() {
            val rolledBackCopy = snapshot.copy(rolledBack = true)
            if (lastActiveMergeSnapshot?.id == snapshot.id) {
                lastActiveMergeSnapshot = null
            }
            if (snapshot.id.isNotBlank()) {
                db.collection("merge_snapshots").document(snapshot.id)
                    .set(rolledBackCopy, SetOptions.merge())
                    .addOnCompleteListener { onSuccess() }
            } else {
                onSuccess()
            }
        }

        if (snapshot.snapshotType == MergeRollbackSnapshot.TYPE_INTRA_BRANCH) {
            val prevA = snapshot.previousPersonA
            val prevB = snapshot.previousPersonB
            if (prevA != null && prevB != null) {
                updatePerson(prevA, onSuccess = {
                    updatePerson(prevB, onSuccess = {
                        CentralTreeSynchronizer.getInstance().notifyRelationshipCorrected(
                            primaryPerson = prevA,
                            affectedRelatives = setOf(prevB.id),
                            sourceScreen = "MergeRollbackEngine"
                        )
                        markSnapshotRolledBackAndFinish()
                    }, onFailure = onFailure)
                }, onFailure = onFailure)
            } else if (prevA != null) {
                updatePerson(prevA, onSuccess = {
                    CentralTreeSynchronizer.getInstance().notifyRelationshipCorrected(
                        primaryPerson = prevA,
                        sourceScreen = "MergeRollbackEngine"
                    )
                    markSnapshotRolledBackAndFinish()
                }, onFailure = onFailure)
            } else {
                markSnapshotRolledBackAndFinish()
            }
        } else {
            val targetTreeId = snapshot.synthesizedMasterTreeId
            if (targetTreeId.isNotBlank()) {
                deleteTree(
                    treeId = targetTreeId,
                    context = context,
                    onSuccess = { markSnapshotRolledBackAndFinish() },
                    onFailure = onFailure
                )
            } else {
                markSnapshotRolledBackAndFinish()
            }
        }
    }

    /**
     * Safely and comprehensively deletes a tree (e.g. a Merged Clan Tree or personal tree),
     * wiping all associated records atomically to prevent orphaned data.
     * Purges:
     * 1. trees/{treeId} document
     * 2. persons where treeId == treeId
     * 3. tree_members where treeId == treeId
     * 4. mergeInviteCodes where treeId == treeId
     * 5. inviteCodes where treeId == treeId
     * 6. inMemoryPersonsCache purge
     * 7. TreePreferences active_tree fallback
     * 8. CentralTreeSynchronizer event broadcast
     */
    fun deleteTree(
        treeId: String,
        context: Context? = null,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (treeId.isBlank()) {
            onSuccess()
            return
        }

        db.collection("trees").document(treeId).delete()
            .addOnSuccessListener {
                val pQuery = db.collection("persons").whereEqualTo("treeId", treeId).get()
                val mQuery = db.collection("tree_members").whereEqualTo("treeId", treeId).get()
                val mergeQuery = db.collection("mergeInviteCodes").whereEqualTo("treeId", treeId).get()
                val invQuery = db.collection("inviteCodes").whereEqualTo("treeId", treeId).get()

                Tasks.whenAllComplete(pQuery, mQuery, mergeQuery, invQuery).addOnCompleteListener { taskList ->
                    val docsToDelete = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                    if (taskList.isSuccessful && taskList.result != null) {
                        for (t in taskList.result) {
                            if (t.isSuccessful) {
                                val snap = t.result as? com.google.firebase.firestore.QuerySnapshot
                                if (snap != null && !snap.isEmpty) {
                                    docsToDelete.addAll(snap.documents)
                                }
                            }
                        }
                    }

                    fun finalizeDeletion() {
                        // Purge deleted tree members from in-memory cache
                        inMemoryPersonsCache = inMemoryPersonsCache?.filter { it.treeId != treeId }

                        // Active tree fallback if context is provided
                        if (context != null) {
                            val activeId = TreePreferences.getActiveTreeId(context)
                            if (activeId == treeId) {
                                val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                                if (currentUid.isNotBlank()) {
                                    getUserTrees(currentUid, includeMergedClan = false,
                                        onSuccess = { personalTrees ->
                                            val fallback = personalTrees.firstOrNull()
                                            if (fallback != null) {
                                                TreePreferences.setActiveTree(context, fallback.id, fallback.name)
                                            } else {
                                                TreePreferences.clear(context)
                                            }
                                        },
                                        onFailure = {
                                            TreePreferences.clear(context)
                                        }
                                    )
                                } else {
                                    TreePreferences.clear(context)
                                }
                            }
                        }

                        CentralTreeSynchronizer.getInstance().notifyTreeDeleted(treeId, sourceScreen = "FirestoreHelper.deleteTree")
                        onSuccess()
                    }

                    if (docsToDelete.isEmpty()) {
                        finalizeDeletion()
                    } else {
                        val chunks = docsToDelete.chunked(400)
                        val commitTasks = chunks.map { chunk ->
                            val batch = db.batch()
                            for (doc in chunk) {
                                batch.delete(doc.reference)
                            }
                            batch.commit()
                        }
                        Tasks.whenAllComplete(commitTasks).addOnCompleteListener {
                            finalizeDeletion()
                        }
                    }
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Backward-compatible overload for callers without context.
     */
    fun deleteTree(
        treeId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        deleteTree(treeId, context = null, onSuccess = onSuccess, onFailure = onFailure)
    }

    // ══════════════════════════════════════════════════════════════
    // NOTIFICATION RECORDS (DFD Store D7 per Capstone Manuscript)
    // ══════════════════════════════════════════════════════════════

    fun addNotification(
        notification: NotificationRecord,
        onSuccess: (String) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val docRef = db.collection("notifications").document()
        val record = notification.copy(id = docRef.id)
        docRef.set(record)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { onFailure(it) }
    }

    fun addNotification(
        treeId: String,
        userId: String,
        title: String,
        message: String,
        type: String,
        targetId: String = "",
        onSuccess: (String) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val record = NotificationRecord(
            treeId = treeId,
            userId = userId,
            title = title,
            message = message,
            type = type,
            targetId = targetId,
            timestamp = System.currentTimeMillis()
        )
        addNotification(record, onSuccess, onFailure)
    }

    /**
     * Resolves the user's current role ("Owner", "Editor", or "Viewer") in [treeId]
     * via [getUserMembership], defaulting safely to "Viewer" if not found.
     */
    fun getUserRole(
        treeId: String,
        userId: String,
        onResult: (String) -> Unit
    ) {
        if (treeId.isBlank() || userId.isBlank()) {
            onResult("Viewer")
            return
        }
        getUserMembership(
            userId = userId,
            treeId = treeId,
            onSuccess = { member ->
                onResult(member?.role?.takeIf { it.isNotBlank() } ?: "Viewer")
            },
            onFailure = {
                onResult("Viewer")
            }
        )
    }

    /**
     * Sends a Botanical Golden Leaf Role Upgrade Request notification directly to the Tree Owner's
     * notification center in Cloud Firestore.
     */
    fun sendRoleUpgradeRequestToOwner(
        treeId: String,
        treeName: String = "",
        ownerId: String = "",
        requesterId: String,
        requesterName: String,
        currentRole: String,
        requestedRole: String,
        featureName: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val cleanRequester = requesterName.ifBlank { "A family member" }
        val cleanFeature = featureName.ifBlank { "tree management features" }
        val dispatchToOwner: (String, String) -> Unit = { resolvedOwnerId, resolvedTreeName ->
            val treeLabel = resolvedTreeName.ifBlank { "your family tree" }
            addNotification(
                treeId = treeId,
                userId = resolvedOwnerId,
                title = "🍃 Role Upgrade Request",
                message = "$cleanRequester ($currentRole) sent a Golden Leaf requesting an upgrade to $requestedRole in $treeLabel to access: $cleanFeature.",
                type = "role_upgrade_request",
                targetId = requesterId,
                onSuccess = { onComplete(true) },
                onFailure = { onComplete(false) }
            )
        }

        if (ownerId.isNotBlank() || treeId.isBlank()) {
            dispatchToOwner(ownerId, treeName)
        } else {
            getTree(
                treeId = treeId,
                onSuccess = { tree ->
                    dispatchToOwner(tree?.ownerId.orEmpty(), tree?.name ?: treeName)
                },
                onFailure = {
                    dispatchToOwner("", treeName)
                }
            )
        }
    }

    private fun documentToNotificationRecord(doc: com.google.firebase.firestore.DocumentSnapshot): NotificationRecord? {
        val parsed = doc.toObject(NotificationRecord::class.java) ?: return null
        val isReadFlag = doc.getBoolean("isRead") ?: doc.getBoolean("read") ?: parsed.isRead
        val cleanTitle = com.example.btproject2.utils.NotificationHelper.sanitizeNotificationTitle(parsed.title)
        if (cleanTitle != parsed.title && doc.id.isNotBlank()) {
            doc.reference.update("title", cleanTitle)
        }
        return parsed.copy(id = doc.id, title = cleanTitle, isRead = isReadFlag)
    }

    fun getNotifications(
        treeId: String,
        userId: String,
        onSuccess: (List<NotificationRecord>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (treeId.isEmpty()) {
            db.collection("notifications")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { result ->
                    val list = result.mapNotNull { doc ->
                        documentToNotificationRecord(doc)
                    }.sortedByDescending { it.timestamp }
                    onSuccess(list)
                }
                .addOnFailureListener { onFailure(it) }
            return
        }

        db.collection("notifications")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { treeResult ->
                val treeList = treeResult.mapNotNull { doc ->
                    documentToNotificationRecord(doc)
                }.filter { it.userId.isEmpty() || it.userId == userId }

                if (userId.isEmpty()) {
                    onSuccess(treeList.sortedByDescending { it.timestamp })
                    return@addOnSuccessListener
                }

                // Also fetch user-specific notifications (like welcome or account alerts)
                db.collection("notifications")
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("treeId", "")
                    .get()
                    .addOnSuccessListener { userResult ->
                        val userList = userResult.mapNotNull { doc ->
                            documentToNotificationRecord(doc)
                        }
                        val combined = (treeList + userList).distinctBy { it.id }.sortedByDescending { it.timestamp }
                        onSuccess(combined)
                    }
                    .addOnFailureListener {
                        onSuccess(treeList.sortedByDescending { it.timestamp })
                    }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun markNotificationAsRead(
        notificationId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (notificationId.isBlank()) return
        db.collection("notifications").document(notificationId)
            .update(mapOf("isRead" to true, "read" to true))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getUnreadNotificationCount(
        treeId: String,
        userId: String,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getNotifications(treeId, userId,
            onSuccess = { notifications ->
                val unreadCount = notifications.count { !it.isRead }
                onSuccess(unreadCount)
            },
            onFailure = { onFailure(it) }
        )
    }

    // ══════════════════════════════════════════════════════════════
    // ACTIVITY RECORDS (DFD Store D8 per Capstone Manuscript)
    // ══════════════════════════════════════════════════════════════

    fun logActivity(
        activity: ActivityRecord,
        onSuccess: (String) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val docRef = db.collection("activities").document()
        val record = activity.copy(id = docRef.id)
        docRef.set(record)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { onFailure(it) }
    }

    fun logActivity(
        treeId: String,
        userId: String,
        userName: String,
        type: String,
        description: String,
        targetId: String = "",
        targetName: String = "",
        onSuccess: (String) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val record = ActivityRecord(
            treeId = treeId,
            userId = userId,
            userName = userName,
            type = type,
            description = description,
            targetId = targetId,
            targetName = targetName,
            timestamp = System.currentTimeMillis()
        )
        logActivity(record, onSuccess, onFailure)
    }

    fun getRecentActivities(
        treeId: String,
        limit: Long = 10,
        onSuccess: (List<ActivityRecord>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Query by treeId only and sort by timestamp client-side so Firestore never fails with FAILED_PRECONDITION
        db.collection("activities")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { doc ->
                    doc.toObject(ActivityRecord::class.java)?.copy(id = doc.id)
                }.sortedByDescending { it.timestamp }
                 .take(limit.toInt())
                onSuccess(list)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Executes atomic merge of duplicate member records (FR-08).
     * Updates keeperPerson, reparents affected children, deletes redundant record, updates spouse links,
     * and synchronizes inMemoryPersonsCache + CentralTreeSynchronizer.
     */
    fun mergePersons(
        treeId: String,
        mergedPerson: Person,
        deletedPersonId: String,
        affectedChildren: List<Person>,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val batch = db.batch()

        // 1. Update the keeper person document
        val keeperRef = db.collection("persons").document(mergedPerson.id)
        batch.set(keeperRef, mergedPerson)

        // 2. Reparent all affected children
        for (child in affectedChildren) {
            val childRef = db.collection("persons").document(child.id)
            val updates = mutableMapOf<String, Any>()
            if (child.fatherId == deletedPersonId) {
                updates["fatherId"] = mergedPerson.id
            }
            if (child.motherId == deletedPersonId) {
                updates["motherId"] = mergedPerson.id
            }
            if (updates.isNotEmpty()) {
                batch.update(childRef, updates)
            }
        }

        // 3. Delete the redundant person document
        val deletedRef = db.collection("persons").document(deletedPersonId)
        batch.delete(deletedRef)

        // 4. If spouse was pointing to deletedPersonId, re-link to mergedPerson.id
        if (!mergedPerson.spouseId.isNullOrEmpty()) {
            val spouseRef = db.collection("persons").document(mergedPerson.spouseId!!)
            batch.update(spouseRef, "spouseId", mergedPerson.id)
        }

        batch.commit()
            .addOnSuccessListener {
                applyMergePersonsToCache(mergedPerson, deletedPersonId, affectedChildren)
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Connects two disconnected branch members via marriage or parentage (FR-08).
     */
    fun connectBranchMembers(
        treeId: String,
        personAId: String,
        personBId: String,
        connectionType: String, // "SPOUSE", "PARENT_OF", "CHILD_OF"
        personAGender: String,
        personBGender: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        when (connectionType) {
            "SPOUSE" -> {
                updateSpouse(personAId, personBId, "Married", onSuccess = onSuccess, onFailure = onFailure)
            }
            "PARENT_OF" -> {
                setChildParent(childId = personBId, parentId = personAId, parentGender = personAGender, onSuccess = onSuccess, onFailure = onFailure)
            }
            "CHILD_OF" -> {
                setChildParent(childId = personAId, parentId = personBId, parentGender = personBGender, onSuccess = onSuccess, onFailure = onFailure)
            }
            else -> {
                onFailure(IllegalArgumentException("Unsupported connection type: $connectionType"))
            }
        }
    }

    data class BulkImportReport(
        val totalProcessed: Int,
        val savedPersonsCount: Int,
        val excludedSpouseCount: Int,
        val importErrorReports: List<String>,
        val auditReport: com.example.btproject2.service.FamilyRelationshipService.TreeAuditReport
    )

    /**
     * Authoritative bulk import of family records.
     * 1. Checks every spouse relationship before saving it.
     * 2. If a relationship is invalid, does NOT save it and includes it in an import error report with the reason.
     * 3. Validates the sanitized tree using FamilyRelationshipService.auditFamilyTree().
     * 4. Persists the clean records atomically.
     */
    fun importFamilyData(
        treeId: String,
        persons: List<Person>,
        onSuccess: (BulkImportReport) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val treeMap = persons.associateBy { it.id }.toMutableMap()
        val importErrorReports = mutableListOf<String>()
        val invalidSpousePairs = mutableSetOf<String>()

        // 1. Check every spouse relationship before saving it
        for (person in persons) {
            val sId = person.spouseId?.trim()
            if (!sId.isNullOrEmpty()) {
                val pairKey = if (person.id < sId) "${person.id}__$sId" else "${sId}__${person.id}"
                if (pairKey in invalidSpousePairs) continue

                val spouseCandidate = treeMap[sId] ?: Person(id = sId)
                val sValidation = centralFamilyService.checkProposedRelationship(
                    type = ProposedRelationshipType.SPOUSE,
                    primaryPersonId = person.id,
                    secondaryPersonId = sId,
                    tree = treeMap
                )

                if (!sValidation.isAllowed) {
                    invalidSpousePairs.add(pairKey)
                    val errorMsg = com.example.btproject2.utils.SpouseValidationMessageHelper.formatErrorMessage(person, spouseCandidate, sValidation)
                    importErrorReports.add("Excluded invalid spouse connection for ${person.firstName} ${person.lastName} & ${spouseCandidate.firstName} ${spouseCandidate.lastName}:\n$errorMsg")
                }
            }
        }

        // 2. If a relationship is invalid, do NOT save it: strip the invalid spouse link
        val sanitizedPersons = persons.map { p ->
            val sId = p.spouseId?.trim()
            if (!sId.isNullOrEmpty()) {
                val pairKey = if (p.id < sId) "${p.id}__$sId" else "${sId}__${p.id}"
                if (pairKey in invalidSpousePairs) {
                    p.copy(
                        spouseId = null,
                        maritalStatus = if (p.maritalStatus.equals("Married", ignoreCase = true)) "Single" else p.maritalStatus
                    )
                } else p
            } else p
        }

        val sanitizedTreeMap = sanitizedPersons.associateBy { it.id }
        val auditReport = centralFamilyService.auditFamilyTree(sanitizedTreeMap)
        if (!auditReport.isValid) {
            val firstIssue = auditReport.criticalErrors.firstOrNull()?.description ?: "Illegal relationships detected."
            onFailure(IllegalArgumentException("Bulk import rejected: ${auditReport.criticalErrorCount} critical violations found. First issue: $firstIssue"))
            return
        }

        val batch = db.batch()
        sanitizedPersons.forEach { p ->
            val ref = db.collection("persons").document(p.id)
            batch.set(ref, p.copy(treeId = treeId), SetOptions.merge())
        }
        batch.commit()
            .addOnSuccessListener {
                val updatedCache = (inMemoryPersonsCache.orEmpty() + sanitizedPersons).distinctBy { it.id }
                setCachedPersons(updatedCache)
                val bulkReport = BulkImportReport(
                    totalProcessed = persons.size,
                    savedPersonsCount = sanitizedPersons.size,
                    excludedSpouseCount = invalidSpousePairs.size,
                    importErrorReports = importErrorReports,
                    auditReport = auditReport
                )
                onSuccess(bulkReport)
            }
            .addOnFailureListener { onFailure(it) }
    }

    // =========================================================================
    // SYSTEM ADMIN PORTAL & MAINTENANCE ENGINE (FR-11)
    // =========================================================================

    fun publishSystemAnnouncement(
        announcement: SystemAnnouncement,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("system_announcements")
            .document("active_broadcast")
            .set(announcement)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun retractSystemAnnouncement(
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val retracted = SystemAnnouncement(
            id = "active_broadcast",
            isActive = false,
            title = "",
            message = ""
        )
        db.collection("system_announcements")
            .document("active_broadcast")
            .set(retracted)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun listenToActiveSystemAnnouncement(
        onUpdate: (SystemAnnouncement?) -> Unit
    ): ListenerRegistration {
        return db.collection("system_announcements")
            .document("active_broadcast")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    onUpdate(null)
                    return@addSnapshotListener
                }
                val parsed = snapshot.toObject(SystemAnnouncement::class.java)
                val activeFlag = snapshot.getBoolean("isActive") ?: snapshot.getBoolean("active") ?: parsed?.isActive ?: false
                val announcement = parsed?.copy(isActive = activeFlag)
                if (announcement != null && announcement.isActive) {
                    onUpdate(announcement)
                } else {
                    onUpdate(null)
                }
            }
    }

    fun logAdminAuditAction(
        log: AdminAuditLog,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val docId = if (log.id.isNotBlank()) log.id else "AUDIT_${System.currentTimeMillis()}"
        val record = log.copy(id = docId)
        db.collection("admin_audit_logs")
            .document(docId)
            .set(record)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun fetchAdminAuditLogs(
        limit: Int = 25,
        onSuccess: (List<AdminAuditLog>) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("admin_audit_logs")
            .get()
            .addOnSuccessListener { snap ->
                val logs = snap.documents.mapNotNull { doc ->
                    doc.toObject(AdminAuditLog::class.java)?.copy(id = doc.id)
                }.sortedByDescending { it.timestamp }
                 .take(limit)
                onSuccess(logs)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun measureFirestoreLatencyMs(
        onResult: (Long, Boolean) -> Unit
    ) {
        val startNs = System.nanoTime()
        db.collection("system_config").document("app_version")
            .get(Source.SERVER)
            .addOnSuccessListener {
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                onResult(elapsedMs, true)
            }
            .addOnFailureListener {
                val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                onResult(elapsedMs, false)
            }
    }

    fun saveReunionPass(
        pass: ReunionPassRecord,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val docId = if (pass.code.isNotBlank()) pass.code else "PASS_${System.currentTimeMillis()}"
        val record = pass.copy(code = docId)
        db.collection("reunion_passes")
            .document(docId)
            .set(record)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    data class PlatformTelemetry(
        val totalAccounts: Long = 0,
        val totalClans: Long = 0,
        val totalNodes: Long = 0,
        val isOnline: Boolean = true
    )

    fun fetchLivePlatformTelemetry(
        onResult: (PlatformTelemetry) -> Unit
    ) {
        val usersTask = db.collection("users").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
        val treesTask = db.collection("trees").count().get(com.google.firebase.firestore.AggregateSource.SERVER)
        val personsTask = db.collection("persons").count().get(com.google.firebase.firestore.AggregateSource.SERVER)

        Tasks.whenAllComplete(usersTask, treesTask, personsTask).addOnCompleteListener {
            val accountsCount = if (usersTask.isSuccessful) usersTask.result?.count ?: 0L else 0L
            val clansCount = if (treesTask.isSuccessful) treesTask.result?.count ?: 0L else 0L
            val nodesCount = if (personsTask.isSuccessful) personsTask.result?.count ?: 0L else 0L
            val isOnline = usersTask.isSuccessful || treesTask.isSuccessful || personsTask.isSuccessful

            onResult(
                PlatformTelemetry(
                    totalAccounts = accountsCount,
                    totalClans = clansCount,
                    totalNodes = nodesCount,
                    isOnline = isOnline
                )
            )
        }
    }

    fun fetchLatestAppVersion(
        onSuccess: (com.example.btproject2.models.AppVersionConfig?) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("system_config")
            .document("app_version")
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val config = doc.toObject(com.example.btproject2.models.AppVersionConfig::class.java)
                    onSuccess(config)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun publishAppVersion(
        config: com.example.btproject2.models.AppVersionConfig,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        db.collection("system_config")
            .document("app_version")
            .set(config)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    // ══════════════════════════════════════════════════════════════
    // SOFT-DELETE RECOVERY CONSOLE & OPS GOVERNANCE (Global)
    // ══════════════════════════════════════════════════════════════

    fun getArchivedDeletedMembers(
        onSuccess: (List<ArchivedMemberRecord>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("archived_deleted_members")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onSuccess(emptyList())
                } else {
                    val unRestored = snapshot.documents.mapNotNull { doc ->
                        val record = doc.toObject(ArchivedMemberRecord::class.java)?.copy(id = doc.id)
                        val docRestored = doc.getBoolean("isRestored") == true || doc.getBoolean("restored") == true || record?.isRestored == true
                        if (record != null && !docRestored) record else null
                    }.sortedByDescending { it.deletedAt }
                    onSuccess(unRestored)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun restoreArchivedMember(
        recordId: String,
        onSuccess: (Person) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (recordId.isBlank()) {
            onFailure(IllegalArgumentException("Record ID cannot be empty"))
            return
        }

        db.collection("archived_deleted_members").document(recordId).get()
            .addOnSuccessListener { doc ->
                val archive = doc.toObject(ArchivedMemberRecord::class.java)
                if (archive == null) {
                    onFailure(IllegalStateException("Archived record not found"))
                    return@addOnSuccessListener
                }

                val personToRestore = archive.person.copy(
                    id = archive.id,
                    treeId = archive.treeId,
                    fatherId = archive.originalFatherId,
                    motherId = archive.originalMotherId,
                    spouseId = archive.originalSpouseId,
                    maritalStatus = if (!archive.originalSpouseId.isNullOrBlank()) "Married" else archive.person.maritalStatus,
                    updatedAt = System.currentTimeMillis()
                )

                val batch = db.batch()
                // 1. Put back into active persons collection
                batch.set(db.collection("persons").document(personToRestore.id), personToRestore)

                // 2. Restore former children's fatherId / motherId links
                archive.formerFatherChildIds.filter { it.isNotBlank() }.forEach { childId ->
                    batch.set(
                        db.collection("persons").document(childId),
                        mapOf("fatherId" to personToRestore.id),
                        SetOptions.merge()
                    )
                }
                archive.formerMotherChildIds.filter { it.isNotBlank() }.forEach { childId ->
                    batch.set(
                        db.collection("persons").document(childId),
                        mapOf("motherId" to personToRestore.id),
                        SetOptions.merge()
                    )
                }

                // 3. Restore former spouse's reciprocal spouseId & maritalStatus
                if (!archive.originalSpouseId.isNullOrBlank()) {
                    batch.set(
                        db.collection("persons").document(archive.originalSpouseId),
                        mapOf(
                            "spouseId" to personToRestore.id,
                            "maritalStatus" to if (!personToRestore.isLiving) "Widowed" else "Married"
                        ),
                        SetOptions.merge()
                    )
                }

                // 4. Mark restored in archive (write both fields for complete compatibility)
                batch.update(
                    db.collection("archived_deleted_members").document(recordId),
                    mapOf<String, Any>("isRestored" to true, "restored" to true)
                )

                batch.commit()
                    .addOnSuccessListener {
                        // 5. Update memory cache (including children & spouse links) and notify synchronizer
                        val restored = applyRestoreArchivedMemberToCache(archive)

                        CentralTreeSynchronizer.getInstance().notifyMemberAdded(
                            newPerson = restored,
                            sourceScreen = "DeletedMemberRecoveryConsole"
                        )

                        // 6. Log restore activity
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        val activity = ActivityRecord(
                            id = java.util.UUID.randomUUID().toString(),
                            treeId = restored.treeId,
                            userId = currentUser?.uid.orEmpty(),
                            userName = currentUser?.displayName ?: currentUser?.email ?: "Admin",
                            type = "MEMBER_RESTORED",
                            description = "Restored ${restored.firstName} ${restored.lastName} to ${archive.treeName} via Ops & Recovery Console.",
                            targetId = restored.id,
                            targetName = "${restored.firstName} ${restored.lastName}".trim(),
                            timestamp = System.currentTimeMillis()
                        )
                        logActivity(activity, onSuccess = {}, onFailure = {})

                        onSuccess(restored)
                    }
                    .addOnFailureListener { onFailure(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // ══════════════════════════════════════════════════════════════
    // MERGE COLLISION ESCALATION DESK (Global)
    // ══════════════════════════════════════════════════════════════

    fun saveMergeCollision(
        collision: MergeCollisionRecord,
        onSuccess: (String) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val docRef = if (collision.id.isNotBlank()) db.collection("merge_collisions").document(collision.id)
                     else db.collection("merge_collisions").document()
        val record = collision.copy(id = docRef.id)

        docRef.set(record)
            .addOnSuccessListener {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val activity = ActivityRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    treeId = record.sourceTree1Id,
                    userId = currentUser?.uid.orEmpty(),
                    userName = currentUser?.displayName ?: currentUser?.email ?: "Tree Owner",
                    type = "MERGE_COLLISION_BLOCKED",
                    description = "Merge Collision Blocked: ${record.conflictSummary}. Escalated to System Admin Citadel.",
                    targetId = record.id,
                    targetName = "Escalation #${record.id.takeLast(4)}",
                    timestamp = System.currentTimeMillis()
                )
                logActivity(activity, onSuccess = {}, onFailure = {})
                onSuccess(record.id)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getOpenMergeCollisions(
        onSuccess: (List<MergeCollisionRecord>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("merge_collisions")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onSuccess(emptyList())
                } else {
                    val openList = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(MergeCollisionRecord::class.java)?.copy(id = doc.id)
                    }.filter { it.status == MergeCollisionRecord.STATUS_OPEN }
                     .sortedByDescending { it.createdAt }
                    onSuccess(openList)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun resolveMergeCollision(
        collisionId: String,
        resolutionNote: String,
        resolvedBy: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("merge_collisions").document(collisionId)
            .update(
                mapOf(
                    "status" to MergeCollisionRecord.STATUS_RESOLVED,
                    "resolvedAt" to System.currentTimeMillis(),
                    "resolvedBy" to resolvedBy,
                    "suggestedResolution" to resolutionNote
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener {
                val fallbackResolved = MergeCollisionRecord(
                    id = collisionId,
                    status = MergeCollisionRecord.STATUS_RESOLVED,
                    resolvedAt = System.currentTimeMillis(),
                    resolvedBy = resolvedBy,
                    suggestedResolution = resolutionNote
                )
                db.collection("merge_collisions").document(collisionId).set(fallbackResolved)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(it) }
            }
    }

    // ══════════════════════════════════════════════════════════════
    // ADMIN CITADEL REAL GOVERNANCE OPERATIONS (Global)
    // ══════════════════════════════════════════════════════════════

    /**
     * Queries Firestore for family trees that have 0 member records in `persons`
     * and deletes those unpopulated ghost trees from `trees`.
     */
    fun pruneDormantTrees(
        onSuccess: (Int, List<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("trees").get()
            .addOnSuccessListener { treeSnap ->
                db.collection("persons").get()
                    .addOnSuccessListener { personSnap ->
                        val populatedTreeIds = personSnap.documents.mapNotNull {
                            it.getString("treeId")?.trim()
                        }.filter { it.isNotEmpty() }.toSet()

                        val dormantDocs = treeSnap.documents.filter { doc ->
                            val tId = doc.id.trim()
                            tId.isNotEmpty() && !populatedTreeIds.contains(tId)
                        }

                        if (dormantDocs.isEmpty()) {
                            onSuccess(0, emptyList())
                            return@addOnSuccessListener
                        }

                        val batch = db.batch()
                        val prunedNames = mutableListOf<String>()
                        dormantDocs.forEach { doc ->
                            val name = doc.getString("name")?.takeIf { it.isNotBlank() } ?: doc.id
                            prunedNames.add(name)
                            batch.delete(doc.reference)
                        }

                        batch.commit()
                            .addOnSuccessListener {
                                onSuccess(dormantDocs.size, prunedNames)
                            }
                            .addOnFailureListener { onFailure(it) }
                    }
                    .addOnFailureListener { onFailure(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    data class TreeQuotaStatus(
        val tree: FamilyTree,
        val liveMemberCount: Int,
        val resolvedOwnerDisplay: String = "",
        val ownerEmail: String = resolvedOwnerDisplay.ifBlank { tree.ownerEmail }
    ) {
        val effectiveQuota: Int
            get() = tree.effectiveQuota

        val quota: Int
            get() = tree.effectiveQuota

        val nextQuota: Int
            get() = tree.nextExpandedQuota()

        val isCapped: Boolean
            get() = tree.isCapped(liveMemberCount)

        val isNearCap: Boolean
            get() = tree.isNearCap(liveMemberCount)

        val isExpandedTier: Boolean
            get() = effectiveQuota > FamilyTree.DEFAULT_FREEMIUM_QUOTA

        val badgeLabel: String
            get() = when {
                isCapped -> "🛑 $liveMemberCount/$quota Capped"
                tree.quotaExpansionRequested -> "🔔 $liveMemberCount/$quota Requested"
                isNearCap -> "⚠️ $liveMemberCount/$quota Near Cap"
                else -> "✅ $liveMemberCount/$quota Active"
            }

        val metaSummary: String
            get() {
                val ownerLabel = resolvedOwnerDisplay.ifBlank {
                    ownerEmail.ifBlank { tree.ownerName.ifBlank { "Clan Owner" } }
                }
                val tierNum = if (nextQuota <= 200) 2 else 3
                val actionLabel = when {
                    isCapped || tree.quotaExpansionRequested -> "Request: Tier $tierNum ($nextQuota Members)"
                    isNearCap -> "Approaching cap limit (Tier $tierNum → $nextQuota)"
                    else -> "Active Tier Capacity ($quota Members)"
                }
                return "Owner: $ownerLabel • $actionLabel"
            }
    }

    /**
     * Submits a live Clan Expansion request for [treeId] so it surfaces immediately
     * in the System Admin Citadel Quota & Passes queue for any user/tree.
     */
    fun requestTreeQuotaExpansion(
        treeId: String,
        requesterEmail: String = "",
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (treeId.isBlank()) {
            onFailure(IllegalArgumentException("Tree ID cannot be blank"))
            return
        }
        val currentUser = FirebaseAuth.getInstance().currentUser
        val emailToSave = requesterEmail.ifBlank { currentUser?.email.orEmpty() }
        val updates = mutableMapOf<String, Any>(
            "quotaExpansionRequested" to true,
            "quotaRequestedAt" to System.currentTimeMillis()
        )
        if (emailToSave.isNotBlank()) {
            updates["ownerEmail"] = emailToSave
        }
        db.collection("trees").document(treeId)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                val activity = ActivityRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    treeId = treeId,
                    userId = currentUser?.uid.orEmpty(),
                    userName = currentUser?.displayName ?: emailToSave.ifBlank { "Clan Owner" },
                    type = "QUOTA_EXPANSION_REQUESTED",
                    description = "Requested Clan Capacity Expansion (+${FamilyTree.EXPANSION_STEP} member cap) for Admin Citadel review.",
                    targetId = treeId,
                    targetName = "Tier Expansion (+${FamilyTree.EXPANSION_STEP})",
                    timestamp = System.currentTimeMillis()
                )
                logActivity(activity, onSuccess = {}, onFailure = {})
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Dynamically queries all registered clan trees, live member counts in `persons`,
     * and owner profiles in `users` to populate the Admin Citadel Freemium Quota Queue
     * with zero hardcoded accounts or tree IDs.
     */
    fun fetchQuotaQueueTrees(
        includeAllForInspection: Boolean = false,
        onSuccess: (List<TreeQuotaStatus>) -> Unit,
        onFailure: (Exception) -> Unit = {}
    ) {
        val treesTask = db.collection("trees").get()
        val personsTask = db.collection("persons").get()
        val usersTask = db.collection("users").get()

        Tasks.whenAllComplete(treesTask, personsTask, usersTask).addOnCompleteListener {
            if (!treesTask.isSuccessful) {
                val err = treesTask.exception ?: IllegalStateException("Failed to query trees")
                onFailure(err)
                return@addOnCompleteListener
            }

            val treeDocs = treesTask.result?.documents.orEmpty()
            val personDocs = if (personsTask.isSuccessful) personsTask.result?.documents.orEmpty() else emptyList()
            val userDocs = if (usersTask.isSuccessful) usersTask.result?.documents.orEmpty() else emptyList()

            // Count live members per treeId
            val memberCountByTree = mutableMapOf<String, Int>()
            personDocs.forEach { pDoc ->
                val tId = pDoc.getString("treeId")?.trim().orEmpty()
                if (tId.isNotEmpty()) {
                    memberCountByTree[tId] = (memberCountByTree[tId] ?: 0) + 1
                }
            }

            // Map userId -> email / displayName
            val userEmailByUid = mutableMapOf<String, String>()
            userDocs.forEach { uDoc ->
                val email = uDoc.getString("email")?.trim().orEmpty()
                val name = uDoc.getString("displayName")?.trim()
                    ?: uDoc.getString("fullName")?.trim()
                    ?: uDoc.getString("name")?.trim()
                    .orEmpty()
                val label = when {
                    email.isNotEmpty() && name.isNotEmpty() -> "$email ($name)"
                    email.isNotEmpty() -> email
                    name.isNotEmpty() -> name
                    else -> ""
                }
                if (label.isNotEmpty()) {
                    userEmailByUid[uDoc.id] = label
                }
            }

            val allStatuses = treeDocs.mapNotNull { doc ->
                val tree = documentToFamilyTree(doc) ?: return@mapNotNull null
                val liveCount = memberCountByTree[tree.id] ?: tree.memberCount.coerceAtLeast(0)
                val ownerDisplay = when {
                    tree.ownerEmail.isNotBlank() && tree.ownerName.isNotBlank() && !tree.ownerName.equals("Owner", true) ->
                        "${tree.ownerEmail} (${tree.ownerName})"
                    tree.ownerEmail.isNotBlank() -> tree.ownerEmail
                    userEmailByUid[tree.ownerId] != null -> userEmailByUid[tree.ownerId]!!
                    tree.ownerName.isNotBlank() -> tree.ownerName
                    else -> "Clan Owner"
                }
                TreeQuotaStatus(
                    tree = tree,
                    liveMemberCount = liveCount,
                    resolvedOwnerDisplay = ownerDisplay
                )
            }

            // Include any tree that:
            // 1. Explicitly requested an expansion, OR
            // 2. Reached or exceeded its quota (Capped), OR
            // 3. Is approaching its quota (>= 85%), OR
            // 4. Has >= 50 members (including expanded trees so Admin can monitor active high-capacity clans)
            val filtered = allStatuses.filter { status ->
                status.tree.quotaExpansionRequested ||
                    status.isCapped ||
                    status.isNearCap ||
                    status.liveMemberCount >= FamilyTree.DEFAULT_FREEMIUM_QUOTA
            }.sortedWith(
                compareByDescending<TreeQuotaStatus> { it.isCapped }
                    .thenByDescending { it.tree.quotaExpansionRequested }
                    .thenByDescending { it.liveMemberCount }
            )

            // Fallback: if no tree has reached 43+ members yet, surface the top 3 largest real trees in Firestore
            // so the Admin can proactively inspect and expand any real user's clan tree.
            val finalQueue = if (filtered.isNotEmpty()) {
                filtered
            } else {
                allStatuses.sortedByDescending { it.liveMemberCount }.take(3)
            }

            onSuccess(finalQueue)
        }
    }

    /**
     * Persists an expanded member quota cap for a specific clan tree in Firestore
     * and clears any pending expansion request flag.
     */
    fun expandTreeMemberQuota(
        treeId: String,
        treeName: String,
        newQuota: Int,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val data = mapOf(
            "memberQuota" to newQuota,
            "quotaExpansionRequested" to false,
            "quotaApprovedAt" to System.currentTimeMillis(),
            "name" to treeName
        )
        db.collection("trees").document(treeId)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                db.collection("trees").document(treeId).get().addOnSuccessListener { tDoc ->
                    val ownerId = tDoc.getString("ownerId").orEmpty()
                    if (ownerId.isNotBlank()) {
                        addNotification(
                            treeId = treeId,
                            userId = ownerId,
                            title = "Clan Capacity Expanded ($newQuota Cap)",
                            message = "System Admin approved your clan expansion for '$treeName'. You can now record up to $newQuota family members.",
                            type = "SYSTEM",
                            targetId = treeId
                        )
                    }
                }
                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Explicitly seeds an isolated `[DEMO] Heritage Sandbox Clan` tree into Firestore
     * only when manually requested by the System Admin in the Citadel Ops Desk.
     */
    fun seedDemoSandboxClan(
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val demoTreeId = "tree_demo_sandbox"
        val now = System.currentTimeMillis()
        val demoTree = FamilyTree(
            id = demoTreeId,
            name = "[DEMO] Heritage Sandbox Clan",
            bridgeDescription = "Isolated 4-generation demonstration clan seeded via Citadel Ops Desk.",
            ownerId = FirebaseAuth.getInstance().currentUser?.uid ?: "system_admin",
            ownerName = "System Admin",
            inviteCode = "DEMO26",
            memberCount = 8,
            createdAt = now,
            isPrivate = false
        )

        val demoMembers = listOf(
            Person(id = "demo_gen1_pat", treeId = demoTreeId, firstName = "Mateo", lastName = "Delgado", gender = "Male", birthDate = "1935-03-12", birthPlace = "Vigan, Ilocos Sur", isLiving = false, deathDate = "2012-08-19", spouseId = "demo_gen1_mat", maritalStatus = "Married", lineageRole = "PATRIARCH", updatedAt = now),
            Person(id = "demo_gen1_mat", treeId = demoTreeId, firstName = "Consuelo", lastName = "Delgado", gender = "Female", birthDate = "1938-07-24", birthPlace = "Vigan, Ilocos Sur", isLiving = false, deathDate = "2018-01-05", spouseId = "demo_gen1_pat", maritalStatus = "Married", lineageRole = "MATRIARCH", updatedAt = now),
            Person(id = "demo_gen2_son1", treeId = demoTreeId, firstName = "Ricardo", lastName = "Delgado", gender = "Male", birthDate = "1962-05-14", birthPlace = "Manila", fatherId = "demo_gen1_pat", motherId = "demo_gen1_mat", spouseId = "demo_gen2_sp1", maritalStatus = "Married", updatedAt = now),
            Person(id = "demo_gen2_sp1", treeId = demoTreeId, firstName = "Elena", lastName = "Delgado", gender = "Female", birthDate = "1964-11-02", birthPlace = "Quezon City", spouseId = "demo_gen2_son1", maritalStatus = "Married", updatedAt = now),
            Person(id = "demo_gen2_dau1", treeId = demoTreeId, firstName = "Teresa", lastName = "Delgado", gender = "Female", birthDate = "1966-09-20", birthPlace = "Manila", fatherId = "demo_gen1_pat", motherId = "demo_gen1_mat", updatedAt = now),
            Person(id = "demo_gen3_son1", treeId = demoTreeId, firstName = "Marco", lastName = "Delgado", gender = "Male", birthDate = "1990-02-18", birthPlace = "Makati", fatherId = "demo_gen2_son1", motherId = "demo_gen2_sp1", spouseId = "demo_gen3_sp1", maritalStatus = "Married", updatedAt = now),
            Person(id = "demo_gen3_sp1", treeId = demoTreeId, firstName = "Sofia", lastName = "Delgado", gender = "Female", birthDate = "1992-06-30", birthPlace = "Cebu City", spouseId = "demo_gen3_son1", maritalStatus = "Married", updatedAt = now),
            Person(id = "demo_gen4_ch1", treeId = demoTreeId, firstName = "Lucas", lastName = "Delgado", gender = "Male", birthDate = "2018-10-11", birthPlace = "Taguig", fatherId = "demo_gen3_son1", motherId = "demo_gen3_sp1", updatedAt = now)
        )

        val batch = db.batch()
        batch.set(db.collection("trees").document(demoTreeId), demoTree)
        demoMembers.forEach { member ->
            batch.set(db.collection("persons").document(member.id), member)
        }
        batch.commit()
            .addOnSuccessListener { onSuccess(demoMembers.size) }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Flushes the isolated `[DEMO] Heritage Sandbox Clan` and any legacy seeded demo documents
     * (`member_elias_malone`, `member_juan_delacruz`, `escalation_402`) from Firestore.
     */
    fun flushDemoSandboxClan(
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val demoTreeId = "tree_demo_sandbox"
        db.collection("persons").whereEqualTo("treeId", demoTreeId).get()
            .addOnSuccessListener { personSnap ->
                val batch = db.batch()
                var deletedCount = 0
                personSnap.documents.forEach { doc ->
                    batch.delete(doc.reference)
                    deletedCount++
                }
                batch.delete(db.collection("trees").document(demoTreeId))
                batch.delete(db.collection("archived_deleted_members").document("member_elias_malone"))
                batch.delete(db.collection("archived_deleted_members").document("member_juan_delacruz"))
                batch.delete(db.collection("merge_collisions").document("escalation_402"))

                batch.commit()
                    .addOnSuccessListener {
                        inMemoryPersonsCache = inMemoryPersonsCache?.filterNot { it.treeId == demoTreeId }
                        onSuccess(deletedCount)
                    }
                    .addOnFailureListener { onFailure(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    // ══════════════════════════════════════════════════════════════
    // GUIDED FAMILY HELPER: STEP-BY-STEP & ONE-TAP AUTO-REPAIR (Global)
    // ══════════════════════════════════════════════════════════════

    /**
     * Repairs a single tree relationship conflict in Firestore, updates the in-memory cache,
     * and broadcasts a real-time sync event via CentralTreeSynchronizer.
     */
    fun autoRepairConflict(
        conflict: com.example.btproject2.engine.ConflictScanner.Conflict,
        allPersons: List<Person>,
        onSuccess: (Person?) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val person = allPersons.find { it.id == conflict.personId }
        if (person == null) {
            onFailure(IllegalArgumentException("Member not found in tree"))
            return
        }

        if (conflict.type == "non_reciprocal" && conflict.relatedPersonId.isNotBlank()) {
            updateSpouse(
                personId = person.id,
                spouseId = conflict.relatedPersonId,
                maritalStatus = "Married",
                onSuccess = {
                    val updated = getCachedPersons(person.treeId.ifBlank { null })?.find { it.id == person.id } ?: person
                    onSuccess(updated)
                },
                onFailure = onFailure
            )
            return
        }

        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any?>("updatedAt" to now)
        var updatedPerson = person.copy(updatedAt = now)

        when {
            conflict.field == "fatherId" -> {
                updates["fatherId"] = null
                updatedPerson = updatedPerson.copy(fatherId = null)
            }
            conflict.field == "motherId" -> {
                updates["motherId"] = null
                updatedPerson = updatedPerson.copy(motherId = null)
            }
            conflict.field == "spouseId" -> {
                val oldSpouseId = person.spouseId ?: conflict.relatedPersonId
                val oldSpouse = allPersons.find { it.id == oldSpouseId }
                updates["spouseId"] = null
                updates["maritalStatus"] = "Single"
                updatedPerson = updatedPerson.copy(spouseId = null, maritalStatus = "Single")

                if (oldSpouse != null && oldSpouse.spouseId == person.id) {
                    val updatedSpouse = oldSpouse.copy(spouseId = null, maritalStatus = "Single", updatedAt = now)
                    updatePersonInCache(updatedSpouse)
                    db.collection("persons").document(oldSpouse.id)
                        .set(mapOf("spouseId" to null, "maritalStatus" to "Single", "updatedAt" to now), SetOptions.merge())
                        .addOnSuccessListener {
                            updatePersonInCache(updatedSpouse)
                            CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                                updatedPerson = updatedSpouse,
                                previousPerson = oldSpouse,
                                sourceScreen = "GuidedFamilyHelper"
                            )
                        }
                }
            }
            conflict.field == "maritalStatus" || conflict.type == "status_mismatch" -> {
                if (person.spouseId.isNullOrBlank()) {
                    updates["maritalStatus"] = "Single"
                    updatedPerson = updatedPerson.copy(maritalStatus = "Single")
                } else {
                    updates["maritalStatus"] = "Married"
                    updatedPerson = updatedPerson.copy(maritalStatus = "Married")
                }
            }
            else -> {
                onSuccess(person)
                return
            }
        }

        updatePersonInCache(updatedPerson)
        db.collection("persons").document(person.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                updatePersonInCache(updatedPerson)
                CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                    updatedPerson = updatedPerson,
                    previousPerson = person,
                    sourceScreen = "GuidedFamilyHelper"
                )
                onSuccess(updatedPerson)
            }
            .addOnFailureListener { err ->
                updatePersonInCache(person)
                onFailure(err)
            }
    }

    /**
     * Atomically repairs all auto-fixable relationship conflicts in the tree in a single
     * Firestore batch and notifies CentralTreeSynchronizer for real-time UI updates.
     */
    fun autoRepairAllConflicts(
        conflicts: List<com.example.btproject2.engine.ConflictScanner.Conflict>,
        allPersons: List<Person>,
        onSuccess: (Int) -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val personById = allPersons.associateBy { it.id }.toMutableMap()
        val batch = db.batch()
        val now = System.currentTimeMillis()
        val modifiedPersons = mutableMapOf<String, Pair<Person, Person>>() // id -> (old, new)
        var repairedCount = 0

        for (conflict in conflicts) {
            val current = modifiedPersons[conflict.personId]?.second ?: personById[conflict.personId] ?: continue
            val ref = db.collection("persons").document(current.id)

            when {
                conflict.type == "non_reciprocal" && conflict.relatedPersonId.isNotBlank() -> {
                    val spouse = modifiedPersons[conflict.relatedPersonId]?.second ?: personById[conflict.relatedPersonId]
                    if (spouse != null) {
                        val spRef = db.collection("persons").document(spouse.id)
                        batch.set(spRef, mapOf("spouseId" to current.id, "maritalStatus" to "Married", "updatedAt" to now), SetOptions.merge())
                        val updatedSpouse = spouse.copy(spouseId = current.id, maritalStatus = "Married", updatedAt = now)
                        modifiedPersons[spouse.id] = (personById[spouse.id] ?: spouse) to updatedSpouse
                        repairedCount++
                    }
                }
                conflict.field == "fatherId" -> {
                    batch.set(ref, mapOf("fatherId" to null, "updatedAt" to now), SetOptions.merge())
                    val updated = current.copy(fatherId = null, updatedAt = now)
                    modifiedPersons[current.id] = (personById[current.id] ?: current) to updated
                    repairedCount++
                }
                conflict.field == "motherId" -> {
                    batch.set(ref, mapOf("motherId" to null, "updatedAt" to now), SetOptions.merge())
                    val updated = current.copy(motherId = null, updatedAt = now)
                    modifiedPersons[current.id] = (personById[current.id] ?: current) to updated
                    repairedCount++
                }
                conflict.field == "spouseId" -> {
                    val oldSpouseId = current.spouseId ?: conflict.relatedPersonId
                    batch.set(ref, mapOf("spouseId" to null, "maritalStatus" to "Single", "updatedAt" to now), SetOptions.merge())
                    val updated = current.copy(spouseId = null, maritalStatus = "Single", updatedAt = now)
                    modifiedPersons[current.id] = (personById[current.id] ?: current) to updated
                    if (oldSpouseId.isNotBlank()) {
                        val other = modifiedPersons[oldSpouseId]?.second ?: personById[oldSpouseId]
                        if (other != null && other.spouseId == current.id) {
                            val otherRef = db.collection("persons").document(other.id)
                            batch.set(otherRef, mapOf("spouseId" to null, "maritalStatus" to "Single", "updatedAt" to now), SetOptions.merge())
                            val updatedOther = other.copy(spouseId = null, maritalStatus = "Single", updatedAt = now)
                            modifiedPersons[other.id] = (personById[other.id] ?: other) to updatedOther
                        }
                    }
                    repairedCount++
                }
                conflict.field == "maritalStatus" || conflict.type == "status_mismatch" -> {
                    val newStatus = if (current.spouseId.isNullOrBlank()) "Single" else "Married"
                    batch.set(ref, mapOf("maritalStatus" to newStatus, "updatedAt" to now), SetOptions.merge())
                    val updated = current.copy(maritalStatus = newStatus, updatedAt = now)
                    modifiedPersons[current.id] = (personById[current.id] ?: current) to updated
                    repairedCount++
                }
            }
        }

        if (repairedCount == 0) {
            onSuccess(0)
            return
        }

        modifiedPersons.values.forEach { (_, updated) -> updatePersonInCache(updated) }

        batch.commit()
            .addOnSuccessListener {
                modifiedPersons.values.forEach { (oldPerson, updatedPerson) ->
                    updatePersonInCache(updatedPerson)
                    CentralTreeSynchronizer.getInstance().notifyProfileEdited(
                        updatedPerson = updatedPerson,
                        previousPerson = oldPerson,
                        sourceScreen = "GuidedFamilyHelperBatch"
                    )
                }
                onSuccess(repairedCount)
            }
            .addOnFailureListener { err ->
                modifiedPersons.values.forEach { (oldPerson, _) -> updatePersonInCache(oldPerson) }
                onFailure(err)
            }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DUAL-MODE ACCOUNT VAULT CLOUD SYNCHRONIZATION (3D Flip-Safe & Binder Tabs)
    // ═══════════════════════════════════════════════════════════════════════════

    fun syncVaultFolderToCloud(folder: com.example.btproject2.models.VaultFolder) {
        try {
            val payload = mapOf(
                "id" to folder.id,
                "name" to folder.name,
                "iconEmoji" to folder.iconEmoji,
                "vaultMode" to folder.vaultMode.storageKey,
                "createdAt" to folder.createdAt
            )
            db.collection("vault_folders").document(folder.id).set(payload, SetOptions.merge())
        } catch (_: Exception) {
        }
    }

    fun deleteVaultFolderFromCloud(folderId: String) {
        try {
            db.collection("vault_folders").document(folderId).delete()
        } catch (_: Exception) {
        }
    }

    fun syncVaultAccountToCloud(account: com.example.btproject2.models.DemoAccount) {
        try {
            val encryptedPass = com.example.btproject2.utils.KinTraceCryptoEngine.encryptField(
                account.password,
                "kintrace_dual_mode_vault_2026"
            )
            val payload = mapOf(
                "id" to account.id,
                "name" to account.name,
                "roleTitle" to account.roleTitle,
                "roleBadge" to account.roleBadge,
                "email" to account.email,
                "password" to encryptedPass,
                "treeSummary" to account.treeSummary,
                "emojiIcon" to account.emojiIcon,
                "vaultMode" to account.vaultMode.storageKey,
                "folderId" to account.folderId,
                "folderName" to account.folderName
            )
            db.collection("vault_accounts").document(account.id).set(payload, SetOptions.merge())
        } catch (_: Exception) {
        }
    }

    fun deleteVaultAccountFromCloud(accountId: String) {
        try {
            db.collection("vault_accounts").document(accountId).delete()
        } catch (_: Exception) {
        }
    }
}


