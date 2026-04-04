package com.example.btproject2.engine

import com.example.btproject2.models.Person

class AncestorFinder {

    fun findAncestors(
        personId: String,
        personMap: Map<String, Person>,
        maxDepth: Int = 5
    ): Map<String, Pair<Int, List<Person>>> {

        val ancestors = mutableMapOf<String, Pair<Int, List<Person>>>()
        val startPerson = personMap[personId] ?: return ancestors

        data class QueueItem(
            val person: Person,
            val distance: Int,
            val path: List<Person>
        )

        val queue = ArrayDeque<QueueItem>()
        queue.add(QueueItem(startPerson, 0, listOf(startPerson)))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current.distance >= maxDepth) continue

            current.person.motherId?.let { motherId ->
                val mother = personMap[motherId]
                if (mother != null && !ancestors.containsKey(motherId)) {
                    val newPath = current.path + mother
                    val newDistance = current.distance + 1
                    ancestors[motherId] = Pair(newDistance, newPath)
                    queue.add(QueueItem(mother, newDistance, newPath))
                }
            }

            current.person.fatherId?.let { fatherId ->
                val father = personMap[fatherId]
                if (father != null && !ancestors.containsKey(fatherId)) {
                    val newPath = current.path + father
                    val newDistance = current.distance + 1
                    ancestors[fatherId] = Pair(newDistance, newPath)
                    queue.add(QueueItem(father, newDistance, newPath))
                }
            }
        }

        return ancestors
    }
}