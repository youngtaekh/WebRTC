package kr.young.rtp.observer

import kr.young.common.DebugLog
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SDPListener(
    private var isOffer: Boolean,
    private val peerConnection: PeerConnection,
    private val pcObserverImpl: PCObserverImpl
): SdpObserver {

    private var executor: ExecutorService? = null
    private var localSDP: SessionDescription? = null
    private var queueRemoteCandidates: ArrayList<IceCandidate> = arrayListOf()

    init {
        this.executor = Executors.newSingleThreadExecutor()
    }

    fun addCandidate(candidate: IceCandidate) {
        this.executor!!.execute {
            this.queueRemoteCandidates.add(candidate)
        }
        this.queueRemoteCandidates
    }

    private fun drainCandidates() {
        DebugLog.i(TAG, "drainCandidates")
        DebugLog.i(TAG, "Add ${queueRemoteCandidates.size} remote candidates")
        for (candidate in queueRemoteCandidates) {
            peerConnection.addIceCandidate(candidate)
        }
    }

    override fun onCreateSuccess(p0: SessionDescription?) {
        val description = p0!!.description
        val sdp = SessionDescription(p0.type, description)
        localSDP = sdp
        executor!!.execute {
            DebugLog.i(TAG, "Set local SDP from ${sdp.type}")
            peerConnection.setLocalDescription(this, sdp)
        }
    }

    override fun onSetSuccess() {
        this.executor!!.execute {
            if (isOffer) {
                if (peerConnection.remoteDescription == null) {
                    DebugLog.i(TAG, "Local SDP set successfully")
                    pcObserverImpl.onLocalDescriptionObserver(localSDP)
                } else {
                    DebugLog.i(TAG, "Remote SDP set successfully")
                    drainCandidates()
                }
            } else {
                if (peerConnection.localDescription != null) {
                    DebugLog.i(TAG, "Local SDP set successfully")
                    pcObserverImpl.onLocalDescriptionObserver(localSDP)
                    drainCandidates()
                }
            }
        }
    }

    override fun onCreateFailure(p0: String?) {
        DebugLog.e(TAG, "onCreateFailure($p0)")
    }

    override fun onSetFailure(p0: String?) {
        DebugLog.e(TAG, "onSetFailure($p0)")
    }

    companion object {
        private const val TAG = "SDPListener"
    }

}
