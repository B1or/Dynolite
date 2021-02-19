package ru.dynolite.elm7

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class InformationViewModel: ViewModel() {
    private val mutableDevice = MutableLiveData<String>()
    private val mutableVersion = MutableLiveData<String>()
    private val mutableConnect = MutableLiveData<Boolean>()
    private val mutableSpeed = MutableLiveData<Int>()
    private val mutableRpm = MutableLiveData<Int>()
    val device: LiveData<String> get() = mutableDevice
    val version: LiveData<String> get() = mutableVersion
    val connect: LiveData<Boolean> get() = mutableConnect
    val speed: LiveData<Int> get() = mutableSpeed
    val rpm: LiveData<Int> get() = mutableRpm
    fun setDevice(device: String) {
        mutableDevice.value = device
    }
    fun setVersion(version: String) {
        mutableVersion.value = version
    }
    fun setConnect(connect: Boolean) {
        mutableConnect.value = connect
    }
    fun setSpeed(speed: Int) {
        mutableSpeed.value = speed
    }
    fun setRpm(rpm: Int) {
        mutableRpm.value = rpm
    }
}