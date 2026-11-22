package com.example.oop_warehouse_location.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.activities.MainActivity
import com.example.oop_warehouse_location.models.ActivityLog
import com.example.oop_warehouse_location.repository.firebase.FirebaseActivityLogRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.auth.FirebaseAuth

/**
 * GeofenceBroadcastReceiver — Automated Spatial Trigger Receiver (Phase 4).
 *
 * Captures background GPS geofence transitions when employees enter, dwell in,
 * or exit the warehouse perimeter:
 * - GEOFENCE_TRANSITION_ENTER / DWELL: Logs arrival audit trail & pushes welcome/active shift notification
 * - GEOFENCE_TRANSITION_EXIT: Logs departure audit trail & pushes checkout notification
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val CHANNEL_ID = "warehouse_geofence_channel"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofencing error code: ${geofencingEvent.errorCode} - $errorMessage")
            return
        }

        val transition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return

        for (geofence in triggeringGeofences) {
            val warehouseId = geofence.requestId
            handleGeofenceTransition(context, warehouseId, transition)
        }
    }

    private fun handleGeofenceTransition(context: Context, warehouseId: String, transitionType: Int) {
        val auth = FirebaseAuth.getInstance()
        val currentUserId = auth.currentUser?.uid.orEmpty()
        val currentUserName = auth.currentUser?.displayName.orEmpty().ifBlank { "Staff Member" }

        val logRepo = FirebaseActivityLogRepository(warehouseId = warehouseId)

        when (transitionType) {
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> {
                Log.d(TAG, "Entered warehouse geofence: $warehouseId")

                val title = "On-Site Check-In"
                val message = "Welcome, $currentUserName! You are inside the warehouse geofence. Ready for inventory operations."
                showNotification(context, warehouseId, title, message)

                // Record audit log to Firestore
                val log = ActivityLog(
                    parcelId = "",
                    userId = currentUserId,
                    action = "Staff arrived on-site ($currentUserName entered warehouse boundary)"
                ).apply { createLog() }
                logRepo.logActivity(log)
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d(TAG, "Exited warehouse geofence: $warehouseId")

                val title = "Departure Detected"
                val message = "You have departed from the warehouse grounds. Shift departure logged."
                showNotification(context, warehouseId, title, message)

                // Record audit log to Firestore
                val log = ActivityLog(
                    parcelId = "",
                    userId = currentUserId,
                    action = "Staff departed from facility grounds ($currentUserName)"
                ).apply { createLog() }
                logRepo.logActivity(log)
            }
        }
    }

    private fun showNotification(
        context: Context,
        warehouseId: String,
        title: String,
        message: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Warehouse Geofence Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications triggered upon arriving or departing from warehouse grounds"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("warehouseId", warehouseId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, warehouseId.hashCode(), tapIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BASE + (warehouseId.hashCode() % 1000), notification)
    }
}
