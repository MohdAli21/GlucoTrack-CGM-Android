package com.mohammed.glucotrack

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CustomMarkerView(context: Context, layoutResource: Int, private val startTime: Long) : MarkerView(context, layoutResource) {

    private val timeTextView: TextView = findViewById(R.id.marker_time_text)
    private val valueTextView: TextView = findViewById(R.id.marker_value_text)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // This method is called each time the marker is drawn
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let {
            val millis = startTime + it.x.toLong() * 1000
            timeTextView.text = timeFormat.format(Date(millis))
            valueTextView.text = "${it.y.toInt()} mg/dL"
        }
        super.refreshContent(e, highlight)
    }

    // This controls the marker's position
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2f), -height.toFloat() - 10)
    }
}