package com.rama.mako.activities.settings

import android.view.View
import com.rama.bohio.objects.PrefKeys
import com.rama.mako.R
import com.rama.mako.activities.SettingsActivity
import com.rama.mako.managers.PrefsManager.FileKeys
import com.rama.bohio.widgets.WdCheckbox

class SettingsCheckboxController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        bindWdCheckbox(
            R.id.show_search,
            FileKeys.APPS_SEARCH,
            false,
            listOf(R.id.always_show_search)
        )
        bindWdCheckbox(R.id.always_show_search, FileKeys.APPS_SEARCH_ALWAYS_VISIBLE, false)
        bindWdCheckbox(
            R.id.search_include_hidden,
            FileKeys.APPS_SEARCH_INCLUDE_HIDDEN_GROUPS,
            false
        )

        val showGroupHeader = activity.findViewById<WdCheckbox>(R.id.show_group_header)
        val hasCollapsibleGroups = activity.findViewById<WdCheckbox>(R.id.has_collapsible_groups)
        val collapseOnHomeFocus =
            activity.findViewById<WdCheckbox>(R.id.collapse_groups_on_home_focus)


        fun updateCollapseOnHomeVisibility() {
            collapseOnHomeFocus.visibility =
                if (showGroupHeader.isChecked() && hasCollapsibleGroups.isChecked()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        bindWdCheckbox(
            R.id.show_group_header,
            FileKeys.GROUPS_HEADERS,
            false,
            listOf(R.id.has_collapsible_groups),
            onChange = { updateCollapseOnHomeVisibility() }
        )
        bindWdCheckbox(
            R.id.has_collapsible_groups,
            FileKeys.GROUPS_COLLAPSIBLE,
            false,
            listOf(R.id.auto_collapse_groups),
            onChange = { updateCollapseOnHomeVisibility() }
        )
        bindWdCheckbox(
            R.id.collapse_groups_on_home_focus,
            FileKeys.GROUPS_COLLAPSE_ON_HOME_FOCUS,
            false
        )

        val autoCollapseGroups = activity.findViewById<WdCheckbox>(R.id.auto_collapse_groups)
        autoCollapseGroups.setChecked(prefs.isAutoCollapseGroupsEnabled())
        autoCollapseGroups.setOnCheckedChangeListener { checked ->
            prefs.setAutoCollapseGroupsEnabled(checked)
        }

        updateCollapseOnHomeVisibility()
        bindWdCheckbox(R.id.show_year_day, FileKeys.DATE_YEAR_DAY, prefs.isYearDayVisible())
        bindWdCheckbox(R.id.show_year_week, FileKeys.DATE_YEAR_WEEK, prefs.isYearWeekVisible())
        bindWdCheckbox(
            R.id.show_battery,
            FileKeys.BATTERY_VISIBLE,
            false,
            listOf(R.id.show_battery_temperature, R.id.show_battery_charge_status)
        )
        bindWdCheckbox(R.id.show_battery_temperature, FileKeys.BATTERY_TEMPERATURE, false)
        bindWdCheckbox(R.id.show_battery_charge_status, FileKeys.BATTERY_CHARGE_STATUS, false)
        bindWdCheckbox(R.id.show_system_bar, PrefKeys.SYSTEM_BAR_VISIBLE, false)
        bindWdCheckbox(
            R.id.prevent_home_screen_rotation,
            PrefKeys.SYSTEM_PREVENT_ROTATION,
            false,
            onChange = { isChecked ->
                activity.applyRotationLock(isChecked)
            }
        )

        bindWdCheckbox(
            R.id.nav_bar_transparent,
            PrefKeys.SYSTEM_NAV_BAR_TRANSPARENT,
            false,
            onChange = { isChecked ->
                activity.applyNavBarContrastEnforced(!isChecked)
            }
        )

        bindWdCheckbox(R.id.show_profile_indicator, FileKeys.APPS_PROFILE_INDICATOR, true)
        bindWdCheckbox(R.id.multi_column, FileKeys.APPS_MULTI_COLUMN, false)
        bindWdCheckbox(R.id.show_api_indicators, FileKeys.APPS_SHOW_API_INDICATORS, false)
        bindWdCheckbox(R.id.show_app_size, FileKeys.APPS_SHOW_SIZE, false)
        bindWdCheckbox(R.id.show_app_shortcuts, FileKeys.APPS_SHOW_SHORTCUTS, false)
        bindWdCheckbox(R.id.rtl_row, FileKeys.APPS_RTL_ROW, false)

        bindWdCheckbox(
            R.id.lock_settings,
            FileKeys.SECURITY_KEYPAD_VISIBLE,
            false,
            listOf(R.id.randomized_keypad, R.id.pin_field),
            onChange = { checked ->
                if (!checked) prefs.clearPin()
            }
        )
        bindWdCheckbox(
            R.id.randomized_keypad,
            FileKeys.SECURITY_KEYPAD_RANDOMIZED,
            true,
        )
        bindWdCheckbox(
            R.id.icons_open_settings,
            FileKeys.APPS_ICONS_OPEN_SETTINGS,
            true,
        )
    }

    private fun bindWdCheckbox(
        wdCheckboxId: Int,
        key: String,
        defaultValue: Boolean,
        dependentViewIds: List<Int>? = null,
        onChange: ((Boolean) -> Unit)? = null
    ) {
        val checkbox = activity.findViewById<WdCheckbox>(wdCheckboxId)
        val dependents = dependentViewIds?.map { activity.findViewById<View>(it) }

        val isChecked = prefs.getBoolean(key, defaultValue)
        checkbox.setChecked(isChecked)

        dependents?.forEach {
            it.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        checkbox.setOnCheckedChangeListener { checked ->
            prefs.setBoolean(key, checked)
            dependents?.forEach {
                it.visibility = if (checked) View.VISIBLE else View.GONE
            }
            onChange?.invoke(checked)
        }
    }
}
