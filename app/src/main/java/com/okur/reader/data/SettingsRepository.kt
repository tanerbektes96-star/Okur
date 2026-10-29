package com.okur.reader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>, default: T): T =
    this[key]?.let { stored -> enumValues<T>().firstOrNull { e -> e.name == stored } } ?: default

class SettingsRepository(private val context: Context) {

    private object K {
        // Okuma
        val theme = stringPreferencesKey("r_theme")
        val fontSize = intPreferencesKey("r_font_size")
        val font = stringPreferencesKey("r_font")
        val lineHeight = floatPreferencesKey("r_line_height")
        val margin = intPreferencesKey("r_margin")
        val justify = booleanPreferencesKey("r_justify")
        val hyphenate = booleanPreferencesKey("r_hyphenate")
        val pageMode = stringPreferencesKey("r_page_mode")
        val pdfPageMode = stringPreferencesKey("r_pdf_page_mode")
        val dualPage = booleanPreferencesKey("r_dual_page")
        val brightness = floatPreferencesKey("r_brightness")
        val volumeKeys = booleanPreferencesKey("r_volume_keys")
        val tapZones = booleanPreferencesKey("r_tap_zones")
        val keepScreenOn = booleanPreferencesKey("r_keep_screen_on")
        val orientation = stringPreferencesKey("r_orientation")
        val pdfFollowTheme = booleanPreferencesKey("r_pdf_follow_theme")
        val ttsRate = floatPreferencesKey("r_tts_rate")
        val ttsPitch = floatPreferencesKey("r_tts_pitch")
        val ttsVoice = stringPreferencesKey("r_tts_voice")
        val pageTurn = stringPreferencesKey("r_page_turn")
        val tapGuideShown = booleanPreferencesKey("r_tap_guide_shown")

        // Göz bakımı
        val filterMode = stringPreferencesKey("e_filter_mode")
        val filterStrength = floatPreferencesKey("e_filter_strength")
        val scheduleStart = intPreferencesKey("e_schedule_start")
        val scheduleEnd = intPreferencesKey("e_schedule_end")
        val extraDim = floatPreferencesKey("e_extra_dim")
        val breakReminder = booleanPreferencesKey("e_break_reminder")
        val breakInterval = intPreferencesKey("e_break_interval")
        val smartEye = booleanPreferencesKey("e_smart_eye")
        val smartStay = booleanPreferencesKey("e_smart_stay")
        val distanceAlert = booleanPreferencesKey("e_distance_alert")
        val blinkReminder = booleanPreferencesKey("e_blink_reminder")
        val pauseWhenAway = booleanPreferencesKey("e_pause_when_away")
        val autoNightTheme = booleanPreferencesKey("e_auto_night_theme")

        // Uygulama
        val themeMode = stringPreferencesKey("a_theme_mode")
        val dynamicColor = booleanPreferencesKey("a_dynamic_color")
        val gridView = booleanPreferencesKey("a_grid_view")
        val sort = stringPreferencesKey("a_sort")
        val dailyGoal = intPreferencesKey("a_daily_goal")
    }

    val readerPrefs: Flow<ReaderPrefs> = context.dataStore.data.map { it.toReader() }
    val eyePrefs: Flow<EyePrefs> = context.dataStore.data.map { it.toEye() }
    val appPrefs: Flow<AppPrefs> = context.dataStore.data.map { it.toApp() }

    private fun Preferences.toReader(): ReaderPrefs {
        val d = ReaderPrefs()
        return ReaderPrefs(
            theme = enum(K.theme, d.theme),
            fontSize = this[K.fontSize] ?: d.fontSize,
            font = enum(K.font, d.font),
            lineHeight = this[K.lineHeight] ?: d.lineHeight,
            margin = this[K.margin] ?: d.margin,
            justify = this[K.justify] ?: d.justify,
            hyphenate = this[K.hyphenate] ?: d.hyphenate,
            pageMode = enum(K.pageMode, d.pageMode),
            pdfPageMode = enum(K.pdfPageMode, d.pdfPageMode),
            dualPage = this[K.dualPage] ?: d.dualPage,
            brightness = this[K.brightness] ?: d.brightness,
            volumeKeys = this[K.volumeKeys] ?: d.volumeKeys,
            tapZones = this[K.tapZones] ?: d.tapZones,
            keepScreenOn = this[K.keepScreenOn] ?: d.keepScreenOn,
            orientation = enum(K.orientation, d.orientation),
            pdfFollowTheme = this[K.pdfFollowTheme] ?: d.pdfFollowTheme,
            ttsRate = this[K.ttsRate] ?: d.ttsRate,
            ttsPitch = this[K.ttsPitch] ?: d.ttsPitch,
            ttsVoice = this[K.ttsVoice] ?: d.ttsVoice,
            pageTurn = enum(K.pageTurn, d.pageTurn),
            tapGuideShown = this[K.tapGuideShown] ?: d.tapGuideShown,
        )
    }

    private fun Preferences.toEye(): EyePrefs {
        val d = EyePrefs()
        return EyePrefs(
            filterMode = enum(K.filterMode, d.filterMode),
            filterStrength = this[K.filterStrength] ?: d.filterStrength,
            scheduleStart = this[K.scheduleStart] ?: d.scheduleStart,
            scheduleEnd = this[K.scheduleEnd] ?: d.scheduleEnd,
            extraDim = this[K.extraDim] ?: d.extraDim,
            breakReminder = this[K.breakReminder] ?: d.breakReminder,
            breakIntervalMin = this[K.breakInterval] ?: d.breakIntervalMin,
            smartEye = this[K.smartEye] ?: d.smartEye,
            smartStay = this[K.smartStay] ?: d.smartStay,
            distanceAlert = this[K.distanceAlert] ?: d.distanceAlert,
            blinkReminder = this[K.blinkReminder] ?: d.blinkReminder,
            pauseWhenAway = this[K.pauseWhenAway] ?: d.pauseWhenAway,
            autoNightTheme = this[K.autoNightTheme] ?: d.autoNightTheme,
        )
    }

    private fun Preferences.toApp(): AppPrefs {
        val d = AppPrefs()
        return AppPrefs(
            themeMode = enum(K.themeMode, d.themeMode),
            dynamicColor = this[K.dynamicColor] ?: d.dynamicColor,
            gridView = this[K.gridView] ?: d.gridView,
            sort = enum(K.sort, d.sort),
            dailyGoalMin = this[K.dailyGoal] ?: d.dailyGoalMin,
        )
    }

    suspend fun updateReader(transform: (ReaderPrefs) -> ReaderPrefs) {
        context.dataStore.edit { p ->
            val n = transform(p.toReader())
            p[K.theme] = n.theme.name
            p[K.fontSize] = n.fontSize
            p[K.font] = n.font.name
            p[K.lineHeight] = n.lineHeight
            p[K.margin] = n.margin
            p[K.justify] = n.justify
            p[K.hyphenate] = n.hyphenate
            p[K.pageMode] = n.pageMode.name
            p[K.pdfPageMode] = n.pdfPageMode.name
            p[K.dualPage] = n.dualPage
            p[K.brightness] = n.brightness
            p[K.volumeKeys] = n.volumeKeys
            p[K.tapZones] = n.tapZones
            p[K.keepScreenOn] = n.keepScreenOn
            p[K.orientation] = n.orientation.name
            p[K.pdfFollowTheme] = n.pdfFollowTheme
            p[K.ttsRate] = n.ttsRate
            p[K.ttsPitch] = n.ttsPitch
            p[K.ttsVoice] = n.ttsVoice
            p[K.pageTurn] = n.pageTurn.name
            p[K.tapGuideShown] = n.tapGuideShown
        }
    }

    suspend fun updateEye(transform: (EyePrefs) -> EyePrefs) {
        context.dataStore.edit { p ->
            val n = transform(p.toEye())
            p[K.filterMode] = n.filterMode.name
            p[K.filterStrength] = n.filterStrength
            p[K.scheduleStart] = n.scheduleStart
            p[K.scheduleEnd] = n.scheduleEnd
            p[K.extraDim] = n.extraDim
            p[K.breakReminder] = n.breakReminder
            p[K.breakInterval] = n.breakIntervalMin
            p[K.smartEye] = n.smartEye
            p[K.smartStay] = n.smartStay
            p[K.distanceAlert] = n.distanceAlert
            p[K.blinkReminder] = n.blinkReminder
            p[K.pauseWhenAway] = n.pauseWhenAway
            p[K.autoNightTheme] = n.autoNightTheme
        }
    }

    suspend fun updateApp(transform: (AppPrefs) -> AppPrefs) {
        context.dataStore.edit { p ->
            val n = transform(p.toApp())
            p[K.themeMode] = n.themeMode.name
            p[K.dynamicColor] = n.dynamicColor
            p[K.gridView] = n.gridView
            p[K.sort] = n.sort.name
            p[K.dailyGoal] = n.dailyGoalMin
        }
    }
}
