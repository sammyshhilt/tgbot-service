package tgbot.service.session

import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import tgbot.service.config.Role

@Component
@Scope("prototype")
class UserSession(val userId: Long, val nickname: String, var role: Role = Role.USER) {

    fun info(): String {
        return("${userId.toString().trimEnd()}\t---\t${nickname}\t---\t${role}")
    }
}