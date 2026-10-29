package au.com.intheevent.unscribe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import au.com.intheevent.unscribe.ui.UnscribeTheme
import au.com.intheevent.unscribe.ui.VoiceTestScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnscribeTheme {
                VoiceTestScreen()
            }
        }
    }
}
