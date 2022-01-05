package com.young.rtp.util

import java.lang.Exception

class PCState {
    enum class State {
        IDLE,

        OFFER_PENDING,
        CREATE_OFFER,
        SET_LOCAL_OFFER,

        ANSWER_PENDING,
        SET_REMOTE_OFFER,
        CREATE_ANSWER,

        CONNECT_PENDING
    }

    class WrongPCStateException(message: String): Exception(message)

    private var state = State.IDLE

    fun setPCState(state: State) {
        this.state = state
    }

    private fun check(state: State, nextState: State): Boolean {
        return when (state) {
            State.IDLE -> nextState == State.OFFER_PENDING ||
                    nextState == State.ANSWER_PENDING ||
                    nextState == State.IDLE
            State.OFFER_PENDING -> nextState == State.CREATE_OFFER
            State.CREATE_OFFER -> nextState == State.SET_LOCAL_OFFER
            State.SET_LOCAL_OFFER -> nextState == State.CONNECT_PENDING

            State.ANSWER_PENDING -> nextState == State.SET_REMOTE_OFFER
            State.SET_REMOTE_OFFER -> nextState == State.CREATE_ANSWER
            State.CREATE_ANSWER -> nextState == State.CONNECT_PENDING
            State.CONNECT_PENDING -> nextState == State.IDLE
        }
    }
    /*
    createPeerConnection
    createOffer
    setLocalDescription
    setRemoteDescription

    createPeerConnection
    setRemoteDescription
    createAnswer
    setLocalDescription
     */
}