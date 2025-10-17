package com.android.mockgps.service

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.FOREGROUND_SERVICE_LOCATION
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.android.mockgps.MockGpsApp

object LocationHelper {
    private const val REQUEST_CODE = 69
    private const val NOTIF_REQUEST_CODE = 70
    val DEFAULT_LOCATION = LatLng(19.027663, 73.022646)
    fun requestPermissions(activity: ComponentActivity) {
        val perms = mutableListOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(FOREGROUND_SERVICE_LOCATION)      // Added for foreground location permission
            perms.add(POST_NOTIFICATIONS)
        }
        activity.requestPermissions(perms.toTypedArray(), REQUEST_CODE)
    }

    fun hasPermission(activity: ComponentActivity): Boolean {
        val locOk = ContextCompat.checkSelfPermission(
            activity, ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val fgslOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity, FOREGROUND_SERVICE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val notifOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity, POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        return locOk && fgslOk && notifOk
    }

    // Geocoding
    fun reverseGeocoding(latLng: LatLng, result: (Address?) -> Unit) {
        val geocoder = Geocoder(MockGpsApp.shared.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { resp ->
                result(resp.firstOrNull())
            }
        } else {
            val resp = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            result(resp?.firstOrNull())
        }
    }

    fun geocoding(searchterm: String, result: (LatLng?) -> Unit) {
        val geocoder = Geocoder(MockGpsApp.shared.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(searchterm, 1) { resp ->
                val addr = resp.firstOrNull() ?: run { result(null); return@getFromLocationName }
                result(LatLng(addr.latitude, addr.longitude))
            }
        } else {
            val resp = geocoder.getFromLocationName(searchterm, 1)
            val addr = resp?.firstOrNull() ?: run { result(null); return }
            result(LatLng(addr.latitude, addr.longitude))
        }
    }
}
