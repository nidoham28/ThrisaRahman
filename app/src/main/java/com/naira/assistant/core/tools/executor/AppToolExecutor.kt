
package com.naira.assistant.core.tools.executor

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import com.naira.assistant.core.tools.executor.CommandExecution.jsonStringArg
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
    val canLaunch: Boolean
)

object AppToolExecutor {

    private const val DEFAULT_LIMIT = 50
    private const val MAX_LIMIT = 100

    // ---------------------------------------------------------
    // LIST INSTALLED APPS
    // ---------------------------------------------------------

    fun executeListInstalledApps(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val query = jsonStringArg(call, "query")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val includeSystem =
            extractBooleanArg(call, "include_system_apps") ?: false

        val limit = (extractIntArg(call, "limit") ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)

        return try {
            val apps = getInstalledApps(
                context = context,
                includeSystem = includeSystem
            )

            val filtered = if (query != null) {
                apps.filter { app ->
                    app.name.contains(query, ignoreCase = true) ||
                            app.packageName.contains(query, ignoreCase = true)
                }
            } else {
                apps
            }

            val results = filtered.take(limit)

            buildJsonObject {
                put("success", true)
                put("action", "list_apps")
                put("query", query ?: "")
                put("include_system_apps", includeSystem)
                put("total_found", filtered.size)
                put("returned_count", results.size)
                put("limit", limit)

                put("apps", buildJsonArray {
                    results.forEach { app ->
                        add(buildJsonObject {
                            put("name", app.name)
                            put("package_name", app.packageName)
                            put("is_system", app.isSystem)
                            put("can_launch", app.canLaunch)
                        })
                    }
                })
            }
        } catch (e: SecurityException) {
            error(
                "Permission denied while retrieving installed applications."
            )
        } catch (e: Exception) {
            error(
                "Unable to retrieve installed applications: ${e.message ?: "Unknown error"}"
            )
        }
    }

    // ---------------------------------------------------------
    // CHECK APP INSTALLED
    // ---------------------------------------------------------

    fun executeCheckAppInstalled(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val appName = jsonStringArg(call, "app_name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val packageName = jsonStringArg(call, "package_name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (appName == null && packageName == null) {
            return error(
                "Provide at least an app_name or package_name."
            )
        }

        val pm = context.packageManager

        // Exact package name takes priority.
        if (packageName != null) {
            return try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo)
                    .toString()
                    .ifBlank { packageName }

                val canLaunch = pm.getLaunchIntentForPackage(packageName) != null

                val isSystem = isSystemApplication(appInfo)

                buildJsonObject {
                    put("success", true)
                    put("installed", true)
                    put("app_name", label)
                    put("package_name", packageName)
                    put("is_system", isSystem)
                    put("can_launch", canLaunch)
                    put(
                        "message",
                        "App '$label' ($packageName) is installed on this device."
                    )
                }
            } catch (_: PackageManager.NameNotFoundException) {
                buildJsonObject {
                    put("success", true)
                    put("installed", false)
                    put("package_name", packageName)
                    put(
                        "message",
                        "No visible installed application was found with package '$packageName'."
                    )
                }
            } catch (e: SecurityException) {
                error(
                    "Permission denied while checking package '$packageName'."
                )
            } catch (e: Exception) {
                error(
                    "Unable to check application: ${e.message ?: "Unknown error"}"
                )
            }
        }

        // Search by display name.
        return try {
            val apps = getInstalledApps(
                context = context,
                includeSystem = true
            )

            val matched = findBestAppMatch(apps, appName.orEmpty())

            if (matched != null) {
                buildJsonObject {
                    put("success", true)
                    put("installed", true)
                    put("app_name", matched.name)
                    put("package_name", matched.packageName)
                    put("is_system", matched.isSystem)
                    put("can_launch", matched.canLaunch)
                    put(
                        "message",
                        "App '${matched.name}' (${matched.packageName}) is installed on this device."
                    )
                }
            } else {
                buildJsonObject {
                    put("success", true)
                    put("installed", false)
                    put("app_name", appName.orEmpty())
                    put(
                        "message",
                        "No visible installed application matching '${appName.orEmpty()}' was found."
                    )
                }
            }
        } catch (e: SecurityException) {
            error(
                "Permission denied while searching installed applications."
            )
        } catch (e: Exception) {
            error(
                "Unable to check application: ${e.message ?: "Unknown error"}"
            )
        }
    }

    // ---------------------------------------------------------
    // OPEN APP
    // ---------------------------------------------------------

    fun executeOpenApp(
        call: FunctionCall,
        context: Context
    ): JsonObject {
        val appName = jsonStringArg(call, "app_name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val packageName = jsonStringArg(call, "package_name")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        if (appName == null && packageName == null) {
            return error(
                "Provide an app_name or package_name for the application to open."
            )
        }

        val pm = context.packageManager

        // Exact package name takes priority.
        if (packageName != null) {
            return try {
                val appInfo = pm.getApplicationInfo(packageName, 0)

                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                    ?: return error(
                        "The application '$packageName' is installed but has no launchable activity."
                    )

                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)

                val label = pm.getApplicationLabel(appInfo)
                    .toString()
                    .ifBlank { packageName }

                openedResult(
                    name = label,
                    packageName = packageName
                )
            } catch (_: PackageManager.NameNotFoundException) {
                error(
                    "No visible installed application was found with package '$packageName'."
                )
            } catch (_: ActivityNotFoundException) {
                error(
                    "The application '$packageName' does not have an available launch activity."
                )
            } catch (e: SecurityException) {
                error(
                    "Permission denied while opening '$packageName'."
                )
            } catch (e: Exception) {
                error(
                    "Unable to open application: ${e.message ?: "Unknown error"}"
                )
            }
        }

        // Find the best matching launchable app by display name.
        return try {
            val apps = getInstalledApps(
                context = context,
                includeSystem = false
            )

            val matched = findBestAppMatch(
                apps = apps,
                target = appName.orEmpty()
            ) ?: return error(
                "Could not find a launchable application matching '${appName.orEmpty()}'."
            )

            val launchIntent = pm.getLaunchIntentForPackage(matched.packageName)
                ?: return error(
                    "Found '${matched.name}', but it has no available launch activity."
                )

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)

            openedResult(
                name = matched.name,
                packageName = matched.packageName
            )
        } catch (_: ActivityNotFoundException) {
            error("The requested application's launch activity was not found.")
        } catch (e: SecurityException) {
            error("Permission denied while opening the application.")
        } catch (e: Exception) {
            error(
                "Unable to open application: ${e.message ?: "Unknown error"}"
            )
        }
    }

    // ---------------------------------------------------------
    // GET INSTALLED APPS
    // ---------------------------------------------------------

    fun getInstalledApps(
        context: Context,
        includeSystem: Boolean
    ): List<InstalledAppInfo> {
        val pm = context.packageManager
        val result = mutableMapOf<String, InstalledAppInfo>()

        if (includeSystem) {
            // Includes all packages visible to this application.
            @Suppress("DEPRECATION")
            val installedApps = pm.getInstalledApplications(0)

            installedApps.forEach { appInfo ->
                val packageName = appInfo.packageName

                val label = runCatching {
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(packageName)
                    .trim()
                    .ifBlank { packageName }

                val canLaunch = runCatching {
                    pm.getLaunchIntentForPackage(packageName) != null
                }.getOrDefault(false)

                result[packageName] = InstalledAppInfo(
                    name = label,
                    packageName = packageName,
                    isSystem = isSystemApplication(appInfo),
                    canLaunch = canLaunch
                )
            }
        } else {
            // Retrieve launcher-visible applications.
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            @Suppress("DEPRECATION")
            val resolveInfos = pm.queryIntentActivities(
                launcherIntent,
                0
            )

            resolveInfos.forEach { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@forEach
                val packageName = activityInfo.packageName

                val appInfo = activityInfo.applicationInfo
                val label = resolveInfo.loadLabel(pm)
                    .toString()
                    .trim()
                    .ifBlank { packageName }

                val isSystem = appInfo?.let {
                    isSystemApplication(it)
                } ?: false

                result[packageName] = InstalledAppInfo(
                    name = label,
                    packageName = packageName,
                    isSystem = isSystem,
                    canLaunch = true
                )
            }
        }

        return result.values.sortedBy {
            it.name.lowercase()
        }
    }

    // ---------------------------------------------------------
    // APP MATCHING
    // ---------------------------------------------------------

    fun findBestAppMatch(
        apps: List<InstalledAppInfo>,
        target: String
    ): InstalledAppInfo? {
        val clean = target.trim().lowercase()

        if (clean.isBlank()) return null

        // Exact display-name match.
        apps.firstOrNull {
            it.name.equals(clean, ignoreCase = true)
        }?.let { return it }

        // Exact package-name match.
        apps.firstOrNull {
            it.packageName.equals(clean, ignoreCase = true)
        }?.let { return it }

        // Prefer names beginning with the requested term.
        apps.firstOrNull {
            it.name.startsWith(clean, ignoreCase = true)
        }?.let { return it }

        // Match complete words.
        apps.firstOrNull { app ->
            app.name.lowercase()
                .split(Regex("\\s+"))
                .any { word -> word == clean }
        }?.let { return it }

        // Partial display-name match.
        apps.firstOrNull {
            it.name.contains(clean, ignoreCase = true)
        }?.let { return it }

        // Partial package-name match.
        apps.firstOrNull {
            it.packageName.contains(clean, ignoreCase = true)
        }?.let { return it }

        return null
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------

    private fun isSystemApplication(
        appInfo: ApplicationInfo
    ): Boolean {
        val systemFlags =
            ApplicationInfo.FLAG_SYSTEM or
                    ApplicationInfo.FLAG_UPDATED_SYSTEM_APP

        return (appInfo.flags and systemFlags) != 0
    }

    private fun openedResult(
        name: String,
        packageName: String
    ): JsonObject = buildJsonObject {
        put("success", true)
        put("action", "opened")
        put("app_name", name)
        put("package_name", packageName)
        put("message", "Opened $name successfully.")
    }

    private fun error(message: String): JsonObject =
        buildJsonObject {
            put("success", false)
            put("error", message)
        }

    private fun extractIntArg(
        call: FunctionCall,
        key: String
    ): Int? {
        return runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.intOrNull
                ?: element.jsonPrimitive.content.trim().toIntOrNull()
        }.getOrNull()
    }

    private fun extractBooleanArg(
        call: FunctionCall,
        key: String
    ): Boolean? {
        return runCatching {
            val element = call.args[key] ?: return null
            element.jsonPrimitive.booleanOrNull
                ?: element.jsonPrimitive.content
                    .trim()
                    .lowercase()
                    .toBooleanStrictOrNull()
        }.getOrNull()
    }
}
