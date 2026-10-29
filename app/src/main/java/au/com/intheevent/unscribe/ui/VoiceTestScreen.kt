package au.com.intheevent.unscribe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import au.com.intheevent.unscribe.tts.PcmPlayer
import au.com.intheevent.unscribe.tts.SherpaPiperEngine
import au.com.intheevent.unscribe.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

private const val SAMPLE_TEXT =
    "It was a bright cold day in April, and the clocks were striking thirteen. " +
        "Mrs. Wilson wandered down to the beach, wondering whether she should have stayed in bed. " +
        "The kettle was on, the dog was asleep, and the whole street smelled of rain."

// Temporary screen (phase 2) for judging voice quality and speed on the phone.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceTestScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { SherpaPiperEngine(context.applicationContext) }
    val player = remember { PcmPlayer(engine.sampleRate) }

    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Loading voice…") }
    var text by remember { mutableStateOf(SAMPLE_TEXT) }
    var voice by remember { mutableStateOf(Voice.DEFAULT) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        withContext(Dispatchers.IO) { engine.load() }
        loaded = true
        status = "Voice loaded in ${"%.1f".format((System.nanoTime() - start) / 1e9)}s"
    }
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            player.release()
            engine.release()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Voice test", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Text("Voice", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Voice.entries.forEach { v ->
                    FilterChip(selected = v == voice, onClick = { voice = v }, label = { Text(v.displayName) })
                }
            }
            Text("Speed ${"%.2f".format(speed)}×", style = MaterialTheme.typography.titleSmall)
            Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.75f..2.0f, steps = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = loaded && job?.isActive != true,
                    onClick = {
                        job = scope.launch(Dispatchers.Default) {
                            speak(engine, player, text, voice, speed) { status = it }
                        }
                    },
                ) { Text("Speak") }
                OutlinedButton(
                    enabled = job?.isActive == true,
                    onClick = {
                        job?.cancel()
                        player.stop()
                        status = "Stopped"
                    },
                ) { Text("Stop") }
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun speak(
    engine: SherpaPiperEngine,
    player: PcmPlayer,
    text: String,
    voice: Voice,
    speed: Float,
    report: (String) -> Unit,
) {
    // Naive split; the real sentence splitter arrives with the reader.
    val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
    var synthSeconds = 0.0
    var audioSeconds = 0.0
    for (sentence in sentences) {
        coroutineContext.ensureActive()
        val start = System.nanoTime()
        val samples = engine.synthesize(sentence, voice, speed)
        synthSeconds += (System.nanoTime() - start) / 1e9
        audioSeconds += samples.size.toDouble() / engine.sampleRate
        report("Synthesised ${"%.1f".format(audioSeconds)}s of audio in ${"%.1f".format(synthSeconds)}s " +
            "(${"%.2f".format(synthSeconds / audioSeconds)}× real time; under 1 is good)")
        coroutineContext.ensureActive()
        player.play(samples)
    }
}
