package com.example.oop_warehouse_location.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.models.Parcel
import com.example.oop_warehouse_location.models.spatial.RackSlot
import com.example.oop_warehouse_location.models.spatial.WarehouseSpatialMap
import com.example.oop_warehouse_location.models.spatial.WarehouseZoneArea


class FloorPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Paints ──────────────────────────────────────────────────────────
    private val perimeterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
        color = Color.parseColor("#455A64")
    }

    private val zoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val zoneStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(6f), dpToPx(4f)), 0f)
    }

    private val aislePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#B0BEC5")
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f)
    }

    private val rackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val rackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = Color.parseColor("#CFD8DC")
    }

    private val selectedRackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
        color = ContextCompat.getColor(context, R.color.pink)
    }

    private val highlightedRackGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33B80049")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#263238")
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pink)
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }

    private val parcelPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.pink)
    }

    // ── State ────────────────────────────────────────────────────────────
    private var parcels: List<Parcel> = emptyList()
    private var slotParcelMap: Map<String, List<Parcel>> = emptyMap()

    private var selectedSlot: RackSlot? = null
    private var highlightedSlotId: String? = null
    private var activeZoneFilter: String? = null

    // Listeners
    var onSlotClickListener: ((slot: RackSlot, parcelsInSlot: List<Parcel>) -> Unit)? = null
    var onZoneClickListener: ((zone: WarehouseZoneArea) -> Unit)? = null

    // Temporary rect for drawing
    private val drawRect = RectF()

    init {
        // Minimum recommended size for floor map
        isClickable = true
        isFocusable = true
    }

    fun setParcels(list: List<Parcel>) {
        this.parcels = list
        // Group parcels into slots
        val map = mutableMapOf<String, MutableList<Parcel>>()
        WarehouseSpatialMap.getAllSlots().forEach { slot ->
            map[slot.slotId] = mutableListOf()
        }

        for (p in list) {
            val slot = WarehouseSpatialMap.findSlotForParcel(p)
            if (slot != null) {
                map.getOrPut(slot.slotId) { mutableListOf() }.add(p)
            }
        }
        this.slotParcelMap = map
        invalidate()
    }

    fun setSelectedSlot(slot: RackSlot?) {
        this.selectedSlot = slot
        invalidate()
    }

    fun getSelectedSlot(): RackSlot? = selectedSlot

    fun getParcelsForSlot(slot: RackSlot): List<Parcel> {
        return slotParcelMap[slot.slotId] ?: emptyList()
    }

    fun highlightParcel(parcelId: String?) {
        if (parcelId.isNullOrBlank()) {
            highlightedSlotId = null
            invalidate()
            return
        }
        val target = parcels.firstOrNull { it.parcelID.contains(parcelId, ignoreCase = true) }
        if (target != null) {
            val slot = WarehouseSpatialMap.findSlotForParcel(target)
            highlightedSlotId = slot?.slotId
            selectedSlot = slot
            slot?.let { onSlotClickListener?.invoke(it, slotParcelMap[it.slotId] ?: emptyList()) }
        } else {
            highlightedSlotId = null
        }
        invalidate()
    }

    fun setZoneFilter(zoneId: String?) {
        this.activeZoneFilter = if (zoneId.equals("All", ignoreCase = true) || zoneId.isNullOrBlank()) null else zoneId
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // Maintain roughly 4:3 or 1:1 floor plan aspect ratio if height is wrap_content
        val desiredHeight = (width * 0.95f).toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val resolvedHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> minOf(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> desiredHeight
        }
        setMeasuredDimension(width, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        // 1. Draw outer warehouse building boundary
        drawBuildingPerimeter(canvas, w, h)

        // 2. Draw walkways / aisles
        drawAislesAndTraffic(canvas, w, h)

        // 3. Draw zones
        WarehouseSpatialMap.ZONES.forEach { zone ->
            val isZoneDimmed = activeZoneFilter != null && !zone.zoneId.equals(activeZoneFilter, ignoreCase = true)
            drawZone(canvas, zone, w, h, isZoneDimmed)
        }

        // 4. Draw racks within each zone
        WarehouseSpatialMap.getAllSlots().forEach { slot ->
            val isSlotDimmed = activeZoneFilter != null && !slot.zoneId.equals(activeZoneFilter, ignoreCase = true)
            drawRackSlot(canvas, slot, w, h, isSlotDimmed)
        }

        // 5. Draw orientation compass / legend header
        drawMapDecorations(canvas, w, h)
    }

    private fun drawBuildingPerimeter(canvas: Canvas, w: Float, h: Float) {
        val pad = dpToPx(8f)
        drawRect.set(pad, pad, w - pad, h - pad)

        // Subtle warehouse concrete floor background
        val bgPaint = Paint().apply {
            color = Color.parseColor("#F8F9FA")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(drawRect, dpToPx(12f), dpToPx(12f), bgPaint)

        // Building wall perimeter
        canvas.drawRoundRect(drawRect, dpToPx(12f), dpToPx(12f), perimeterPaint)

        // Entrance Gate Indicator at bottom center
        val dockW = dpToPx(60f)
        val dockH = dpToPx(6f)
        val dockPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.pink)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            (w - dockW) / 2f,
            h - pad - (dockH / 2f),
            (w + dockW) / 2f,
            h - pad + (dockH / 2f),
            dpToPx(3f),
            dpToPx(3f),
            dockPaint
        )
    }

    private fun drawAislesAndTraffic(canvas: Canvas, w: Float, h: Float) {
        // Main cross-aisle separating storage bays from Staging dock
        val crossAisleY = h * 0.74f
        canvas.drawLine(w * 0.05f, crossAisleY, w * 0.95f, crossAisleY, aislePaint)

        // Vertical aisles between Zone A & B, and Zone B & C
        val aisle1X = w * 0.34f
        val aisle2X = w * 0.66f
        canvas.drawLine(aisle1X, h * 0.12f, aisle1X, h * 0.72f, aislePaint)
        canvas.drawLine(aisle2X, h * 0.12f, aisle2X, h * 0.72f, aislePaint)

        // Walkway text labels
        textPaint.textSize = dpToPx(9f)
        textPaint.color = Color.parseColor("#90A4AE")
        textPaint.isFakeBoldText = false
        canvas.drawText("AISLE 1", aisle1X, h * 0.42f, textPaint)
        canvas.drawText("AISLE 2", aisle2X, h * 0.42f, textPaint)
        canvas.drawText("▲ FORKLIFT MAIN TRANSIT LANE ▲", w * 0.5f, crossAisleY - dpToPx(3f), textPaint)
    }

    private fun drawZone(canvas: Canvas, zone: WarehouseZoneArea, w: Float, h: Float, isDimmed: Boolean) {
        drawRect.set(
            zone.bounds.left * w,
            zone.bounds.top * h,
            zone.bounds.right * w,
            zone.bounds.bottom * h
        )

        zoneFillPaint.color = if (isDimmed) Color.parseColor("#F5F5F5") else zone.fillColor
        zoneFillPaint.alpha = if (isDimmed) 100 else 180
        canvas.drawRoundRect(drawRect, dpToPx(8f), dpToPx(8f), zoneFillPaint)

        zoneStrokePaint.color = if (isDimmed) Color.parseColor("#E0E0E0") else zone.strokeColor
        canvas.drawRoundRect(drawRect, dpToPx(8f), dpToPx(8f), zoneStrokePaint)

        // Zone title tag
        textPaint.textSize = dpToPx(11f)
        textPaint.color = if (isDimmed) Color.parseColor("#BDBDBD") else Color.parseColor("#37474F")
        textPaint.isFakeBoldText = true

        val titleY = drawRect.top + dpToPx(13f)
        canvas.drawText(zone.zoneId.uppercase(), drawRect.centerX(), titleY, textPaint)
    }

    private fun drawRackSlot(canvas: Canvas, slot: RackSlot, w: Float, h: Float, isDimmed: Boolean) {
        drawRect.set(
            slot.bounds.left * w,
            slot.bounds.top * h,
            slot.bounds.right * w,
            slot.bounds.bottom * h
        )

        val parcelsInSlot = slotParcelMap[slot.slotId] ?: emptyList()
        val count = parcelsInSlot.size
        val isSelected = selectedSlot?.slotId == slot.slotId
        val isHighlighted = highlightedSlotId == slot.slotId

        // Determine Heatmap color based on occupancy
        val (fillColor, strokeColor) = when {
            isDimmed -> Pair(Color.parseColor("#FAFAFA"), Color.parseColor("#EEEEEE"))
            count == 0 -> Pair(Color.WHITE, Color.parseColor("#CFD8DC"))
            count in 1..2 -> Pair(Color.parseColor("#FCE4EC"), Color.parseColor("#F48FB1")) // Low
            count in 3..5 -> Pair(Color.parseColor("#F8BBD0"), Color.parseColor("#EC407A")) // Medium
            else -> Pair(Color.parseColor("#F48FB1"), Color.parseColor("#B80049"))          // High density
        }

        rackFillPaint.color = fillColor
        canvas.drawRoundRect(drawRect, dpToPx(6f), dpToPx(6f), rackFillPaint)

        // Glow highlight if selected/searched
        if (isHighlighted || isSelected) {
            canvas.drawRoundRect(drawRect, dpToPx(6f), dpToPx(6f), highlightedRackGlow)
            canvas.drawRoundRect(drawRect, dpToPx(6f), dpToPx(6f), selectedRackPaint)
        } else {
            rackStrokePaint.color = strokeColor
            canvas.drawRoundRect(drawRect, dpToPx(6f), dpToPx(6f), rackStrokePaint)
        }

        // Rack Label & Tier
        textPaint.textSize = dpToPx(9.5f)
        textPaint.isFakeBoldText = true
        textPaint.color = if (isDimmed) Color.parseColor("#BDBDBD") else Color.parseColor("#263238")
        val labelY = drawRect.top + dpToPx(12f)
        canvas.drawText(slot.shortCode, drawRect.centerX(), labelY, textPaint)

        textPaint.textSize = dpToPx(7.5f)
        textPaint.isFakeBoldText = false
        textPaint.color = if (isDimmed) Color.parseColor("#E0E0E0") else Color.parseColor("#78909C")
        canvas.drawText(slot.tier, drawRect.centerX(), labelY + dpToPx(9f), textPaint)

        // Draw parcel dots or occupancy badge
        if (count > 0 && !isDimmed) {
            val badgeRadius = dpToPx(8f)
            val badgeCx = drawRect.right - badgeRadius - dpToPx(2f)
            val badgeCy = drawRect.top + badgeRadius + dpToPx(2f)

            badgePaint.color = ContextCompat.getColor(context, R.color.pink)
            canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgePaint)

            badgeTextPaint.textSize = dpToPx(8f)
            val textY = badgeCy + dpToPx(3f)
            canvas.drawText(if (count > 99) "99+" else count.toString(), badgeCx, textY, badgeTextPaint)

            // Draw small parcel dots inside the rack slot if there's enough room
            drawParcelDots(canvas, drawRect, count)
        }
    }

    private fun drawParcelDots(canvas: Canvas, rect: RectF, count: Int) {
        val dotRadius = dpToPx(2.5f)
        val maxDots = minOf(count, 4)
        val startY = rect.bottom - dpToPx(8f)
        val dotSpacing = dpToPx(7f)
        val totalWidth = (maxDots - 1) * dotSpacing
        var startX = rect.centerX() - (totalWidth / 2f)

        for (i in 0 until maxDots) {
            canvas.drawCircle(startX, startY, dotRadius, parcelPinPaint)
            startX += dotSpacing
        }
    }

    private fun drawMapDecorations(canvas: Canvas, w: Float, h: Float) {
        // Warehouse entrance label at the bottom
        textPaint.textSize = dpToPx(9f)
        textPaint.color = Color.parseColor("#546E7A")
        textPaint.isFakeBoldText = true
        canvas.drawText("WAREHOUSE DOCKS & MAIN ENTRANCE", w * 0.5f, h - dpToPx(13f), textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val normX = event.x / width.toFloat()
            val normY = event.y / height.toFloat()

            val tappedSlot = WarehouseSpatialMap.findSlotAt(normX, normY)
            if (tappedSlot != null) {
                selectedSlot = tappedSlot
                highlightedSlotId = tappedSlot.slotId
                invalidate()
                val parcelsInSlot = slotParcelMap[tappedSlot.slotId] ?: emptyList()
                onSlotClickListener?.invoke(tappedSlot, parcelsInSlot)
                return true
            }

            val tappedZone = WarehouseSpatialMap.findZoneAt(normX, normY)
            if (tappedZone != null) {
                onZoneClickListener?.invoke(tappedZone)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dpToPx(dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
