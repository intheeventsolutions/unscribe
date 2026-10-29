package au.com.intheevent.unscribe.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTest {
    @Test
    fun defaultVoiceIsTully() {
        assertEquals(Voice.TULLY, Voice.DEFAULT)
        assertEquals(8, Voice.TULLY.speakerId)
    }

    @Test
    fun paceCorrectionSlowsRushedVoices() {
        assertEquals(0.8f, Voice.TULLY.engineSpeed(1.0f), 1e-6f)
        assertEquals(1.6f, Voice.TULLY.engineSpeed(2.0f), 1e-6f)
        assertEquals(1.0f, Voice.BINDI.engineSpeed(1.0f), 1e-6f)
    }
}
