package com.android.mockgps.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.LatLng
import com.android.mockgps.MainActivity
import com.android.mockgps.MockGpsApp
import com.android.mockgps.R
import com.android.mockgps.storage.StorageManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MockLocationService : Service() {

    companion object {
        const val TAG = "MockLocationService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "MockLocationChannel"
        const val ACTION_START_MOCKING = "com.android.mockgps.START_MOCKING"
        const val ACTION_STOP_MOCKING = "com.android.mockgps.STOP_MOCKING"
        const val ACTION_MOCK_STATE_CHANGED = "com.android.mockgps.MOCK_STATE_CHANGED"
        const val EXTRA_IS_MOCKING = "isMocking"

        // Update interval for mock location (in milliseconds)
        // Set to 1 second to ensure continuous mocking even during device sleep
        private const val MOCK_UPDATE_INTERVAL = 1000L

        var instance: MockLocationService? = null
    }

    var isMocking = false
        private set

    var latLng: LatLng = LatLng(0.0, 0.0)

    private var wakeLock: PowerManager.WakeLock? = null

    // Coroutine job for continuous location mocking
    private var mockingJob: Job? = null

    private val locationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Service created")

        // Acquire wake lock with indefinite timeout to keep CPU awake
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::WakeLock"
        ).apply {
            // Use acquire() without timeout for continuous operation
            // The wake lock will be released only when service is destroyed
            acquire()
            Log.d(TAG, "WakeLock acquired")
        }

        createNotificationChannel()
        loadLastLocationFromStorage()

        // Initial (idle) notification shows Start action
        startForeground(NOTIFICATION_ID, buildNotification("Service ready", isRunning = false))
        notifyStateChanged(false)
    }

    private fun loadLastLocationFromStorage() {
        try {
            val lastLocation = StorageManager.getLatestLocation()
            if (lastLocation.latitude != 0.0 || lastLocation.longitude != 0.0) {
                latLng = lastLocation
                Log.d(TAG, "Loaded last location from storage: ${latLng.latitude}, ${latLng.longitude}")
            } else {
                latLng = LocationHelper.DEFAULT_LOCATION
                Log.d(TAG, "No valid location in storage, using default: ${latLng.latitude}, ${latLng.longitude}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading location from storage, using default", e)
            latLng = LocationHelper.DEFAULT_LOCATION
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MOCKING -> {
                if (latLng.latitude != 0.0 || latLng.longitude != 0.0) {
                    startMockingLocation()
                } else {
                    Log.w(TAG, "Attempted to start mocking, but latLng not set; trying to recover")
                    loadLastLocationFromStorage()
                    if (latLng.latitude != 0.0 || latLng.longitude != 0.0) {
                        startMockingLocation()
                    }
                }
            }
            ACTION_STOP_MOCKING -> stopMockingLocation()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = MockLocationBinder()

    override fun onDestroy() {
        stopMockingLocation()

        // Release wake lock when service is destroyed
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null

        super.onDestroy()
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "Mock Location",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active mock location service"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun buildNotification(text: String, isRunning: Boolean): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPI = PendingIntent.getActivity(
            this, 0, mainIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val startIntent = Intent(this, MockLocationService::class.java).apply { action = ACTION_START_MOCKING }
        val startPI = PendingIntent.getService(
            this, 100, startIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP_MOCKING }
        val stopPI = PendingIntent.getService(
            this, 101, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val largeIcon = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val bigText = NotificationCompat.BigTextStyle()
            .setBigContentTitle("Mock GPS active")
            .bigText(text)
            .setSummaryText("Foreground service")

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock GPS")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setLargeIcon(largeIcon)
            .setStyle(bigText)
            .setContentIntent(mainPI)
            .setOngoing(true)
            .setSilent(true)

        if (isRunning) builder.addAction(android.R.drawable.ic_media_pause, "Stop", stopPI)
        else builder.addAction(android.R.drawable.ic_media_play, "Start", startPI)

        return builder.build()
    }

    fun toggleMocking() {
        if (isMocking) stopMockingLocation() else startMockingLocation()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private fun startMockingLocation() {
        if (isMocking) {
            Log.d(TAG, "Already mocking, ignoring start request")
            return
        }

        // Safety: prevent mocking with invalid coordinates
        if (latLng.latitude == 0.0 && latLng.longitude == 0.0) {
            Log.w(TAG, "Attempting to mock with 0.0,0.0 - trying to recover from storage")
            val recoveredLocation = StorageManager.getLatestLocation()
            latLng = if (recoveredLocation.latitude != 0.0 || recoveredLocation.longitude != 0.0) {
                Log.d(TAG, "Recovered location from storage: ${recoveredLocation.latitude}, ${recoveredLocation.longitude}")
                recoveredLocation
            } else {
                Log.w(TAG, "No valid location found, using default")
                LocationHelper.DEFAULT_LOCATION
            }
        }

        // Ensure test provider is available BEFORE flipping to running state
        if (!addTestProvider()) {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, buildNotification("Service ready", isRunning = false)
            )
            notifyStateChanged(false)
            Log.w(TAG, "Cannot start mocking: mock provider not allowed (select app in Developer Options)")
            return
        }

        try {
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException enabling test provider", se)
            stopMockingLocation()
            return
        }

        StorageManager.addLocationToHistory(latLng)
        isMocking = true

        // Show initial status
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID, buildNotification("Resolving address…", isRunning = true)
        )

        // Start continuous location mocking loop
        startContinuousMocking()

        // Resolve and display address
        GlobalScope.launch(Dispatchers.IO) {
            val addressText = resolveAddress(latLng.latitude, latLng.longitude)
            GlobalScope.launch(Dispatchers.Main) {
                getSystemService(NotificationManager::class.java).notify(
                    NOTIFICATION_ID, buildNotification(addressText, isRunning = true)
                )
            }
        }

        notifyStateChanged(true)
        Log.d(TAG, "Mock location started with continuous updates every ${MOCK_UPDATE_INTERVAL}ms")
    }

    /**
     * Starts a coroutine that continuously updates the mock location
     * This ensures the location remains mocked even when device sleeps
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun startContinuousMocking() {
        // Cancel any existing job
        mockingJob?.cancel()

        mockingJob = GlobalScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Continuous mocking loop started")

            while (isActive && isMocking) {
                try {
                    setMockLocation(latLng)
                    delay(MOCK_UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in continuous mocking loop", e)
                    // Continue the loop even if there's an error
                    delay(MOCK_UPDATE_INTERVAL)
                }
            }

            Log.d(TAG, "Continuous mocking loop stopped")
        }
    }

    private fun stopMockingLocation() {
        if (isMocking) {
            isMocking = false

            // Cancel the continuous mocking job
            mockingJob?.cancel()
            mockingJob = null

            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, buildNotification("Service ready", isRunning = false)
            )

            try {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {
                Log.w(TAG, "Error removing test provider (may not exist)")
            }

            notifyStateChanged(false)
            Log.d(TAG, "Mock location stopped")
        } else {
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID, buildNotification("Service ready", isRunning = false)
            )
            notifyStateChanged(false)
        }
    }

    private fun addTestProvider(): Boolean {
        val name = LocationManager.GPS_PROVIDER
        try {
            runCatching { locationManager.removeTestProvider(name) }
            locationManager.addTestProvider(
                name, true, false, false,
                false, false, false, false,
                ProviderProperties.POWER_USAGE_HIGH,
                ProviderProperties.ACCURACY_FINE
            )
            return true
        } catch (se: SecurityException) {
            val ctx = MockGpsApp.shared.applicationContext
            GlobalScope.launch(Dispatchers.Main) {
                Toast.makeText(ctx, "Set this app as your mock location app", Toast.LENGTH_SHORT).show()
            }
            stopMockingLocation()
            return false
        }
    }

    /**
     * Sets the mock location for GPS provider
     * This method is called continuously to maintain the mock location
     */
    @SuppressLint("MissingPermission")
    private fun setMockLocation(target: LatLng) {
        try {
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = target.latitude
                longitude = target.longitude
                altitude = 12.5
                time = System.currentTimeMillis()
                accuracy = 2f
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

                // Set additional properties for better mock location accuracy
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 2f
                    speedAccuracyMetersPerSecond = 0.1f
                    bearingAccuracyDegrees = 5f
                }
            }

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting mock location", e)
        }
    }

    private fun resolveAddress(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val list = geocoder.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
                val a = list[0]
                listOfNotNull(
                    a.thoroughfare, a.subLocality, a.locality,
                    a.adminArea, a.postalCode, a.countryName
                ).joinToString(", ")
            } else String.format(Locale.US, "%.6f, %.6f", lat, lng)
        } catch (e: Exception) {
            String.format(Locale.US, "%.6f, %.6f", lat, lng)
        }
    }

    private fun notifyStateChanged(isRunning: Boolean) {
        val intent = Intent(ACTION_MOCK_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(EXTRA_IS_MOCKING, isRunning)
        sendBroadcast(intent)
    }

    inner class MockLocationBinder : Binder() {
        fun getService(): MockLocationService = this@MockLocationService
    }
}
