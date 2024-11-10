package tgbot.service.session.context

import tgbot.service.config.Role
import tgbot.service.session.UserSession

object UserSessionContext{
    private val currentUserSession: ThreadLocal<UserSession> = ThreadLocal()
    private val admins = listOf("wwwyacheese", "anotherAdmin")

    fun setCurrentSession(userSession: UserSession) {
        currentUserSession.set(userSession)
    }



    fun isAdmin(userSession: UserSession){
        if (admins.contains(userSession.nickname)){
            userSession.role = Role.ADMIN
        }
    }

    fun getCurrentSession(): UserSession? {
        return currentUserSession.get()
    }

    fun clear() {
        currentUserSession.remove()
    }
}
