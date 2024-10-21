package tgbot.service

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
@EnableFeignClients(basePackages = ["tgbot.service.client"])
@EntityScan("tgbot.service.model")
//@EnableJpaRepositories("tgbot.service.repository")// Включение поддержки Feign-клиентов
class TgBotApplication

fun main(args: Array<String>) {
    runApplication<TgBotApplication>(*args)
}
