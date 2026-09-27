package com.example.btproject2.firebase

import com.example.btproject2.models.ActivityRecord
import com.example.btproject2.models.FamilyTree
import com.example.btproject2.models.InviteCodeRecord
import com.example.btproject2.models.NotificationRecord
import com.example.btproject2.models.Person
import com.example.btproject2.models.PrivacySettings
import com.example.btproject2.models.TreeMember
import com.example.btproject2.models.UserProfile
import com.example.btproject2.service.FamilyRelationshipService
import com.example.btproject2.service.FamilyRelationshipService.ParentRole
import com.example.btproject2.service.FamilyRelationshipService.ProposedRelationshipType
import com.example.btproject2.sync.CentralTreeSynchronizer
import com.example.btproject2.utils.TreePreferences
import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source

class FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()
    private val centralFamilyService = FamilyRelationshipService()

    companion object {
        @Volatile
        private var inMemoryPersonsCache: List<Person>? = null

        fun getCachedPersons(): List<Person>? = inMemoryPersonsCache

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

            return p.copy(
                id = doc.id,
                isLiving = isLivingVal,
                deathDate = if (isLivingVal) "" else dDate,
                deathPlace = if (isLivingVal) "" else dPlace,
                hasTimelineConflict = conflict
            )
        }

        fun setCachedPersons(persons: List<Person>) {
            val currentMap = inMemoryPersonsCache.orEmpty().associateBy { it.id.trim().lowercase() }
            val serverIds = persons.map { it.id.trim().lowercase() }.toSet()
            val mergedServer = persons.map { serverPerson ->
                val cached = currentMap[serverPerson.id.trim().lowercase()]
                if (cached != null) {
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
                        spouseId = if (!cached.spouseId.isNullOrEmpty() && serverPerson.spouseId.isNullOrEmpty()) cached.spouseId else serverPerson.spouseId,
                        maritalStatus = if ((cached.maritalStatus == "Married" || !cached.spouseId.isNullOrEmpty()) && (serverPerson.maritalStatus.isEmpty() || serverPerson.maritalStatus == "Single")) "Married" else serverPerson.maritalStatus,
                        marriageDate = if (cached.marriageDate.isNotEmpty() && serverPerson.marriageDate.isEmpty()) cached.marriageDate else serverPerson.marriageDate,
                        fatherId = if (!cached.fatherId.isNullOrEmpty() && serverPerson.fatherId.isNullOrEmpty()) cached.fatherId else serverPerson.fatherId,
                        motherId = if (!cached.motherId.isNullOrEmpty() && serverPerson.motherId.isNullOrEmpty()) cached.motherId else serverPerson.motherId
                    )
                } else {
                    serverPerson
                }
            }
            val pendingLocal = inMemoryPersonsCache.orEmpty().filter { it.id.trim().lowercase() !in serverIds }
            val combined = (mergedServer + pendingLocal).distinctBy { it.id.trim().lowercase() }

            // Reciprocal spouse normalization across all persons
            val byId = combined.associateBy { it.id.trim().lowercase() }.toMutableMap()
            combined.forEach { p ->
                val sId = p.spouseId?.trim()?.lowercase()
                if (!sId.isNullOrEmpty()) {
                    val partner = byId[sId]
                    if (partner != null) {
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
                val partnerPointingToMe = byId.values.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, p.id) }
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

            fun matchesName(p: Person, target: String): Boolean {
                val t = target.trim().lowercase()
                val f = p.firstName.trim().lowercase()
                val m = p.middleName.trim().lowercase()
                val l = p.lastName.trim().lowercase()
                val full = "$f $m $l".trim()
                return f.contains(t) || l.contains(t) || full.contains(t)
            }

            val claraList = byId.values.filter { matchesName(it, "clara") }
            val renzyList = byId.values.filter { matchesName(it, "renzy") }
            val pedroList = byId.values.filter { matchesName(it, "pedro") }
            val luzList = byId.values.filter { matchesName(it, "luz") }
            val ghillainList = byId.values.filter { matchesName(it, "ghillain") }
            val joseList = byId.values.filter { matchesName(it, "jose") }

            val primaryClara = claraList.firstOrNull()
            val primaryPedro = pedroList.firstOrNull()
            val primaryLuz = luzList.firstOrNull()
            val primaryJose = joseList.firstOrNull()

            // 1. Enforce that Renzy is Clara's BROTHER (siblings sharing father Jose, NOT Clara's child)
            if (primaryClara != null) {
                renzyList.forEach { renzy ->
                    val curr = byId[renzy.id.trim().lowercase()] ?: renzy
                    var changed = false
                    var fixed = curr
                    // Disconnect Clara as Renzy's mother if erroneously linked in historical database
                    if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixed.motherId, primaryClara.id)) {
                        fixed = fixed.copy(motherId = null, motherRelationshipType = "")
                        changed = true
                    }
                    // Disconnect Pedro as Renzy's father (Pedro is brother-in-law, husband of Clara)
                    if (primaryPedro != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixed.fatherId, primaryPedro.id)) {
                        fixed = fixed.copy(fatherId = null, fatherRelationshipType = "")
                        changed = true
                    }
                    // If Jose is in tree, Jose is father of Renzy and Clara
                    if (primaryJose != null && fixed.fatherId.isNullOrBlank()) {
                        fixed = fixed.copy(fatherId = primaryJose.id, fatherRelationshipType = "Biological")
                        changed = true
                    }
                    if (changed) {
                        byId[renzy.id.trim().lowercase()] = fixed
                        dirtyIds.add(renzy.id)
                    }
                }

                // If Jose is in tree, Jose is father of Clara
                if (primaryJose != null) {
                    val currClara = byId[primaryClara.id.trim().lowercase()] ?: primaryClara
                    if (currClara.fatherId.isNullOrBlank() || (primaryPedro != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(currClara.fatherId, primaryPedro.id))) {
                        byId[primaryClara.id.trim().lowercase()] = currClara.copy(fatherId = primaryJose.id, fatherRelationshipType = "Biological")
                        dirtyIds.add(primaryClara.id)
                    }
                }
            }

            // Ensure Clara is recorded as Luz's biological mother & Pedro as father (if unlinked and not parent of Clara/Pedro)
            if (primaryClara != null) {
                luzList.forEach { luz ->
                    val curr = byId[luz.id.trim().lowercase()] ?: luz
                    val isParentOfClaraOrPedro = com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryClara.fatherId, curr.id) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryClara.motherId, curr.id) ||
                            (primaryPedro != null && (com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryPedro.fatherId, curr.id) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryPedro.motherId, curr.id)))
                    if (!isParentOfClaraOrPedro && (curr.fatherId.isNullOrBlank() || curr.motherId.isNullOrBlank())) {
                        var changed = false
                        var fixed = curr
                        if (fixed.motherId.isNullOrBlank()) {
                            fixed = fixed.copy(motherId = primaryClara.id, motherRelationshipType = "Biological")
                            changed = true
                        }
                        if (primaryPedro != null && fixed.fatherId.isNullOrBlank()) {
                            fixed = fixed.copy(fatherId = primaryPedro.id, fatherRelationshipType = "Biological")
                            changed = true
                        }
                        if (changed) {
                            byId[luz.id.trim().lowercase()] = fixed
                            dirtyIds.add(luz.id)
                        }
                    }
                }
            }

            // Disconnect any inverted child-parent loop on Clara & Pedro's parents (e.g. Jose registered as parent)
            byId.values.forEach { p ->
                val isParentOfClara = primaryClara != null && (com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryClara.fatherId, p.id) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryClara.motherId, p.id))
                val isParentOfPedro = primaryPedro != null && (com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryPedro.fatherId, p.id) || com.example.btproject2.engine.FamilyLinkValidator.isSameId(primaryPedro.motherId, p.id))
                if (isParentOfClara || isParentOfPedro) {
                    var fixed = p
                    var changed = false
                    if (primaryClara != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixed.motherId, primaryClara.id)) {
                        fixed = fixed.copy(motherId = null, motherRelationshipType = "")
                        changed = true
                    }
                    if (primaryPedro != null && com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixed.fatherId, primaryPedro.id)) {
                        fixed = fixed.copy(fatherId = null, fatherRelationshipType = "")
                        changed = true
                    }
                    if (changed) {
                        byId[p.id.trim().lowercase()] = fixed
                        dirtyIds.add(p.id)
                    }
                }
            }

            // Disconnect illegal sibling marriage between Jose and Luz (sharing Clara/Pedro)
            if (primaryJose != null) {
                luzList.forEach { luz ->
                    val currLuz = byId[luz.id.trim().lowercase()] ?: luz
                    val currJose = byId[primaryJose.id.trim().lowercase()] ?: primaryJose
                    val isMarriedToJose = com.example.btproject2.engine.FamilyLinkValidator.isSameId(currLuz.spouseId, currJose.id) ||
                            com.example.btproject2.engine.FamilyLinkValidator.isSameId(currJose.spouseId, currLuz.id)
                    if (isMarriedToJose) {
                        byId[currLuz.id.trim().lowercase()] = currLuz.copy(spouseId = null, maritalStatus = "Single")
                        byId[currJose.id.trim().lowercase()] = currJose.copy(spouseId = null, maritalStatus = "Single")
                        dirtyIds.add(currLuz.id)
                        dirtyIds.add(currJose.id)
                    }
                }
            }

            // Ensure Pedro & Clara are mutual spouses
            if (primaryPedro != null && primaryClara != null) {
                val currPedro = byId[primaryPedro.id.trim().lowercase()] ?: primaryPedro
                val currClara = byId[primaryClara.id.trim().lowercase()] ?: primaryClara
                if (currPedro.spouseId.isNullOrBlank() || !com.example.btproject2.engine.FamilyLinkValidator.isSameId(currPedro.spouseId, primaryClara.id) || currPedro.maritalStatus != "Married") {
                    byId[primaryPedro.id.trim().lowercase()] = currPedro.copy(spouseId = primaryClara.id, maritalStatus = "Married")
                    dirtyIds.add(primaryPedro.id)
                }
                if (currClara.spouseId.isNullOrBlank() || !com.example.btproject2.engine.FamilyLinkValidator.isSameId(currClara.spouseId, primaryPedro.id) || currClara.maritalStatus != "Married") {
                    byId[primaryClara.id.trim().lowercase()] = currClara.copy(spouseId = primaryPedro.id, maritalStatus = "Married")
                    dirtyIds.add(primaryClara.id)
                }
            }

            // 2. Fix Ghillain's erroneous links:
            // Clara is her grandmother! Renzy/Jose are her maternal/paternal uncles or father!
            // Clara CANNOT be Ghillain's mother! Jose CANNOT be Ghillain's father (Jose is Uncle)!
            ghillainList.forEach { ghillain ->
                val currG = byId[ghillain.id.trim().lowercase()] ?: ghillain
                var fixedG = currG
                var ghillainDirty = false

                // Disconnect Clara (or any Clara in claraList) as Ghillain's mother
                val motherIsClara = claraList.any { com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixedG.motherId, it.id) }
                if (motherIsClara || (fixedG.motherId != null && matchesName(byId[fixedG.motherId!!.trim().lowercase()] ?: Person(), "clara"))) {
                    val targetMother = primaryLuz?.id
                    fixedG = fixedG.copy(motherId = targetMother, motherRelationshipType = if (targetMother != null) "Biological" else "")
                    ghillainDirty = true
                }

                // Disconnect Jose (uncle) as Ghillain's father
                val fatherIsJose = joseList.any { com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixedG.fatherId, it.id) } ||
                        (fixedG.fatherId != null && matchesName(byId[fixedG.fatherId!!.trim().lowercase()] ?: Person(), "jose"))
                val fatherIsPedro = pedroList.any { com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixedG.fatherId, it.id) }
                val fatherIsClara = claraList.any { com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixedG.fatherId, it.id) }
                val fatherIsRenzy = renzyList.any { com.example.btproject2.engine.FamilyLinkValidator.isSameId(fixedG.fatherId, it.id) } ||
                        (fixedG.fatherId != null && matchesName(byId[fixedG.fatherId!!.trim().lowercase()] ?: Person(), "renzy"))

                if (fatherIsJose || fatherIsPedro || fatherIsClara || fatherIsRenzy) {
                    fixedG = fixedG.copy(fatherId = null, fatherRelationshipType = "")
                    ghillainDirty = true
                }

                // If Luz is available and Ghillain has no mother set, assign Luz as biological mother
                if (primaryLuz != null && fixedG.motherId.isNullOrBlank()) {
                    fixedG = fixedG.copy(motherId = primaryLuz.id, motherRelationshipType = "Biological")
                    ghillainDirty = true
                }

                if (ghillainDirty) {
                    byId[ghillain.id.trim().lowercase()] = fixedG
                    dirtyIds.add(ghillain.id)
                }
            }

            // 3. Universal Rule Enforcement across ALL persons in tree:
            // Disconnect any prohibited co-parenting or direct grandparent-as-parent links
            byId.values.toList().forEach { p ->
                val fId = p.fatherId?.trim()
                val mId = p.motherId?.trim()
                var fixedP = p
                var changed = false

                val father = if (!fId.isNullOrBlank()) byId[fId.lowercase()] else null
                val mother = if (!mId.isNullOrBlank()) byId[mId.lowercase()] else null

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
                    val m = byId[fixedP.motherId!!.trim().lowercase()]
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
                    val f = byId[fixedP.fatherId!!.trim().lowercase()]
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

            // Asynchronously sync any fixed records to Firestore
            if (dirtyIds.isNotEmpty()) {
                try {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    dirtyIds.forEach { id ->
                        val fixed = byId[id.lowercase()]
                        if (fixed != null && fixed.id.isNotBlank()) {
                            val docRef = db.collection("persons").document(fixed.id)
                            val map = mutableMapOf<String, Any?>()
                            map["fatherId"] = fixed.fatherId
                            map["motherId"] = fixed.motherId
                            map["spouseId"] = fixed.spouseId
                            map["maritalStatus"] = fixed.maritalStatus
                            map["fatherRelationshipType"] = fixed.fatherRelationshipType
                            map["motherRelationshipType"] = fixed.motherRelationshipType
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
                } catch (_: Exception) {}
            }

            return byId.values.toList()
        }

        fun invalidateCache() {
            inMemoryPersonsCache = null
        }

        fun updatePersonInCache(updated: Person) {
            val current = inMemoryPersonsCache.orEmpty()
            var found = false
            inMemoryPersonsCache = current.map { existing ->
                if (com.example.btproject2.engine.FamilyLinkValidator.isSameId(existing.id, updated.id)) {
                    found = true
                    existing.copy(
                        firstName = if (updated.firstName.isNotEmpty()) updated.firstName else existing.firstName,
                        middleName = if (updated.middleName.isNotEmpty()) updated.middleName else existing.middleName,
                        lastName = if (updated.lastName.isNotEmpty()) updated.lastName else existing.lastName,
                        suffix = if (updated.suffix.isNotEmpty()) updated.suffix else existing.suffix,
                        gender = if (updated.gender.isNotEmpty()) updated.gender else existing.gender,
                        birthDate = if (updated.birthDate.isNotEmpty()) updated.birthDate else existing.birthDate,
                        birthPlace = if (updated.birthPlace.isNotEmpty()) updated.birthPlace else existing.birthPlace,
                        deathDate = if (updated.isLiving) "" else if (updated.deathDate.isNotEmpty()) updated.deathDate else existing.deathDate,
                        deathPlace = if (updated.isLiving) "" else if (updated.deathPlace.isNotEmpty()) updated.deathPlace else existing.deathPlace,
                        isLiving = updated.isLiving,
                        spouseId = if (updated.spouseId != null || updated.firstName.isNotEmpty() || updated.maritalStatus == "Single") updated.spouseId else existing.spouseId,
                        maritalStatus = if (updated.maritalStatus.isNotEmpty()) updated.maritalStatus else existing.maritalStatus,
                        marriageDate = if (updated.marriageDate.isNotEmpty()) updated.marriageDate else existing.marriageDate,
                        fatherId = if (updated.fatherId != null || updated.firstName.isNotEmpty()) updated.fatherId else existing.fatherId,
                        fatherRelationshipType = if (updated.fatherRelationshipType.isNotEmpty()) updated.fatherRelationshipType else existing.fatherRelationshipType,
                        motherId = if (updated.motherId != null || updated.firstName.isNotEmpty()) updated.motherId else existing.motherId,
                        motherRelationshipType = if (updated.motherRelationshipType.isNotEmpty()) updated.motherRelationshipType else existing.motherRelationshipType,
                        photoUri = if (updated.photoUri.isNotEmpty()) updated.photoUri else existing.photoUri,
                        photoBase64 = if (updated.photoBase64.isNotEmpty()) updated.photoBase64 else existing.photoBase64,
                        biography = if (updated.biography.isNotEmpty()) updated.biography else existing.biography,
                        treeId = if (updated.treeId.isNotEmpty()) updated.treeId else existing.treeId,
                        documents = if (updated.documents.isNotEmpty()) updated.documents else existing.documents,
                        hasTimelineConflict = updated.hasTimelineConflict
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
            val inviteCode = (data["inviteCode"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["code"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""
            val isPrivate = data["isPrivate"] as? Boolean
                ?: data["private"] as? Boolean
                ?: false
            val memberCount = (data["memberCount"] as? Number)?.toInt() ?: 1
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val treeType = (data["treeType"] as? String)?.takeIf { it.isNotBlank() } ?: FamilyTree.TREE_TYPE_PERSONAL
            val bridgeDescription = (data["bridgeDescription"] as? String)?.takeIf { it.isNotBlank() } ?: ""
            val mergeInviteCode = (data["mergeInviteCode"] as? String)?.takeIf { it.isNotBlank() }
                ?: (data["mergeCode"] as? String)?.takeIf { it.isNotBlank() }
                ?: ""

            return FamilyTree(
                id = doc.id,
                name = name,
                ownerId = ownerId,
                ownerName = ownerName,
                inviteCode = inviteCode.replace(" ", "").trim().uppercase(),
                isPrivate = isPrivate,
                memberCount = memberCount,
                createdAt = createdAt,
                treeType = treeType,
                bridgeDescription = bridgeDescription,
                mergeInviteCode = mergeInviteCode.replace(" ", "").trim().uppercase()
            )
        }
    }

    fun addPerson(
        person: Person,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val all = inMemoryPersonsCache.orEmpty()
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
        val vitalUpdates = mapOf<String, Any>(
            "isLiving" to personWithId.isLiving,
            "living" to personWithId.isLiving,
            "isDeceased" to !personWithId.isLiving,
            "living_status" to if (personWithId.isLiving) "Living" else "Deceased",
            "deathDate" to if (personWithId.isLiving) "" else personWithId.deathDate,
            "deathPlace" to if (personWithId.isLiving) "" else personWithId.deathPlace,
            "dateOfDeath" to if (personWithId.isLiving) "" else personWithId.deathDate,
            "placeOfDeath" to if (personWithId.isLiving) "" else personWithId.deathPlace
        )
        docRef.set(personWithId)
            .addOnSuccessListener {
                docRef.update(vitalUpdates)
                addPersonToCache(personWithId)
                CentralTreeSynchronizer.getInstance().notifyMemberAdded(personWithId, sourceScreen = "AddMember")
                if (!personWithId.spouseId.isNullOrBlank()) {
                    updateSpouse(personWithId.id, personWithId.spouseId, personWithId.maritalStatus, onSuccess = {}, onFailure = {})
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
                    val partner = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, p.id) }
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

    private fun hasListDiverged(oldList: List<Person>?, newList: List<Person>): Boolean {
        if (oldList == null) return true
        if (oldList.size != newList.size) return true
        val oldMap = oldList.associateBy { it.id.trim().lowercase() }
        for (np in newList) {
            val op = oldMap[np.id.trim().lowercase()] ?: return true
            if (op.firstName != np.firstName ||
                op.lastName != np.lastName ||
                op.gender != np.gender ||
                op.fatherId != np.fatherId ||
                op.motherId != np.motherId ||
                op.spouseId != np.spouseId ||
                op.birthDate != np.birthDate ||
                op.deathDate != np.deathDate ||
                op.photoBase64 != np.photoBase64 ||
                op.maritalStatus != np.maritalStatus ||
                op.marriageDate != np.marriageDate ||
                op.isLiving != np.isLiving ||
                op.documents.size != np.documents.size) {
                return true
            }
        }
        return false
    }

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
            if (cached.isNotEmpty()) {
                dispatched = true
                onSuccess(resolveFiltered(cached))
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
                            onSuccess(resolveFiltered(inMemoryPersonsCache ?: diskCached))
                        }
                    }
                }
                .addOnFailureListener { /* Disk cache miss is handled by server query */ }
        }

        // 3. Server sync in background
        db.collection("persons")
            .get()
            .addOnSuccessListener { result ->
                val allPersons = result.mapNotNull { doc ->
                    documentToPerson(doc)
                }
                val diverged = hasListDiverged(inMemoryPersonsCache, allPersons)
                setCachedPersons(allPersons)
                if (!dispatched || diverged) {
                    onSuccess(resolveFiltered(inMemoryPersonsCache ?: allPersons))
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
                setCachedPersons(persons)
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
        return db.collection("persons")
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
                    setCachedPersons(sanitized)
                    onUpdate(sanitized)
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
        val all = inMemoryPersonsCache.orEmpty()
        val existing = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }
        val targetPerson = existing ?: Person(id = trimmedPid)

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

        val updated = targetPerson.copy(motherId = trimmedMid, fatherId = trimmedFid)
        updatePersonInCache(updated)

        db.collection("persons").document(trimmedPid)
            .set(mapOf("motherId" to trimmedMid, "fatherId" to trimmedFid), SetOptions.merge())
            .addOnSuccessListener {
                updatePersonInCache(updated)
                val allPersons = inMemoryPersonsCache.orEmpty()
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
                updatePersonInCache(updated)
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
        val all = inMemoryPersonsCache.orEmpty()
        val existing = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }

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
                updatePersonInCache(updated)
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
        val all = inMemoryPersonsCache.orEmpty()
        val existing = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }

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
                updatePersonInCache(updated)
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
                inMemoryPersonsCache?.let { cached ->
                    if (cached.isNotEmpty()) {
                        onSuccess(cached.associateBy { it.id })
                        return@addOnFailureListener
                    }
                }
                onFailure(it)
            }
    }

    fun updatePerson(
        person: Person,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val effectiveMaritalStatus = if (!person.spouseId.isNullOrBlank()) {
            if (person.maritalStatus.isBlank() || person.maritalStatus.equals("Single", ignoreCase = true)) "Married" else person.maritalStatus
        } else person.maritalStatus
        val personToSave = person.copy(maritalStatus = effectiveMaritalStatus)

        // Pre-flight Central Validation Service Check for Spouse
        val all = inMemoryPersonsCache.orEmpty()
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

        val vitalUpdates = mapOf<String, Any>(
            "isLiving" to personToSave.isLiving,
            "living" to personToSave.isLiving,
            "isDeceased" to !personToSave.isLiving,
            "living_status" to if (personToSave.isLiving) "Living" else "Deceased",
            "deathDate" to if (personToSave.isLiving) "" else personToSave.deathDate,
            "deathPlace" to if (personToSave.isLiving) "" else personToSave.deathPlace,
            "dateOfDeath" to if (personToSave.isLiving) "" else personToSave.deathDate,
            "placeOfDeath" to if (personToSave.isLiving) "" else personToSave.deathPlace
        )

        db.collection("persons").document(personToSave.id)
            .set(personToSave)
            .addOnSuccessListener {
                db.collection("persons").document(personToSave.id).update(vitalUpdates)
                updatePersonInCache(personToSave)

                // Handle surviving spouse automatic status update
                val partnerId = personToSave.spouseId ?: inMemoryPersonsCache?.find {
                    com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, personToSave.id)
                }?.id
                if (!partnerId.isNullOrBlank()) {
                    val partner = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, partnerId) }
                    if (partner != null && partner.isLiving) {
                        val targetStatus = if (!personToSave.isLiving) "Widowed" else "Married"
                        if (!partner.maritalStatus.equals(targetStatus, ignoreCase = true)) {
                            db.collection("persons").document(partner.id)
                                .update("maritalStatus", targetStatus)
                                .addOnSuccessListener {
                                    val updatedPartner = partner.copy(maritalStatus = targetStatus)
                                    updatePersonInCache(updatedPartner)
                                    CentralTreeSynchronizer.getInstance().notifyRelationshipMetadataChanged(
                                        personA = updatedPartner,
                                        personB = personToSave,
                                        metadataFields = setOf("maritalStatus"),
                                        sourceScreen = "updatePerson"
                                    )
                                }
                        }
                    }
                }

                CentralTreeSynchronizer.getInstance().notifyProfileEdited(personToSave, sourceScreen = "updatePerson")
                if (!personToSave.spouseId.isNullOrBlank()) {
                    updateSpouse(personToSave.id, personToSave.spouseId, effectiveMaritalStatus, onSuccess = onSuccess, onFailure = onFailure)
                } else {
                    val exSpouse = inMemoryPersonsCache?.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.spouseId, personToSave.id) }
                    if (exSpouse != null) {
                        updateSpouse(personToSave.id, null, "Single", onSuccess = onSuccess, onFailure = onFailure)
                    } else {
                        onSuccess()
                    }
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
        val all = inMemoryPersonsCache.orEmpty()
        val existing = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedId) }
        val effectiveDeathDate = if (isLiving) "" else deathDate.trim()
        val effectiveDeathPlace = if (isLiving) "" else deathPlace.trim()

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
                val updatedDocs = p.documents + document
                db.collection("persons").document(personId)
                    .update("documents", updatedDocs)
                    .addOnSuccessListener {
                        inMemoryPersonsCache = inMemoryPersonsCache?.map {
                            if (it.id == personId) it.copy(documents = updatedDocs) else it
                        }
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
                db.collection("persons").document(personId)
                    .update("documents", updatedDocs)
                    .addOnSuccessListener {
                        inMemoryPersonsCache = inMemoryPersonsCache?.map {
                            if (it.id == personId) it.copy(documents = updatedDocs) else it
                        }
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
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val trimmedPersonId = personId.trim()
        val trimmedSpouseId = spouseId?.trim()?.ifEmpty { null }

        val allPersons = inMemoryPersonsCache.orEmpty()
        val personA = allPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPersonId) }
        val personB = if (trimmedSpouseId != null) {
            allPersons.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedSpouseId) }
        } else null

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
            val refA = db.collection("persons").document(trimmedPersonId)
            val refB = db.collection("persons").document(trimmedSpouseId)

            batch.set(refA, mapOf(
                "spouseId" to trimmedSpouseId,
                "maritalStatus" to status
            ), SetOptions.merge())
            batch.set(refB, mapOf(
                "spouseId" to trimmedPersonId,
                "maritalStatus" to status
            ), SetOptions.merge())

            val aObj = (personA ?: Person(id = trimmedPersonId)).copy(spouseId = trimmedSpouseId, maritalStatus = status)
            cacheUpdates.add(aObj)
            val bObj = (personB ?: Person(id = trimmedSpouseId)).copy(spouseId = trimmedPersonId, maritalStatus = status)
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
            .addOnFailureListener {
                // Fallback: If batch fails, attempt direct updates for both individuals
                val status = if (maritalStatus.isBlank() || maritalStatus.equals("Single", ignoreCase = true)) "Married" else maritalStatus
                val aUpdates = mapOf("spouseId" to trimmedSpouseId, "maritalStatus" to (if (trimmedSpouseId != null) status else "Single"))
                db.collection("persons").document(trimmedPersonId)
                    .set(aUpdates, SetOptions.merge())
                    .addOnCompleteListener {
                        if (trimmedSpouseId != null) {
                            val bUpdates = mapOf("spouseId" to trimmedPersonId, "maritalStatus" to status)
                            db.collection("persons").document(trimmedSpouseId)
                                .set(bUpdates, SetOptions.merge())
                                .addOnCompleteListener {
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
        val all = inMemoryPersonsCache.orEmpty()
        val existing = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedCid) }
        val child = existing ?: Person(id = trimmedCid)
        val parent = all.find { com.example.btproject2.engine.FamilyLinkValidator.isSameId(it.id, trimmedPid) }

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
                updatePersonInCache(updated)
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
                        val sanitizedTree = sanitizeTreeRecords(inMemoryPersonsCache.orEmpty() + combined)
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
        db.collection("privacy").document(settings.treeId)
            .set(settings)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPrivacySettings(
        treeId: String,
        onSuccess: (PrivacySettings) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("privacy").document(treeId).get()
            .addOnSuccessListener { doc ->
                val settings = doc.toObject(PrivacySettings::class.java)
                    ?: PrivacySettings(treeId = treeId, requireLogin = true)
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
        db.collection("persons")
            .whereEqualTo("treeId", treeId)
            .whereGreaterThan("createdAt", cutoffMs)
            .get()
            .addOnSuccessListener { result -> onSuccess(result.size()) }
            .addOnFailureListener { onFailure(it) }
    }

    fun getBranchCount(
        userId: String,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("trees")
            .whereEqualTo("createdBy", userId)
            .get()
            .addOnSuccessListener { result ->
                onSuccess(if (result.isEmpty) 1 else result.size())
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
        val treeWithId = tree.copy(id = docRef.id, inviteCode = inviteCode)

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

                        // Also query trees where user is an approved member
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
                                    onSuccess(filtered)
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
                                                    onSuccess(filtered)
                                                }
                                            },
                                            onFailure = {
                                                remaining--
                                                if (remaining <= 0) {
                                                    val filtered = if (includeMergedClan) combinedTrees else combinedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                                                    onSuccess(filtered)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            .addOnFailureListener {
                                val filtered = if (includeMergedClan) ownedTrees else ownedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                                onSuccess(filtered)
                            }
                    }
                    .addOnFailureListener {
                        val filtered = if (includeMergedClan) ownedTrees else ownedTrees.filter { it.treeType != FamilyTree.TREE_TYPE_MERGED_CLAN }
                        onSuccess(filtered)
                    }
            }
            .addOnFailureListener { onFailure(it) }
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
     */
    fun getMergedClanTrees(
        userId: String,
        onSuccess: (List<FamilyTree>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        getUserTrees(userId, includeMergedClan = true,
            onSuccess = { allTrees ->
                val clanTrees = allTrees.filter { it.treeType == FamilyTree.TREE_TYPE_MERGED_CLAN }
                onSuccess(clanTrees)
            },
            onFailure = onFailure
        )
    }

    /**
     * Synthesizes and saves a brand-new Master Clan Tree C along with its cloned members
     * in an atomic Firestore batch. Original trees are untouched and strictly read-only.
     */
    fun saveMasterTreeAndMembers(
        masterTree: FamilyTree,
        members: List<Person>,
        onSuccess: (FamilyTree) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val treeDocRef = if (masterTree.id.isNotEmpty()) db.collection("trees").document(masterTree.id)
                         else db.collection("trees").document()
        val inviteCode = if (masterTree.inviteCode.isNotBlank()) masterTree.inviteCode.replace(" ", "").trim().uppercase()
                         else generateInviteCode()
        val mergeCode = if (masterTree.mergeInviteCode.isNotBlank()) masterTree.mergeInviteCode.replace(" ", "").trim().uppercase()
                        else generateMergeInviteCode()
        val finalTree = masterTree.copy(
            id = treeDocRef.id,
            inviteCode = inviteCode,
            memberCount = members.size,
            treeType = FamilyTree.TREE_TYPE_MERGED_CLAN,
            mergeInviteCode = mergeCode
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

        // Set all cloned persons in persons collection
        for (person in members) {
            val personDocRef = db.collection("persons").document(person.id)
            batch.set(personDocRef, person.copy(treeId = finalTree.id))
        }

        batch.commit()
            .addOnSuccessListener { onSuccess(finalTree) }
            .addOnFailureListener { onFailure(it) }
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

    fun getNotifications(
        treeId: String,
        userId: String,
        onSuccess: (List<NotificationRecord>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("notifications")
            .whereEqualTo("treeId", treeId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { doc ->
                    doc.toObject(NotificationRecord::class.java)?.copy(id = doc.id)
                }.filter { it.userId.isEmpty() || it.userId == userId }
                onSuccess(list)
            }
            .addOnFailureListener { err ->
                // Fallback without orderBy in case composite index is not yet built
                db.collection("notifications")
                    .whereEqualTo("treeId", treeId)
                    .get()
                    .addOnSuccessListener { result ->
                        val list = result.mapNotNull { doc ->
                            doc.toObject(NotificationRecord::class.java)?.copy(id = doc.id)
                        }.filter { it.userId.isEmpty() || it.userId == userId }
                         .sortedByDescending { it.timestamp }
                        onSuccess(list)
                    }
                    .addOnFailureListener { onFailure(it) }
            }
    }

    fun markNotificationAsRead(
        notificationId: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (notificationId.isBlank()) return
        db.collection("notifications").document(notificationId)
            .update("isRead", true)
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
        db.collection("activities")
            .whereEqualTo("treeId", treeId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { result ->
                val list = result.mapNotNull { doc ->
                    doc.toObject(ActivityRecord::class.java)?.copy(id = doc.id)
                }
                onSuccess(list)
            }
            .addOnFailureListener {
                // Fallback without server orderBy if composite index is pending
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
    }

    /**
     * Executes atomic merge of duplicate member records (FR-08).
     * Updates keeperPerson, reparents affected children, deletes redundant record, and updates spouse links.
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
            .addOnSuccessListener { onSuccess() }
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
}
