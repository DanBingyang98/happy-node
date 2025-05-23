package com.danby.happynode.framework.biz.operationlog.aspect;

import com.danby.happynode.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.Arrays;
import java.util.stream.Collectors;

@Aspect
@Slf4j
public class ApiOperationLogAspect {
    /**
     * 以自定义 @ApiOperationLog 注解为切点，凡是添加 @ApiOperationLog 的方法，都会执行环绕中的代码
     */
    @Pointcut("@annotation(com.danby.happynode.framework.biz.operationlog.aspect.ApiOperationLog)")
    public void apiOperationLog() {
    }

    @Around("apiOperationLog()")
    // 定义一个环绕通知，用于记录API操作日志
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取当前时间
        long startTime = System.currentTimeMillis();

        // 获取目标类的简单名称
        String className = joinPoint.getTarget().getClass().getSimpleName();
        // 获取目标方法名称
        String methodName = joinPoint.getSignature().getName();
        // 获取目标方法的参数
        Object[] args = joinPoint.getArgs();
        // 将参数转换为JSON字符串
        String argsJsonStr = Arrays.stream(args).map(JsonUtils::toJsonString).collect(Collectors.joining(","));
        if (argsJsonStr.endsWith(",")) {
            argsJsonStr = argsJsonStr.substring(0, argsJsonStr.length() - 1);
        }
        String description = getApiOperationLogDescription(joinPoint);

        // 记录API操作日志
        log.info("====== 请求开始: [{}], 入参: {}, 请求类: {}, 请求方法: {}",
                description, argsJsonStr, className, methodName);
        // 执行目标方法
        Object result = joinPoint.proceed();
        long executionTime = System.currentTimeMillis() - startTime;
        // 打印出参等相关信息
        log.info("====== 请求结束: [{}], 耗时: {}ms, 出参: {}",
                description, executionTime, JsonUtils.toJsonString(result));
        return result;
    }

    private String getApiOperationLogDescription(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod().getAnnotation(ApiOperationLog.class).description();
    }
}
