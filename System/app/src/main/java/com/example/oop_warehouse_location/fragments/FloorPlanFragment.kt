package com.example.oop_warehouse_location.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.adapters.FloorParcelAdapter
import com.example.oop_warehouse_location.fragments.base.BaseWarehouseFragment
import com.example.oop_warehouse_location.models.Parcel
import com.example.oop_warehouse_location.models.spatial.RackSlot
import com.example.oop_warehouse_location.views.FloorPlanView
import com.google.firebase.firestore.ListenerRegistration


class FloorPlanFragment : BaseWarehouseFragment() {

    private lateinit var floorPlanView: FloorPlanView
    private lateinit var adapter: FloorParcelAdapter
    private val allParcels = mutableListOf<Parcel>()
    private var parcelsListener: ListenerRegistration? = null

    private var activeZone: String? = null
    private var targetParcelId: String? = null

    // UI references
    private lateinit var tvParcelCountBadge: TextView
    private lateinit var layoutSelectedSlotInfo: LinearLayout
    private lateinit var layoutNoSlotPrompt: LinearLayout
    private lateinit var tvSelectedSlotZone: TextView
    private lateinit var tvSelectedSlotRack: TextView
    private lateinit var tvSelectedSlotCount: TextView
    private lateinit var tvSlotEmptyMessage: TextView
    private lateinit var rvSlotParcels: RecyclerView

    private lateinit var chipAll: TextView
    private lateinit var chipA: TextView
    private lateinit var chipB: TextView
    private lateinit var chipC: TextView
    private lateinit var chipStaging: TextView

    companion object {
        private const val ARG_INITIAL_ZONE = "initialZone"
        private const val ARG_TARGET_PARCEL = "targetParcel"

        fun newInstance(
            warehouseId: String,
            initialZone: String? = null,
            targetParcelId: String? = null
        ): FloorPlanFragment {
            return FloorPlanFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WAREHOUSE_ID, warehouseId)
                    putString(ARG_INITIAL_ZONE, initialZone)
                    putString(ARG_TARGET_PARCEL, targetParcelId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeZone = arguments?.getString(ARG_INITIAL_ZONE)
        targetParcelId = arguments?.getString(ARG_TARGET_PARCEL)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_floor_plan, container, false)
        bindViews(view)
        setupZoneChips(view)
        setupSearch(view)
        setupFloorPlanInteractions()
        observeWarehouseParcels()
        return view
    }

    private fun bindViews(view: View) {
        val ivBack = view.findViewById<ImageView>(R.id.ivFloorBack)
        ivBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        tvParcelCountBadge = view.findViewById(R.id.tvFloorParcelCountBadge)
        floorPlanView = view.findViewById(R.id.floorPlanView)

        layoutSelectedSlotInfo = view.findViewById(R.id.layoutSelectedSlotInfo)
        layoutNoSlotPrompt = view.findViewById(R.id.layoutNoSlotPrompt)
        tvSelectedSlotZone = view.findViewById(R.id.tvSelectedSlotZone)
        tvSelectedSlotRack = view.findViewById(R.id.tvSelectedSlotRack)
        tvSelectedSlotCount = view.findViewById(R.id.tvSelectedSlotCount)
        tvSlotEmptyMessage = view.findViewById(R.id.tvSlotEmptyMessage)

        rvSlotParcels = view.findViewById(R.id.rvSlotParcels)
        rvSlotParcels.layoutManager = LinearLayoutManager(requireContext())
        adapter = FloorParcelAdapter { parcel ->
            openParcelDetail(parcel)
        }
        rvSlotParcels.adapter = adapter
    }

    private fun setupZoneChips(view: View) {
        chipAll = view.findViewById(R.id.chipFloorZoneAll)
        chipA = view.findViewById(R.id.chipFloorZoneA)
        chipB = view.findViewById(R.id.chipFloorZoneB)
        chipC = view.findViewById(R.id.chipFloorZoneC)
        chipStaging = view.findViewById(R.id.chipFloorZoneStaging)

        chipAll.setOnClickListener { setZoneFilter(null) }
        chipA.setOnClickListener { setZoneFilter("Zone A") }
        chipB.setOnClickListener { setZoneFilter("Zone B") }
        chipC.setOnClickListener { setZoneFilter("Zone C") }
        chipStaging.setOnClickListener { setZoneFilter("Staging") }

        updateChipStyles()
    }

    private fun setZoneFilter(zoneId: String?) {
        activeZone = zoneId
        updateChipStyles()
        floorPlanView.setZoneFilter(zoneId)

        // If currently selected slot is outside the filtered zone, deselect it
        val currentSlot = floorPlanView.getSelectedSlot()
        if (currentSlot != null && zoneId != null && !currentSlot.zoneId.equals(zoneId, ignoreCase = true)) {
            floorPlanView.setSelectedSlot(null)
            layoutSelectedSlotInfo.visibility = View.GONE
            layoutNoSlotPrompt.visibility = View.VISIBLE
        }
    }

    private fun updateChipStyles() {
        val ctx = context ?: return
        val activeBg = R.drawable.bg_badge_pink_solid
        val inactiveBg = R.drawable.bg_card_rounded
        val activeText = ContextCompat.getColor(ctx, R.color.white)
        val inactiveText = ContextCompat.getColor(ctx, R.color.black)

        fun styleChip(chip: TextView, isSelected: Boolean) {
            chip.setBackgroundResource(if (isSelected) activeBg else inactiveBg)
            chip.setTextColor(if (isSelected) activeText else inactiveText)
        }

        styleChip(chipAll, activeZone == null)
        styleChip(chipA, activeZone.equals("Zone A", ignoreCase = true))
        styleChip(chipB, activeZone.equals("Zone B", ignoreCase = true))
        styleChip(chipC, activeZone.equals("Zone C", ignoreCase = true))
        styleChip(chipStaging, activeZone.equals("Staging", ignoreCase = true))
    }

    private fun setupSearch(view: View) {
        val etSearch = view.findViewById<EditText>(R.id.etFloorSearch)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                floorPlanView.highlightParcel(query)
            }
        })
    }

    private fun setupFloorPlanInteractions() {
        floorPlanView.onSlotClickListener = { slot, parcelsInSlot ->
            displaySlotDetails(slot, parcelsInSlot)
        }

        floorPlanView.onZoneClickListener = { zone ->
            setZoneFilter(zone.zoneId)
        }
    }

    private fun displaySlotDetails(slot: RackSlot, parcelsInSlot: List<Parcel>) {
        layoutNoSlotPrompt.visibility = View.GONE
        layoutSelectedSlotInfo.visibility = View.VISIBLE

        tvSelectedSlotZone.text = slot.zoneId.uppercase()
        tvSelectedSlotRack.text = "${slot.rackName} (${slot.tier})"

        val count = parcelsInSlot.size
        tvSelectedSlotCount.text = "$count Parcel${if (count == 1) "" else "s"}"

        if (count == 0) {
            tvSlotEmptyMessage.visibility = View.VISIBLE
            rvSlotParcels.visibility = View.GONE
            adapter.submitList(emptyList())
        } else {
            tvSlotEmptyMessage.visibility = View.GONE
            rvSlotParcels.visibility = View.VISIBLE
            adapter.submitList(parcelsInSlot)
        }
    }

    private fun observeWarehouseParcels() {
        parcelsListener?.let {
            it.remove()
            listeners.remove(it)
        }

        val registration = parcelRepository.observeParcels { parcels ->
            if (isAdded && view != null) {
                allParcels.clear()
                allParcels.addAll(parcels)

                val activeList = parcels.filter { it.isInStock() || it.isOutboundStaged() }
                tvParcelCountBadge.text = "${activeList.size} Active"
                floorPlanView.setParcels(activeList)

                // If a slot is currently selected, refresh its details list
                floorPlanView.getSelectedSlot()?.let { slot ->
                    val updatedParcelsInSlot = floorPlanView.getParcelsForSlot(slot)
                    displaySlotDetails(slot, updatedParcelsInSlot)
                }

                // If target parcel was requested on fragment launch, highlight it
                if (!targetParcelId.isNullOrBlank()) {
                    floorPlanView.highlightParcel(targetParcelId)
                    targetParcelId = null
                }
            }
        }
        parcelsListener = registration
        listeners.add(registration)
    }

    private fun openParcelDetail(parcel: Parcel) {
        val bottomSheet = ParcelDetailBottomSheetFragment.newInstance(warehouseId, parcel)
        bottomSheet.onActionCompleted = {
            // Firestore real-time listener will automatically update the floor map
        }
        bottomSheet.show(childFragmentManager, "FloorParcelDetail")
    }

    override fun onDestroyView() {
        parcelsListener = null
        super.onDestroyView()
    }
}
