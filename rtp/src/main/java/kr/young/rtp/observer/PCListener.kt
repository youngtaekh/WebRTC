package kr.young.rtp.observer

import kr.young.rtp.observer.PCObserverImpl.Companion.instance
import kr.young.util.DebugLog
import org.webrtc.*
import java.nio.charset.Charset
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PCListener(private val pcObserverImpl: PCObserverImpl)
    : PeerConnection.Observer {

    private var executor: ExecutorService? = null

    init {
        this.executor = Executors.newSingleThreadExecutor()
    }

    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        DebugLog.i(TAG, "onSignalingChange($p0)")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        DebugLog.i(TAG, "onIceConnectionChange($p0)")
        this.executor!!.execute {
            when (p0) {
                PeerConnection.IceConnectionState.CONNECTED -> {
                    DebugLog.i(TAG, "ICE connection CONNECTED")
                    pcObserverImpl.onICEConnectedObserver()
                }
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    DebugLog.i(TAG, "ICE connection DISCONNECTED")
                    pcObserverImpl.onICEDisconnectedObserver()
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    DebugLog.e(TAG, "ICE connection FAILED")
                }
                PeerConnection.IceConnectionState.NEW -> {
                    DebugLog.i(TAG, "ICE connection NEW")
                }
                PeerConnection.IceConnectionState.CHECKING -> {
                    DebugLog.i(TAG, "ICE connection CHECKING")
                }
                PeerConnection.IceConnectionState.COMPLETED -> {
                    DebugLog.i(TAG, "ICE connection COMPLETED")
                }
                PeerConnection.IceConnectionState.CLOSED -> {
                    DebugLog.i(TAG, "ICE connection CLOSED")
                }
                else -> {
                    DebugLog.e(TAG, "ICE connection UNKNOWN")
                }
            }
        }
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {
        DebugLog.i(TAG, "onIceConnectionReceivingChange($p0)")
    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        DebugLog.i(TAG, "onIceGatheringChange($p0)")
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        DebugLog.i(TAG, "onIceCandidate($p0)")
        this.executor!!.execute { pcObserverImpl.onICECandidateObserver(p0) }
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
        DebugLog.i(TAG, "onIceCandidatesRemoved($p0)")
        this.executor!!.execute { pcObserverImpl.onICECandidatesRemovedObserver(p0) }
    }

    override fun onAddStream(p0: MediaStream?) {
        DebugLog.i(TAG, "onAddStream($p0)")
    }

    override fun onRemoveStream(p0: MediaStream?) {
        DebugLog.i(TAG, "onRemoveStream($p0)")
    }

    override fun onDataChannel(dc: DataChannel?) {
        dc!!.registerObserver(object: DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {
                DebugLog.i(TAG, "onDataChannel.onBufferedAmountChange" +
                        "label: ${dc.label()}, state: ${dc.state()}")
            }

            override fun onStateChange() {
                DebugLog.i(TAG, "onDataChannel.onStateChange" +
                        "label: ${dc.label()}, state: ${dc.state()}")
            }

            override fun onMessage(p0: DataChannel.Buffer?) {
                DebugLog.i(TAG, "onDataChannel.onMessage" +
                        "label: ${dc.label()}, state: ${dc.state()}")
                if (p0 == null || p0.binary) {
                    DebugLog.i(TAG, "Received binary msg over $dc")
                    return
                }
                val data = p0.data
                val bytes = ByteArray(data.capacity())
                data[bytes]
                val strData = String(bytes, Charset.forName("UTF-8"))
                DebugLog.i(TAG, "Got msg: $strData over $dc")
                instance.onMessageObserver(strData)
            }
        })
    }

    override fun onRenegotiationNeeded() {
        DebugLog.i(TAG, "onRenegotiationNeeded()")
    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        DebugLog.i(TAG, "onAddTrack($p0, $p1)")
    }

    companion object {
        private const val TAG = "PCListener"
    }
}
