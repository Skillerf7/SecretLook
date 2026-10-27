package app.secretlock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val Bg = Color(0xFF0D0F12)
    val Surface = Color(0xFF16191E)
    val Surface2 = Color(0xFF1E2229)
    val Accent = Color(0xFF8AA4FF)
    val Ok = Color(0xFF6FCF97)
    val Warn = Color(0xFFF2C94C)
    val Bad = Color(0xFFEB5757)
    val Text = Color(0xFFE6E8EB)
    val Dim = Color(0xFF9AA0A9)
}

@Composable
fun SecretLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Accent, onPrimary = Pal.Bg,
            background = Pal.Bg, onBackground = Pal.Text,
            surface = Pal.Surface, onSurface = Pal.Text,
            surfaceVariant = Pal.Surface2, onSurfaceVariant = Pal.Dim,
            secondaryContainer = Color(0xFF2A3350), onSecondaryContainer = Pal.Text,
            error = Pal.Bad,
        ),
        content = content,
    )
}
