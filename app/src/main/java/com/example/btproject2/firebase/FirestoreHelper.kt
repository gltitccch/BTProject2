package com.example.btproject2.firebase

import com.example.btproject2.models.Person
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreHelper {

    private val db = FirebaseFirestore.getInstance()

    fun addPerson(
        person: Person,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val docRef = db.collection("persons").document()
        val personWithId = person.copy(id = docRef.id)

        docRef.set(personWithId)
            .addOnSuccessListener { onSuccess(docRef.id) }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPerson(
        personId: String,
        onSuccess: (Person?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("persons").document(personId).get()
            .addOnSuccessListener { doc ->
                onSuccess(doc.toObject(Person::class.java))
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getPersonsByTree(
        treeId: String,
        onSuccess: (List<Person>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("persons")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                val persons = result.map { it.toObject(Person::class.java) }
                onSuccess(persons)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateParents(
        personId: String,
        motherId: String?,
        fatherId: String?,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("persons").document(personId)
            .update(
                mapOf(
                    "motherId" to motherId,
                    "fatherId" to fatherId
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun loadEntireTree(
        treeId: String,
        onSuccess: (Map<String, Person>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("persons")
            .whereEqualTo("treeId", treeId)
            .get()
            .addOnSuccessListener { result ->
                val personMap = mutableMapOf<String, Person>()
                for (doc in result) {
                    val person = doc.toObject(Person::class.java)
                    personMap[person.id] = person
                }
                onSuccess(personMap)
            }
            .addOnFailureListener { onFailure(it) }
    }
}