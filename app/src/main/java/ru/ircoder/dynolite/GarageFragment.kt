package ru.ircoder.dynolite

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_CARS
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_USERS
import ru.ircoder.dynolite.databinding.FragmentGarageBinding
import ru.ircoder.dynolite.databinding.ItemCarsBinding

class GarageFragment : Fragment() {

    private var _binding: FragmentGarageBinding? = null
    private val binding get() = _binding!!
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var adapter: FirebaseRecyclerAdapter<Car, CarViewHolder>? = null
    private var user: FirebaseUser? = null
    private var uidUser = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGarageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth.addAuthStateListener { it ->
            user = it.currentUser
            user?.let { listCar(it, view) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        adapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        adapter?.stopListening()
    }

    private fun listCar(user: FirebaseUser, view: View) {
        binding.rvCars.layoutManager = LinearLayoutManager(this.context)
        uidUser = user.uid
        val query = firebaseDatabase.reference.child(FIREBASE_USERS).child(uidUser).child(FIREBASE_CARS)
        val options = FirebaseRecyclerOptions.Builder<Car>()
            .setQuery(query, Car::class.java)
            .setLifecycleOwner(this)
            .build()

        adapter = object : FirebaseRecyclerAdapter<Car, CarViewHolder>(options) {

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val binding = ItemCarsBinding.inflate(inflater)
                return CarViewHolder(binding)
            }

            override fun onBindViewHolder(holder: CarViewHolder, position: Int, model: Car) {
                holder.bind(model)
            }
        }

        binding.rvCars.adapter = adapter
        binding.fabAddCar.setOnClickListener {
            val bundle = bundleOf(ARG_USER to uidUser)
            findNavController().navigate(R.id.action_car_add, bundle)
        }
    }

    inner class CarViewHolder(private val bindingItem: ItemCarsBinding): RecyclerView.ViewHolder(bindingItem.root) {
        fun bind(car: Car) {
            with(bindingItem) {
                tvNameItemCars.text = car.name
                ivMenuItemCars.setOnClickListener {
                    val popupMenu = PopupMenu(requireContext(), bindingItem.ivMenuItemCars)
                    activity?.menuInflater?.inflate(R.menu.item_menu, popupMenu.menu)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true)
                    popupMenu.setOnMenuItemClickListener {
                        when (it.itemId) {
                            R.id.edit -> {
                                val bundle = bundleOf(ARG_USER to uidUser, ARG_CAR to car.uid)
                                findNavController().navigate(R.id.action_car_edit, bundle)
                            }
                            R.id.delete -> {
                                val builder = AlertDialog.Builder(requireContext())
                                builder.apply {
                                    setTitle(R.string.remove)
                                    setMessage(getString(R.string.car_) + car.name)
                                    setPositiveButton(R.string.delete) { _, _ ->
                                        val query = firebaseDatabase.reference.child(FIREBASE_USERS).child(uidUser).child(FIREBASE_CARS).child(car.uid)
                                        query.removeValue()
                                    }
                                    setNegativeButton(R.string.cancel) { _, _ ->
                                    }
                                }
                                val dialog: AlertDialog = builder.create()
                                dialog.show()
                            }
                            R.id.use -> {
                                val bundle = bundleOf(ARG_USER to uidUser, ARG_CAR to car.uid)
                                findNavController().navigate(R.id.action_car_use, bundle)
                            }
                        }
                        true
                    }
                    popupMenu.show()
                }
            }
        }
    }

    companion object {
        const val ARG_USER = "user"
        const val ARG_CAR = "car"
    }
}