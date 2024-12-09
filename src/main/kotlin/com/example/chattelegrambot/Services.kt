package com.example.chattelegrambot

import Conversation
import Message
import Operator
import Queue
import Users
import WorkSession
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service


interface UserService {
    fun findUser(userChatId: Long): Users? //
    fun addUser(user: RegisterUser, chatId: Long, langType: Language) //
    fun findMessagesByUser(userChatId: Long): List<String>? //
    fun addQueue(chatId: Long) //
    fun addRatingScore(score: Int, chatId: Long)
    fun findConversationByUser(userChatId: Long): Conversation?
    fun deleteMessage(chatId: Long)
    fun findMessageByUser(chatId: Long, message: String): Message?
}

interface OperatorService {



    fun addConversation(chatId: Long, user: Users)
    fun addMessage(chatId: Long, content: String, userMessage: String, userChatId: Long, operatorMessageId: Int)
    fun changeStatus(chatId: Long, status: Status)
    fun findOperator(operatorChatId: Long): Operator?
    fun findAvailableOperator(langType: Language): Operator?
    fun startWork(chatId: Long, langType: Language): Users?



}

@Service
class UserServiceImpl(

    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val ratingRepository: RatingRepository
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

    override fun addRatingScore(score: Int, chatId: Long) {
        ratingRepository.findRating(chatId)?.let {
            it.score = score
            ratingRepository.save(it)
        }
    }


    override fun findConversationByUser(userChatId: Long): Conversation? {
        return conversationRepository.findConversationByUser(userChatId)
    }

    @Transactional
    override fun deleteMessage(chatId: Long) {
        messageRepository.deleteMessagesByUser(chatId)
    }

    override fun findMessageByUser(chatId: Long, message: String): Message? {
        return messageRepository.findMessageByUser(chatId, message)
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
    override fun findOperator(operatorChatId: Long): Operator? {
        return operatorRepository.findOperatorByChatId(operatorChatId)
    }

    override fun findAvailableOperator(langType: Language): Operator? {
        return operatorRepository.findAvailableOperator(langType)
    }

    override fun startWork(chatId: Long, langType: Language): Users? {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            it.status = Status.OPERATOR_ACTIVE
            operatorRepository.save(it)
            workSessionRepository.save(WorkSession(it, null, null, null))
        }
        return queueRepository.findFirstUserFromQueue(langType)
    }

}
