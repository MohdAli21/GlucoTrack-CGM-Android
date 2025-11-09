package com.mohammed.glucotrack

// Add these imports at the top
import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

@SuppressLint("MissingPermission") // We handle permissions checking before calling BLE functions
class ConnectionActivity : AppCompatActivity() {

    private val cgmDeviceName = "GlucoTrack CGM"
    private var isScanning = false
    private var bluetoothGatt: BluetoothGatt? = null

    // UI Elements
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var retryButton: Button

    // Get the phone's Bluetooth adapter
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        // Initialize UI elements
        statusText = findViewById(R.id.status_text)
        progressBar = findViewById(R.id.connection_progress_bar)
        retryButton = findViewById(R.id.retry_button)

        retryButton.setOnClickListener {
            startScanOrRequestPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        // Every time the screen is shown, start the connection process
        startScanOrRequestPermissions()
    }

    // --- Main Connection Logic ---

    private fun startScanOrRequestPermissions() {
        // Hide the retry button and show the progress bar
        retryButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        statusText.text = "Searching for your CGM..."

        if (allPermissionsGranted()) {
            startBleScan()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun startBleScan() {
        val bleScanner = bluetoothAdapter.bluetoothLeScanner
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner.startScan(null, scanSettings, scanCallback)
        isScanning = true
        Log.d("ConnectionActivity", "BLE Scan started.")

        // Stop scanning after 15 seconds if the device isn't found
        Handler(Looper.getMainLooper()).postDelayed({
            if (isScanning) {
                stopBleScan()
                showConnectionFailed("CGM not found. Please make sure it's on and nearby.")
            }
        }, 15000)
    }

    private fun stopBleScan() {
        if (isScanning) {
            bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
            isScanning = false
            Log.d("ConnectionActivity", "BLE Scan stopped.")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.device.name == cgmDeviceName) {
                Log.d("ConnectionActivity", "CGM device found! Connecting...")
                stopBleScan()
                statusText.text = "CGM Found. Connecting..."
                result.device.connectGatt(this@ConnectionActivity, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("ConnectionActivity", "Successfully connected to GATT server.")
                bluetoothGatt = gatt
                // Run on the main UI thread
                runOnUiThread {
                    statusText.text = "Connected!"
                    navigateToMainApp()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("ConnectionActivity", "Disconnected from GATT server.")
                runOnUiThread {
                    showConnectionFailed("Failed to connect to CGM.")
                }
            }
        }
    }

    private fun showConnectionFailed(message: String) {
        statusText.text = message
        progressBar.visibility = View.GONE
        retryButton.visibility = View.VISIBLE
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close this screen so the user can't come back to it
    }


    // --- Permission Handling Logic (same as before) ---

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.entries.all { it.value }) {
            startBleScan()
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Permissions are required.", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermissions() {
        requestPermissionLauncher.launch(requiredPermissions)
    }
}