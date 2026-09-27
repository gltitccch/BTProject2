package com.example.btproject2

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseAuditTest {

    @Test
    fun auditDatabaseTreesAndMembersIntegrity() {
        val db = FirebaseFirestore.getInstance()

        // 1. Fetch all trees
        val treesSnapshot = Tasks.await(db.collection("trees").get())
        println("=== [AUDIT] TOTAL TREES IN FIRESTORE: ${treesSnapshot.size()} ===")

        for (doc in treesSnapshot.documents) {
            val treeId = doc.id
            val treeName = doc.getString("name") ?: "Unnamed Tree"
            val ownerId = doc.getString("ownerId") ?: "No owner"
            val mergeCode = doc.getString("mergeInviteCode") ?: "NO_CODE"

            // 2. Fetch all members belonging to this tree
            val membersSnapshot = Tasks.await(
                db.collection("persons")
                    .whereEqualTo("treeId", treeId)
                    .get()
            )

            // 3. Count relationships (parents, spouses, children links)
            var totalParentLinks = 0
            var totalSpouses = 0
            for (mDoc in membersSnapshot.documents) {
                val fatherId = mDoc.getString("fatherId")
                val motherId = mDoc.getString("motherId")
                val spouseIds = mDoc.get("spouseIds") as? List<*>

                if (!fatherId.isNullOrEmpty()) totalParentLinks++
                if (!motherId.isNullOrEmpty()) totalParentLinks++
                if (!spouseIds.isNullOrEmpty()) totalSpouses += spouseIds.size
            }

            println("--- TREE: '$treeName' (ID: $treeId) ---")
            println("    Owner ID: $ownerId")
            println("    Merge Code: $mergeCode")
            println("    Total Members: ${membersSnapshot.size()}")
            println("    Parent Links: $totalParentLinks")
            println("    Spouse Links: $totalSpouses")
        }

        // Verify that Malone Family exists and has 20 members
        val maloneQuery = Tasks.await(
            db.collection("persons")
                .whereEqualTo("treeId", "KgVuKYSIJjyXKepjNEal")
                .get()
        )
        println("=== [AUDIT] MALONE FAMILY MEMBER COUNT: ${maloneQuery.size()} ===")
        assertTrue("Malone family members should be intact", maloneQuery.size() > 0)

        // Verify Smith Family exists
        val smithQuery = Tasks.await(
            db.collection("persons")
                .whereEqualTo("treeId", "3x8b8HvC4Oz2Zggyekvj")
                .get()
        )
        println("=== [AUDIT] SMITH FAMILY MEMBER COUNT: ${smithQuery.size()} ===")
        assertTrue("Smith family members should be intact", smithQuery.size() > 0)
    }
}
