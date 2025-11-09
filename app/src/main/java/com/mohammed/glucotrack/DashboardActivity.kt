package com.mohammed.glucotrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random

class DashboardActivity : AppCompatActivity() {

    // --- Views & Other variables ---
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var glucoseValueText: TextView
    private lateinit var trendArrowImage: ImageView
    private lateinit var lineChart: LineChart
    private lateinit var settingsIcon: ImageView
    private lateinit var logbookIcon: ImageView
    private lateinit var logMealButton: ImageButton
    private lateinit var logExerciseButton: ImageButton
    private lateinit var logInsulinButton: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private var startTime = System.currentTimeMillis()
    private var currentGlucose = 100.0

    // --- Advanced Simulation Parameters ---
    private var glucoseState = GlucoseState.FASTING
    private var basalRate = 90.0
    private var mealStartTime = 0L
    private var peakGlucose = 0.0
    private var timeToPeakMinutes = 50.0
    private var recoveryDurationMinutes = 120.0
    private val recentReadings = ArrayList<Pair<Long, Double>>()

    // --- Alert Logic ---
    private var hasSentHighAlert = false
    private var hasSentLowAlert = false
    private var highGlucoseThreshold = 180
    private var lowGlucoseThreshold = 70
    private val NOTIFICATION_CHANNEL_ID = "GLUCOSE_ALERTS"

    private lateinit var db: AppDatabase

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                loadAlertThresholds()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_dashboard)

        db = AppDatabase.getDatabase(this)

        initializeViews()
        loadAlertThresholds()

        val mainLayout: ConstraintLayout = findViewById(R.id.main_layout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom, left = insets.left, right = insets.right)
            WindowInsetsCompat.CONSUMED
        }

        createNotificationChannel()
        askForNotificationPermission()
        setupSimulationParameters()
        setupChart()
        startDataSimulation()
    }

    private fun initializeViews() {
        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        glucoseValueText = findViewById(R.id.glucose_value_text)
        trendArrowImage = findViewById(R.id.trend_arrow_image)
        lineChart = findViewById(R.id.line_chart)
        settingsIcon = findViewById(R.id.settings_icon)
        logbookIcon = findViewById(R.id.logbook_icon)
        logMealButton = findViewById(R.id.log_meal_button)
        logExerciseButton = findViewById(R.id.log_exercise_button)
        logInsulinButton = findViewById(R.id.log_insulin_button)

        settingsIcon.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                putExtra("CURRENT_HIGH", highGlucoseThreshold)
                putExtra("CURRENT_LOW", lowGlucoseThreshold)
            }
            settingsLauncher.launch(intent)
        }

        logbookIcon.setOnClickListener {
            startActivity(Intent(this, LogbookActivity::class.java))
        }

        logMealButton.setOnClickListener { logEvent("MEAL") }
        logExerciseButton.setOnClickListener { logEvent("EXERCISE") }
        logInsulinButton.setOnClickListener { logEvent("INSULIN") }
    }

    private fun setupSimulationParameters() {
        val isDiabetic = intent.getBooleanExtra("IS_DIABETIC", false)
        val ageCategory = intent.getStringExtra("AGE_CATEGORY")

        basalRate = when {
            isDiabetic && ageCategory?.contains("Senior") == true -> Random.nextDouble(90.0, 200.0)
            isDiabetic -> Random.nextDouble(80.0, 180.0)
            else -> Random.nextDouble(70.0, 99.0)
        }
        currentGlucose = basalRate
    }

    private fun calculateNextGlucose(): Double {
        val now = System.currentTimeMillis()
        val minutesSinceMeal = if (mealStartTime == 0L) Long.MAX_VALUE else (now - mealStartTime) / 60000

        when (glucoseState) {
            GlucoseState.POSTPRANDIAL_RISING -> {
                if (minutesSinceMeal >= timeToPeakMinutes) {
                    glucoseState = GlucoseState.POSTPRANDIAL_FALLING
                } else {
                    val progress = minutesSinceMeal / timeToPeakMinutes
                    currentGlucose = basalRate + (peakGlucose - basalRate) * progress
                }
            }
            GlucoseState.POSTPRANDIAL_FALLING -> {
                val minutesAfterPeak = minutesSinceMeal - timeToPeakMinutes
                if (minutesAfterPeak >= recoveryDurationMinutes) {
                    glucoseState = GlucoseState.FASTING
                    currentGlucose = basalRate
                } else {
                    val progress = minutesAfterPeak / recoveryDurationMinutes
                    currentGlucose = peakGlucose - (peakGlucose - basalRate) * progress
                }
            }
            GlucoseState.FASTING -> {
                val drift = (basalRate - currentGlucose) * 0.1
                currentGlucose += drift
            }
        }

        currentGlucose += Random.nextDouble(-3.0, 3.0)
        return currentGlucose.coerceIn(45.0, 400.0)
    }

    private fun logEvent(eventType: String) {
        val isDiabetic = intent.getBooleanExtra("IS_DIABETIC", false)

        if (eventType == "MEAL") {
            val mealTypeId = intent.getIntExtra("MEAL_TYPE_ID", -1)
            val spike = when (mealTypeId) {
                R.id.radio_light_snack -> if (isDiabetic) Random.nextDouble(30.0, 60.0) else Random.nextDouble(10.0, 20.0)
                R.id.radio_balanced_meal -> if (isDiabetic) Random.nextDouble(50.0, 90.0) else Random.nextDouble(20.0, 40.0)
                R.id.radio_heavy_meal -> if (isDiabetic) Random.nextDouble(80.0, 180.0) else Random.nextDouble(40.0, 70.0)
                else -> 0.0
            }
            recoveryDurationMinutes = when (mealTypeId) {
                R.id.radio_light_snack -> if (isDiabetic) Random.nextDouble(120.0, 180.0) else Random.nextDouble(60.0, 90.0)
                R.id.radio_balanced_meal -> if (isDiabetic) Random.nextDouble(120.0, 240.0) else Random.nextDouble(90.0, 120.0)
                R.id.radio_heavy_meal -> if (isDiabetic) Random.nextDouble(180.0, 360.0) else Random.nextDouble(120.0, 180.0)
                else -> 120.0
            }

            glucoseState = GlucoseState.POSTPRANDIAL_RISING
            mealStartTime = System.currentTimeMillis()
            peakGlucose = currentGlucose + spike
            timeToPeakMinutes = Random.nextDouble(45.0, 60.0)
        }

        lifecycleScope.launch {
            val record = GlucoseRecord(
                timestamp = System.currentTimeMillis(),
                value = currentGlucose.toFloat(),
                eventType = eventType
            )
            db.glucoseDao().insert(record)
            Toast.makeText(this@DashboardActivity, "$eventType logged", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addEntryToChart(glucoseValue: Float) {
        val data = lineChart.data ?: LineData()
        lineChart.data = data
        var set = data.getDataSetByIndex(0) as? LineDataSet
        if (set == null) {
            set = createSet()
            data.addDataSet(set)
        }
        val now = System.currentTimeMillis()
        val timeElapsed = (now - startTime) / 1000f
        val newEntry = Entry(timeElapsed, glucoseValue)
        data.addEntry(newEntry, 0)
        adjustYAxis(set)
        data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(7200f)
        lineChart.moveViewToX(newEntry.x)
        lifecycleScope.launch {
            db.glucoseDao().insert(GlucoseRecord(timestamp = now, value = glucoseValue))
        }
    }

    private fun adjustYAxis(dataSet: LineDataSet) {
        if (dataSet.entryCount > 0) {
            val yMin = dataSet.yMin - 20f
            val yMax = dataSet.yMax + 20f
            lineChart.axisLeft.axisMinimum = yMin
            lineChart.axisLeft.axisMaximum = yMax
        }
    }

    private fun loadAlertThresholds() {
        val prefs = getSharedPreferences("GlucoTrackPrefs", Context.MODE_PRIVATE)
        highGlucoseThreshold = prefs.getInt("HIGH_GLUCOSE_THRESHOLD", 180)
        lowGlucoseThreshold = prefs.getInt("LOW_GLUCOSE_THRESHOLD", 70)
    }

    private fun startDataSimulation() {
        lineChart.data?.clearValues()
        lineChart.invalidate()
        startTime = System.currentTimeMillis()
        currentGlucose = basalRate
        val dataSimulator = object : Runnable {
            override fun run() {
                updateDateTime()
                val previousGlucose = currentGlucose
                currentGlucose = calculateNextGlucose()
                updateGlucoseUI(previousGlucose, currentGlucose)
                addEntryToChart(currentGlucose.toFloat())
                handler.postDelayed(this, 30000)
            }
        }
        handler.post(dataSimulator)
    }

    private fun updateDateTime() {
        timeText.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        dateText.text = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    private fun updateGlucoseUI(previousGlucose: Double, newGlucose: Double) {
        val glucoseInt = newGlucose.toInt()
        glucoseValueText.text = glucoseInt.toString()
        if (glucoseInt > highGlucoseThreshold) {
            glucoseValueText.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            if (!hasSentHighAlert) {
                sendNotification("High Glucose Alert", "Your glucose is $glucoseInt mg/dL.", 1)
                hasSentHighAlert = true
            }
        } else if (glucoseInt < lowGlucoseThreshold) {
            glucoseValueText.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            if (!hasSentLowAlert) {
                sendNotification("Low Glucose Alert", "Your glucose is $glucoseInt mg/dL.", 2)
                hasSentLowAlert = true
            }
        } else {
            glucoseValueText.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            hasSentHighAlert = false
            hasSentLowAlert = false
        }
        val now = System.currentTimeMillis()
        recentReadings.add(Pair(now, newGlucose))
        recentReadings.removeAll { now - it.first > 15 * 60 * 1000 }
        val reading15MinsAgo = recentReadings.firstOrNull()
        if (reading15MinsAgo != null) {
            val changePerMinute = (newGlucose - reading15MinsAgo.second) / 15.0
            trendArrowImage.setImageResource(
                when {
                    changePerMinute > 2 -> R.drawable.ic_arrow_rising_steadily
                    changePerMinute < -2 -> R.drawable.ic_arrow_falling_steadily
                    else -> R.drawable.ic_arrow_flat
                }
            )
        }
    }

    private fun setupChart() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false
        val marker = CustomMarkerView(this, R.layout.layout_chart_marker, startTime)
        lineChart.marker = marker
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.textColor = Color.GRAY
        xAxis.setLabelCount(6, true)
        xAxis.valueFormatter = object : ValueFormatter() {
            private val mFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            override fun getFormattedValue(value: Float): String {
                val millis = startTime + value.toLong() * 1000
                return mFormat.format(Date(millis))
            }
        }
        val leftAxis = lineChart.axisLeft
        leftAxis.textColor = Color.GRAY
        leftAxis.setDrawGridLines(true)
        leftAxis.setLabelCount(6, true)
        leftAxis.removeAllLimitLines()
        val lowLine = LimitLine(70f, "Low").apply {
            lineWidth = 2f
            lineColor = ContextCompat.getColor(this@DashboardActivity, R.color.status_red)
            textColor = Color.BLACK
            textSize = 12f
        }
        val highLine = LimitLine(180f, "High").apply {
            lineWidth = 2f
            lineColor = ContextCompat.getColor(this@DashboardActivity, R.color.status_red)
            textColor = Color.BLACK
            textSize = 12f
        }
        leftAxis.addLimitLine(lowLine)
        leftAxis.addLimitLine(highLine)
        leftAxis.setDrawLimitLinesBehindData(true)
        lineChart.axisRight.isEnabled = false
        lineChart.data = LineData()
    }

    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "Glucose")
        set.axisDependency = YAxis.AxisDependency.LEFT
        set.color = ContextCompat.getColor(this, R.color.primary_accent)
        set.lineWidth = 2.5f
        set.setDrawCircles(true)
        set.circleRadius = 4f
        set.setCircleColor(ContextCompat.getColor(this, R.color.primary_accent))
        set.setDrawValues(false)
        set.setDrawFilled(false)
        set.mode = LineDataSet.Mode.CUBIC_BEZIER
        return set
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Glucose Alerts"
            val descriptionText = "Notifications for high or low glucose readings"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendNotification(title: String, message: String, notificationId: Int) {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            with(NotificationManagerCompat.from(this)) {
                notify(notificationId, builder.build())
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
