package tgbot.service.bot

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.text
import com.github.kotlintelegrambot.entities.*
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import mu.KLogging
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import tgbot.service.config.Role
import tgbot.service.model.NotificationDto
import tgbot.service.service.TgBotService
import tgbot.service.session.UserSession
import tgbot.service.session.context.UserSessionContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class TgBot(
    @Value("\${bot.token}") private val token: String,
    private val tgBotService: TgBotService,
    //private val userSessionProvider: ObjectProvider<UserSession>
) {
    companion object : KLogging()

    //private val userSessions = ConcurrentHashMap<Long, UserSession>()
    private val userSelectedDays = ConcurrentHashMap<Long, Int>()

    @Bean
    fun getBot(): Bot {
        val bot = bot {
            token = this@TgBot.token
            timeout = 60

            dispatch {
                command("start") {
                    val chatId = update.message?.chat?.id ?: return@command
                    val nickname = update.message?.from?.username ?: "Без имени"

                    startUserSession(chatId, nickname)
                    val userSession = UserSessionContext.getCurrentSession()

                    bot.sendMessage(
                        ChatId.fromId(chatId),
                        "Добро пожаловать! Это бот для операций с заметками, введите /help для списка команд."
                    )

                    if (userSession != null) {

                        try {
                            tgBotService.createUserIfNotExists(chatId, nickname)
                            logger.info{"CurrentUserSession: ${userSession.info()}"}

                        } catch (e: Exception) {
                            logger.error(e) { "Ошибка при вызове createUserIfNotExists для chatId: $chatId, Nickname: $nickname" }
                        }
                    }
                }

                command("help") {
                    val chatId = update.message?.chat?.id ?: return@command
                    val userSession = UserSessionContext.getCurrentSession()
                    var helpMessage = """
                        Доступные команды:
                        
                        /start - Начать работу с ботом.
                        /new - Создать новую заметку. Сначала выберите дату, затем введите текст заметки.
                        /search [текст] - Найти заметки по ключевому слову в тексте.
                        /find - найти заметки в календаре
                        /help - Вывести информацию обо всех командах.
                        /list - список всех Ваших заметок
                    """.trimIndent()

                    if (userSession != null) {
                        if (userSession.role == Role.ADMIN){
                            bot.sendMessage(ChatId.fromId(chatId), "У вас есть права админа: 'ADMIN'")
                            helpMessage = "$helpMessage\n/delete - удалить пользователя за нарушения"
                        }
                    }
                    bot.sendMessage(ChatId.fromId(chatId), helpMessage)
                }

                command("new") {
                    val chatId = update.message?.chat?.id ?: return@command
                    showCalendar(chatId, bot)
                }

                command("find") {
                    val chatId = update.message?.chat?.id ?: return@command
                    showCalendar(chatId, bot, isSearch = true)
                }

                command("delete"){
                    val userSession = UserSessionContext.getCurrentSession()
                    val chatId = update.message?.chat?.id ?: return@command
                    if (userSession != null) {
                        handleDeleteCommand(chatId, bot, userSession)
                    }
                }

                command("search") {
                    val chatId = update.message?.chat?.id ?: return@command
                    val queryText = update.message?.text?.removePrefix("/search")?.trim()

                    if (queryText.isNullOrEmpty()) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите текст для поиска после команды /search.")
                        return@command
                    }

                    val searchResults = tgBotService.searchNotifications(text = queryText, day = "")
                    if (searchResults.isEmpty()) {
                        bot.sendMessage(ChatId.fromId(chatId), "Заметки не найдены по запросу: \"$queryText\".")
                    } else {
                        val resultMessages = searchResults.joinToString(separator = "\n") { "${it.day}: ${it.text}" }
                        bot.sendMessage(ChatId.fromId(chatId), "Результаты поиска:\n$resultMessages")
                    }
                }

                command("list") {
                    val chatId = update.message?.chat?.id ?: return@command

                    val notifications = tgBotService.getUserNotifications(chatId)
                    if (notifications.isEmpty()) {
                        bot.sendMessage(ChatId.fromId(chatId), "У вас нет сохраненных заметок.")
                    } else {
                        notifications.forEach { note ->
                            bot.sendMessage(ChatId.fromId(chatId), "Заметка: ${note.text} на день ${note.day}")
                        }
                    }
                }

                callbackQuery {
                    val callbackData = update.callbackQuery?.data
                    val chatId = update.callbackQuery?.message?.chat?.id ?: return@callbackQuery

                    callbackData?.let { data ->

                        if (data.toIntOrNull()!= null){

                            val selectedDay = data.toInt()
                            userSelectedDays[chatId] = selectedDay

                            if (update.callbackQuery?.message?.text == "Выберите дату для поиска заметок:") {
                                val searchResults = tgBotService.searchNotificationsByUserAndDay(userId=chatId, day=selectedDay)
                                if (searchResults.isEmpty()) {
                                    bot.sendMessage(ChatId.fromId(chatId), "Заметки на день $selectedDay не найдены.")
                                }
                                else {
                                    val resultMessages = searchResults.joinToString(separator = "\n") { "${it.day}: ${it.text}" }
                                    bot.sendMessage(ChatId.fromId(chatId), "Заметки на день $selectedDay:\n$resultMessages")
                                }
                            }
                            else {
                                requestNoteText(chatId, selectedDay, bot)
                            }
                        }

                    else{ handleDeleteUserCallback(chatId, data, bot) }

                    }

                }

                text {
                    val chatId = update.message?.chat?.id ?: return@text
                    val messageText = update.message?.text ?: return@text

                    if (messageText.startsWith("/")) return@text

                    val day = userSelectedDays[chatId]

                    if (day != null) {
                        val notificationDto = NotificationDto(text = messageText, day = day, userId = chatId)
                        bot.sendMessage(ChatId.fromId(chatId), "Заметка сохранена на день $day.")
                        tgBotService.createNotification(notificationDto)
                        userSelectedDays.remove(chatId)
                    } else {
                        bot.sendMessage(ChatId.fromId(chatId), "Пожалуйста, выберите дату перед вводом текста заметки.")
                    }
                }
            }
        }

        bot.startPolling()
        return bot
    }

    private fun startUserSession(chatId: Long, nickname: String) {
        val userSession = UserSession(chatId, nickname, Role.USER)
        UserSessionContext.setCurrentSession(userSession)
        UserSessionContext.isAdmin(userSession)

    }

    private fun showCalendar(chatId: Long, bot: Bot, isSearch: Boolean = false) {
        val currentDate = LocalDate.now()
        val today = currentDate.dayOfMonth
        val daysInMonth = currentDate.lengthOfMonth()
        val firstDayOfMonth = currentDate.withDayOfMonth(1)
        val dayOfWeekOfFirstDay = firstDayOfMonth.dayOfWeek.value % 7

        val buttons = mutableListOf<List<InlineKeyboardButton>>()
        var weekButtons = mutableListOf<InlineKeyboardButton>()

        for (i in 0 until dayOfWeekOfFirstDay) {
            weekButtons.add(InlineKeyboardButton.CallbackData(" ", "ignore"))
        }

        for (day in 1..daysInMonth) {
            val buttonText = when {
                day < today -> "◀ $day"
                day == today -> "🔵 $day"
                else -> day.toString()
            }

            val button = InlineKeyboardButton.CallbackData(
                text = buttonText,
                callbackData = day.toString()
            )
            weekButtons.add(button)

            if (weekButtons.size == 7) {
                buttons.add(weekButtons.toList())
                weekButtons.clear()
            }
        }

        if (weekButtons.isNotEmpty()) {
            while (weekButtons.size < 7) {
                weekButtons.add(InlineKeyboardButton.CallbackData(" ", "ignore"))
            }
            buttons.add(weekButtons.toList())
        }

        val inlineKeyboard = InlineKeyboardMarkup.create(buttons)

        val messageText = if (isSearch) {
            "Выберите дату для поиска заметок:"
        } else {
            "Выберите дату для создания заметки:"
        }

        bot.sendMessage(ChatId.fromId(chatId), messageText, replyMarkup = inlineKeyboard)
    }

    private fun requestNoteText(chatId: Long, day: Int, bot: Bot) {
        bot.sendMessage(ChatId.fromId(chatId), "Введите текст заметки для дня $day:")
    }

    // DELETE

    private fun handleDeleteUserCallback(chatId: Long, nickname: String, bot: Bot) {
        val response = tgBotService.deleteUserByNickname(nickname)
        val message = if (response.statusCode.is2xxSuccessful) {
            "Пользователь $nickname успешно удален."
        } else {
            "Ошибка при удалении пользователя $nickname."
        }
        bot.sendMessage(ChatId.fromId(chatId), message)
    }



    private fun handleDeleteCommand(chatId: Long, bot: Bot, userSession: UserSession) {
        if (checkUserRole(userSession)){
        val users = tgBotService.getAllUsersExcludingRequester(chatId)
        if (users.isEmpty()) {
            bot.sendMessage(ChatId.fromId(chatId), "Нет пользователей для удаления.")
            return
        }

        val buttons = users.map { user ->
            listOf(InlineKeyboardButton.CallbackData(user.nickname, user.nickname))
        }

        val inlineKeyboard = InlineKeyboardMarkup.create(buttons)
        bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "Выберите пользователя для удаления:",
            replyMarkup = inlineKeyboard
        )
        }
        else{bot.sendMessage(chatId = ChatId.fromId(chatId), "You have not permissions for this command...")}
    }

    private fun checkUserRole(userSession: UserSession): Boolean {
        logger.info{"Attempt to have access to admin methods: ${userSession.info()}"}
        return userSession.role == Role.ADMIN
    }

}
