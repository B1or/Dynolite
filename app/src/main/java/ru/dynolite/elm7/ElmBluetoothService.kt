package ru.dynolite.elm7

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import ru.dynolite.elm7.MainActivity.Companion.MESSAGE_CONNECT
import ru.dynolite.elm7.MainActivity.Companion.MESSAGE_CONNECTING
import ru.dynolite.elm7.MainActivity.Companion.MESSAGE_DISCONNECT
import ru.dynolite.elm7.MainActivity.Companion.MESSAGE_READ
import ru.dynolite.elm7.MainActivity.Companion.MY_UUID
import ru.dynolite.elm7.MainActivity.Companion.TAG
import java.io.IOException

class ElmBluetoothService(val handler: Handler) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var connectThread: ConnectThread? = null

    @Synchronized
    fun connect(device: BluetoothDevice) {
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread!!.start()
    }

    @Synchronized
    fun stop() {
        connectThread?.cancel()
        connectThread = null
    }

    fun write(string: String) {
        // if (connectThread != null && stateConnect == STATE_CONNECTED) connectThread!!.writer(string)
        connectThread?.writer(string)
    }

    private inner class ConnectThread(device: BluetoothDevice): Thread() {
        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run() {
            if (bluetoothAdapter != null) {
                bluetoothAdapter.cancelDiscovery()
                try {
                    socket?.use {
                        handler.obtainMessage(MESSAGE_CONNECTING).sendToTarget()
                        it.connect()
                        handler.obtainMessage(MESSAGE_CONNECT).sendToTarget()
                        reader(it)
                    }
                } catch (e: IOException) {
                    handler.obtainMessage(MESSAGE_DISCONNECT).sendToTarget()
                }
                handler.obtainMessage(MESSAGE_DISCONNECT).sendToTarget()
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                socket?.close()
                handler.obtainMessage(MESSAGE_DISCONNECT).sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }

        private fun reader(socket: BluetoothSocket) {
            val stream = socket.inputStream
            val buffer = ByteArray(1024)
            var numBytes: Int
            while (true) {
                numBytes = try {
                    stream.read(buffer)
                } catch (e: IOException) {
                    Log.e(TAG, "Input stream was disconnected", e)
                    handler.obtainMessage(MESSAGE_DISCONNECT).sendToTarget()
                    break
                }
                val string = String(buffer, 0, numBytes)
                handler.obtainMessage(MESSAGE_READ, string).sendToTarget()
            }
        }

        fun writer(string: String) {
            val stream = socket?.outputStream
            if (stream != null) {
                val bytes: ByteArray = (string + "\r").toByteArray()
                try {
                    stream.write(bytes)
                } catch (e: IOException) {
                    Log.e(TAG, "Output stream was disconnected", e)
                    handler.obtainMessage(MESSAGE_DISCONNECT).sendToTarget()
                }
            } else {
                handler.obtainMessage(MESSAGE_DISCONNECT).sendToTarget()
            }
        }
    }
}