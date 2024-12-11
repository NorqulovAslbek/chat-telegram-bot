package com.example.chattelegrambot

import java.math.BigDecimal


private val usersSteps = mutableMapOf<Long, Status>()
private val usersLanguage = mutableMapOf<Long, Language>()
private val userRegistrations = mutableMapOf<Long, RegisterUser>()
private val mapFullNameChatIdAndMessageId = mutableMapOf<Long, Int>()
val HOURLY_RATE = BigDecimal("100000")
@Synchronized
fun getAllFullNameIdAndMessageIds(): LinkedHashMap<Long, Int> {
    return LinkedHashMap(mapFullNameChatIdAndMessageId)
}
@Synchronized
fun putFullNameIdAndMessageId(chatId: Long, messageId: Int) {
    mapFullNameChatIdAndMessageId[chatId] = messageId
}
@Synchronized
fun getUserStep(chatId: Long) = usersSteps[chatId]

@Synchronized
fun setUserStep(chatId: Long, step: Status) {
    usersSteps[chatId] = step
}

@Synchronized
fun removeUsersStep(chatId: Long) {
    usersSteps.remove(chatId)
}

@Synchronized
fun setUserLanguage(chatId: Long, language: Language) {
    usersLanguage[chatId] = language
}

@Synchronized
fun getUserLanguage(chatId: Long) = usersLanguage[chatId]

@Synchronized
fun removeUserLanguage(chatId: Long) {
    usersLanguage.remove(chatId)
}

@Synchronized
fun getRegistrationData(chatId: Long) = userRegistrations.getOrPut(chatId) { RegisterUser() }

@Synchronized
fun setRegistrationData(chatId: Long, registrationData: RegisterUser) {
    userRegistrations[chatId] = registrationData
}

@Synchronized
fun removeRegistrationData(chatId: Long) {
    userRegistrations.remove(chatId)
}