package com.example.btproject2

import com.example.btproject2.models.Person
import com.example.btproject2.models.TriviaQuestion
import com.example.btproject2.utils.ClanTriviaEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClanTriviaEngineTest {

    private fun createMockClanTree(): List<Person> {
        val lolo = Person(
            id = "p_lolo",
            firstName = "Jose",
            lastName = "Bugarin",
            birthDate = "1932-05-14",
            birthPlace = "Batangas",
            photoUri = "", // No photo to test silhouette fallback
            isLiving = false
        )
        val lola = Person(
            id = "p_lola",
            firstName = "Carmen",
            lastName = "Bugarin",
            birthDate = "1935-05-22", // Same birth month as lolo (May)
            birthPlace = "Ilocos Sur",
            photoUri = "https://kintrace.ph/photos/lola.jpg", // Has photo
            isLiving = false
        )
        val father = Person(
            id = "p_father",
            firstName = "Roberto",
            lastName = "Bugarin",
            fatherId = "p_lolo",
            motherId = "p_lola",
            birthDate = "1962-08-10",
            birthPlace = "Quezon City",
            isLiving = true
        )
        val child = Person(
            id = "p_child",
            firstName = "Carlo",
            lastName = "Bugarin",
            fatherId = "p_father",
            birthDate = "1994-11-03",
            birthPlace = "Davao",
            isLiving = true
        )
        val cousin = Person(
            id = "p_cousin",
            firstName = "Sarah",
            lastName = "Santos",
            birthDate = "1998-02-19",
            birthPlace = "Manila",
            isLiving = true
        )
        return listOf(lolo, lola, father, child, cousin)
    }

    @Test
    fun testGenerateDeckWithFullTree_ReturnsRequestedCount() {
        val tree = createMockClanTree()
        val deck = ClanTriviaEngine.generateQuestionDeck(tree, maxQuestions = 5)

        assertEquals("Deck should return exactly 5 questions", 5, deck.size)

        for (q in deck) {
            assertEquals("Every question must have exactly 4 choices", 4, q.options.size)
            assertTrue("Correct option index must be in range 0..3", q.correctOptionIndex in 0..3)
            val correctChoice = q.options[q.correctOptionIndex]
            assertTrue("Correct option must not be blank", correctChoice.isNotBlank())
            assertNotNull("Prompt must be non-null", q.prompt)
        }
    }

    @Test
    fun testGenerateDeckWithNoPhotos_SafelyEnablesSilhouetteFallback() {
        val treeWithoutPhotos = listOf(
            Person(id = "1", firstName = "Lolo", lastName = "Jose", birthDate = "1930-01-01", photoUri = "", photoBase64 = ""),
            Person(id = "2", firstName = "Tito", lastName = "Jun", fatherId = "1", photoUri = "", photoBase64 = "")
        )

        val deck = ClanTriviaEngine.generateQuestionDeck(treeWithoutPhotos, maxQuestions = 3)
        assertTrue("Deck should generate questions without crashing", deck.isNotEmpty())

        val silhouetteQ = deck.find { it.isSilhouetteMode }
        if (silhouetteQ != null) {
            assertTrue("Silhouette questions must have 3 progressive clues", silhouetteQ.fallbackClues.size >= 3)
            assertTrue("Silhouette question prompt must indicate silhouette mode", silhouetteQ.prompt.contains("Silhouette"))
        }
    }

    @Test
    fun testGenerateDeckWithEmptyTree_NeverCrashes() {
        val deck = ClanTriviaEngine.generateQuestionDeck(emptyList(), maxQuestions = 5)
        assertEquals("Empty tree should safely pad with 5 milestone questions", 5, deck.size)

        for (q in deck) {
            assertEquals(4, q.options.size)
            assertTrue(q.correctOptionIndex in 0..3)
        }
    }

    @Test
    fun testGenerateDeckWithSingleMember_NeverCrashes() {
        val singleTree = listOf(Person(id = "solo", firstName = "Renzy", lastName = "Millare"))
        val deck = ClanTriviaEngine.generateQuestionDeck(singleTree, maxQuestions = 5)

        assertEquals("Single-member tree should safely generate 5 questions", 5, deck.size)
        for (q in deck) {
            assertEquals(4, q.options.size)
            assertTrue(q.correctOptionIndex in 0..3)
        }
    }

    @Test
    fun testCustomHostQuestions_AreInjectedAtStartOfDeck() {
        val tree = createMockClanTree()
        val customQ = TriviaQuestion(
            id = "custom_1",
            prompt = "Who forgot to bring the lechon at the 2023 reunion?",
            category = TriviaQuestion.CATEGORY_HOST_CUSTOM,
            options = listOf("Tito Boy", "Kuya Noel", "Tito Jun", "Kuya Mark"),
            correctOptionIndex = 2,
            isCustomHost = true
        )

        val deck = ClanTriviaEngine.generateQuestionDeck(tree, maxQuestions = 5, customQuestions = listOf(customQ))
        assertEquals("First question should be the custom host inside joke", "custom_1", deck[0].id)
        assertEquals("Custom question prompt should match", customQ.prompt, deck[0].prompt)
        assertEquals(5, deck.size)
    }

    @Test
    fun testDeckHasNoDuplicateQuestionPrompts() {
        val tree = createMockClanTree()
        val deck = ClanTriviaEngine.generateQuestionDeck(tree, maxQuestions = 5)
        val prompts = deck.map { it.prompt.trim().lowercase() }
        val distinctPrompts = prompts.distinct()

        assertEquals("All question prompts in the deck should be unique", distinctPrompts.size, prompts.size)
    }

    @Test
    fun testPhilippineHeritageBank_HasOver200ValidQuestions() {
        val bank = com.example.btproject2.utils.PhilippineHeritageQuestionBank.QUESTIONS
        assertTrue("Heritage question bank must have at least 200 questions, but has ${bank.size}", bank.size >= 200)

        for (q in bank) {
            assertEquals("Question '${q.prompt}' must have 4 options", 4, q.options.size)
            assertTrue("Question '${q.prompt}' correct index out of bounds", q.correctOptionIndex in 0..3)
            assertTrue("Prompt must not be empty", q.prompt.isNotBlank())
            assertTrue("Proof fact must not be empty", q.proofFact.isNotBlank())
        }
    }

    @Test
    fun testLargeDeck_DrawsExactRequestedCount_WithZeroDuplicates() {
        val tree = createMockClanTree()
        for (count in listOf(8, 10, 15, 20, 25)) {
            val deck = ClanTriviaEngine.generateQuestionDeck(tree, maxQuestions = count)
            assertEquals("Deck size should match requested count of $count", count, deck.size)

            val prompts = deck.map { it.prompt.trim().lowercase() }
            assertEquals("Deck of $count questions must contain zero duplicates", prompts.distinct().size, prompts.size)
        }
    }

    @Test
    fun testCustomNumberRngDraw_PreservesInsideJokesWhileDrawingRemaining() {
        val tree = createMockClanTree()
        val customJoke1 = TriviaQuestion(
            id = "joke_1",
            prompt = "Who brought the wrong cake?",
            category = TriviaQuestion.CATEGORY_HOST_CUSTOM,
            options = listOf("A", "B", "C", "D"),
            correctOptionIndex = 0,
            isCustomHost = true
        )
        val customJoke2 = TriviaQuestion(
            id = "joke_2",
            prompt = "Who fell asleep first at the reunion?",
            category = TriviaQuestion.CATEGORY_HOST_CUSTOM,
            options = listOf("A", "B", "C", "D"),
            correctOptionIndex = 1,
            isCustomHost = true
        )

        val targetCount = 12
        val deck = ClanTriviaEngine.generateQuestionDeck(
            members = tree,
            maxQuestions = targetCount,
            customQuestions = listOf(customJoke1, customJoke2)
        )

        assertEquals("Total deck size must be $targetCount", targetCount, deck.size)
        assertTrue("Inside joke 1 must be in deck", deck.any { it.id == "joke_1" })
        assertTrue("Inside joke 2 must be in deck", deck.any { it.id == "joke_2" })

        val prompts = deck.map { it.prompt.trim().lowercase() }
        assertEquals("No duplicate prompts allowed in deck", prompts.distinct().size, prompts.size)
    }
}
