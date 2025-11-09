package com.mohammed.glucotrack

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var highGlucoseEditText: TextInputEditText
    private lateinit var lowGlucoseEditText: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_settings)

        val settingsLayout: ConstraintLayout = findViewById(R.id.settings_layout)
        ViewCompat.setOnApplyWindowInsetsListener(settingsLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top, bottom = insets.bottom, left = insets.left, right = insets.right)
            WindowInsetsCompat.CONSUMED
        }

        highGlucoseEditText = findViewById(R.id.high_glucose_edit_text)
        lowGlucoseEditText = findViewById(R.id.low_glucose_edit_text)
        val saveButton: MaterialButton = findViewById(R.id.save_button)

        val currentHigh = intent.getIntExtra("CURRENT_HIGH", 180)
        val currentLow = intent.getIntExtra("CURRENT_LOW", 70)
        highGlucoseEditText.setText(currentHigh.toString())
        lowGlucoseEditText.setText(currentLow.toString())

        saveButton.setOnClickListener {
            val newHigh = highGlucoseEditText.text.toString().toIntOrNull() ?: 180
            val newLow = lowGlucoseEditText.text.toString().toIntOrNull() ?: 70

            val prefs = getSharedPreferences("GlucoTrackPrefs", Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putInt("HIGH_GLUCOSE_THRESHOLD", newHigh)
                putInt("LOW_GLUCOSE_THRESHOLD", newLow)
                apply()
            }
            setResult(RESULT_OK)
            finish()
        }
    }
}