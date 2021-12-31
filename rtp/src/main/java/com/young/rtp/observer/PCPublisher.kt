package com.young.rtp.observer

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.StatsReport

interface PCPublisher {
    fun add(observer: PCObserver)
    fun remove(observer: PCObserver)
    fun add(observer: PCObserver.SDP)
    fun remove(observer: PCObserver.SDP)
    fun add(observer: PCObserver.ICE)
    fun remove(observer: PCObserver.ICE)

    fun onLocalDescriptionObserver(sdp: SessionDescription?)
    fun onICECandidateObserver(candidate: IceCandidate?)
    fun onICECandidatesRemovedObserver(candidates: Array<out IceCandidate>?)
    fun onICEConnectedObserver()
    fun onICEDisconnectedObserver()
    fun onPCConnectedObserver()
    fun onPCDisconnectedObserver()
    fun onPCFailedObserver()
    fun onPCClosedObserver()
    fun onPCStatsReadyObserver(reports: Array<StatsReport?>?)
    fun onPCErrorObserver(description: String?)
    fun onMessageObserver(message: String)
}