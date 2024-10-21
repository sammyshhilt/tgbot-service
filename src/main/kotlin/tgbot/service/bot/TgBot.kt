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
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import tgbot.service.model.NotificationDto
import tgbot.service.service.TgBotService
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

@Component
class TgBot(
    @Value("\${bot.token}")
    private val token: String,
    private val tgBotService: TgBotService
) {

    companion object : KLogging()

    // Сохраняем выбранный день для каждого пользователя
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

                    // Логируем информацию о пользователе
                    logger.info { "Получена команда /start от пользователя: $nickname (chatId: $chatId)" }

                    bot.sendMessage(ChatId.fromId(chatId), "Добро пожаловать! Это бот для операций с заметками, для того чтобы узнать, что может этот бот, введите /help.")

                    // Создание пользователя при первом взаимодействии
                    tgBotService.createUserIfNotExists(chatId, nickname)
                }

                command("help") {
                    val chatId = update.message?.chat?.id ?: return@command
                    val helpMessage = """
                        Доступные команды:
                        
                        /start - Начать работу с ботом.
                        /new - Создать новую заметку. Сначала выберите дату, затем введите текст заметки.
                        /search [текст] - Найти заметки по ключевому слову в тексте.
                        /find - найти заметки в календаре
                        /help - Вывести информацию обо всех командах.
                    """.trimIndent()

                    bot.sendMessage(ChatId.fromId(chatId), helpMessage)
                }

                command("new") {
                    val chatId = update.message?.chat?.id ?: return@command

                    // Логируем запрос на создание новой заметки
                    logger.info { "Получена команда /new от пользователя с chatId: $chatId" }

                    // Отображение календаря для выбора даты
                    showCalendar(chatId, bot)
                }

                command("find") {
                    val chatId = update.message?.chat?.id ?: return@command

                    // Логируем запрос на поиск заметок по дню
                    logger.info { "Получена команда /getByDay от пользователя с chatId: $chatId" }

                    // Отображаем календарь для выбора дня
                    showCalendar(chatId, bot, isSearch = true)
                }


                command("search") {
                    val chatId = update.message?.chat?.id ?: return@command
                    val queryText = update.message?.text?.removePrefix("/search")?.trim()

                    if (queryText.isNullOrEmpty()) {
                        bot.sendMessage(ChatId.fromId(chatId), "Введите текст для поиска после команды /search.")
                        return@command
                    }

                    logger.info { "Получена команда /search от пользователя с chatId: $chatId, текст поиска: $queryText" }

                    // Выполняем поиск через Feign-клиент
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

                    // Логируем выбор даты
                    logger.info { "Выбрана дата: $callbackData для пользователя с chatId: $chatId" }

                    callbackData?.let {
                        val selectedDay = it.toInt()
                        userSelectedDays[chatId] = selectedDay // Сохраняем выбранный день для пользователя

                        // Проверяем, находится ли пользователь в процессе поиска заметок по дню
                        if (update.callbackQuery?.message?.text == "Выберите дату для поиска заметок:") {
                            // Если да, то вызываем поиск заметок по выбранному дню
                            val searchResults = tgBotService.searchNotificationsByUserAndDay(userId=chatId, day=selectedDay)
                            if (searchResults.isEmpty()) {
                                bot.sendMessage(ChatId.fromId(chatId), "Заметки на день $selectedDay не найдены.")
                            } else {
                                val resultMessages = searchResults.joinToString(separator = "\n") { "${it.day}: ${it.text}" }
                                bot.sendMessage(ChatId.fromId(chatId), "Заметки на день $selectedDay:\n$resultMessages")
                            }
                        } else {
                            // Если это создание новой заметки, то запрашиваем текст заметки
                            requestNoteText(chatId, selectedDay, bot)
                        }
                    }
                }


                text {
                    val chatId = update.message?.chat?.id ?: return@text
                    val messageText = update.message?.text ?: return@text

                    // Если сообщение начинается с "/" — это команда, пропускаем её
                    if (messageText.startsWith("/search")) {
                        logger.info { "Получена команда /search, игнорируем обработку как текст." }
                        return@text
                    }

                    if (messageText.startsWith("/")) {
                        logger.info { "Получена команда $messageText, игнорируем обработку как текст." }
                        return@text
                    }

                    // Логируем текст заметки
                    logger.info { "Получено текстовое сообщение от пользователя с chatId: $chatId: $messageText" }

                    // Получаем ранее выбранную дату для пользователя
                    val day = userSelectedDays[chatId]

                    if (day != null) {
                        // Логируем сохранение заметки
                        logger.info { "Сохранение заметки для дня $day от пользователя с chatId: $chatId" }

                        // Сохраняем заметку
                        val notificationDto = NotificationDto( text = messageText, day = day, userId = chatId)
                        bot.sendMessage(ChatId.fromId(chatId), "Заметка сохранена на день $day.")
                        tgBotService.createNotification(notificationDto)
                        // Убираем сохранённый день, чтобы при следующем сообщении не было ошибок
                        userSelectedDays.remove(chatId)
                    } else {
                        // Если день не был выбран, логируем предупреждение
                        logger.warn { "День не был выбран до ввода текста заметки пользователем с chatId: $chatId" }
                        bot.sendMessage(ChatId.fromId(chatId), "Пожалуйста, выберите дату перед вводом текста заметки.")
                    }
                }
            }
        }

        bot.startPolling()
        return bot
    }

    private fun showCalendar(chatId: Long, bot: Bot, isSearch: Boolean = false) {
        val currentDate = LocalDate.now()
        val today = currentDate.dayOfMonth
        val daysInMonth = currentDate.lengthOfMonth()
        val firstDayOfMonth = currentDate.withDayOfMonth(1)
        val dayOfWeekOfFirstDay = firstDayOfMonth.dayOfWeek.value % 7 // День недели первого дня месяца (0 - воскресенье, 6 - суббота)

        val buttons = mutableListOf<List<InlineKeyboardButton>>()
        var weekButtons = mutableListOf<InlineKeyboardButton>()

        // Добавляем пустые кнопки до первого дня месяца
        for (i in 0 until dayOfWeekOfFirstDay) {
            weekButtons.add(InlineKeyboardButton.CallbackData(" ", "ignore"))
        }

        // Заполняем кнопки днями месяца с учётом сегодняшнего и прошедших дней
        for (day in 1..daysInMonth) {
            val buttonText = when {
                day < today -> "◀ $day" // Прошедшие дни
                day == today -> "🔵 $day" // Сегодняшний день
                else -> day.toString() // Будущие дни
            }

            val button = InlineKeyboardButton.CallbackData(
                text = buttonText,
                callbackData = day.toString()
            )
            weekButtons.add(button)

            // Если собрали неделю (7 кнопок) или дошли до конца месяца — добавляем в общий список
            if (weekButtons.size == 7) {
                buttons.add(weekButtons.toList())
                weekButtons.clear()
            }
        }

        // Добавляем остаток дней, если остались дни в последней неполной неделе
        if (weekButtons.isNotEmpty()) {
            while (weekButtons.size < 7) {
                weekButtons.add(InlineKeyboardButton.CallbackData(" ", "ignore")) // Добавляем пустые кнопки для заполнения недели
            }
            buttons.add(weekButtons.toList())
        }

        val inlineKeyboard = InlineKeyboardMarkup.create(buttons)

        val messageText = if (isSearch) {
            "Выберите дату для поиска заметок:"
        } else {
            "Выберите дату для создания заметки:"
        }

        logger.info { "Показ календаря пользователю с chatId: $chatId" }
        bot.sendMessage(ChatId.fromId(chatId), messageText, replyMarkup = inlineKeyboard)
    }



    private fun requestNoteText(chatId: Long, day: Int, bot: Bot) {
        // Логируем запрос текста заметки
        logger.info { "Запрос текста заметки для дня $day у пользователя с chatId: $chatId" }

        bot.sendMessage(ChatId.fromId(chatId), "Введите текст заметки для дня $day:")
    }
}
