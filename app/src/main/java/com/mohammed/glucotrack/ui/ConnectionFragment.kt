package com.mohammed.glucotrack.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.mohammed.glucotrack.R
import com.mohammed.glucotrack.WarmupActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment(R.layout.fragment_connection) {

    private val viewModel: BleConnectionViewModel by viewModels()

    // --- UPDATED: This launcher now handles MULTIPLE permissions ---
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Check if ALL required permissions were granted
            if (permissions.values.all { it }) {
                viewModel.startScan()
            } else {
                Snackbar.make(requireView(), "Permissions are required to find the sensor.", Snackbar.LENGTH_LONG).show()
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                checkPermissionsAndStartScan()
            } else {
                Snackbar.make(requireView(), "Bluetooth must be enabled to connect.", Snackbar.LENGTH_LONG).show()
            }
        }

    @SuppressLint("MissingPermission") // Permissions are checked before this is called
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchingView = view.findViewById<LinearLayout>(R.id.searching_view)
        val foundView = view.findViewById<LinearLayout>(R.id.found_view)
        val errorView = view.findViewById<LinearLayout>(R.id.error_view)
        val deviceNameTextView = view.findViewById<TextView>(R.id.deviceNameTextView)
        val connectButton = view.findViewById<Button>(R.id.connect_button)
        val retryButton = view.findViewById<Button>(R.id.retry_button)
        val errorTextView = view.findViewById<TextView>(R.id.errorTextView)

        lifecycleScope.launch {
            viewModel.scanState.collect { state ->
                searchingView.visibility = View.GONE
                foundView.visibility = View.GONE
                errorView.visibility = View.GONE

                when (state) {
                    is BleScanState.Scanning -> searchingView.visibility = View.VISIBLE
                    is BleScanState.DeviceFound -> {
                        foundView.visibility = View.VISIBLE
                        // This line is now safe to call because we have the connect permission
                        deviceNameTextView.text = state.device.device.name ?: "GlucoTrack Sensor"
                        connectButton.setOnClickListener {
                            viewModel.connectToDevice(state.device.device)
                            val intent = Intent(requireActivity(), WarmupActivity::class.java)
                            startActivity(intent)
                            requireActivity().finish()
                        }
                    }
                    is BleScanState.Error -> {
                        errorView.visibility = View.VISIBLE
                        errorTextView.text = state.message
                        retryButton.setOnClickListener { checkPermissionsAndStartScan() }
                    }
                    is BleScanState.Idle -> {}
                }
            }
        }
        checkPermissionsAndStartScan()
    }

    private fun checkPermissionsAndStartScan() {
        val bluetoothManager = ContextCompat.getSystemService(requireContext(), android.bluetooth.BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        // --- UPDATED: Define a list of required permissions ---
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Check if all permissions in the list are granted
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            viewModel.startScan()
        } else {
            // --- UPDATED: Launch the launcher with the list of permissions ---
            requestPermissionsLauncher.launch(requiredPermissions)
        }
    }
}

