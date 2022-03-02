package ru.ircoder.dynolite

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _textUsers = MutableLiveData<String>().apply {
        value = "This is users Fragment"
    }
    private val _textGarage = MutableLiveData<String>().apply {
        value = "This is garage Fragment"
    }
    private val _textSpeed = MutableLiveData<Int>().apply {
        value = 0
    }
    val textUsers: LiveData<String> = _textUsers
    val textGarage: LiveData<String> = _textGarage
    val textSpeed: LiveData<Int> = _textSpeed
    fun textUsers(text: String) {
        _textUsers.value = text
    }
    fun consoleBluetooth(appendText: String) {
        _textUsers.value = textUsers.value + "\n" + appendText
    }
    fun textSpeed(speed: Int) {
        _textSpeed.value = speed
    }
}
