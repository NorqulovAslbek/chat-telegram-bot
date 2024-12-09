package com.example.chattelegrambot

import BaseEntity
import Conversation
import Message
import Operator
import Queue
import Rating
import Users
import WorkSession
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@NoRepositoryBean
interface BaseRepository<T : BaseEntity> : JpaRepository<T, Long>, JpaSpecificationExecutor<T> {
    fun findByIdAndDeletedFalse(id: Long): T?
    fun trash(id: Long): T?
    fun trashList(ids: List<Long>): List<T?>
    fun findAllNotDeleted(): List<T>
    fun findAllNotDeleted(pageable: Pageable): List<T>
    fun findAllNotDeletedForPageable(pageable: Pageable): Page<T>
    fun saveAndRefresh(t: T): T
}

class BaseRepositoryImpl<T : BaseEntity>(
    entityInformation: JpaEntityInformation<T, Long>,
    private val entityManager: EntityManager
) : SimpleJpaRepository<T, Long>(entityInformation, entityManager), BaseRepository<T> {

    val isNotDeletedSpecification = Specification<T> { root, _, cb -> cb.equal(root.get<Boolean>("deleted"), false) }

    override fun findByIdAndDeletedFalse(id: Long) = findByIdOrNull(id)?.run { if (deleted) null else this }

    @Transactional
    override fun trash(id: Long): T? = findByIdAndDeletedFalse(id)?.run {
        deleted = true
        save(this)
    }

    override fun findAllNotDeleted(): List<T> = findAll(isNotDeletedSpecification)
    override fun findAllNotDeleted(pageable: Pageable): List<T> = findAll(isNotDeletedSpecification, pageable).content
    override fun findAllNotDeletedForPageable(pageable: Pageable): Page<T> =
        findAll(isNotDeletedSpecification, pageable)

    override fun trashList(ids: List<Long>): List<T?> = ids.map { trash(it) }

    @Transactional
    override fun saveAndRefresh(t: T): T {
        return save(t).apply { entityManager.refresh(this) }
    }
}

@Repository
interface UserRepository : BaseRepository<Users> {

    @Query(
        """
        select u from users u
         where u.deleted = false
         and u.chatId =?1
    """
    )
    fun findUsersByChatId(chatId: Long): Users?

}//

@Repository
interface OperatorRepository : BaseRepository<Operator> {
    @Query(
        """
        select o from operators o
         where o.deleted = false
         and o.chatId =?1
    """
    )
    fun findOperatorByChatId(chatId: Long): Operator?


    @Modifying
    @Query(
        """
        update operators o set o.status = ?2
        where o.chatId = ?1 and o.deleted = false
    """
    )
    fun changeStatus(chatId: Long, status: Status)

    @Query(
        """
        select o from operators o
        where o.deleted = false and o.language = ?1 and o.status = 'OPERATOR_ACTIVE'
        order by o.id
        limit 1
    """
    )
    fun findAvailableOperator(language: Language): Operator?


}

@Repository
interface WorkSessionRepository : BaseRepository<WorkSession> {


}

@Repository
interface QueueRepository : BaseRepository<Queue> {
    @Query(
        """
        select q.users from queues q
        where q.deleted = false and q.language = ?1
        order by q.createdDate asc
        limit 1
    """
    )
    fun findFirstUserFromQueue(language: Language): Users?


    @Query("""
        select q from queues q
        where q.users.chatId = ?1 and q.deleted = false
    """)
    fun existUser(chatId: Long) : Queue?

}

@Repository
interface RatingRepository : BaseRepository<Rating> {
    @Query(
        """
        select r from ratings r 
        where r.users.chatId = ?1 and r.score is null
    """
    )
    fun findRating(chatId: Long): Rating?
}

@Repository
interface MessageRepository : BaseRepository<Message> {

    @Query(
        """
        select m.content from messages m
        where m.senderId = ?1 and m.conversation is null and m.deleted = false
    """
    )
    fun findMessagesByUser(chatId: Long): List<String>?

    @Query(
        """
        select m from messages m
        where m.senderId = ?1 and m.conversation is null and m.content = ?2 and  m.deleted = false
    """
    )
    fun findMessageByUser(chatId: Long, content: String): Message?

    @Modifying
    @Query("""
        update messages m set m.deleted = true
        where m.senderId = ?1 and m.conversation is null
    """)
    fun deleteMessagesByUser(chatId: Long)

}

@Repository
interface ConversationRepository : BaseRepository<Conversation> {

    @Query(
        """
        select c from conversations c
        where c.operator.chatId = ?1 and c.endDate is null and c.deleted = false
    """
    )
    fun findConversationByOperator(chatId: Long): Conversation?


}
