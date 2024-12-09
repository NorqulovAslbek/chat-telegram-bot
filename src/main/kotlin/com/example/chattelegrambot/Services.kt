package com.example.chattelegrambot

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service


interface UserService {
    fun findUser(userChatId: Long): Users? //
    fun addUser(user: RegisterUser, chatId: Long, langType: Language) //
    fun findMessagesByUser(userChatId: Long): List<String>? //
    fun addQueue(chatId: Long) //

}

interface OperatorService {



    fun addConversation(chatId: Long, user: Users)
    fun addMessage(chatId: Long, content: String, userMessage: String, userChatId: Long, operatorMessageId: Int)
    fun changeStatus(chatId: Long, status: Status)



}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val queueRepository: QueueRepository,
) : UserService {
    override fun findUser(userChatId: Long): Users? {
        return userRepository.findUsersByChatId(userChatId)
    }

    override fun addUser(user: RegisterUser, chatId: Long, langType: Language) {
        userRepository.save(
            Users(chatId, user.fullName!!, user.phoneNumber!!, langType)
        )
    }

    override fun findMessagesByUser(userChatId: Long): List<String>? {
        return messageRepository.findMessagesByUser(userChatId)
    }

    override fun addQueue(chatId: Long) {
        userRepository.findUsersByChatId(chatId)?.let {
            queueRepository.existUser(chatId) ?: queueRepository.save(Queue(it, it.langType))
        }
    }

}


@Service
class OperatorServiceImpl(
    private val operatorRepository: OperatorRepository,
    private val workSessionRepository: WorkSessionRepository,
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userService: UserService
) : OperatorService {
    override fun addConversation(chatId: Long, user: Users) {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            conversationRepository.save(Conversation(user, it, user.langType))
        }
    }

    override fun addMessage(
        chatId: Long,
        content: String,
        userMessage: String,
        userChatId: Long,
        operatorMessageId: Int
    ) {
        conversationRepository.findConversationByOperator(chatId)?.let { item ->
            messageRepository.save(Message(item, chatId, SenderType.OPERATOR, content, operatorMessageId))
            messageRepository.findMessageByUser(userChatId, userMessage)?.let {
                it.conversation = item
                messageRepository.save(it)
            }
        }
    }

    @Transactional
    override fun changeStatus(chatId: Long, status: Status) {
        operatorRepository.changeStatus(chatId, status)
    }

}
