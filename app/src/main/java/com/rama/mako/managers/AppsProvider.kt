package com.rama.mako.managers

import android.content.pm.PackageManager
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
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

        return realApps
    }

    fun launch(app: AppEntry): Boolean {
        return try {
            when (app) {
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
    fun getAppSizeBytes(app: ActivityEntry): Long {
        val key = appCacheKey(app)
        return appSizeCache.getOrPut(key) {
            runCatching {
                val info = app.activityInfo.applicationInfo
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