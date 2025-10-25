package com.android.mockgps.ui.screens

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import com.android.mockgps.MainActivity
import com.android.mockgps.extensions.roundedShadow
import com.android.mockgps.service.LocationHelper
import com.android.mockgps.service.MockLocationService
import com.android.mockgps.storage.StorageManager
import com.android.mockgps.ui.components.FavoritesListComponent
import com.android.mockgps.ui.components.FooterComponent
import com.android.mockgps.ui.components.SearchComponent
import com.android.mockgps.ui.screens.viewmodels.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.android.mockgps.R
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material.icons.filled.Star

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    activity: MainActivity,
    isMocking: Boolean,
    onMockingToggle: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mapViewModel.markerPosition.value, 15f)
    }

    val favoritesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showFavoritesSheet by remember { mutableStateOf(false) }
    val appearanceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAppearanceSheet by remember { mutableStateOf(false) }
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showMenuSheet by remember { mutableStateOf(false) }

    val mapTypeOptions: List<Pair<MapType, String>> = remember {
        listOf(MapType.NORMAL to "Default", MapType.HYBRID to "Satellite")
    }

    // Load saved preferences instead of hardcoded defaults
    var currentMapType by remember {
        mutableStateOf(
            when (StorageManager.savedMapType) {
                "HYBRID" -> MapType.HYBRID
                "SATELLITE" -> MapType.SATELLITE
                "TERRAIN" -> MapType.TERRAIN
                else -> MapType.NORMAL
            }
        )
    }
    var showTraffic by remember { mutableStateOf(StorageManager.savedShowTraffic) }
    var darkMode by remember { mutableStateOf(StorageManager.savedDarkMode) }

    var mapTypeDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val mapStyle = remember(darkMode, currentMapType, context) {
        if (darkMode && currentMapType == MapType.NORMAL) {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.style_json)
        } else null
    }

    val scrim = BottomSheetDefaults.ScrimColor
    // Exclude Favorites from dim (per your spec)
    val shouldDim by remember(
        showAppearanceSheet, showMenuSheet, mapTypeDropdownExpanded
    ) { mutableStateOf(showAppearanceSheet || showMenuSheet || mapTypeDropdownExpanded) }

    val mapIsDark by remember(currentMapType, darkMode) {
        mutableStateOf(currentMapType == MapType.HYBRID || (currentMapType == MapType.NORMAL && darkMode))
    }

    val currentShouldDim by rememberUpdatedState(shouldDim)
    val currentMapIsDark by rememberUpdatedState(mapIsDark)

    // Edge-to-edge with transparent status bar; overlay supplies dim
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false) // edge-to-edge
        window.statusBarColor = Color.Transparent.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = if (currentShouldDim) false else !currentMapIsDark
    }

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

    LaunchedEffect(isMocking) {
        MockLocationService.instance?.latLng?.let { live ->
            if (live != mapViewModel.markerPosition.value) {
                mapViewModel.updateMarkerPosition(live)
                animateCamera()
            }
        }
    }

    // Compute bottom inset height once per composition for the bottom overlay bar
    val bottomInsetPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            onMapLoaded = {
                LocationHelper.requestPermissions(activity)
                mapViewModel.updateMarkerPosition(mapViewModel.markerPosition.value)
            },
            properties = MapProperties(
                mapType = currentMapType,
                isTrafficEnabled = showTraffic,
                mapStyleOptions = mapStyle
            ),
            uiSettings = MapUiSettings(
                tiltGesturesEnabled = false,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false,
                mapToolbarEnabled = false,
                compassEnabled = false
            ),
            onMapClick = { latLng -> mapViewModel.updateMarkerPosition(latLng) },
            cameraPositionState = cameraPositionState
        ) {
            Marker(state = MarkerState(mapViewModel.markerPosition.value))
        }

        Column(modifier = Modifier.statusBarsPadding()) {
            SearchComponent(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxHeight(0.080f)
                    .fillMaxWidth()
                    .padding(horizontal = 65.dp, vertical = 7.dp)
                    .roundedShadow(32.dp)
                    .zIndex(32f),
                onSearch = { searchTerm ->
                    LocationHelper.geocoding(searchTerm) { foundLatLng ->
                        foundLatLng?.let {
                            mapViewModel.updateMarkerPosition(it)
                            animateCamera()
                        } ?: run {
                            Toast.makeText(activity, "Location not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            IconButton(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .align(Alignment.End),
                onClick = { showMenuSheet = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black, contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Filled.List, contentDescription = "menu")
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

        // Global overlay dim
        if (shouldDim) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrim) // uniform dim
                    .zIndex(16f)
            )
        }

        // NEW: Bottom-cover overlay bar (solid black) to cover the map strip below the sheet
        if (shouldDim) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomInsetPadding + 28.dp) // inset + small buffer to sit over logo area
                    .background(Color.Black) // opaque black as requested
                    .zIndex(17f)
            )
        }

        // MENU SHEET — pops from bottom (no extra spacing)
        if (showMenuSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMenuSheet = false },
                sheetState = menuSheetState,
                scrimColor = Color.Transparent,
                windowInsets = BottomSheetDefaults.windowInsets
            ) {
                SheetEdgeToEdge(lightStatusBarIcons = false)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    ListItem(
                        headlineContent = { Text("Appearance") },
                        leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { menuSheetState.hide() }.invokeOnCompletion {
                                    showMenuSheet = false
                                    showAppearanceSheet = true
                                }
                            }
                    )
                    ListItem(
                        headlineContent = { Text("Favorites") },
                        leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { menuSheetState.hide() }.invokeOnCompletion {
                                    showMenuSheet = false
                                    showFavoritesSheet = true
                                }
                            }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // FAVORITES SHEET — unchanged component
        if (showFavoritesSheet) {
            FavoritesListComponent(
                onDismissRequest = { showFavoritesSheet = false },
                sheetState = favoritesSheetState,
                data = StorageManager.favorites,
                onEntryClicked = { clickedEntry ->
                    mapViewModel.updateMarkerPosition(clickedEntry.latLng)
                    scope.launch {
                        favoritesSheetState.hide()
                        showFavoritesSheet = false
                    }
                    animateCamera()
                }
            )
        }

        // APPEARANCE SHEET — pops from bottom (no extra spacing)
        if (showAppearanceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAppearanceSheet = false },
                sheetState = appearanceSheetState,
                scrimColor = Color.Transparent,
                windowInsets = BottomSheetDefaults.windowInsets
            ) {
                SheetEdgeToEdge(lightStatusBarIcons = false)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    MapTypeDropdown(
                        current = currentMapType,
                        options = mapTypeOptions,
                        onSelect = { type ->
                            currentMapType = type
                            // Save preference when map type changes
                            StorageManager.savedMapType = type.name
                            if (type != MapType.NORMAL) {
                                darkMode = false
                                StorageManager.savedDarkMode = false
                            }
                        },
                        onExpandedChange = { expanded -> mapTypeDropdownExpanded = expanded }
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Traffic", modifier = Modifier.weight(1f))
                        Switch(
                            checked = showTraffic,
                            onCheckedChange = {
                                showTraffic = it
                                // Save preference when traffic toggle changes
                                StorageManager.savedShowTraffic = it
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (currentMapType == MapType.NORMAL) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dark mode", modifier = Modifier.weight(1f))
                            Switch(
                                checked = darkMode,
                                onCheckedChange = {
                                    darkMode = it
                                    // Save preference when dark mode toggle changes
                                    StorageManager.savedDarkMode = it
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapTypeDropdown(
    current: MapType,
    options: List<Pair<MapType, String>>,
    onSelect: (MapType) -> Unit,
    onExpandedChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == current }?.second ?: current.name

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = !expanded
            onExpandedChange(expanded)
        }
    ) {
        TextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Map type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onExpandedChange(false)
            }
        ) {
            options.forEach { (type, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                        onExpandedChange(false)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
private fun SheetEdgeToEdge(
    lightStatusBarIcons: Boolean
) {
    val sheetView = LocalView.current
    SideEffect {
        var parent = sheetView.parent
        var window: android.view.Window? = null
        while (parent != null) {
            if (parent is DialogWindowProvider) { window = parent.window; break }
            parent = parent.parent
        }
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false) // dialog edge-to-edge
            w.statusBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                w.isStatusBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(w, sheetView)
                .isAppearanceLightStatusBars = lightStatusBarIcons
        }
    }
}
