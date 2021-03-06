package kr.young.rtp

import kr.young.common.DebugLog
import kr.young.rtp.pc.VideoMedia
import kr.young.rtp.util.DefaultValues
import org.webrtc.StatsReport

class StatParser {
    private var displayHud = false
    private var isRunning = false

    private var prevByteSent: Long = 0
    private var prevByteReceived: Long = 0

    private var test = true

    init {
        displayHud = DefaultValues.isDisplayHUD
        isRunning = true
    }

    fun release() {
        isRunning = false
    }

    private fun getReportMap(report: StatsReport): Map<String, String> {
        val reportMap: MutableMap<String, String> =
            HashMap()
        for (value in report.values) {
            reportMap[value.name] = value.value
        }
        return reportMap
    }

    fun updateEncoderStatistics(reports: Array<StatsReport?>, isVideo: Boolean) {
        if (test) {
            for ((index, report) in reports.withIndex()) {
                DebugLog.i(TAG, "index $index")
                DebugLog.i(TAG, "report.id ${report?.id}")
                DebugLog.i(TAG, "report.type ${report?.type}")
                DebugLog.i(TAG, "report.timestamp ${report?.timestamp}")
                DebugLog.i(TAG, "report.values====================")
                for ((index2, value) in report!!.values.withIndex()) {
                    DebugLog.i(TAG, "\tindex2 $index2")
                    DebugLog.i(TAG, "\tvalue.name ${value.name}")
                    DebugLog.i(TAG, "\tvalue.value ${value.value}")
                    DebugLog.i(TAG, "=============================")
                }
            }
            return
        }
        if (!isRunning || !displayHud) {
            return
        }
        val encoderStat = StringBuilder(128)
        val bweStat = StringBuilder()
        val connectionStat = StringBuilder()
        val videoSendStat = StringBuilder()
        val videoRecvStat = StringBuilder()
        var fps: String? = null
        var targetBitrate: String? = null
        var actualBitrate: String? = null
        for (report in reports) {
            if (report == null)
                continue
            if (report.type == "ssrc" && report.id.contains("ssrc") && report.id.contains("send")) {
                // Send video statistics.
                val reportMap =
                    getReportMap(report)
                val trackId = reportMap["googTrackId"]
                if (trackId != null && trackId.contains(VideoMedia.VIDEO_TRACK_ID)) {
                    fps = reportMap["googFrameRateSent"]
                    videoSendStat.append(report.id).append("\n")
                    for (value in report.values) {
                        val name = value.name.replace("goog", "")
                        videoSendStat.append(name).append("=").append(value.value).append("\n")
                    }
                }
            } else if (report.type == "ssrc" && report.id.contains("ssrc")
                && report.id.contains("recv")
            ) {
                // Receive video statistics.
                val reportMap =
                    getReportMap(report)
                // Check if this stat is for video track.
                val frameWidth = reportMap["googFrameWidthReceived"]
                if (frameWidth != null) {
                    videoRecvStat.append(report.id).append("\n")
                    for (value in report.values) {
                        val name = value.name.replace("goog", "")
                        videoRecvStat.append(name).append("=").append(value.value).append("\n")
                    }
                }
            } else if (report.id == "bweforvideo") {
                // BWE statistics.
                val reportMap =
                    getReportMap(report)
                targetBitrate = reportMap["googTargetEncBitrate"]
                actualBitrate = reportMap["googActualEncBitrate"]
                bweStat.append(report.id).append("\n")
                for (value in report.values) {
                    val name =
                        value.name.replace("goog", "").replace("Available", "")
                    bweStat.append(name).append("=").append(value.value).append("\n")
                }
            } else if (report.type == "googCandidatePair") {
                // Connection statistics.
                val reportMap =
                    getReportMap(report)
                val activeConnection = reportMap["googActiveConnection"]
                if (activeConnection != null && activeConnection == "true") {
                    connectionStat.append(report.id).append("\n")
                    for (value in report.values) {
                        val name = value.name.replace("goog", "")
                        if ("bytesReceived" == name) {
                            val byteReceivedPerSec = value.value.toLong() - prevByteReceived
                            connectionStat.append("byteReceivedPerSec").append("=").append(byteReceivedPerSec).append("\n")
                            prevByteReceived = value.value.toLong()
                        }
                        if ("bytesSent" == name) {
                            val byteSentPerSec = value.value.toLong() - prevByteSent
                            connectionStat.append("ByteSentPerSec").append("=").append(byteSentPerSec).append("\n")
                            prevByteSent = value.value.toLong()
                        }
                        connectionStat.append(name).append("=").append(value.value).append("\n")
                    }
                }
            }
        }
        DebugLog.i(TAG, "Bwe $bweStat")
        DebugLog.i(TAG, "Connection $connectionStat")
//        DebugLog.i(TAG, "Video Send $videoSendStat")
//        DebugLog.i(TAG, "Video Receive $videoRecvStat")
        if (isVideo) {
            if (fps != null) {
                encoderStat.append("Fps:  ").append(fps).append("\n")
            }
            if (targetBitrate != null) {
                encoderStat.append("Target BR: ").append(targetBitrate).append("\n")
            }
            if (actualBitrate != null) {
                encoderStat.append("Actual BR: ").append(actualBitrate).append("\n")
            }
        }
        DebugLog.i(TAG, "Encoder stat $encoderStat")
    }

    companion object {
        private const val TAG = "StatParser"
    }
}
