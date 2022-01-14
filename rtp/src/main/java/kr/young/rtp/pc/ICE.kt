package kr.young.rtp.pc

import kr.young.rtp.util.DefaultValues.Companion.STUN_PORT
import kr.young.rtp.util.DefaultValues.Companion.STUN_SERVER
import kr.young.rtp.util.DefaultValues.Companion.TURN_PASSWORD
import kr.young.rtp.util.DefaultValues.Companion.TURN_PORT
import kr.young.rtp.util.DefaultValues.Companion.TURN_SERVER
import kr.young.rtp.util.DefaultValues.Companion.TURN_USER_ID
import org.webrtc.PeerConnection

class ICE {
    internal fun getIceServers(stunAddress: String, stunPort: String, turnAddress: String, turnPort: String, userName:String, password: String): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        val stunServer = PeerConnection.IceServer.builder("stun:$stunAddress:$stunPort")
        val turnServer = PeerConnection.IceServer.builder("turn:$turnAddress:$turnPort")

        turnServer.setUsername(userName)
        turnServer.setPassword(password)
        iceServers.add(stunServer.createIceServer())
        iceServers.add(turnServer.createIceServer())
//        iceServers.add(
//            PeerConnection.IceServer.builder("stun:172.217.211.127:19302")
//                .setUsername("")
//                .setPassword("")
//                .createIceServer())
//        iceServers.add(
//            PeerConnection.IceServer.builder("turn:64.233.191.127:19305")
//                .setUsername("CPug0/kFEgb3ogfgyxgYzc/s6OMTIICjBQ")
//                .setPassword("qnYUc7hWdmqGkWEmNOcPMqNjVw8=")
//                .createIceServer())
        return iceServers
    }

    internal fun getIceServers(): List<PeerConnection.IceServer> {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        val stunServer = PeerConnection.IceServer.builder("stun:$STUN_SERVER:$STUN_PORT")
        val turnServer = PeerConnection.IceServer.builder("turn:$TURN_SERVER:$TURN_PORT")
        turnServer.setUsername(TURN_USER_ID)
        turnServer.setPassword(TURN_PASSWORD)
        iceServers.add(stunServer.createIceServer())
        iceServers.add(turnServer.createIceServer())
        return iceServers
    }
}