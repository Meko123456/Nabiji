package io.github.meko123456.nabiji.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val Forest = Color(0xFF123B2A)
private val Mint = Color(0xFF8BE9A8)
private val Amber = Color(0xFFFFD166)
private val MintPale = Color(0xFFCFF3DB)
private val AmberPale = Color(0xFFFFE9B8)
private val AmberDeep = Color(0xFF4A3800)

// The ink each accent is written in. Naming an accent without naming its `on-` partner leaves
// the partner at the Material baseline, which is white: white on Amber is 1.4:1 and white on
// Mint is 1.5:1, so any label Material chose to put on either was simply not there. These are
// picked to clear WCAG AA several times over — Forest 12.5:1, Amber 9.2:1, Mint 8.7:1.
private val OnForest = Color(0xFFFFFFFF)
private val OnAmber = Color(0xFF3D2E00)
private val OnMint = Color(0xFF073B21)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = OnForest,
    primaryContainer = MintPale,
    onPrimaryContainer = OnMint,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = AmberPale,
    onSecondaryContainer = OnAmber,
    tertiary = Mint,
    onTertiary = OnMint,
    tertiaryContainer = MintPale,
    onTertiaryContainer = OnMint,
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = OnMint,
    primaryContainer = Forest,
    onPrimaryContainer = Mint,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = AmberDeep,
    onSecondaryContainer = AmberPale,
    tertiary = Mint,
    onTertiary = OnMint,
    tertiaryContainer = Forest,
    onTertiaryContainer = Mint,
)

/**
 * True when the colours actually in use are the dark ones.
 *
 * [isSystemInDarkTheme] answers a different question — what the system setting is — and the two
 * part company the moment the theme is handed a `darkTheme` of its own, which a preview or an
 * in-app theme setting does. Anything picking a colour by hand has to ask the scheme, not the
 * system, or it ends up drawing dark ink on a dark surface.
 */
val ColorScheme.isDark: Boolean
    get() = surface.luminance() < 0.5f

@Composable
fun NabijiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
