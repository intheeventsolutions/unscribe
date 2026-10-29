package au.com.intheevent.unscribe.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays float PCM chunks back to back. Blocking: call [play] off the main thread. */
class PcmPlayer(sampleRate: Int) {
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 4,
        )
        .build()

    fun play(samples: FloatArray) {
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
    }

    fun stop() {
        track.pause()
        track.flush()
    }

    fun release() = track.release()
}
