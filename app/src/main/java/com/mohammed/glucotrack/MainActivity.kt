package com.mohammed.glucotrack

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // All Bluetooth and permission logic has been moved to ConnectionActivity.kt
        // This activity is now just a simple screen.

        // You can re-purpose this button to go to the Profile screen or something else.
        val button: MaterialButton = findViewById(R.id.connect_button) // Assuming this button is still in your activity_main.xml

        button.setOnClickListener {
            // For example, navigate to the ProfileActivity
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }
}