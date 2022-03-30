package tech.rutschmann.ledmaster.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.jakewharton.rx.ReplayingShare
import com.polidea.rxandroidble2.*
import com.polidea.rxandroidble2.internal.RxBleLog
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import tech.rutschmann.ledmaster.databinding.FragmentHomeBinding
import java.util.*


class HomeFragment : Fragment() {


    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val macAddress = "84:CC:A8:5E:C6:FA"
    private val characteristicUuid: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val statusCharacteristicUuid: UUID = UUID.fromString("d68315cf-d548-4374-a6d5-633a5970c03e")
    private val effects = arrayOf("Flash", "Pump", "Pixel Tube", "Pump Limiter", "Duck", "Sparkle", "UpDown", "Color Loop", "Blend", "Strobe", "Strobe2")
    private val disconnectTriggerSubject = PublishSubject.create<Unit>()
    private lateinit var connectionObservable: Observable<RxBleConnection>
    private val connectionDisposable = CompositeDisposable()
    private lateinit var rxBleClient: RxBleClient
    private lateinit var bleDevice: RxBleDevice
    private var stateDisposable: Disposable? = null
    private lateinit var pbars : Array<ProgressBar>

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    fun Boolean.toInt() = if (this) 1 else 0

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        pbars = arrayOf(binding.progressBar1, binding.progressBar2,binding.progressBar3, binding.progressBar4, binding.progressBar5, binding.progressBar6, binding.progressBar7)

        RxBleLog.updateLogOptions(LogOptions.Builder().setLogLevel(LogConstants.VERBOSE).build())
        rxBleClient = RxBleClient.create(requireContext())
        bleDevice = rxBleClient.getBleDevice(macAddress)
        connectionObservable = prepareConnectionObservable()
        bleDevice.observeConnectionStateChanges()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { onConnectionStateChange(it) }
            .let { stateDisposable = it }

        binding.connectButton.setOnClickListener{onConnectClicked()}
        binding.switch1.setOnCheckedChangeListener { _, isChecked ->
            val c = isChecked.toInt()
            send("report:$c")
        }

        binding.switch2.setOnCheckedChangeListener { _, isChecked ->
            val c = (!isChecked).toInt()
            send("static:$c")
        }
        val seekBars = arrayOf(binding.seekBar1, binding.seekBar2, binding.seekBar3)

        for (bar in seekBars){
            bar.setOnSeekBarChangeListener(seekListener())
        }

        for (i in effects.indices){
            val btnTag = Button(context)
            btnTag.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            btnTag.text = effects[i]
            btnTag.id = i

            btnTag.setOnClickListener { button ->
                Log.w("LEDMaster", button.id.toString())
                send("mode:${button.id}")
            }

            binding.linearLayout.addView(btnTag)
        }

        for (pbar in pbars){
            pbar.setOnClickListener { bar  ->
                for (ibar in pbars) {
                    if (ibar == bar){
                        ibar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF9800")));
                    } else {
                        ibar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#009688")));
                    }
                }
                send("band:${bar.tag}")
            }
        }


        return root
    }

    inner class seekListener: OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar) {}
        override fun onStopTrackingTouch(seekBar: SeekBar) {
            Log.w("LEDMaster", "changed ${seekBar.tag} ${seekBar.progress}")
            send("${seekBar.tag}:${seekBar.progress}")
        }
    }

    private fun onConnectClicked(){
        if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            triggerDisconnect()
        } else {
            connectionObservable
                .flatMap { it.setupNotification(characteristicUuid) }
                .flatMap { it }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ onNotificationReceived(it) }, { onNotificationSetupFailure(it) })
                .let { connectionDisposable.add(it) }

            connectionObservable
                .flatMap { it.setupNotification(statusCharacteristicUuid) }
                .flatMap { it }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ onStatusNotificationReceived(it) }, { onNotificationSetupFailure(it) })
                .let { connectionDisposable.add(it) }

            connectionObservable
                .flatMapSingle { it.discoverServices() }
                .flatMapSingle { it.getCharacteristic(characteristicUuid) }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { binding.connectButton.setText("Connecting") }
                .subscribe(
                    { characteristic ->
                        updateUI()
                        Log.w("LEDMaster", "Hey, connection has been established!")
                        send("request")
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

    fun send(data:String){
        Log.w("LEDMaster", data)
        if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
            connectionObservable
                .firstOrError()
                .flatMap { it.writeCharacteristic(characteristicUuid, data.toByteArray()) }
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

    private fun onStatusNotificationReceived(bytes: ByteArray) {
        binding.seekBar1.setProgress(bytes[0].toUByte().toInt(),true)
        binding.seekBar2.setProgress(bytes[2].toUByte().toInt(),true)
        binding.seekBar3.setProgress(bytes[1].toUByte().toInt(),true)
    }

    private fun onNotificationReceived(bytes: ByteArray) {
        for (i in bytes.indices){
            pbars[i].setProgress(bytes[i].toUByte().toInt(), true)
        }
    }

    private fun updateUI() {
        binding.connectButton.setText(if (bleDevice.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) "Disconnect" else "Connect")
    }

    private fun onNotificationSetupFailure(throwable: Throwable) {
        Toast.makeText(getActivity(), "Notification error: $throwable", Toast.LENGTH_SHORT).show()
        Log.w("LEDMaster", throwable)
    }

    private fun onConnectionFailure(throwable: Throwable) {
        Toast.makeText(getActivity(), "Connection error: $throwable", Toast.LENGTH_LONG).show()
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