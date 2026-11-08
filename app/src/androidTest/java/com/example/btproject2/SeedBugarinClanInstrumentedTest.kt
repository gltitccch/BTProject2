package com.example.btproject2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.btproject2.models.BugarinClanData
import com.example.btproject2.models.FamilyTree
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeedBugarinClanInstrumentedTest {

    @Test
    fun seedAuthentic200MemberBugarinClan() {
        val db = FirebaseFirestore.getInstance()
        val treeId = BugarinClanData.TREE_ID
        val now = System.currentTimeMillis()
        val auth = FirebaseAuth.getInstance()
        val currentUid = auth.currentUser?.uid ?: "eyvZ4rq9cZZtu6EcqP9fXI8L3812"
        val currentName = auth.currentUser?.displayName?.ifBlank { "Renzy Bugarin" } ?: "Renzy Bugarin"

        println("=== [SEED] Starting Seeding for treeId: $treeId, currentUid: $currentUid ===")

        // 1. Fetch any existing members for this tree
        val oldMembersQuery = Tasks.await(
            db.collection("persons")
                .whereEqualTo("treeId", treeId)
                .get()
        )
        println("=== [SEED] Found ${oldMembersQuery.size()} existing persons to purge ===")

        // 2. Purge old members in chunks of 300
        val oldDocs = oldMembersQuery.documents
        for (chunk in oldDocs.chunked(300)) {
            val batch = db.batch()
            for (doc in chunk) {
                batch.delete(doc.reference)
            }
            Tasks.await(batch.commit())
        }
        println("=== [SEED] Purge complete ===")

        // 3. Create the authentic tree record and the 200 authentic members
        val bugarinTree = BugarinClanData.createTree(now).copy(
            ownerId = currentUid,
            ownerName = currentName,
            memberCount = 200,
            memberQuota = 500
        )
        val bugarinMembers = BugarinClanData.createMembers(now)
        assertEquals("Must generate exactly 200 authentic members", 200, bugarinMembers.size)

        // 4. Save the tree document
        Tasks.await(db.collection("trees").document(treeId).set(bugarinTree))
        println("=== [SEED] Tree metadata document written to Firestore ===")

        // 5. Commit the 200 members in a batch
        val memberBatch = db.batch()
        for (member in bugarinMembers) {
            memberBatch.set(db.collection("persons").document(member.id), member)
        }
        Tasks.await(memberBatch.commit())
        println("=== [SEED] 200 Members committed to Firestore successfully ===")

        // 6. Direct Verification Query
        val verifyMembers = Tasks.await(
            db.collection("persons")
                .whereEqualTo("treeId", treeId)
                .get()
        )
        println("=== [SEED VERIFICATION] Firestore persons count for $treeId: ${verifyMembers.size()} ===")
        assertEquals("Firestore must now hold exactly 200 authentic members", 200, verifyMembers.size())

        // 7. Update SharedPreferences on device so active tree is Bugarin Family
        val context = ApplicationProvider.getApplicationContext<Context>()
        val treePrefs = context.getSharedPreferences("kintrace_tree_prefs", Context.MODE_PRIVATE)
        treePrefs.edit()
            .putString("active_tree_id", treeId)
            .putString("active_tree_name", "Bugarin Family")
            .putString("user_tree_id_$currentUid", treeId)
            .putString("user_tree_name_$currentUid", "Bugarin Family")
            .apply()
        println("=== [SEED] Device SharedPreferences updated to Bugarin Family ($treeId) ===")
    }
}
