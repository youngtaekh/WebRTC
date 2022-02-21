package kr.young.restsignal.response

import kr.young.rtp.util.RTPLog
import kotlin.reflect.typeOf

class Model<T> (val header: Header, val body: T) {

    init {
        if (body is List<*>) {
            RTPLog.i(TAG, "body is List")
        } else if (body is UserModel) {
            RTPLog.i(TAG, "body is UserModel")
        }
    }

    fun checkType() {
        RTPLog.i(TAG, "checkType()")
        when (body) {
            is List<*> -> {
                RTPLog.i(TAG, "body is List")
            }
            is UserModel -> {
                RTPLog.i(TAG, "body is UserModel")
            }
            else -> {
                RTPLog.i(TAG, body!!::class.toString())
            }
        }
    }

    override fun toString(): String {
        RTPLog.i(TAG, "response.Model")
        return "Response(${header}, body = ${body})"
    }

    open class Header(val status: Int, val code: Int, val description: String) {
        override fun toString(): String {
            return "Header(status: $status, code: $code, description: $description)"
        }
    }

    companion object {
        const val TAG = "response.Model"
    }
}