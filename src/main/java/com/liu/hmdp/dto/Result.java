package com.liu.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 业务结果类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;// 是否成功
    private String errorMsg;// 异常的时候返回
    private Object data;// 成功的时候返回
    private Long total;

    public static Result ok() {
        return new Result(true, null, null, null);
    }

    public static Result ok(Object data) {
        return new Result(true, null, data, null);
    }

    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total);
    }

    public static Result fail(String errorMsg) {
        return new Result(false, errorMsg, null, null);
    }
}
