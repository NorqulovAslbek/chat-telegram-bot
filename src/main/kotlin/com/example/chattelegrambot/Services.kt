import org.springframework.stereotype.Service


interface MyService {
    fun existUser(userChatId: Long): Boolean
    fun existOperator(operatorChatId: Long) : Boolean
    fun addUser()
}