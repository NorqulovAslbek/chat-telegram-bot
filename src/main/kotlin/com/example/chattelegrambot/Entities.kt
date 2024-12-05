import com.example.chattelegrambot.Language
import com.example.chattelegrambot.SenderType
import com.example.chattelegrambot.Status
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

@Entity
class Users(
    @Column(nullable = false)
    val chatId: Long,
    @Column(nullable = false, length = 50)
    var fullName: String,
    @Column(nullable = false)
    var phone: String,
    @Column(nullable = false)
    var langType: Language,
    @Column(nullable = false)
    var status: Status
) : BaseEntity()


@Entity
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
    var isBusy: Boolean = false,
    @Column
    var rating: Double? = null,
) : BaseEntity()


@Entity
data class Queue(
    @ManyToOne
    val users: Users,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val language: Language,
) : BaseEntity()


@Entity
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


@Entity
data class Message(
    @ManyToOne
    val conversation: Conversation,
    @Column(nullable = false)
    val senderId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val senderType: SenderType,
    @Column(nullable = false)
    val content: String,

    ) : BaseEntity()


@Entity
data class Rating(
    @ManyToOne
    val conversation: Conversation,
    @ManyToOne
    val users: Users,
    @ManyToOne
    val operator: Operator,
    @Column(nullable = false)
    val score: Int, // 1 to 5
) : BaseEntity()


@Entity
data class WorkSession(
    @ManyToOne
    var operator: Operator,
    var workHour: Int,
    @Column(nullable = false)
    var salary: BigDecimal,
    var endDate: Date
) : BaseEntity()
