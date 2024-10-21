package tgbot.service.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*
import tgbot.service.model.NotificationDto
import tgbot.service.model.UserDto

@FeignClient(name = "notification-service", url = "\${notification-service.url}")
interface NotificationFeignClient {

    @PostMapping("")
    fun createNotification(@RequestBody notificationDto: NotificationDto): NotificationDto

    @PostMapping("/users")
    fun createUser(@RequestBody userDto: UserDto)

    @GetMapping("/user/{userId}")
    fun getNotificationsByUserId(@PathVariable userId: Long): List<NotificationDto>

    @GetMapping("/search")
    fun searchNotifications(@RequestParam text: String, @RequestParam day: String): List<NotificationDto>

    @GetMapping("/user/{userId}/day/{day}")
    fun searchNotificationsByUserAndDay(@PathVariable userId: Long, @PathVariable day: Int): List<NotificationDto>

}


