package com.example.chattelegrambot

import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.math.BigDecimal
import java.util.*


@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
class BaseEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @CreatedDate @Temporal(TemporalType.TIMESTAMP)
    var createdDate: Date? = null,
    @LastModifiedDate @Temporal(TemporalType.TIMESTAMP)
    var modifiedDate: Date? = null, //ozgartirilgan vaqt
    @ColumnDefault(value = "false")
    var deleted: Boolean = false
)

@Entity(name = "users")
class Users(
    @Column(nullable = false)
    val chatId: Long,
    @Column(nullable = false, length = 50)
    var fullName: String,
    @Column(nullable = false)
    var phone: String,
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var langType: Language,
) : BaseEntity()


@Entity(name = "operators")
data class Operator(
    @Column(nullable = false)
    val chatId: Long,
    @Column(nullable = false)
    val fullName: String,
    @Column(nullable = false)
    val phone: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val language: Language,
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: Status = Status.OPERATOR_INACTIVE,
) : BaseEntity()


@Entity(name = "queues")
data class Queue(
    @ManyToOne
    val users: Users,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val language: Language,
) : BaseEntity()


@Entity(name = "conversations")
data class Conversation(
    @ManyToOne
    val users: Users,
    @ManyToOne
    val operator: Operator,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val language: Language,
    var endDate: Date? = null
) : BaseEntity()


@Entity(name = "messages")
data class Message(
    @ManyToOne
    var conversation: Conversation?,
    @Column(nullable = false)
    val senderId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val senderType: SenderType,
    @Column(nullable = false)
    val content: String,
    @Column(nullable = false)
    val messageId: Int
) : BaseEntity()


@Entity(name = "ratings")
data class Rating(
    @OneToOne
    val conversation: Conversation,
    @ManyToOne
    val users: Users,
    @ManyToOne
    val operator: Operator,
    var score: Int? = null,
) : BaseEntity()


@Entity(name = "workSessions")
data class WorkSession(
    @ManyToOne
    var operator: Operator,
    var workHour: Int?,
    var salary: BigDecimal?,
    var endDate: Date?
) : BaseEntity()