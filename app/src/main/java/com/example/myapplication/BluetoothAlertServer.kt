package com.example.myapplication

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.UUID

@Suppress("unused", "DEPRECATION")
class BluetoothAlertServer(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }
    private val uuid: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    private var clientSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var acceptThread: AcceptThread? = null

    fun startServer() {
        if (bluetoothAdapter == null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        try {
            acceptThread = AcceptThread()
            acceptThread?.start()
        } catch (e: Exception) {
            Log.e("BluetoothServer", "Error starting Bluetooth server", e)
        }
    }

    fun sendAlertToPhone(message: String) {
        try {
            outputStream?.let { stream ->
                stream.write((message + "\n").toByteArray())
                stream.flush()
                Log.d("BluetoothServer", "Alert sent to phone over Bluetooth: $message")
            }
        } catch (e: Exception) {
            Log.e("BluetoothServer", "Failed to send alert over Bluetooth", e)
        }
    }

    fun stopServer() {
        try {
            acceptThread?.cancel()
            clientSocket?.close()
        } catch (e: Exception) {
            Log.e("BluetoothServer", "Error closing Bluetooth", e)
        }
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? by lazy {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return@lazy null
                }
                bluetoothAdapter?.listenUsingRfcommWithServiceRecord("AIMaintenanceServer", uuid)
            } catch (e: SecurityException) {
                null
            } catch (e: Exception) {
                null
            }
        }

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    shouldLoop = false
                    null
                }
                socket?.let {
                    clientSocket = it
                    outputStream = it.outputStream
                    try {
                        serverSocket?.close()
                    } catch (_: Exception) {
                    }
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
