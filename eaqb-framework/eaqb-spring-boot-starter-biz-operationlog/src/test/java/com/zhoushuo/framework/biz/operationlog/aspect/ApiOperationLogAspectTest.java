package com.zhoushuo.framework.biz.operationlog.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ApiOperationLogAspect} 脱敏逻辑回归测试。
 * 通过反射调用私有方法，验证敏感字段在各种场景下的脱敏表现。
 */
@DisplayName("操作日志脱敏")
class ApiOperationLogAspectTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Aspect 实例，用于调用非静态私有方法 */
    private static final ApiOperationLogAspect ASPECT = new ApiOperationLogAspect();

    private static final Method MASK_SENSITIVE;
    private static final Method MASK_NODE;
    private static final Method TO_JSON_STR;

    static {
        try {
            MASK_SENSITIVE = ApiOperationLogAspect.class.getDeclaredMethod("maskSensitive", String.class);
            MASK_SENSITIVE.setAccessible(true);
            MASK_NODE = ApiOperationLogAspect.class.getDeclaredMethod("maskNode", JsonNode.class);
            MASK_NODE.setAccessible(true);
            TO_JSON_STR = ApiOperationLogAspect.class.getDeclaredMethod("toJsonStr");
            TO_JSON_STR.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private String mask(Object obj) throws Exception {
        Function<Object, String> toJsonStr = (Function<Object, String>) TO_JSON_STR.invoke(ASPECT);
        return toJsonStr.apply(obj);
    }

    /** 对 JSON 字符串执行 maskSensitive 并返回结果 */
    private String maskJson(String json) throws Exception {
        return (String) MASK_SENSITIVE.invoke(ASPECT, json);
    }

    // ======================== 基础脱敏 ========================

    @Nested
    @DisplayName("单层字段脱敏")
    class SingleField {

        @Test
        @DisplayName("password 脱敏为 ****")
        void maskPassword() throws Exception {
            String result = maskJson("{\"password\":\"abc123\"}");

            assertTrue(result.contains("\"password\":\"****\""));
            assertFalse(result.contains("abc123"));
        }

        @Test
        @DisplayName("code 验证码脱敏")
        void maskCode() throws Exception {
            String result = maskJson("{\"code\":\"5678\"}");
            assertTrue(result.contains("\"code\":\"****\""));
        }

        @Test
        @DisplayName("token 脱敏")
        void maskToken() throws Exception {
            String result = maskJson("{\"token\":\"eyJhbGciOiJIUzI1NiJ9.xxx\"}");
            assertTrue(result.contains("\"token\":\"****\""));
        }

        @Test
        @DisplayName("oldPassword 脱敏")
        void maskOldPassword() throws Exception {
            String result = maskJson("{\"oldPassword\":\"old123\"}");
            assertTrue(result.contains("\"oldPassword\":\"****\""));
        }

        @Test
        @DisplayName("newPassword 脱敏")
        void maskNewPassword() throws Exception {
            String result = maskJson("{\"newPassword\":\"new456\"}");
            assertTrue(result.contains("\"newPassword\":\"****\""));
        }

        @Test
        @DisplayName("pwd 脱敏")
        void maskPwd() throws Exception {
            String result = maskJson("{\"pwd\":\"mypwd\"}");
            assertTrue(result.contains("\"pwd\":\"****\""));
        }

        @Test
        @DisplayName("secret 脱敏")
        void maskSecret() throws Exception {
            String result = maskJson("{\"secret\":\"sk-live-xxx\"}");
            assertTrue(result.contains("\"secret\":\"****\""));
        }

        @Test
        @DisplayName("verificationCode 脱敏")
        void maskVerificationCode() throws Exception {
            String result = maskJson("{\"verificationCode\":\"912345\"}");
            assertTrue(result.contains("\"verificationCode\":\"****\""));
        }

        @Test
        @DisplayName("非敏感字段保留原值")
        void preserveNormalField() throws Exception {
            String result = maskJson("{\"phone\":\"13800138000\",\"username\":\"张三\"}");

            assertTrue(result.contains("\"phone\":\"13800138000\""));
            assertTrue(result.contains("\"username\":\"张三\""));
        }

        @Test
        @DisplayName("混合敏感与非敏感字段")
        void mixedFields() throws Exception {
            String result = maskJson("{\"phone\":\"13800138000\",\"password\":\"secret123\",\"nickname\":\"小明\"}");

            assertTrue(result.contains("\"phone\":\"13800138000\""));
            assertTrue(result.contains("\"password\":\"****\""));
            assertTrue(result.contains("\"nickname\":\"小明\""));
            assertFalse(result.contains("secret123"));
        }
    }

    // ======================== 特殊值 ========================

    @Nested
    @DisplayName("特殊值处理")
    class SpecialValues {

        @Test
        @DisplayName("敏感字段数字值脱敏")
        void maskNumericCode() throws Exception {
            String result = maskJson("{\"code\":123456}");
            assertTrue(result.contains("\"code\":\"****\""));
            assertFalse(result.contains("123456"));
        }

        @Test
        @DisplayName("null 值敏感字段同样脱敏")
        void maskNullSensitive() throws Exception {
            String result = maskJson("{\"password\":null}");
            assertTrue(result.contains("\"password\":\"****\""));
        }

        @Test
        @DisplayName("空字符串脱敏")
        void maskEmptyString() throws Exception {
            String result = maskJson("{\"code\":\"\"}");
            assertTrue(result.contains("\"code\":\"****\""));
            assertFalse(result.contains("\"code\":\"\""));
        }
    }

    // ======================== 嵌套结构 ========================

    @Nested
    @DisplayName("嵌套结构")
    class NestedStructure {

        @Test
        @DisplayName("嵌套对象内敏感字段脱敏")
        void nestedObject() throws Exception {
            String result = maskJson("{\"user\":{\"name\":\"张三\",\"password\":\"abc\"},\"age\":18}");

            assertTrue(result.contains("\"password\":\"****\""));
            assertTrue(result.contains("\"name\":\"张三\""));
            assertFalse(result.contains("\"password\":\"abc\""));
        }

        @Test
        @DisplayName("数组内对象敏感字段脱敏")
        void arrayOfObjects() throws Exception {
            String result = maskJson("[{\"password\":\"a\"},{\"password\":\"b\"},{\"name\":\"c\"}]");

            assertTrue(result.contains("\"password\":\"****\""));
            assertFalse(result.contains("\"password\":\"a\""));
            assertFalse(result.contains("\"password\":\"b\""));
            assertTrue(result.contains("\"name\":\"c\""));
        }

        @Test
        @DisplayName("深层嵌套不遗漏")
        void deeplyNested() throws Exception {
            String result = maskJson("{\"a\":{\"b\":{\"c\":{\"password\":\"secret\"}}}}");

            assertTrue(result.contains("\"password\":\"****\""));
            assertFalse(result.contains("\"password\":\"secret\""));
        }
    }

    // ======================== toJsonStr 入参路径 ========================

    @Nested
    @DisplayName("toJsonStr 入口")
    class ToJsonStr {

        @Test
        @DisplayName("Map 入参脱敏")
        void mapInput() throws Exception {
            Map<String, Object> map = Map.of("phone", "13800138000", "password", "abc123");
            String result = mask(map);

            assertTrue(result.contains("\"password\":\"****\""));
            assertTrue(result.contains("13800138000"));
        }

        @Test
        @DisplayName("null 入参安全")
        void nullInput() throws Exception {
            // JsonUtils.toJsonString(null) 返回字符串 "null"
            String result = mask(null);
            assertEquals("null", result);
        }

        @Test
        @DisplayName("List 入参脱敏")
        void listInput() throws Exception {
            List<Map<String, String>> list = List.of(
                    Map.of("password", "a"),
                    Map.of("password", "b")
            );
            String result = mask(list);

            assertFalse(result.contains("\"password\":\"a\""));
            assertFalse(result.contains("\"password\":\"b\""));
        }
    }

    // ======================== maskNode 单元 ========================

    @Nested
    @DisplayName("maskNode 单元")
    class MaskNode {

        @Test
        @DisplayName("ObjectNode 替换敏感字段")
        void maskObjectNode() throws Exception {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("password", "value");
            node.put("name", "keep");

            MASK_NODE.invoke(null, node);

            assertEquals("****", node.get("password").asText());
            assertEquals("keep", node.get("name").asText());
        }

        @Test
        @DisplayName("ArrayNode 递归处理")
        void maskArrayNode() throws Exception {
            ArrayNode arr = MAPPER.createArrayNode();
            ObjectNode item = MAPPER.createObjectNode();
            item.put("password", "value");
            arr.add(item);

            MASK_NODE.invoke(null, arr);

            assertEquals("****", arr.get(0).get("password").asText());
        }

        @Test
        @DisplayName("非容器节点无影响")
        void maskValueNode() throws Exception {
            TextNode textNode = TextNode.valueOf("hello");
            MASK_NODE.invoke(null, textNode);
            assertEquals("hello", textNode.asText());
        }
    }
}
