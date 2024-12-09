package com.example.chattelegrambot


import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.*


interface UserService {
    fun deleteQueue(chatId: Long) ///
    fun addMessage(chatId: Long, content: String, messageId: Int) ///
    fun addConversation(chatId: Long, operator: Operator) ///
    fun addRating(user: Users, operator: Operator, conversation: Conversation) ///

}

interface OperatorService {

    fun finishWork(chatId: Long): Long?
    fun finishConversation(chatId: Long): Long?
    fun findConversationByOperator(chatId: Long): Conversation?
}

@Service
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val queueRepository: QueueRepository,
    private val conversationRepository: ConversationRepository,
    private val ratingRepository: RatingRepository
) : UserService {


    @Transactional
    override fun deleteQueue(chatId: Long) {
        userRepository.findUsersByChatId(chatId)?.let {
            queueRepository.deleteUserFromQueue(it.chatId, it.langType)
        }
    }

    override fun addMessage(chatId: Long, content: String, messageId: Int) {
        messageRepository.save(Message(null, chatId, SenderType.USER, content, messageId))
    }

    override fun addConversation(chatId: Long, operator: Operator) {
        userRepository.findUsersByChatId(chatId)?.let {
            conversationRepository.save(Conversation(it, operator, it.langType))
        }
    }

    override fun addRating(user: Users, operator: Operator, conversation: Conversation) {
        ratingRepository.save(Rating(conversation, user, operator))
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

    override fun finishWork(chatId: Long): Long? {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            it.status = Status.OPERATOR_INACTIVE
            operatorRepository.save(it)
            val workSession = workSessionRepository.getTodayWorkSession(chatId)
            val startDate = workSession.createdDate
            val endDate = Date()
            val workHour = (endDate.time - startDate!!.time) / (1000 * 60 * 60)

            workSession.endDate = endDate
            workSession.workHour = workHour.toInt()
            workSession.salary = workHour.toBigDecimal() * HOURLY_RATE
            workSessionRepository.save(workSession)
        }
        return finishConversation(chatId)
    }

    override fun finishConversation(chatId: Long): Long? {
        var userChatId: Long? = null
        operatorRepository.findOperatorByChatId(chatId)?.let { item ->
            conversationRepository.findConversationByOperator(item.chatId)?.let {
                userChatId = it.users.chatId
                userService.addRating(it.users, it.operator, it)
                it.endDate = Date()
                conversationRepository.save(it)
            }
        }
        return userChatId
    }

    override fun findConversationByOperator(chatId: Long): Conversation? {
        return conversationRepository.findConversationByOperator(chatId)
    }
}
