package ru.ircoder.dynolite

data class Car(
    var uid: String = "",
    var name: String = "",
    var diameter: Int = 0,
    var weight: Int = 0,
    var area: Float = 0f,
    var coef: Float = 0f,
    var ratio: Float = 0f,
    var drag: ArrayList<Drag> = arrayListOf()
)

/*
Автомобиль:
uid - уникальный код.
name - название.
diameter - диаметр ведущего колеса, см.
weight - вес, кг.
area - поперечное сечение, кв. м.
coef - коэффициент сопротивления воздуха.
*/
