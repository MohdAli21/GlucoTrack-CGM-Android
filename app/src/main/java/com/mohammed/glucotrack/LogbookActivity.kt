package com.mohammed.glucotrack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class LogbookActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogbookAdapter
    private var recordList = mutableListOf<GlucoseRecord>()
    private lateinit var exportButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_logbook)

        val logbookLayout: ConstraintLayout = findViewById(R.id.logbook_layout)
        ViewCompat.setOnApplyWindowInsetsListener(logbookLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

        db = AppDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.logbook_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        exportButton = findViewById(R.id.export_button)

        adapter = LogbookAdapter(recordList) { record ->
            if (record.eventType != null) {
                showUnlogConfirmationDialog(record)
            }
        }
        recyclerView.adapter = adapter

        exportButton.setOnClickListener {
            exportReport()
        }

        loadLogbookData()
    }

    private fun loadLogbookData() {
        lifecycleScope.launch {
            val records = db.glucoseDao().getAllRecords()
            recordList.clear()
            recordList.addAll(records)
            adapter.notifyDataSetChanged()
        }
    }

    private fun showUnlogConfirmationDialog(record: GlucoseRecord) {
        AlertDialog.Builder(this)
            .setTitle("Remove Event")
            .setMessage("Are you sure you want to remove the '${record.eventType}' event from this log entry?")
            .setPositiveButton("Remove") { _, _ ->
                unlogEvent(record)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unlogEvent(record: GlucoseRecord) {
        lifecycleScope.launch {
            db.glucoseDao().clearEventFromRecord(record.id)
            loadLogbookData()
        }
    }

    private fun exportReport() {
        lifecycleScope.launch {
            val allRecords = db.glucoseDao().getAllRecords()
            if (allRecords.size < 10) { // Require a minimum amount of data
                Toast.makeText(this@LogbookActivity, "Not enough data to generate a report.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val prefs = getSharedPreferences("GlucoTrackPrefs", Context.MODE_PRIVATE)
            val userName = prefs.getString("USER_NAME", "User") ?: "User"

            val generator = PdfReportGenerator(this@LogbookActivity)
            val pdfUri = generator.createReport(allRecords, userName)

            if (pdfUri != null) {
                shareReport(pdfUri)
            } else {
                Toast.makeText(this@LogbookActivity, "Failed to create report.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareReport(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Report"))
    }
}