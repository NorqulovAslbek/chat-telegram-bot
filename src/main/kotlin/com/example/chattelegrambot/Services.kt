package com.example.chattelegrambot

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.*
import kotlin.time.Duration.Companion.hours


interface UserService {
    fun findUser(userChatId: Long): Users? //
    fun addUser(user: RegisterUser, chatId: Long, langType: Language) //
    fun findMessagesByUser(userChatId: Long): List<String>? //
    fun addQueue(chatId: Long) //
    fun addRatingScore(score: Int, chatId: Long)
    fun findConversationByUser(userChatId: Long): Conversation?
    fun deleteMessage(chatId: Long)
    fun findMessageByUser(chatId: Long, message: String): Message?
    fun deleteQueue(chatId: Long) ///
    fun addMessage(chatId: Long, content: String, messageId: Int) ///
    fun addConversation(chatId: Long, operator: Operator) ///
    fun addRating(user: Users, operator: Operator, conversation: Conversation) ///

}

interface OperatorService {
    fun addConversation(chatId: Long, user: Users)
    fun addMessage(chatId: Long, content: String, userMessage: String, userChatId: Long, operatorMessageId: Int)
    fun addOperator(userId: Long, language: List<Language>): Long?
    fun changeStatus(chatId: Long, status: Status)
    fun findOperator(operatorChatId: Long): Operator?
    fun findAvailableOperator(langType: Language): Operator?
    fun startWork(chatId: Long, langType: Language): Users?
    fun startWorkSession(chatId: Long)
    fun finishWork(chatId: Long)
    fun finishConversation(chatId: Long): Long?
    fun findConversationByOperator(chatId: Long): Conversation?


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
            Users(chatId, user.fullName!!, user.phoneNumber!!, langType, Status.USER_WRITE_MESSAGE)
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
    private val userService: UserService,
    private val userRepository: UserRepository
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

    override fun addOperator(userId: Long, language: List<Language>): Long? {
        var userChatId: Long? = null
        userRepository.findByIdAndDeletedFalse(userId)?.let {
            operatorRepository.save(Operator(it.chatId, it.fullName, it.phone, language))
            userChatId = it.chatId
            userRepository.trash(userId)
        }
        return userChatId
    }

    @Transactional
    override fun changeStatus(chatId: Long, status: Status) {
        operatorRepository.changeStatus(chatId, status)
    }

    override fun findOperator(operatorChatId: Long): Operator? {
        return operatorRepository.findOperatorByChatId(operatorChatId)
    }

    override fun findAvailableOperator(langType: Language): Operator? {
        return operatorRepository.findAvailableOperator(langType.toString())
    }

    override fun startWork(chatId: Long, langType: Language): Users? {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            it.status = Status.OPERATOR_ACTIVE
            operatorRepository.save(it)
            workSessionRepository.save(WorkSession(it, null, null, null))
        }
        return queueRepository.findFirstUserFromQueue(langType)
    }

    override fun startWorkSession(chatId: Long) {
        operatorRepository.findOperatorByChatId(chatId)?.let {
            workSessionRepository.save(WorkSession(it, null, null, null))
        }
    }


    override fun finishWork(chatId: Long) {
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
    }

    @Transactional
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


interface OperatorStatisticsService {
    fun getTotalOperators(): Long
    fun findTotalWorkHours(): List<OperatorWorkHoursDto>

    fun findTotalSalary(): List<OperatorSalaryDto>

    fun findAverageRatings(): List<OperatorRatingDto>

    fun findOperatorConversationCounts(): List<OperatorConversationDto>

}

@Service
class OperatorStatisticsServiceImpl(
    private val operatorRepository: OperatorRepository,
    private val workSessionRepository: WorkSessionRepository,
    private val conversationRepository: ConversationRepository,
    private val ratingRepository: RatingRepository
) : OperatorStatisticsService {
    override fun getTotalOperators(): Long {
        return operatorRepository.count()
    }

    override fun findTotalWorkHours(): List<OperatorWorkHoursDto> {
        return workSessionRepository.findTotalWorkHoursRaw().map { row ->
            val operatorName = row[0] as String
            val totalWorkHours = (row[1] as Number).toLong()
            OperatorWorkHoursDto(operatorName, totalWorkHours)
        }
    }

    override fun findTotalSalary(): List<OperatorSalaryDto> {
        return workSessionRepository.findTotalSalaryRaw().map { row ->
            val operatorName = row[0] as String
            val totalSalary = (row[1] as BigDecimal?) ?: BigDecimal.ZERO
            OperatorSalaryDto(operatorName, totalSalary)
        }
    }


    override fun findAverageRatings(): List<OperatorRatingDto> {
        return ratingRepository.findAverageRatingsRaw().map { row ->
            val operatorName = row[0] as String
            val averageRating = (row[1] as Number).toDouble()
            OperatorRatingDto(operatorName, averageRating)
        }
    }


    override fun findOperatorConversationCounts(): List<OperatorConversationDto> {
        return conversationRepository.findOperatorConversationCountsRaw().map { row ->
            val operatorName = row[0] as String
            val conversationCount = (row[1] as Number).toLong()
            OperatorConversationDto(operatorName, conversationCount)
        }
    }

}