package ru.ircoder.dynolite

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import ru.ircoder.dynolite.databinding.FragmentUsersBinding
import ru.ircoder.dynolite.databinding.ItemUserBinding

class UsersFragment : Fragment() {

    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!
    private val model: SharedViewModel by activityViewModels()
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var adapter: FirebaseRecyclerAdapter<User, UserViewHolder>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val textView: TextView = binding.textUsers
        model.textUsers.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth.addAuthStateListener {
            val listenedUser = it.currentUser
            if (listenedUser != null) checkUser(listenedUser)
        }
    }

    override fun onStart() {
        super.onStart()
        adapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        adapter?.stopListening()
    }

    private fun checkUser(user: FirebaseUser) {
        val reference = firebaseDatabase.reference.child("users").child(user.uid)
        reference.get()
            .addOnSuccessListener {
                if (it.exists()) listUser()
                else reference.child("name").setValue(user.displayName)
                    .addOnSuccessListener {
                        reference.child("phone").setValue(user.phoneNumber).addOnSuccessListener {
                            listUser()
                        }
                    }
            }
    }

    private fun listUser() {
        binding.rvUsers.layoutManager = LinearLayoutManager(this.context)
        val query = firebaseDatabase.reference.child("users")
        val options = FirebaseRecyclerOptions.Builder<User>()
            .setQuery(query, User::class.java)
            .setLifecycleOwner(this)
            .build()
        adapter = object : FirebaseRecyclerAdapter<User, UserViewHolder>(options) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val binding = ItemUserBinding.inflate(inflater)
                return UserViewHolder(binding)
            }
            override fun onBindViewHolder(holder: UserViewHolder, position: Int, model: User) {
                holder.bind(model)
            }
        }
        binding.rvUsers.adapter = adapter
    }

    class UserViewHolder(private val binding: ItemUserBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            with(binding) {
                tvUserName.text = user.name
                tvUserPhone.text = user.phone
            }
        }
    }
}
