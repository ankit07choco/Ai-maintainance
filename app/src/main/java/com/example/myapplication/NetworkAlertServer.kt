package com.example.myapplication

import android.util.Log
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class NetworkAlertServer {

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private var isRunning = false
    private var serverThread: Thread? = null

    fun startServer() {
        if (isRunning) return
        isRunning = true
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(8888)
                Log.d("NetworkAlertServer", "TCP Server started on port 8888")
                while (isRunning) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        clientSocket = socket
                        outputStream = socket.getOutputStream()
                        Log.d("NetworkAlertServer", "Phone connected via Wi-Fi TCP!")
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkAlertServer", "Server error", e)
            }
        }
        serverThread?.start()
    }

    fun sendAlertToPhone(message: String) {
        try {
            outputStream?.let { stream ->
                stream.write((message + "\n").toByteArray())
                stream.flush()
                Log.d("NetworkAlertServer", "Alert sent to phone over Wi-Fi: $message")
            }
        } catch (e: Exception) {
            Log.e("NetworkAlertServer", "Failed to send alert over Wi-Fi", e)
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("NetworkAlertServer", "Error closing server", e)
        }
    }
}
