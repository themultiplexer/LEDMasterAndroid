package tech.rutschmann.ledmaster.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.polidea.rxandroidble2.*
import com.polidea.rxandroidble2.internal.RxBleLog
import tech.rutschmann.ledmaster.R
import tech.rutschmann.ledmaster.databinding.FragmentHomeBinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable

//import com.polidea.rxandroidble2.;

class HomeFragment : Fragment() {


    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!


    private lateinit var bleDevice: RxBleDevice
    private lateinit var rxBleClient: RxBleClient
    private var stateDisposable: Disposable? = null
    private var connectionDisposable: Disposable? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
                ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val macAddress = "84:CC:A8:5E:C6:FA"
        val characteristic = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
        rxBleClient = RxBleClient.create(requireContext())

        RxBleLog.updateLogOptions(LogOptions.Builder().setLogLevel(LogConstants.VERBOSE).build())

        bleDevice = rxBleClient.getBleDevice(macAddress)
        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onConnectionStateChange(it) }
            .let { stateDisposable = it }


        binding.connectButton.setOnClickListener{onConnectClicked()}

        return root
    }

    private fun onConnectClicked(){
        if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            triggerDisconnect()
        } else {
            bleDevice.establishConnection(false)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally { dispose() }
                .subscribe({ onConnectionReceived() }, { onConnectionFailure(it) })
                .let { connectionDisposable = it }
        }

        //disposable.dispose()
        Toast.makeText(getActivity(), "You clicked me.", Toast.LENGTH_SHORT).show()
    }

    private fun dispose() {
        connectionDisposable = null
        updateUI()
    }

    private fun triggerDisconnect() = connectionDisposable?.dispose()

    private fun onNotificationReceived(bytes: ByteArray) {
        //print("Change: ${bytes.toHex()}")
    }

    private fun updateUI() {
        binding.connectButton.setText(if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) "Disconnect" else "Connect")
    }
    private fun onConnectionFailure(throwable: Throwable) {
        Toast.makeText(getActivity(), "Connection error: $throwable", Toast.LENGTH_SHORT).show()
        println(throwable)
    }

    private fun onConnectionReceived(){
        Toast.makeText(getActivity(), "Connection received", Toast.LENGTH_SHORT).show()
    }

    private fun onConnectionStateChange(newState: RxBleConnection.RxBleConnectionState) {
        Toast.makeText(getActivity(), newState.toString(), Toast.LENGTH_SHORT).show()
        binding.connectionTextView.text = newState.toString()
        updateUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}