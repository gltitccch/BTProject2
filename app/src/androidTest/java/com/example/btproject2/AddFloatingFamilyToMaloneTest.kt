package com.example.btproject2

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.btproject2.models.Person
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddFloatingFamilyToMaloneTest {

    @Test
    fun injectFloating12MemberFamilyIntoMaloneTree() {
        val db = FirebaseFirestore.getInstance()

        val maloneTreeId = "KgVuKYSIJjyXKepjNEal"
        val maloneTreeName = "Malone Family"

        println("=== [INJECT] Target Tree: '$maloneTreeName' (ID: $maloneTreeId) ===")

        val now = System.currentTimeMillis()

        // IDs
        val loloId = "floating_morales_lolo"
        val lolaId = "floating_morales_lola"
        val fatherId = "floating_morales_father"
        val motherId = "floating_morales_mother"
        val uncleId = "floating_morales_uncle"
        val auntId = "floating_morales_aunt"
        val sonId = "floating_morales_son"
        val sisterId = "floating_morales_sister"
        val cousin1Id = "floating_morales_cousin1"
        val cousin2Id = "floating_morales_cousin2"
        val cousin3Id = "floating_morales_cousin3"
        val cousin4Id = "floating_morales_cousin4"

        // 12 Members: 3 Generations of Morales Family
        val floating12Members = listOf(
            // Gen 1: Grandparents (2)
            Person(
                id = loloId,
                treeId = maloneTreeId,
                firstName = "Fernando",
                lastName = "Morales",
                gender = "Male",
                birthDate = "1942-01-10",
                birthPlace = "Vigan, Ilocos Sur",
                isLiving = true,
                spouseId = lolaId,
                maritalStatus = "Married",
                fatherId = null,
                motherId = null,
                updatedAt = now
            ),
            Person(
                id = lolaId,
                treeId = maloneTreeId,
                firstName = "Remedios",
                lastName = "Morales",
                gender = "Female",
                birthDate = "1945-05-18",
                birthPlace = "Vigan, Ilocos Sur",
                isLiving = true,
                spouseId = loloId,
                maritalStatus = "Married",
                fatherId = null,
                motherId = null,
                updatedAt = now
            ),

            // Gen 2: Parents & Uncle/Aunt (4)
            Person(
                id = fatherId,
                treeId = maloneTreeId,
                firstName = "Danilo",
                lastName = "Morales",
                gender = "Male",
                birthDate = "1968-04-15",
                birthPlace = "Vigan, Ilocos Sur",
                isLiving = true,
                spouseId = motherId,
                maritalStatus = "Married",
                fatherId = loloId,
                motherId = lolaId,
                updatedAt = now
            ),
            Person(
                id = motherId,
                treeId = maloneTreeId,
                firstName = "Cynthia",
                lastName = "Ramos",
                gender = "Female",
                birthDate = "1970-08-22",
                birthPlace = "Laoag, Ilocos Norte",
                isLiving = true,
                spouseId = fatherId,
                maritalStatus = "Married",
                fatherId = null,
                motherId = null,
                updatedAt = now
            ),
            Person(
                id = uncleId,
                treeId = maloneTreeId,
                firstName = "Eduardo",
                lastName = "Morales",
                gender = "Male",
                birthDate = "1972-09-03",
                birthPlace = "Vigan, Ilocos Sur",
                isLiving = true,
                spouseId = auntId,
                maritalStatus = "Married",
                fatherId = loloId,
                motherId = lolaId,
                updatedAt = now
            ),
            Person(
                id = auntId,
                treeId = maloneTreeId,
                firstName = "Maricel",
                lastName = "Cruz",
                gender = "Female",
                birthDate = "1974-12-11",
                birthPlace = "San Fernando, La Union",
                isLiving = true,
                spouseId = uncleId,
                maritalStatus = "Married",
                fatherId = null,
                motherId = null,
                updatedAt = now
            ),

            // Gen 3: Children & First Cousins (6)
            Person(
                id = sonId,
                treeId = maloneTreeId,
                firstName = "Gabriel",
                lastName = "Morales",
                gender = "Male",
                birthDate = "1996-11-05",
                birthPlace = "Quezon City",
                isLiving = true,
                spouseId = null,
                maritalStatus = "Single",
                fatherId = fatherId,
                motherId = motherId,
                updatedAt = now
            ),
            Person(
                id = sisterId,
                treeId = maloneTreeId,
                firstName = "Andrea",
                lastName = "Morales",
                gender = "Female",
                birthDate = "2000-02-14",
                birthPlace = "Quezon City",
                isLiving = true,
                spouseId = null,
                maritalStatus = "Single",
                fatherId = fatherId,
                motherId = motherId,
                updatedAt = now
            ),
            Person(
                id = cousin1Id,
                treeId = maloneTreeId,
                firstName = "Paolo",
                lastName = "Morales",
                gender = "Male",
                birthDate = "1998-03-25",
                birthPlace = "Manila",
                isLiving = true,
                spouseId = null,
                maritalStatus = "Single",
                fatherId = uncleId,
                motherId = auntId,
                updatedAt = now
            ),
            Person(
                id = cousin2Id,
                treeId = maloneTreeId,
                firstName = "Bea",
                lastName = "Morales",
                gender = "Female",
                birthDate = "2001-07-19",
                birthPlace = "Manila",
                isLiving = true,
                spouseId = null,
                maritalStatus = "Single",
                fatherId = uncleId,
                motherId = auntId,
                updatedAt = now
            ),
            Person(
                id = cousin3Id,
                treeId = maloneTreeId,
                firstName = "Carlo",
                lastName = "Morales",
                gender = "Male",
                birthDate = "2003-10-08",
                birthPlace = "Manila",
                isLiving = true,
                spouseId = null,
                maritalStatus = "Single",
                fatherId = uncleId,
                motherId = auntId,
                updatedAt = now
            ),
            Person(
                id = cousin4Id,
                treeId = maloneTreeId,
                firstName = "Diane",
                lastName = "Morales",
                gender = "Female",
                birthDate = "2006-06-30",
                birthPlace = "Manila",
                isLiving = true,
                spouseId = null,
                maritalStatus = "Single",
                fatherId = uncleId,
                motherId = auntId,
                updatedAt = now
            )
        )

        val batch = db.batch()
        floating12Members.forEach { member ->
            batch.set(db.collection("persons").document(member.id), member)
        }

        // Count total members in Malone tree
        val allPersonsQuery = Tasks.await(
            db.collection("persons")
                .whereEqualTo("treeId", maloneTreeId)
                .get()
        )
        // Set tree memberCount directly to reflect actual count
        val totalMembers = allPersonsQuery.documents.map { it.id }.toSet() + floating12Members.map { it.id }.toSet()
        val treeRef = db.collection("trees").document(maloneTreeId)
        batch.update(treeRef, "memberCount", totalMembers.size)

        Tasks.await(batch.commit())
        println("=== [INJECT] Successfully injected 12 floating members into $maloneTreeName ===")

        val verifyQuery = Tasks.await(
            db.collection("persons")
                .whereEqualTo("treeId", maloneTreeId)
                .get()
        )
        println("=== [INJECT] Total member count in $maloneTreeName: ${verifyQuery.size()} ===")
        assertTrue("All 12 members must be present", verifyQuery.documents.any { it.id == loloId })
        assertTrue("All 12 members must be present", verifyQuery.documents.any { it.id == lolaId })
        assertTrue("All 12 members must be present", verifyQuery.documents.any { it.id == cousin1Id })
        assertTrue("All 12 members must be present", verifyQuery.documents.any { it.id == cousin4Id })
    }
}
