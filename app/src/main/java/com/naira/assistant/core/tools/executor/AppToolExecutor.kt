package com.naira.assistant.core.tools.executor

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.naira.assistant.core.tools.executor.BuiltinToolExecutor.jsonStringArg
import com.naira.assistant.domain.FunctionCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class InstalledAppInfo(
    val name: String,
    val packageName: String,
    val isSystem: Boolean,
    val canLaunch: Boolean,
)

object AppToolExecutor {

    /**
     * Executes the `list_installed_apps` tool.
     */
    fun executeListInstalledApps(call: FunctionCall, context: Context): JsonObject {
        val query = jsonStringArg(call, "query")?.trim()?.lowercase()
        val includeSystem = extractBooleanArg(call, "include_system_apps") ?: false
        val limit = (extractIntArg(call, "limit") ?: 50).coerceIn(1, 100)

        val apps = getInstalledApps(context, includeSystem = includeSystem)
        val filtered = if (!query.isNullOrBlank()) {
            apps.filter {
                it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        } else {
            apps
        }.take(limit)

        return buildJsonObject {
            put("success", true)
            put("action", "list_apps")
            put("query", query ?: "")
            put("total_found", filtered.size)
            put("apps", buildJsonArray {
                filtered.forEach { app ->
                    add(buildJsonObject {
                        put("name", app.name)
                        put("package_name", app.packageName)
                        put("is_system", app.isSystem)
                        put("can_launch", app.canLaunch)
                    })
                }
            })
        }
    }

    /**
     * Executes the `check_app_installed` tool.
     */
    fun executeCheckAppInstalled(call: FunctionCall, context: Context): JsonObject {
        val appName = jsonStringArg(call, "app_name")?.trim()
        val packageName = jsonStringArg(call, "package_name")?.trim()

        if (appName.isNullOrBlank() && packageName.isNullOrBlank()) {
            return error("Please provide at least 'app_name' or 'package_name' to check.")
        }

        val pm = context.packageManager

        // 1. Check by direct package name if provided
        if (!packageName.isNullOrBlank()) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val canLaunch = pm.getLaunchIntentForPackage(packageName) != null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                return buildJsonObject {
                    put("success", true)
                    put("installed", true)
                    put("app_name", label)
                    put("package_name", packageName)
                    put("is_system", isSystem)
                    put("can_launch", canLaunch)
                    put("message", "App '$label' ($packageName) is installed on this device.")
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // Not found by exact package; fall through if appName is available
                if (appName.isNullOrBlank()) {
                    return buildJsonObject {
                        put("success", true)
                        put("installed", false)
                        put("package_name", packageName)
                        put("message", "App with package '$packageName' is not installed.")
                    }
                }
            }
        }

        // 2. Search by app name across installed apps
        val targetName = appName.orEmpty()
        val installedApps = getInstalledApps(context, includeSystem = true)
        val matched = findBestAppMatch(installedApps, targetName)

        return if (matched != null) {
            buildJsonObject {
                put("success", true)
                put("installed", true)
                put("app_name", matched.name)
                put("package_name", matched.packageName)
                put("is_system", matched.isSystem)
                put("can_launch", matched.canLaunch)
                put("message", "App '${matched.name}' (${matched.packageName}) is installed on this device.")
            }
        } else {
            buildJsonObject {
                put("success", true)
                put("installed", false)
                put("app_name", targetName)
                put("message", "No installed app found matching '$targetName'.")
            }
        }
    }

    /**
     * Executes the `open_app` tool.
     */
    fun executeOpenApp(call: FunctionCall, context: Context): JsonObject {
        val appName = jsonStringArg(call, "app_name")?.trim()
        val packageName = jsonStringArg(call, "package_name")?.trim()

        if (appName.isNullOrBlank() && packageName.isNullOrBlank()) {
            return error("Please provide the 'app_name' or 'package_name' of the app you want to open.")
        }

        val pm = context.packageManager

        // 1. Try launching by exact package name first
        if (!packageName.isNullOrBlank()) {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val label = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    packageName
                }
                return buildJsonObject {
                    put("success", true)
                    put("action", "opened")
                    put("app_name", label)
                    put("package_name", packageName)
                    put("message", "Opened $label successfully.")
                }
            } else if (appName.isNullOrBlank()) {
                return error("App with package '$packageName' is not installed or has no launchable screen.")
            }
        }

        // 2. Search for the app by name among launchable apps
        val targetName = appName.orEmpty()
        val launchableApps = getInstalledApps(context, includeSystem = false)
        val matched = findBestAppMatch(launchableApps, targetName)

        if (matched == null) {
            return error("Could not find an installed app matching '$targetName'. Call 'list_installed_apps' to see available apps.")
        }

        val launchIntent = pm.getLaunchIntentForPackage(matched.packageName)
        if (launchIntent == null) {
            return error("Found '${matched.name}', but it cannot be opened directly from the launcher.")
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        return buildJsonObject {
            put("success", true)
            put("action", "opened")
            put("app_name", matched.name)
            put("package_name", matched.packageName)
            put("message", "Opened ${matched.name} successfully.")
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    fun getInstalledApps(context: Context, includeSystem: Boolean): List<InstalledAppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<InstalledAppInfo>()
        val seenPackages = mutableSetOf<String>()

        if (!includeSystem) {
            // Retrieve only launchable activities
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (seenPackages.add(pkg)) {
                    val label = info.loadLabel(pm).toString().trim().ifBlank { pkg }
                    val isSystem = (info.activityInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                    result.add(
                        InstalledAppInfo(
                            name = label,
                            packageName = pkg,
                            isSystem = isSystem,
                            canLaunch = true
                        )
                    )
                }
            }
        } else {
            // Retrieve all installed applications
            val allApps = pm.getInstalledApplications(0)
            for (appInfo in allApps) {
                val pkg = appInfo.packageName
                if (seenPackages.add(pkg)) {
                    val label = pm.getApplicationLabel(appInfo).toString().trim().ifBlank { pkg }
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val canLaunch = pm.getLaunchIntentForPackage(pkg) != null
                    result.add(
                        InstalledAppInfo(
                            name = label,
                            packageName = pkg,
                            isSystem = isSystem,
                            canLaunch = canLaunch
                        )
                    )
                }
            }
        }

        return result.sortedBy { it.name.lowercase() }
    }

    fun findBestAppMatch(apps: List<InstalledAppInfo>, target: String): InstalledAppInfo? {
        val clean = target.trim().lowercase()
        if (clean.isBlank()) return null

        // 1. Exact name match (case-insensitive)
        apps.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }

        // 2. Exact package name match
        apps.firstOrNull { it.packageName.equals(clean, ignoreCase = true) }?.let { return it }

        // 3. Name starts with target (e.g. "Google" -> "Google Chrome")
        apps.firstOrNull { it.name.lowercase().startsWith(clean) }?.let { return it }

        // 4. Word boundary match (e.g. "Chrome" in "Google Chrome")
        apps.firstOrNull {
            it.name.lowercase().split("\\s+".toRegex()).any { word -> word == clean }
        }?.let { return it }

        // 5. Name contains target
        apps.firstOrNull { it.name.lowercase().contains(clean) }?.let { return it }

        // 6. Package contains target (e.g. "whatsapp" in "com.whatsapp")
        apps.firstOrNull { it.packageName.lowercase().contains(clean) }?.let { return it }

        return null
    }

    private fun error(message: String): JsonObject = buildJsonObject {
        put("success", false)
        put("error", message)
    }

    private fun extractIntArg(call: FunctionCall, key: String): Int? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.intOrNull
                ?: element.jsonPrimitive.content.trim().toIntOrNull()
        }.getOrNull()

    private fun extractBooleanArg(call: FunctionCall, key: String): Boolean? =
        runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.booleanOrNull
                ?: element.jsonPrimitive.content.trim().lowercase().toBooleanStrictOrNull()
        }.getOrNull()
}
