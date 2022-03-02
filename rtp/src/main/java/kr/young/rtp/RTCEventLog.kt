package kr.young.rtp

import android.os.ParcelFileDescriptor
import kr.young.common.DebugLog
import org.webrtc.PeerConnection
import java.io.File
import java.io.IOException

class RTCEventLog constructor(private val peerConnection: PeerConnection?) {
    private var state = RTCEventLogState.INACTIVE

    enum class RTCEventLogState {
        INACTIVE,
        STARTED,
        STOPPED,
    }

    init {
        if (peerConnection == null) {
            throw NullPointerException("PeerConnection is null")
        }
    }

    fun start(outputFile: File) {
        if (state == RTCEventLogState.STARTED) {
            DebugLog.e(TAG, "RTCEventLog has already started.")
            return
        }
        val fileDescriptor: ParcelFileDescriptor
        try {
            fileDescriptor = ParcelFileDescriptor.open(outputFile,
                ParcelFileDescriptor.MODE_READ_WRITE or
                        ParcelFileDescriptor.MODE_CREATE or
                        ParcelFileDescriptor.MODE_TRUNCATE)
        } catch (e: IOException) {
            DebugLog.e(TAG, "Failed to create a new file $e")
            return
        }

        val success = peerConnection!!.startRtcEventLog(fileDescriptor.detachFd(), OUTPUT_FILE_MAX_BYTES)
        if (!success) {
            DebugLog.e(TAG, "Failed to start RTC event log.")
            return
        }
        state = RTCEventLogState.STARTED
        DebugLog.i(TAG, "started")
    }

    fun stop() {
        if (state != RTCEventLogState.STARTED) {
            DebugLog.e(TAG, "RTCEventLog was not started")
            return
        }
        peerConnection!!.stopRtcEventLog()
        state = RTCEventLogState.STOPPED
        DebugLog.i(TAG, "stopped")
    }

    companion object {
        private const val TAG = "RTCEventLog"
        private const val OUTPUT_FILE_MAX_BYTES = 10_000_000
    }
}
