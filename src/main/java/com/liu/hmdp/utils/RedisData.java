package com.liu.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 设置的逻辑过期时间
    private LocalDateTime expireTime;
    // 存进Redis的数据，万能数据，给什么就是什么
    private Object data;
}
