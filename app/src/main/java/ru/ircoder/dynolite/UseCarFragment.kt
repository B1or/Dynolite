package ru.ircoder.dynolite

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.activityViewModels
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.ircoder.dynolite.GarageFragment.Companion.ARG_CAR
import ru.ircoder.dynolite.GarageFragment.Companion.ARG_USER
import ru.ircoder.dynolite.MainActivity.Companion.COUNT_TESTING_RATIO
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_CARS
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_USERS
import ru.ircoder.dynolite.MainActivity.Companion.FORMAT_RATIO
import ru.ircoder.dynolite.MainActivity.Companion.POINTS_X
import ru.ircoder.dynolite.MainActivity.Companion.POINTS_Y
import ru.ircoder.dynolite.MainActivity.Companion.RATIO_SCATTER
import ru.ircoder.dynolite.MainActivity.Companion.TAG
import ru.ircoder.dynolite.databinding.FragmentCarUseBinding
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow

class UseCarFragment : Fragment() {
    private var _binding: FragmentCarUseBinding? = null
    private val binding get() = _binding!!
    private val model: SharedViewModel by activityViewModels()
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private val reference = firebaseDatabase.reference
    private lateinit var uidUser: String
    private lateinit var uidCar: String
    private var flagTestingDrag = false
    private var oldFlagTestingDrag = false
    private var flagTestingRatio = false
    private var indexTestingRatio = 0
    private val arrayTestingRatio = Array(COUNT_TESTING_RATIO) { 0f }
    private val arrayTestingDrag = arrayListOf<Drag>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            uidUser = it.getString(ARG_USER) ?: ""
            uidCar = it.getString(ARG_CAR) ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCarUseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val queryCar = reference.child(FIREBASE_USERS).child(uidUser).child(FIREBASE_CARS).child(uidCar)
        queryCar.get()
            .addOnSuccessListener { dataSnapshot ->
                val car = dataSnapshot.getValue<Car>()
                if (car != null) {
                    binding.tvNameUseCar.text = car.name
                    binding.tvDiameterUseCar.text = car.diameter.toString()
                    binding.tvWeightUseCar.text = car.weight.toString()
                    binding.tvAreaUseCar.text = car.area.toString()
                    binding.tvCoefUseCar.text = car.coef.toString()
                    if (car.diameter > 0) {
                        model.drag.observe(viewLifecycleOwner) { item ->
                            if (flagTestingRatio) {
                                if (indexTestingRatio < COUNT_TESTING_RATIO)
                                    arrayTestingRatio[indexTestingRatio++] = ratio(item.speed, item.rpm, car.diameter)
                                else {
                                    var average = 0f
                                    arrayTestingRatio.forEach { testingRatio ->
                                        average += testingRatio
                                    }
                                    average /= arrayTestingRatio.size
                                    indexTestingRatio = 0
                                    car.ratio = average
                                    queryCar.setValue(car)
                                    binding.tvRatioDrag.text = String.format(FORMAT_RATIO, average)
                                    binding.tvRatioDrag.visibility = View.VISIBLE
                                    binding.tvLabelRatioDrag.visibility = View.VISIBLE
                                    binding.bRatioDrag.visibility = View.GONE
                                    binding.bRatioDrag.isEnabled = true
                                    binding.bStartDrag.isEnabled = true
                                    flagTestingRatio = false
                                }
                            }
                            if (flagTestingDrag) {
                                if (!oldFlagTestingDrag)
                                    arrayTestingDrag.clear()
                                arrayTestingDrag.add(item)
                            }
                            else
                                if (oldFlagTestingDrag) {
                                    CoroutineScope(Dispatchers.Default).launch {
                                        car.drag = arrayTestingDrag
                                        queryCar.setValue(car)
                                    }
                                }
                            oldFlagTestingDrag = flagTestingDrag
                        }
                        if (car.ratio > 0.001) {
                            binding.tvLabelRatioDrag.visibility = View.VISIBLE
                            binding.tvRatioDrag.visibility = View.VISIBLE
                            binding.bRatioDrag.visibility = View.GONE
                            binding.tvRatioDrag.text = String.format(FORMAT_RATIO, car.ratio)
                            binding.bStartDrag.isEnabled = true
                            binding.tvRatioDrag.setOnClickListener {
                                binding.tvLabelRatioDrag.visibility = View.GONE
                                binding.tvRatioDrag.visibility = View.GONE
                                binding.bRatioDrag.visibility = View.VISIBLE
                                binding.bStartDrag.isEnabled = false
                            }
                        } else {
                            binding.tvLabelRatioDrag.visibility = View.GONE
                            binding.tvRatioDrag.visibility = View.GONE
                            binding.bRatioDrag.visibility = View.VISIBLE
                            binding.bStartDrag.isEnabled = false
                        }
                        if (car.drag.isNotEmpty()) binding.bGraphUseCar.visibility = View.VISIBLE
                        else binding.bGraphUseCar.visibility = View.GONE
                        binding.bStartDrag.setOnClickListener {
                            it.isEnabled = false
                            flagTestingDrag = true
                            binding.bStopDrag.isEnabled =true
                        }
                        binding.bStopDrag.setOnClickListener {
                            it.isEnabled = false
                            flagTestingDrag = false
                            binding.bStartDrag.isEnabled = true
                        }
                        binding.bRatioDrag.setOnClickListener {
                            it.isEnabled = false
                            flagTestingRatio = true
                        }
                        binding.bGraphUseCar.setOnClickListener { it ->   // TODO
                            it.isEnabled = false
                                if (car.drag.isNotEmpty()) {
                                    processingDrag(queryCar)
                                }
                        }
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        binding.bGraphUseCar.isEnabled = true
    }

    private fun processingDrag(referenceCar: DatabaseReference) {
        var result: Array<Drag>? = null
        referenceCar.get()
            .addOnSuccessListener {
                val car = it.getValue<Car>()
                if (car != null && car.drag.isNotEmpty()) {
                    val arrayDrag = sort(car.drag)
                    if (car.ratio > 0.001 && car.diameter > 0) {
                        val diameter = car.diameter
                        val ratio = car.ratio
                        //<editor-fold desc="выборка по передаточному числу">
                        var maxSizeInterval = 0
                        var maxStartInterval = -1
                        var startLooper = 0
                        while (startLooper < arrayDrag.size) {
                            var startInterval = startLooper - 1
                            var stopInterval = arrayDrag.size
                            for (i: Int in startLooper until arrayDrag.size) {
                                if (inScatter(ratio, ratio(arrayDrag[i].speed, arrayDrag[i].rpm, diameter))) {
                                    startInterval = i
                                    break
                                }
                            }
                            val sizeInterval = if (startInterval >= startLooper) {
                                for (i: Int in startInterval + 1 until arrayDrag.size) {
                                    if (!inScatter(ratio, ratio(arrayDrag[i].speed, arrayDrag[i].rpm, diameter))) {
                                        stopInterval = i
                                        break
                                    }
                                }
                                stopInterval - startInterval
                            } else 0
                            if (sizeInterval > maxSizeInterval) {
                                maxSizeInterval = sizeInterval
                                maxStartInterval = startInterval
                            }
                            startLooper = stopInterval
                        }
                        //</editor-fold>
                        if (maxSizeInterval > 0) {
                            val arrayRatio = Array(maxSizeInterval) { Drag(0, 0, 0) }
                            arrayRatio.forEachIndexed { index, _ ->
                                arrayRatio[index] = arrayDrag[index + maxStartInterval]
                            }
                            Log.d(TAG, "arrayRatio size ${arrayRatio.size}")
                            //<editor-fold desc="выборка по изменению скорости">
                            val arrayListSpeed: ArrayList<Drag> = arrayListOf()
                            var oldSpeed = -1
                            arrayRatio.forEach { drag ->
                                if (drag.speed != oldSpeed)
                                    arrayListSpeed.add(drag)
                                oldSpeed = drag.speed
                            }
                            //</editor-fold>
                            Log.d(TAG, "arrayListSpeed size: ${arrayListSpeed.size}")
                            //<editor-fold desc="наибольший интервал по увеличению скорости">
                            maxSizeInterval = 0
                            maxStartInterval = 0
                            var currentSizeInterval = 0
                            var currentStartInterval =  0
                            var previousSign = false
                            var currentSign: Boolean
                            for (i: Int in 1 until arrayListSpeed.size) {
                                currentSign = arrayListSpeed[i].speed > arrayListSpeed[i - 1].speed
                                if (currentSign)
                                    currentSizeInterval++
                                else {
                                    if (previousSign && currentSizeInterval > maxSizeInterval) {
                                        maxSizeInterval = currentSizeInterval
                                        maxStartInterval = currentStartInterval
                                    }
                                    currentSizeInterval = 0
                                    currentStartInterval = i
                                }
                                previousSign = currentSign
                            }
                            val arraySpeed = Array(maxSizeInterval) { Drag(0, 0, 0) }
                            for (i: Int in 0 until maxSizeInterval)
                                arraySpeed[i] = arrayListSpeed[i + maxStartInterval]
                            //</editor-fold>
                            Log.d(TAG, "arraySpeed size: ${arraySpeed.size}")
                            power(arraySpeed, car)

                        }
                    }
                }
            }
    }

    private fun power(array: Array<Drag>, car: Car) {
        Log.d(TAG, "array size: ${array.size}")
        val weight = car.weight
        val area = car.area
        val coef = car.coef
        val powerDrag = arrayListOf<Power>()
        var time0 = array[0].time
        var speed0 = array[0].speed
        for (i: Int in 1 until array.size) {
            val time = array[i].time
            val speed = array[i].speed
/*
    формула мгновенной мощности -  P = ( (v2)2 - (v1)2 ) * m / 2 / t  - где
        v1 - начальная скорость
        v2 - конечная скорость
        m - масса автомобиля
        t - время
        (v1)2 - начальная скорость в квадрате
        (v2)2 - конечная скорость в квадрате
*/
            val instant =                                   // формула мгновенной мощности, Вт
                ((speed / 3.6).pow(2) -                  // конечная скорость км/ч -> м/сек в квадрате
                    (speed0 / 3.6).pow(2)) *             // начальная скорость км/ч -> м/сек в квадрате
                    weight /                                // масса автомобиля, кг
                    2 /                                     // делить на два
                    ((time - time0) / 1000.0)               // время, миллисек -> сек

/*
    формула сопротивления воздуха -  P = S * С * p * (v)3 / 2  - где
        S - поеречное сечение автомобиля
        C - коэффициент сопротивления воздуха
        p - плотность воздуха
        v - скорость
        (v)3 - скорость в кубе
*/
            val aero =                                      // расчёт сопротивления воздуха, Вт
                area *                                      // поперечное сечение автомобиля, кв. м.
                    coef *                                  // коэффициент сопротивления воздуха
                    (speed / 3.6).pow(3) *               // скорость км/час -> м/сек в кубе
                    1.22 /                                  // плотность воздуха, кг/м3
                    2                                       // делить на два

            val power = (instant + aero) / 735.5            // общая мощность, Вт -> л. с.
            powerDrag.add(Power(array[i].rpm, power))

            time0 = time
            speed0 = speed
        }

/*
        //<editor-fold desc="запись в базу данных">
        val queryCar = reference.child(FIREBASE_USERS).child(uidUser).child(FIREBASE_CARS).child(uidCar)
        val queryTest = queryCar.child("test")
        queryTest.removeValue()
            .addOnSuccessListener {
                powerDrag.forEachIndexed { index, drag ->
                    queryTest.child(index.toString()).setValue(drag)
                }
            }
        //</editor-fold>
*/

        val pointsX = DoubleArray(powerDrag.size) { 0.0 }
        val pointsY = DoubleArray(powerDrag.size) { 0.0 }
        powerDrag.forEachIndexed { index, dragData ->
            // pointsX[index] = dragData.time / 1000.0              // график от времени
            pointsX[index] = dragData.rpm.toDouble()                // график от оборотов
            pointsY[index] = dragData.power
        }
        val intent = Intent(requireContext(), GraphActivity::class.java)
        val bundle = Bundle()
        bundle.putDoubleArray(POINTS_X, pointsX)
        bundle.putDoubleArray(POINTS_Y, pointsY)
        intent.putExtras(bundle)
        startActivity(intent)

        // TODO вычисление мощности
        // TODO построение графика
    }

    private fun sort(inputArrayList: ArrayList<Drag>): Array<Drag>   // сортировка
    {
        val resultArray = Array(inputArrayList.size) { Drag(0, 0, 0) }
        resultArray.forEachIndexed { index, _ ->
            var minIndex = 0
            inputArrayList.forEachIndexed { indexList, list ->
                if (list.time < inputArrayList[minIndex].time)
                    minIndex = indexList
            }
            resultArray[index] = inputArrayList[minIndex]
            inputArrayList.removeAt(minIndex)
        }
        val startTime = resultArray[0].time
        resultArray.forEachIndexed { index, drag ->
            resultArray[index] = Drag(drag.speed, drag.rpm, drag.time - startTime)
        }
        return resultArray
    }

    private fun ratio(speed: Int, rpm: Int, diameter: Int) = rpm * 60 * diameter * PI.toFloat() / 100 / speed / 1000   // передаточное число

    private fun inScatter(ratio1: Float, ratio2: Float) = abs(ratio1 - ratio2) / min(ratio1, ratio2) < RATIO_SCATTER   // в пределах разброса
}