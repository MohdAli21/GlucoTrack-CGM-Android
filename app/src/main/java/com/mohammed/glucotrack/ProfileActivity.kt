package com.mohammed.glucotrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // --- Find all the views ---
        val nameInputLayout: TextInputLayout = findViewById(R.id.name_input_layout)
        val nameEditText: TextInputEditText = findViewById(R.id.name_edit_text)
        val ageAutoComplete: AutoCompleteTextView = findViewById(R.id.age_auto_complete)
        val isDiabeticSwitch: SwitchMaterial = findViewById(R.id.diabetic_switch)
        val startMonitoringButton: MaterialButton = findViewById(R.id.start_monitoring_button)
        val eatenRadioGroup: RadioGroup = findViewById(R.id.eaten_radio_group)
        val mealTypeRadioGroup: RadioGroup = findViewById(R.id.meal_type_radio_group)

        // --- Setup Age Dropdown ---
        val ageCategories = resources.getStringArray(R.array.age_categories)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ageCategories)
        ageAutoComplete.setAdapter(adapter)

        // --- Setup Button Click with Validation ---
        startMonitoringButton.setOnClickListener {
            val isNameValid = validateName(nameEditText, nameInputLayout)
            val isAgeValid = validateAge(ageAutoComplete, findViewById(R.id.age_dropdown_layout))

            if (isNameValid && isAgeValid) {
                // Get all user choices
                val isDiabetic = isDiabeticSwitch.isChecked
                val hasEaten = eatenRadioGroup.checkedRadioButtonId == R.id.radio_yes
                val mealType = mealTypeRadioGroup.checkedRadioButtonId
                val ageCategory = ageAutoComplete.text.toString()

                // Save the user's name to SharedPreferences
                val prefs = getSharedPreferences("GlucoTrackPrefs", Context.MODE_PRIVATE)
                with(prefs.edit()) {
                    putString("USER_NAME", nameEditText.text.toString())
                    apply()
                } // <-- THIS IS THE CORRECTED CLOSING BRACE

                // Create the intent for the WarmupActivity
                val intent = Intent(this, WarmupActivity::class.java).apply {
                    putExtra("IS_DIABETIC", isDiabetic)
                    putExtra("HAS_EATEN", hasEaten)
                    putExtra("MEAL_TYPE_ID", mealType)
                    putExtra("AGE_CATEGORY", ageCategory)
                }
                startActivity(intent)
                finish()
            }
        }

        // --- Setup Meal Question Logic ---
        eatenRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_yes) {
                mealTypeRadioGroup.visibility = View.VISIBLE
            } else {
                mealTypeRadioGroup.visibility = View.GONE
            }
        }
    }

    private fun validateName(editText: TextInputEditText, layout: TextInputLayout): Boolean {
        return if (editText.text.toString().trim().isEmpty()) {
            layout.error = "Name is required"
            false
        } else {
            layout.error = null
            true
        }
    }

    private fun validateAge(autoComplete: AutoCompleteTextView, layout: TextInputLayout): Boolean {
        return if (autoComplete.text.toString().isEmpty()) {
            layout.error = "Please select an age category"
            false
        } else {
            layout.error = null
            true
        }
    }
}
