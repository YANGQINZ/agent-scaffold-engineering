package com.ai.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AgentException extends AppException {

    private static final long serialVersionUID = 1L;

    public AgentException(String code) {
        super(code);
    }

    public AgentException(String code, String message) {
        super(code, message);
    }

    public AgentException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

}
