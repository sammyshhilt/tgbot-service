package tgbot.service

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.openfeign.EnableFeignClients

@SpringBootApplication
@EnableFeignClients
class TgBotServiceApplication

fun main(args: Array<String>) {
    SpringApplication.run(TgBotServiceApplication::class.java, *args)
}

