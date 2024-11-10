package tgbot.service.aspect.annotation

import tgbot.service.config.Role

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckRoleAnnotation(val roles: Role)