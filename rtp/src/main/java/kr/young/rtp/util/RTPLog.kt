package kr.young.rtp.util

import android.util.Log
import kr.young.rtp.BuildConfig

class RTPLog {
    companion object {
        @JvmStatic var isModuleTag = false
        private const val MODULE_TAG = "RTP"
        @JvmStatic fun v(tag: String, message:String) {
            if (BuildConfig.DEBUG) {
                if (isModuleTag) {
                    Log.v(MODULE_TAG, message)
                } else {
                    Log.v(tag, message)
                }
            }
        }

        @JvmStatic fun d(tag: String, message:String) {
            if (BuildConfig.DEBUG) {
                if (isModuleTag) {
                    Log.d(MODULE_TAG, message)
                } else {
                    Log.d(tag, message)
                }
            }
        }

        @JvmStatic fun i(tag: String, message:String) {
            if (BuildConfig.DEBUG) {
                if (isModuleTag) {
                    Log.i(MODULE_TAG, message)
                } else {
                    Log.i(tag, message)
                }
            }
        }

        @JvmStatic fun w(tag: String, message:String) {
            if (BuildConfig.DEBUG) {
                if (isModuleTag) {
                    Log.w(MODULE_TAG, message)
                } else {
                    Log.w(tag, message)
                }
            }
        }

        @JvmStatic fun e(tag: String, message:String) {
            if (BuildConfig.DEBUG) {
                if (isModuleTag) {
                    Log.e(MODULE_TAG, message)
                } else {
                    Log.e(tag, message)
                }
            }
        }
    }
}