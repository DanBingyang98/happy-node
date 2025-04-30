package com.danby.happynode.framework.common.response;

import com.danby.happynode.framework.common.exception.BaseExceptionInterface;
import com.danby.happynode.framework.common.exception.BusinessException;
import lombok.Data;

import java.io.Serializable;

@Data
public class Response<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    // 成功标志
    private boolean success = true;
    // 错误码
    private String code;
    // 错误信息
    private String message;
    // 数据
    private T data;

    // 成功返回
    public static <T> Response<T> success() {
        // 创建一个Response对象
        Response<T> response = new Response<>();
        // 返回Response对象
        return response;
    }

    // 成功返回，带数据
    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setData(data);
        return response;
    }

    // 失败返回
    public static <T> Response<T> fail() {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        return response;
    }

    // 失败返回，带错误信息
    public static <T> Response<T> fail(String message) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    // 失败返回，带错误码和错误信息
    public static <T> Response<T> fail(String code, String message) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    // 失败返回，带自定义异常
    public static <T> Response<T> fail(BaseExceptionInterface exception) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setCode(exception.getErrorCode());
        response.setMessage(exception.getMessage());
        return response;
    }

    // 失败返回，带业务异常
    public static <T> Response<T> fail(BusinessException exception) {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setCode(exception.getErrorCode());
        response.setMessage(exception.getMessage());
        return response;
    }
}
