package com.lilstiffy.mockgps

import android.Manifest.permission.FOREGROUND_SERVICE_LOCATION
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import com.lilstiffy.mockgps.service.MockLocationService
import com.lilstiffy.mockgps.service.VibratorService
import com.lilstiffy.mockgps.ui.screens.MapScreen
import com.lilstiffy.mockgps.ui.theme.MockGpsTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_MOCK_STATE_CHANGED = "com.lilstiffy.mockgps.MOCK_STATE_CHANGED"
        const val EXTRA_IS_MOCKING = "isMocking"
    }

    private var mockLocationService: MockLocationService? = null
        private set(value) {
            field = value
            MockLocationService.instance = value
        }

    private var isBound = false
    private val PERMISSION_REQUEST_CODE = 69

    var isMockingUIState by mutableStateOf(false)
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MockLocationService.MockLocationBinder
            mockLocationService = binder.getService()
            isBound = true
            isMockingUIState = mockLocationService?.isMocking ?: false
        }

        override fun onServiceDisconnected(className: ComponentName) {
            isBound = false
            isMockingUIState = false
        }
    }

    private fun hasAllPermissions(): Boolean {
        val requiredPerms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPerms.add(FOREGROUND_SERVICE_LOCATION)
        }
        return requiredPerms.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestMissingPermissions() {
        val permsToRequest = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsToRequest.add(FOREGROUND_SERVICE_LOCATION)
        }
        if (permsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startAndBindService()
        }
    }

    private fun startAndBindService() {
        Intent(this, MockLocationService::class.java).also { serviceIntent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MockGpsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MapScreen(
                        activity = this,
                        isMocking = isMockingUIState,
                        onMockingToggle = {
                            val toggled = toggleMocking()
                            isMockingUIState = toggled
                        }
                    )
                }
            }
        }

        requestMissingPermissions()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startAndBindService()
            } else {
                Toast.makeText(this, "Permissions denied. App cannot function properly.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiverSafe()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiverSafe()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        // Best-effort cleanup in case onStop wasn’t called
        unregisterReceiverSafe()
    }

    override fun onResume() {
        super.onResume()
        if (!isBound && hasAllPermissions()) {
            startAndBindService()
        }
        // Defensive resync in case a state-change happened while Activity was stopped
        isMockingUIState = (MockLocationService.instance?.isMocking == true)
    }

    fun toggleMocking(): Boolean {
        if (isBound && hasAllPermissions()) {
            mockLocationService?.toggleMocking()
            if (mockLocationService?.isMocking == true) {
                Toast.makeText(this, "Mocking location...", Toast.LENGTH_SHORT).show()
                VibratorService.vibrate()
                return true
            } else {
                Toast.makeText(this, "Stopped mocking location...", Toast.LENGTH_SHORT).show()
                VibratorService.vibrate()
                return false
            }
        } else if (!isBound && hasAllPermissions()) {
            Toast.makeText(this, "Service not bound", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No Location permission", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    // --- Broadcast plumbing to keep UI in sync with Service state ---
    private var receiverRegistered = false

    private val mockStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MOCK_STATE_CHANGED) {
                isMockingUIState = intent.getBooleanExtra(EXTRA_IS_MOCKING, false)
            }
        }
    }

    private fun registerReceiverSafe() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_MOCK_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mockStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(mockStateReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {
            // no-op
        }
    }

    private fun unregisterReceiverSafe() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(mockStateReceiver)
        } catch (_: Exception) {
            // no-op
        } finally {
            receiverRegistered = false
        }
    }
}
