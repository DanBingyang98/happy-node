package com.danby.happynode.framework.common.exception;

public class BusinessException extends RuntimeException {
    // 定义错误码
    private String errorCode;

    // 构造函数，传入错误码和消息
    public BusinessException(BaseExceptionInterface baseException) {
        super(baseException.getMessage());
        this.errorCode = baseException.getErrorCode();
    }

    public String getErrorCode() {
        return errorCode;
    }
}


