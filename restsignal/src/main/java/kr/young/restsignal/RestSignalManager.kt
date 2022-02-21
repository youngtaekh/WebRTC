package kr.young.restsignal

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kr.young.rtp.util.RTPLog
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.Executors

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
