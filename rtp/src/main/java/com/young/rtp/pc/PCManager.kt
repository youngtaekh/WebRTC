package com.young.rtp.pc

import android.content.Context
import android.util.Log
import com.young.rtp.RecordedAudioToFileController
import com.young.rtp.observer.PCListener
import com.young.rtp.observer.PCObserverImpl
import com.young.rtp.observer.SDPListener
import com.young.rtp.util.DefaultValues
import com.young.rtp.util.DefaultValues.Companion.videoFPS
import com.young.rtp.util.DefaultValues.Companion.videoHeight
import com.young.rtp.util.DefaultValues.Companion.videoWidth
import com.young.rtp.util.RTPLog
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ExecutorService

class PCManager(
    appContext: Context,
    private val pcParameters: PCParameters
) {
    private var pcObserverImpl: PCObserverImpl? = null
    private var sdpListener: SDPListener? = null

    var factory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
    private var pcListener: PCListener? = null
    private var sdpMediaConstraints: MediaConstraints? = null
    private var dataChannel: DataChannel? = null
    private var saveRecordedAudioToFile: RecordedAudioToFileController? = null

    /** Options for audio/video */
    private var isFlexFEC: Boolean = DefaultValues.isFlexFEC
    private var isBuiltInAGC: Boolean = DefaultValues.isBuiltInAGC
    private var isLoopback: Boolean = DefaultValues.isLoopback
    private var isHardwareCodec: Boolean = DefaultValues.isHardwareCodec
    private var isAudioToFile = DefaultValues.isAudioToFile
    private var useOpenSLES = DefaultValues.useOpenSLES

    /** timer for stat period */
    private var statTimer: Timer? = null

    private var timerTask: TimerTask = object : TimerTask() {
        override fun run() {
            peerConnection?.getStats(statsObserver, null)
        }
    }

    private var statsObserver = StatsObserver {
        pcObserverImpl?.onPCStatsReadyObserver(it)
    }

    init {
        pcObserverImpl = PCObserverImpl.instance
        pcListener = PCListener(pcObserverImpl!!)
        statTimer = Timer()

        val fieldTrials = getFieldTrials()
        val options = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setFieldTrials(fieldTrials)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun getFieldTrials(): String? {
        var fieldTrials = ""
        if (isFlexFEC) {
            fieldTrials += VIDEO_FLEX_FEC_FIELD_TRIAL
            Log.d(TAG, "Enable FlexFEC field trial.")
        }
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELD_TRIAL
        if (isBuiltInAGC) {
            fieldTrials += DISABLE_WEBRTC_AGC_FIELD_TRIAL
            Log.d(TAG, "Disable WebRTC AGC field trial.")
        }
        return fieldTrials
    }

    /**
     * Create encoder/decoder
     * Set event audio record
     */
    fun createPeerConnectionFactory(
        appContext: Context,
        eglBase: EglBase,
        executor: ExecutorService,
        isAudioToFile: Boolean = true
    ): PeerConnectionFactory? {
        if (factory != null) {
            RTPLog.e(TAG, "peerConnectionFactory already created")
            return null
        }
        if (isAudioToFile) {
            if (!useOpenSLES) {
                saveRecordedAudioToFile =
                    RecordedAudioToFileController(executor)
            }
        }

        val audioDeviceModule = AudioMedia()
            .createJavaAudioDevice(appContext, saveRecordedAudioToFile)

        val options: PeerConnectionFactory.Options = PeerConnectionFactory.Options()
        if (isLoopback) {
            options.networkIgnoreMask = 0
        }

        val encoderFactory: VideoEncoderFactory?
        val decoderFactory: VideoDecoderFactory?

        if (isHardwareCodec) {
            encoderFactory = DefaultVideoEncoderFactory(
                eglBase.eglBaseContext,
                true,
                false)
            decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        } else {
            encoderFactory = SoftwareVideoEncoderFactory()
            decoderFactory = SoftwareVideoDecoderFactory()
        }

        this.factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        audioDeviceModule?.release()

        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO)

        return factory
    }

    /**
     * create peer connection
     * set media constraints
     * set RTC config
     * set peer connection event listener
     */
    fun createPeerConnection(isOffer: Boolean, iceServers: List<PeerConnection.IceServer>): PeerConnection? {
        createMediaConstraintsInternal()
        this.peerConnection = factory!!.createPeerConnection(getRTCConfig(iceServers), pcListener)
        this.sdpListener = SDPListener(
            isOffer,
            peerConnection!!,
            pcObserverImpl!!
        )

        if (pcParameters.isDataChannel) {
            val init = DataChannel.Init()
            init.ordered = DefaultValues.isOrdered
            init.negotiated = DefaultValues.isNegotiated
            init.maxRetransmits = DefaultValues.maxRetransmitPreference
            init.maxRetransmitTimeMs = DefaultValues.maxRetransmitTimeMs
            init.id = DefaultValues.dataId
            init.protocol = DefaultValues.subProtocol
            dataChannel = peerConnection!!.createDataChannel(DATA_CHANNEL_LABEL, init)
        }
        return peerConnection
    }

    fun startRecording() {
        RTPLog.i(TAG, "startRecording(${isAudioToFile && useOpenSLES})")
        saveRecordedAudioToFile?.start()
    }

    fun release() {
        statTimer?.cancel()
        RTPLog.d(TAG, "Dispose dataChannel.")
        dataChannel?.dispose()
        dataChannel = null

        RTPLog.d(TAG, "Dispose peerConnection.")
        peerConnection?.dispose()
        peerConnection = null

        saveRecordedAudioToFile?.stop()
        saveRecordedAudioToFile = null

        Log.d(TAG, "Closing peer connection factory.")
        factory?.dispose()
        factory = null

        Log.d(TAG, "Closing peer connection done.")
        pcObserverImpl!!.onPCClosedObserver()
        PeerConnectionFactory.stopInternalTracingCapture()
        PeerConnectionFactory.shutdownInternalTracer()
    }

    /**
     * Set video width/height/fps
     * receive audio/video or not
     */
    private fun createMediaConstraintsInternal() {
        if (pcParameters.isVideo) {
            if (videoWidth == 0 || videoHeight == 0) {
                videoWidth =
                    HD_VIDEO_WIDTH
                videoHeight =
                    HD_VIDEO_HEIGHT
            }

            if (videoFPS == 0) {
                videoFPS = 30
            }
            RTPLog.d(TAG, "Capturing format: $videoWidth x $videoHeight @ $videoFPS")
        }

        // Create SDP constraints.
        sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", pcParameters.isAudio.toString())
        )
        sdpMediaConstraints!!.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", pcParameters.isVideo.toString())
        )
    }

    /**
     * tcpCandidatePolicy - Connect RTP using TCP(common UDP)
     * bundlePolicy - ICE gathering for tracks(audio, video, data)
     * rtcpMuxPolicy - RTP and multiplex RTCP
     * continualGatheringPolicy - ICE gathering policy(once, CONTINUALLY)
     * keyType - For SRTP
     * enableDtlsSrtp - true(DTLS-SRTP), false(SDES)
     * sdpSemantics - SDP format
     */
    private fun getRTCConfig(iceServers: List<PeerConnection.IceServer>): PeerConnection.RTCConfiguration {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy =
            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        rtcConfig.enableDtlsSrtp = !isLoopback
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        return rtcConfig
    }

    /** Add audio/video track to peer connection */
    fun addTrack(track: MediaStreamTrack, label: List<String>) {
        peerConnection!!.addTrack(track, label)
    }

    fun createOffer() {
        if (peerConnection != null) {
            RTPLog.d(TAG, "PC Create OFFER")
            peerConnection!!.createOffer(sdpListener, sdpMediaConstraints)
        }
    }

    fun createAnswer() {
        if (peerConnection != null) {
            RTPLog.d(TAG, "PC create ANSWER")
            peerConnection!!.createAnswer(sdpListener, sdpMediaConstraints)
        }
    }

    fun addRemoteIceCandidate(candidate: IceCandidate?) {
        RTPLog.d(TAG, "addRemoteIceCandidate")
        if (peerConnection != null) {
//            if (sdpListener != null) {
//                RTPLog.d(TAG, "sdpListener!!.addCandidate")
//                sdpListener!!.addCandidate(candidate!!)
//            } else {
            RTPLog.d(TAG, "peerConnection!!.addIceCandidate")
            peerConnection!!.addIceCandidate(candidate)
//            }
        }
    }

    /** Set remote description to peer connection */
    fun setRemoteDescription(sdp: SessionDescription) {
        if (peerConnection != null) {
            RTPLog.d(TAG, "Set remote SDP.")
            peerConnection!!.setRemoteDescription(sdpListener, sdp)
        }
    }

    fun sendData(message: String) {
        if (dataChannel == null) return
        RTPLog.i(TAG, "sendData($message)")
        val byteBuffer = ByteBuffer.allocate(100)
        byteBuffer.put(message.toByteArray())
        byteBuffer.flip()
        val buffer = DataChannel.Buffer(byteBuffer, false)
        dataChannel!!.send(buffer)
    }

    fun enableStatsEvents(periodMs: Long) {
        statTimer?.schedule(timerTask, 0, periodMs)
    }

    companion object {
        private const val TAG = "PCManager"
        private const val HD_VIDEO_WIDTH = 1280
        private const val HD_VIDEO_HEIGHT = 720
        private const val VIDEO_FLEX_FEC_FIELD_TRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/"
        private const val VIDEO_VP8_INTEL_HW_ENCODER_FIELD_TRIAL =
            "WebRTC-IntelVP8/Enabled/"
        private const val DISABLE_WEBRTC_AGC_FIELD_TRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/"
        private const val DATA_CHANNEL_LABEL = "message data"
    }
}