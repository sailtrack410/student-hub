package com.studenthub.common.util;

import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

/**
 * 集成客户端异常收敛模板：原先 wechat/qq/ai/jw 各集成客户端重复的
 * {@code catch (BusinessException e) throw e; catch (Exception e) { log; throw new BusinessException(errorCode); }}
 * 同形 try/catch 收敛到这里，调用点改为单行 wrap。
 */
@Slf4j
public final class IntegrationExceptions {

    private IntegrationExceptions() {
    }

    /** 允许抛受检异常的 Supplier：对齐原 try 块内含 IOException / Jackson 异常的写法。 */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /** BusinessException 原样上抛；其余异常按 logMessage 记 error 日志后转 errorCode。 */
    public static <T> T wrap(ThrowingSupplier<T> action, ErrorCode errorCode, String logMessage) {
        try {
            return action.get();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error(logMessage, exception);
            throw new BusinessException(errorCode);
        }
    }
}
