package com.young.rtp.util

import java.lang.StringBuilder

class SDPEditor {
    fun addIceCandidate(sdp: String, candidates: ArrayList<String>): String {
        var find = false
        val builder = StringBuilder()
        val lines: Array<String> = sdp.split(NEW_LINE.toRegex()).toTypedArray()
        for (line in lines) {
            if (line.startsWith(PREFIX, true)) {
                find = true
                builder.append(line).append(NEW_LINE)
                continue
            }
            if (find && !line.startsWith(PREFIX, true)) {
                find = false
                for (candidate in candidates) {
                    builder.append("a=").append(candidate).append(NEW_LINE)
                }
            }
            builder.append(line).append(NEW_LINE)
        }
        return builder.toString()
    }

    companion object {
        private const val PREFIX = "a=ice"
        private const val NEW_LINE = "\r\n"
    }
}