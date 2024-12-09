package com.example.chattelegrambot

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service


interface UserService {

    fun addRatingScore(score: Int, chatId: Long)
    fun findConversationByUser(userChatId: Long): Conversation?
    fun deleteMessage(chatId: Long)
    fun findMessageByUser(chatId: Long, message: String): Message?
}

interface OperatorService {
    fun findOperator(operatorChatId: Long): Operator?
    fun findAvailableOperator(langType: Language): Operator?
    fun startWork(chatId: Long, langType: Language): Users?

}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val ratingRepository: RatingRepository
) : UserService {
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