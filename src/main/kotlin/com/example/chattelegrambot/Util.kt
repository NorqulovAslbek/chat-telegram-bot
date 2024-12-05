package com.example.chattelegrambot


private val usersSteps = mutableMapOf<Long, UsersStep>()
fun getUsersSteps(chatId: Long) = usersSteps[chatId]
fun setUsersSteps(chatId: Long, step: UsersStep) {
    usersSteps[chatId] = step
}