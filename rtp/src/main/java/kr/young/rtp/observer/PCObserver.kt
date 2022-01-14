package kr.young.rtp.observer

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.StatsReport

interface PCObserver {
    interface SDP {
        fun onLocalDescription(sdp: SessionDescription?)
    }

    interface ICE {
        fun onICECandidate(candidate: IceCandidate?)
        fun onICECandidatesRemoved(candidates: Array<out IceCandidate>?)
        fun onICEConnected()
        fun onICEDisconnected()
    }

    fun onPCConnected()
    fun onPCDisconnected()
    fun onPCFailed()
    fun onPCClosed()
    fun onPCStatsReady(reports: Array<StatsReport?>?)
    fun onPCError(description: String?)
    fun onMessage(message: String)
}