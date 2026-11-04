package app.sentinela

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.sentinela.ui.SentinelaRoot
import app.sentinela.ui.SentinelaViewModel
import app.sentinela.ui.theme.SentinelaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SentinelaTheme {
                val vm: SentinelaViewModel = viewModel()
                SentinelaRoot(vm)
            }
        }
    }
}
