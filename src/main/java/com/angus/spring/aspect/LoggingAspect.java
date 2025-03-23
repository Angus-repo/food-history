package com.angus.spring.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @Before("execution(* com.example.foodhistory.controller..*(..))")
    public void logBefore(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Object[] logArgs = new Object[args.length];
        
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof StandardMultipartHttpServletRequest) {
                logArgs[i] = "[MultipartRequest]";
            } else if (args[i] instanceof MultipartFile) {
                MultipartFile file = (MultipartFile) args[i];
                logArgs[i] = String.format("[File:%s, Size:%d]", 
                    file.getOriginalFilename(), 
                    file.getSize());
            } else {
                logArgs[i] = args[i];
            }
        }
        
        logger.info("Entering method: {} with arguments: {}", 
                   joinPoint.getSignature().toShortString(), 
                   logArgs);
    }

    @AfterReturning(pointcut = "execution(* com.example.foodhistory.controller..*(..))", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        logger.info("Exiting method: {} with result: {}", joinPoint.getSignature().toShortString(), result);
    }

    @Around("execution(* com.example.foodhistory.controller..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object proceed = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - start;
        logger.info("duration {} ms", executionTime);
        return proceed;
    }
}