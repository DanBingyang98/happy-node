package com.danby.happynode.auth.exception;

import com.danby.happynode.auth.enums.ResponseCodeEnum;
import com.danby.happynode.framework.common.exception.BusinessException;
import com.danby.happynode.framework.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Optional;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public Response<Object> handleBusinessException(HttpServletRequest req, BusinessException ex) {
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", req.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return Response.fail(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public Response<Object> handleMethodArgumentException(HttpServletRequest req, MethodArgumentNotValidException ex) {
        // 参数错误异常码
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();
        StringBuilder sb = new StringBuilder();
        BindingResult bindingResult = ex.getBindingResult();
        // 如果有参数错误，则遍历错误信息
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors -> {
            errors.forEach(error ->
                    sb.append(error.getField())
                            .append(" ")
                            .append(error.getDefaultMessage())
                            .append(", 当前值: '")
                            .append(error.getRejectedValue())
                            .append("'; "));
        });
        String errorMessage = sb.toString();
        // 记录错误日志
        log.warn("{} request error, errorCode: {}, errorMessage: {}", req.getRequestURI(), errorCode, errorMessage);
        // 返回错误信息
        return Response.fail(errorCode, errorMessage);
    }

    /**
     * 其他类型异常
     *
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler({Exception.class})
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseBody
    public Response<Object> handleIllegalArgumentException(HttpServletRequest request, IllegalArgumentException e) {
        //
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();
        String errorMessage = e.getMessage();
        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);
        return Response.fail(errorCode, errorMessage);
    }


}
