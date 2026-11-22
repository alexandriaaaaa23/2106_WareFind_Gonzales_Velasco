package com.example.oop_warehouse_location.fragments

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.activities.MainActivity
import com.example.oop_warehouse_location.adapters.MemberAdapter
import com.example.oop_warehouse_location.fragments.base.BaseWarehouseFragment
import com.example.oop_warehouse_location.models.Warehouse
import com.example.oop_warehouse_location.models.user.User
import com.example.oop_warehouse_location.services.WarehouseGeofenceManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

class ManageWarehouseFragment : BaseWarehouseFragment() {

    private lateinit var memberAdapter: MemberAdapter
    private var currentOwnerId: String = ""
    private var loadedWarehouse: Warehouse? = null

    private var pendingLocationCallback: ((Double, Double) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            pendingLocationCallback?.let { fetchDeviceLocation(it) }
        } else {
            context?.let {
                Toast.makeText(it, R.string.location_permission_required, Toast.LENGTH_SHORT).show()
            }
        }
        pendingLocationCallback = null
    }

    companion object {
        fun newInstance(warehouseId: String): ManageWarehouseFragment {
            return ManageWarehouseFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WAREHOUSE_ID, warehouseId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memberAdapter = MemberAdapter(
            currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
            onRemoveClicked = { user ->
                showRemoveConfirmationDialog(user, user.fullName(), currentOwnerId)
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_manage_warehouse, container, false)

        view.findViewById<ImageView>(R.id.ivMenuButton).setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        val rvMembers = view.findViewById<RecyclerView>(R.id.rvMembers)
        rvMembers.layoutManager = LinearLayoutManager(requireContext())
        rvMembers.adapter = memberAdapter

        loadWarehouse(view)
        return view
    }

    private fun loadWarehouse(view: View) {
        if (warehouseId.isBlank()) {
            Toast.makeText(requireContext(), "No warehouse assigned", Toast.LENGTH_SHORT).show()
            return
        }

        warehouseRepository.getWarehouse(warehouseId)
            .addOnSuccessListener { warehouse ->
                if (!isAdded || this.view == null) return@addOnSuccessListener

                loadedWarehouse = warehouse
                currentOwnerId = warehouse.ownerId
                view.findViewById<TextView>(R.id.tvWarehouseName).text = warehouse.name
                view.findViewById<TextView>(R.id.tvInviteCode).text = warehouse.inviteCode

                view.findViewById<View>(R.id.inviteCodeCard).setOnClickListener {
                    val clipboard = requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", warehouse.inviteCode))
                    Toast.makeText(requireContext(), "Invite code copied!", Toast.LENGTH_SHORT).show()
                }

                // Bind Geographic Location Card
                bindLocationCard(view, warehouse)

                val memberIds = linkedSetOf<String>()
                if (warehouse.ownerId.isNotBlank()) memberIds.add(warehouse.ownerId)
                memberIds.addAll(warehouse.members)

                view.findViewById<TextView>(R.id.tvMemberCount).text =
                    getString(R.string.member_count, memberIds.size)

                userRepository.getUsersByIds(memberIds.toList())
                    .addOnSuccessListener { users ->
                        if (isAdded && this.view != null) {
                            bindMembers(view, users, warehouse.ownerId)
                        }
                    }
                    .addOnFailureListener {
                        if (isAdded && this.view != null) {
                            bindMembers(view, emptyList(), warehouse.ownerId)
                        }
                    }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    Toast.makeText(requireContext(), e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun bindLocationCard(view: View, warehouse: Warehouse) {
        val tvAddress = view.findViewById<TextView>(R.id.tvWarehouseAddress)
        val tvCoords = view.findViewById<TextView>(R.id.tvWarehouseCoordinates)
        val tvRadius = view.findViewById<TextView>(R.id.tvWarehouseRadius)
        val btnEdit = view.findViewById<View>(R.id.btnEditLocation)
        val locationCard = view.findViewById<View>(R.id.locationCard)

        tvAddress?.text = warehouse.address.ifBlank { getString(R.string.no_address_set) }
        tvCoords?.text = if (warehouse.hasCoordinates()) {
            "Coords: ${warehouse.getFormattedCoordinates()}"
        } else {
            getString(R.string.no_coordinates_set)
        }
        tvRadius?.text = getString(R.string.geofence_radius_format, warehouse.radiusMeters.toInt())

        val openEdit = View.OnClickListener {
            showEditLocationDialog(warehouse)
        }
        btnEdit?.setOnClickListener(openEdit)
        locationCard?.setOnClickListener(openEdit)
    }

    private fun fetchDeviceLocation(onLocation: (Double, Double) -> Unit) {
        val ctx = context ?: return
        val fineGranted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            pendingLocationCallback = onLocation
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        try {
            val locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                Toast.makeText(ctx, "Location service unavailable", Toast.LENGTH_SHORT).show()
                return
            }

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLocation: Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                        bestLocation = loc
                    }
                }
            }

            if (bestLocation != null) {
                onLocation(bestLocation.latitude, bestLocation.longitude)
                Toast.makeText(ctx, "GPS location acquired!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    ctx,
                    "Unable to acquire GPS fix. Please enter coordinates manually.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "Permission error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditLocationDialog(warehouse: Warehouse) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_edit_warehouse_location, null)
        val etAddress = dialogView.findViewById<EditText>(R.id.etAddress)
        val etLatitude = dialogView.findViewById<EditText>(R.id.etLatitude)
        val etLongitude = dialogView.findViewById<EditText>(R.id.etLongitude)
        val etRadius = dialogView.findViewById<EditText>(R.id.etRadius)
        val btnDetectGps = dialogView.findViewById<MaterialButton>(R.id.btnDetectGps)

        etAddress.setText(warehouse.address)
        if (warehouse.hasCoordinates()) {
            etLatitude.setText(String.format(Locale.US, "%.5f", warehouse.latitude))
            etLongitude.setText(String.format(Locale.US, "%.5f", warehouse.longitude))
        }
        etRadius.setText(warehouse.radiusMeters.toInt().toString())

        btnDetectGps.setOnClickListener {
            fetchDeviceLocation { lat, lng ->
                etLatitude.setText(String.format(Locale.US, "%.5f", lat))
                etLongitude.setText(String.format(Locale.US, "%.5f", lng))
            }
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val address = etAddress.text.toString().trim()
                val lat = etLatitude.text.toString().trim().toDoubleOrNull() ?: 0.0
                val lng = etLongitude.text.toString().trim().toDoubleOrNull() ?: 0.0
                val radius = etRadius.text.toString().trim().toDoubleOrNull() ?: 100.0

                if (lat < -90.0 || lat > 90.0) {
                    Toast.makeText(requireContext(), "Latitude must be between -90 and 90", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (lng < -180.0 || lng > 180.0) {
                    Toast.makeText(requireContext(), "Longitude must be between -180 and 180", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                warehouseRepository.updateWarehouseLocation(
                    warehouseId = warehouse.warehouseId,
                    latitude = lat,
                    longitude = lng,
                    address = address,
                    radiusMeters = radius
                ).addOnSuccessListener {
                    if (isAdded) {
                        Toast.makeText(requireContext(), R.string.location_updated_success, Toast.LENGTH_SHORT).show()
                        val updatedWh = warehouse.copy(latitude = lat, longitude = lng, address = address, radiusMeters = radius)
                        context?.let { ctx ->
                            WarehouseGeofenceManager(ctx).registerWarehouseGeofence(updatedWh)
                        }
                        view?.let { loadWarehouse(it) }
                    }
                }.addOnFailureListener { e ->
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "${getString(R.string.failed_to_update_location)}: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun bindMembers(view: View, users: List<User>, ownerId: String) {
        val rvMembers = view.findViewById<RecyclerView>(R.id.rvMembers)
        val emptyState = view.findViewById<TextView>(R.id.tvNoMembers)

        if (users.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            rvMembers.visibility = View.GONE
            memberAdapter.submitList(emptyList())
            return
        }

        emptyState.visibility = View.GONE
        rvMembers.visibility = View.VISIBLE
        memberAdapter.submitMembers(users, ownerId = ownerId)
    }

    private fun showRemoveConfirmationDialog(user: User, fullName: String, ownerId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_employee)
            .setMessage(getString(R.string.confirm_remove_employee, fullName))
            .setPositiveButton(R.string.remove) { _, _ ->
                val managerUid = FirebaseAuth.getInstance().currentUser?.uid ?: ownerId
                warehouseRepository.removeMemberFromWarehouse(
                    warehouseId = warehouseId,
                    memberUid = user.uid,
                    memberName = fullName,
                    managerUid = managerUid
                ).addOnSuccessListener {
                    if (isAdded) {
                        Toast.makeText(requireContext(), R.string.employee_removed_success, Toast.LENGTH_SHORT).show()
                        view?.let { loadWarehouse(it) }
                    }
                }.addOnFailureListener { e ->
                    if (isAdded) {
                        Toast.makeText(
                            requireContext(),
                            "${getString(R.string.failed_to_remove_employee)}: ${e.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
