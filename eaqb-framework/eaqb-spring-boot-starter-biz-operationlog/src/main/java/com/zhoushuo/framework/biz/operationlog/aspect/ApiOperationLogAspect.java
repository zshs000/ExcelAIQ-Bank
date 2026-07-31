package com.zhoushuo.framework.biz.operationlog.aspect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.zhoushuo.framework.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;



@Aspect
@Slf4j
public class ApiOperationLogAspect {

    /**
     * 需要脱敏的 JSON 字段名集合。
     */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "code", "oldPassword", "newPassword", "pwd", "secret", "token", "verificationCode"
    );

    /** 以自定义 @ApiOperationLog 注解为切点，凡是添加 @ApiOperationLog 的方法，都会执行环绕中的代码 */
    @Pointcut("@annotation(com.zhoushuo.framework.biz.operationlog.aspect.ApiOperationLog)")
    public void apiOperationLog() {}

    /**
     * 环绕
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("apiOperationLog()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // 请求开始时间
        long startTime = System.currentTimeMillis();

        // 获取被请求的类和方法
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        // 请求入参
        Object[] args = joinPoint.getArgs();
        // 入参转 JSON 字符串
        String argsJsonStr = Arrays.stream(args).map(toJsonStr()).collect(Collectors.joining(", "));

        // 功能描述信息
        String description = getApiOperationLogDescription(joinPoint);

        // 打印请求相关参数
        log.info("====== 请求开始: [{}], 入参: {}, 请求类: {}, 请求方法: {} =================================== ",
                description, argsJsonStr, className, methodName);

        // 执行切点方法
        Object result = joinPoint.proceed();

        // 执行耗时
        long executionTime = System.currentTimeMillis() - startTime;

        // 打印出参等相关信息
        log.info("====== 请求结束: [{}], 耗时: {}ms, 出参: {} =================================== ",
                description, executionTime, JsonUtils.toJsonString(result));

        return result;
    }

    /**
     * 获取注解的描述信息
     * @param joinPoint
     * @return
     */
    private String getApiOperationLogDescription(ProceedingJoinPoint joinPoint) {
        // 1. 从 ProceedingJoinPoint 获取 MethodSignature
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 2. 使用 MethodSignature 获取当前被注解的 Method
        Method method = signature.getMethod();

        // 3. 从 Method 中提取 LogExecution 注解
        ApiOperationLog apiOperationLog = method.getAnnotation(ApiOperationLog.class);

        // 4. 从 LogExecution 注解中获取 description 属性
        return apiOperationLog.description();
    }

    /**
     * 转 JSON 字符串
     * @return
     */
    //todo 暂时没有解决出参文件序列化问题
    private Function<Object, String> toJsonStr() {
        return obj -> {
            if (obj != null && obj.getClass().getName().contains("MultipartFile")) {
                return "\"[MultipartFile]\"";
            }
            return maskSensitive(JsonUtils.toJsonString(obj));
        };
    }

    /**
     * 将 JSON 字符串解析为树，遍历并替换敏感字段的值，再序列化回去。
     * 相比正则替换，可以正确处理转义引号、非字符串值、嵌套对象和数组。
     */
    private String maskSensitive(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            maskNode(root);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // 解析失败（非标准 JSON）时降级返回原字符串
            return json;
        }
    }

    private static void maskNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fields().forEachRemaining(entry -> {
                if (SENSITIVE_FIELDS.contains(entry.getKey())) {
                    entry.setValue(TextNode.valueOf("****"));
                } else {
                    maskNode(entry.getValue());
                }
            });
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            arr.forEach(ApiOperationLogAspect::maskNode);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

}