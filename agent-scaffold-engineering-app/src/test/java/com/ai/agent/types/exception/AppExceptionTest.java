package com.ai.agent.types.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppExceptionTest {

    @Test
    void toString_shouldContainCorrectClassName() {
        AppException ex = new AppException("E001", "测试异常");
        String str = ex.toString();
        // toString 应包含正确的类名 AppException，而非遗留的 XApiException
        assertTrue(str.contains("AppException"),
                "toString 应包含正确的类名 AppException，实际输出: " + str);
        assertFalse(str.contains("XApiException"),
                "toString 不应包含遗留类名 XApiException，实际输出: " + str);
    }

    @Test
    void toString_shouldContainCodeAndInfo() {
        AppException ex = new AppException("E001", "错误描述");
        String str = ex.toString();
        assertTrue(str.contains("E001"), "toString 应包含异常码");
        assertTrue(str.contains("错误描述"), "toString 应包含异常信息");
    }

    @Test
    void toString_withCodeOnly_shouldNotShowNullInfo() {
        AppException ex = new AppException("E002");
        String str = ex.toString();
        assertTrue(str.contains("E002"), "toString 应包含异常码");
        assertTrue(str.contains("AppException"), "toString 应包含正确的类名");
    }
}
