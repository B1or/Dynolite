package ru.ircoder.dynolite

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.firebase.database.FirebaseDatabase
import ru.ircoder.dynolite.GarageFragment.Companion.ARG_USER
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_CARS
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_USERS
import ru.ircoder.dynolite.databinding.FragmentCarAddBinding

class AddCarFragment : Fragment() {
    private var _binding: FragmentCarAddBinding? = null
    private val binding get() = _binding!!
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private lateinit var uidUser: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            uidUser = bundle.getString(ARG_USER) ?: ""
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCarAddBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.bAddAddCar.setOnClickListener {
            val name = binding.etNameAddCar.text.toString()
            val diameter = binding.etDiameterAddCar.text.toString().toInt()
            val weight = binding.etWeightAddCar.text.toString().toInt()
            val area = binding.etAreaAddCar.text.toString().toFloat()
            val coef = binding.etCoefAddCar.text.toString().toFloat()
            if (name.isNotBlank() && uidUser.isNotBlank()) {
                val query = firebaseDatabase.reference.child(FIREBASE_USERS).child(uidUser).child(FIREBASE_CARS)
                val key = query.push().key
                if (!key.isNullOrBlank()) {
                    query.child(key).setValue(Car(key, name, diameter, weight, area, coef))
                    findNavController().popBackStack()
                }
            }
        }
    }
}