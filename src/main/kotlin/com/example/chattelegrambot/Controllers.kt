package com.example.chattelegrambot

import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import java.util.*


@Component
class BotHandler(
    private val botHandlerForMessages: BotHandlerForMessages,
    private val botHandlerForReplyMarkUp: BotHandlerForReplyMarkUp,
    private val userService: UserService,
    private val messageSource: MessageSource,
    private val operatorService: OperatorService,
) : TelegramLongPollingBot() {
    override fun getBotUsername(): String {
        return "@chat_telegram_1_0_bot"
    }

    override fun getBotToken(): String {
        return "7923535042:AAHgoQ0uf1h3zxHnidCSWBz7iszFtIbeKRA"
    }

    override fun onUpdateReceived(update: Update?) {
        if (update != null && update.hasMessage() && update.message.hasText()) {
            val text = update.message.text
            val chatId = update.message.chatId

            if (text.equals("/start")) {
                find(chatId)
            } else {
                when (getUserStep(chatId)) {
                    Status.USER_FULL_NAME -> {
                        val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                        userRegisterUser.fullName = text.toString()
                        setRegistrationData(chatId, getRegistrationData(chatId))
                        sendResponse(
                            chatId,
                            "enter.phone.number"
                        )
                        setUserStep(chatId, Status.USER_PHONE)
                    }

                    Status.USER_PHONE -> {
                        val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                        userRegisterUser.phoneNumber = text.toString()
                        setRegistrationData(chatId, getRegistrationData(chatId))
                        userService.addUser(getRegistrationData(chatId), chatId, getUserLanguage(chatId)!!)
                        removeRegistrationData(chatId)
                        sendResponse(
                            chatId,
                            "write.question"
                        )
                        setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                    }

                    Status.USER_WRITE_MESSAGE -> {
                        sendWritedMessage(chatId, text, update.message.messageId)
                    }

                    Status.USER_QUEUE -> {
                        if (text.equals("Orqaga\uD83D\uDD19") || text.equals("Back\uD83D\uDD19")) {
                            userService.deleteQueue(chatId)
                            userService.deleteMessage(chatId)
                            sendResponse(
                                chatId,
                                "not.answer.delete",
                            )
                        } else {
                            addMessage(chatId, text, update.message.messageId)
                        }
                    }

                    Status.USER_CHATTING -> {
                        findConversation(chatId, text, update.message.messageId)

                    }

                    Status.OPERATOR_START_WORK -> {
                        if (text.equals("Start Work") || text.equals("Ishni Boshlash")) {
                            startWork(chatId, getUserLanguage(chatId)!!)
                        }
                    }

                    Status.OPERATOR_BUSY -> {
                        if (update.message.isReply) {
                            val userChatId = operatorService.findConversationByOperator(chatId)?.users?.chatId
                            val userMessage = update.message.replyToMessage.text
                            val messageId = userService.findMessageByUser(userChatId!!, userMessage)?.messageId
                            sendReplyMessage(userChatId, text, messageId!!)
                            addMessage(chatId, text, userMessage, userChatId, update.message.messageId)
                        } else if (text.equals("Ishni Yakunlash") || text.equals("Finish Work")) {
                            finishWork(chatId)

                        } else if (text.equals("Suhbatni Yakunlash") || text.equals("Finish Conversation")) {
                            finishConversation(chatId)

                        }
                    }

                    else -> ""
                }
            }

        } else if (update != null && update.hasCallbackQuery()) {
            val chatId = update.callbackQuery.message.chatId
            val data = update.callbackQuery.data
            val userStep = getUserStep(chatId)
            when {
                "${Language.EN}_call_back_data" == data && userStep == Status.USER_LANGUAGE -> {
                    setUserStep(chatId, Status.USER_FULL_NAME)
                    setUserLanguage(chatId, Language.EN)
                    execute(
                        botHandlerForMessages.sendMessage(
                            chatId, getMessageFromResourceBundle(chatId, "enter.name")
                        )
                    )
                }

                "${Language.UZ}_call_back_data" == data && userStep == Status.USER_LANGUAGE -> {
                    setUserStep(chatId, Status.USER_FULL_NAME)
                    setUserLanguage(chatId, Language.UZ)
                    execute(
                        botHandlerForMessages.sendMessage(
                            chatId, getMessageFromResourceBundle(chatId, "enter.name")
                        )
                    )
                }

                "1_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(1, chatId)
                "2_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(2, chatId)
                "3_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(3, chatId)
                "4_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(4, chatId)
                "5_call_back_data" == data && userStep == Status.USER_RATING -> addRatingScore(5, chatId)
                else -> ""
            }


        }
    }

    fun find(chatId: Long) {
        val sendMessage: SendMessage
        when {
            userService.findUser(chatId) != null -> {
                val user = userService.findUser(chatId)
                getUserStep(chatId)?.let { step ->
                    setUserStep(chatId, getUserStep(chatId)!!)
                } ?: setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                setUserLanguage(chatId, user!!.langType)
                sendResponse(
                    chatId,
                    "have.question"
                )
            }

            operatorService.findOperator(chatId) != null -> {
                val operator = operatorService.findOperator(chatId)
                getUserStep(chatId)?.let {
                    setUserStep(chatId, getUserStep(chatId)!!)
                } ?: setUserStep(chatId, Status.OPERATOR_START_WORK)
                setUserLanguage(chatId, operator!!.language[0])
                sendResponse(
                    chatId,
                    "hello",
                    operator.fullName
                )
                sendReplyMarkUp(
                    chatId,
                    "start.work",
                    "sent.stark.work"
                )
            }

            else -> {
                setUserStep(chatId, Status.USER_LANGUAGE)
                execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        chatId,
                        Language.UZ.toString(),
                        Language.EN.toString(),
                        "Choose the language(Tilni tanlang):"
                    )
                )
            }
        }
    }

    fun startWork(chatId: Long, language: Language) {
        var sendMessage: SendMessage
        operatorService.startWork(chatId, language)?.let { it ->
            userService.findMessagesByUser(it.chatId)?.let {
                it.forEach { message ->
                    sendMessage = botHandlerForMessages.sendMessage(chatId, message)
                    execute(sendMessage)
                }
            }
            setUserStep(chatId, Status.OPERATOR_BUSY)
            setUserStep(it.chatId, Status.USER_CHATTING)
            operatorService.addConversation(chatId, it)
            userService.deleteQueue(it.chatId)
            operatorService.changeStatus(chatId, Status.OPERATOR_BUSY)
            sendResponse(
                chatId,
                "start.conversation",
                it.fullName
            )
            sendReplyMarkUp(
                chatId,
                "finish.work",
                "finish.conversation",
                "message.for.finish"
            )
            sendResponse(
                it.chatId,
                "sent.successfully.to.operator",
                it.fullName
            )
        } ?: run {
            sendResponse(
                chatId,
                "not.user.in.queue",
                ReplyKeyboardRemove(true)
            )
        }
    }

    fun sendInlineMarkup(
        chatId: Long,
        firstMessageUz: String,
        secondMessageUz: String,
        firstMessageEn: String,
        secondMessageEn: String,
        messageResponseUz: String,
        messageResponseEn: String
    ) {

        when (getUserLanguage(chatId)) {
            Language.UZ -> execute(
                botHandlerForReplyMarkUp.sendInlineMarkUp(
                    chatId,
                    firstMessageUz,
                    secondMessageUz,
                    messageResponseUz
                )
            )

            Language.EN -> execute(
                botHandlerForReplyMarkUp.sendInlineMarkUp(chatId, firstMessageEn, secondMessageEn, messageResponseEn)
            )

            else -> ""
        }
    }

    fun sendReplyMarkUp(
        chatId: Long,
        message: String,
        response: String,
    ) {
        val userLanguage = getUserLanguage(chatId)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        val message1 = messageSource.getMessage(message, null, locale)
        val response1 = messageSource.getMessage(response, null, locale)
        execute(botHandlerForReplyMarkUp.sendReplyMarkUp(chatId, message1, response1))

    }

    fun sendReplyMarkUp(
        chatId: Long,
        first: String,
        second: String,
        response: String,
    ) {
        val userLanguage = getUserLanguage(chatId)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)


        execute(
            botHandlerForReplyMarkUp.sendReplyMarkUp(
                chatId,
                messageSource.getMessage(first, null, locale),
                messageSource.getMessage(second, null, locale),
                messageSource.getMessage(response, null, locale)
            )
        )
    }


    fun sendResponse(chatId: Long, code: String, vararg args: Any?) {

        val userLanguage = getUserLanguage(chatId)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        val response = if (args.isNotEmpty()) {
            messageSource.getMessage(code, args, locale)
        } else {
            messageSource.getMessage(code, null, locale)
        }
        execute(botHandlerForMessages.sendMessage(chatId, response))
    }


    fun getMessageFromResourceBundle(chatId: Long, code: String): String {
        val userLanguage = getUserLanguage(chatId)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        return messageSource.getMessage(code, null, locale)
    }


    fun sendResponse(chatId: Long, code: String, replyKeyboardRemove: ReplyKeyboardRemove) {

        val response = getMessageFromResourceBundle(chatId, code)
        execute(botHandlerForMessages.sendMessage(chatId, response, replyKeyboardRemove))
    }


    fun sendWritedMessage(chatId: Long, message: String, messageId: Int) {
        operatorService.findAvailableOperator(getUserLanguage(chatId)!!)?.let {
            execute(botHandlerForMessages.sendMessage(it.chatId, message))
            setUserStep(it.chatId, Status.OPERATOR_BUSY)
            setUserStep(chatId, Status.USER_CHATTING)
            userService.addConversation(chatId, it)
            operatorService.changeStatus(it.chatId, Status.OPERATOR_BUSY)
            sendResponse(
                chatId,
                "sent.successfully.to.operator",
                it.fullName

            )
            sendResponse(
                it.chatId,
                "start.conversation",
                userService.findUser(chatId)!!.fullName

            )
            sendReplyMarkUp(
                it.chatId,
                "finish.work",
                "finish.conversation",
                "message.for.finish"
            )

        } ?: run {
            if (getUserStep(chatId) != Status.USER_QUEUE) {
                userService.addQueue(chatId)
            }
            sendResponse(
                chatId,
                "busy.operator"
            )
            setUserStep(chatId, Status.USER_QUEUE)
            sendReplyMarkUp(
                chatId,
                "back",
                "back.message"
            )
        }

        addMessage(chatId, message, messageId)
    }

    fun addMessage(chatId: Long, message: String, messageId: Int) {
        userService.addMessage(chatId, message, messageId)
    }

    fun addMessage(chatId: Long, message: String, userMessage: String, userChatId: Long, operatorMessageId: Int) {
        operatorService.addMessage(chatId, message, userMessage, userChatId, operatorMessageId)
    }

    fun findConversation(chatId: Long, message: String, messageId: Int) {
        userService.findConversationByUser(chatId)?.let {
            execute(botHandlerForMessages.sendMessage(it.operator.chatId, message))
            sendResponse(
                chatId,
                "sent.message.to.operator",
                ReplyKeyboardRemove(true)
            )
            addMessage(chatId, message, messageId)
        }
    }

    fun sendReplyMessage(chatId: Long, operatorMessage: String, messageId: Int) {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.replyToMessageId = messageId
        sendMessage.text = operatorMessage
        execute(sendMessage)
    }

    fun finishConversation(chatId: Long) {
        addRating(operatorService.finishConversation(chatId))
        setUserStep(chatId, Status.OPERATOR_ACTIVE)
        sendResponse(
            chatId,
            "conversation.finished.success"
        )
        startWork(chatId, getUserLanguage(chatId)!!)

    }

    fun finishWork(chatId: Long) {
        operatorService.finishWork(chatId)
        addRating(operatorService.finishConversation(chatId))
        setUserStep(chatId, Status.OPERATOR_INACTIVE)
        sendResponse(
            chatId,
            "work.finished.success",
            ReplyKeyboardRemove(true)
        )
        find(chatId)
    }

    fun addRating(chatId: Long?) {
        if (chatId != null) {
            userService.deleteMessage(chatId)
            when (getUserLanguage(chatId)) {
                Language.EN -> execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        listOf("1", "2", "3", "4", "5"),
                        chatId,
                        getMessageFromResourceBundle(chatId, "rate.conversation")

                    )
                )

                Language.UZ -> execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        listOf("1", "2", "3", "4", "5"),
                        chatId,
                        getMessageFromResourceBundle(chatId, "rate.conversation")
                    )
                )

                else -> ""
            }
            setUserStep(chatId, Status.USER_RATING)
        }
    }

    fun addRatingScore(score: Int, chatId: Long) {
        userService.addRatingScore(score, chatId)
        sendResponse(
            chatId,
            "write.question",
            ReplyKeyboardRemove(true)
        )
        setUserStep(chatId, Status.USER_WRITE_MESSAGE)
    }
}

@Controller
class BotHandlerForMessages {

    fun sendMessage(chatId: Long, message: String): SendMessage {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.text = message
        return sendMessage

    }

    fun sendMessage(chatId: Long, message: String, replyKeyboardRemove: ReplyKeyboardRemove): SendMessage {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.text = message
        sendMessage.replyMarkup = replyKeyboardRemove
        return sendMessage

    }
}

@Controller
class BotHandlerForReplyMarkUp {
    fun sendInlineMarkUp(chatId: Long, firstMessage: String, secondMessage: String, messageText: String): SendMessage {
        val sendMessage = SendMessage()
        val sendInlineKeyboardMarkUp = InlineKeyboardMarkup()
        val inlineKeyboardMarkupButton1 = InlineKeyboardButton()
        val inlineKeyboardMarkupButton2 = InlineKeyboardButton()
        inlineKeyboardMarkupButton1.text = "\uD83C\uDDFA\uD83C\uDDFF $firstMessage"
        inlineKeyboardMarkupButton1.callbackData = "${firstMessage}_call_back_data"
        inlineKeyboardMarkupButton2.text = "\uD83C\uDDEC\uD83C\uDDE7 $secondMessage"
        inlineKeyboardMarkupButton2.callbackData = "${secondMessage}_call_back_data"
        val listOfButtons = mutableListOf(inlineKeyboardMarkupButton1, inlineKeyboardMarkupButton2)
        val listOfListsOfButtons = mutableListOf(listOfButtons)
        sendInlineKeyboardMarkUp.keyboard = listOfListsOfButtons
        sendMessage.replyMarkup = sendInlineKeyboardMarkUp
        sendMessage.chatId = chatId.toString()
        sendMessage.text = messageText
        return sendMessage
    }

    fun sendInlineMarkUp(listOfInlines: List<String>, chatId: Long, message: String): SendMessage {
        val sendMessage = SendMessage()
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val rows = mutableListOf<InlineKeyboardButton>()

        for (i in listOfInlines.indices) {
            val button = InlineKeyboardButton().apply {
                text = listOfInlines[i]
                callbackData = "${listOfInlines[i]}_call_back_data"
            }
            rows.add(button)
        }
        inlineKeyboardMarkup.apply {
            keyboard = listOf(rows)
        }
        return sendMessage.apply {
            this.chatId = chatId.toString()
            this.text = message
            replyMarkup = inlineKeyboardMarkup
        }
    }


    fun sendReplyMarkUp(chatId: Long, message: String, messageResponse: String): SendMessage {
        val sendMessage = SendMessage()
        val replyKeyboardMarkup = ReplyKeyboardMarkup()
        val replyMarkUpRow = KeyboardRow()
        val replyKeyboardButton = KeyboardButton()

        replyKeyboardButton.text = message
        replyMarkUpRow.add(replyKeyboardButton)
        replyKeyboardMarkup.selective = true
        replyKeyboardMarkup.resizeKeyboard = true
        replyKeyboardMarkup.keyboard = mutableListOf(replyMarkUpRow)


        sendMessage.replyMarkup = replyKeyboardMarkup
        sendMessage.chatId = chatId.toString()
        sendMessage.text = messageResponse
        return sendMessage
    }

    fun sendReplyMarkUp(
        chatId: Long,
        firstMessage: String,
        secondMessage: String,
        messageResponse: String,
    ): SendMessage {
        val sendMessage = SendMessage()
        val replyKeyboardMarkup = ReplyKeyboardMarkup()
        val replyMarkUpRow = KeyboardRow()
        val replyKeyboardButton1 = KeyboardButton()
        val replyKeyboardButton2 = KeyboardButton()

        replyKeyboardButton1.text = firstMessage
        replyKeyboardButton2.text = secondMessage
        replyMarkUpRow.add(replyKeyboardButton1)
        replyMarkUpRow.add(replyKeyboardButton2)
        replyKeyboardMarkup.selective = true
        replyKeyboardMarkup.resizeKeyboard = true
        replyKeyboardMarkup.keyboard = mutableListOf(replyMarkUpRow)

        sendMessage.replyMarkup = replyKeyboardMarkup
        sendMessage.chatId = chatId.toString()
        sendMessage.text = messageResponse
        return sendMessage
    }

}


@RestController
@RequestMapping("/api/operators/statistics")
class OperatorStatisticsController(
    private val operatorStatisticsService: OperatorStatisticsService
) {

    @GetMapping("/total")
    fun getTotalOperators(): Long {
        return operatorStatisticsService.getTotalOperators()
    }


    @GetMapping("/work-hours")
    fun getTotalWorkHours(): List<OperatorWorkHoursDto> {
        return operatorStatisticsService.findTotalWorkHours()
    }

    @GetMapping("/salary")
    fun getTotalSalary(): List<OperatorSalaryDto> {
        return operatorStatisticsService.findTotalSalary()
    }

    @GetMapping("/ratings")//ortacha
    fun getAverageRatings(): List<OperatorRatingDto> {
        return operatorStatisticsService.findAverageRatings()
    }

    @GetMapping("/conversations")
    fun getTotalConversations(): List<OperatorConversationDto> {
        return operatorStatisticsService.findOperatorConversationCounts()
    }
}


@RestController
@RequestMapping("/admin")
class AdminPanelController(
    private val operatorService: OperatorService,
    private val botHandler: BotHandler
) {
    @PostMapping("/create")
    fun createOperator(@RequestBody operator: RegisterOperator) {
        operatorService.addOperator(operator.id, operator.langType)?.let {
            removeUsersStep(it)
            botHandler.sendResponse(
                it,
                "session.expired"
            )
            removeUserLanguage(it)
        }
    }
}





