package com.android.mockgps.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.*
import com.android.mockgps.MainActivity
import com.android.mockgps.extensions.roundedShadow
import com.android.mockgps.service.LocationHelper
import com.android.mockgps.storage.StorageManager
import com.android.mockgps.ui.components.FavoritesListComponent
import com.android.mockgps.ui.components.FooterComponent
import com.android.mockgps.ui.components.SearchComponent
import com.android.mockgps.ui.screens.viewmodels.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    activity: MainActivity,
    isMocking: Boolean,
    onMockingToggle: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Edge-to-edge with dark status bar icons; keep bar transparent so map is truly full-screen
    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        window.statusBarColor = Color.Transparent.toArgb()
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapViewModel.markerPosition.value, 15f)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    fun animateCamera() {
        scope.launch(Dispatchers.Main) {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition(mapViewModel.markerPosition.value, 15f, 0f, 0f)
                ),
                durationMs = 1000
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Google Map (kept in light mode by not providing a dark style)
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            onMapLoaded = {
                LocationHelper.requestPermissions(activity)
                mapViewModel.updateMarkerPosition(mapViewModel.markerPosition.value)
            },
            properties = MapProperties(
                mapStyleOptions = null // default light map
            ),
            uiSettings = MapUiSettings(
                tiltGesturesEnabled = false,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false
            ),
            onMapClick = { latLng ->
                if (!isMocking) {
                    mapViewModel.updateMarkerPosition(latLng)
                }
            },
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = MarkerState(mapViewModel.markerPosition.value)
            )
        }

        Column(
            modifier = Modifier.statusBarsPadding()
        ) {
            SearchComponent(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxHeight(0.080f)
                    .fillMaxWidth()
                    .padding(horizontal = 65.dp, vertical = 7.dp)
                    .roundedShadow(32.dp)
                    .zIndex(32f),
                onSearch = { searchTerm ->
                    if (isMocking) {
                        Toast.makeText(
                            activity,
                            "You can't search while mocking location",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@SearchComponent
                    }

                    LocationHelper.geocoding(searchTerm) { foundLatLng ->
                        foundLatLng?.let {
                            mapViewModel.updateMarkerPosition(it)
                            animateCamera()
                        }
                    }
                }
            )

            IconButton(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .align(Alignment.End),
                onClick = { showBottomSheet = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black, contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.List,
                    contentDescription = "show favorites"
                )
            }
        }

        FooterComponent(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 225.dp)
                .padding(horizontal = 50.dp, vertical = 10.dp)
                .zIndex(32f)
                .roundedShadow(16.dp),
            address = mapViewModel.address.value,
            latLng = mapViewModel.markerPosition.value,
            isMocking = isMocking,
            isFavorite = mapViewModel.markerPositionIsFavorite.value,
            onStart = { onMockingToggle() },
            onFavorite = { mapViewModel.toggleFavoriteForLocation() }
        )

        if (showBottomSheet) {
            FavoritesListComponent(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                data = StorageManager.favorites,
                onEntryClicked = { clickedEntry ->
                    if (isMocking) {
                        Toast.makeText(
                            activity,
                            "You can't switch location while mocking",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@FavoritesListComponent
                    }
                    mapViewModel.apply {
                        mapViewModel.updateMarkerPosition(clickedEntry.latLng)
                        scope.launch {
                            sheetState.hide()
                            showBottomSheet = false
                        }
                        animateCamera()
                    }
                }
            )
        }
    }
}
