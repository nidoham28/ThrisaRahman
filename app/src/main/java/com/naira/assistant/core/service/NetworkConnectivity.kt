@file:Suppress("DEPRECATION")

package com.naira.assistant.core.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

private const val TAG = "Connectivity"

/**
 * Whether the device currently has a validated internet connection.
 *
 * "Validated" means Android's captive-portal check succeeded: the network
 * can actually reach the internet, not just that Wi-Fi is associated. This
 * is the correct signal for "should I make a network request right now" —
 * an unvalidated Wi-Fi (hotel / airport / café captive portal) reports
 * `NET_CAPABILITY_INTERNET` but will block your HTTP call until the user
 * logs into the portal, so you want `false` here in that case.
 *
 * ### What this checks
 * Only the **active network** (the one the system will use for the next
 * outgoing request). If the active network is unvalidated but a fallback
 * network (e.g. cellular while Wi-Fi is stuck on a captive portal) is
 * validated, this returns `false`. If you need "is *any* usable network
 * available?", use [hasAnyValidatedNetwork] instead.
 *
 * ### Requirements
 * - **Manifest permission:** `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.
 *   Without it, `getActiveNetwork()` throws `SecurityException` on API 31+.
 * - **API level:** `NET_CAPABILITY_VALIDATED` exists on API 23+. On API 21–22,
 *   this returns `false` always. If your `minSdk < 23`, use [hasAnyValidatedNetwork]
 *   or gate the call.
 *
 * ### Do not poll this in a composable
 * Validation lags by ~1–2 s after a network appears (the captive-portal
 * check has to run). Polling this every frame will produce visible flicker
 * between "offline" and "online" during that window. For reactive UI, use
 * `ConnectivityManager.registerNetworkCallback()` with a `callbackFlow`.
 *
 * ### Edge cases intentionally returning `false`
 * - No active network (airplane mode, no signal).
 * - Wi-Fi associated but captive portal not yet logged into.
 * - Connected just now, before validation completes.
 * - Data Saver / background restriction blocking the default network.
 * - Missing `ACCESS_NETWORK_STATE` (defensive fallback).
 */
fun hasValidatedInternet(context: Context): Boolean {

    val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false

    // `getActiveNetwork()` / `getNetworkCapabilities()` require
    // ACCESS_NETWORK_STATE. It's a normal permission granted at install time,
    // but a manifest misconfiguration would throw SecurityException here
    // rather than return null — guard against that so a broken manifest
    // surfaces as "offline" instead of a crash.
    val capabilities = try {
        val network = manager.activeNetwork ?: return false
        manager.getNetworkCapabilities(network)
    } catch (e: SecurityException) {
        Log.e(TAG, "ACCESS_NETWORK_STATE not granted; cannot check connectivity", e)
        return false
    } ?: return false

    return capabilities.isValidatedForRequests()
}

/**
 * Whether **any** network on the device is validated and usable for requests.
 *
 * Use this when the question is "could a request succeed *somewhere*", not
 * "would a request succeed *right now*". Typical scenario: the device has
 * unvalidated Wi-Fi (stuck on a captive portal) but valid cellular. The
 * system's default route goes to Wi-Fi ([hasValidatedInternet] returns
 * `false`), but a request that binds to cellular would succeed.
 *
 * Slightly more expensive than [hasValidatedInternet] — iterates all networks.
 * Still sub-millisecond for typical configurations (1–3 networks).
 */
fun hasAnyValidatedNetwork(context: Context): Boolean {
    val manager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false

    val networks = try {
        manager.allNetworks
    } catch (e: SecurityException) {
        Log.e(TAG, "ACCESS_NETWORK_STATE not granted; cannot check connectivity", e)
        return false
    }

    return networks.any { network ->
        try {
            manager.getNetworkCapabilities(network)?.isValidatedForRequests() == true
        } catch (_: SecurityException) {
            false
        }
    }
}

/**
 * True when [this] has both INTERNET and (on API 23+) VALIDATED capability.
 *
 * - `INTERNET` on its own is insufficient: captive portals advertise it.
 * - `VALIDATED` (API 23+) means Android's captive-portal check succeeded.
 * - On API < 23, `VALIDATED` doesn't exist; we fall back to INTERNET-only,
 *   which is the best signal those platforms offer.
 */
private fun NetworkCapabilities.isValidatedForRequests(): Boolean {
    return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}