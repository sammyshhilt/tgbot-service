package tgbot.service.aspect



import okhttp3.internal.concurrent.TaskRunner.Companion.logger
import org.springframework.stereotype.Component

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.*
import tgbot.service.aspect.annotation.CheckRoleAnnotation
import tgbot.service.session.context.UserSessionContext

@Aspect
@Component
class SecurityAspect {

    @Pointcut("@annotation(tgbot.service.aspect.annotation.CheckRoleAnnotation)")
    fun checkRoleAnnotation() {}

    @Before("@annotation(checkRoleAnnotation)")
    fun checkRoleBefore(joinPoint: JoinPoint, checkRoleAnnotation: CheckRoleAnnotation) {
        val userSession = UserSessionContext.getCurrentSession()

        if (userSession == null) {
            throw SecurityException("User session not found")
        }

        if (userSession.role != checkRoleAnnotation.roles) {
            throw SecurityException("User does not have required role: ${checkRoleAnnotation.roles}")
        }

        println("User with role ${userSession.role} has permission to access ${joinPoint.signature.name}")
    }

    @AfterThrowing(pointcut = "checkRoleAnnotation()", throwing = "exception")
    fun handleSecurityException(exception: Exception) {
        println("Security exception caught: ${exception.message}")
    }

    @Around("execution(* tgbot.service.client.NotificationFeignClient.*(..))")
    fun logAroundSpecificMethods(joinPoint: ProceedingJoinPoint) {
        val methodName = joinPoint.signature.name
        val args = joinPoint.args.joinToString()
        logger.info("AOP logging\n\tBefore executing $methodName with args: $args")

        val result = joinPoint.proceed()

        logger.info("AOP logging\n\tAfter executing $methodName, result: $result")

    }
}
