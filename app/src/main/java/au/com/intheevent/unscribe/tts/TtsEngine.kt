package au.com.intheevent.unscribe.tts

/** Turns one sentence of text into mono float PCM. Implementations are not thread-safe. */
interface TtsEngine {
    val sampleRate: Int

    /** Loads models; slow, call off the main thread. */
    fun load()

    fun synthesize(text: String, voice: Voice, speed: Float): FloatArray

    fun release()
}
