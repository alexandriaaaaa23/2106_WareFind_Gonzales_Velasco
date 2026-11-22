package com.example.oop_warehouse_location.services

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.oop_warehouse_location.models.Warehouse
import com.example.oop_warehouse_location.receivers.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * WarehouseGeofenceManager — Automated Spatial Triggers & Boundary Monitor (Phase 4).
 *
 * Encapsulates:
 * - Google Play Services GeofencingClient lifecycle
 * - Automated registration of circular warehouse perimeters
 * - Real-time distance and in-geofence containment checks
 * - Background transition triggers (ENTER, EXIT, DWELL)
 */
class WarehouseGeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    companion object {
        private const val TAG = "WarehouseGeofence"
        const val ACTION_GEOFENCE_EVENT = "com.example.oop_warehouse_location.ACTION_GEOFENCE_EVENT"
        const val EXTRA_WAREHOUSE_ID = "warehouse_id"
        const val EXTRA_WAREHOUSE_NAME = "warehouse_name"
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * Registers a circular geofence around the specified warehouse.
     */
    fun registerWarehouseGeofence(
        warehouse: Warehouse,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (!warehouse.hasCoordinates()) {
            onFailure(IllegalArgumentException("Warehouse has no geographic coordinates set."))
            return
        }

        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted) {
            onFailure(SecurityException("Fine location permission required to register geofence."))
            return
        }

        try {
            val radius = warehouse.radiusMeters.toFloat().coerceAtLeast(30f)

            val geofence = Geofence.Builder()
                .setRequestId(warehouse.warehouseId)
                .setCircularRegion(warehouse.latitude, warehouse.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(
                    Geofence.GEOFENCE_TRANSITION_ENTER or
                    Geofence.GEOFENCE_TRANSITION_EXIT or
                    Geofence.GEOFENCE_TRANSITION_DWELL
                )
                .setLoiteringDelay(15000) // 15 seconds dwell time
                .build()

            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                .addGeofence(geofence)
                .build()

            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence registered for warehouse ${warehouse.name} (${warehouse.warehouseId})")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to register geofence: ${e.message}", e)
                    onFailure(e)
                }
        } catch (e: SecurityException) {
            onFailure(e)
        }
    }

    /**
     * Unregisters the geofence for the given warehouse ID.
     */
    fun removeWarehouseGeofence(
        warehouseId: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (warehouseId.isBlank()) {
            onComplete(false)
            return
        }
        geofencingClient.removeGeofences(listOf(warehouseId))
            .addOnSuccessListener {
                Log.d(TAG, "Geofence removed for warehouse $warehouseId")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to remove geofence: ${e.message}", e)
                onComplete(false)
            }
    }

    /**
     * Checks if a specific lat/lng point is within the warehouse's geofence boundary.
     */
    fun isInsideGeofence(lat: Double, lng: Double, warehouse: Warehouse): Boolean {
        if (!warehouse.hasCoordinates()) return true // default to true if warehouse coordinates not configured
        val distance = getDistanceMeters(lat, lng, warehouse.latitude, warehouse.longitude)
        return distance <= warehouse.radiusMeters
    }

    /**
     * Calculates surface distance in meters using Android Location API.
     */
    fun getDistanceMeters(startLat: Double, startLng: Double, endLat: Double, endLng: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLng, endLat, endLng, results)
        return results[0]
    }
}
