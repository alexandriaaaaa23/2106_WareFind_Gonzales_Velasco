package com.example.oop_warehouse_location.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider


class FusedLocationProvider(private val context: Context) : IMyLocationProvider {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var myLocationConsumer: IMyLocationConsumer? = null
    private var lastLocation: Location? = null
    private var isRunning = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return

            // Accuracy filtering: reject degraded readings > 25 meters error
            if (location.hasAccuracy() && location.accuracy > 25.0f) {
                Log.d(TAG, "Filtered out inaccurate GPS reading (accuracy: ${location.accuracy}m)")
                return
            }

            lastLocation = location
            myLocationConsumer?.onLocationChanged(location, this@FusedLocationProvider)
        }
    }

    @SuppressLint("MissingPermission")
    override fun startLocationProvider(consumer: IMyLocationConsumer?): Boolean {
        myLocationConsumer = consumer
        if (isRunning) return true

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).apply {
            setMinUpdateIntervalMillis(1000L)
            setMinUpdateDistanceMeters(1.0f) // Ignores jitter < 1m
            setGranularity(Granularity.GRANULARITY_FINE)
            setWaitForAccurateLocation(true)
        }.build()

        try {
            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )

            // Immediately dispatch last known location if available
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    lastLocation = loc
                    myLocationConsumer?.onLocationChanged(loc, this)
                }
            }

            isRunning = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start FusedLocationProvider: ${e.message}", e)
            return false
        }
    }

    override fun stopLocationProvider() {
        if (!isRunning) return
        try {
            fusedClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates: ${e.message}", e)
        }
        isRunning = false
    }

    override fun getLastKnownLocation(): Location? = lastLocation

    override fun destroy() {
        stopLocationProvider()
        myLocationConsumer = null
    }

    companion object {
        private const val TAG = "FusedLocationProvider"
    }
}
