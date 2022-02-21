package kr.young.restsignal.response

import java.util.*

class UserModel(
    val userId: String,
    val email: String,
    val name: String,
    val password: String,
    val createdDate: String,
    val modifiedDate: Date
) {
    override fun toString(): String {
        return "User(userId: $userId, email: $email, name: $name, password: $password, " +
                "createdDate: ${createdDate}, modifiedDate: ${modifiedDate})"
    }
}