package com.example.chattelegrambot

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.*


@Component
class BotHandler(
    @Lazy private val botHandlerForMessages: BotHandlerForMessages,
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
        if (update != null && update.hasMessage() && update.message.hasContact()) {
            val contact = update.message.contact
            val phoneNumber = contact.phoneNumber
            val chatId = update.message.chatId

            if (contact.userId != chatId) {
                sendResponse(chatId, "contact.error")
            } else {
                // Full name va xabar identifikatorlarini o‘chirish
                val messageId = getAllFullNameIdAndMessageIds(chatId)
                if (messageId != null) {
                    deleteCallBack(chatId, messageId) // Callback o‘chiriladi
                    removeMapFullNameChatIdAndMessageId(chatId)// Mapdan olib tashlanadi
                }

                // Callback o‘chirish
                deleteCallBack(chatId, update.message.messageId)

                // Foydalanuvchi bosqichini tekshirish
                when (userService.getUserStep(chatId)) {
                    Status.USER_PHONE -> {
                        val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                        userRegisterUser.phoneNumber = phoneNumber
                        setRegistrationData(chatId, userRegisterUser) // Royxatga olish malumotlari saqlanadi
                        userService.addUser(userRegisterUser, chatId)
                        removeRegistrationData(chatId) // Royxatga olish malumotlarini ochirish
                        sendResponse(
                            chatId,
                            "write.question"
                        )
                        userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE) // Foydalanuvchi bosqichi yangilanadi
                    }

                    else -> ""
                }
            }
        } else if (update != null && update.hasMessage()) {
            val chatId = update.message.chatId
            val message = update.message
            val mId = update.message.messageId
            val text = update.message.text

            if (text != null && text.equals("/start")) {
                find(chatId)
            } else {
                when (userService.getUserStep(chatId)) {
                    Status.USER_FULL_NAME -> {
                        if (text != null) {
                            val userRegisterUser: RegisterUser = getRegistrationData(chatId)
                            userRegisterUser.fullName = text
                            setRegistrationData(chatId, userRegisterUser)

                            // ism yozilgan xabarni ochirish
                            deleteCallBack(chatId, update.message.messageId)

                            // ismini soragan xabarni olish va ochirish
                            val messageId = getAllFullNameIdAndMessageIds(chatId)
                            if (messageId != null) {
                                deleteCallBack(chatId, messageId)
                                removeMapFullNameChatIdAndMessageId(chatId)
                            }
                            sendContactRequest(chatId) //kontakni yuborish

                            userService.setUserStep(chatId, Status.USER_PHONE)
                        }
                    }

                    Status.USER_WRITE_MESSAGE -> {
                        sendWritedMessage(chatId, update.message, update.message.messageId)
                    }

                    Status.USER_QUEUE -> {
                        if (text != null) {
                            if (text.equals("Orqaga\uD83D\uDD19") || text.equals("Back\uD83D\uDD19")) {
                                userService.deleteQueue(chatId)
                                userService.deleteMessage(chatId)
                                sendResponse(
                                    chatId,
                                    "not.answer.delete", ReplyKeyboardRemove(true)
                                )
                                find(chatId)
                            } else {
                                addMessageForUser(chatId, update.message, update.message.messageId)
                            }
                        } else {
                            addMessageForUser(chatId, update.message, update.message.messageId)
                        }
                    }

                    Status.USER_CHATTING -> {
                        findConversation(chatId, message, mId)
                    }

                    Status.OPERATOR_START_WORK -> {
                        if (text != null) {
                            if (text.equals("Start Work") || text.equals("Ishni Boshlash")) {
                                startWork(chatId)
                                operatorService.startWorkSession(chatId)
                            }
                        }
                    }

                    Status.OPERATOR_ACTIVE -> {
                        if (text != null) {
                            if (text.equals("Ishni Yakunlash") || text.equals("Finish Work")) {
                                finishWork(chatId)
                            }
                        }
                    }

                    Status.OPERATOR_BUSY -> {
                        if (text != null && (text.equals("Ishni Yakunlash") || text.equals("Finish Work"))) {
                            finishWork(chatId)
                        } else if (text != null && (text.equals("Suhbatni Yakunlash") || text.equals("Finish Conversation"))) {
                            finishConversation(chatId)
                        } else {
                            val userChatId = operatorService.findConversationByOperator(chatId)?.users?.chatId
                            if (update.message.isReply) {
                                val userMessageContent =
                                    botHandlerForMessages.getContent(update.message.replyToMessage)
                                val userMessage =
                                    userMessageContent?.let { userService.findMessageByUser(userChatId!!, it) }
                                if (userMessage != null) {
                                    sendReplyMessage(userChatId!!, message, userMessage.messageId)
                                    addReplyMessageForOperator(
                                        chatId,
                                        message,
                                        mId,
                                        userChatId,
                                        userMessageContent
                                    )
                                }
                            } else {
                                botHandlerForMessages.sendMessage(userChatId!!, message)
                                addMessageForOperator(chatId, message, mId, userChatId)
                            }
                        }
                    }

                    else -> ""
                }
            }

        } else if (update != null && update.hasCallbackQuery()) {
            val chatId = update.callbackQuery.message.chatId
            val data = update.callbackQuery.data
            val userStep = userService.getUserStep(chatId)
            //// delete calback query
            deleteCallBack(chatId, update.callbackQuery.message.messageId)
            when {
                "${Language.EN}_call_back_data" == data && userStep == Status.USER_LANGUAGE -> {
                    userService.setUserStep(chatId, Status.USER_FULL_NAME)
                    val registerUser = getRegistrationData(chatId)
                    registerUser.langType = Language.EN
                    setRegistrationData(chatId, registerUser)
                    userService.addLanguage(chatId, Language.EN)
                    val message = execute(
                        botHandlerForMessages.sendText(
                            chatId, getMessageFromResourceBundle(chatId, "enter.name")
                        )
                    )
                    ////
                    putFullNameIdAndMessageId(chatId, message.messageId)
                }

                "${Language.UZ}_call_back_data" == data && userStep == Status.USER_LANGUAGE -> {
                    userService.setUserStep(chatId, Status.USER_FULL_NAME)
                    val registerUser = getRegistrationData(chatId)
                    registerUser.langType = Language.UZ
                    setRegistrationData(chatId, registerUser)
                    userService.addLanguage(chatId, Language.UZ)


                    val message = execute(
                        botHandlerForMessages.sendText(
                            chatId, getMessageFromResourceBundle(chatId, "enter.name")
                        )
                    )
                    ////
                    putFullNameIdAndMessageId(chatId, message.messageId)
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
            userService.findUser(chatId)?.phone != null -> {
                userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE)
                sendResponse(
                    chatId,
                    "have.question", ReplyKeyboardRemove(true)
                )
            }

            operatorService.findOperator(chatId) != null -> {
                val operator = operatorService.findOperator(chatId)
                operatorService.setOperatorStep(chatId, Status.OPERATOR_START_WORK)
                sendResponse(
                    chatId,
                    "hello",
                    operator?.fullName
                )
                sendReplyMarkUp(
                    chatId,
                    "start.work",
                    "sent.stark.work"
                )
            }

            else -> {
                if (userService.findUser(chatId) == null) {
                    userService.addUser(chatId, Status.USER_LANGUAGE)
                } else {
                    userService.deleteUser(chatId)
                }
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

    fun startWork(chatId: Long) {
        operatorService.startWork(chatId)?.let { it ->
            operatorService.setOperatorStep(chatId, Status.OPERATOR_BUSY)
            userService.setUserStep(it.chatId, Status.USER_CHATTING)
            userService.findMessagesByUser(it.chatId)?.let {
                it.forEach { message ->
                    botHandlerForMessages.sendMessage(chatId, message.type, message.caption, message.content)
                }
            }
            operatorService.addConversation(chatId, it)
            userService.deleteQueue(it.chatId)
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
                operatorService.findOperator(chatId)?.fullName, ReplyKeyboardRemove(true)
            )
        } ?: run {
            sendResponse(
                chatId,
                "not.user.in.queue",
            )
            sendReplyMarkUp(chatId, "finish.work", "message.for.finish.work")
        }
    }

    fun sendReplyMarkUp(
        chatId: Long,
        message: String,
        response: String,
    ) {
        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
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
        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
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

        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        val response = if (args.isNotEmpty()) {
            messageSource.getMessage(code, args, locale)
        } else {
            messageSource.getMessage(code, null, locale)
        }
        execute(botHandlerForMessages.sendText(chatId, response))
    }


    fun getMessageFromResourceBundle(chatId: Long, code: String): String {
        val userLanguage = userService.getUserLanguage(chatId)?.get(0)?.name?.lowercase() ?: "en"
        val locale = Locale(userLanguage)

        return messageSource.getMessage(code, null, locale)
    }


    fun sendResponse(chatId: Long, code: String, replyKeyboardRemove: ReplyKeyboardRemove) {

        val response = getMessageFromResourceBundle(chatId, code)
        execute(botHandlerForMessages.sendMessage(chatId, response, replyKeyboardRemove))
    }


    fun sendWritedMessage(chatId: Long, message: Message, messageId: Int) {
        operatorService.findAvailableOperator(userService.getUserLanguage(chatId)!![0])?.let {
            botHandlerForMessages.sendMessage(it.chatId, message)
            operatorService.setOperatorStep(it.chatId, Status.OPERATOR_BUSY)
            userService.setUserStep(chatId, Status.USER_CHATTING)
            userService.addConversation(chatId, it)
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
            userService.addQueue(chatId)
            sendResponse(
                chatId,
                "busy.operator"
            )
            userService.setUserStep(chatId, Status.USER_QUEUE)
            sendReplyMarkUp(
                chatId,
                "back",
                "back.message"
            )
        }
        addMessageForUser(chatId, message, messageId)
    }

    fun addMessageForUser(chatId: Long, message: Message, messageId: Int) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                userService.addMessage(chatId, it, item, message.caption, messageId)
            }
        }
    }

    fun addMessageForOperator(chatId: Long, message: Message, messageId: Int, userChatId: Long) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                operatorService.addMessage(chatId, it, item, message.caption, messageId)
            }
        }
        userService.addConversationToMessage(userChatId)
    }

    fun addReplyMessageForOperator(
        chatId: Long,
        message: Message,
        messageId: Int,
        userChatId: Long,
        userContent: String
    ) {
        botHandlerForMessages.findTypeOfMessage(message)?.let { item ->
            botHandlerForMessages.getContent(message)?.let {
                operatorService.addMessage(chatId, it, item, message.caption, messageId)
            }
        }
        userService.addConversationToMessage(userChatId, userContent)
    }


    fun findConversation(chatId: Long, message: Message, messageId: Int) {
        userService.findConversationByUser(chatId)?.let {
            botHandlerForMessages.sendMessage(it.operator.chatId, message)
            addMessageForUser(chatId, message, messageId)
        }
    }

    fun sendReplyMessage(chatId: Long, operatorMessage: Message, messageId: Int) {
        botHandlerForMessages.sendRepLyMessage(chatId, operatorMessage, messageId)
    }

    fun finishConversation(chatId: Long) {
        addRating(operatorService.finishConversation(chatId))
        operatorService.setOperatorStep(chatId, Status.OPERATOR_ACTIVE)
        sendResponse(
            chatId,
            "conversation.finished.success"
        )
        startWork(chatId)

    }

    fun finishWork(chatId: Long) {
        operatorService.finishWork(chatId)
        addRating(operatorService.finishConversation(chatId))
        operatorService.setOperatorStep(chatId, Status.OPERATOR_INACTIVE)
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
            when (userService.getUserLanguage(chatId)?.get(0)) {
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
            userService.setUserStep(chatId, Status.USER_RATING)
        }
    }

    fun addRatingScore(score: Int, chatId: Long) {
        userService.addRatingScore(score, chatId)
        sendResponse(
            chatId,
            "write.question",
            ReplyKeyboardRemove(true)
        )
        userService.setUserStep(chatId, Status.USER_WRITE_MESSAGE)
    }

    fun deleteCallBack(chatId: Long, messageId: Int) {
        try {
            execute(
                org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage(
                    chatId.toString(),
                    messageId
                )
            )
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    fun sendContactRequest(chatId: Long) {
        // Keyboard tugmasi yaratiladi
        val contactButton = KeyboardButton("\uD83D\uDCDE").apply {
            requestContact = true // Kontaktni so‘rashni yoqish
        }

        // Klaviatura qatorini yaratish
        val keyboardRow = KeyboardRow().apply {
            add(contactButton)
        }

        // ReplyKeyboardMarkup ni sozlash
        val replyKeyboardMarkup = ReplyKeyboardMarkup().apply {
            keyboard = listOf(keyboardRow)
            resizeKeyboard = true // Klaviatura ekranga moslashadi
            oneTimeKeyboard = true // Tugma faqat bir marta ko‘rinadi
        }

        // Xabarni yaratish
        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            text = getMessageFromResourceBundle(chatId, "share.your.contact") + "☎\uFE0F"
            replyMarkup = replyKeyboardMarkup // Klaviatura qo‘shiladi
        }

        // Xabarni yuborish
        val sendMessages = execute(message)
        putFullNameIdAndMessageId(chatId, sendMessages.messageId)
    }
}

@Controller
class BotHandlerForMessages(
    private val botHandler: BotHandler
) {

    fun sendMessage(chatId: Long, message: Message) {
        if (message.hasPhoto()) {
            botHandler.execute(sendPhoto(chatId, message.photo[message.photo.size - 1].fileId, message.caption))
        } else if (message.hasVideo()) {
            botHandler.execute(sendVideo(chatId, message.video.fileId, message.caption))
        } else if (message.hasText()) {
            botHandler.execute(sendText(chatId, message.text))
        } else if (message.hasAnimation()) {
            botHandler.execute(sendAnimation(chatId, message.animation.fileId, message.caption))
        } else if (message.hasAudio()) {
            botHandler.execute(sendAudio(chatId, message.audio.fileId, message.caption))
        } else if (message.hasVideoNote()) {
            botHandler.execute(sendVideoNote(chatId, message.videoNote.fileId))
        } else if (message.hasDocument()) {
            botHandler.execute(sendDocument(chatId, message.document.fileId, message.caption))
        } else if (message.hasSticker()) {
            botHandler.execute(sendSticker(chatId, message.sticker.fileId))
        } else if (message.hasVoice()) {
            botHandler.execute(sendVoice(chatId, message.voice.fileId, message.caption))
        }
    }

    fun sendMessage(chatId: Long, messageType: String, caption: String?, messageContent: String) {
        if (messageType == "PHOTO") {
            botHandler.execute(sendPhoto(chatId, messageContent, caption))
        } else if (messageType == "VIDEO") {
            botHandler.execute(sendVideo(chatId, messageContent, caption))
        } else if (messageType == "TEXT") {
            botHandler.execute(sendText(chatId, messageContent))
        } else if (messageType == "ANIMATION") {
            botHandler.execute(sendAnimation(chatId, messageContent, caption))
        } else if (messageType == "AUDIO") {
            botHandler.execute(sendAudio(chatId, messageContent, caption))
        } else if (messageType == "VIDEONOTE") {
            botHandler.execute(sendVideoNote(chatId, messageContent))
        } else if (messageType == "DOCUMENT") {
            botHandler.execute(sendDocument(chatId, messageContent, caption))
        } else if (messageType == "STICKER") {
            botHandler.execute(sendSticker(chatId, messageContent))
        } else if (messageType == "VOICE") {
            botHandler.execute(sendVoice(chatId, messageContent, caption))
        }
    }

    fun sendRepLyMessage(chatId: Long, message: Message, messageId: Int) {
        if (message.hasPhoto()) {
            val sendPhoto = sendPhoto(chatId, message.photo[message.photo.size - 1].fileId, message.caption)
            sendPhoto.replyToMessageId = messageId
            botHandler.execute(sendPhoto)
        } else if (message.hasVideo()) {
            val sendVideo = sendVideo(chatId, message.video.fileId, message.caption)
            sendVideo.replyToMessageId = messageId
            botHandler.execute(sendVideo)
        } else if (message.hasText()) {
            val sendText = sendText(chatId, message.text)
            sendText.replyToMessageId = messageId
            botHandler.execute(sendText)
        } else if (message.hasAnimation()) {
            val sendAnimation = sendAnimation(chatId, message.animation.fileId, message.caption)
            sendAnimation.replyToMessageId = messageId
            botHandler.execute(sendAnimation)
        } else if (message.hasAudio()) {
            val sendAudio = sendAudio(chatId, message.audio.fileId, message.caption)
            sendAudio.replyToMessageId = messageId
            botHandler.execute(sendAudio)
        } else if (message.hasVideoNote()) {
            val sendVideoNote = sendVideoNote(chatId, message.videoNote.fileId)
            sendVideoNote.replyToMessageId = messageId
            botHandler.execute(sendVideoNote)
        } else if (message.hasDocument()) {
            val sendDocument = sendDocument(chatId, message.document.fileId, message.caption)
            sendDocument.replyToMessageId = messageId
            botHandler.execute(sendDocument)
        } else if (message.hasSticker()) {
            val sendSticker = sendSticker(chatId, message.sticker.fileId)
            sendSticker.replyToMessageId = messageId
            botHandler.execute(sendSticker)
        } else if (message.hasVoice()) {
            val sendVoice = sendVoice(chatId, message.voice.fileId, message.caption)
            sendVoice.replyToMessageId = messageId
            botHandler.execute(sendVoice)
        }
    }

    fun findTypeOfMessage(message: Message): String? {
        if (message.hasPhoto()) {
            return "PHOTO"
        } else if (message.hasVideo()) {
            return "VIDEO"
        } else if (message.hasText()) {
            return "TEXT"
        } else if (message.hasAnimation()) {
            return "ANIMATION"
        } else if (message.hasAudio()) {
            return "AUDIO"
        } else if (message.hasVideoNote()) {
            return "VIDEONOTE"
        } else if (message.hasDocument()) {
            return "DOCUMENT"
        } else if (message.hasSticker()) {
            return "STICKER"
        } else if (message.hasVoice()) {
            return "VOICE"
        } else {
            return null
        }
    }

    fun getContent(message: Message): String? {
        if (message.hasPhoto()) {
            return message.photo[message.photo.size - 1].fileId
        } else if (message.hasVideo()) {
            return message.video.fileId
        } else if (message.hasText()) {
            return message.text
        } else if (message.hasAnimation()) {
            return message.animation.fileId
        } else if (message.hasAudio()) {
            return message.audio.fileId
        } else if (message.hasVideoNote()) {
            return message.videoNote.fileId
        } else if (message.hasDocument()) {
            return message.document.fileId
        } else if (message.hasSticker()) {
            return message.sticker.fileId
        } else if (message.hasVoice()) {
            return message.voice.fileId
        } else {
            return null
        }
    }

    fun sendMessage(chatId: Long, message: String, replyKeyboardRemove: ReplyKeyboardRemove): SendMessage {
        val sendMessage = SendMessage()
        sendMessage.chatId = chatId.toString()
        sendMessage.text = message
        sendMessage.replyMarkup = replyKeyboardRemove
        return sendMessage
    }

    fun sendText(chatId: Long, message: String): SendMessage {
        return SendMessage().apply {
            this.text = message
            this.chatId = chatId.toString()
        }
    }

    fun sendPhoto(chatId: Long, fileId: String, caption: String?): SendPhoto {
        return SendPhoto().apply {
            this.chatId = chatId.toString()
            this.photo = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendAudio(chatId: Long, fileId: String, caption: String?): SendAudio {
        return SendAudio().apply {
            this.chatId = chatId.toString()
            this.audio = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendVideo(chatId: Long, fileId: String, caption: String?): SendVideo {
        return SendVideo().apply {
            this.chatId = chatId.toString()
            this.video = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendAnimation(chatId: Long, fileId: String, caption: String?): SendAnimation {
        return SendAnimation().apply {
            this.chatId = chatId.toString()
            this.animation = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendVoice(chatId: Long, fileId: String, caption: String?): SendVoice {
        return SendVoice().apply {
            this.chatId = chatId.toString()
            this.voice = InputFile(fileId)
            this.caption = caption
        }
    }

    fun sendVideoNote(chatId: Long, fileId: String): SendVideoNote {
        return SendVideoNote().apply {
            this.chatId = chatId.toString()
            this.videoNote = InputFile(fileId)

        }
    }

    fun sendSticker(chatId: Long, fileId: String): SendSticker {
        return SendSticker().apply {
            this.chatId = chatId.toString()
            this.sticker = InputFile(fileId)
        }
    }

    fun sendDocument(chatId: Long, fileId: String, caption: String?): SendDocument {
        return SendDocument().apply {
            this.chatId = chatId.toString()
            this.document = InputFile(fileId)
            this.caption = caption
        }
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
//            removeUsersStep(it)
            botHandler.sendResponse(
                it,
                "session.expired"
            )
        }
    }
}





