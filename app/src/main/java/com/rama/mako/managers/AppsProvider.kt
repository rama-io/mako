package com.rama.mako.managers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.ChecksSdkIntAtLeast
import java.io.File

class AppsProvider(private val context: Context) {

    sealed class AppEntry(
        val packageName: String,
        val label: String,
        val userHandle: UserHandle,
        val profileInitial: String?
    ) {
        val isWorkProfile: Boolean = userHandle.hashCode() != 0
        val displayLabel: String = label
        open val statusLevel: Int get() = STATUS_NONE
        val isDisabled: Boolean get() = statusLevel == STATUS_DISABLED
        val isNotRunning: Boolean get() = statusLevel == STATUS_NOT_RUNNING
        val hasStatus: Boolean get() = statusLevel != STATUS_NONE
    }

    class ActivityEntry(
        packageName: String,
        label: String,
        userHandle: UserHandle,
        val activityInfo: LauncherActivityInfo,
        profileInitial: String?
    ) : AppEntry(packageName, label, userHandle, profileInitial) {
        // ApplicationInfo.minSdkVersion only exists starting API 24 (N).
        val minSdkVersion: Int
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                activityInfo.applicationInfo.minSdkVersion
            } else 0

        val targetSdkVersion: Int
            get() = activityInfo.applicationInfo.targetSdkVersion

        override val statusLevel: Int
            get() = if (isStopped(activityInfo.applicationInfo)) STATUS_NOT_RUNNING else STATUS_NONE
    }

    class DisabledEntry(
        packageName: String,
        label: String,
        userHandle: UserHandle,
        val componentName: ComponentName,
        val applicationInfo: ApplicationInfo,
        profileInitial: String?
    ) : AppEntry(packageName, label, userHandle, profileInitial) {
        val minSdkVersion: Int
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                applicationInfo.minSdkVersion
            } else 0

        val targetSdkVersion: Int
            get() = applicationInfo.targetSdkVersion

        override val statusLevel: Int get() = STATUS_DISABLED
    }

    class ShortcutEntry(
        packageName: String,
        label: String,
        userHandle: UserHandle,
        profileInitial: String?,
        val shortcutId: String,
        val shortcutInfo: ShortcutInfo,
        val isPinned: Boolean = false
    ) : AppEntry(packageName, label, userHandle, profileInitial)

    companion object {
        const val STATUS_NONE = -1
        const val STATUS_DISABLED = 1
        const val STATUS_NOT_RUNNING = 2

        private fun isStopped(info: ApplicationInfo): Boolean =
            (info.flags and ApplicationInfo.FLAG_STOPPED) != 0
    }

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val iconCache = mutableMapOf<String, Drawable>()
    private val appSizeCache = mutableMapOf<String, Long>()
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager

    fun getAll(includeShortcuts: Boolean = true): List<AppEntry> {
        val realApps = userManager.userProfiles.flatMap { userHandle ->
            val profileInitial = getProfileInitial(userHandle)

            val activities = try {
                launcherApps.getActivityList(null, userHandle)
            } catch (e: SecurityException) {
                emptyList()
            } catch (e: IllegalStateException) {
                emptyList()
            }

            val appEntries = activities.map { info ->
                ActivityEntry(
                    packageName = info.applicationInfo.packageName,
                    label = localizedActivityLabel(info),
                    userHandle = userHandle,
                    activityInfo = info,
                    profileInitial = profileInitial
                )
            }

            if (!includeShortcuts) {
                return@flatMap appEntries
            }

            // Used to prefix shortcut labels with their parent app's name, e.g. "Clock: Timer".
            val appLabelsByPackage = activities
                .groupBy { it.applicationInfo.packageName }
                .mapValues { (_, infos) -> localizedActivityLabel(infos.first()) }

            val shortcutEntries = getShortcutEntries(userHandle, profileInitial, appLabelsByPackage)

            appEntries + shortcutEntries
        }

        return realApps + getDisabledEntries(realApps)
    }

    private fun getDisabledEntries(realApps: List<AppEntry>): List<AppEntry> {
        val pm = context.packageManager
        val me = Process.myUserHandle()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.GET_DISABLED_COMPONENTS)
        } catch (e: Exception) {
            return emptyList()
        }

        val enabledPackages = realApps
            .filterIsInstance<ActivityEntry>()
            .filter { it.userHandle == me }
            .mapTo(HashSet()) { it.packageName }
        val seen = HashSet<String>()
        val profileInitial = getProfileInitial(me)

        return resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val appInfo = activity.applicationInfo ?: return@mapNotNull null
            val unavailable = !activity.enabled || !appInfo.enabled
            if (!unavailable) return@mapNotNull null
            if (appInfo.packageName in enabledPackages) return@mapNotNull null
            if (!seen.add(appInfo.packageName)) return@mapNotNull null

            val label = runCatching { appInfo.loadLabel(pm).toString() }
                .getOrDefault(appInfo.packageName)
            DisabledEntry(
                packageName = appInfo.packageName,
                label = label,
                userHandle = me,
                componentName = ComponentName(activity.packageName, activity.name),
                applicationInfo = appInfo,
                profileInitial = profileInitial
            )
        }
    }

    fun launch(app: AppEntry): Boolean {
        if (app.isDisabled) return false
        return try {
            when (app) {
                is DisabledEntry -> Unit

                is ActivityEntry -> launcherApps.startMainActivity(
                    app.activityInfo.componentName,
                    app.userHandle,
                    null,
                    null
                )

                is ShortcutEntry -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
                    launcherApps.startShortcut(app.shortcutInfo, null, null)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAppDetails(app: ActivityEntry): Boolean {
        return try {
            launcherApps.startAppDetailsActivity(
                app.activityInfo.componentName,
                app.userHandle,
                null,
                null
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getIcon(app: AppEntry): Drawable {
        return iconCache.getOrPut(appCacheKey(app)) {
            when (app) {
                is ActivityEntry ->
                    app.activityInfo.getIcon(context.resources.displayMetrics.densityDpi)

                is DisabledEntry -> runCatching {
                    context.packageManager.getActivityIcon(app.componentName)
                }.recoverCatching {
                    app.applicationInfo.loadIcon(context.packageManager)
                }.getOrDefault(context.packageManager.defaultActivityIcon)

                is ShortcutEntry -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        context.packageManager.defaultActivityIcon
                    } else {
                        launcherApps.getShortcutIconDrawable(
                            app.shortcutInfo,
                            context.resources.displayMetrics.densityDpi
                        ) ?: context.packageManager.defaultActivityIcon
                    }
                }
            }
        }
    }

    fun unpinShortcut(shortcut: ShortcutEntry): Boolean {
        if (!hasShortcutHostPermission()) return false
        return try {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                setPackage(shortcut.packageName)
            }
            val remainingIds = launcherApps
                .getShortcuts(query, shortcut.userHandle)
                .orEmpty()
                .asSequence()
                .map { it.id }
                .filter { it != shortcut.shortcutId }
                .toList()

            launcherApps.pinShortcuts(
                shortcut.packageName,
                remainingIds,
                shortcut.userHandle
            )
            iconCache.remove(appCacheKey(shortcut))
            true
        } catch (e: Exception) {
            false
        }
    }

    // Size on disk of the installed APK(s) (base + splits). Cached since it
    // requires file stat calls and only changes when an app is updated.
    fun getAppSizeBytes(app: AppEntry): Long {
        val key = appCacheKey(app)
        return appSizeCache.getOrPut(key) {
            runCatching {
                val info = when (app) {
                    is ActivityEntry -> app.activityInfo.applicationInfo
                    is DisabledEntry -> app.applicationInfo
                    is ShortcutEntry -> return@runCatching 0L
                }
                var total = File(info.sourceDir).length()
                info.splitSourceDirs?.forEach { split ->
                    total += File(split).length()
                }
                total
            }.getOrDefault(0L)
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun hasShortcutHostPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                launcherApps.hasShortcutHostPermission()

    private fun getShortcutEntries(
        userHandle: UserHandle,
        profileInitial: String?,
        appLabelsByPackage: Map<String, String>
    ): List<ShortcutEntry> {
        if (!hasShortcutHostPermission()) return emptyList()

        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        return runCatching {
            launcherApps.getShortcuts(query, userHandle).orEmpty().map { shortcut ->
                val shortcutId = shortcut.id
                val shortcutLabel = shortcut.shortLabel?.toString()
                    ?.ifBlank { null }
                    ?: shortcut.longLabel?.toString()
                        ?.ifBlank { null }
                    ?: shortcutId
                val appLabel = appLabelsByPackage[shortcut.`package`]
                val combinedLabel = if (appLabel != null) {
                    "$appLabel: $shortcutLabel"
                } else {
                    shortcutLabel
                }
                ShortcutEntry(
                    packageName = shortcut.`package`,
                    label = combinedLabel,
                    userHandle = userHandle,
                    profileInitial = profileInitial,
                    shortcutId = shortcutId,
                    shortcutInfo = shortcut,
                    isPinned = shortcut.isPinned
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun localizedActivityLabel(info: LauncherActivityInfo): String {
        return try {
            val pm = context.packageManager
            val activityInfo = pm.getActivityInfo(info.componentName, 0)
            val appInfo = activityInfo.applicationInfo

            val res = pm.getResourcesForApplication(appInfo)

            val labelRes =
                if (activityInfo.labelRes != 0) activityInfo.labelRes else appInfo.labelRes
            if (labelRes != 0) res.getText(labelRes).toString() else info.label.toString()
        } catch (e: Throwable) {
            info.label.toString()
        }
    }

    private fun appCacheKey(app: AppEntry): String =
        when (app) {
            is ActivityEntry -> "${app.packageName}:${app.userHandle.hashCode()}"
            is DisabledEntry -> "disabled:${app.packageName}:${app.userHandle.hashCode()}"
            is ShortcutEntry ->
                "shortcut:${app.packageName}:${app.shortcutId}:${app.userHandle.hashCode()}"
        }

    fun getPrivateProfileHandle(): UserHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null

        return userManager.userProfiles.firstOrNull { handle ->
            runCatching {
                launcherApps.getLauncherUserInfo(handle)?.userType ==
                        UserManager.USER_TYPE_PROFILE_PRIVATE
            }.getOrDefault(false)
        }
    }

    fun hasPrivateSpace(): Boolean = getPrivateProfileHandle() != null

    fun isPrivateSpaceLocked(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null

        val handle = getPrivateProfileHandle() ?: return null
        return userManager.isQuietModeEnabled(handle)
    }

    fun setPrivateSpaceLocked(locked: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false

        val handle = getPrivateProfileHandle() ?: return false

        return try {
            userManager.requestQuietModeEnabled(locked, handle)
        } catch (e: SecurityException) {
            false
        }
    }

    private fun getProfileInitial(userHandle: UserHandle): String? {
        if (userHandle.hashCode() == 0) return null
        return context.packageManager.getUserBadgedLabel("", userHandle).toString()
            .trim()
            .firstOrNull { it.isLetter() }
            ?.uppercase()
            ?: "E"
    }
}