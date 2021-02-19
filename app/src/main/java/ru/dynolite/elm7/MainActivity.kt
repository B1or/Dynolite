package ru.dynolite.elm7

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import ru.dynolite.elm7.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity(), InformationFragment.InformationListener {
    private lateinit var binding: ActivityMainBinding
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    // Проверка на включенность Bluetooth.
    private val startForResultEnableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            Snackbar.make(binding.coordinatorLayout, R.string.bluetooth_enabled, Snackbar.LENGTH_SHORT).show()
        } else {
            val dialog: AlertDialog = this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(R.string.bluetooth_not_enabled)
                    setPositiveButton(R.string.ok) { _, _ ->
                        finish()
                    }
                    setCancelable(false)
                }
                builder.create()
            }
            dialog.show()
        }
    }
    private var elmBluetoothService: ElmBluetoothService? = null
    private var stateConnect = STATE_NONE
    private var unpairedDevices: ArrayList<CharSequence> = arrayListOf()
    // Register for broadcasts when a device is discovered.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device?.name
                    val deviceHardwareAddress = device?.address
                    if (deviceName == ELM_DEVICE_NAME) {
                        deviceHardwareAddress?.let {
                            unpairedDevices.add(it)
                        }
                        dialogDiscover() // Список неспаренных устройств.
                    }
                }
            }
        }
    }
    private var response: StringBuilder = StringBuilder()
    private var jobCommand: Job? = null
    private val handlerThread = HandlerThread("MyHandlerThread")
    private var handler: Handler? = null
    private var dialogDiscover: AlertDialog? = null
    private val informationViewModel: InformationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_information, R.id.navigation_dragmeter))
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.bottomNavigationView.setupWithNavController(navController)
        // Проверка на наличие Bluetooth.
        if (bluetoothAdapter == null) {
            val dialog: AlertDialog = this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setMessage(R.string.bluetooth_not_support)
                    setPositiveButton(R.string.ok) { _, _ ->
                        finish()
                    }
                    setCancelable(false)
                }
                builder.create()
            }
            dialog.show()
        }
        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)
        // Получение сообщений от сервиса bluetooth.
        handlerThread.start()
        handler = object: Handler(handlerThread.looper) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when (msg.what) {
                    MESSAGE_CONNECTING -> {
                        if (stateConnect != STATE_CONNECTING) {
                            stateConnect = STATE_CONNECTING
                            GlobalScope.launch(Dispatchers.Main) {
                                binding.fabBluetooth.visibility = View.GONE
                                Snackbar.make(binding.coordinatorLayout, R.string.connecting, Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                    MESSAGE_CONNECT -> {
                        if (stateConnect != STATE_CONNECTED) {
                            stateConnect = STATE_CONNECTED
                            GlobalScope.launch(Dispatchers.Main) {
                                nullCommand()
                                binding.fabBluetooth.visibility = View.GONE
                                Snackbar.make(binding.coordinatorLayout, R.string.connected, Snackbar.LENGTH_SHORT).show()
                                if (initCommand(COMMAND_ECHO_ON) && initCommand(COMMAND_LINEFEED_ON)) {
                                    Snackbar.make(binding.coordinatorLayout, R.string.init_success, Snackbar.LENGTH_SHORT).show()
                                    informationViewModel.setConnect(true)
                                }
                                else Snackbar.make(binding.coordinatorLayout, R.string.init_fail, Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                    MESSAGE_DISCONNECT -> {
                        if (stateConnect != STATE_NONE) {
                            stateConnect = STATE_NONE
                            GlobalScope.launch(Dispatchers.Main) {
                                Snackbar.make(binding.coordinatorLayout, R.string.disconnect, Snackbar.LENGTH_SHORT).show()
                                binding.fabBluetooth.visibility = View.VISIBLE
                            }
                        }
                    }
                    MESSAGE_READ -> {
                        val string = msg.obj as String
                        response.append(string)
                        if (string.contains(">")) {
                            jobCommand?.cancel()
                        }
                    }
                }
            }
        }
        informationViewModel.setConnect(false)
    }

    override fun onStart() {
        super.onStart()
        // Проверка на включенность Bluetooth.
        if (bluetoothAdapter?.isEnabled == false) {
            startForResultEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    override fun onResume() {
        super.onResume()
        // Запуск сервиса.
        if (elmBluetoothService == null && handler != null) elmBluetoothService = ElmBluetoothService(handler!!)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver)
        // Остановка сервиса.
        elmBluetoothService?.stop()
        handlerThread.quit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            R.id.action_test -> {
                Snackbar.make(binding.coordinatorLayout, testCommand(COMMAND_VIN_COUNT), Snackbar.LENGTH_LONG).show()
                true
            }
            R.id.action_reset -> {
                resetCommand()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Команда получения информации об устройстве ELM
    override fun infoCommand(): String? = runBlocking {
        var result: String? = null
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(COMMAND_INFO)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            val lines = response.lines()
            result = if (lines[0] == COMMAND_INFO) lines[1]
            else null
            response.clear()
        }
        result
    }

    // Команда замера скорости
    override fun speedCommand(): Int = runBlocking {
        var result = -1
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(COMMAND_SPEED)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            val lines = response.lines()
            result =
                if (lines.size > 1 && lines[0] == COMMAND_SPEED && lines[1].length > 7 && lines[1].substring(0, 5) == "41 0D")
                    lines[1].substring(6, 8).toInt(radix = 16)
                else -1
            response.clear()
        }
        result
    }

    // Команда замера оборотов
    override fun rpmCommand(): Int = runBlocking {
        var result = -1
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(COMMAND_RPM)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            val lines = response.lines()
            result =
                if (lines.size > 1 && lines[0] == COMMAND_RPM && lines[1].length > 10 && lines[1].substring(0, 5) == "41 0C")
                    (lines[1].substring(6, 8).toInt(radix = 16) * 256 + lines[1].substring(9, 11).toInt(radix = 16)) / 4
                else -1
            response.clear()
        }
        result
    }

    override fun vinCommand(): String? = runBlocking {
        var result: String? = null
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(COMMAND_VIN_COUNT)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            val linesCount = response.lines()
            if (linesCount.size > 1 && linesCount[0] == COMMAND_VIN_COUNT && linesCount[1].length > 7 && linesCount[1].substring(0, 5) == "49 01") {
                val count = linesCount[1].substring(6, 8).toInt(radix = 16)
                elmBluetoothService!!.write(COMMAND_VIN)
                jobCommand = GlobalScope.launch(Dispatchers.Default) {
                    delay(TIMEOUT_COMMAND_RESPONSE)
                }
                jobCommand!!.join()
                val linesVin = response.lines()
                if (linesVin[0] == COMMAND_VIN) {
                    result = linesVin[1]
                    for (i in 1 .. count) {
                        result += linesVin[i]
                    }
                }
            }
            response.clear()
        }
        result
    }

    fun onClick(view: View) {
        when (view) {
            binding.fabBluetooth -> {
                // Подключение к устройству ELM.
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                    // Спаренные устройства.
                    val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
                    if (!pairedDevices.isNullOrEmpty()) {
                        val arrayList: ArrayList<CharSequence> = arrayListOf()
                        pairedDevices.forEach { device ->
                            val deviceName = device.name
                            val deviceHardwareAddress = device.address // MAC address
                            if (deviceName == ELM_DEVICE_NAME) arrayList.add(deviceHardwareAddress)
                        }
                        if (arrayList.isNotEmpty()) {
                            if (arrayList.size == 1) {
                                val device = bluetoothAdapter.getRemoteDevice(arrayList[0].toString())
                                if (elmBluetoothService != null && stateConnect == STATE_NONE) {
                                    elmBluetoothService!!.connect(device)
                                }
                            } else {
                                // Список спаренных устройств.
                                val array: Array<CharSequence> = arrayList.toTypedArray()
                                val dialog: AlertDialog = this.let {
                                    val builder = AlertDialog.Builder(it)
                                    builder.apply {
                                        setTitle(R.string.list_paired_devices)
                                        setMessage(R.string.select_paired_device)
                                        setItems(array) { _, which ->
                                            val device = bluetoothAdapter.getRemoteDevice(array[which].toString())
                                            if (elmBluetoothService != null && stateConnect == STATE_NONE) elmBluetoothService!!.connect(device)
                                        }
                                        setPositiveButton(R.string.choose_unpaired_devices) { _, _ ->
                                            discoverDevices() // Неспаренные устройства
                                        }
                                    }
                                    builder.create()
                                }
                                dialog.show()
                            }
                        } else discoverDevices() // Неспаренные устройства.
                    } else discoverDevices() // Неспаренные устройства.
                }
            }
        }
    }

    // Неспаренные устройства.
    private fun discoverDevices() {
        if (bluetoothAdapter != null && !unpairedDevices.isNullOrEmpty()) {
            bluetoothAdapter.startDiscovery()
        }
    }

    // Список неспаренных устройств.
    private fun dialogDiscover() {
        if (bluetoothAdapter != null) {
            val array: Array<CharSequence> = unpairedDevices.toTypedArray()
            dialogDiscover = this.let {
                val builder = AlertDialog.Builder(it)
                builder.apply {
                    setTitle(R.string.list_unpaired_devices)
                    setMessage(R.string.select_unpaired_device)
                    setItems(array) { _, which ->
                        val device = bluetoothAdapter.getRemoteDevice(array[which].toString())
                        if (elmBluetoothService != null && stateConnect == STATE_NONE) elmBluetoothService!!.connect(device)
                    }
                }
                builder.create()
            }
            dialogDiscover!!.show()
        }
    }

    // Команда инициализации.
    private fun initCommand(command: String): Boolean = runBlocking {
        var result = false
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(command)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            result = response.contains(RESPONSE_OK)
            response.clear()
        }
        result
    }

    private fun availableCommand(command: String): Long = runBlocking {
        var result = -1L
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(command)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            val lines = response.lines()
            if (COMMAND_PID1.replaceRange(0..0, "4") == lines[0].substring(0 .. 4)) {
                var string = ""
                for (i in 1 .. 4) {
                    string += lines[1].substring(i * 3 + 3 .. i * 3 + 4)
                }
                result = string.toLong(radix = 16)
            }
            response.clear()
        }
        result
    }

    // Тестовая Команда.
    private fun testCommand(command: String): String = runBlocking {
        var result = ""
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(command)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
/*
            val lines = response.lines()
            lines.forEach { line ->
                result += "_$line"
            }
            result += "_"
*/
            result = response.toString()
                .replace(' ', '_')
                .replace("\r".toCharArray().first(), '~')
                .replace("\n".toCharArray().first(), '^')
            response.clear()
        }
        result
    }

    // Команда инициализации.
    private fun resetCommand() = runBlocking {
        if (elmBluetoothService != null) {
            elmBluetoothService!!.write(COMMAND_RESET)
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            response.clear()
        }
    }

    // Команда забора запускаемого текста.
    private fun nullCommand() = runBlocking {
        if (elmBluetoothService != null) {
            jobCommand = GlobalScope.launch(Dispatchers.Default) {
                delay(TIMEOUT_COMMAND_RESPONSE)
            }
            jobCommand!!.join()
            response.clear()
        }
    }

    companion object {
        const val TAG = "Dynah"
        const val ELM_DEVICE_NAME = "OBDII"
        const val COMMAND_RESET = "AT Z"
        const val COMMAND_ECHO_ON = "AT E1"
        const val COMMAND_LINEFEED_ON ="AT L1"
        const val RESPONSE_OK = "OK"
        const val COMMAND_INFO = "AT I"
        const val COMMAND_PID1 = "01 00"
        const val COMMAND_SPEED = "01 0D"
        const val COMMAND_RPM = "01 0C"
        const val COMMAND_VIN_COUNT = "09 01"
        const val COMMAND_VIN = "09 02"
        const val STATE_NONE = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3
        const val MESSAGE_CONNECTING = 4
        const val MESSAGE_CONNECT = 5
        const val MESSAGE_DISCONNECT = 6
        const val MESSAGE_READ = 7
        const val TIMEOUT_COMMAND_RESPONSE = 3000L
        const val TIMEOUT_INDICATE_CYCLE = 300L
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}