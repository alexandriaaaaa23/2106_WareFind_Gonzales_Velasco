package com.example.oop_warehouse_location.models

import com.example.oop_warehouse_location.models.base.BaseModel

/**
 * Warehouse entity — extends [BaseModel] for shared identity and timestamp.
 * Demonstrates INHERITANCE from the abstract base model.
 */
data class Warehouse(
    var warehouseId: String = "",
    var name: String = "Main Warehouse",
    var totalCapacity: Int = 500,
    var ownerId: String = "",              // Manager's UID
    var inviteCode: String = "",           // 6-char alphanumeric code
    var members: MutableList<String> = mutableListOf(),  // employee UIDs
    override var createdAt: Long = 0L,

    // ── NEW GIS FIELDS ──────────────────────────────
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var address: String = "",
    var radiusMeters: Double = 100.0   // geofence radius
) : BaseModel(id = warehouseId, createdAt = createdAt) {

    /** Returns true if geographic coordinates have been set. */
    fun hasCoordinates(): Boolean = latitude != 0.0 || longitude != 0.0

    /** Returns latitude and longitude formatted as decimal coordinates. */
    fun getFormattedCoordinates(): String =
        if (hasCoordinates()) String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude)
        else "No coordinates set"

    override fun displaySummary(): String {
        val locSuffix = if (hasCoordinates()) ", location: ${getFormattedCoordinates()}" else ""
        return "Warehouse: $name (capacity: $totalCapacity$locSuffix)"
    }
}
