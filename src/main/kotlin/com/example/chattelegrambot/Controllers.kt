package com.example.chattelegrambot

import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow


@Component
class BotHandler(
    private val botHandlerForMessages: BotHandlerForMessages,
    private val botHandlerForReplyMarkUp: BotHandlerForReplyMarkUp,
    private val userService: UserService,
    private val operatorService: OperatorService
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
                            "Telefon raqamingizni kiriting: Namuna(+998 XX XXX - XX - XX)",
                            "Enter your phone number like(+998 XX XXX - XX - XX)"
                        )
                        setUserStep(chatId, Status.USER_PHONE)
                    }

                    Status.USER_PHONE -> {
                        val uzbekistanPhoneRegex = Regex("^\\+998\\s?\\d{2}\\s?\\d{3}[-\\s]?\\d{2}[-\\s]?\\d{2}\$")
                        if (uzbekistanPhoneRegex.matches(text)) {
                            val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                            userRegisterUser.phoneNumber = text.toString()
                            setRegistrationData(chatId, getRegistrationData(chatId))
                            userService.addUser(getRegistrationData(chatId), chatId, getUserLanguage(chatId)!!)
                            removeRegistrationData(chatId)
                            sendResponse(
                                chatId,
                                "Agar sizda qandaydir savollar mavjud bo'lsa yozishingiz mumkin",
                                "If you have any question , you can write something for support"
                            )
                            setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                        } else {
                            sendResponse(
                                chatId,
                                "Siz telefon raqamni noto'g'ri kirittingiz qaytadan kiriting",
                                "You sent phone number incorrect you should send correct version again"
                            )
                        }

                    }

                    Status.USER_WRITE_MESSAGE -> {
                        sendWritedMessage(chatId, text, update.message.messageId)
                    }

                    Status.USER_QUEUE -> {
                        if (text.equals("Orqaga") || text.equals("Back")) {
                            userService.deleteQueue(chatId)
                            userService.deleteMessage(chatId)
                            sendResponse(
                                chatId,
                                "Sizning shu paytgacha javob berilmagan savollaringiz muvaffaqiyatli o'chirildi",
                                "Your unanswered questions have been successfully deleted."
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
                            operatorService.startWorkSession(chatId)
                        }
                    }

                    Status.OPERATOR_ACTIVE -> {
                        if (text.equals("Ishni Yakunlash") || text.equals("Finish Work")) {
                            finishWork(chatId)
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
                "${Language.EN}_call_back_data".equals(data) && userStep == Status.USER_LANGUAGE -> {
                    setUserStep(chatId, Status.USER_FULL_NAME)
                    setUserLanguage(chatId, Language.EN)
                    execute(
                        botHandlerForMessages.sendMessage(
                            chatId, "Please, Enter your full name ",
                        )
                    )
                }

                "${Language.UZ}_call_back_data".equals(data) && userStep == Status.USER_LANGUAGE -> {
                    setUserStep(chatId, Status.USER_FULL_NAME)
                    setUserLanguage(chatId, Language.UZ)
                    execute(
                        botHandlerForMessages.sendMessage(
                            chatId,
                            "Iltimos, Ism familiyangizni kiriting!",
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
        when {
            userService.findUser(chatId) != null -> {
                val user = userService.findUser(chatId)
                getUserStep(chatId)?.let { step ->
                    setUserStep(chatId, getUserStep(chatId)!!)
                } ?: setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                setUserLanguage(chatId, user!!.langType)
                sendResponse(
                    chatId,
                    "Agar sizda qandaydir savollar mavjud bo'lsa yozishingiz mumkin",
                    "If you have any question , you can write something for support"
                )
            }

            operatorService.findOperator(chatId) != null -> {
                val operator = operatorService.findOperator(chatId)
                getUserStep(chatId)?.let {
                    setUserStep(chatId, getUserStep(chatId)!!)
                } ?: setUserStep(chatId, Status.OPERATOR_START_WORK)
                setUserLanguage(chatId, operator!!.language)
                sendResponse(
                    chatId,
                    "Salom  ${operator.fullName}",
                    "Hello ${operator.fullName}"
                )
                sendReplyMarkUp(
                    chatId,
                    "Ishni Boshlash",
                    "Start Work",
                    "Ishni boshlash uchun tasdiqlang xabarini jo'nating",
                    "For starting work send me confirmation message"
                )
            }

            else -> {
                setUserStep(chatId, Status.USER_LANGUAGE)
                execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        chatId,
                        Language.UZ.toString(),
                        Language.EN.toString(),
                        "Choose the language"
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
                "Sizning shu foydalanuvchi bilan muloqatingizni boshlandi ${it.fullName}",
                "Your conversation started with this user ${it.fullName}"
            )
            sendReplyMarkUp(
                chatId,
                "Ishni Yakunlash",
                "Suhbatni Yakunlash",
                "Finish Work",
                "Finish Conversation",
                "Quyidagi tugmalar yordamida ishni yoki suhbatni yakunlashingiz mumkin",
                "You can finish work or conversation by this buttons"
            )
            sendResponse(
                it.chatId,
                "Sizning xabaringiz ${it.fullName} operatorga muvaffaqiyatli jo'natildi",
                "Your messages successfully sent to operator ${it.fullName} !",
                ReplyKeyboardRemove(true)
            )
        } ?: run {
            sendResponse(
                chatId, "Hozircha navbatda turgan foydalanumchi yo'q!", "There is no user in queue",
                ReplyKeyboardRemove(true)
            )
            sendReplyMarkUp(
                chatId,
                "Ishni Yakunlash",
                "Finish Work",
                "Agar uzoq muddat foydalanuvchi chiqmasa ishni yakunlashingiz mumkin",
                "If the user remains inactive for a long time, you can conclude the task."
            )
            setUserStep(chatId, Status.OPERATOR_ACTIVE)
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
        messageUz: String,
        messageEn: String,
        messageResponseUz: String,
        messageResponseEn: String
    ) {
        when (getUserLanguage(chatId)) {
            Language.EN -> execute(botHandlerForReplyMarkUp.sendReplyMarkUp(chatId, messageEn, messageResponseEn))
            Language.UZ -> execute(botHandlerForReplyMarkUp.sendReplyMarkUp(chatId, messageUz, messageResponseUz))
            else -> ""
        }
    }

    fun sendReplyMarkUp(
        chatId: Long,
        messageFirstUz: String,
        messageSecondUz: String,
        messageFirstEn: String,
        messageSecondEn: String,
        messageResponseUz: String,
        messageResponseEn: String
    ) {
        when (getUserLanguage(chatId)) {
            Language.EN -> execute(
                botHandlerForReplyMarkUp.sendReplyMarkUp(
                    chatId,
                    messageFirstEn,
                    messageSecondEn,
                    messageResponseEn
                )
            )

            Language.UZ -> execute(
                botHandlerForReplyMarkUp.sendReplyMarkUp(
                    chatId,
                    messageFirstUz,
                    messageSecondUz,
                    messageResponseUz
                )
            )

            else -> ""
        }
    }

    fun sendResponse(chatId: Long, messageUz: String, messageEn: String) {
        val response = when (getUserLanguage(chatId)) {
            Language.EN -> messageEn
            Language.UZ -> messageUz
            else -> ""
        }
        execute(botHandlerForMessages.sendMessage(chatId, response))
    }

    fun sendResponse(chatId: Long, messageUz: String, messageEn: String, replyKeyboardRemove: ReplyKeyboardRemove) {
        val response = when (getUserLanguage(chatId)) {
            Language.EN -> messageEn
            Language.UZ -> messageUz
            else -> ""
        }
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
                "Sizning xabaringiz ${it.fullName} operatorga muvaffaqiyatli jo'natildi",
                "Your messages successfully sent to operator ${it.fullName} !"
            )
            sendResponse(
                it.chatId,
                "Sizning shu foydalanuvchi bilan muloqatingizni boshlandi ${userService.findUser(chatId)!!.fullName}}",
                "Your conversation started with this user ${userService.findUser(chatId)!!.fullName}"
            )
            sendReplyMarkUp(
                it.chatId,
                "Ishni Yakunlash",
                "Suhbatni Yakunlash",
                "Finish Work",
                "Finish Conversation",
                "Quyidagi tugmalar yordamida ishni yoki suhbatni yakunlashingiz mumkin",
                "You can finish work or conversation by this buttons"
            )

        } ?: run {
            if (getUserStep(chatId) != Status.USER_QUEUE) {
                userService.addQueue(chatId)
            }
            sendResponse(
                chatId,
                "Hozircha bo'sh operatorlarimiz yo'q , sizning xabarlaringiz navbatga qo'yiladi bo'sh operator bo'lishi bilan aloqaga chiqamiz",
                "Now don't have any available operators ,your messages will store to queue we will contact you when will be available operator"
            )
            setUserStep(chatId, Status.USER_QUEUE)
            sendReplyMarkUp(
                chatId,
                "Orqaga",
                "Back",
                "Agar kutishni hohlamasangiz Orqaga qaytishingiz mumkin.",
                "If you don't want to wait you can comeback"
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
                "Sizning xabaringiiz operatorga muvaffaqiyatli jo'natildi",
                "Your messages successfully sent to operator!"
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
        sendResponse(
            chatId,
            "Sizning suhbatingzi muvaffaqiyatli tugadi",
            "Your conversation has finished successfully ",
            ReplyKeyboardRemove(true)
        )
        startWork(chatId, getUserLanguage(chatId)!!)
    }

    fun finishWork(chatId: Long) {
        operatorService.finishWork(chatId)
        addRating(operatorService.finishConversation(chatId))
        setUserStep(chatId, Status.OPERATOR_INACTIVE)
        sendResponse(
            chatId, "Sizning ishingiz muvaffaqiyatli tugadi,", "Your work has finished successfully",
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
                        "Rate the conversation (1-5)"
                    )
                )

                Language.UZ -> execute(
                    botHandlerForReplyMarkUp.sendInlineMarkUp(
                        listOf("1", "2", "3", "4", "5"),
                        chatId,
                        "Suhbatni baholang (1-5)"
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
            "Agar sizda qandaydir savollar mavjud bo'lsa yozishingiz mumkin",
            "If you have any question , you can write something for support",
            ReplyKeyboardRemove(true)
        )
        setUserStep(chatId, Status.USER_WRITE_MESSAGE)
    }
}

@Controller
class BotHandlerForMessages(
) {

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
class BotHandlerForReplyMarkUp(
) {
    fun sendInlineMarkUp(chatId: Long, firstMessage: String, secondMessage: String, messageText: String): SendMessage {
        val sendMessage = SendMessage()
        val sendInlineKeyboardMarkUp = InlineKeyboardMarkup()
        val inlineKeyboardMarkupButton1 = InlineKeyboardButton()
        val inlineKeyboardMarkupButton2 = InlineKeyboardButton()
        inlineKeyboardMarkupButton1.text = firstMessage
        inlineKeyboardMarkupButton1.callbackData = "${firstMessage}_call_back_data"
        inlineKeyboardMarkupButton2.text = secondMessage
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
        messageResponse: String
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
                "Sessiyangiz o'chirildi qaytadan ishga tushirish uchun /start so'zini jo'nating",
                "Your session expired you send /start word"
            )
            removeUserLanguage(it)
        }
    }
}

