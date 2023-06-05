package com.liu.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * id生成器，随机生成id，存放在redis中(以天存储)
 */
@Component // SpingBean
public class RedisIdWorker {

    // 初始时间(2022.1.1 0:0:0) 开始的时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200;

    // 序列号位数
    private static final long COUNT_BITS = 32;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * @param keyPrefix 业务前缀：区分不同的业务
     * @return
     */
    public long nextId(String keyPrefix) {
        // 1、生成时间戳
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        long second = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = second - BEGIN_TIMESTAMP;

        // 2、生成序列号（Redis的自增String类型）
        // 同一个业务也要采用不同的key，采用同一个key随着时间增长可能会超过最大限制
        // key：采用头 + 业务名 + 时间戳（这一天的时间戳）
        // 【好处：1、每天都有不同的key，这样就不会超过限制
        //       2、统计每天下单数，可以直接看对应日期key的值】
        // 2.1、获取当前日期，精确到天（mow.format:格式化）【如果需要精确到月、年，只需要用：分隔 -> Redis中：会分层级存储】
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));// ofPattern:自定义格式化方式

        // 2.2、自增长（redis存储id生成个数）
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + date);

        // 3、拼接时间戳和序列号作为秒杀业务id
        // 先将时间戳向左移动序列号位，然后再或运算序列号，把后面的0填充为序列号
        return timestamp << COUNT_BITS | count;
    }

    /**
     * 用main函数生成初始时间，用于计算时间戳
     * @param args
     */
    /*public static void main(String[] args) {
        // 指定年月日，具体时间
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        // 将具体时间转化成秒数（参数接收的是时区）
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }*/
}
