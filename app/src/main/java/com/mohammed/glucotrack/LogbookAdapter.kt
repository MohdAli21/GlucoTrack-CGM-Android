package com.mohammed.glucotrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

// Add a listener parameter to the constructor
class LogbookAdapter(
    private val records: MutableList<GlucoseRecord>,
    private val onItemClicked: (GlucoseRecord) -> Unit
) : RecyclerView.Adapter<LogbookAdapter.LogViewHolder>() {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.log_date_text)
        val valueText: TextView = view.findViewById(R.id.log_value_text)
        val eventIcon: ImageView = view.findViewById(R.id.log_event_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val record = records[position]
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        holder.dateText.text = dateFormat.format(Date(record.timestamp))
        holder.valueText.text = "${record.value.toInt()} mg/dL"

        if (record.eventType != null) {
            holder.eventIcon.visibility = View.VISIBLE
            holder.eventIcon.setImageResource(
                when (record.eventType) {
                    "MEAL" -> R.drawable.ic_meal
                    "EXERCISE" -> R.drawable.ic_exercise
                    "INSULIN" -> R.drawable.ic_insulin
                    else -> 0
                }
            )
        } else {
            holder.eventIcon.visibility = View.GONE
        }

        // Set the click listener on the whole item view
        holder.itemView.setOnClickListener {
            onItemClicked(record)
        }
    }

    override fun getItemCount() = records.size

    // Function to update the list after an item is deleted
    fun removeItem(record: GlucoseRecord) {
        val position = records.indexOf(record)
        if (position > -1) {
            records.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}