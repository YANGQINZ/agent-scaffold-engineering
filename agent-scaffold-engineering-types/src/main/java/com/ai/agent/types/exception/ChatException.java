package com.ai.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatException extends AppException {

    private static final long serialVersionUID = 1L;

    public ChatException(String code) {
        super(code);
    }

    public ChatException(String code, String message) {
        super(code, message);
    }

    public ChatException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

}
