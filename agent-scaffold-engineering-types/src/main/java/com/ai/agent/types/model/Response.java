package com.ai.agent.types.model;

import com.ai.agent.types.exception.base.BaseErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

/**
 * 统一的Spring mvc响应结果封装对象
 */
@Slf4j
public class Response<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求成功
     */
    private static final boolean REQUEST_SUCCESS = true;

    /**
     * 请求失败
     */
    private static final boolean REQUEST_FAIL = false;

    /**
     * 默认错误码
     */
    private static final String DEFAULT_ERROR_CODE = "-1";

    /**
     * 请求是否成功
     */
    private Boolean success;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误提示语
     */
    private String errorMessage;


    public Response() {
    }

    public Response(Boolean success, T data, String errorCode, String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 成功，不用返回数据
     *
     * @return
     */
    public static <T> Response<T> buildSuccess() {
        return new Response<>(REQUEST_SUCCESS, null, null, null);
    }

    /**
     * 成功，返回数据
     *
     * @param data
     * @return
     */
    public static <T> Response<T> buildSuccess(T data) {
        return new Response<>(REQUEST_SUCCESS, data, null, null);
    }

    /**
     * 失败，固定状态码
     *
     * @param errorMsg
     * @return
     */
    public static <T> Response<T> buildError(String errorMsg) {
        return new Response<>(REQUEST_FAIL, null, DEFAULT_ERROR_CODE, errorMsg);
    }

    /**
     * 失败，固定状态码
     *
     * @param errorMsg
     * @return
     */
    public static <T> Response<T> buildError(String errorMsg, T data) {
        return new Response<>(REQUEST_FAIL, data, DEFAULT_ERROR_CODE, errorMsg);
    }

    /**
     * 失败，自定义错误码和信息
     *
     * @param errorCode 错误码
     * @param errorMsg  错误提示
     * @return
     */
    public static <T> Response<T> buildError(String errorCode, String errorMsg) {
        return new Response<>(REQUEST_FAIL, null, errorCode, errorMsg);
    }

    /**
     * 失败，自定义错误码和信息，携带数据返回
     *
     * @param errorCode 错误码
     * @param errorMsg  错误提示
     * @return
     */
    public static <T> Response<T> buildError(String errorCode, String errorMsg, T data) {
        return new Response<>(REQUEST_FAIL, data, errorCode, errorMsg);
    }

    /**
     * 失败，枚举类定义错误码和信息
     *
     * @param baseErrorCodeEnum
     * @return
     */
    public static <T> Response<T> buildError(BaseErrorCodeEnum baseErrorCodeEnum) {
        return new Response<>(REQUEST_FAIL, null, baseErrorCodeEnum.getErrorCode(), baseErrorCodeEnum.getErrorMsg());
    }

    /**
     * 失败，枚举类定义错误码和信息
     *
     * @param baseErrorCodeEnum
     * @return
     */
    public static <T> Response<T> buildError(BaseErrorCodeEnum baseErrorCodeEnum, T data) {
        return new Response<>(REQUEST_FAIL, data, baseErrorCodeEnum.getErrorCode(), baseErrorCodeEnum.getErrorMsg());
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
