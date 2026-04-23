package com.ai.agent.trigger.http;

import com.ai.agent.types.exception.AgentException;
import com.ai.agent.types.exception.AppException;
import com.ai.agent.types.exception.ChatException;
import com.ai.agent.types.exception.KnowledgeException;
import com.ai.agent.types.model.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatException.class)
    public Response<Void> handleChatException(ChatException e) {
        log.warn("Chat异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(AgentException.class)
    public Response<Void> handleAgentException(AgentException e) {
        log.warn("Agent异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(KnowledgeException.class)
    public Response<Void> handleKnowledgeException(KnowledgeException e) {
        log.warn("Knowledge异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(AppException.class)
    public Response<Void> handleAppException(AppException e) {
        log.warn("业务异常: code={}, info={}", e.getCode(), e.getInfo());
        return Response.<Void>builder().code(e.getCode()).info(e.getInfo()).build();
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.<Void>builder()
                .code(com.ai.agent.types.common.Constants.ResponseCode.UN_ERROR.getCode())
                .info("系统繁忙，请稍后重试")
                .build();
    }
}
