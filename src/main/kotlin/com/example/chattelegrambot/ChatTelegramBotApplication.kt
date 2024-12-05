package com.example.chattelegrambot

import MyBotController
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@SpringBootApplication
class ChatTelegramBotApplication

fun main(args: Array<String>) {
    runApplication<ChatTelegramBotApplication>(*args)
    val telegramApi = TelegramBotsApi(DefaultBotSession::class.java)
    telegramApi.registerBot(MyBotController())
}
