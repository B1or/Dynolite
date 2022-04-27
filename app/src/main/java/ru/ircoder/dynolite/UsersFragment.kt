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
import ru.ircoder.dynolite.MainActivity.Companion.FIREBASE_USERS
import ru.ircoder.dynolite.databinding.FragmentUsersBinding
import ru.ircoder.dynolite.databinding.ItemUsersBinding

class UsersFragment : Fragment() {

    private var _binding: FragmentUsersBinding? = null
    private val binding get() = _binding!!
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firebaseDatabase = FirebaseDatabase.getInstance()
    private var adapter: FirebaseRecyclerAdapter<User, UserViewHolder>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvUsers.layoutManager = LinearLayoutManager(this.context)
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
        val reference = firebaseDatabase.reference
        val queryUser = reference.child(FIREBASE_USERS).child(user.uid)
        queryUser.get()
            .addOnSuccessListener {
                if (it.exists()) listUser()
                else {
                    val queryUsers = reference.child(FIREBASE_USERS)
                    val uid = user.uid
                    queryUsers.child(uid).setValue(User(uid, user.displayName ?: "", user.phoneNumber ?: ""))
                        .addOnSuccessListener {
                            listUser()
                        }
                }
            }
    }

    private fun listUser() {
        val query = firebaseDatabase.reference.child(FIREBASE_USERS)
        val options = FirebaseRecyclerOptions.Builder<User>()
            .setQuery(query, User::class.java)
            .setLifecycleOwner(this)
            .build()

        adapter = object : FirebaseRecyclerAdapter<User, UserViewHolder>(options) {

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                val binding = ItemUsersBinding.inflate(inflater)
                return UserViewHolder(binding)
            }

            override fun onBindViewHolder(holder: UserViewHolder, position: Int, model: User) {
                holder.bind(model)
            }
        }

        if (_binding != null) binding.rvUsers.adapter = adapter
    }

    inner class UserViewHolder(private val binding: ItemUsersBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            with(binding) {
                tvUserName.text = user.name
                tvUserPhone.text = user.phone
            }
        }
    }
}
