package com.young.rtp.util

class DefaultValues {
    companion object {
        const val isLoopback = false
        const val isVideo = false
        const val isScreen = false
        const val isDataChannel = false
        const val useCamera2 = true
        const val videoCodec = "VP8"
        const val audioCodec = "OPUS"
        //
        const val isHardwareCodec = true
        const val captureToTexture = true
        const val isFlexFEC = false
        const val isAudioProcessing = false
        const val isAECDump = false
        const val isAudioToFile = true
        const val useOpenSLES = false
        const val isBuiltInAEC = false
        const val isBuiltInAGC = false
        const val isBuiltInNS = false
        const val isGainControl = false
        const val isCaptureQualitySlider = false
        const val isDisplayHUD = true
        const val isTracing = false
        const val isRTCLogEvent = false

        //data channel options
        const val isOrdered = true
        const val isNegotiated = false
        const val maxRetransmitTimeMs = -1
        const val maxRetransmitPreference = -1
        const val dataId = -1
        const val subProtocol = ""

        const val STUN_SERVER = "stun.linphone.org"
        const val STUN_PORT = "3478"
        const val TURN_SERVER = "64.233.191.127"
        const val TURN_PORT = "19305"
        const val TURN_USER_ID = "CPug0/kFEgb3ogfgyxgYzc/s6OMTIICjBQ"
        const val TURN_PASSWORD = "qnYUc7hWdmqGkWEmNOcPMqNjVw8="

        @JvmStatic var videoWidth = 0
        @JvmStatic var videoHeight = 0
        @JvmStatic var videoFPS = 0
        @JvmStatic var videoStartBitrate = 0
        @JvmStatic var audioStartBitrate = 0
        @JvmStatic var saveRemoteVideoToFile: String? = null
    }
}