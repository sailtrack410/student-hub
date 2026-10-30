package com.studenthub.exception;

import com.studenthub.common.ErrorCode;
import com.studenthub.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException exception,
                                                        HttpServletRequest request) {
        log.warn("业务请求失败, method={}, path={}, code={}",
                request.getMethod(), request.getRequestURI(), exception.getCode());
        return ResponseEntity.status(exception.getHttpStatus())
                .body(Result.failed(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException exception,
            HttpServletRequest request) {
        log.warn("权限不足, method={}, path={}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(Result.failed(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        log.warn("路径参数类型不合法, method={}, path={}, name={}",
                request.getMethod(), request.getRequestURI(), exception.getName());
        return ResponseEntity.badRequest()
                .body(Result.failed(ErrorCode.INVALID_PARAMETER.getCode(), "请求参数不合法"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return ResponseEntity.badRequest()
                .body(Result.failed(ErrorCode.INVALID_PARAMETER.getCode(), message));
    }

    /** 未注册的路由/静态资源不应被当成 500：统一返回 404，避免前端把「接口不存在」误判为系统繁忙。 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<Result<Void>> handleNotFound(Exception exception, HttpServletRequest request) {
        log.warn("接口或资源不存在, method={}, path={}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(Result.failed(ErrorCode.NOT_FOUND.getCode(), "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception,
                                                         HttpServletRequest request) {
        log.error("未处理的系统异常, method={}, path={}",
                request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.internalServerError()
                .body(Result.failed(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
