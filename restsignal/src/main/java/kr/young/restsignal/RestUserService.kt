package kr.young.restsignal

import io.reactivex.Observable
import kr.young.restsignal.response.Model
import kr.young.restsignal.response.UserModel
import retrofit2.http.GET

interface RestUserService {
    @GET("user")
    fun getUsers(): Observable<Model<List<UserModel>>>
}