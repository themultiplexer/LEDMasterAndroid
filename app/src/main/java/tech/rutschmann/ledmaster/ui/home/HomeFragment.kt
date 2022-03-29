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
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.*
import com.polidea.rxandroidble2.internal.RxBleLog
import io.reactivex.Observable
import tech.rutschmann.ledmaster.R
import tech.rutschmann.ledmaster.databinding.FragmentHomeBinding
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import java.util.UUID

class HomeFragment : Fragment() {


    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var characteristicUuid: UUID
    private val disconnectTriggerSubject = PublishSubject.create<Unit>()
    private lateinit var connectionObservable: Observable<RxBleConnection>
    private val connectionDisposable = CompositeDisposable()
    private lateinit var rxBleClient: RxBleClient
    private lateinit var bleDevice: RxBleDevice
    private var stateDisposable: Disposable? = null

    private val inputBytes: ByteArray
        get() = "report:1".toByteArray()

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

        RxBleLog.updateLogOptions(LogOptions.Builder().setLogLevel(LogConstants.VERBOSE).build())
        rxBleClient = RxBleClient.create(requireContext())
        bleDevice = rxBleClient.getBleDevice(macAddress)
        connectionObservable = prepareConnectionObservable()
        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onConnectionStateChange(it) }
            .let { stateDisposable = it }


        binding.connectButton.setOnClickListener{onConnectClicked()}
        binding.writeButton.setOnClickListener{onWriteClick()}

        return root
    }

    private fun onConnectClicked(){
        if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            triggerDisconnect()
        } else {
            connectionObservable
                .flatMapSingle { it.discoverServices() }
                .flatMapSingle { it.getCharacteristic(characteristicUuid) }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { binding.connectButton.setText("Connecting") }
                .subscribe(
                    { characteristic ->
                        updateUI()
                        print("Hey, connection has been established!")
                    },
                    { onConnectionFailure(it) },
                    { updateUI() }
                )
                .let { connectionDisposable.add(it) }
        }

        Toast.makeText(getActivity(), "You clicked me.", Toast.LENGTH_SHORT).show()
    }

    private fun onWriteSuccess() {
        Toast.makeText(getActivity(), "Write Succeeded", Toast.LENGTH_SHORT).show()
    }
    private fun onWriteFailure(throwable: Throwable) {
        Toast.makeText(getActivity(), "Write Failed $throwable ", Toast.LENGTH_SHORT).show()
    }

    private fun onWriteClick() {
        if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            connectionObservable
                .firstOrError()
                .flatMap { it.writeCharacteristic(characteristicUuid, inputBytes) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ onWriteSuccess() }, { onWriteFailure(it) })
                .let { connectionDisposable.add(it) }
        }
    }

    private fun prepareConnectionObservable(): Observable<RxBleConnection> =
        bleDevice.establishConnection(false)
            .takeUntil(disconnectTriggerSubject)
            .compose(ReplayingShare.instance())

    private fun triggerDisconnect() = disconnectTriggerSubject.onNext(Unit)

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