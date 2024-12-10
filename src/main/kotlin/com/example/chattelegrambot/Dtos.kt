package com.example.chattelegrambot


data class BaseMessage(val code: Int, val message: String?)


data class RegisterUser(
    var fullName: String? = null,
    var phoneNumber: String? = null
)

data class RegisterOperator(
    val id: Long,
    val langType: Language,
)