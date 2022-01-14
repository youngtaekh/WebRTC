package kr.young.restsignal

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
        return restUrl == null || subUrl == null
    }

    fun test() {
        if (checkUrl()) {
            throw NoRestUrlException()
        }
    }

    companion object {
        private const val TAG = "RestSignalManager"
        val instance: RestSignalManager by lazy { Holder.INSTANCE }
    }
}