package com.liu.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

/**
 * UV统计
 * 没有这么多用户，所以只是做了一个统计测试，测试内存占用和统计效果
 * <p>
 * UV：独立访客量，一天内一个用户多次访问该网站，只记录一次。可以知道一个网站的用户访问量
 * PV：页面访问量或点击量，用户每次访问网站的一个页面，记录一次PV。通常用来衡量网站的流量
 */
@SpringBootTest
public class HyperLogLogTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testHyperLogLog() {
        // 通过数组分批导入
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                // 数组满了，发送到redis中
                stringRedisTemplate.opsForHyperLogLog().add("hll", values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hll");
        System.out.println("count = " + count);
    }
}
