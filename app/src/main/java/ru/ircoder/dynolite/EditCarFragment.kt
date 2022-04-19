package ru.ircoder.dynolite

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import ru.ircoder.dynolite.GarageFragment.Companion.ARG_CAR
import ru.ircoder.dynolite.GarageFragment.Companion.ARG_USER
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_CARS
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_USERS
import ru.ircoder.dynolite.databinding.FragmentCarEditBinding

class EditCarFragment : Fragment() {
    private var _binding: FragmentCarEditBinding? = null
    private val binding get() = _binding!!
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private lateinit var uidUser: String
    private lateinit var uidCar: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            uidUser = bundle.getString(ARG_USER) ?: ""
            uidCar = bundle.getString(ARG_CAR) ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentCarEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val query = firebaseDatabase.reference.child(FIREBASE_USERS).child(uidUser).child(FIREBASE_CARS).child(uidCar)
        query.get()
            .addOnSuccessListener {
                val car = it.getValue<Car>()
                if (car != null) {
                    binding.etNameEditCar.setText(car.name)
                    binding.etDiameterEditCar.setText(car.diameter.toString())
                    binding.etWeightEditCar.setText(car.weight.toString())
                    binding.etAreaEditCar.setText(car.area.toString())
                    binding.etCoefEditCar.setText(car.coef.toString())
                    binding.bAcceptEditCar.setOnClickListener {
                        car.name = binding.etNameEditCar.text.toString()
                        car.diameter = binding.etDiameterEditCar.text.toString().toInt()
                        car.weight = binding.etWeightEditCar.text.toString().toInt()
                        car.area = binding.etAreaEditCar.text.toString().toFloat()
                        car.coef = binding.etCoefEditCar.text.toString().toFloat()
                        if (car.name.isNotBlank() && uidUser.isNotBlank() && car.uid.isNotBlank()) {
                            query.setValue(car)
                            findNavController().popBackStack()
                        }
                    }
                }
            }
    }
}