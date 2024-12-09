package com.example.chattelegrambot

data class BaseMessage(val code: Int, val message: String?)


data class RegisterUser(
    var fullName: String? = null,
    var phoneNumber: String? = null
)

