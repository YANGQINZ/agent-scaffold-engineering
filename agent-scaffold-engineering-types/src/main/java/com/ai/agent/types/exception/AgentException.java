package com.ai.agent.types.exception;

import com.ai.agent.types.exception.base.BaseBizException;
import com.ai.agent.types.exception.base.BaseErrorCodeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AgentException extends BaseBizException {

    private static final long serialVersionUID = 1L;

    public AgentException(String errorMsg) {
        super(errorMsg);
    }

    public AgentException(String errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public AgentException(BaseErrorCodeEnum baseErrorCodeEnum) {
        super(baseErrorCodeEnum);
    }

    public AgentException(String errorCode, String errorMsg, Object... arguments) {
        super(errorCode, errorMsg, arguments);
    }

    public AgentException(BaseErrorCodeEnum baseErrorCodeEnum, Object... arguments) {
        super(baseErrorCodeEnum, arguments);
    }

}
