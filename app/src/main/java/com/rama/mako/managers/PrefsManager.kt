package com.rama.mako.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.UserHandle
import com.rama.bohio.util.IdUtils
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rama.bohio.objects.PrefKeys
import com.rama.bohio.objects.PrefTheme
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

class PrefsManager private constructor(context: Context) : BohioPrefsManager(context) {
    override val defaultTheme: String = PrefTheme.RAMA

    // Local preference keys
    object FileKeys {
        const val APPS_SEARCH = "apps:search"
        const val APPS_SEARCH_ALWAYS_VISIBLE = "apps:search:always_visible"
        const val APPS_SEARCH_INCLUDE_HIDDEN_GROUPS = "apps:search:include_hidden_groups"
        const val APPS_PROFILE_INDICATOR = "apps:profile_indicator"

        const val APPS_ICONS = "apps:icons"
        const val APPS_ICON_SOURCE = "apps:icon_source"
        const val APPS_ICON_PACK_PACKAGE = "apps:icon_pack_package"
        const val APPS_ICONS_OPEN_SETTINGS = "apps:icons:open_settings"
        const val APPS_MULTI_COLUMN = "apps:multi_column"
        const val APPS_SHOW_API_INDICATORS = "apps:show_api_indicators"
        const val APPS_SHOW_SIZE = "apps:show_size"
        const val APPS_SHOW_SHORTCUTS = "apps:show_shortcuts"
        const val APPS_RTL_ROW = "apps:rtl_row"
        const val APPS_FONT_SIZE = "apps:font_size"
        const val APPS_ROW_SPACING = "apps:row_spacing"

        const val HOME_BACKGROUND_MODE = "home:background_mode"
        const val HOME_BACKGROUND_MODE_SCREEN_OPACITY_STRENGTH =
            "home:background_mode:screen_opacity_strength"
        const val GROUPS_IDS = "groups:ids"
        const val GROUPS_HEADERS = "groups:headers"
        const val GROUPS_COLLAPSIBLE = "groups:collapsible"
        const val GROUPS_COLLAPSE_ON_HOME_FOCUS = "groups:collapse_on_home_focus"
        const val GROUPS_AUTO_COLLAPSE = "auto_collapse_groups"

        @Deprecated("Migrated into DATE_FORMAT (see MIGRATION_DATE_FORMAT_RADIO)")
        const val DATE_VISIBLE = "date:visible"
        const val DATE_FORMAT = "date:format"
        const val DATE_YEAR_DAY = "date:year_day"
        const val DATE_YEAR_WEEK = "date:year_week"
        const val BATTERY_VISIBLE = "battery:visible"
        const val BATTERY_TEMPERATURE = "battery:temperature"
        const val TEMPERATURE_FORMAT = "temperature:format"
        const val BATTERY_CHARGE_STATUS = "battery:charge_status"
        const val CLOCK_FORMAT = "clock:format"
        const val CLOCK_APP = "clock:app"
        const val DATE_APP = "date:app"
        const val MIGRATION_ICON_SOURCE_RADIO = "migration:icon_source_radio"
        const val MIGRATION_DATE_FORMAT_RADIO = "migration:date_format_radio"

        const val SECURITY_KEYPAD_VISIBLE = "security:keypad:visible"
        const val SECURITY_KEYPAD_RANDOMIZED = "security:keypad:randomized"
        const val SECURITY_PIN = "security:pin"

        fun appKey(pkg: String, userHandle: UserHandle): String {
            val userId = userHandle.hashCode()
            return if (userId == 0) "app:$pkg" else "app:$pkg:profile_$userId"
        }

        fun appKey(app: AppsProvider.AppEntry): String {
            val base = appKey(app.packageName, app.userHandle)
            return if (app is AppsProvider.ShortcutEntry) {
                "$base:shortcut_${app.shortcutId}"
            } else base
        }

        fun isShortcutAppKey(key: String): Boolean =
            ":shortcut_" in key

        fun APP_GROUP_ID(pkg: String, userHandle: UserHandle) =
            "${appKey(pkg, userHandle)}:group_id"

        fun APP_GROUP_ID(app: AppsProvider.AppEntry) =
            "${appKey(app)}:group_id"

        fun APP_CUSTOM_LABEL(pkg: String, userHandle: UserHandle) =
            "${appKey(pkg, userHandle)}:custom_label"

        fun APP_CUSTOM_LABEL(app: AppsProvider.AppEntry) =
            "${appKey(app)}:custom_label"

        fun GROUP_LABEL(id: String) = "group:$id:label"
        fun GROUP_VISIBLE(id: String) = "group:$id:visible"
        fun GROUP_EXPANDED(id: String) = "group:$id:expanded"
        fun GROUP_ORDER(id: String) = "group:$id:order"
        fun GROUP_KEEP_EXPANDED(id: String) = "group:$id:keep_expanded"


    }

    object UI {
        const val SEPARATOR = "------"
        const val UNGROUPED_LABEL = "$SEPARATOR Default"
        const val FAVORITES_LABEL = "$SEPARATOR Favorites"
    }

    object SystemIds {
        val UNGROUPED = IdUtils.toBase36Fixed(0)
        val FAVORITES = IdUtils.toBase36Fixed(1)
    }

    object ClockFormat {
        const val NONE = "none"
        const val DEFAULT = "default"
        const val HOUR_12 = "12-hour"
        const val HOUR_24 = "24-hour"
    }

    object DateFormat {
        const val NONE = "none"
        const val DEFAULT = "default"
        const val YMD = "YYYYMMDD"
        const val MDY = "MMDDYYYY"
        const val DMY = "DDMMYYYY"
    }

    object IconSource {
        const val NONE = "none"
        const val SYSTEM = "system"
        const val MONOCHROME = "monochrome"
        const val ICON_PACK = "icon_pack"
    }

    object TemperatureFormat {
        const val DEFAULT = "default"
        const val CELSIUS = "celsius"
        const val FAHRENHEIT = "fahrenheit"
    }

    object BackgroundMode {
        const val DEFAULT = "default"
        const val WALLPAPER = "wallpaper"
    }

    // Local InitPrefs
    override fun applyAppDefaults(editor: SharedPreferences.Editor) {
        val defaultIds = setOf(
            SystemIds.UNGROUPED,
            SystemIds.FAVORITES
        )

        editor.putStringSet(FileKeys.GROUPS_IDS, defaultIds)

        editor.putString(FileKeys.GROUP_LABEL(SystemIds.UNGROUPED), UI.UNGROUPED_LABEL)
        editor.putBoolean(FileKeys.GROUP_VISIBLE(SystemIds.UNGROUPED), true)
        editor.putBoolean(FileKeys.GROUP_EXPANDED(SystemIds.UNGROUPED), true)
        editor.putInt(FileKeys.GROUP_ORDER(SystemIds.UNGROUPED), 0)

        editor.putString(FileKeys.GROUP_LABEL(SystemIds.FAVORITES), UI.FAVORITES_LABEL)
        editor.putBoolean(FileKeys.GROUP_VISIBLE(SystemIds.FAVORITES), true)
        editor.putBoolean(FileKeys.GROUP_EXPANDED(SystemIds.FAVORITES), true)
        editor.putInt(FileKeys.GROUP_ORDER(SystemIds.FAVORITES), 1)

        editor.putString(FileKeys.CLOCK_FORMAT, ClockFormat.HOUR_24)
        editor.putString(FileKeys.CLOCK_APP, "")

        editor.putBoolean(FileKeys.APPS_SEARCH, false)
        editor.putBoolean(FileKeys.APPS_SEARCH_INCLUDE_HIDDEN_GROUPS, false)
        editor.putBoolean(FileKeys.APPS_PROFILE_INDICATOR, true)

        editor.putBoolean(FileKeys.APPS_ICONS, false)
        editor.putString(FileKeys.APPS_ICON_SOURCE, IconSource.NONE)
        editor.putString(FileKeys.APPS_ICON_PACK_PACKAGE, "")
        editor.putBoolean(FileKeys.APPS_ICONS_OPEN_SETTINGS, true)
        editor.putBoolean(FileKeys.APPS_MULTI_COLUMN, false)
        editor.putBoolean(FileKeys.APPS_SHOW_API_INDICATORS, false)
        editor.putBoolean(FileKeys.APPS_SHOW_SIZE, false)
        editor.putBoolean(FileKeys.APPS_SHOW_SHORTCUTS, false)
        editor.putBoolean(FileKeys.APPS_RTL_ROW, false)
        editor.putInt(FileKeys.APPS_FONT_SIZE, 32)
        editor.putInt(FileKeys.APPS_ROW_SPACING, 12)

        editor.putString(FileKeys.HOME_BACKGROUND_MODE, BackgroundMode.DEFAULT)
        editor.putInt(FileKeys.HOME_BACKGROUND_MODE_SCREEN_OPACITY_STRENGTH, 9)

        editor.putBoolean(FileKeys.BATTERY_VISIBLE, true)
        editor.putBoolean(FileKeys.BATTERY_TEMPERATURE, true)
        editor.putString(FileKeys.TEMPERATURE_FORMAT, TemperatureFormat.DEFAULT)
        editor.putBoolean(FileKeys.BATTERY_CHARGE_STATUS, false)

        editor.putString(FileKeys.DATE_FORMAT, DateFormat.YMD)
        editor.putBoolean(FileKeys.DATE_YEAR_DAY, true)
        editor.putBoolean(FileKeys.DATE_YEAR_WEEK, true)
        editor.putString(FileKeys.DATE_APP, "")

        editor.putBoolean(FileKeys.GROUPS_HEADERS, true)
        editor.putBoolean(FileKeys.GROUPS_COLLAPSIBLE, true)
        editor.putBoolean(FileKeys.GROUPS_COLLAPSE_ON_HOME_FOCUS, false)
        editor.putBoolean(FileKeys.SECURITY_KEYPAD_RANDOMIZED, true)
        editor.putBoolean(FileKeys.SECURITY_KEYPAD_VISIBLE, false)

        fun migrateLegacyPrefs(sync: Boolean = false) {
            val editor = prefs.edit()
            var hasChanges = false

            if (!prefs.getBoolean(FileKeys.MIGRATION_ICON_SOURCE_RADIO, false)) {
                val iconsEnabled = prefs.getBoolean(FileKeys.APPS_ICONS, false)
                val currentSource = prefs.getString(FileKeys.APPS_ICON_SOURCE, IconSource.SYSTEM)

                val normalizedSource = when (currentSource) {
                    IconSource.NONE -> IconSource.NONE
                    IconSource.MONOCHROME -> IconSource.MONOCHROME
                    IconSource.ICON_PACK -> IconSource.ICON_PACK
                    else -> IconSource.SYSTEM
                }

                val migratedSource = if (iconsEnabled) normalizedSource else IconSource.NONE

                editor.putString(FileKeys.APPS_ICON_SOURCE, migratedSource)
                editor.putBoolean(FileKeys.MIGRATION_ICON_SOURCE_RADIO, true)
                hasChanges = true
            }

            if (!prefs.getBoolean(FileKeys.MIGRATION_DATE_FORMAT_RADIO, false)) {
                if (prefs.contains(FileKeys.DATE_VISIBLE)) {
                    val wasVisible = prefs.getBoolean(FileKeys.DATE_VISIBLE, true)

                    if (!wasVisible) {
                        editor.putString(FileKeys.DATE_FORMAT, DateFormat.NONE)
                    }
                    editor.remove(FileKeys.DATE_VISIBLE)
                }

                editor.putBoolean(FileKeys.MIGRATION_DATE_FORMAT_RADIO, true)
                hasChanges = true
            }

            if (hasChanges) {
                if (sync) editor.commit() else editor.apply()
            }
        }

        migrateLegacyPrefs(true)
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAllKeys(): Set<String> = prefs.all.keys

    fun addGroupId(id: String) {
        val updated = getGroupIds().toMutableSet()
        updated.add(id)
        setGroupIds(updated)
    }

    fun removeGroupId(id: String) {
        val updated = getGroupIds().toMutableSet()
        updated.remove(id)
        setGroupIds(updated)
    }

    fun clearGroupMetadata(id: String) {
        prefs.edit()
            .remove(FileKeys.GROUP_LABEL(id))
            .remove(FileKeys.GROUP_VISIBLE(id))
            .remove(FileKeys.GROUP_EXPANDED(id))
            .remove(FileKeys.GROUP_ORDER(id))
            .remove(FileKeys.GROUP_KEEP_EXPANDED(id))
            .apply()
    }

    fun getAppGroupId(app: AppsProvider.AppEntry): String =
        getAppGroupId(FileKeys.APP_GROUP_ID(app))

    fun getAppGroupId(pkg: String, userHandle: UserHandle): String =
        getAppGroupId(FileKeys.APP_GROUP_ID(pkg, userHandle))

    private fun getAppGroupId(key: String): String {
        return prefs.getString(key, null)
            ?: SystemIds.UNGROUPED.also {
                prefs.edit().putString(key, it).apply()
            }
    }

    fun getRawAppGroupId(app: AppsProvider.AppEntry): String? =
        prefs.getString(FileKeys.APP_GROUP_ID(app), null)

    fun getRawAppGroupId(pkg: String, userHandle: UserHandle): String? =
        prefs.getString(FileKeys.APP_GROUP_ID(pkg, userHandle), null)

    fun setAppGroupId(app: AppsProvider.AppEntry, groupId: String?) =
        setAppGroupId(FileKeys.APP_GROUP_ID(app), groupId)

    fun setAppGroupId(pkg: String, userHandle: UserHandle, groupId: String?) =
        setAppGroupId(FileKeys.APP_GROUP_ID(pkg, userHandle), groupId)

    private fun setAppGroupId(key: String, groupId: String?) {
        val editor = prefs.edit()
        if (groupId != null) editor.putString(key, groupId) else editor.remove(key)
        editor.apply()
    }

    fun getGroupIds(): Set<String> =
        prefs.getStringSet(FileKeys.GROUPS_IDS, emptySet()) ?: emptySet()

    fun setGroupIds(ids: Set<String>) =
        prefs.edit().putStringSet(FileKeys.GROUPS_IDS, ids).apply()

    fun getGroupLabel(id: String): String =
        prefs.getString(FileKeys.GROUP_LABEL(id), "") ?: ""

    fun setGroupLabel(id: String, value: String) =
        prefs.edit().putString(FileKeys.GROUP_LABEL(id), value).apply()

    fun isGroupVisible(id: String): Boolean =
        prefs.getBoolean(FileKeys.GROUP_VISIBLE(id), false)

    fun setGroupVisible(id: String, value: Boolean) =
        prefs.edit().putBoolean(FileKeys.GROUP_VISIBLE(id), value).apply()

    fun isGroupExpanded(id: String): Boolean =
        prefs.getBoolean(FileKeys.GROUP_EXPANDED(id), false)

    fun setGroupExpanded(id: String, value: Boolean) =
        prefs.edit().putBoolean(FileKeys.GROUP_EXPANDED(id), value).apply()

    fun setGroupsExpanded(ids: Set<String>, expanded: Boolean) {
        val editor = prefs.edit()
        ids.forEach { editor.putBoolean(FileKeys.GROUP_EXPANDED(it), expanded) }
        editor.apply()
    }

    fun isGroupKeepExpanded(id: String): Boolean =
        prefs.getBoolean(FileKeys.GROUP_KEEP_EXPANDED(id), false)

    fun setGroupKeepExpanded(id: String, value: Boolean) =
        prefs.edit().putBoolean(FileKeys.GROUP_KEEP_EXPANDED(id), value).apply()

    fun hasGroupOrder(id: String): Boolean =
        prefs.contains(FileKeys.GROUP_ORDER(id))

    fun getGroupOrder(id: String): Int =
        prefs.getInt(FileKeys.GROUP_ORDER(id), Int.MAX_VALUE)

    fun setGroupOrder(id: String, value: Int) =
        prefs.edit().putInt(FileKeys.GROUP_ORDER(id), value).apply()

    fun isAutoCollapseGroupsEnabled(): Boolean =
        getBoolean(FileKeys.GROUPS_AUTO_COLLAPSE, false)

    fun setAutoCollapseGroupsEnabled(enabled: Boolean) {
        setBoolean(FileKeys.GROUPS_AUTO_COLLAPSE, enabled)
    }

    fun healData(
        installedApps: List<AppsProvider.AppEntry>,
        preserveShortcutData: Boolean
    ): Boolean {
        if (installedApps.isEmpty()) return false

        val editor = prefs.edit()
        var changed = false
        val preservedShortcutGroupIds = mutableSetOf<String>()

        val installedKeys = installedApps
            .map(FileKeys::appKey)
            .toSet()

        getAllKeys().forEach { key ->
            if (!key.startsWith("app:")) return@forEach

            val appKey = when {
                key.endsWith(":group_id") -> key.removeSuffix(":group_id")
                key.endsWith(":custom_label") -> key.removeSuffix(":custom_label")
                else -> null
            }

            if (appKey == null) return@forEach
            if (preserveShortcutData && FileKeys.isShortcutAppKey(appKey)) {
                if (key.endsWith(":group_id")) {
                    prefs.getString(key, null)?.let(preservedShortcutGroupIds::add)
                }
                return@forEach
            }

            if (appKey !in installedKeys) {
                editor.remove(key)
                changed = true
            }
        }

        val activeGroupIds = mutableSetOf(SystemIds.UNGROUPED, SystemIds.FAVORITES)
        activeGroupIds.addAll(preservedShortcutGroupIds)
        installedApps.forEach { app ->
            getRawAppGroupId(app)?.let(activeGroupIds::add)
        }

        val currentGroupIds = getGroupIds().toMutableSet()
        val idsToRemove = currentGroupIds - activeGroupIds
        idsToRemove.forEach { id ->
            editor.remove(FileKeys.GROUP_LABEL(id))
            editor.remove(FileKeys.GROUP_VISIBLE(id))
            editor.remove(FileKeys.GROUP_EXPANDED(id))
            editor.remove(FileKeys.GROUP_ORDER(id))
            editor.remove(FileKeys.GROUP_KEEP_EXPANDED(id))
            currentGroupIds.remove(id)
            changed = true
        }

        if (changed) {
            editor.putStringSet(FileKeys.GROUPS_IDS, currentGroupIds)
            editor.apply()
        }

        return changed
    }

    fun isSearchVisible(): Boolean =
        prefs.getBoolean(FileKeys.APPS_SEARCH, false)

    fun isSearchBarAlwaysVisible(): Boolean =
        prefs.getBoolean(FileKeys.APPS_SEARCH_ALWAYS_VISIBLE, false)

    fun includeHiddenGroupsInSearch(): Boolean =
        prefs.getBoolean(FileKeys.APPS_SEARCH_INCLUDE_HIDDEN_GROUPS, false)

    fun hasIconsVisible(): Boolean =
        getIconSource() != IconSource.NONE

    fun hasProfileIndicator(): Boolean =
        prefs.getBoolean(FileKeys.APPS_PROFILE_INDICATOR, true)

    fun hasIconsOpenSettings(): Boolean =
        prefs.getBoolean(FileKeys.APPS_ICONS_OPEN_SETTINGS, true)

    fun isMultiColumnEnabled(): Boolean =
        prefs.getBoolean(FileKeys.APPS_MULTI_COLUMN, false)

    fun hasApiIndicatorsVisible(): Boolean =
        prefs.getBoolean(FileKeys.APPS_SHOW_API_INDICATORS, false)

    fun hasAppSizeVisible(): Boolean =
        prefs.getBoolean(FileKeys.APPS_SHOW_SIZE, false)

    fun hasAppShortcuts(): Boolean =
        prefs.getBoolean(FileKeys.APPS_SHOW_SHORTCUTS, false)

    fun isRtlRowEnabled(): Boolean =
        prefs.getBoolean(FileKeys.APPS_RTL_ROW, false)

    fun setRtlRowEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(FileKeys.APPS_RTL_ROW, enabled).apply()

    fun getAppFontSize(): Int =
        prefs.getInt(FileKeys.APPS_FONT_SIZE, 32)

    fun setAppFontSize(size: Int) =
        prefs.edit().putInt(FileKeys.APPS_FONT_SIZE, size).apply()

    fun getAppRowSpacing(): Int =
        prefs.getInt(FileKeys.APPS_ROW_SPACING, 12)

    fun setAppRowSpacing(spacing: Int) =
        prefs.edit().putInt(FileKeys.APPS_ROW_SPACING, spacing).apply()

    fun setMultiColumnEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(FileKeys.APPS_MULTI_COLUMN, enabled).apply()

    fun getIconSource(): String {
        return when (prefs.getString(FileKeys.APPS_ICON_SOURCE, IconSource.NONE)) {
            IconSource.NONE -> IconSource.NONE
            IconSource.MONOCHROME -> IconSource.MONOCHROME
            IconSource.ICON_PACK -> IconSource.ICON_PACK
            IconSource.SYSTEM -> IconSource.SYSTEM
            else -> IconSource.NONE
        }
    }

    fun setIconSource(source: String) {
        val normalized = when (source) {
            IconSource.NONE -> IconSource.NONE
            IconSource.MONOCHROME -> IconSource.MONOCHROME
            IconSource.ICON_PACK -> IconSource.ICON_PACK
            else -> IconSource.SYSTEM
        }
        prefs.edit().putString(FileKeys.APPS_ICON_SOURCE, normalized).apply()
    }

    fun getIconPackPackage(): String =
        prefs.getString(FileKeys.APPS_ICON_PACK_PACKAGE, "") ?: ""

    fun setIconPackPackage(packageName: String) =
        prefs.edit().putString(FileKeys.APPS_ICON_PACK_PACKAGE, packageName).apply()

    fun hasGroupHeaders(): Boolean =
        prefs.getBoolean(FileKeys.GROUPS_HEADERS, false)

    fun hasCollapsibleGroups(): Boolean =
        prefs.getBoolean(FileKeys.GROUPS_COLLAPSIBLE, false)

    fun shouldCollapseGroupsOnHomeFocus(): Boolean =
        prefs.getBoolean(FileKeys.GROUPS_COLLAPSE_ON_HOME_FOCUS, false)

    fun getClockFormat(): String =
        prefs.getString(FileKeys.CLOCK_FORMAT, "") ?: ""

    fun setClockFormat(format: String) =
        prefs.edit().putString(FileKeys.CLOCK_FORMAT, format).apply()

    fun getClockApp(): String =
        prefs.getString(FileKeys.CLOCK_APP, "") ?: ""

    fun setClockApp(appId: String) =
        prefs.edit().putString(FileKeys.CLOCK_APP, appId).apply()

    fun getDateFormat(): String =
        prefs.getString(FileKeys.DATE_FORMAT, "") ?: ""

    fun setDateFormat(format: String) =
        prefs.edit().putString(FileKeys.DATE_FORMAT, format).apply()

    fun getDateApp(): String =
        prefs.getString(FileKeys.DATE_APP, "") ?: ""

    fun setDateApp(appId: String) =
        prefs.edit().putString(FileKeys.DATE_APP, appId).apply()

    // SETTINGS - DATE

    fun isDateVisible(): Boolean =
        getDateFormat() != DateFormat.NONE

    fun isYearDayVisible(): Boolean =
        prefs.getBoolean(FileKeys.DATE_YEAR_DAY, false)

    fun isYearWeekVisible(): Boolean =
        prefs.getBoolean(FileKeys.DATE_YEAR_WEEK, true)

    fun isBatteryVisible(): Boolean =
        prefs.getBoolean(FileKeys.BATTERY_VISIBLE, false)

    fun isBatteryTemperatureVisible(): Boolean =
        prefs.getBoolean(FileKeys.BATTERY_TEMPERATURE, false)

    fun getTemperatureFormat(): String {
        return when (prefs.getString(FileKeys.TEMPERATURE_FORMAT, TemperatureFormat.DEFAULT)) {
            TemperatureFormat.CELSIUS -> TemperatureFormat.CELSIUS
            TemperatureFormat.FAHRENHEIT -> TemperatureFormat.FAHRENHEIT
            else -> TemperatureFormat.DEFAULT
        }
    }

    fun setTemperatureFormat(format: String) {
        val normalized = when (format) {
            TemperatureFormat.CELSIUS -> TemperatureFormat.CELSIUS
            TemperatureFormat.FAHRENHEIT -> TemperatureFormat.FAHRENHEIT
            else -> TemperatureFormat.DEFAULT
        }
        prefs.edit().putString(FileKeys.TEMPERATURE_FORMAT, normalized).apply()
    }

    fun isBatteryChargeStatusVisible(): Boolean =
        prefs.getBoolean(FileKeys.BATTERY_CHARGE_STATUS, false)

    fun getHomeBackgroundScreenOpacityStrength(): Int {
        return prefs.getInt(FileKeys.HOME_BACKGROUND_MODE_SCREEN_OPACITY_STRENGTH, 9)
    }

    fun setHomeBackgroundScreenOpacityStrength(strength: Int) {
        prefs.edit().putInt(FileKeys.HOME_BACKGROUND_MODE_SCREEN_OPACITY_STRENGTH, strength).apply()
    }

    fun getHomeBackgroundMode(): String {
        return when (prefs.getString(FileKeys.HOME_BACKGROUND_MODE, BackgroundMode.DEFAULT)) {
            BackgroundMode.WALLPAPER -> BackgroundMode.WALLPAPER
            else -> BackgroundMode.DEFAULT
        }
    }

    fun setHomeBackgroundMode(mode: String) {
        val normalized = when (mode) {
            BackgroundMode.WALLPAPER -> BackgroundMode.WALLPAPER
            else -> BackgroundMode.DEFAULT
        }

        prefs.edit().putString(FileKeys.HOME_BACKGROUND_MODE, normalized).apply()
    }

    fun getPin(): String =
        encryptedPrefs.getString(FileKeys.SECURITY_PIN, "") ?: ""

    fun setPin(pin: String) =
        encryptedPrefs.edit().putString(FileKeys.SECURITY_PIN, pin).apply()

    fun clearPin() =
        encryptedPrefs.edit().remove(FileKeys.SECURITY_PIN).apply()

    fun isLockEnabled(): Boolean =
        prefs.getBoolean(FileKeys.SECURITY_KEYPAD_VISIBLE, false)

    fun isKeypadRandomized(): Boolean =
        prefs.getBoolean(FileKeys.SECURITY_KEYPAD_RANDOMIZED, true)

    fun getCustomName(app: AppsProvider.AppEntry): String? =
        prefs.getString(FileKeys.APP_CUSTOM_LABEL(app), null)

    fun getCustomName(pkg: String, userHandle: UserHandle): String? =
        prefs.getString(FileKeys.APP_CUSTOM_LABEL(pkg, userHandle), null)

    fun setCustomName(app: AppsProvider.AppEntry, name: String) =
        prefs.edit().putString(FileKeys.APP_CUSTOM_LABEL(app), name).apply()

    fun setCustomName(pkg: String, userHandle: UserHandle, name: String) =
        prefs.edit().putString(FileKeys.APP_CUSTOM_LABEL(pkg, userHandle), name).apply()

    fun clearCustomName(app: AppsProvider.AppEntry) =
        prefs.edit().remove(FileKeys.APP_CUSTOM_LABEL(app)).apply()

    fun clearCustomName(pkg: String, userHandle: UserHandle) =
        prefs.edit().remove(FileKeys.APP_CUSTOM_LABEL(pkg, userHandle)).apply()

    companion object {
        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also {
                    INSTANCE = it
                    register(it)
                }
            }
    }
}
