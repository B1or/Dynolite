package ru.ircoder.dynolite

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import ru.ircoder.dynolite.databinding.ActivityMainBinding
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val model: SharedViewModel by viewModels()
    private lateinit var navController: NavController
    private var bluetoothService: BluetoothService? = null
    private val intentService: Intent by lazy { Intent(this, BluetoothService::class.java) }
    private val localBroadcastManager: LocalBroadcastManager by lazy { LocalBroadcastManager.getInstance(this) }
    private val adapterDevices: ArrayAdapter<String> by lazy { ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, arrayListOf()) }
    private val arrayDevices: ArrayList<BluetoothDevice> = arrayListOf()
    private val listCommand: ArrayList<String> = arrayListOf()
    private var collectedResponse: String = ""
    private lateinit var menuItemDisconnect: MenuItem
    private val looperCommand = COMMAND_SPEED_RPM
    private var currentCommand = ""
    private var oldSpeed = 0
    private var oldRpm = 0

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) checkHardwareAddress()
    }

    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract()) { res ->
        this.onSignInResult(res)
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted)
            checkEnableBluetooth()
        else
            Snackbar.make(binding.container, R.string.no_permissions, LENGTH_INDEFINITE).setAction(R.string.close_app) {
                finish()
            }.show()
    }

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGrantedMap: Map<String, Boolean> ->
        var isGranted = true
        isGrantedMap.forEach {
            isGranted = isGranted && it.value
        }
        if (isGranted)
            checkEnableBluetooth()
        else
            Snackbar.make(binding.container, R.string.no_permission, LENGTH_INDEFINITE).setAction(R.string.close_app) {
                finish()
            }.show()
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            localBroadcastManager.registerReceiver(receiverMessages, IntentFilter(SERVICE_MESSAGES))
            if (bluetoothService!!.isConnected()) {
                connected()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            localBroadcastManager.unregisterReceiver(receiverMessages)
            bluetoothService = null
        }
    }

    private val receiverDiscovery = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when(intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if (device != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                Log.w(TAG, "Check permissions.")
                                return
                            }
                            val deviceName = device.name
                            val deviceHardwareAddress = device.address
                            adapterDevices.add("$deviceName $deviceHardwareAddress")
                            arrayDevices.add(device)
                        }
                    }
                }
            }
        }
    }

    private val receiverMessages = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when (intent.getStringExtra("type")) {
                    MESSAGE_CONNECTED -> {
                        connected()
                    }
                    MESSAGE_DISCONNECTED -> {
                        disconnectedUI()
                    }
                    MESSAGE_READ -> {
                        val data = intent.getStringExtra("data") ?: ""
                        val result = data.replace("\n", "").replace("\r", "\n")
                        collectedResponse += result
                        read()
                    }
                    MESSAGE_READ_ERROR -> {
                        when (bluetoothService?.isConnected()) {
                            false -> disconnectedUI()
                            true -> {
                                connectedUI()
                                bluetoothService!!.connected()  // TODO ?
                            }
                            else -> {
                                // TODO
                            }
                        }
                    }
                    MESSAGE_WRITE -> {
                        val data = showString(intent.getStringExtra("data") ?: "")
                    }
                    MESSAGE_WRITE_ERROR -> {
                        val data = showString(intent.getStringExtra("data") ?: "")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navView: BottomNavigationView = binding.navView
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_users, R.id.navigation_garage, R.id.navigation_settings))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        intentService.also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        model.drag.observe(this) { speedRpm ->
            if (speedRpm.speed != oldSpeed) {
                binding.svSpeedo.speedTo(speedRpm.speed.toFloat(), 500)
                oldSpeed = speedRpm.speed
            }
            if ((speedRpm.rpm / 100) != oldRpm) {
                binding.svRpm.speedTo(speedRpm.rpm / 100f, 500)
                oldRpm = speedRpm.rpm / 100
            }
        }
        binding.fabBluetooth.setOnClickListener {
            binding.fabBluetooth.isEnabled = false
            checkPermissions()
        }
        if (Firebase.auth.currentUser == null) {
            val providers = arrayListOf(AuthUI.IdpConfig.PhoneBuilder().build())
            val signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build()
            signInLauncher.launch(signInIntent)
        }
    }

    override fun onDestroy() {
        localBroadcastManager.unregisterReceiver(receiverMessages)
        unbindService(connection)
        bluetoothService = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        menuItemDisconnect = menu.getItem(0)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.disconnect -> {
                disconnect()
                true
            }
            R.id.exit -> {
                stopService(intentService)
                throw RuntimeException("Test Crash 4") // Force a crash
                finish()
                true
            }
            android.R.id.home -> {
                navController.popBackStack()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode != RESULT_OK) {
            if (response == null) {
                Snackbar.make(binding.container, R.string.not_signed_in, Snackbar.LENGTH_INDEFINITE).setAction(R.string.close_app) {
                    finish()
                }.show()
            } else {
                Log.v(TAG, "Sign in failed: ${response.error?.errorCode}")
                Snackbar.make(binding.container, R.string.error_authentication, Snackbar.LENGTH_INDEFINITE).setAction(R.string.close_app) {
                    finish()
                }.show()
            }
        }
    }

    private fun checkPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                        checkEnableBluetooth()
                    shouldShowRequestPermissionRationale() -> showInContextUI()
                    else ->
                        requestPermissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION))
                }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                        checkEnableBluetooth()
                    shouldShowRequestPermissionRationale() -> showInContextUI()
                    else -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            else ->
                when {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                        checkEnableBluetooth()
                    shouldShowRequestPermissionRationale() -> showInContextUI()
                    else -> requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
        }
    }

    private fun shouldShowRequestPermissionRationale() = true

    private fun showInContextUI() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setTitle(R.string.need_permissions)
                    setMessage(R.string.need_permissions_text)
                    setPositiveButton(R.string.request_permissions) { _, _ ->
                        requestPermissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                    setNegativeButton(R.string.close_app) { _, _ ->
                        finish()
                    }
                }
                val dialog: AlertDialog = builder.create()
                dialog.show()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setTitle(R.string.need_permission)
                    setMessage(R.string.need_permission_text)
                    setPositiveButton(R.string.request_permission) { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    setNegativeButton(R.string.close_app) { _, _ ->
                        finish()
                    }
                }
                val dialog: AlertDialog = builder.create()
                dialog.show()
            }
            else -> {
                val builder = AlertDialog.Builder(this)
                builder.apply {
                    setTitle(R.string.need_permission)
                    setMessage(R.string.need_permission_text)
                    setPositiveButton(R.string.request_permission) { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    setNegativeButton(R.string.close_app) { _, _ ->
                        finish()
                    }
                }
                val dialog: AlertDialog = builder.create()
                dialog.show()
            }
        }
    }
    
    private fun checkEnableBluetooth() {
        if (bluetoothAdapter.isEnabled) checkHardwareAddress()
        else requestEnableBluetooth()
    }

    private fun requestEnableBluetooth() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothLauncher.launch(intent)
    }

    private fun checkHardwareAddress() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val hardware = sharedPreferences.getString("hardware", "")
        if (!hardware.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(hardware)) {
            val bluetoothDevice: BluetoothDevice? = try {
                bluetoothAdapter.getRemoteDevice(hardware)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.toString())
                chooseFromPaired()
                null
            }
            if (bluetoothDevice != null) seekInPaired(bluetoothDevice)
        } else {
            chooseFromPaired()
        }
    }

    private fun seekInPaired(searchedDevice: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Check permissions.")
            return
        }
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        if(!pairedDevices.isNullOrEmpty() && searchedDevice in pairedDevices) {
            connect(searchedDevice)
        } else {
            chooseFromPaired()
        }
    }

    private fun chooseFromPaired() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Check permissions.")
            return
        }
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val arrayString: Array<CharSequence> = pairedDevices?.map { "${it.name} ${it.address}" }?.toTypedArray() ?: arrayOf()
        val arrayDevices: Array<BluetoothDevice> = pairedDevices?.map { it }?.toTypedArray() ?: arrayOf()
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle(R.string.choose_from_paired)
            setItems(arrayString) { _, which ->
                connect(arrayDevices[which])
            }
            setNegativeButton(R.string.device_search) { _, _ ->
                discoveryDevices()
            }
        }
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    private fun discoveryDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Check permissions.")
            return
        }
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiverDiscovery, filter)
        if (bluetoothAdapter.startDiscovery()) {
            val builder = AlertDialog.Builder(this)
            builder.apply {
                setTitle(R.string.choose_from_found)
                setAdapter(adapterDevices) { _, which ->
                    connect(arrayDevices[which])
                }
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        } else {
            Log.w(TAG, "Can't start device discovery.")
            bluetoothAdapter.cancelDiscovery()
            unregisterReceiver(receiverDiscovery)
        }
    }

    private fun connect(bluetoothDevice: BluetoothDevice) {
        bluetoothService?.connect(localBroadcastManager, bluetoothAdapter, bluetoothDevice)
    }

    private fun disconnect() {
        listCommand.clear()
        bluetoothService?.cancel()
    }

    private fun read() {  // +
        while (true) {
            val separated = collectedResponse.split(Pattern.compile("\n"), 2)
            if (separated.size > 1) {
                collectedResponse = separated[1]
                analyzeLine(separated[0])
            }
            else {
                if (separated[0] == RESPONSE_PROMPT) {
                    collectedResponse = ""
                    executeCommand()
                }
                else collectedResponse = separated[0]
                break
            }
        }
    }

    private fun write(stringWrite: String) {
        bluetoothService?.write("$stringWrite\r")
    }

    private fun connected() {
        connectedUI()
        initialize()
    }

    private fun initialize() {  // +
        listCommand.add(COMMAND_DEFAULT)
        listCommand.add(COMMAND_LINEFEED_ON)
        listCommand.add(COMMAND_ECHO_ON)
        write("")
    }

    private fun executeCommand() {  // +
        while (listCommand.isNotEmpty()) {
            val command = listCommand.first()
            listCommand.removeFirst()
            if (command.isNotBlank()) {
                currentCommand = command
                write(command)
                return
            }
        }
        if (looperCommand.isNotBlank()) {
            currentCommand = looperCommand
            write(looperCommand)
        }
    }

    private fun analyzeLine(line: String) {  // +
        if (line.isBlank()) return
        when (line) {
            currentCommand -> {
                Log.w(TAG, "Echo ok")
            }
            RESPONSE_OK -> {
                currentCommand = ""
            }
            else -> {
                when {
                    line.length > 10 && line.substring(0, 5) == RESPONSE_SPEED && line.substring(8, 11) == RESPONSE_AND_RPM -> {
                        if (line.length > 16) {
                            val speed = line.substring(6, 8).toInt(radix = 16)
                            val rpm = (line.substring(12, 14).toInt(radix = 16) * 256 + line.substring(15, 17).toInt(radix = 16)) / 4
                            val time = System.currentTimeMillis()
                            model.drag(Drag(speed, rpm, time))
                        }
                    }
                    line.length > 4 && line.substring(0, 5) == RESPONSE_RPM -> {
                        if (line.length > 10) model.rpm((line.substring(6, 8).toInt(radix = 16) * 256 + line.substring(9, 11).toInt(radix = 16)) / 4)
                    }
                    line.length > 4 && line.substring(0, 5) == RESPONSE_SPEED -> {
                        if (line.length > 7) model.speed(line.substring(6, 8).toInt(radix = 16))
                    }
                    line.length > 8 && line.substring(0, 9) == RESPONSE_ERROR -> {
                        // TODO auto not start
                    }
                    line.length > 6 && line.substring(0, 7) == RESPONSE_NO_DATA -> {
                        // TODO auto not start
                    }
                }
            }
        }
    }

    private fun connectedUI() {
        binding.fabBluetooth.visibility = View.GONE
        menuItemDisconnect.isEnabled = true
    }

    private fun disconnectedUI() {
        binding.fabBluetooth.isEnabled = true
        binding.fabBluetooth.visibility = View.VISIBLE
        menuItemDisconnect.isEnabled = false
    }

    private fun showString(original: String): String {
        var result = original.replace(" ", "_")
        result = result.replace("\n", "^")
        result = result.replace("\r", "~")
        return result
    }

    companion object {
        const val TAG = "LogDynolite"
        const val MESSAGE_CONNECTED = "message_connected"
        const val MESSAGE_DISCONNECTED = "message_disconnected"
        const val MESSAGE_READ = "message_read"
        const val MESSAGE_READ_ERROR = "message_read_error"
        const val MESSAGE_WRITE = "message_write"
        const val MESSAGE_WRITE_ERROR = "message_write_error"
        const val SERVICE_MESSAGES = "service_messages"
        const val COMMAND_DEFAULT = "AT D"
        const val COMMAND_ECHO_ON = "AT E1"
        const val COMMAND_LINEFEED_ON ="AT L1"
        const val COMMAND_RPM = "01 0C"
        const val COMMAND_SPEED = "01 0D"
        const val COMMAND_SPEED_RPM = "01 0D 0C"
        const val RESPONSE_PROMPT = ">"
        const val RESPONSE_OK = "OK"
        const val RESPONSE_ERROR = "CAN ERROR"
        const val RESPONSE_NO_DATA = "NO DATA"
        const val RESPONSE_RPM = "41 0C"
        const val RESPONSE_SPEED = "41 0D"
        const val RESPONSE_AND_RPM = " 0C"
        const val FIREBASE_USERS = "users"
        const val FIREBASE_CARS = "cars"
        const val POINTS_X = "pointsX"
        const val POINTS_Y = "pointsY"
        const val COUNT_TESTING_RATIO = 10
        const val FORMAT_RATIO = "%5.2f"
        const val RATIO_SCATTER = 0.03f
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
