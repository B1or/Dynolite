package ru.dynolite.elm7

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.dynolite.elm7.MainActivity.Companion.TIMEOUT_INDICATE_CYCLE
import ru.dynolite.elm7.databinding.FragmentInformationBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class InformationFragment : Fragment() {
    private lateinit var listener: InformationListener
    private val informationViewModel: InformationViewModel by activityViewModels()
    private var _binding: FragmentInformationBinding? = null
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = activity as InformationListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentInformationBinding.inflate(inflater, container, false)
        informationViewModel.device.observe(viewLifecycleOwner, {
            binding.textviewDevice.text = it
        })
        informationViewModel.version.observe(viewLifecycleOwner, {
            binding.textviewVersion.text = it
        })
        informationViewModel.connect.observe(viewLifecycleOwner, {
            if (it) {
                displayInfo()
            }
        })
        informationViewModel.speed.observe(viewLifecycleOwner, {
            binding.textviewSpeed.text =
                if (it > -1) it.toString()
                else getString(R.string.error)
        })
        informationViewModel.rpm.observe(viewLifecycleOwner, {
            binding.textviewRpm.text =
                if (it > -1) it.toString()
                else getString(R.string.error)
        })
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (informationViewModel.connect.value == true) displayInfo()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface InformationListener {
        fun infoCommand(): String?
        fun speedCommand(): Int
        fun rpmCommand(): Int
        fun vinCommand(): String?
    }

    private fun displayInfo() {
        val info = listener.infoCommand()
        if (info != null) {
            informationViewModel.setDevice(info.split(" ").first())
            informationViewModel.setVersion(info.split(" ")[1])
        } else {
            informationViewModel.setDevice(getString(R.string.error))
            informationViewModel.setVersion(getString(R.string.error))
        }
        // TODO VIN
        GlobalScope.launch(Dispatchers.Default) {
            while (informationViewModel.connect.value == true) {
                GlobalScope.launch(Dispatchers.Main) {
                    informationViewModel.setSpeed(listener.speedCommand())
                    informationViewModel.setRpm(listener.rpmCommand())
                }
                delay(TIMEOUT_INDICATE_CYCLE)
            }
        }
    }
}