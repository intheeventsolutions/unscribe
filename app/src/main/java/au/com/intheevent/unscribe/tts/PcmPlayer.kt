package au.com.intheevent.unscribe.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays float PCM chunks back to back. [play] blocks until the chunk is buffered,
 * so call it off the main thread; [stop] from another thread unblocks it.
 */
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

    @Volatile
    private var stopped = false

    /** Total frames handed to the track since creation. */
    @Volatile
    var framesWritten = 0L
        private set

    /** Frames actually played so far; compare with [framesWritten] to see what's audible. */
    val framesPlayed: Long
        get() = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    fun play(samples: FloatArray) {
        if (stopped) return
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written > 0) framesWritten += written
    }

    fun stop() {
        stopped = true
        track.pause()
        track.flush()
    }

    fun release() = track.release()
}
