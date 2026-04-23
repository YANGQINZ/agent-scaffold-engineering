package com.ai.agent.types.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class KnowledgeException extends AppException {

    private static final long serialVersionUID = 1L;

    public KnowledgeException(String code) {
        super(code);
    }

    public KnowledgeException(String code, String message) {
        super(code, message);
    }

    public KnowledgeException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

}
