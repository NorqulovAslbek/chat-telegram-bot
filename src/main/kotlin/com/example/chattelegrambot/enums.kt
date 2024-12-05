package com.example.chattelegrambot

enum class ErrorCodes(val code: Int) {
    USER_NOT_FOUND(100),
}

enum class Language {
    UZ, EN
}

enum class Status {
    CHATTING, QUEUE
}

enum class SenderType {
    USER, OPERATOR
}
enum class UsersStep{
    USER_START,
    USER_LANGUAGE,
    USER_PHONE,
    USER_FULL_NAME,
    USER_WRITE_MESSAGE,
    OPERATOR_LANGUAGE,
    OPERATOR_START_WORK,
    OPERATOR_FINISH_CONVERSATION,
    OPERATOR_FINISH_WORK

}

