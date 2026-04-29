package com.ai.agent.types.exception.enums;


import com.ai.agent.types.exception.base.BaseErrorCodeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCodeEnum implements BaseErrorCodeEnum {
    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败"),
    ILLEGAL_PARAMETER("0002", "非法参数"),
    BAD_REQUEST("400", "请求参数错误"),
    CHAT_MODE_UNSUPPORTED("1001","不支持的对话模式"),
    CHAT_LLM_ERROR("1002","服务繁忙请稍后重试"),
    AGENT_NOT_FOUND("1003","未找到Agent定义"),
    AGENT_FAILED("1003","Agent编排执行失败"),
    AGENT_ENGINE_INVALID("1003","不支持的引擎类型"),
    KNOW_PROCESSING_FAILED("1004","文档处理失败"),
    UNSUPPORTED_FILE_TYPES("1005","不支持的文件类型"),
    FILE_SIZE_EXCEEDS_LIMIT("1006","文件大小超过限制"),
    ;

    private String errorCode;

    private String errorMsg;

}
