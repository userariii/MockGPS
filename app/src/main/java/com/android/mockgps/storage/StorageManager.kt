package com.android.mockgps.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.android.mockgps.extensions.equalTo
import com.android.mockgps.service.LocationHelper
import com.android.mockgps.ui.models.LocationEntry

object StorageManager {
    private const val PREF = "gpsprefs"
    private const val KEY_HISTORY = "history"
    private const val KEY_FAVORITES = "favorites"

    // Preference keys for UI settings
    private const val KEY_MAP_TYPE = "map_type"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_SHOW_TRAFFIC = "show_traffic"

    private lateinit var pref: SharedPreferences

    fun initialise(context: Context) {
        pref = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    fun getLatestLocation(): LatLng {
        return locationHistory.lastOrNull() ?: LocationHelper.DEFAULT_LOCATION
    }

    fun addLocationToHistory(latLng: LatLng) {
        val tempHistory = locationHistory
        if (!locationHistory.contains(latLng)) {
            tempHistory.add(latLng)
            locationHistory = tempHistory
        }
    }

    fun toggleFavoriteForPosition(locationEntry: LocationEntry) {
        if (containsFavoriteEntry(locationEntry))
            removeFavorite(locationEntry)
        else
            addLocationToFavorites(locationEntry)
    }

    private fun addLocationToFavorites(locationEntry: LocationEntry) {
        val tempFavorites = favorites
        tempFavorites.add(locationEntry)
        favorites = tempFavorites
    }

    private fun removeFavorite(locationEntry: LocationEntry) {
        val tempFavorites = favorites
        tempFavorites.removeIf { it.latLng.equalTo(locationEntry.latLng) }
        favorites = tempFavorites
    }

    fun containsFavoriteEntry(locationEntry: LocationEntry): Boolean {
        return favorites.any { it.latLng.equalTo(locationEntry.latLng) }
    }

    private var locationHistory: MutableList<LatLng>
        get() {
            val json = pref.getString(KEY_HISTORY, "[]")
            val typeToken = object : TypeToken<MutableList<LatLng>>() {}.type
            return Gson().fromJson(json, typeToken)
        }
        private set(value) {
            val json = Gson().toJson(value)
            pref.edit {
                putString(KEY_HISTORY, json)
                commit()
            }
        }

    var favorites: MutableList<LocationEntry>
        get() {
            val json = pref.getString(KEY_FAVORITES, "[]")
            val typeToken = object : TypeToken<MutableList<LocationEntry>>() {}.type
            return Gson().fromJson(json, typeToken)
        }
        private set(value) {
            val json = Gson().toJson(value)
            pref.edit {
                putString(KEY_FAVORITES, json)
                commit()
            }
        }

    // Map appearance preferences
    var savedMapType: String
        get() = pref.getString(KEY_MAP_TYPE, "NORMAL") ?: "NORMAL"
        set(value) {
            pref.edit {
                putString(KEY_MAP_TYPE, value)
                apply()
            }
        }

    var savedDarkMode: Boolean
        get() = pref.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            pref.edit {
                putBoolean(KEY_DARK_MODE, value)
                apply()
            }
        }

    var savedShowTraffic: Boolean
        get() = pref.getBoolean(KEY_SHOW_TRAFFIC, false)
        set(value) {
            pref.edit {
                putBoolean(KEY_SHOW_TRAFFIC, value)
                apply()
            }
        }
}
