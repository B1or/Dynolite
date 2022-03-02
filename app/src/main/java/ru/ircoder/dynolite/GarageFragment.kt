package ru.ircoder.dynolite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import ru.ircoder.dynolite.databinding.FragmentGarageBinding
import ru.ircoder.dynolite.databinding.ItemCarBinding

class GarageFragment : Fragment() {

    private var _binding: FragmentGarageBinding? = null
    private val binding get() = _binding!!
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var adapter: FirebaseRecyclerAdapter<Car, CarViewHolder>? = null
    private var user: FirebaseUser? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGarageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth.addAuthStateListener { it ->
            user = it.currentUser
            user?.let { listCar(it) }
        }
        binding.fabAddCar.setOnClickListener {
            // TODO
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

    private fun listCar(user: FirebaseUser) {
        binding.rvCars.layoutManager = LinearLayoutManager(this.context)
        val query = firebaseDatabase.reference.child("users").child(user.uid).child("cars")
        val options = FirebaseRecyclerOptions.Builder<Car>()
            .setQuery(query, Car::class.java)
            .setLifecycleOwner(this)
            .build()
        adapter = object : FirebaseRecyclerAdapter<Car, CarViewHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val binding = ItemCarBinding.inflate(inflater)
                return CarViewHolder(binding)
            }
            override fun onBindViewHolder(holder: CarViewHolder, position: Int, model: Car) {
                holder.bind(model)
            }
        }
        binding.rvCars.adapter = adapter
    }

    class CarViewHolder(private val binding: ItemCarBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(car: Car) {
            with(binding) {
                tvCarName.text = car.name
            }
        }
    }
}