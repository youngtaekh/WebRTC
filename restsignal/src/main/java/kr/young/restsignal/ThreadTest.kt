package kr.young.restsignal

import android.annotation.SuppressLint
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
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

class ThreadTest {
    companion object {
        private const val TAG = "ThreadTest"

        @JvmStatic
        fun coroutineTest() = runBlocking {
            val job = launch {
                delay(1000)
                RTPLog.i(TAG, "launch")
            }
            RTPLog.i(TAG, "runBlocking")

            job.join()
        }

        @SuppressLint("CheckResult")
        @JvmStatic
        fun rxTest() {
            val ob = Observable.just(1)
            ob.subscribeOn(Schedulers.io())
                .subscribe { RTPLog.i(TAG, "Schedulers.io()-${Thread.currentThread().name}") }
            ob.subscribeOn(Schedulers.computation())
                .subscribe { RTPLog.i(TAG, "Schedulers.computation()-${Thread.currentThread().name}") }
            ob.subscribeOn(Schedulers.newThread())
                .subscribe { RTPLog.i(TAG, "Schedulers.newThread()-${Thread.currentThread().name}") }
            ob.subscribeOn(Schedulers.single())
                .subscribe { RTPLog.i(TAG, "Schedulers.single()-${Thread.currentThread().name}") }
            ob.subscribeOn(Schedulers.trampoline())
                .subscribe { RTPLog.i(TAG, "Schedulers.trampoline()-${Thread.currentThread().name}") }
            ob.subscribeOn(AndroidSchedulers.mainThread())
                .subscribe { RTPLog.i(TAG, "AndroidSchedulers.mainThread()-${Thread.currentThread().name}") }

            val executor = Executors.newFixedThreadPool(2)
            val customScheduler = Schedulers.from(executor)
            ob.subscribeOn(customScheduler)
                .subscribe { println("Schedulers.from() - ${Thread.currentThread().name}") }
        }

        @SuppressLint("CheckResult")
        @JvmStatic
        fun restCall(restUrl: String, subUrl: String?) {
            val ob = Observable.just(3)
            ob.subscribeOn(Schedulers.computation())
                .subscribe {
                    RTPLog.i(TAG, "Schedulers.io()-${Thread.currentThread().name}")
                    val retrofit = Retrofit.Builder()
                        .baseUrl(restUrl + subUrl)
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                    val service = retrofit.create(RestUserService::class.java)
                    service.getUsers()
                        .doOnError { RTPLog.i(TAG, "0${it.message!!}") }
                        .subscribeBy(
                            onNext = {
                                it.checkType()
//                            RTPLog.i(TAG, response.toString())
                            },
                            onError = { it.printStackTrace() }
                        )
                }
        }
    }
}
