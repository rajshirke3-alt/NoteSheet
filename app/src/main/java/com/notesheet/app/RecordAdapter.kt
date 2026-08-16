package com.notesheet.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class RecordAdapter(
    private var items: List<PatientRecord>,
    private val onClick: (PatientRecord) -> Unit,
    private val onLongClick: (PatientRecord) -> Unit
) : RecyclerView.Adapter<RecordAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val bed: android.widget.TextView = view.findViewById(R.id.tvBed)
        val name: android.widget.TextView = view.findViewById(R.id.tvName)
        val consultant: android.widget.TextView = view.findViewById(R.id.tvConsultant)
        val details: android.widget.TextView = view.findViewById(R.id.tvDetails)
        val date: android.widget.TextView = view.findViewById(R.id.tvDate)
        val root: View = view.findViewById(R.id.rowRoot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bed.text = item.bedNumber
        holder.name.text = item.patientName
        holder.consultant.text = item.primaryConsultant
        holder.details.text = item.details
        holder.date.text = item.dateAdded
        val bg = if (position % 2 == 0) R.color.row_normal else R.color.row_alt
        holder.root.setBackgroundColor(ContextCompat.getColor(holder.root.context, bg))
        holder.root.setOnClickListener { onClick(item) }
        holder.root.setOnLongClickListener { onLongClick(item); true }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<PatientRecord>) {
        items = newItems
        notifyDataSetChanged()
    }
}
