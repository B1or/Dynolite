package ru.ircoder.dynolite

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _drag = MutableLiveData<Drag>().apply {
        value = Drag(0, 0, 0)
    }
    val drag: LiveData<Drag> = _drag
    fun drag(drag: Drag) {
        _drag.value = drag
    }
    fun speed(speed: Int) {
        _drag.value!!.speed = speed
    }
    fun rpm(rpm: Int) {
        _drag.value!!.rpm = rpm
    }
}
