package com.mohammed.glucotrack.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mohammed.glucotrack.R

/**
 * This fragment displays the initial welcome screen of the application.
 * It contains a button to start the sensor connection process.
 */
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    /**
     * This function is called after the fragment's view has been created.
     * We set up the button's click listener here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find the "Connect Sensor" button from the layout file.
        val connectButton = view.findViewById<Button>(R.id.connect_button)

        // Set a listener that will be triggered when the user taps the button.
        connectButton.setOnClickListener {
            // This line tells the Navigation Component to move to the next screen.
            // It uses the "action" we defined in the nav_graph.xml file to find the correct path.
            findNavController().navigate(R.id.action_welcomeFragment_to_connectionFragment)
        }
    }
}

