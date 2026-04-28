package com.ai.agent.types.model;

import com.ai.agent.types.common.CommonConstants;
import com.ai.agent.types.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
public class Response<T> implements Serializable {


    private Integer code;
    private String info;
    private T data;

    private Response(Integer code,String info,T data){
        this.code = code;
        this.info = info;
        this.data = data;
    }


    public static <T> Response<T> success() {
        return new Response<>(CommonConstants.StatusCode.SUCCESS, "success", null);
    }

    public static <T> Response<T> success(T data) {
        return new Response<>(CommonConstants.StatusCode.SUCCESS, "success", data);
    }

    public static <T> Response<T> success(String message, T data) {
        return new Response<>(CommonConstants.StatusCode.SUCCESS, message, data);
    }

    // ========== 失败响应 ==========

    public static <T> Response<T> error(String message) {
        return new Response<>(CommonConstants.StatusCode.SERVER_ERROR, message, null);
    }

    public static <T> Response<T> error(Integer code, String message) {
        return new Response<>(code, message, null);
    }

    public static <T> Response<T> error(Constants.ResponseCode errorCode) {
        return new Response<>(errorCode.getCode(), errorCode.getInfo(), null);
    }

    public static <T> Response<T> error(Constants.ResponseCode errorCode, String message) {
        return new Response<>(errorCode.getCode(), message, null);
    }
}