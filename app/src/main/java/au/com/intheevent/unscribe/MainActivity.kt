package au.com.intheevent.unscribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import au.com.intheevent.unscribe.data.Library
import au.com.intheevent.unscribe.ui.LibraryScreen
import au.com.intheevent.unscribe.ui.ReaderScreen
import au.com.intheevent.unscribe.ui.UnscribeTheme
import au.com.intheevent.unscribe.ui.VoiceTestScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val library = Library(applicationContext)
        setContent {
            UnscribeTheme {
                var openBookId by rememberSaveable { mutableStateOf<Long?>(null) }
                var showVoiceTest by rememberSaveable { mutableStateOf(false) }

                val bookId = openBookId
                when {
                    showVoiceTest -> {
                        BackHandler { showVoiceTest = false }
                        VoiceTestScreen()
                    }
                    bookId != null -> {
                        BackHandler { openBookId = null }
                        ReaderScreen(library, bookId, onBack = { openBookId = null })
                    }
                    else -> LibraryScreen(
                        library,
                        onOpen = { openBookId = it.id },
                        onSettings = { showVoiceTest = true },
                    )
                }
            }
        }
    }
}
