package kr.young.rtp.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.*
import android.os.Build
import android.os.Build.VERSION_CODES.M
import android.util.Log
import kr.young.common.DebugLog
import kr.young.rtp.util.RTCBluetoothManager.State.*
import org.webrtc.ThreadUtils

class RTCAudioManager private constructor(val context: Context) {

    enum class AudioDevice {
        SPEAKER_PHONE,
        WIRED_HEADSET,
        EARPIECE,
        BLUETOOTH,
        NONE
    }

    enum class AudioManagerState {
        UNINITIALIZED,
        PREINITIALIZED,
        RUNNING
    }

    interface AudioManagerEvents {
        fun onAudioDeviceChanged(
            selectedAudioDevice: AudioDevice,
            availableAudioDevices: Set<AudioDevice>
        )
    }

    private var audioManager: AudioManager?
    private var audioManagerEvents: AudioManagerEvents? = null
    private var audioManagerState: AudioManagerState
    private var savedAudioMode = MODE_NORMAL
    private var savedIsSpeakerPhoneOn = false
    private var savedIsMicrophoneMute = false
    private var hasWiredHeadset = false

    // Default audio device; speaker phone for video calls or earpiece for audio
    // only calls.
    private var defaultAudioDevice: AudioDevice

    // Contains the currently selected audio device.
    // This device is changed automatically using a certain scheme where e.g.
    // a wired headset "wins" over speaker phone. It is also possible for a
    // user to explicitly select a device (and overrid any predefined scheme).
    // See |userSelectedAudioDevice| for details.
    private var selectedAudioDevice = AudioDevice.NONE

    // Contains the user-selected audio device which overrides the predefined
    // selection scheme.
    // TODO: always set to AudioDevice.NONE today. Add support for
    // explicit selection based on choice by userSelectedAudioDevice.
    private var userSelectedAudioDevice = AudioDevice.NONE

    // Contains speakerphone setting: auto, true or false
    private val useSpeakerphone: String = "false"

    // Proximity sensor object. It measures the proximity of an object in cm
    // relative to the view screen of a device and can therefore be used to
    // assist device switching (close to ear <=> use headset earpiece if
    // available, far from ear <=> use speaker phone).
    private var proximitySensor: RTCProximitySensor?

    // Handles all tasks related to Bluetooth headset devices.
    private val bluetoothManager: RTCBluetoothManager

    // Contains a list of available audio devices. A Set collection is used to
    // avoid duplicate elements.
    private var audioDevices = HashSet<AudioDevice>()

    // Broadcast receiver for wired headset intent broadcasts.
    private var wiredHeadsetReceiver: BroadcastReceiver

    // Callback method for changes in audio focus.
    private var audioFocusChangeListener: OnAudioFocusChangeListener? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private fun onProximitySensorChangedState() {
        if (useSpeakerphone != SPEAKERPHONE_AUTO) {
            return
        }

        if (audioDevices.size == 2 &&
            audioDevices.contains(AudioDevice.EARPIECE) &&
            audioDevices.contains(AudioDevice.SPEAKER_PHONE)) {
            if (proximitySensor!=null && proximitySensor!!.sensorReportsNearState()) {
                setAudioDeviceInternal(AudioDevice.EARPIECE)
            } else {
                setAudioDeviceInternal(AudioDevice.SPEAKER_PHONE)
            }
        }
    }

    private inner class WiredHeadsetReceiver: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra("state", STATE_UNPLUGGED)
            val microphone = intent?.getIntExtra("microphone", HAS_NO_MIC)
            val name = intent?.getStringExtra("name")
            DebugLog.d(TAG, "WiredHeadsetReceiver.onReceive ${RTCUtils.getThreadInfo()}: " +
                    "a=${intent?.action}, " +
                    "s=${if (state == STATE_UNPLUGGED) "unplugged" else "plugged"}, " +
                    "m=${if (microphone == HAS_MIC) "mic" else "no mic"}, " +
                    "n=$name, " +
                    "sb=$isInitialStickyBroadcast")
            hasWiredHeadset = state == STATE_PLUGGED
            updateAudioDeviceState()
        }
    }

    init {
        ThreadUtils.checkIsOnMainThread()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        bluetoothManager = RTCBluetoothManager.create(context, this)
        wiredHeadsetReceiver = WiredHeadsetReceiver()
        audioManagerState = AudioManagerState.UNINITIALIZED

        DebugLog.d(TAG, "useSpeakerphone: $useSpeakerphone")
        defaultAudioDevice = if (useSpeakerphone == SPEAKERPHONE_FALSE) {
            AudioDevice.EARPIECE
        } else {
            AudioDevice.SPEAKER_PHONE
        }

        proximitySensor = RTCProximitySensor.create(context, this::onProximitySensorChangedState)
        DebugLog.d(TAG, "defaultAudioDevice: $defaultAudioDevice")
        RTCUtils.logDeviceInfo(TAG)
    }

    fun start(audioManagerEvents: AudioManagerEvents) {
        DebugLog.i(TAG, "start")
        ThreadUtils.checkIsOnMainThread()
        if (audioManagerState == AudioManagerState.RUNNING) {
            DebugLog.e(TAG, "AudioManager is already active")
            return
        }

        DebugLog.d(TAG, "AudioManager starts...")
        this.audioManagerEvents = audioManagerEvents
        audioManagerState = AudioManagerState.RUNNING

        savedAudioMode = audioManager!!.mode
        savedIsSpeakerPhoneOn = audioManager!!.isSpeakerphoneOn
        savedIsMicrophoneMute = audioManager!!.isMicrophoneMute
        hasWiredHeadset = hasWiredHeadset()

        audioFocusChangeListener = OnAudioFocusChangeListener {
            val typeOfChange = when (it) {
                AUDIOFOCUS_GAIN -> "AUDIOFOCUS_GAIN"
                AUDIOFOCUS_GAIN_TRANSIENT -> "AUDIOFOCUS_GAIN_TRANSIENT"
                AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE"
                AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"
                AUDIOFOCUS_LOSS -> "AUDIOFOCUS_LOSS"
                AUDIOFOCUS_LOSS_TRANSIENT -> "AUDIOFOCUS_LOSS_TRANSIENT"
                AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK"
                else -> "AUDIOFOCUS_INVALID"
            }
            DebugLog.d(TAG, "onAudioFocusChangeL $typeOfChange")
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
//                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener!!)
                .build()
            audioManager!!.requestAudioFocus(audioFocusRequest!!)
        } else {
            audioManager!!.requestAudioFocus(
                audioFocusChangeListener,
                STREAM_VOICE_CALL,
                AUDIOFOCUS_GAIN_TRANSIENT)
        }
        when (result) {
            AUDIOFOCUS_REQUEST_GRANTED ->
                DebugLog.d(TAG, "Audio focus request granted for VOICE_CALL streams")
            else -> DebugLog.d(TAG, "Audio focus request failed")
        }

        audioManager!!.mode = MODE_IN_COMMUNICATION
        setMicrophoneMute(false)

        userSelectedAudioDevice = AudioDevice.NONE
        selectedAudioDevice = AudioDevice.NONE
        audioDevices.clear()

        bluetoothManager.start()
        updateAudioDeviceState()
        registerReceiver(wiredHeadsetReceiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        DebugLog.d(TAG, "AudioManager started")
    }

    fun stop() {
        Log.d(TAG, "stop")
        ThreadUtils.checkIsOnMainThread()
        if (audioManagerState != AudioManagerState.RUNNING) {
            DebugLog.e(TAG, "Trying to stop AudioManager in incorrect state: $audioManagerState")
            return
        }
        audioManagerState = AudioManagerState.UNINITIALIZED
        unregisterReceiver(wiredHeadsetReceiver)
        bluetoothManager.stop()

        setSpeakerphoneOn(savedIsSpeakerPhoneOn)
        setMicrophoneMute(savedIsMicrophoneMute)
        audioManager!!.mode = savedAudioMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager!!.abandonAudioFocusRequest(audioFocusRequest!!)
            audioFocusRequest = null
        } else {
            audioManager!!.abandonAudioFocus(audioFocusChangeListener)
        }
        audioFocusChangeListener = null
        DebugLog.d(TAG, "Abandoned audio focus for VOICE_CALL streams")

        if (proximitySensor != null) {
            proximitySensor!!.stop()
            proximitySensor = null
        }

        audioManagerEvents = null
        DebugLog.i(TAG, "AudioManager stopped")
    }

    private fun setAudioDeviceInternal(device: AudioDevice) {
        DebugLog.i(TAG, "setAudioDeviceInternal(device=$device)")
        RTCUtils.assertIsTrue(audioDevices.contains(device))

        when (device) {
            AudioDevice.SPEAKER_PHONE -> setSpeakerphoneOn(true)
            AudioDevice.EARPIECE -> setSpeakerphoneOn(false)
            AudioDevice.WIRED_HEADSET -> setSpeakerphoneOn(false)
            AudioDevice.BLUETOOTH -> setSpeakerphoneOn(false)
            else -> DebugLog.e(TAG, "Invalid audio device selection")
        }
        selectedAudioDevice = device
    }

    fun setDefaultAudioDevice(defaultDevice: AudioDevice) {
        ThreadUtils.checkIsOnMainThread()
        when (defaultDevice) {
            AudioDevice.SPEAKER_PHONE -> defaultAudioDevice = defaultDevice
            AudioDevice.EARPIECE -> {
                if (hasEarpiece()) {
                    defaultAudioDevice = defaultDevice
                } else {
                    defaultAudioDevice = AudioDevice.SPEAKER_PHONE
                }
            }
            else -> DebugLog.e(TAG, "Invalid default audio device selection")
        }
        DebugLog.d(TAG, "setDefaultAudioDevice(device=$defaultAudioDevice)")
        updateAudioDeviceState()
    }

    fun selectAudioDevice(device: AudioDevice) {
        ThreadUtils.checkIsOnMainThread()
        if (!audioDevices.contains(device)) {
            DebugLog.e(TAG, "Can not select $device from available $audioDevices")
        }
        userSelectedAudioDevice = device
        updateAudioDeviceState()
    }

    fun getAudioDevices(): HashSet<AudioDevice> {
        ThreadUtils.checkIsOnMainThread()
        return audioDevices
    }

    fun getSelectedAudioDevice(): AudioDevice {
        ThreadUtils.checkIsOnMainThread()
        return selectedAudioDevice
    }

    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        context.registerReceiver(receiver, filter)
    }

    private fun unregisterReceiver(receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    private fun setSpeakerphoneOn(on: Boolean) {
        val wasOn = audioManager!!.isSpeakerphoneOn
        if (wasOn == on) {
            return
        }
        audioManager!!.isSpeakerphoneOn = on
    }

    private fun setMicrophoneMute(on: Boolean) {
        val wasMuted = audioManager!!.isMicrophoneMute
        if (wasMuted == on) {
            return
        }
        audioManager!!.isMicrophoneMute = on
    }

    private fun hasEarpiece(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    }

    private fun hasWiredHeadset(): Boolean {
        if (Build.VERSION.SDK_INT < M) {
            return audioManager!!.isWiredHeadsetOn
        } else {
            @SuppressLint("WrongConstant")
            val devices = audioManager!!.getDevices(GET_DEVICES_ALL)
            for (device in devices) {
                val type = device.type
                if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    DebugLog.d(TAG, "hasWiredHeadset: found wired headset")
                    return true
                } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                    DebugLog.d(TAG, "hasWiredHeadset: found USB audio device")
                    return true
                }
            }
        }
        return false
    }

    fun updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread()
        DebugLog.d(TAG, "updateAudioDeviceState: " +
                "wired headset=$hasWiredHeadset, " +
                "BT state=${bluetoothManager.getState()}")
        DebugLog.d(TAG, "Device status: " +
                "available=$audioDevices, " +
                "selected=$selectedAudioDevice, " +
                "user selected=$userSelectedAudioDevice")

        if (bluetoothManager.getState() == HEADSET_AVAILABLE
            || bluetoothManager.getState() == HEADSET_UNAVAILABLE
            || bluetoothManager.getState() == SCO_DISCONNECTING) {
            bluetoothManager.updateDevice()
        }

        val newAudioDevices = HashSet<AudioDevice>()

        if (bluetoothManager.getState() == SCO_CONNECTED
            || bluetoothManager.getState() == SCO_CONNECTING
            || bluetoothManager.getState() == HEADSET_AVAILABLE) {
            newAudioDevices.add(AudioDevice.BLUETOOTH)
        }

        if (hasWiredHeadset) {
            newAudioDevices.add(AudioDevice.WIRED_HEADSET)
        } else {
            newAudioDevices.add(AudioDevice.SPEAKER_PHONE)
            if (hasEarpiece()) {
                newAudioDevices.add(AudioDevice.EARPIECE)
            }
        }

        var audioDeviceSetUpdated = audioDevices != newAudioDevices
        audioDevices = newAudioDevices
        if (bluetoothManager.getState() == HEADSET_UNAVAILABLE
            && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
            userSelectedAudioDevice = AudioDevice.NONE
        }
        if (hasWiredHeadset && userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE) {
            userSelectedAudioDevice = AudioDevice.WIRED_HEADSET
        }
        if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE
        }

        val needBluetoothAudioStart = bluetoothManager.getState() == HEADSET_AVAILABLE
                && (userSelectedAudioDevice == AudioDevice.NONE
                || userSelectedAudioDevice == AudioDevice.BLUETOOTH)

        val needBluetoothAudioStop = (bluetoothManager.getState() == SCO_CONNECTED
                || bluetoothManager.getState() == SCO_CONNECTING)
                && (userSelectedAudioDevice != AudioDevice.NONE
                && userSelectedAudioDevice != AudioDevice.BLUETOOTH)

        if (bluetoothManager.getState() == HEADSET_AVAILABLE
            || bluetoothManager.getState() == SCO_CONNECTING
            || bluetoothManager.getState() == SCO_CONNECTED) {
            DebugLog.d(TAG, "Need BT audio: start=$needBluetoothAudioStart, " +
                    "stop=$needBluetoothAudioStop, " +
                    "BT state=${bluetoothManager.getState()}")
        }

        if (needBluetoothAudioStop) {
            bluetoothManager.stopScoAudio()
            bluetoothManager.updateDevice()
        }

        if (needBluetoothAudioStart && !needBluetoothAudioStop) {
            if (!bluetoothManager.startScoAudio()) {
                audioDevices.remove(AudioDevice.BLUETOOTH)
                audioDeviceSetUpdated = true
            }
        }

        val newAudioDevice: AudioDevice = when {
            bluetoothManager.getState() == SCO_CONNECTED -> {
                AudioDevice.BLUETOOTH
            }
            hasWiredHeadset -> {
                AudioDevice.WIRED_HEADSET
            }
            else -> {
                defaultAudioDevice
            }
        }

        if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
            setAudioDeviceInternal(newAudioDevice)
            DebugLog.d(TAG, "New device status: " +
                    "available=${audioDevices}, " +
                    "selected=$newAudioDevice")
            if (audioManagerEvents != null) {
                audioManagerEvents!!.onAudioDeviceChanged(selectedAudioDevice, audioDevices)
            }
        }
        DebugLog.i(TAG, "updateAudioDeviceState done")
    }

    companion object {
        private const val TAG = "RTCAudioManager"
        private const val SPEAKERPHONE_AUTO = "auto"
        private const val SPEAKERPHONE_TRUE = "true"
        private const val SPEAKERPHONE_FALSE = "false"

        private const val STATE_UNPLUGGED = 0
        private const val STATE_PLUGGED = 1
        private const val HAS_NO_MIC = 0
        private const val HAS_MIC = 1

        @JvmStatic
        fun create(context: Context): RTCAudioManager {
            return RTCAudioManager(context)
        }
    }
}
