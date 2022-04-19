package ru.ircoder.dynolite

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import ru.ircoder.dynolite.MainActivity.Companion.MESSAGE_CONNECTED
import ru.ircoder.dynolite.MainActivity.Companion.MESSAGE_DISCONNECTED
import ru.ircoder.dynolite.MainActivity.Companion.MESSAGE_READ
import ru.ircoder.dynolite.MainActivity.Companion.MESSAGE_READ_ERROR
import ru.ircoder.dynolite.MainActivity.Companion.MESSAGE_WRITE
import ru.ircoder.dynolite.MainActivity.Companion.MESSAGE_WRITE_ERROR
import ru.ircoder.dynolite.MainActivity.Companion.MY_UUID
import ru.ircoder.dynolite.MainActivity.Companion.SERVICE_MESSAGES
import ru.ircoder.dynolite.MainActivity.Companion.TAG
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class BluetoothService : Service() {

    private val binder = LocalBinder()
    private var bluetoothSocket: BluetoothSocket? = null
    private var localBroadcastManager: LocalBroadcastManager? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun connect(localBroadcastManagerActivity: LocalBroadcastManager, bluetoothAdapter: BluetoothAdapter, deviceConnect: BluetoothDevice) {
        localBroadcastManager = localBroadcastManagerActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Check permissions.")
            return
        }
        bluetoothAdapter.cancelDiscovery()
        thread(start = true) {
            try {
                bluetoothSocket = deviceConnect.createInsecureRfcommSocketToServiceRecord(MY_UUID)
                if (bluetoothSocket == null) return@thread
                bluetoothSocket!!.connect()
                outputStream = bluetoothSocket!!.outputStream
                inputStream = bluetoothSocket!!.inputStream
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
                sendMessage(MESSAGE_DISCONNECTED)
                return@thread
            }
            sendMessage(MESSAGE_CONNECTED)
            connected()
        }
    }

    fun write(stringWrite: String) {
        val bytes: ByteArray = stringWrite.toByteArray()
        if (outputStream != null) {
            try {
                outputStream!!.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
                sendMessage(MESSAGE_WRITE_ERROR, stringWrite)
                return
            }
            sendMessage(MESSAGE_WRITE, stringWrite)
        } else sendMessage(MESSAGE_WRITE_ERROR, stringWrite)
    }

    fun cancel() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
            return
        }
        sendMessage(MESSAGE_DISCONNECTED)
    }

    fun isConnected(): Boolean = bluetoothSocket?.isConnected ?: false

    private fun sendMessage(type: String, data: Any? = null) {
        val intent = Intent(SERVICE_MESSAGES)
        intent.putExtra("type", type)
        when (data) {
            is String -> intent.putExtra("data", data)
        }
        localBroadcastManager?.sendBroadcast(intent)
    }

    fun connected() {
        if (inputStream != null)
            thread(start = true) {
                var numBytes: Int
                val buffer = ByteArray(1024)
                while (true) {
                    val result = StringBuilder()
                    numBytes = try {
                        inputStream!!.read(buffer)
                    } catch (e: IOException) {
                        Log.e(TAG, "Input stream was disconnected", e)
                        sendMessage(MESSAGE_READ_ERROR)
                        break
                    }
                    if (numBytes > 0) {
                        for (i in 0 until numBytes) result.append(buffer[i].toInt().toChar())
                        sendMessage(MESSAGE_READ, result.toString())
                    }
                }
            }
    }
}
