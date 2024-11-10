package tgbot.service.service

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.http.ResponseEntity

import tgbot.service.client.NotificationFeignClient
import org.springframework.stereotype.Service
import tgbot.service.aspect.annotation.CheckRoleAnnotation
import tgbot.service.bot.TgBot.Companion.logger
import tgbot.service.config.Role
import tgbot.service.model.NotificationDto
import tgbot.service.model.UserDto

@Service
class TgBotService(private val notificationFeignClient: NotificationFeignClient)
{

    @PostConstruct
    fun onInit() {
        logger.info { "TgBotService initialized and ready to handle notifications and users." }

    }

    @PreDestroy
    fun onDestroy() {
        logger.info { "Shutting down TgBotService and cleaning up resources." }

    }

    @CheckRoleAnnotation(roles = Role.ADMIN)
    fun getAllUsersExcludingRequester(requesterId: Long): List<UserDto> {
        return notificationFeignClient.getAllUsersExcludingRequester(requesterId)
    }

    @CheckRoleAnnotation(roles = Role.ADMIN)
    fun deleteUserByNickname(nickname: String): ResponseEntity<String> {
        return notificationFeignClient.deleteUser(nickname)
    }
//    fun deleteUser(userId: Long): String {
//        try {
//            val response = notificationFeignClient.deleteUser(userId)
//
//            return if (response.statusCode.is2xxSuccessful) {
//                val successMessage = "User with ID $userId deleted successfully"
//                logger.info(successMessage)
//                successMessage
//            } else {
//                val notFoundMessage = "User with ID $userId not found"
//                logger.warn(notFoundMessage)
//                notFoundMessage
//            }
//        } catch (e: Exception) {
//            val errorMessage = "An error occurred while deleting the user with ID $userId"
//            logger.error(errorMessage, e)
//            return errorMessage
//        }
//    }



//    @CheckRoleAnnotation(roles = Role.USER)
    fun createNotification(notificationDto: NotificationDto): NotificationDto {
        return notificationFeignClient.createNotification(notificationDto)
    }


    //@CheckRoleAnnotation(roles = Role.USER)
    fun createUserIfNotExists(userId: Long, nickname: String) {
        val userDto = UserDto(id = userId, nickname = nickname)
        try {
            logger.info { "Creating of user $userDto"}
            notificationFeignClient.createUser(userDto)
            logger.info { "User created successfully with FeignClient" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create user through FeignClient" }
        }
    }

    //@CheckRoleAnnotation(roles = Role.USER)
    fun getUserNotifications(userId: Long): List<NotificationDto> {
        return notificationFeignClient.getNotificationsByUserId(userId)
    }

    //@CheckRoleAnnotation(roles = Role.USER)
    fun searchNotifications(text: String, day: String): List<NotificationDto> {
        return notificationFeignClient.searchNotifications(text, day)
    }

    //@CheckRoleAnnotation(roles = Role.USER)
    fun searchNotificationsByUserAndDay(userId: Long, day: Int): List<NotificationDto> {
        return notificationFeignClient.searchNotificationsByUserAndDay(userId, day)
    }

}
