package au.com.intheevent.unscribe.text

/**
 * Estimates which word is being spoken. The voice model doesn't report word
 * timings, so a sentence's audio is shared out by word length, with extra
 * weight for the pauses punctuation brings. Good to within a word or so, and
 * it starts fresh every sentence, so errors never accumulate.
 */
object WordTiming {
    /** A word's character range in its sentence (end exclusive) and its share of the audio. */
    data class Word(val start: Int, val end: Int, val weight: Float)

    private val WORD = Regex("\\S+")

    fun words(sentence: String): List<Word> = WORD.findAll(sentence).map { match ->
        val token = match.value
        val spoken = token.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        Word(match.range.first, match.range.last + 1, spoken + pauseAfter(token))
    }.toList()

    /** Index of the word being heard [fraction] (0..1) of the way through the sentence's audio. */
    fun indexAt(words: List<Word>, fraction: Float): Int {
        if (words.isEmpty()) return -1
        val target = fraction.coerceIn(0f, 1f) * words.sumOf { it.weight.toDouble() }
        var total = 0.0
        words.forEachIndexed { i, word ->
            total += word.weight
            if (target < total) return i
        }
        return words.lastIndex
    }

    private fun pauseAfter(token: String): Float {
        val last = token.trimEnd('"', '\'', '”', '’', ')', ']').lastOrNull() ?: return 0f
        return when (last) {
            '.', '!', '?', '…' -> 4f
            ',', ';', ':', '—', '–' -> 2.5f
            else -> 0f
        }
    }
}
