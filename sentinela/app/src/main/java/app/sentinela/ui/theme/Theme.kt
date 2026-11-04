package app.sentinela.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Teal = Color(0xFF1E6F6B)
private val TealDark = Color(0xFF8FD8D2)
private val Amber = Color(0xFFB15C00)
private val Red = Color(0xFFB3261E)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = Amber,
    error = Red,
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    secondary = Color(0xFFFFB871),
    error = Color(0xFFF2B8B5),
)

@Composable
fun SentinelaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
