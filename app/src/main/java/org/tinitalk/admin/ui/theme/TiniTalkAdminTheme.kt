package org.tinitalk.admin.ui.theme

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.tinitalk.admin.i18n.AppLanguage
import androidx.compose.ui.graphics.Color

val BrandGold = Color(0xFFD4AF37)
val BrandBackground = Color(0xFF111111)
val AccessVerifiedGreen = Color(0xFF76D39B)

private val AdminColors = darkColorScheme(
    primary = BrandGold,
    onPrimary = Color(0xFF211B08),
    primaryContainer = Color(0xFF3B3216),
    onPrimaryContainer = Color(0xFFF8E7A4),
    secondary = Color(0xFFC8B978),
    onSecondary = Color(0xFF211D0D),
    background = BrandBackground,
    surface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFF292929),
    onBackground = Color(0xFFF4F4F4),
    onSurface = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFFA2A2A2),
    outline = Color(0xFF545454),
    outlineVariant = Color(0xFF404040),
    error = Color(0xFFFFB3B4),
    errorContainer = Color(0xFF5B2027),
    onErrorContainer = Color(0xFFFFDADB),
)

@Composable
fun TiniTalkAdminTheme(content: @Composable () -> Unit) {
    // Resolve the Activity owner before replacing its context with localized resources.
    val activityResultOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
    val base = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = AppLanguage.locale
    val localized = remember(base, configuration, locale) { AppLanguage.context(base) }
    val direction = if (localized.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalActivityResultRegistryOwner provides activityResultOwner,
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalLayoutDirection provides direction,
    ) {
        MaterialTheme(colorScheme = AdminColors, content = content)
    }
}
