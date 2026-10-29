package au.com.intheevent.unscribe.tts

/**
 * Female speakers in the en_AU-librivox-medium Piper model.
 *
 * [lengthScale] is the per-voice pace correction from the model card: some
 * narrators rush at the trained rate, so they're slowed to sound natural at 1x.
 */
enum class Voice(val speakerId: Int, val displayName: String, val lengthScale: Float) {
    TULLY(8, "Tully", 1.25f),
    BINDI(1, "Bindi", 1.0f),
    MARLO(2, "Marlo", 1.08f),
    KIRRA(3, "Kirra", 1.0f),
    MATILDA(7, "Matilda", 1.0f),
    WILLOW(9, "Willow", 1.0f);

    /**
     * sherpa-onnx divides the model's length scale by `speed`, so fold the
     * per-voice correction into the speed passed at generation time.
     */
    fun engineSpeed(userSpeed: Float): Float = userSpeed / lengthScale

    companion object {
        val DEFAULT = TULLY
    }
}
