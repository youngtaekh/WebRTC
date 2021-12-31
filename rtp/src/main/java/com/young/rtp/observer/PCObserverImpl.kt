package com.young.rtp.observer

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import org.webrtc.StatsReport

class PCObserverImpl private constructor()
    : PCPublisher {

    private object Holder {
        val INSTANCE = PCObserverImpl()
    }

    private val pcObservers: MutableList<PCObserver> = mutableListOf()
    private val sdpObservers: MutableList<PCObserver.SDP> = mutableListOf()
    private val iceObservers: MutableList<PCObserver.ICE> = mutableListOf()

    override fun add(observer: PCObserver) {
        pcObservers.add(observer)
    }

    override fun add(observer: PCObserver.SDP) {
        sdpObservers.add(observer)
    }

    override fun add(observer: PCObserver.ICE) {
        iceObservers.add(observer)
    }

    override fun remove(observer: PCObserver) {
        pcObservers.remove(observer)
    }

    override fun remove(observer: PCObserver.SDP) {
        sdpObservers.remove(observer)
    }

    override fun remove(observer: PCObserver.ICE) {
        iceObservers.remove(observer)
    }

    override fun onLocalDescriptionObserver(sdp: SessionDescription?) {
        for (observer in sdpObservers) {
            observer.onLocalDescription(sdp)
        }
    }

    override fun onICECandidateObserver(candidate: IceCandidate?) {
        for (observer in iceObservers) {
            observer.onICECandidate(candidate)
        }
    }

    override fun onICECandidatesRemovedObserver(candidates: Array<out IceCandidate>?) {
        for (observer in iceObservers) {
            observer.onICECandidatesRemoved(candidates)
        }
    }

    override fun onICEConnectedObserver() {
        for (observer in iceObservers) {
            observer.onICEConnected()
        }
    }

    override fun onICEDisconnectedObserver() {
        for (observer in iceObservers) {
            observer.onICEDisconnected()
        }
    }

    override fun onPCConnectedObserver() {
        for (observer in pcObservers) {
            observer.onPCConnected()
        }
    }

    override fun onPCDisconnectedObserver() {
        for (observer in pcObservers) {
            observer.onPCDisconnected()
        }
    }

    override fun onPCFailedObserver() {
        for (observer in pcObservers) {
            observer.onPCFailed()
        }
    }

    override fun onPCClosedObserver() {
        for (observer in pcObservers) {
            observer.onPCClosed()
        }
    }

    override fun onPCStatsReadyObserver(reports: Array<StatsReport?>?) {
        for (observer in pcObservers) {
            observer.onPCStatsReady(reports)
        }
    }

    override fun onPCErrorObserver(description: String?) {
        for (observer in pcObservers) {
            observer.onPCError(description)
        }
    }

    override fun onMessageObserver(message: String) {
        for (observer in pcObservers) {
            observer.onMessage(message)
        }
    }

    companion object {
        private const val TAG = "PCObserverImpl"
        val instance: PCObserverImpl by lazy { Holder.INSTANCE }
    }
}