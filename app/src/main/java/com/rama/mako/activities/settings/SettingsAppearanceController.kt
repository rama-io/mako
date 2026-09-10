package com.rama.mako.activities.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import com.rama.bohio.widgets.WdCheckbox
import com.rama.mako.R
import com.rama.bohio.R as BohioR
import com.rama.mako.activities.MainActivity
import com.rama.mako.activities.SettingsActivity
import com.rama.bohio.managers.FontManager
import com.rama.mako.managers.PrefsManager
import com.rama.bohio.managers.ThemeManager
import com.rama.bohio.objects.PrefFontStyle
import com.rama.bohio.objects.PrefKeys
import com.rama.bohio.objects.PrefTheme
import com.rama.bohio.objects.Themes
import java.io.File
import java.io.FileOutputStream
import com.rama.bohio.widgets.WdColorPicker
import com.rama.bohio.widgets.WdRange

class SettingsAppearanceController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        setupFontStyle()
        setupTemperatureFormat()
        setupTheme()
        setupCustomTheme()
        setupBackgroundMode()
        setupScreenOpacity()
        setupUiScale()
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == activity.FONT_PICK_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val savedPath = copyFontToInternalStorage(uri)
            if (savedPath != null) {
                FontManager.clearCustomCache()
                prefs.setCustomFontPath(savedPath)
                prefs.setFontStyle(PrefFontStyle.CUSTOM)
                updateCustomFontLabel()
                activity.refreshFont()
            }
        }
    }

    private fun setupFontStyle() {
        val group = activity.findViewById<RadioGroup>(R.id.font_style_group)
        val customContainer = activity.findViewById<View>(R.id.custom_font_container)

        when (prefs.getFontStyle()) {
            PrefFontStyle.JERSEY_25 -> group.check(R.id.font_jersey)
            PrefFontStyle.CUSTOM -> group.check(R.id.font_custom)
            else -> group.check(R.id.font_default)
        }

        customContainer.visibility =
            if (prefs.getFontStyle() == PrefFontStyle.CUSTOM) View.VISIBLE else View.GONE

        group.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.font_jersey -> {
                    customContainer.visibility = View.GONE
                    prefs.setFontStyle(PrefFontStyle.JERSEY_25)
                    activity.refreshFont()
                }

                R.id.font_default -> {
                    customContainer.visibility = View.GONE
                    prefs.setFontStyle(PrefFontStyle.DEFAULT)
                    activity.refreshFont()
                }

                R.id.font_custom -> {
                    customContainer.visibility = View.VISIBLE
                    prefs.setFontStyle(PrefFontStyle.CUSTOM)
                    activity.refreshFont()
                }
            }
        }

        activity.findViewById<View>(R.id.font_custom_pick_btn).setOnClickListener {
            openFontPicker()
        }

        updateCustomFontLabel()
    }

    private fun openFontPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "font/ttf", "font/otf", "application/x-font-ttf",
                    "application/x-font-otf", "application/octet-stream"
                )
            )
        }
        activity.startActivityForResult(intent, activity.FONT_PICK_REQUEST)
    }

    private fun copyFontToInternalStorage(uri: Uri): String? {
        return runCatching {
            val inputStream = activity.contentResolver.openInputStream(uri) ?: return null
            val dir = File(activity.filesDir, "fonts").also { it.mkdirs() }
            // Preserve extension (.ttf / .otf) for Typeface.createFromFile
            val ext = activity.contentResolver.getType(uri)
                ?.let { if (it.contains("otf")) "otf" else "ttf" } ?: "ttf"
            val dest = File(dir, "custom_font.$ext")
            FileOutputStream(dest).use { out -> inputStream.copyTo(out) }
            dest.absolutePath
        }.getOrNull()
    }

    private fun updateCustomFontLabel() {
        val label = activity.findViewById<TextView>(R.id.font_custom_name_label)
        val path = prefs.getCustomFontPath()
        label.text =
            if (path.isNotBlank()) File(path).name else activity.getString(BohioR.string.filepicker_font_custom_none)
    }

    private fun setupTemperatureFormat() {
        val group = activity.findViewById<RadioGroup>(R.id.temperature_format_group)

        when (prefs.getTemperatureFormat()) {
            PrefsManager.TemperatureFormat.CELSIUS -> group.check(R.id.temperature_celsius)
            PrefsManager.TemperatureFormat.FAHRENHEIT -> group.check(R.id.temperature_fahrenheit)
            else -> group.check(R.id.temperature_system)
        }

        group.setOnCheckedChangeListener { _, id ->
            when (id) {
                R.id.temperature_celsius -> prefs.setTemperatureFormat(PrefsManager.TemperatureFormat.CELSIUS)
                R.id.temperature_fahrenheit -> prefs.setTemperatureFormat(PrefsManager.TemperatureFormat.FAHRENHEIT)
                else -> prefs.setTemperatureFormat(PrefsManager.TemperatureFormat.DEFAULT)
            }
        }
    }

    private fun setupTheme() {
        val group = activity.findViewById<RadioGroup>(R.id.themes_group)
        val form = activity.findViewById<View>(R.id.themes_form)

        // Show form only if custom is already selected
        form.visibility =
            if (prefs.getTheme() == PrefTheme.CUSTOM) View.VISIBLE else View.GONE

        when (prefs.getTheme()) {
            PrefTheme.RAMA -> group.check(R.id.theme_rama)
            PrefTheme.MAKO -> group.check(R.id.theme_mako)
            PrefTheme.TEYIN -> group.check(R.id.theme_teyin)
            PrefTheme.CATPPUCCIN_MOCHA -> group.check(R.id.theme_catppuccin_mocha)
            PrefTheme.CATPPUCCIN_LATTE -> group.check(R.id.theme_catppuccin_latte)
            PrefTheme.DRACULA -> group.check(R.id.theme_dracula)
            PrefTheme.MELANGE -> group.check(R.id.theme_melange)
            PrefTheme.TOKYO_NIGHT -> group.check(R.id.theme_tokyo_night)
            PrefTheme.MONO_DARK -> group.check(R.id.theme_mono_dark)
            PrefTheme.MONO_LIGHT -> group.check(R.id.theme_mono_light)
            PrefTheme.CUSTOM -> group.check(R.id.theme_custom)
            else -> group.check(R.id.theme_teyin)
        }

        group.setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                R.id.theme_mako -> PrefTheme.MAKO
                R.id.theme_rama -> PrefTheme.RAMA
                R.id.theme_teyin -> PrefTheme.TEYIN
                R.id.theme_catppuccin_mocha -> PrefTheme.CATPPUCCIN_MOCHA
                R.id.theme_catppuccin_latte -> PrefTheme.CATPPUCCIN_LATTE
                R.id.theme_dracula -> PrefTheme.DRACULA
                R.id.theme_melange -> PrefTheme.MELANGE
                R.id.theme_tokyo_night -> PrefTheme.TOKYO_NIGHT
                R.id.theme_mono_dark -> PrefTheme.MONO_DARK
                R.id.theme_mono_light -> PrefTheme.MONO_LIGHT
                R.id.theme_custom -> PrefTheme.CUSTOM
                else -> PrefTheme.TEYIN
            }

            // Show/hide the custom form
            form.visibility = if (theme == PrefTheme.CUSTOM) View.VISIBLE else View.GONE

            if (theme != PrefTheme.CUSTOM) {
                // For built-in themes: save and apply immediately
                prefs.setTheme(theme)
                activity.recreate()
            } else {
                // For custom: save selection immediately so it persists navigation,
                // then populate fields with current custom palette (or MAKO defaults)
                val previousTheme = prefs.getTheme()
                prefs.setTheme(PrefTheme.CUSTOM)
                populateCustomFields(ThemeManager.paletteFor(previousTheme, activity))
            }
        }
    }

    private fun populateCustomFields(palette: Themes.Palette) {
        activity.findViewById<WdColorPicker>(R.id.foreground).setColor(palette.foreground)
        activity.findViewById<WdColorPicker>(R.id.collapsible_header)
            .setColor(palette.collapsible_header)
        activity.findViewById<WdColorPicker>(R.id.h1).setColor(palette.h1)
        activity.findViewById<WdColorPicker>(R.id.icons).setColor(palette.icon)
        activity.findViewById<WdColorPicker>(R.id.accent).setColor(palette.accent_1)
        activity.findViewById<WdColorPicker>(R.id.bg_2).setColor(palette.bg_2)
        activity.findViewById<WdColorPicker>(R.id.bg_3).setColor(palette.bg_3)
        activity.findViewById<WdColorPicker>(R.id.bg_1).setColor(palette.bg_1)
        activity.findViewById<WdColorPicker>(R.id.input).setColor(palette.input)
        activity.findViewById<WdColorPicker>(R.id.btn_1).setColor(palette.button_1)
        activity.findViewById<WdColorPicker>(R.id.btn_2).setColor(palette.button_2)
        activity.findViewById<WdColorPicker>(R.id.danger).setColor(palette.danger)
    }

    private fun setupCustomTheme() {
        // Populate fields with current theme palette on open
        val currentPalette = ThemeManager.paletteFor(prefs.getTheme(), activity)
        populateCustomFields(currentPalette)

        val saveButton = activity.findViewById<View>(R.id.save_custom_theme)
        saveButton.setOnClickListener {
            val fields = mapOf(
                PrefKeys.APP_THEME_FOREGROUND to activity.findViewById<WdColorPicker>(R.id.foreground),
                PrefKeys.APP_THEME_COLLAPSIBLE_HEADER to activity.findViewById<WdColorPicker>(
                    R.id.collapsible_header
                ),
                PrefKeys.APP_THEME_H1 to activity.findViewById<WdColorPicker>(R.id.h1),
                PrefKeys.APP_THEME_ICON to activity.findViewById<WdColorPicker>(R.id.icons),
                PrefKeys.APP_THEME_ACCENT_1 to activity.findViewById<WdColorPicker>(R.id.accent),
                PrefKeys.APP_THEME_BG_1 to activity.findViewById<WdColorPicker>(R.id.bg_1),
                PrefKeys.APP_THEME_BG_2 to activity.findViewById<WdColorPicker>(R.id.bg_2),
                PrefKeys.APP_THEME_BG_3 to activity.findViewById<WdColorPicker>(R.id.bg_3),
                PrefKeys.APP_THEME_INPUT to activity.findViewById<WdColorPicker>(R.id.input),
                PrefKeys.APP_THEME_BUTTON_1 to activity.findViewById<WdColorPicker>(R.id.btn_1),
                PrefKeys.APP_THEME_BUTTON_2 to activity.findViewById<WdColorPicker>(R.id.btn_2),
                PrefKeys.APP_THEME_DANGER to activity.findViewById<WdColorPicker>(R.id.danger),
            )

            fields.forEach { (key, colorPicker) ->
                val color = colorPicker.getColor()
                prefs.setCustomThemeColor(key, color)
            }
            prefs.setTheme(PrefTheme.CUSTOM)
            activity.recreate()

        }
    }

    private fun setupBackgroundMode() {
        val checkbox = activity.findViewById<WdCheckbox>(R.id.home_background_wallpaper)
        val opacityContainer = activity.findViewById<View>(R.id.screen_opacity_container)

        val isWallpaper = prefs.getHomeBackgroundMode() == PrefsManager.BackgroundMode.WALLPAPER
        checkbox.setChecked(isWallpaper)
        opacityContainer.visibility = if (isWallpaper) View.VISIBLE else View.GONE

        checkbox.setOnCheckedChangeListener { isChecked ->
            opacityContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            val mode =
                if (isChecked) PrefsManager.BackgroundMode.WALLPAPER else PrefsManager.BackgroundMode.DEFAULT
            prefs.setHomeBackgroundMode(mode)
            activity.applySettingsBackground()
        }
    }

    private fun setupScreenOpacity() {
        val range = activity.findViewById<WdRange>(R.id.screen_opacity)

        val steps =
            activity.resources.getStringArray(R.array.wallpaper_screen_opacity_strength).toList()
        val savedStrength = prefs.getHomeBackgroundScreenOpacityStrength()
        val matchIndex = steps.indexOfFirst { it.toIntOrNull() == savedStrength }

        if (matchIndex >= 0) {
            range.post {
                val container = range.findViewById<android.widget.LinearLayout>(BohioR.id.container)
                (container?.getChildAt(matchIndex) as? android.widget.Button)?.performClick()
            }
        }

        range.onValueChanged = { value ->
            val strength = value.toIntOrNull()
            if (strength != null && strength != prefs.getHomeBackgroundScreenOpacityStrength()) {
                prefs.setHomeBackgroundScreenOpacityStrength(strength)
                activity.applySettingsBackground(force = true)
            }
        }
    }

    private fun setupUiScale() {
        val range = activity.findViewById<WdRange>(R.id.zoom)

        val savedScale = prefs.getUiScale()

        range.onValueChanged = { value ->
            val scale = value.toFloatOrNull() ?: 1f
            if (scale != prefs.getUiScale()) {
                prefs.setUiScale(scale)
                activity.recreate()
            }
        }

        val steps = activity.resources.getStringArray(BohioR.array.ui_scale_steps).toList()
        val matchIndex = steps.indexOfFirst { it.toFloatOrNull() == savedScale }
        if (matchIndex >= 0) {
            range.post {
                val container = range.findViewById<android.widget.LinearLayout>(BohioR.id.container)
                (container?.getChildAt(matchIndex) as? android.widget.Button)?.performClick()
            }
        }
    }
}
