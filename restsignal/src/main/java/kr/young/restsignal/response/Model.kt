package kr.young.restsignal.response

import kr.young.util.DebugLog

class Model<T> (val header: Header, val body: T) {

    init {
        if (body is List<*>) {
            DebugLog.i(TAG, "body is List")
        } else if (body is UserModel) {
            DebugLog.i(TAG, "body is UserModel")
        }
    }

    fun checkType() {
        DebugLog.i(TAG, "checkType()")
        when (body) {
            is List<*> -> {
                DebugLog.i(TAG, "body is List")
            }
            is UserModel -> {
                DebugLog.i(TAG, "body is UserModel")
            }
            else -> {
                DebugLog.i(TAG, body!!::class.toString())
            }
        }
    }

    override fun toString(): String {
        DebugLog.i(TAG, "response.Model")
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
