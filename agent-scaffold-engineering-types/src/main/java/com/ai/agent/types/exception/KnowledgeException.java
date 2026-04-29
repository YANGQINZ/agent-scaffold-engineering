package com.ai.agent.types.exception;

import com.ai.agent.types.exception.base.BaseBizException;
import com.ai.agent.types.exception.base.BaseErrorCodeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeException extends BaseBizException {

    private static final long serialVersionUID = 1L;

    public KnowledgeException(String errorMsg) {
        super(errorMsg);
    }

    public KnowledgeException(String errorCode, String errorMsg) {
        super(errorCode, errorMsg);
    }

    public KnowledgeException(BaseErrorCodeEnum baseErrorCodeEnum) {
        super(baseErrorCodeEnum);
    }

    public KnowledgeException(String errorCode, String errorMsg, Object... arguments) {
        super(errorCode, errorMsg, arguments);
    }

    public KnowledgeException(BaseErrorCodeEnum baseErrorCodeEnum, Object... arguments) {
        super(baseErrorCodeEnum, arguments);
    }

}
