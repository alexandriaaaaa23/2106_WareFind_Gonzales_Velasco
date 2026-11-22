package com.example.oop_warehouse_location.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.oop_warehouse_location.R
import com.example.oop_warehouse_location.adapters.base.BaseListAdapter
import com.example.oop_warehouse_location.models.Parcel

class FloorParcelAdapter(
    private val onParcelClicked: (Parcel) -> Unit
) : BaseListAdapter<Parcel, FloorParcelAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_floor_parcel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvParcelId: TextView = itemView.findViewById(R.id.tvFloorParcelId)
        private val tvRecipient: TextView = itemView.findViewById(R.id.tvFloorRecipient)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tvFloorStatusBadge)

        fun bind(parcel: Parcel) {
            tvParcelId.text = parcel.parcelID
            val carrier = parcel.carrier.ifBlank { "Carrier" }
            val name = parcel.receiptName.ifBlank { "Recipient" }
            val bin = parcel.binCoordinate.ifBlank { parcel.rack }
            tvRecipient.text = "$name · $carrier ($bin)"

            tvStatusBadge.text = parcel.status
            val (bgColor, textColor) = when {
                parcel.isShipped() -> Pair(R.drawable.bg_badge_orange, R.color.orangeAccent)
                parcel.isOutboundStaged() -> Pair(R.drawable.bg_badge_orange, R.color.orangeAccent)
                else -> Pair(R.drawable.bg_badge_green, R.color.greenAccent)
            }
            tvStatusBadge.setBackgroundResource(bgColor)
            tvStatusBadge.setTextColor(ContextCompat.getColor(itemView.context, textColor))

            itemView.setOnClickListener {
                onParcelClicked(parcel)
            }
        }
    }
}
