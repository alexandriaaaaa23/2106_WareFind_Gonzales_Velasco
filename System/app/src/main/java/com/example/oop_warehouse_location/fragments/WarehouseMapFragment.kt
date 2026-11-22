package com.example.oop_warehouse_location.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.activities.MainActivity
import com.example.oop_warehouse_location.fragments.base.BaseWarehouseFragment
import com.example.oop_warehouse_location.models.Warehouse
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.util.Locale

/**
 * WarehouseMapFragment — Real-World Macro-GIS Screen powered by OpenStreetMap (OSMDroid).
 *
 * Visualizes:
 * - Real-world geographic coordinates of warehouses
 * - Circular geofence boundary perimeters
 * - Tap-to-place location configuration for warehouse managers
 * - Seamless one-tap bridge to the Indoor Floor Plan (Micro-GIS)
 */
class WarehouseMapFragment : BaseWarehouseFragment() {

    private lateinit var mapView: MapView
    private var locationOverlay: MyLocationNewOverlay? = null

    // UI references
    private lateinit var tvWarehouseCount: TextView
    private lateinit var bannerSetLocation: LinearLayout
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var fabSetLocation: FloatingActionButton

    private lateinit var layoutInfoCard: LinearLayout
    private lateinit var tvName: TextView
    private lateinit var tvAddress: TextView
    private lateinit var tvCapacity: TextView
    private lateinit var tvGeofenceBadge: TextView
    private lateinit var btnOpenIndoor: MaterialButton
    private lateinit var btnSaveLocation: MaterialButton

    // State
    private var isSettingLocation = false
    private var pendingGeoPoint: GeoPoint? = null
    private var pendingAddress: String = ""
    private var currentWarehouse: Warehouse? = null
    private val allWarehouses = mutableListOf<Warehouse>()

    // Overlays
    private val warehouseMarkers = mutableListOf<Marker>()
    private val geofenceCircles = mutableListOf<Polygon>()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableUserLocation()
        }
    }

    companion object {
        fun newInstance(warehouseId: String): WarehouseMapFragment {
            return WarehouseMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WAREHOUSE_ID, warehouseId)
                }
            }
        }

        /**
         * 100% Free Public OpenStreetMap tile provider (FOSSGIS & OSM-FR).
         * Requires NO API KEY, completely free public servers,
         * and renders global road and geographic data.
         */
        val TILE_SOURCE_OSM_FREE: XYTileSource = XYTileSource(
            "OSM_Public",
            0, 19, 256, ".png",
            arrayOf(
                "https://tile.openstreetmap.de/",
                "https://a.tile.openstreetmap.fr/osmfr/",
                "https://b.tile.openstreetmap.fr/osmfr/"
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext().applicationContext
        val prefs = ctx.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE)
        val osmConfig = Configuration.getInstance()

        // Load configuration and ensure our descriptive user agent is registered & saved
        osmConfig.load(ctx, prefs)
        val appUserAgent = "OOPWarehouseLocator/1.0 (Android; Enterprise Logistics GIS; admin@warehouselocator.com)"
        osmConfig.userAgentValue = appUserAgent
        prefs.edit { putString("osmdroid.userAgentValue", appUserAgent) }
        osmConfig.save(ctx, prefs)

        // Internal sandboxed tile paths (Android 10+ scoped storage compliant)
        val basePath = File(ctx.filesDir, "osmdroid")
        val cachePath = File(ctx.cacheDir, "osmdroid/tiles")
        basePath.mkdirs()
        cachePath.mkdirs()
        osmConfig.osmdroidBasePath = basePath
        osmConfig.osmdroidTileCache = cachePath

        val view = inflater.inflate(R.layout.fragment_warehouse_map, container, false)
        bindViews(view)
        setupOsmMap()
        loadWarehouses()
        checkLocationPermissions()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView.onResume()
    }

    private fun bindViews(view: View) {
        val ivBack = view.findViewById<ImageView>(R.id.ivMapBack)
        ivBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        mapView = view.findViewById(R.id.osmMapView)
        tvWarehouseCount = view.findViewById(R.id.tvMapWarehouseCount)
        bannerSetLocation = view.findViewById(R.id.bannerSetLocationPrompt)
        fabMyLocation = view.findViewById(R.id.fabMyLocation)
        fabSetLocation = view.findViewById(R.id.fabSetLocation)

        layoutInfoCard = view.findViewById(R.id.layoutWarehouseInfoCard)
        tvName = view.findViewById(R.id.tvSelectedWarehouseName)
        tvAddress = view.findViewById(R.id.tvSelectedWarehouseAddress)
        tvCapacity = view.findViewById(R.id.tvSelectedWarehouseCapacity)
        tvGeofenceBadge = view.findViewById(R.id.tvSelectedWarehouseGeofenceBadge)
        btnOpenIndoor = view.findViewById(R.id.btnOpenIndoorFloorPlan)
        btnSaveLocation = view.findViewById(R.id.btnSaveNewLocation)

        // FAB: Locate Me
        fabMyLocation.setOnClickListener {
            locationOverlay?.myLocation?.let { loc ->
                mapView.controller.animateTo(loc)
                mapView.controller.setZoom(17.0)
            } ?: run {
                Toast.makeText(requireContext(), "Locating your position...", Toast.LENGTH_SHORT).show()
                enableUserLocation()
            }
        }

        // FAB: Set Location (Pin placement mode)
        fabSetLocation.setOnClickListener {
            toggleSetLocationMode()
        }

        // Open Micro-GIS Floor Plan
        btnOpenIndoor.setOnClickListener {
            (activity as? MainActivity)?.openFloorPlan()
        }

        // Save New Location Button
        btnSaveLocation.setOnClickListener {
            savePendingLocation()
        }
    }

    private fun setupOsmMap() {
        mapView.setDestroyMode(false)
        mapView.setTileSource(TILE_SOURCE_OSM_FREE)
        mapView.tileProvider.clearTileCache()
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = true
        mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)

        // Default initial center (Metro Manila coordinates as default anchor)
        val defaultPoint = GeoPoint(14.5995, 120.9842)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(defaultPoint)
        mapView.post {
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(defaultPoint)
        }

        // Tap listener for setting warehouse location
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (isSettingLocation && p != null) {
                    onMapTappedToSetLocation(p)
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    onMapTappedToSetLocation(p)
                    return true
                }
                return false
            }
        }
        mapView.overlays.add(0, MapEventsOverlay(mapEventsReceiver))
    }

    private fun checkLocationPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted) {
            enableUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun enableUserLocation() {
        if (!isAdded || view == null) return
        if (locationOverlay == null) {
            locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView).apply {
                enableMyLocation()
                enableFollowLocation()
            }
            mapView.overlays.add(locationOverlay)
        } else {
            locationOverlay?.enableMyLocation()
        }
    }

    private fun loadWarehouses() {
        warehouseRepository.getAllWarehouses()
            .addOnSuccessListener { warehouses ->
                if (!isAdded || view == null) return@addOnSuccessListener
                allWarehouses.clear()
                allWarehouses.addAll(warehouses)

                currentWarehouse = warehouses.firstOrNull { it.warehouseId == warehouseId }
                    ?: warehouses.firstOrNull()

                tvWarehouseCount.text = getString(R.string.warehouse_count_mapped, warehouses.size)
                plotWarehouseOverlays()

                // Center on current warehouse and ensure a visual marker exists
                currentWarehouse?.let { wh ->
                    if (!wh.hasCoordinates()) {
                        // Sample demonstration coordinate if not configured yet
                        wh.latitude = 14.5995
                        wh.longitude = 120.9842
                        wh.address = "Metro Manila Center (Default Location)"
                        plotWarehouseOverlays()
                    }
                    val whPoint = GeoPoint(wh.latitude, wh.longitude)
                    mapView.post {
                        mapView.controller.animateTo(whPoint)
                        mapView.controller.setZoom(16.0)
                    }
                    displayWarehouseCard(wh)
                }
            }
            .addOnFailureListener {
                // Fallback to loading single warehouse
                if (warehouseId.isNotBlank()) {
                    warehouseRepository.getWarehouse(warehouseId).addOnSuccessListener { wh ->
                        if (!isAdded || view == null) return@addOnSuccessListener
                        currentWarehouse = wh
                        allWarehouses.clear()
                        allWarehouses.add(wh)
                        plotWarehouseOverlays()
                        displayWarehouseCard(wh)
                    }
                }
            }
    }

    private fun plotWarehouseOverlays() {
        // Remove existing markers & circles
        warehouseMarkers.forEach { mapView.overlays.remove(it) }
        geofenceCircles.forEach { mapView.overlays.remove(it) }
        warehouseMarkers.clear()
        geofenceCircles.clear()

        allWarehouses.forEach { wh ->
            if (wh.hasCoordinates()) {
                val point = GeoPoint(wh.latitude, wh.longitude)

                // 1. Draw Geofence circle
                val circle = Polygon(mapView).apply {
                    points = Polygon.pointsAsCircle(point, wh.radiusMeters)
                    fillPaint.color = "#25B80049".toColorInt() // 15% opacity crimson
                    outlinePaint.color = "#B80049".toColorInt()
                    outlinePaint.strokeWidth = 3f
                    title = "${wh.name} Geofence (${wh.radiusMeters.toInt()}m)"
                }
                mapView.overlays.add(circle)
                geofenceCircles.add(circle)

                // 2. Draw Marker
                val marker = Marker(mapView).apply {
                    position = point
                    title = wh.name
                    snippet = "Capacity: ${wh.totalCapacity} units\n${wh.address.ifBlank { wh.getFormattedCoordinates() }}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_warehouse_pin)
                    setOnMarkerClickListener { m, _ ->
                        displayWarehouseCard(wh)
                        m.showInfoWindow()
                        true
                    }
                }
                mapView.overlays.add(marker)
                warehouseMarkers.add(marker)
            }
        }

        mapView.invalidate()
    }

    private fun displayWarehouseCard(wh: Warehouse) {
        layoutInfoCard.visibility = View.VISIBLE
        tvName.text = wh.name
        tvAddress.text = wh.address.ifBlank { wh.getFormattedCoordinates() }
        tvCapacity.text = "Capacity: ${wh.totalCapacity} units"
        tvGeofenceBadge.text = "${wh.radiusMeters.toInt()}m Geofence"
        btnSaveLocation.visibility = View.GONE
    }

    private fun toggleSetLocationMode() {
        isSettingLocation = !isSettingLocation
        bannerSetLocation.visibility = if (isSettingLocation) View.VISIBLE else View.GONE

        if (isSettingLocation) {
            fabSetLocation.setColorFilter(ContextCompat.getColor(requireContext(), R.color.greenAccent))
            Toast.makeText(requireContext(), "Tap anywhere on the map to place the warehouse.", Toast.LENGTH_SHORT).show()
        } else {
            fabSetLocation.setColorFilter(ContextCompat.getColor(requireContext(), R.color.pink))
            btnSaveLocation.visibility = View.GONE
        }
    }

    private fun onMapTappedToSetLocation(point: GeoPoint) {
        pendingGeoPoint = point
        reverseGeocode(point)

        // Show pending card
        layoutInfoCard.visibility = View.VISIBLE
        tvName.text = "${currentWarehouse?.name ?: "Warehouse"} (New Location)"
        tvAddress.text = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
        btnSaveLocation.visibility = View.VISIBLE

        // Place temporary marker on map
        val tempMarker = Marker(mapView).apply {
            position = point
            title = "Proposed Location"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_warehouse_pin)
        }
        mapView.overlays.add(tempMarker)
        mapView.invalidate()
    }

    private fun reverseGeocode(point: GeoPoint) {
        if (!Geocoder.isPresent()) return
        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        geocoder.getFromLocation(point.latitude, point.longitude, 1) { addresses ->
            val addr = addresses.firstOrNull()?.getAddressLine(0).orEmpty()
            activity?.runOnUiThread {
                if (addr.isNotBlank()) {
                    pendingAddress = addr
                    tvAddress.text = addr
                }
            }
        }
    }

    private fun savePendingLocation() {
        val point = pendingGeoPoint ?: return
        val targetId = currentWarehouse?.warehouseId ?: warehouseId

        if (targetId.isBlank()) {
            Toast.makeText(requireContext(), "No warehouse selected", Toast.LENGTH_SHORT).show()
            return
        }

        val addr = if (pendingAddress.isNotBlank()) pendingAddress else String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
        val radius = currentWarehouse?.radiusMeters ?: 150.0

        warehouseRepository.updateWarehouseLocation(
            warehouseId = targetId,
            latitude = point.latitude,
            longitude = point.longitude,
            address = addr,
            radiusMeters = radius
        ).addOnSuccessListener {
            Toast.makeText(requireContext(), "Warehouse location updated!", Toast.LENGTH_SHORT).show()
            currentWarehouse?.apply {
                latitude = point.latitude
                longitude = point.longitude
                address = addr
            }
            toggleSetLocationMode()
            plotWarehouseOverlays()
            currentWarehouse?.let { displayWarehouseCard(it) }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Failed to update location: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        mapView.onDetach()
        super.onDestroyView()
    }
}
