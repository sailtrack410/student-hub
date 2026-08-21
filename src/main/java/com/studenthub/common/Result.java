package com.studenthub.common;

import lombok.Getter;

import java.time.Instant;

@Getter
public class Result<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
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
