package com.example.oop_warehouse_location.models.spatial

import android.graphics.Color
import android.graphics.RectF
import com.example.oop_warehouse_location.models.Parcel

/**
 * Spatial representation of a rack slot in the warehouse floor plan.
 * Stores normalized 2D coordinates (0.0 to 1.0) so it scales to any screen density.
 */
data class RackSlot(
    val slotId: String,
    val zoneId: String,
    val rackName: String,
    val shortCode: String,
    val tier: String,
    val bounds: RectF,
    val capacity: Int = 10
) {
    fun contains(normX: Float, normY: Float): Boolean {
        return bounds.contains(normX, normY)
    }
}

/**
 * Spatial representation of a functional zone within the warehouse floor plan.
 */
data class WarehouseZoneArea(
    val zoneId: String,
    val displayName: String,
    val bounds: RectF,
    val fillColor: Int,
    val strokeColor: Int,
    val slots: List<RackSlot>
) {
    fun contains(normX: Float, normY: Float): Boolean {
        return bounds.contains(normX, normY)
    }
}


object WarehouseSpatialMap {

    val ZONES: List<WarehouseZoneArea> by lazy {
        listOf(
            createZoneA(),
            createZoneB(),
            createZoneC(),
            createStagingZone()
        )
    }

    private fun createZoneA(): WarehouseZoneArea {
        val bounds = RectF(0.04f, 0.10f, 0.32f, 0.72f)
        val slots = listOf(
            // Column 1
            RackSlot("A-R01", "Zone A", "Rack 01 - Top", "R01", "Top", RectF(0.06f, 0.17f, 0.17f, 0.32f)),
            RackSlot("A-R03", "Zone A", "Rack 03 - Mid", "R03", "Mid", RectF(0.06f, 0.35f, 0.17f, 0.50f)),
            RackSlot("A-R05", "Zone A", "Rack 05 - Bottom", "R05", "Bottom", RectF(0.06f, 0.53f, 0.17f, 0.68f)),
            // Column 2
            RackSlot("A-R02", "Zone A", "Rack 02 - Top", "R02", "Top", RectF(0.19f, 0.17f, 0.30f, 0.32f)),
            RackSlot("A-R04", "Zone A", "Rack 04 - Mid", "R04", "Mid", RectF(0.19f, 0.35f, 0.30f, 0.50f)),
            RackSlot("A-R06", "Zone A", "Rack 06 - Floor", "R06", "Floor", RectF(0.19f, 0.53f, 0.30f, 0.68f))
        )
        return WarehouseZoneArea(
            zoneId = "Zone A",
            displayName = "Zone A (North-West)",
            bounds = bounds,
            fillColor = Color.parseColor("#EBF3FC"),
            strokeColor = Color.parseColor("#90CAF9"),
            slots = slots
        )
    }

    private fun createZoneB(): WarehouseZoneArea {
        val bounds = RectF(0.36f, 0.10f, 0.64f, 0.72f)
        val slots = listOf(
            // Column 1
            RackSlot("B-R01", "Zone B", "Rack 01 - Top", "R01", "Top", RectF(0.38f, 0.17f, 0.49f, 0.32f)),
            RackSlot("B-R03", "Zone B", "Rack 03 - Mid", "R03", "Mid", RectF(0.38f, 0.35f, 0.49f, 0.50f)),
            RackSlot("B-R05", "Zone B", "Rack 05 - Bottom", "R05", "Bottom", RectF(0.38f, 0.53f, 0.49f, 0.68f)),
            // Column 2
            RackSlot("B-R02", "Zone B", "Rack 02 - Top", "R02", "Top", RectF(0.51f, 0.17f, 0.62f, 0.32f)),
            RackSlot("B-R04", "Zone B", "Rack 04 - Mid", "R04", "Mid", RectF(0.51f, 0.35f, 0.62f, 0.50f)),
            RackSlot("B-R06", "Zone B", "Rack 06 - Floor", "R06", "Floor", RectF(0.51f, 0.53f, 0.62f, 0.68f))
        )
        return WarehouseZoneArea(
            zoneId = "Zone B",
            displayName = "Zone B (Central Core)",
            bounds = bounds,
            fillColor = Color.parseColor("#E8F5E9"),
            strokeColor = Color.parseColor("#A5D6A7"),
            slots = slots
        )
    }

    private fun createZoneC(): WarehouseZoneArea {
        val bounds = RectF(0.68f, 0.10f, 0.96f, 0.72f)
        val slots = listOf(
            // Column 1
            RackSlot("C-R01", "Zone C", "Rack 01 - Top", "R01", "Top", RectF(0.70f, 0.17f, 0.81f, 0.32f)),
            RackSlot("C-R03", "Zone C", "Rack 03 - Mid", "R03", "Mid", RectF(0.70f, 0.35f, 0.81f, 0.50f)),
            RackSlot("C-R05", "Zone C", "Rack 05 - Bottom", "R05", "Bottom", RectF(0.70f, 0.53f, 0.81f, 0.68f)),
            // Column 2
            RackSlot("C-R02", "Zone C", "Rack 02 - Top", "R02", "Top", RectF(0.83f, 0.17f, 0.94f, 0.32f)),
            RackSlot("C-R04", "Zone C", "Rack 04 - Mid", "R04", "Mid", RectF(0.83f, 0.35f, 0.94f, 0.50f)),
            RackSlot("C-R06", "Zone C", "Rack 06 - Floor", "R06", "Floor", RectF(0.83f, 0.53f, 0.94f, 0.68f))
        )
        return WarehouseZoneArea(
            zoneId = "Zone C",
            displayName = "Zone C (North-East)",
            bounds = bounds,
            fillColor = Color.parseColor("#FFF8E1"),
            strokeColor = Color.parseColor("#FFE082"),
            slots = slots
        )
    }

    private fun createStagingZone(): WarehouseZoneArea {
        val bounds = RectF(0.04f, 0.76f, 0.96f, 0.94f)
        val slots = listOf(
            RackSlot("STG-01", "Staging", "Inbound Dock", "IN-01", "Dock", RectF(0.08f, 0.81f, 0.46f, 0.91f)),
            RackSlot("STG-02", "Staging", "Outbound Staging", "OUT-01", "Dock", RectF(0.54f, 0.81f, 0.92f, 0.91f))
        )
        return WarehouseZoneArea(
            zoneId = "Staging",
            displayName = "Staging & Logistics Docks",
            bounds = bounds,
            fillColor = Color.parseColor("#F3E5F5"),
            strokeColor = Color.parseColor("#CE93D8"),
            slots = slots
        )
    }

    /**
     * Resolves a [Parcel] to its spatial [RackSlot] on the floor plan.
     */
    fun findSlotForParcel(parcel: Parcel): RackSlot? {
        val zone = ZONES.firstOrNull { it.zoneId.equals(parcel.zone, ignoreCase = true) }

        if (parcel.isOutboundStaged() || parcel.zone.equals("Staging", ignoreCase = true)) {
            val staging = ZONES.firstOrNull { it.zoneId == "Staging" }
            return staging?.slots?.find { it.slotId == "STG-02" } ?: staging?.slots?.firstOrNull()
        }

        if (zone == null) return null

        // Try matching exact rack name first
        var slot = zone.slots.firstOrNull { it.rackName.equals(parcel.rack, ignoreCase = true) }

        // If not found, match by short code (e.g. "Rack 01" or "R01")
        if (slot == null && parcel.rack.isNotBlank()) {
            slot = zone.slots.firstOrNull {
                parcel.rack.contains(it.shortCode, ignoreCase = true) ||
                    parcel.rack.contains(it.tier, ignoreCase = true)
            }
        }

        // Fallback to first available slot in that zone
        return slot ?: zone.slots.firstOrNull()
    }

    /**
     * Finds the [RackSlot] at the given normalized touch point (x, y in [0.0..1.0]).
     */
    fun findSlotAt(normX: Float, normY: Float): RackSlot? {
        for (zone in ZONES) {
            for (slot in zone.slots) {
                if (slot.contains(normX, normY)) {
                    return slot
                }
            }
        }
        return null
    }

    /**
     * Finds the [WarehouseZoneArea] at the given normalized touch point.
     */
    fun findZoneAt(normX: Float, normY: Float): WarehouseZoneArea? {
        return ZONES.firstOrNull { it.contains(normX, normY) }
    }

    /**
     * Returns all rack slots across all warehouse zones.
     */
    fun getAllSlots(): List<RackSlot> {
        return ZONES.flatMap { it.slots }
    }
}
