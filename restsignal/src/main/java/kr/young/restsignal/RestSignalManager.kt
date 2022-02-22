package kr.young.restsignal

import android.annotation.SuppressLint

class RestSignalManager private constructor() {
    private var restUrl: String? = null
    private var subUrl: String? = null

    private object Holder {
        val INSTANCE = RestSignalManager()
    }

    fun setBasicUrl(restUrl: String, subUrl: String) {
        this.restUrl = restUrl
        this.subUrl = subUrl
    }

    private fun checkUrl(): Boolean {
        return restUrl.isNullOrEmpty()
    }

    @SuppressLint("CheckResult")
    fun test() {
        if (checkUrl()) {
            throw NoRestUrlException()
        }
        ThreadTest.restCall(restUrl!!, subUrl)
    }

    companion object {
        private const val TAG = "RestSignalManager"
        val instance: RestSignalManager by lazy { Holder.INSTANCE }
    }
}
