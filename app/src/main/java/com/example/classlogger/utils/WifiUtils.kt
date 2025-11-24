package com.example.classlogger.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

class WiFiUtils(private val context: Context) {

    companion object {
        private const val TAG = "WiFiUtils"

        /**
         * Get list of required permissions for WiFi detection
         */
        fun getRequiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
        }
    }

    data class WiFiInfo(
        val ssid: String,
        val bssid: String,
        val isConnected: Boolean
    )

    /**
     * Get current WiFi connection information
     * Requires: ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION permissions
     */
    fun getCurrentWiFiInfo(): WiFiInfo? {
        Log.d(TAG, "=== Getting Current WiFi Info ===")

        // Check permissions first
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions")
            logPermissionStatus()
            return null
        }
        Log.d(TAG, "✓ Permissions granted")

        // Check if location is enabled (required on Android 10+)
        if (!isLocationEnabled()) {
            Log.e(TAG, "Location services are disabled")
            return null
        }
        Log.d(TAG, "✓ Location services enabled")

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            Log.e(TAG, "WiFi Manager is null")
            return null
        }
        Log.d(TAG, "✓ WiFi Manager obtained")

        // Check if WiFi is enabled
        if (!wifiManager.isWifiEnabled) {
            Log.e(TAG, "WiFi is disabled")
            return null
        }
        Log.d(TAG, "✓ WiFi is enabled")

        // Check if connected to WiFi
        if (!isConnectedToWiFi()) {
            Log.e(TAG, "Not connected to WiFi")
            return null
        }
        Log.d(TAG, "✓ Connected to WiFi")

        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo == null) {
            Log.e(TAG, "WiFi connection info is null")
            return null
        }

        // Log raw WiFi info
        Log.d(TAG, "Raw SSID: '${wifiInfo.ssid}'")
        Log.d(TAG, "Raw BSSID: '${wifiInfo.bssid}'")
        Log.d(TAG, "Network ID: ${wifiInfo.networkId}")

        // Clean SSID
        var ssid = wifiInfo.ssid ?: ""

        // Remove quotes if present
        ssid = ssid.replace("\"", "")

        // Check for unknown SSID (happens on Android 10+ without location permission)
        if (ssid == "<unknown ssid>" || ssid.isEmpty()) {
            Log.e(TAG, "SSID is unknown or empty - likely missing location permission or location is off")
            return null
        }

        val bssid = wifiInfo.bssid
        if (bssid == null || bssid == "02:00:00:00:00:00") {
            Log.e(TAG, "BSSID is invalid")
            return null
        }

        Log.d(TAG, "✓ Cleaned SSID: '$ssid'")
        Log.d(TAG, "✓ BSSID: '$bssid'")

        return WiFiInfo(
            ssid = ssid,
            bssid = bssid.lowercase(), // Normalize to lowercase
            isConnected = true
        )
    }

    /**
     * Check if device is connected to WiFi
     */
    private fun isConnectedToWiFi(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected && networkInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * Check if location services are enabled
     * Required for WiFi SSID access on Android 10+
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /**
     * Verify if student/teacher is connected to the correct classroom WiFi
     */
    fun verifyClassroomWiFi(expectedSSID: String, expectedBSSID: String): Boolean {
        Log.d(TAG, "=== Verifying Classroom WiFi ===")
        Log.d(TAG, "Expected SSID: '$expectedSSID'")
        Log.d(TAG, "Expected BSSID: '$expectedBSSID'")

        val currentWiFi = getCurrentWiFiInfo()

        if (currentWiFi == null) {
            Log.e(TAG, "Could not get current WiFi info")
            return false
        }

        Log.d(TAG, "Current SSID: '${currentWiFi.ssid}'")
        Log.d(TAG, "Current BSSID: '${currentWiFi.bssid}'")

        // Clean and normalize expected values
        val cleanExpectedSSID = expectedSSID.trim().replace("\"", "")
        val cleanExpectedBSSID = expectedBSSID.trim().lowercase()

        // Compare
        val ssidMatch = currentWiFi.ssid.equals(cleanExpectedSSID, ignoreCase = true)
        val bssidMatch = currentWiFi.bssid.equals(cleanExpectedBSSID, ignoreCase = true)

        Log.d(TAG, "SSID Match: $ssidMatch")
        Log.d(TAG, "BSSID Match: $bssidMatch")

        val result = ssidMatch && bssidMatch
        Log.d(TAG, "Verification Result: $result")

        return result
    }

    /**
     * Verify WiFi with more lenient matching (SSID only)
     * Use this as fallback when BSSID might not be reliable
     */
    fun verifyClassroomWiFiBySSID(expectedSSID: String): Boolean {
        Log.d(TAG, "=== Verifying Classroom WiFi (SSID only) ===")

        val currentWiFi = getCurrentWiFiInfo() ?: return false
        val cleanExpectedSSID = expectedSSID.trim().replace("\"", "")

        val match = currentWiFi.ssid.equals(cleanExpectedSSID, ignoreCase = true)
        Log.d(TAG, "SSID Match: $match")

        return match
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get detailed permission status (for debugging)
     */
    fun getPermissionStatus(): Map<String, Boolean> {
        val permissions = getRequiredPermissions()
        return permissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Log permission status for debugging
     */
    private fun logPermissionStatus() {
        getPermissionStatus().forEach { (permission, granted) ->
            Log.d(TAG, "Permission $permission: ${if (granted) "✓ GRANTED" else "✗ DENIED"}")
        }
    }

    /**
     * Get a detailed WiFi status message for display to user
     */
    fun getWiFiStatusMessage(): String {
        return when {
            !hasRequiredPermissions() -> {
                "Location permission is required to detect WiFi network"
            }
            !isLocationEnabled() -> {
                "Please enable Location services in your device settings"
            }
            else -> {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                when {
                    wifiManager == null -> "WiFi service unavailable"
                    !wifiManager.isWifiEnabled -> "WiFi is turned off. Please enable WiFi"
                    !isConnectedToWiFi() -> "Not connected to any WiFi network"
                    else -> {
                        val info = getCurrentWiFiInfo()
                        if (info != null) {
                            "Connected to: ${info.ssid}"
                        } else {
                            "Unable to get WiFi information"
                        }
                    }
                }
            }
        }
    }
}