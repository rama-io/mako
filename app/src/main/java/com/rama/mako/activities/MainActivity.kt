package com.rama.mako.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Space
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.RecyclerView
import com.rama.bohio.R as BohioR
import com.rama.mako.CsActivity
import com.rama.mako.R
import com.rama.mako.managers.AppListManager
import com.rama.mako.managers.AppsProvider
import com.rama.mako.managers.BatteryManager
import com.rama.mako.managers.ClockManager
import com.rama.mako.managers.HomeBackgroundManager
import com.rama.mako.managers.PrefsManager
import com.rama.mako.managers.PrefsManager.FileKeys
import com.rama.bohio.managers.ThemeManager

class MainActivity : CsActivity() {

    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var batteryText: TextView
    private lateinit var appList: RecyclerView
    private lateinit var homeHeader: View

    private lateinit var clockManager: ClockManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var appListManager: AppListManager
    private lateinit var appsProvider: AppsProvider

    private lateinit var homeBackgroundManager: HomeBackgroundManager
    private lateinit var rootView: View

    private lateinit var searchField: EditText
    private lateinit var searchIcon: FrameLayout
    private lateinit var clearBtn: FrameLayout
    private var isSearchBarAlwaysVisible = false

    private var isSearchExpanded = false
    private var isProgrammaticSearchUpdate = false
    private val searchDebounceHandler = Handler(Looper.getMainLooper())
    private var searchDebounceRunnable: Runnable? = null
    private var resumeRefreshRunnable: Runnable? = null
    private var currentSearchQuery: String = ""
    private var wallpaperReceiverRegistered = false
    private var lastAppliedBackgroundMode: String? = null
    private var lastAppliedWallpaperSignature: Int? = null
    private var lastAppliedTheme: String? = null
    private lateinit var lockButton: FrameLayout
    private lateinit var lockButtonIcon: ImageView
    private var lastAppliedLanguage: String? = null

    companion object {
        private const val WALLPAPER_CHANGED_ACTION = "android.intent.action.WALLPAPER_CHANGED"
    }

    private val wallpaperChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == WALLPAPER_CHANGED_ACTION) {
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                )
            }
        }
    }

    private var privacySpaceReceiverRegistered = false

    private val privacySpaceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PROFILE_AVAILABLE,
                Intent.ACTION_PROFILE_UNAVAILABLE -> {
                    appListManager.refresh()
                    updateLockButton()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_F10 -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun updateLockButton() {
        if (appsProvider.hasPrivateSpace()) {
            lockButton.visibility = View.VISIBLE

            val isLocked = appsProvider.isPrivateSpaceLocked() ?: return

            lockButtonIcon.setImageResource(
                if (isLocked) BohioR.drawable.px_lock
                else BohioR.drawable.px_lock_open
            )

        } else {
            lockButton.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsManager.getInstance(this).initPrefs()
        setContentView(R.layout.view_home)

        rootView = findViewById(R.id.root)
        applyEdgeToEdgePadding(rootView)
        applyCurrentTheme(rootView)
        rootView.isFocusableInTouchMode = false
        rootView.requestFocus()
        val palette = ThemeManager.paletteFor(prefs.getTheme())

        homeBackgroundManager = HomeBackgroundManager(this)
        applyHomeBackground(force = true)

        appList = findViewById(R.id.app_list)
        homeHeader = findViewById(R.id.apps_layout)

        timeText = homeHeader.findViewById(R.id.time)
        dateText = homeHeader.findViewById(R.id.date)
        batteryText = homeHeader.findViewById(R.id.battery)

        clockManager = ClockManager(timeText, dateText, this)
        clockManager.start()
        timeText.setOnClickListener { openSystemClock() }
        dateText.setOnClickListener { openDateApp() }

        batteryManager = BatteryManager(
            context = this,
            callback = { status -> batteryText.text = status },
        )
        batteryManager.register()

        appsProvider = AppsProvider(this)
        appListManager = AppListManager(
            this,
            appList,
            appsProvider
        ) {
            if (isSearchExpanded) {
                collapseSearch()
            }
        }
        appListManager.setup()

        lockButton = homeHeader.findViewById(R.id.lock_btn)
        lockButtonIcon = lockButton.findViewById(R.id.lock_icon)

        updateLockButton()

        lockButton.setOnClickListener {
            val isLocked = appsProvider.isPrivateSpaceLocked() ?: return@setOnClickListener

            if (appsProvider.setPrivateSpaceLocked(!isLocked)) {
                updateLockButton()
            }
        }

        timeText.setTextColor(palette.h1)

        val openSettingsOnLongPress = View.OnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        homeHeader.setOnLongClickListener(openSettingsOnLongPress)

        val emptySpaceLongPressDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean = true

                override fun onLongPress(event: MotionEvent) {
                    val child = appList.findChildViewUnder(event.x, event.y)
                    if (child == null || child is Space) {
                        appList.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                }
            }
        )
        appList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(view: RecyclerView, event: MotionEvent): Boolean {
                emptySpaceLongPressDetector.onTouchEvent(event)
                return false
            }
        })

        initSearchbar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (appListManager.handleBackPress()) return

                if (isSearchBarAlwaysVisible) {
                    if (searchField.hasFocus()) {
                        searchField.clearFocus()
                        return
                    }
                } else if (isSearchExpanded) {
                    collapseSearch()
                    return
                }

                if (prefs.getBoolean(FileKeys.GROUPS_COLLAPSIBLE, true)) {
                    appListManager.collapseAllGroups()
                }
            }
        })

    }

    private fun initSearchbar() {
        searchField = findViewById(R.id.search_field)
        searchIcon = homeHeader.findViewById(R.id.search_icon)
        clearBtn = findViewById(R.id.clear_field)

        searchField.visibility = View.GONE
        clearBtn.visibility = View.GONE

        searchIcon.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            if (isSearchExpanded) {
                collapseSearch()
            } else {
                expandSearch()
            }
        }

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticSearchUpdate) return

                val query = s.toString()

                searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }

                searchDebounceRunnable = Runnable {
                    currentSearchQuery = query
                    appListManager.filter(currentSearchQuery)
                }
                searchDebounceHandler.postDelayed(searchDebounceRunnable!!, 300)

                clearBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        clearBtn.setOnClickListener {
            currentSearchQuery = ""
            searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
            isProgrammaticSearchUpdate = true
            searchField.text.clear()
            isProgrammaticSearchUpdate = false
            appListManager.filter("")
            clearBtn.visibility = View.GONE
        }
    }

    private fun expandSearch() {
        isSearchExpanded = true

        searchField.visibility = View.VISIBLE
        if (!isSearchBarAlwaysVisible)
            searchField.requestFocus()

        val scaleX = ObjectAnimator.ofFloat(searchField, "scaleX", 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(searchField, "scaleY", 0.8f, 1f)
        val alpha = ObjectAnimator.ofFloat(searchField, "alpha", 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 300
            interpolator = OvershootInterpolator(1.5f)
            start()
        }

        val imm =
            getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(searchField, 0)
    }

    private fun collapseSearch(clearQuery: Boolean = true, hideKeyboard: Boolean = true) {
        isSearchExpanded = false

        searchField.visibility = View.GONE
        clearBtn.visibility = View.GONE
        searchField.clearFocus()

        if (clearQuery) {
            currentSearchQuery = ""
            searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }
            isProgrammaticSearchUpdate = true
            searchField.text.clear()
            isProgrammaticSearchUpdate = false
            appListManager.filter("")
        }

        if (hideKeyboard) {
            val imm =
                getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(searchField.windowToken, 0)
        }
    }


    override fun onResume() {
        super.onResume()
        registerWallpaperReceiverIfNeeded()
        registerPrivacySpaceReceiverIfNeeded()

        val currentLanguage = prefs.getAppLanguage()
        if (lastAppliedLanguage != null && lastAppliedLanguage != currentLanguage) {
            recreate()
            return
        }

        applyHomeBackground(force = true)
        syncSettings()

        val groupsWereCollapsed = prefs.shouldCollapseGroupsOnHomeFocus() &&
                appListManager.collapseAllGroups()

        schedulePostResumeRefresh(skipAppListRefresh = groupsWereCollapsed)

        if (isSearchBarAlwaysVisible) {
            expandSearch()
        } else if (isSearchExpanded) {
            collapseSearch()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterWallpaperReceiverIfNeeded()
        unregisterPrivacySpaceReceiverIfNeeded()
        clearPendingResumeRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterWallpaperReceiverIfNeeded()
        unregisterPrivacySpaceReceiverIfNeeded()
        clearPendingResumeRefresh()

        searchDebounceRunnable?.let { searchDebounceHandler.removeCallbacks(it) }

        batteryManager.unregister()
        clockManager.stop()
        if (::appListManager.isInitialized) {
            appListManager.unregisterLauncherAppsCallback()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    private fun syncSettings() {
        val searchVisible = prefs.isSearchVisible()

        isSearchBarAlwaysVisible = prefs.isSearchBarAlwaysVisible()
        timeText.visibility =
            if (prefs.getClockFormat() != PrefsManager.ClockFormat.NONE) View.VISIBLE else View.GONE
        homeHeader.findViewById<View>(R.id.date_row).visibility =
            if (prefs.isDateVisible()) View.VISIBLE else View.GONE
        homeHeader.findViewById<View>(R.id.battery_row).visibility =
            if (prefs.isBatteryVisible()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.searchbar).visibility =
            if (searchVisible) View.VISIBLE else View.GONE
        searchIcon.visibility =
            if (searchVisible && !isSearchBarAlwaysVisible) View.VISIBLE else View.GONE
    }

    private fun resolveConfiguredApp(storedValue: String): AppsProvider.AppEntry? {
        if (storedValue.isEmpty()) return null
        val allApps = appsProvider.getAll()
        return allApps.firstOrNull { PrefsManager.FileKeys.appKey(it) == storedValue }
            ?: allApps.filterIsInstance<AppsProvider.ActivityEntry>()
                .firstOrNull { it.packageName == storedValue }
    }

    // --- Open date app ---
    private fun openDateApp() {
        val packageName = prefs.getDateApp()
        if (packageName.isNotEmpty()) {
            val app = resolveConfiguredApp(packageName)
            if (app != null) {
                if (!appsProvider.launch(app)) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_unable_launch_app),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
    }

    // --- Open system clock safely ---
    private fun openSystemClock() {
        val packageName = prefs.getClockApp()
        if (packageName.isNotEmpty()) {
            val app = resolveConfiguredApp(packageName)
            if (app != null) {
                if (!appsProvider.launch(app)) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_unable_launch_app),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
        val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    // The only place in the app where FLAG_SHOW_WALLPAPER is ever set.
    // All other activities get bg_1 via CsActivity.applyWindowBackground().
    private fun applyHomeBackground(force: Boolean = false) {
        val mode = prefs.getHomeBackgroundMode()
        val wallpaperSignature = homeBackgroundManager.getWallpaperSignature()
        val theme = prefs.getTheme()

        if (!force &&
            mode == lastAppliedBackgroundMode &&
            wallpaperSignature == lastAppliedWallpaperSignature &&
            theme == lastAppliedTheme
        ) return

        if (mode == PrefsManager.BackgroundMode.WALLPAPER) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.navigationBarColor = Color.TRANSPARENT
            rootView.background = homeBackgroundManager.createWallpaperOverlayDrawable()
        } else {
            applyWindowBackground()
            homeBackgroundManager.applyTo(rootView, mode)
        }

        rootView.invalidate()
        rootView.requestLayout()

        lastAppliedBackgroundMode = mode
        lastAppliedWallpaperSignature = wallpaperSignature
        lastAppliedTheme = theme
    }

    private fun schedulePostResumeRefresh(skipAppListRefresh: Boolean = false) {
        clearPendingResumeRefresh()

        resumeRefreshRunnable = Runnable {
            if (isFinishing || isDestroyed) return@Runnable
            if (!skipAppListRefresh) appListManager.refresh(reloadApps = false)
            batteryManager.forceUpdate()
        }

        rootView.post(resumeRefreshRunnable)
    }

    private fun clearPendingResumeRefresh() {
        resumeRefreshRunnable?.let {
            rootView.removeCallbacks(it)
        }
        resumeRefreshRunnable = null
    }

    private fun registerWallpaperReceiverIfNeeded() {
        if (wallpaperReceiverRegistered) return

        val filter = IntentFilter(WALLPAPER_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                wallpaperChangedReceiver,
                filter,
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(wallpaperChangedReceiver, filter)
        }

        wallpaperReceiverRegistered = true
    }

    private fun unregisterWallpaperReceiverIfNeeded() {
        if (!wallpaperReceiverRegistered) return

        runCatching { unregisterReceiver(wallpaperChangedReceiver) }
        wallpaperReceiverRegistered = false
    }

    private fun registerPrivacySpaceReceiverIfNeeded() {
        if (privacySpaceReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                privacySpaceReceiver,
                filter,
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(privacySpaceReceiver, filter)
        }

        privacySpaceReceiverRegistered = true
    }

    private fun unregisterPrivacySpaceReceiverIfNeeded() {
        if (!privacySpaceReceiverRegistered) return

        runCatching { unregisterReceiver(privacySpaceReceiver) }
        privacySpaceReceiverRegistered = false
    }
}
