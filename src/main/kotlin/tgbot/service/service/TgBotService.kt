package tgbot.service.service

import tgbot.service.client.NotificationFeignClient
import org.springframework.stereotype.Service
import tgbot.service.bot.TgBot.Companion.logger
import tgbot.service.model.NotificationDto
import tgbot.service.model.UserDto

@Service
class TgBotService(private val notificationFeignClient: NotificationFeignClient) {

    // Создание уведомления через Feign-клиент
    fun createNotification(notificationDto: NotificationDto): NotificationDto {
        return notificationFeignClient.createNotification(notificationDto)
    }

    // Создание пользователя, если его еще нет, через Feign-клиент
    fun createUserIfNotExists(userId: Long, nickname: String) {
        // Feign-клиент делает запрос к сервису Notice для создания пользователя
        val userDto = UserDto(id = userId, nickname = nickname)
        notificationFeignClient.createUser(userDto)
        logger.info { "Creating of user $userDto"}
    }

    // Получение уведомлений пользователя через Feign-клиент
    fun getUserNotifications(userId: Long): List<NotificationDto> {
        return notificationFeignClient.getNotificationsByUserId(userId)
    }

    // Поиск уведомлений по тексту через Feign-клиент
    fun searchNotifications(text: String, day: String): List<NotificationDto> {
        return notificationFeignClient.searchNotifications(text, day)
    }

    fun searchNotificationsByUserAndDay(userId: Long, day: Int): List<NotificationDto> {
        return notificationFeignClient.searchNotificationsByUserAndDay(userId, day)
    }

}
