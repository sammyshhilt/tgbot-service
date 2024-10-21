package tgbot.service.model

import jakarta.persistence.*


@Entity
data class UserEntity(

    @Id
    @Column(unique = true, nullable = false) // Уникальное и не нулевое поле для userId
    val id: Long, // userId из Telegram

    val nickname: String // Никнейм пользователя
)