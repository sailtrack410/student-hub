package com.studenthub.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;

@Getter
@Schema(description = "统一接口响应结构")
public class Result<T> {

    @Schema(description = "请求是否成功", example = "true")
    private final boolean success;
    @Schema(description = "业务响应码，成功固定为 OK", example = "OK")
    private final String code;
    @Schema(description = "响应提示信息", example = "success")
    private final String message;
    @Schema(description = "业务响应数据，具体结构由接口定义")
    private final T data;
    @Schema(description = "服务端响应时间", example = "2026-08-23T10:00:00Z")
    private final Instant timestamp;

    private Result(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
    }

    public static <T> Result<T> success() {
        return new Result<>(true, "OK", "success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(true, "OK", "success", data);
    }

    public static <T> Result<T> failed(String code, String message) {
        return new Result<>(false, code, message, null);
    }
}
