package com.rama.mako.managers

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.generateViewId
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rama.mako.R
import com.rama.bohio.R as BohioR
import com.rama.bohio.util.Dimens.spToPx
import com.rama.mako.activities.SettingsActivity
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import com.rama.bohio.managers.ThemeManager

class AppListManager(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val appsProvider: AppsProvider,
    private val onAppLaunched: (() -> Unit)? = null
) {
    private data class ScoredApp(
        val app: AppsProvider.AppEntry,
        val score: Int,
        val normalizedName: String
    )

    private data class GroupMatch(
        val groupId: String,
        val label: String,
        val bestScore: Int,
        val apps: List<ScoredApp>
    )

    private val prefs = PrefsManager.getInstance(context)
    private val iconManager = IconManager(context, appsProvider)
    private val groupsManager = GroupsManager(context, appsProvider)
    private val items = mutableListOf<ListItem>()
    private val adapter = AppAdapter()
    private lateinit var layoutManager: GridLayoutManager
    private var allAppsCache: List<AppsProvider.AppEntry> = emptyList()
    private val searchableNameCache = mutableMapOf<String, String>()
    private val packageNameCache = mutableMapOf<String, String>()
    private val combiningMarkRegex = Regex("\\p{M}+")
    private val tokenSeparatorRegex = Regex("[^a-z0-9]+")
    private val specialCharRegex = Regex("[^a-z0-9]+")

    private companion object {
        private const val PACKAGE_SCORE_PENALTY = 2000
        private const val GROUP_SCORE_PENALTY = 4000
        private const val APP_SIZE_WARNING_BYTES = 200L * 1024 * 1024 // 200 MB
        private const val VIEW_TYPE_GROUP_HEADER = 1
        private const val VIEW_TYPE_APP = 2
        private const val VIEW_TYPE_EMPTY = 3
    }

    private var isMultiSelectMode = false
    private val selectedApps = mutableSetOf<String>()
    private var multiSelectBar: LinearLayout? = null
    private var selectedCountText: TextView? = null
    private var renameButton: FrameLayout? = null
    private var appSettingsButton: FrameLayout? = null
    private var appSettingsIcon: ImageView? = null

    fun setup() {
        configureRecyclerView()
        updateAppsCache()
        buildItems()
        setupMultiSelectBar()
    }

    fun handleBackPress(): Boolean {
        if (isMultiSelectMode) {
            exitMultiSelectMode()
            return true
        }
        return false
    }

    fun isInMultiSelectMode(): Boolean = isMultiSelectMode

    fun refresh() {
        val columnCount = computeColumnCount()
        if (layoutManager.spanCount != columnCount) {
            layoutManager.spanCount = columnCount
        }
        recyclerView.layoutDirection = if (prefs.isRtlRowEnabled()) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        updateAppsCache()
        buildItems()
    }

    fun collapseAllGroups(): Boolean {
        if (!prefs.hasGroupHeaders() || !prefs.hasCollapsibleGroups()) return false

        val expandedIds = getAllGroupIds().filter {
            prefs.isGroupExpanded(it) && !prefs.isGroupKeepExpanded(it)
        }
        if (expandedIds.isEmpty()) return false

        prefs.setGroupsExpanded(expandedIds.toSet(), false)
        refresh()
        return true
    }

    private fun getAllGroupIds(): List<String> {
        val knownGroupIds = groupsManager.getGroupIds()
        val appGroupIds = allAppsCache
            .map(prefs::getAppGroupId)
            .distinct()
        val unknownGroupIds = appGroupIds.filter { it !in knownGroupIds }
        return (knownGroupIds + unknownGroupIds)
            .distinct()
            .sortedWith(compareByDescending { prefs.isGroupKeepExpanded(it) })
    }

    private fun updateAppsCache() {
        allAppsCache = appsProvider.getAll(includeShortcuts = prefs.hasAppShortcuts())
        searchableNameCache.clear()
        packageNameCache.clear()
    }

    private fun buildItems() {
        val allApps = allAppsCache

        // Map apps by groupId (NOT label)
        val groupedMap = allApps.groupBy(prefs::getAppGroupId)

        items.clear()

        getAllGroupIds().forEach { groupId ->

            val apps = groupedMap[groupId] ?: return@forEach

            val isVisible = prefs.isGroupVisible(groupId)
            if (!isVisible) return@forEach

            val label = prefs.getGroupLabel(groupId)

            // Header uses label only for display
            if (prefs.hasGroupHeaders()) {
                items.add(
                    ListItem.Header(
                        id = groupId,
                        title = label
                    )
                )
            }

            val isExpanded = prefs.isGroupKeepExpanded(groupId) || prefs.isGroupExpanded(groupId)
            if (!isExpanded) return@forEach

            apps.sortedBy { getSearchableName(it) }
                .forEach { items.add(ListItem.App(it)) }
        }

        adapter.updateItems(arrangeItemsForColumns(items))
    }

    private fun getAppCacheKey(app: AppsProvider.AppEntry): String =
        PrefsManager.FileKeys.appKey(app)

    private fun getSearchableName(app: AppsProvider.AppEntry): String {
        val key = getAppCacheKey(app)
        return searchableNameCache.getOrPut(key) {
            normalizeForSearch(getDisplayName(app))
        }
    }

    private fun getSearchablePackageName(app: AppsProvider.AppEntry): String {
        return packageNameCache.getOrPut(app.packageName) {
            stripSpecialChars(normalizeForSearch(app.packageName))
        }
    }

    // Explicit ones that might cause trouble when getting normalized
    private fun normalizeForSearch(value: String): String {
        val foldedTurkish = value
            .lowercase(Locale.ROOT)
            .replace('ı', 'i')
            .replace('ş', 's')
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ö', 'o')
            .replace('ü', 'u')

        val foldedSpanish = foldedTurkish
            .replace('ñ', 'n')
            .replace('¡', ' ')
            .replace('¿', ' ')

        return Normalizer.normalize(foldedSpanish, Normalizer.Form.NFD)
            .replace(combiningMarkRegex, "")
            .trim()
    }

    private fun stripSpecialChars(value: String): String {
        return value.replace(specialCharRegex, "")
    }

    private fun maxFuzzyDistance(queryLength: Int): Int {
        return when {
            queryLength <= 3 -> 0
            queryLength <= 5 -> 1
            queryLength <= 8 -> 2
            else -> 3
        }
    }

    private fun findWordPrefixIndex(text: String, query: String): Int {
        if (query.isEmpty() || query.length > text.length) return -1

        val lastStart = text.length - query.length
        for (i in 0..lastStart) {
            val isWordStart = i == 0 || !text[i - 1].isLetterOrDigit()
            if (!isWordStart) continue

            if (text.regionMatches(i, query, 0, query.length, ignoreCase = false)) {
                return i
            }
        }

        return -1
    }

    private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int? {
        if (abs(a.length - b.length) > maxDistance) return null
        if (a == b) return 0
        if (a.isEmpty()) return if (b.length <= maxDistance) b.length else null
        if (b.isEmpty()) return if (a.length <= maxDistance) a.length else null

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            val left = a[i - 1]

            for (j in 1..b.length) {
                val cost = if (left == b[j - 1]) 0 else 1
                val deletion = previous[j] + 1
                val insertion = current[j - 1] + 1
                val substitution = previous[j - 1] + cost

                val cell = minOf(deletion, insertion, substitution)
                current[j] = cell
                if (cell < rowMin) rowMin = cell
            }

            if (rowMin > maxDistance) return null

            val swap = previous
            previous = current
            current = swap
        }

        val result = previous[b.length]
        return if (result <= maxDistance) result else null
    }

    private fun getFuzzyDistance(name: String, query: String, maxDistance: Int): Int? {
        var best: Int? = null

        fun tryCandidate(candidate: String) {
            if (candidate.isEmpty()) return
            val distance = boundedLevenshtein(candidate, query, maxDistance) ?: return
            if (best == null || distance < best!!) {
                best = distance
            }
        }

        tryCandidate(name)

        name.split(tokenSeparatorRegex)
            .filter { it.isNotEmpty() }
            .forEach { token ->
                tryCandidate(token)
            }

        return best
    }

    private fun scoreMatch(name: String, query: String): Int? {
        if (name == query) return 0

        if (name.startsWith(query)) {
            return 100 + (name.length - query.length).coerceAtLeast(0)
        }

        val wordPrefixIndex = findWordPrefixIndex(name, query)
        if (wordPrefixIndex >= 0) {
            return 200 + wordPrefixIndex
        }

        val containsIndex = name.indexOf(query)
        if (containsIndex >= 0) {
            val lengthDiff = abs(name.length - query.length)
            return 300 + (containsIndex * 2) + lengthDiff
        }

        val maxDistance = maxFuzzyDistance(query.length)
        if (maxDistance == 0) return null

        val fuzzyDistance = getFuzzyDistance(name, query, maxDistance) ?: return null
        val lengthDiff = abs(name.length - query.length)

        return 1000 + (fuzzyDistance * 100) + lengthDiff
    }

    private fun getDisplayName(app: AppsProvider.AppEntry): String {
        val baseName = prefs.getCustomName(app) ?: app.displayLabel
        return if (prefs.hasProfileIndicator() && app.isWorkProfile) {
            "[${app.profileInitial}] $baseName"
        } else {
            baseName
        }
    }

    fun filter(query: String) {
        val normalizedQuery = normalizeForSearch(query)
        val strippedQuery = stripSpecialChars(normalizedQuery)
        val isSearchActive = strippedQuery.isNotEmpty()

        if (!isSearchActive) {
            buildItems()
            return
        }

        val filteredItems = mutableListOf<ListItem>()
        val matchedGroups = mutableListOf<GroupMatch>()

        val allApps = allAppsCache

        // Group by ID
        val groupedMap = allApps.groupBy(prefs::getAppGroupId)

        val allGroupIds = getAllGroupIds()
            .sortedBy { prefs.getGroupLabel(it).lowercase(Locale.ROOT) }

        allGroupIds.forEach { groupId ->

            val apps = groupedMap[groupId] ?: return@forEach

            if (!prefs.includeHiddenGroupsInSearch() && !prefs.isGroupVisible(groupId)) {
                return@forEach
            }

            val label = prefs.getGroupLabel(groupId)
            val normalizedGroupLabel = normalizeForSearch(label)
            val strippedGroupLabel = stripSpecialChars(normalizedGroupLabel)
            val groupLabelScore = scoreMatch(strippedGroupLabel, strippedQuery)

            val matchedApps = apps.mapNotNull { app ->
                val normalizedName = getSearchableName(app)
                val strippedName = stripSpecialChars(normalizedName)
                val displayScore = scoreMatch(strippedName, strippedQuery)

                if (displayScore != null) {
                    ScoredApp(app = app, score = displayScore, normalizedName = normalizedName)
                } else {
                    val packageScore = scoreMatch(getSearchablePackageName(app), strippedQuery)
                    val score = packageScore?.let { it + PACKAGE_SCORE_PENALTY }
                        ?: groupLabelScore?.let { it + GROUP_SCORE_PENALTY }
                        ?: return@mapNotNull null
                    ScoredApp(app = app, score = score, normalizedName = normalizedName)
                }
            }.sortedWith(
                compareBy<ScoredApp> { it.score }
                    .thenBy { it.normalizedName }
            )

            if (matchedApps.isEmpty()) return@forEach

            matchedGroups.add(
                GroupMatch(
                    groupId = groupId,
                    label = label,
                    bestScore = matchedApps.first().score,
                    apps = matchedApps
                )
            )
        }

        matchedGroups
            .sortedWith(
                compareBy<GroupMatch> { it.bestScore }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
            )
            .forEach { group ->
                if (prefs.hasGroupHeaders()) {
                    filteredItems.add(
                        ListItem.Header(
                            id = group.groupId,
                            title = group.label
                        )
                    )
                }

                group.apps.forEach { scoredApp ->
                    filteredItems.add(ListItem.App(scoredApp.app))
                }
            }

        items.clear()
        items.addAll(filteredItems)
        adapter.updateItems(arrangeItemsForColumns(items))
    }

    private fun openAppSettings(app: AppsProvider.AppEntry) {
        when (app) {
            is AppsProvider.ShortcutEntry -> {
                if (!app.isPinned) {
                    allAppsCache.filterIsInstance<AppsProvider.ActivityEntry>()
                        .firstOrNull {
                            it.packageName == app.packageName && it.userHandle == app.userHandle
                        }
                        ?.let { openAppSettings(it) }
                    return
                }

                AlertDialog.Builder(context)
                    .setTitle(R.string.h2_remove_shortcut)
                    .setMessage(getDisplayName(app))
                    .setPositiveButton(R.string.btn_remove_shortcut) { _, _ ->
                        if (!appsProvider.unpinShortcut(app)) {
                            Toast.makeText(
                                context,
                                R.string.toast_unable_remove_shortcut,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        refresh()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }

            is AppsProvider.ActivityEntry -> {
                // Apps living in another profile (e.g. the private space) must be opened
                // via LauncherApps with that profile's UserHandle, otherwise the system
                // resolves App Info (and therefore Uninstall) against the wrong user and
                // the private space app can't be found/uninstalled.
                if (appsProvider.openAppDetails(app)) return
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", app.packageName, null)
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            }
        }
    }

    private fun handleIconClick(app: AppsProvider.AppEntry) {
        if (isMultiSelectMode) {
            toggleSelection(app)
            return
        }

        if (prefs.hasIconsOpenSettings()) {
            openAppSettings(app)
            return
        }

        if (!appsProvider.launch(app)) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_unable_launch_app),
                Toast.LENGTH_SHORT
            ).show()
            refresh()
        } else {
            onAppLaunched?.invoke()
        }
    }

    private fun renderAppInfo(view: View, app: AppsProvider.AppEntry) {
        val apiRow = view.findViewById<LinearLayout>(R.id.api)
        val minApiText = view.findViewById<TextView>(R.id.min_api)
        val apiSeparator = view.findViewById<TextView>(R.id.api_separator)
        val targetApiText = view.findViewById<TextView>(R.id.target_api)
        val appSizeText = view.findViewById<TextView>(R.id.app_size)

        if (app !is AppsProvider.ActivityEntry) {
            apiRow.visibility = View.GONE
            appSizeText.visibility = View.GONE
            return
        }

        ThemeManager.applyTheme(context, apiRow)
        ThemeManager.applyTheme(context, appSizeText)

        val normalColor = ContextCompat.getColor(context, BohioR.color.disabled)
        val dangerColor by lazy { ThemeManager.paletteFor(prefs.getTheme(), context).danger }

        if (prefs.hasApiIndicatorsVisible()) {
            apiRow.visibility = View.VISIBLE

            minApiText.text = app.minSdkVersion.toString()
            targetApiText.text = app.targetSdkVersion.toString()

            val isOutdatedTarget = app.targetSdkVersion < Build.VERSION.SDK_INT
            val apiColor = if (isOutdatedTarget) dangerColor else normalColor

            minApiText.setTextColor(apiColor)
            apiSeparator.setTextColor(apiColor)
            targetApiText.setTextColor(apiColor)
        } else {
            apiRow.visibility = View.GONE
        }

        if (prefs.hasAppSizeVisible()) {
            appSizeText.visibility = View.VISIBLE

            val sizeBytes = appsProvider.getAppSizeBytes(app)
            appSizeText.text = Formatter.formatShortFileSize(context, sizeBytes)
            appSizeText.setTextColor(
                if (sizeBytes > APP_SIZE_WARNING_BYTES) dangerColor else normalColor
            )
        } else {
            appSizeText.visibility = View.GONE
        }
    }

    private fun showRenameDialog(app: AppsProvider.AppEntry) {
        val currentName = prefs.getCustomName(app) ?: app.label

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_rename_app, null)
        ThemeManager.applyTheme(context, view)
        val input = view.findViewById<EditText>(R.id.edit_text)
        val yesButton = view.findViewById<FrameLayout>(R.id.yes_button)
        val resetButton = view.findViewById<Button>(R.id.reset_button)
        val noButton = view.findViewById<Button>(R.id.no_button)

        input.setText(currentName)
        input.setSelection(input.text.length)

        val dialog = AlertDialog.Builder(context).setView(view).create()

        yesButton.setOnClickListener {
            input.text.toString().trim().let { prefs.setCustomName(app, it) }
            refresh()
            Toast.makeText(
                context,
                context.getString(R.string.toast_label_changed),
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        resetButton.setOnClickListener {
            prefs.clearCustomName(app)
            refresh()
            Toast.makeText(
                context,
                context.getString(R.string.toast_label_changed),
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }

        noButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showGroupsDialog(app: AppsProvider.AppEntry) {

        val view = View.inflate(context, R.layout.dialog_groups_pick, null)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        val closeBtn = view.findViewById<View>(R.id.close_button)
        val container = view.findViewById<RadioGroup>(R.id.groups)

        fun renderGroups() {
            container.removeAllViews()

            val radioGroup = RadioGroup(context)

            val currentGroupId = prefs.getAppGroupId(app)

            // All group IDs (include ungrouped)
            val groupIds = groupsManager.getGroupIds()

            groupIds.forEachIndexed { index, groupId ->
                val isLast = index == groupIds.lastIndex
                val label = prefs.getGroupLabel(groupId)

                val radio = RadioButton(context).apply {
                    id = generateViewId()
                    text = label
                    isChecked = groupId == currentGroupId
                    layoutParams = RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.MATCH_PARENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = if (isLast) 0 else spToPx(context, 8f)
                    }
                }

                ThemeManager.applyTheme(context, radio)

                radio.setOnClickListener {
                    prefs.setAppGroupId(app, groupId)
                    refresh()
                    dialog.dismiss()
                }

                radioGroup.addView(radio)
            }

            container.addView(radioGroup)
        }

        renderGroups()

        closeBtn.setOnClickListener { dialog.dismiss() }

        ThemeManager.applyTheme(context, view)
        dialog.show()
    }

    private fun getSelectionKey(app: AppsProvider.AppEntry): String =
        PrefsManager.FileKeys.appKey(app)

    private fun setupMultiSelectBar() {
        val root = recyclerView.rootView
        multiSelectBar = root.findViewById(R.id.menu_bar)
        selectedCountText = root.findViewById(R.id.selected_count)
        renameButton = root.findViewById(R.id.rename_btn)
        appSettingsButton = root.findViewById(R.id.app_settings)
        appSettingsIcon = root.findViewById(R.id.app_settings_icon)

        val moveButton = root.findViewById<FrameLayout>(R.id.move_to_group_button)
        val cancelButton = root.findViewById<FrameLayout>(R.id.multi_select_cancel_button)

        moveButton.setOnClickListener { showBatchGroupsDialog() }
        cancelButton.setOnClickListener { exitMultiSelectMode() }

        renameButton?.setOnClickListener {
            getSingleSelectedApp()?.let { app ->
                exitMultiSelectMode()
                showRenameDialog(app)
            }
        }

        appSettingsButton?.setOnClickListener {
            getSingleSelectedApp()?.let { app ->
                exitMultiSelectMode()
                openAppSettings(app)
            }
        }
    }

    private fun getSingleSelectedApp(): AppsProvider.AppEntry? {
        if (selectedApps.size != 1) return null
        val key = selectedApps.first()
        return allAppsCache.find { getSelectionKey(it) == key }
    }

    private fun enterMultiSelectMode(app: AppsProvider.AppEntry) {
        isMultiSelectMode = true
        selectedApps.clear()
        selectedApps.add(getSelectionKey(app))
        multiSelectBar?.visibility = View.VISIBLE
        updateMultiSelectBar()
        notifyAdapters()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedApps.clear()
        multiSelectBar?.visibility = View.GONE
        notifyAdapters()
    }

    private fun toggleSelection(app: AppsProvider.AppEntry) {
        val key = getSelectionKey(app)
        if (key in selectedApps) {
            selectedApps.remove(key)
            if (selectedApps.isEmpty()) {
                exitMultiSelectMode()
                return
            }
        } else {
            selectedApps.add(key)
        }
        updateMultiSelectBar()
        notifyAdapters()
    }

    private fun updateMultiSelectBar() {
        selectedCountText?.text = context.getString(
            R.string.multi_select_count,
            selectedApps.size
        )
        val selectedApp = getSingleSelectedApp()
        val isSingle = selectedApp != null
        renameButton?.visibility = if (isSingle) View.VISIBLE else View.GONE
        appSettingsButton?.visibility = if (isSingle) View.VISIBLE else View.GONE
        appSettingsIcon?.contentDescription = context.getString(
            if (selectedApp is AppsProvider.ShortcutEntry && selectedApp.isPinned) {
                R.string.btn_remove_shortcut
            } else {
                R.string.ctxmenu_open_settings
            }
        )
    }

    private fun showBatchGroupsDialog() {
        val view = View.inflate(context, R.layout.dialog_groups_pick, null)

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        val closeBtn = view.findViewById<View>(R.id.close_button)
        val container = view.findViewById<RadioGroup>(R.id.groups)

        fun renderGroups() {
            container.removeAllViews()
            val radioGroup = RadioGroup(context)

            val groupIds = groupsManager.getGroupIds()

            groupIds.forEachIndexed { index, groupId ->
                val isLast = index == groupIds.lastIndex
                val label = prefs.getGroupLabel(groupId)

                val radio = RadioButton(context).apply {
                    id = generateViewId()
                    text = label
                    layoutParams = RadioGroup.LayoutParams(
                        RadioGroup.LayoutParams.MATCH_PARENT,
                        RadioGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = if (isLast) 0 else spToPx(context, 8f)
                    }
                }

                ThemeManager.applyTheme(context, radio)

                radio.setOnClickListener {
                    batchAssignToGroup(groupId)
                    dialog.dismiss()
                }

                radioGroup.addView(radio)
            }

            container.addView(radioGroup)
        }

        renderGroups()
        closeBtn.setOnClickListener { dialog.dismiss() }

        ThemeManager.applyTheme(context, view)
        dialog.show()
    }

    private fun batchAssignToGroup(groupId: String) {
        for (key in selectedApps) {
            val app = allAppsCache.find { getSelectionKey(it) == key }
            if (app != null) {
                prefs.setAppGroupId(app, groupId)
            }
        }
        exitMultiSelectMode()
        refresh()
    }

    private fun computeColumnCount(): Int {
        if (!prefs.isMultiColumnEnabled()) return 1
        return context.resources.getInteger(R.integer.app_list_column_count)
    }

    private fun arrangeItemsForColumns(source: List<ListItem>): List<ListItem> {
        val columnCount = computeColumnCount()
        if (columnCount == 1) return source.toList()

        val columns = if (prefs.hasGroupHeaders()) {
            splitItemsByGroupBlocks(source, columnCount)
        } else {
            splitItemsEvenly(source, columnCount)
        }
        val rowCount = columns.maxOfOrNull { it.size } ?: 0

        return buildList(rowCount * columnCount) {
            repeat(rowCount) { row ->
                repeat(columnCount) { column ->
                    add(columns[column].getOrNull(row) ?: ListItem.Empty)
                }
            }
        }
    }

    private fun splitItemsByGroupBlocks(
        source: List<ListItem>,
        columnCount: Int
    ): List<List<ListItem>> {
        val blocks = mutableListOf<List<ListItem>>()
        var currentBlock = mutableListOf<ListItem>()

        source.forEach { item ->
            if (item is ListItem.Header && currentBlock.isNotEmpty()) {
                blocks.add(currentBlock)
                currentBlock = mutableListOf()
            }
            currentBlock.add(item)
        }
        if (currentBlock.isNotEmpty()) blocks.add(currentBlock)

        val columns = List(columnCount) { mutableListOf<ListItem>() }
        if (blocks.isEmpty()) return columns

        val blocksPerColumn = ceil(blocks.size.toFloat() / columnCount).toInt()
        blocks.forEachIndexed { index, block ->
            val column = (index / blocksPerColumn).coerceAtMost(columnCount - 1)
            columns[column].addAll(block)
        }
        return columns
    }

    private fun splitItemsEvenly(
        source: List<ListItem>,
        columnCount: Int
    ): List<List<ListItem>> {
        val itemsPerColumn = ceil(source.size.toFloat() / columnCount).toInt()
        return List(columnCount) { column ->
            val start = (column * itemsPerColumn).coerceAtMost(source.size)
            val end = (start + itemsPerColumn).coerceAtMost(source.size)
            source.subList(start, end)
        }
    }

    private fun configureRecyclerView() {
        layoutManager = GridLayoutManager(context, computeColumnCount())
        recyclerView.layoutManager = layoutManager
        recyclerView.layoutDirection = if (prefs.isRtlRowEnabled()) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        recyclerView.adapter = adapter
    }

    private fun notifyAdapters() {
        adapter.notifyDataSetChanged()
    }

    private inner class AppAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val adapterItems = mutableListOf<ListItem>()

        override fun getItemCount(): Int = adapterItems.size

        override fun getItemViewType(position: Int): Int {
            return when (adapterItems[position]) {
                is ListItem.Header -> VIEW_TYPE_GROUP_HEADER
                is ListItem.App -> VIEW_TYPE_APP
                ListItem.Empty -> VIEW_TYPE_EMPTY
            }
        }

        fun updateItems(newItems: List<ListItem>) {
            adapterItems.clear()
            adapterItems.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_GROUP_HEADER -> GroupHeaderViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.app_list_header, parent, false)
                )

                VIEW_TYPE_EMPTY -> EmptyViewHolder(Space(parent.context).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })

                else -> AppViewHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.list_item_app, parent, false)
                )
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = adapterItems[position]) {
                is ListItem.Header -> bindGroupHeader(holder as GroupHeaderViewHolder, item)
                is ListItem.App -> bindApp(holder as AppViewHolder, item.info)
                ListItem.Empty -> Unit
            }
        }
    }

    private class GroupHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.header_text)
    }

    private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.open_app_button)
        val emptySpace: View = view.findViewById(R.id.empty_space)
        val selectionCheck: ImageView = view.findViewById(R.id.selection_check)
        val icon: ImageView = view.findViewById(R.id.app_icon)
    }

    private fun bindGroupHeader(holder: GroupHeaderViewHolder, item: ListItem.Header) {
        val isRtl = prefs.isRtlRowEnabled()
        holder.itemView.layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        holder.text.layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        holder.text.textAlignment = View.TEXT_ALIGNMENT_VIEW_START

        val pinned = prefs.isGroupKeepExpanded(item.id)
        val collapsible = prefs.hasCollapsibleGroups() && !pinned
        val isExpanded = pinned || (collapsible && prefs.isGroupExpanded(item.id))
        val indicatorSymbol = if (collapsible) {
            context.getString(
                if (isExpanded) {
                    BohioR.string.settings_section_collapse_indicator
                } else {
                    BohioR.string.settings_section_expand_indicator
                }
            )
        } else {
            ""
        }

        val titleText = item.title.uppercase()
        holder.text.text = if (indicatorSymbol.isEmpty()) {
            titleText
        } else if (isRtl) {
            "$titleText $indicatorSymbol"
        } else {
            "$indicatorSymbol $titleText"
        }
        ThemeManager.applyTheme(context, holder.text)
        ViewCompat.setAccessibilityHeading(holder.itemView, true)
        holder.itemView.isFocusable = collapsible

        holder.itemView.setOnClickListener(
            if (collapsible) {
                View.OnClickListener {
                    val position = holder.bindingAdapterPosition
                    val offset = holder.itemView.top - recyclerView.paddingTop

                    val currentlyExpanded = prefs.isGroupExpanded(item.id)
                    val shouldExpand = !currentlyExpanded

                    // Toggle the clicked group
                    prefs.setGroupExpanded(item.id, shouldExpand)

                    // If we are expanding and auto‑collapse is on, collapse all other non‑pinned groups
                    if (shouldExpand && prefs.isAutoCollapseGroupsEnabled()) {
                        val toCollapse = getAllGroupIds().filter {
                            it != item.id && !prefs.isGroupKeepExpanded(it) && prefs.isGroupExpanded(
                                it
                            )
                        }.toSet()
                        if (toCollapse.isNotEmpty()) {
                            prefs.setGroupsExpanded(toCollapse, false)
                        }
                    }

                    refresh()
                    val newPosition =
                        items.indexOfFirst { it is ListItem.Header && it.id == item.id }
                    if (newPosition != -1) {
                        layoutManager.scrollToPositionWithOffset(newPosition, offset)
                    }
                    if (position != RecyclerView.NO_POSITION) {
                        layoutManager.scrollToPositionWithOffset(position, offset)
                    }
                }
            } else {
                null
            }
        )
    }

    private fun bindApp(holder: AppViewHolder, app: AppsProvider.AppEntry) {
        val view = holder.itemView
        val showIcons = prefs.hasIconsVisible()
        renderAppInfo(view, app)
        view.isFocusable = true

        val isRtl = prefs.isRtlRowEnabled()
        view.layoutDirection = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        val fontSize = prefs.getAppFontSize()
        holder.label.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())

        val rowSpacingPx = spToPx(context, prefs.getAppRowSpacing().toFloat())
        holder.label.setPaddingRelative(
            holder.label.paddingStart,
            rowSpacingPx,
            holder.label.paddingEnd,
            rowSpacingPx
        )

        val key = getSelectionKey(app)
        val isSelected = key in selectedApps
        holder.selectionCheck.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        holder.selectionCheck.alpha = if (isSelected) 1f else 0.3f
        view.isSelected = isSelected

        if (showIcons) {
            holder.icon.setImageDrawable(iconManager.getIcon(app))
            holder.icon.visibility = View.VISIBLE
            holder.icon.setOnClickListener { handleIconClick(app) }
            holder.icon.setOnLongClickListener {
                if (isMultiSelectMode) {
                    toggleSelection(app)
                } else {
                    enterMultiSelectMode(app)
                }
                true
            }
        } else {
            holder.icon.visibility = View.GONE
            holder.icon.setImageDrawable(null)
            holder.icon.setOnClickListener(null)
            holder.icon.setOnLongClickListener(null)
        }

        holder.label.text = getDisplayName(app)

        val launchOrToggle = View.OnClickListener {
            if (isMultiSelectMode) {
                toggleSelection(app)
            } else if (!appsProvider.launch(app)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_unable_launch_app),
                    Toast.LENGTH_SHORT
                ).show()
                refresh()
            } else {
                onAppLaunched?.invoke()
            }
        }

        view.setOnClickListener(launchOrToggle)
        holder.label.setOnClickListener(launchOrToggle)
        holder.emptySpace.setOnClickListener(launchOrToggle)

        val selectOnLongPress = View.OnLongClickListener {
            if (isMultiSelectMode) {
                toggleSelection(app)
            } else {
                enterMultiSelectMode(app)
            }
            true
        }
        view.setOnLongClickListener(selectOnLongPress)
        holder.label.setOnLongClickListener(selectOnLongPress)
        holder.emptySpace.setOnLongClickListener {
            if (isMultiSelectMode) {
                toggleSelection(app)
            } else {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            }
            true
        }

        ThemeManager.applyTheme(context, holder.label)
    }

    private sealed class ListItem {
        data class Header(val id: String, val title: String) : ListItem()
        data class App(val info: AppsProvider.AppEntry) : ListItem()
        data object Empty : ListItem()
    }

}