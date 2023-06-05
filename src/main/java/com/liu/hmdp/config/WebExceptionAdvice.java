package com.liu.hmdp.config;

import com.liu.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /**
     * 通用异常的处理
     * @param e
     * @return
     */
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.getMessage(), e);
        return Result.fail(e.getMessage());
    }
}
