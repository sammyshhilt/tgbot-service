package tgbot.service.model

data class NotificationDto(
    val text: String,
    val day: Int,
    val userId: Long
)
