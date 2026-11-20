package app.pausa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pausa.R

/**
 * Noche: black and one lavender accent in dark mode (white and a deeper violet in light mode, following the
 * phone). It is the brand's own palette, not the wallpaper's, so dynamic colour is left out on purpose.
 */
@Composable
fun PausaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PausaTypography,
        shapes = PausaShapes,
        content = content,
    )
}

/**
 * Nothing in Noche has a corner you could cut yourself on. The standard components (chips, text fields, dialogs,
 * menus) take their corners from here, so none of them falls back to a squarer default.
 */
private val PausaShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Space Grotesk is bundled with the app (Pausa has no internet to download fonts), as one variable font whose
 * weight is chosen per style. Light is for the big numerals, Medium for titles; nothing is bolder than that.
 */
@OptIn(ExperimentalTextApi::class)
private fun grotesk(weight: FontWeight) = Font(
    resId = R.font.space_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val SpaceGrotesk = FontFamily(
    grotesk(FontWeight.Light),
    grotesk(FontWeight.Normal),
    grotesk(FontWeight.Medium),
    grotesk(FontWeight.SemiBold),
)

/** The big numerals of Noche: an hour inside a ring, a countdown. Light and a little tight. */
val NumeralStyle = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Light,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.5).sp,
)

private val base = Typography()

private fun TextStyle.grotesk(weight: FontWeight? = null, size: Int? = null, line: Int? = null): TextStyle = copy(
    fontFamily = SpaceGrotesk,
    fontWeight = weight ?: fontWeight,
    fontSize = size?.sp ?: fontSize,
    lineHeight = line?.sp ?: lineHeight,
)

/** One family, a tight scale (12, 14, 16, 20, 24) and weight doing most of the work of hierarchy. */
private val PausaTypography = Typography(
    displayLarge = base.displayLarge.grotesk(FontWeight.Light),
    displayMedium = base.displayMedium.grotesk(FontWeight.Light),
    displaySmall = base.displaySmall.grotesk(FontWeight.Light),
    headlineLarge = base.headlineLarge.grotesk(FontWeight.Medium),
    headlineMedium = base.headlineMedium.grotesk(FontWeight.Medium, 28, 36),
    headlineSmall = base.headlineSmall.grotesk(FontWeight.Medium, 24, 32),
    titleLarge = base.titleLarge.grotesk(FontWeight.Medium, 20, 28),
    titleMedium = base.titleMedium.grotesk(FontWeight.Medium, 16, 24),
    titleSmall = base.titleSmall.grotesk(FontWeight.Medium, 14, 20),
    bodyLarge = base.bodyLarge.grotesk(FontWeight.Normal, 16, 24),
    bodyMedium = base.bodyMedium.grotesk(FontWeight.Normal, 14, 21),
    bodySmall = base.bodySmall.grotesk(FontWeight.Normal, 12, 18),
    labelLarge = base.labelLarge.grotesk(FontWeight.Medium, 14, 20),
    labelMedium = base.labelMedium.grotesk(FontWeight.Medium, 12, 16),
    labelSmall = base.labelSmall.grotesk(FontWeight.Medium),
)
