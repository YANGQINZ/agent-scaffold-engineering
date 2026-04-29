package com.ai.agent.types.exception.base;

public class BaseBizErrorCode implements BaseErrorCodeEnum {

    private final String errorCode;

    private final String errorMsg;

    public BaseBizErrorCode(String errorCode, String errorMsg) {
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getErrorMsg() {
        return errorMsg;
    }

    public static <EX extends BaseBizException> BaseBizErrorCode of(EX exception) {
        return new BaseBizErrorCode(exception.getErrorCode(), exception.getMessage());
    }

}
