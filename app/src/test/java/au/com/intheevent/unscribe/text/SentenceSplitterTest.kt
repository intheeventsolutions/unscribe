package au.com.intheevent.unscribe.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {
    private fun split(text: String) = SentenceSplitter.split(text).map { it.text(text) }

    @Test
    fun splitsOnTerminalPunctuation() {
        assertEquals(
            listOf("It was cold.", "Was it?", "Yes!"),
            split("It was cold. Was it? Yes!"),
        )
    }

    @Test
    fun keepsAbbreviationsAndInitialsTogether() {
        assertEquals(
            listOf("Mr. Darcy met Dr. J. R. Watson on St. George's Day.", "They talked."),
            split("Mr. Darcy met Dr. J. R. Watson on St. George's Day. They talked."),
        )
    }

    @Test
    fun keepsClosingQuotesWithTheSentence() {
        assertEquals(
            listOf("“Stop!” he said.", "“Why?”", "She ran."),
            split("“Stop!” he said. “Why?” She ran."),
        )
    }

    @Test
    fun doesNotSplitBeforeLowercase() {
        assertEquals(listOf("Wait... and see."), split("Wait... and see."))
    }

    @Test
    fun handlesTextWithoutFinalPunctuation() {
        assertEquals(listOf("Chapter One"), split("Chapter One"))
        assertEquals(emptyList<String>(), split("   "))
    }

    @Test
    fun breaksVeryLongSentencesAtClauses() {
        val clause = "and the wind blew over the hills and far away"
        val text = List(12) { clause }.joinToString(", ") + "."
        val parts = split(text)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= SentenceSplitter.MAX_CHARS })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }

    @Test
    fun rangesCoverEverySentenceInOrder() {
        val text = "One. Two. Three."
        val sentences = SentenceSplitter.split(text)
        assertEquals(listOf(0, 5, 10), sentences.map { it.start })
    }
}
