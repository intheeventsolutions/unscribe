package au.com.intheevent.unscribe.tts

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * On-device Piper voice via sherpa-onnx. The model and tokens are read straight
 * from assets; espeak-ng needs real files, so its data is copied out once.
 */
class SherpaPiperEngine(private val context: Context) : TtsEngine {
    private var tts: OfflineTts? = null

    override val sampleRate: Int
        get() = tts?.sampleRate() ?: SAMPLE_RATE

    override fun load() {
        if (tts != null) return
        val dataDir = copyEspeakData()
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$ASSET_DIR/en_AU-librivox-medium.onnx",
                    tokens = "$ASSET_DIR/tokens.txt",
                    dataDir = dataDir.absolutePath,
                ),
                numThreads = 4,
            ),
        )
        tts = OfflineTts(assetManager = context.assets, config = config)
    }

    override fun synthesize(text: String, voice: Voice, speed: Float): FloatArray {
        val engine = checkNotNull(tts) { "load() first" }
        return engine.generate(text, sid = voice.speakerId, speed = voice.engineSpeed(speed)).samples
    }

    override fun release() {
        tts?.release()
        tts = null
    }

    private fun copyEspeakData(): File {
        val target = File(context.filesDir, "espeak-ng-data")
        val marker = File(target, ".version-$ESPEAK_DATA_VERSION")
        if (marker.exists()) return target
        target.deleteRecursively()
        copyAssetDir(context.assets, "$ASSET_DIR/espeak-ng-data", target)
        marker.createNewFile()
        return target
    }

    private fun copyAssetDir(assets: AssetManager, path: String, dest: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        for (child in children) copyAssetDir(assets, "$path/$child", File(dest, child))
    }

    private companion object {
        const val ASSET_DIR = "voice"
        const val SAMPLE_RATE = 22050
        // Bump when the bundled espeak-ng-data changes so devices recopy it.
        const val ESPEAK_DATA_VERSION = 1
    }
}
