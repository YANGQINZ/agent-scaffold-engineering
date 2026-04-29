package com.ai.agent.types.exception;

import com.ai.agent.types.exception.base.BaseBizException;
import com.ai.agent.types.exception.base.BaseErrorCodeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatException extends BaseBizException {

    private static final long serialVersionUID = 1L;

    public ChatException(String errorMsg) {
        super(errorMsg);
    }

    public ChatException(String errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public ChatException(BaseErrorCodeEnum baseErrorCodeEnum) {
        super(baseErrorCodeEnum);
    }

    public ChatException(String errorCode, String errorMsg, Object... arguments) {
        super(errorCode, errorMsg, arguments);
    }

    public ChatException(BaseErrorCodeEnum baseErrorCodeEnum, Object... arguments) {
        super(baseErrorCodeEnum, arguments);
    }

}
