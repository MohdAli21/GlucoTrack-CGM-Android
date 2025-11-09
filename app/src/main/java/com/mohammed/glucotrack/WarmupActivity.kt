package com.mohammed.glucotrack

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

class WarmupActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private var progressStatus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warmup)

        progressBar = findViewById(R.id.warmup_progress_bar)

        // Get the profile data from the previous screen
        val intentExtras = intent.extras

        // Start the warmup simulation
        Thread {
            while (progressStatus < 100) {
                progressStatus += 2 // Increase progress
                handler.post {
                    progressBar.progress = progressStatus
                }
                try {
                    // Simulate a short delay
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            // When warmup is complete, go to the dashboard
            val dashboardIntent = Intent(this, DashboardActivity::class.java)
            if (intentExtras != null) {
                dashboardIntent.putExtras(intentExtras) // Pass the profile data along
            }
            startActivity(dashboardIntent)
            finish() // Finish this activity so the user can't go back
        }.start()
    }
}