package com.young.rtp

import android.content.Context
import android.content.Intent
import com.young.rtp.observer.PCObserver
import com.young.rtp.observer.PCObserverImpl
import com.young.rtp.pc.*
import com.young.rtp.util.DefaultValues
import com.young.rtp.util.DefaultValues.Companion.isAudioProcessing
import com.young.rtp.util.DefaultValues.Companion.saveRemoteVideoToFile
import com.young.rtp.util.DefaultValues.Companion.videoHeight
import com.young.rtp.util.DefaultValues.Companion.videoWidth
import com.young.rtp.util.PCState
import com.young.rtp.util.RTCAudioManager
import com.young.rtp.util.RTPLog
import org.webrtc.*
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * WebRTC init order
 * create peer connection factory
 * create peer connection
 * create video/audio track
 * set remote/local description
 */
class RTPManager: PCObserver, PCObserver.SDP, PCObserver.ICE {
    /** set SurfaceViewRenderer */
    private class ProxyVideoSink : VideoSink {
        private var target: VideoSink? = null

        @Synchronized
        override fun onFrame(frame: VideoFrame) {
            if (target == null) {
                return
            }
            target!!.onFrame(frame)
        }

        @Synchronized
        fun setTarget(target: VideoSink?) {
            this.target = target
        }
    }

    private var executor: ExecutorService? = null

    /** Options related RTP(PeerConnection) */
    private var isOffer = false
    private var isAudio = true
    private var isVideo = DefaultValues.isVideo
    private var isScreen = DefaultValues.isScreen
    private var isDataChannel = DefaultValues.isDataChannel
    private var enableStat = true
    private var recordAudio = true

    private val remoteRenderer = ProxyVideoSink()
    private var remoteSinks: ArrayList<VideoSink>? = null
    private var localVideoSink: ProxyVideoSink? = null

    private var eglBase: EglBase? = null

    private var pcManager: PCManager? = null
    private var pcParameters: PCParameters? = null
    private var pcState: PCState? = null
    private var iceServers: List<PeerConnection.IceServer>? = null

    private var audioMedia: AudioMedia? = null
    private var videoMedia: VideoMedia? = null

    private var isSwappedFeeds = false
    private var fullRenderer: SurfaceViewRenderer? = null
    private var pipRenderer: SurfaceViewRenderer? = null
    private var videoFileRenderer: VideoFileRenderer? = null

    private var rtcAudioManager: RTCAudioManager? = null

    private var statParser: StatParser? = null

    /**
     * Set options for RTP
     * isScreen - screen share
     * isDataChannel - send/receive data
     * enableStat - connection status
     */
    fun init(context: Context,
             isAudio: Boolean = this.isAudio,
             isVideo: Boolean = this.isVideo,
             isDataChannel: Boolean = this.isDataChannel,
             enableStat: Boolean = this.enableStat,
             recordAudio: Boolean = this.recordAudio) {
        RTPLog.i(TAG, "init")
        this.remoteSinks = arrayListOf()
        this.localVideoSink = ProxyVideoSink()
        this.statParser = StatParser()
        this.pcState = PCState()

        this.isAudio = isAudio
        this.isVideo = isVideo
        this.isDataChannel = isDataChannel
        this.enableStat = enableStat
        this.recordAudio = recordAudio
        this.executor = Executors.newSingleThreadExecutor()

        PCObserverImpl.instance.add(this as PCObserver)
        PCObserverImpl.instance.add(this as PCObserver.SDP)
        PCObserverImpl.instance.add(this as PCObserver.ICE)

        this.eglBase = EglBase.create()
        this.remoteSinks!!.add(remoteRenderer)

        if (saveRemoteVideoToFile != null) {
            try {
                this.videoFileRenderer = VideoFileRenderer(saveRemoteVideoToFile, videoWidth,
                    videoHeight, eglBase!!.eglBaseContext)
                this.remoteSinks!!.add(videoFileRenderer!!)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        audioManagerStart(context)

        setPCParameters()
        createPeerConnectionFactory(context)
    }

    /** Release resources */
    fun release() {
        RTPLog.i(TAG, "release()")

        PCObserverImpl.instance.remove(this as PCObserver)
        PCObserverImpl.instance.remove(this as PCObserver.SDP)
        PCObserverImpl.instance.remove(this as PCObserver.ICE)

        this.remoteRenderer.setTarget(null)
        this.localVideoSink?.setTarget(null)
        this.pipRenderer?.release()
        this.pipRenderer = null
        this.fullRenderer?.release()
        this.fullRenderer = null

        this.executor!!.execute {
            RTPLog.d(TAG, "Release audio.")
            this.audioMedia?.release()
            RTPLog.d(TAG, "Release video.")
            this.videoMedia?.release()

            this.localVideoSink = null
            this.remoteSinks = null
            this.pcManager?.release()
            this.pcManager = null
            this.eglBase?.release()
            this.eglBase = null
        }

        this.rtcAudioManager?.stop()
        this.rtcAudioManager = null
    }

    /**
     * Create PeerConnection
     * Create Offer or Answer
     */
    fun startRTP(
        context: Context,
        data: Intent?,
        isOffer: Boolean,
        remoteSdp: SessionDescription?
    ) {
        RTPLog.i(
            TAG, "startRTP(context, isOffer $isOffer, " +
                    "remoteSdp is null ${remoteSdp==null})")
        this.isOffer = isOffer
        createPeerConnection(context, data)

        if (isOffer) {
            createOffer()
        } else if (remoteSdp!=null) {
            setRemoteDescription(remoteSdp)
            createAnswer()
        }
    }

    /** Send data through Data Channel */
    fun sendData(message: String) {
        this.pcManager!!.sendData(message)
    }

    /** Set TURN/STUN Server for ice candidates */
    fun setIceServers(iceServers: List<PeerConnection.IceServer>) {
        this.iceServers = iceServers
    }

    fun setIceServers(stunAddress: String, stunPort:String, turnAddress: String, turnPort: String, userName:String, password: String) {
        this.iceServers = ICE().getIceServers(stunAddress, stunPort, turnAddress, turnPort, userName, password)
    }

    /** Manage audio setting(select speaker, audio focus gain/loss) */
    private fun audioManagerStart(context: Context) {
        RTPLog.i(TAG, "audioManagerStart()")
        rtcAudioManager = RTCAudioManager.create(context)

        rtcAudioManager!!.start(object: RTCAudioManager.AudioManagerEvents {
            override fun onAudioDeviceChanged(
                selectedAudioDevice: RTCAudioManager.AudioDevice,
                availableAudioDevices: Set<RTCAudioManager.AudioDevice>
            ) {
                RTPLog.d(
                    TAG,
                    "onAudioManagerDevicesChanged: $availableAudioDevices ," +
                            "selected: $selectedAudioDevice")
            }
        })
    }

    private fun updateEncoderStatistics(reports: Array<StatsReport?>) {
        RTPLog.i(TAG, "updateEncoderStatistics()")
        statParser!!.updateEncoderStatistics(reports, isVideo)
    }

    private fun setPCParameters() {
        RTPLog.i(TAG, "setPCParameters()")
        pcParameters = PCParameters(
            isAudio, isVideo, isScreen, isDataChannel
        )
    }

    private fun createPeerConnectionFactory(context: Context) {
        RTPLog.i(TAG, "createPeerConnectionFactory()")

        executor!!.execute {
            pcManager =
                PCManager(context, pcParameters!!)
            pcManager!!.createPeerConnectionFactory(context, eglBase!!, executor!!, recordAudio)
        }
    }

    /**
     * Create audio/video track(mic, camera, screen)
     * PeerConnection is object to manager SDP options (ex. RTC config, media constraints)
     */
    private fun createPeerConnection(context: Context,
                                     data: Intent?) {
        RTPLog.i(TAG, "createPeerConnection()")
        RTPLog.i(TAG, "createPeerConnection - $iceServers")
        this.isScreen = data!=null

        executor!!.execute {

            pcManager!!.createPeerConnection(isOffer, iceServers!!)

            val mediaStreamLabels = listOf(MEDIA_STREAM_LABEL)
            if (isAudio) {
                audioMedia = AudioMedia()
                val localAudioTrack = audioMedia!!.createAudioTrack(pcManager!!.factory!!, isAudioProcessing)
                if (localAudioTrack != null) {
                    pcManager!!.addTrack(localAudioTrack, mediaStreamLabels)
                }
            }
            if (isVideo) {
                videoMedia = VideoMedia()
                videoMedia!!.createVideoCapturer(context, isScreen, data)
                val localVideoTrack = videoMedia!!.createVideoTrack(
                    context,
                    localVideoSink!!,
                    pcManager!!.factory!!,
                    eglBase!!
                )
                if (localVideoTrack != null) {
                    pcManager!!.addTrack(localVideoTrack, mediaStreamLabels)
                }
                videoMedia!!.getRemoteVideoTrack(pcManager!!.peerConnection!!, remoteSinks!!)
            }

            pcManager!!.startRecording()
            if (isOffer) {
                pcState!!.setPCState(PCState.State.OFFER_PENDING)
            } else {
                pcState!!.setPCState(PCState.State.ANSWER_PENDING)
            }
        }
    }

    /** Create "Caller" SDP and Gather ICE candidates(host, relay, server reflexive) */
    fun createOffer() {
        executor!!.execute {
            RTPLog.i(TAG, "createOffer()")
            pcState!!.setPCState(PCState.State.CREATE_OFFER)
            pcManager!!.createOffer()
        }
    }

    /** Create "Callee" SDP and Gather ICE candidates(host, relay, server reflexive) */
    fun createAnswer() {
        executor!!.execute {
            RTPLog.i(TAG, "createAnswer()")
            pcState!!.setPCState(PCState.State.CREATE_ANSWER)
            pcManager!!.createAnswer()
        }
    }

    fun setRemoteDescription(remoteSdp: SessionDescription) {
        executor!!.execute {
            RTPLog.i(TAG, "setRemoteDescription()")
            if (isOffer) {
                pcState!!.setPCState(PCState.State.CONNECT_PENDING)
            } else {
                pcState!!.setPCState(PCState.State.SET_REMOTE_OFFER)
            }
            pcManager!!.setRemoteDescription(remoteSdp)
        }
    }

    fun addRemoteIceCandidate(iceCandidate: IceCandidate) {
        executor!!.execute {
            RTPLog.i(TAG, "addRemoteIceCandidate()")
            pcManager!!.addRemoteIceCandidate(iceCandidate)
        }
    }

    private fun enableStatsEvents() {
        if (enableStat) {
            pcManager!!.enableStatsEvents(STAT_CALLBACK_PERIOD)
        }
    }

    fun initVideoView(fullRenderer: SurfaceViewRenderer,
                      pipRenderer: SurfaceViewRenderer
    ) {
        this.fullRenderer = fullRenderer
        this.pipRenderer = pipRenderer

        // Create video renderers.
        this.pipRenderer?.init(eglBase!!.eglBaseContext, null)
        this.pipRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        this.fullRenderer?.init(eglBase!!.eglBaseContext, null)
        this.fullRenderer?.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        this.pipRenderer?.setZOrderMediaOverlay(true)
        this.pipRenderer?.setEnableHardwareScaler(true)
        this.fullRenderer?.setEnableHardwareScaler(false)
        setSwappedFeeds(true)
    }

    fun setSwappedFeeds(isSwappedFeeds: Boolean) {
        RTPLog.d(TAG, "setSwappedFeeds: $isSwappedFeeds")
        this.isSwappedFeeds = isSwappedFeeds
        localVideoSink?.setTarget(if (isSwappedFeeds) fullRenderer else pipRenderer)
        remoteRenderer.setTarget(if (isSwappedFeeds) pipRenderer else fullRenderer)
        fullRenderer?.setMirror(isSwappedFeeds)
        pipRenderer?.setMirror(!isSwappedFeeds)
    }

    fun switchCamera() {
        executor!!.execute {
            RTPLog.i(TAG, "switchCamera()")
            videoMedia?.switchCamera()
        }
    }

    fun captureFormatChange(width: Int, height: Int, frameRate: Int) {
        executor?.execute {
            RTPLog.i(TAG, "captureFormatChange(width $width, height $height, frameRate $frameRate)")
            videoMedia?.changeCaptureFormat(width, height, frameRate)
        }
    }

    fun setScaleType(type: RendererCommon.ScalingType) {
        RTPLog.i(TAG, "setScaleType(type $type)")
        fullRenderer?.setScalingType(type)
    }

    /** Mute on/off */
    fun setAudioEnable(enable: Boolean) {
        executor!!.execute {
            RTPLog.i(TAG, "setAudioEnable(enable $enable)")
            audioMedia?.setEnable(enable)
        }
    }

    fun setVideoEnable(enable: Boolean) {
        executor!!.execute {
            RTPLog.i(TAG, "setVideoEnable(enable $enable)")
            videoMedia?.setEnable(enable)
        }
    }

    fun startVideoSource() {
        executor!!.execute {
            if (!isScreen) {
                videoMedia?.startVideoSource()
            }
        }
    }

    fun stopVideoSource() {
        executor!!.execute {
            if (!isScreen) {
                videoMedia?.stopVideoSource()
            }
        }
    }

    private object Holder {
        val INSTANCE = RTPManager()
    }

    companion object {
        private const val TAG = "RTPManager"
        val instance: RTPManager by lazy { Holder.INSTANCE }
        private const val STAT_CALLBACK_PERIOD = 1000L
        private const val MEDIA_STREAM_LABEL = "ARDAMS"
    }

    /** Event local SDP created */
    override fun onLocalDescription(sdp: SessionDescription?) {
        RTPLog.i(TAG, "onLocalDescription()")
        if (isOffer) {
            pcState!!.setPCState(PCState.State.SET_LOCAL_OFFER)
        } else {
            pcState!!.setPCState(PCState.State.CONNECT_PENDING)
        }
    }

    /** Event ICE candidate gather */
    override fun onICECandidate(candidate: IceCandidate?) {
        RTPLog.i(TAG, "onIceCandidate()")
        RTPLog.i(TAG, "candidate - $candidate")
//        addRemoteIceCandidate(candidate!!)
    }

    override fun onICECandidatesRemoved(candidates: Array<out IceCandidate>?) {
        RTPLog.i(TAG, "onIceCandidatesRemoved()")
    }

    override fun onICEConnected() {
        RTPLog.i(TAG, "onIceConnected()")
    }

    override fun onICEDisconnected() {
        RTPLog.i(TAG, "onIceDisconnected()")
    }

    override fun onPCConnected() {
        RTPLog.i(TAG, "onPeerConnectionConnected()")
        enableStatsEvents()
        if (isVideo) {
            setSwappedFeeds(false)
        }
    }

    override fun onPCDisconnected() {
        RTPLog.i(TAG, "onPeerConnectionDisconnected()")
    }

    override fun onPCFailed() {
        RTPLog.i(TAG, "onPeerConnectionFailed()")
    }

    override fun onPCClosed() {
        RTPLog.i(TAG, "onPeerConnectionClosed()")
    }

    override fun onPCStatsReady(reports: Array<StatsReport?>?) {
        RTPLog.i(TAG, "onPeerConnectionStatsReady()")
        if (reports != null) {
            updateEncoderStatistics(reports)
        }
    }

    override fun onPCError(description: String?) {
        RTPLog.i(TAG, "onPeerConnectionError()")
    }

    /** Event receive data */
    override fun onMessage(message: String) {
        RTPLog.i(TAG, "onMessage()")
    }
}