package net.dom53.inkita.ui.reader

import androidx.annotation.StringRes
import net.dom53.inkita.R
import net.dom53.inkita.core.storage.ReaderThemeMode

data class ReaderFontOption(
    val id: String,
    val label: String,
    val cssFamily: String,
    val assetPath: String? = null,
    val isSerif: Boolean = true,
)

@Suppress("ktlint:standard:max-line-length")
val readerFontOptions =
    listOf(
        ReaderFontOption("literata", "Literata", "Literata", "file:///android_asset/fonts/Literata/Literata-VariableFont_opsz,wght.ttf", isSerif = true),
        ReaderFontOption("noto_serif", "Noto Serif", "Noto Serif", "file:///android_asset/fonts/Noto_Serif/NotoSerif-VariableFont_wdth,wght.ttf", isSerif = true),
        ReaderFontOption("noto_sans", "Noto Sans", "Noto Sans", "file:///android_asset/fonts/Noto_Sans/NotoSans-VariableFont_wdth,wght.ttf", isSerif = false),
        ReaderFontOption("roboto", "Roboto", "Roboto", "file:///android_asset/fonts/Roboto/Roboto-VariableFont_wdth,wght.ttf", isSerif = false),
        ReaderFontOption(
            "roboto_serif",
            "Roboto Serif",
            "Roboto Serif",
            "file:///android_asset/fonts/Roboto_Serif/RobotoSerif-VariableFont_GRAD,opsz,wdth,wght.ttf",
            isSerif = true,
        ),
        ReaderFontOption("roboto_mono", "Roboto Mono", "Roboto Mono", "file:///android_asset/fonts/Roboto_Mono/RobotoMono-VariableFont_wght.ttf", isSerif = false),
        ReaderFontOption("open_sans", "Open Sans", "Open Sans", "file:///android_asset/fonts/Open_Sans/OpenSans-VariableFont_wdth,wght.ttf", isSerif = false),
        ReaderFontOption("nunito", "Nunito", "Nunito", "file:///android_asset/fonts/Nunito/Nunito-VariableFont_wght.ttf", isSerif = false),
        ReaderFontOption("bitter", "Bitter", "Bitter", "file:///android_asset/fonts/Bitter/Bitter-VariableFont_wght.ttf", isSerif = true),
        ReaderFontOption("ubuntu", "Ubuntu", "Ubuntu", "file:///android_asset/fonts/Ubuntu/Ubuntu-Regular.ttf", isSerif = false),
        ReaderFontOption("system_sans", "System Sans", "sans-serif", assetPath = null, isSerif = false),
        ReaderFontOption("system_serif", "System Serif", "serif", assetPath = null, isSerif = true),
    )

data class ReaderThemeOption(
    val mode: ReaderThemeMode,
    @StringRes val labelRes: Int,
)

val readerThemeOptions =
    listOf(
        ReaderThemeOption(ReaderThemeMode.Light, R.string.reader_theme_day),
        ReaderThemeOption(ReaderThemeMode.Dark, R.string.reader_theme_night),
        ReaderThemeOption(ReaderThemeMode.DarkHighContrast, R.string.reader_theme_night_high_contrast),
        ReaderThemeOption(ReaderThemeMode.Sepia, R.string.reader_theme_sepia),
        ReaderThemeOption(ReaderThemeMode.SepiaHighContrast, R.string.reader_theme_sepia_high_contrast),
        ReaderThemeOption(ReaderThemeMode.Gray, R.string.reader_theme_gray),
    )
