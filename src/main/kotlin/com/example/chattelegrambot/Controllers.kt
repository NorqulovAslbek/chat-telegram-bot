import com.example.chattelegrambot.UsersStep
import org.springframework.beans.factory.annotation.Value
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.objects.Update
import org.springframework.stereotype.Component


class MyBotController : TelegramLongPollingBot() {



    override fun getBotUsername(): String {
        return "@chat_telegram_1_0_bot"
    }

    override fun getBotToken(): String {
        return "7923535042:AAHgoQ0uf1h3zxHnidCSWBz7iszFtIbeKRA"
    }

    override fun onUpdateReceived(update: Update?) {
//        when (update?.message?.text) {
//            "/start" -> // USER DATABASEDA  BOSA STEPI USER_WRITE_MESSAGE ga otadi yoq bosa
//        // OPERATOR DATABASEGA BORADI BOR BOSA OPERATOR_START_WORK STEPIGA OTADI
//        // AGAR BUNDAYAM YO BOSA USER_START STEPIGA OTADI
//        }
    }
}


class MyUserHandleChatController() {

}

