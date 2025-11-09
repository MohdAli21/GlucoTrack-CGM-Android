package com.mohammed.glucotrack

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sqrt

class PdfReportGenerator(private val context: Context) {

    private val textDarkColor = getColor(R.color.text_dark)
    private val textLightColor = getColor(R.color.text_light)
    private val lightGrayBorder = DeviceRgb(229, 231, 235)
    private val headerBgColor = DeviceRgb(243, 244, 246)

    fun createReport(records: List<GlucoseRecord>, userName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "GlucoTrack_AGP_Report_${System.currentTimeMillis()}.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            val outputStream: OutputStream? = resolver.openOutputStream(it)
            if (outputStream != null) {
                val writer = PdfWriter(outputStream)
                val pdf = PdfDocument(writer)
                val document = Document(pdf, PageSize.A4)

                // --- Build the PDF Document ---
                addHeader(document, userName, records)
                addSummaryAndPieChart(document, records)
                addAgpGraph(document, records)

                // NEW: Add a page break here
                document.add(AreaBreak())

                addDetailedTimeInRangeTable(document, records)
                addFooter(document)

                document.close()
                return it
            }
        }
        return null
    }

    private fun addHeader(document: Document, userName: String, records: List<GlucoseRecord>) {
        val table = Table(UnitValue.createPercentArray(floatArrayOf(20f, 60f, 20f))).useAllAvailableWidth()

        try {
            val logoDrawable = ContextCompat.getDrawable(context, R.drawable.app_logo)
            val bitmap = (logoDrawable as BitmapDrawable).bitmap
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imageData = ImageDataFactory.create(stream.toByteArray())
            table.addCell(Cell().add(Image(imageData).scaleToFit(50f, 50f)).setBorder(null))
        } catch (e: Exception) {
            table.addCell(Cell().add(Paragraph("")).setBorder(null))
        }

        val title = Paragraph("Ambulatory Glucose Profile (AGP)").setBold().setFontSize(18f).setTextAlignment(TextAlignment.CENTER)
        table.addCell(Cell().add(title).setBorder(null).setVerticalAlignment(VerticalAlignment.MIDDLE))
        table.addCell(Cell().setBorder(null))
        document.add(table)

        val name = Paragraph("Patient: $userName").setFontSize(11f)
        val dateRange = if (records.isNotEmpty()) {
            val sortedRecords = records.sortedBy { it.timestamp }
            val firstDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(sortedRecords.first().timestamp))
            val lastDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(sortedRecords.last().timestamp))
            Paragraph("Report Period: $firstDate - $lastDate").setFontSize(11f)
        } else {
            Paragraph("Report Period: N/A").setFontSize(11f)
        }
        document.add(name)
        document.add(dateRange)
        addDivider(document)
    }

    private fun addSummaryAndPieChart(document: Document, records: List<GlucoseRecord>) {
        if (records.isEmpty()) {
            document.add(Paragraph("No data available for this period."))
            return
        }

        val glucoseValues = records.map { it.value.toDouble() }
        val avgGlucose = glucoseValues.average()
        val gmi = 3.31 + (0.02392 * avgGlucose)
        val stdDev = calculateStdDev(glucoseValues)
        val cv = (stdDev / avgGlucose) * 100

        val timeInRange = records.count { it.value >= 70 && it.value <= 180 }
        val timeHigh = records.count { it.value > 180 }
        val timeLow = records.count { it.value < 70 }
        val total = records.size.toFloat()

        val summary = when {
            avgGlucose < 100 && cv < 36 -> "Overall Control: Excellent ✅"
            avgGlucose < 154 && cv < 36 -> "Overall Control: Good"
            else -> "Overall Control: Needs Improvement ⚠️"
        }
        document.add(Paragraph(summary).setBold().setFontColor(textDarkColor))
        document.add(Paragraph("\n"))

        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth()

        val statsTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f))).useAllAvailableWidth()
        statsTable.addCell(createCell("Average Glucose"))
        statsTable.addCell(createCell("${avgGlucose.toInt()} mg/dL", isValue = true))
        statsTable.addCell(createCell("Glucose Management Indicator (GMI)"))
        statsTable.addCell(createCell("${String.format("%.1f", gmi)}%", isValue = true))
        statsTable.addCell(createCell("Glucose Variability (CV)"))
        statsTable.addCell(createCell("${String.format("%.1f", cv)}%", isValue = true))
        table.addCell(Cell().add(statsTable).setBorder(null).setPaddingRight(10f))

        val pieChartBitmap = createPieChartBitmap(
            timeHigh / total * 100,
            timeInRange / total * 100,
            timeLow / total * 100
        )
        val image = Image(ImageDataFactory.create(bitmapToBytes(pieChartBitmap))).scaleToFit(150f, 150f)
        table.addCell(Cell().add(image).setBorder(null))

        document.add(table)
        addDivider(document)
    }

    private fun addAgpGraph(document: Document, records: List<GlucoseRecord>) {
        if (records.isEmpty()) return

        document.add(Paragraph("24-Hour Glucose Profile").setBold().setFontSize(14f))
        val agpBitmap = createAgpBitmap(records)
        val image = Image(ImageDataFactory.create(bitmapToBytes(agpBitmap)))
            .setWidth(document.getPdfDocument().getDefaultPageSize().getWidth() - 72)
            .setAutoScaleHeight(true)
        document.add(image)
    }

    private fun addDetailedTimeInRangeTable(document: Document, records: List<GlucoseRecord>) {
        if (records.isEmpty()) return

        document.add(Paragraph("Time in Ranges").setBold().setFontSize(14f))

        val timeVeryHigh = records.count { it.value > 250 }
        val timeHigh = records.count { it.value > 180 && it.value <= 250 }
        val timeInRange = records.count { it.value >= 70 && it.value <= 180 }
        val timeLow = records.count { it.value < 70 && it.value >= 54 }
        val timeVeryLow = records.count { it.value < 54 }
        val total = records.size.toFloat()

        val table = Table(UnitValue.createPercentArray(floatArrayOf(50f, 25f, 25f))).useAllAvailableWidth()
        table.addHeaderCell(createCell("Range", isHeader = true))
        table.addHeaderCell(createCell("Percentage", isHeader = true, isValue = true))
        table.addHeaderCell(createCell("Status", isHeader = true, isValue = true))

        table.addCell(createCell("Very High (> 250 mg/dL)"))
        table.addCell(createCell("${(timeVeryHigh/total*100).toInt()}%", isValue = true))
        table.addCell(createCell("❌", isValue = true))

        table.addCell(createCell("High (181-250 mg/dL)"))
        table.addCell(createCell("${(timeHigh/total*100).toInt()}%", isValue = true))
        table.addCell(createCell("⚠️", isValue = true))

        table.addCell(createCell("Target Range (70-180 mg/dL)"))
        table.addCell(createCell("${(timeInRange/total*100).toInt()}%", isValue = true))
        table.addCell(createCell("✅", isValue = true))

        table.addCell(createCell("Low (54-69 mg/dL)"))
        table.addCell(createCell("${(timeLow/total*100).toInt()}%", isValue = true))
        table.addCell(createCell("⚠️", isValue = true))

        table.addCell(createCell("Very Low (< 54 mg/dL)"))
        table.addCell(createCell("${(timeVeryLow/total*100).toInt()}%", isValue = true))
        table.addCell(createCell("❌", isValue = true))

        document.add(table)
    }

    private fun addFooter(document: Document) {
        document.add(Paragraph("\n"))
        document.add(Paragraph(context.getString(R.string.report_footer_notes)).setFontSize(9f))
        document.add(Paragraph(context.getString(R.string.report_disclaimer)).setFontSize(8f).setTextAlignment(TextAlignment.CENTER).setMarginTop(10f))
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val sumOfSquares = values.sumOf { (it - mean) * (it - mean) }
        return sqrt(sumOfSquares / (values.size - 1))
    }

    private fun createPieChartBitmap(high: Float, inRange: Float, low: Float): Bitmap {
        val pieChart = PieChart(context).apply {
            layout(0, 0, 400, 400)
            description.isEnabled = false
            legend.verticalAlignment = Legend.LegendVerticalAlignment.CENTER
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            legend.orientation = Legend.LegendOrientation.VERTICAL
            legend.setDrawInside(false)
            isDrawHoleEnabled = true
            holeRadius = 60f
            setHoleColor(Color.TRANSPARENT)
            setUsePercentValues(true)
        }

        val entries = ArrayList<PieEntry>()
        if (inRange > 0) entries.add(PieEntry(inRange, "In Range"))
        if (high > 0) entries.add(PieEntry(high, "High"))
        if (low > 0) entries.add(PieEntry(low, "Low"))

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                ContextCompat.getColor(context, R.color.status_green),
                ContextCompat.getColor(context, R.color.status_yellow),
                ContextCompat.getColor(context, R.color.status_red)
            )
            valueTextColor = Color.BLACK
            valueTextSize = 12f
        }

        pieChart.data = PieData(dataSet)
        pieChart.invalidate()
        return viewToBitmap(pieChart)
    }

    private fun createAgpBitmap(records: List<GlucoseRecord>): Bitmap {
        val combinedChart = CombinedChart(context).apply {
            layout(0, 0, 1000, 500)
            description.isEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            drawOrder = arrayOf(CombinedChart.DrawOrder.CANDLE, CombinedChart.DrawOrder.LINE)
        }

        val xAxis = combinedChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 4f
        xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()}:00"
            }
        }

        val yAxis = combinedChart.axisLeft
        yAxis.axisMinimum = 40f
        yAxis.axisMaximum = 400f

        val hourlyBuckets = Array<MutableList<Float>>(24) { mutableListOf() }
        val calendar = Calendar.getInstance()
        records.forEach {
            calendar.timeInMillis = it.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            hourlyBuckets[hour].add(it.value)
        }

        val medianEntries = ArrayList<Entry>()
        val rangeEntries = ArrayList<CandleEntry>()

        for (hour in 0..23) {
            val values = hourlyBuckets[hour].sorted()
            if (values.isNotEmpty()) {
                val median = values[values.size / 2]
                val p25 = values.getOrElse(values.size / 4) { median }
                val p75 = values.getOrElse(values.size * 3 / 4) { median }
                medianEntries.add(Entry(hour.toFloat(), median))
                rangeEntries.add(CandleEntry(hour.toFloat(), p75, p25, p25, p75))
            }
        }

        val medianDataSet = LineDataSet(medianEntries, "Median").apply {
            color = Color.BLACK
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
        }

        val rangeDataSet = CandleDataSet(rangeEntries, "Interquartile Range").apply {
            color = Color.LTGRAY
            setDrawValues(false)
            decreasingColor = Color.LTGRAY
            increasingColor = Color.LTGRAY
            shadowColor = Color.LTGRAY
        }

        val combinedData = CombinedData()
        combinedData.setData(LineData(medianDataSet))
        combinedData.setData(CandleData(rangeDataSet))

        combinedChart.data = combinedData
        combinedChart.invalidate()
        return viewToBitmap(combinedChart)
    }

    private fun viewToBitmap(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun createCell(text: String, isHeader: Boolean = false, isValue: Boolean = false): Cell {
        val cell = Cell().add(Paragraph(text)).setPadding(5f)
        if (isHeader) {
            cell.setBackgroundColor(headerBgColor).setBold().setTextAlignment(TextAlignment.CENTER)
        } else {
            cell.setBorder(SolidBorder(lightGrayBorder, 0.5f))
        }
        if (isValue) {
            cell.setTextAlignment(TextAlignment.RIGHT)
        }
        return cell
    }

    private fun addDivider(document: Document) {
        val table = Table(1).useAllAvailableWidth().setMarginTop(10f).setMarginBottom(10f)
        table.addCell(Cell().setBorder(SolidBorder(lightGrayBorder, 0.5f)))
        document.add(table)
    }

    private fun getColor(colorRes: Int): DeviceRgb {
        val colorInt = ContextCompat.getColor(context, colorRes)
        return DeviceRgb(Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
    }
}
