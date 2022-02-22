package kr.young.rtp.util

import android.os.Build
import kr.young.util.DebugLog
import java.lang.AssertionError

class RTCUtils private constructor() {
    companion object {
        @JvmStatic
        fun assertIsTrue(condition: Boolean) {
            if (!condition) {
                throw AssertionError("Expected condition to ble true")
            }
        }

        @JvmStatic
        fun getThreadInfo(): String {
            return "@[name=${Thread.currentThread().name}, id=${Thread.currentThread().id}]"
        }

        @JvmStatic
        fun logDeviceInfo(tag: String) {
            DebugLog.d(tag, "Android SDK: ${Build.VERSION.SDK_INT}, " +
                    "Release: ${Build.VERSION.RELEASE}, " +
                    "Brand: ${Build.BRAND}, " +
                    "Device: ${Build.DEVICE}, " +
                    "Id: ${Build.ID}, " +
                    "Hardware: ${Build.HARDWARE}, " +
                    "Model: ${Build.MODEL}, " +
                    "Product: ${Build.PRODUCT}")
        }
    }
}
