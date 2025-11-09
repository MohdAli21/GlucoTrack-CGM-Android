package com.mohammed.glucotrack.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Defines the different states of the BLE scanning process for the UI to observe.
 */
sealed class BleScanState {
    object Idle : BleScanState()
    object Scanning : BleScanState()
    data class DeviceFound(val device: ScanResult) : BleScanState()
    data class Error(val message: String) : BleScanState()
}

/**
 * ViewModel to manage the logic for scanning and connecting to the GlucoTrack BLE device.
 */
@SuppressLint("MissingPermission") // Permissions are handled in the UI layer (Fragment/Activity)
class BleConnectionViewModel(application: Application) : AndroidViewModel(application) {

    // --- Private Properties ---

    // StateFlow to expose the current scanning state to the UI
    private val _scanState = MutableStateFlow<BleScanState>(BleScanState.Idle)
    val scanState = _scanState.asStateFlow()

    // Access to the Bluetooth hardware
    private val bluetoothManager = application.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    // Scan filter to specifically look for our GlucoTrack device
    private val scanFilter = ScanFilter.Builder()
        .setServiceUuid(ParcelUuid.fromString(GLUCOTRACK_SERVICE_UUID))
        .build()

    // Scan settings for performance
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    /**
     * Callback for handling BLE scan events.
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                stopScan() // Stop scanning as soon as we find the first device
                _scanState.value = BleScanState.DeviceFound(result)
                Log.d("BLE_SCAN", "Success! Found device: ${device.name ?: "Unknown"} - ${device.address}")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _scanState.value = BleScanState.Error("Scan failed with error code: $errorCode")
            Log.e("BLE_SCAN", "Scan failed with error code: $errorCode")
        }
    }

    // --- Public Functions ---

    /**
     * Starts the BLE scan for the GlucoTrack device.
     * IMPORTANT: Ensure Bluetooth and Location permissions are granted before calling.
     */
    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _scanState.value = BleScanState.Error("Bluetooth is not enabled.")
            return
        }

        _scanState.value = BleScanState.Scanning
        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.i("BLE_SCAN", "Scan started...")

        // Set a timeout for the scan
        viewModelScope.launch {
            delay(SCAN_PERIOD)
            // If the state is still Scanning after the timeout, it means no device was found
            if (_scanState.value == BleScanState.Scanning) {
                stopScan()
                _scanState.value = BleScanState.Error("CGM not found. Please make sure it's on and nearby.")
                Log.w("BLE_SCAN", "Scan timed out. Device not found.")
            }
        }
    }

    /**
     * Stops the ongoing BLE scan.
     */
    fun stopScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
        Log.i("BLE_SCAN", "Scan stopped.")
    }

    /**
     * Placeholder function to handle the connection logic.
     * @param device The BluetoothDevice to connect to.
     */
    fun connectToDevice(device: BluetoothDevice) {
        // Here you would implement the gatt.connect() logic.
        Log.i("BLE_CONNECT", "Connecting to ${device.name ?: "Unknown"} (${device.address})...")
    }

    /**
     * Clean up resources when the ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

    companion object {
        // UUIDs from your ESP32-C3 code
        private const val GLUCOTRACK_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

        // Scan period in milliseconds
        private const val SCAN_PERIOD: Long = 10000 // 10 seconds
    }
}