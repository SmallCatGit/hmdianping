package com.liu.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Redisson锁可重入锁的原理：
 * 使用hash结构->key是锁的名称，field是线程标识，value记录锁的重入次数
 * 1、尝试获取锁的时候先判断是否有人获取了锁，如果锁已经被获取，还要判断线程是不是自己，如果线程是自己，把重入次数加一，然后执行自己的业务
 * 2、执行完业务后，不能直接删除锁，而是应该在业务结束后把重入次数减一，方法中获取锁和释放锁成对出现，所以最后只需要判断重入次数为0，即可删除锁
 * <p>
 * hash中没有String中的互斥和过期时间的组合命令，只能手动设置，为了保证原子性，应该使用Lua脚本
 */

/**
 * 获取锁的Lua脚本：
 * local key = KEY[1]; --锁的key
 * local thread = ARGV[1]; -- 线程唯一标识
 * local releaseTime = ARGV[2] -- 锁的自动释放时间
 * --判断锁是否存在
 * if (redis.call('exists', key) == 0) then
 * -- 不存在，获取锁
 * redis.call('hset', key, threadId, '1');
 * -- 设置有效期
 * redis.call('expire', key, releaseTime);
 * return 1; -- 返回结果
 * end;
 * -- 锁已经存在，判断threadId是否是自己的
 * if (redis.call('hexistes', key, threadId) == 1) then
 * -- 存在且是自己的锁，获取锁，重入次数+1
 * redis.call('hincrby', key, threadId, '1');
 * -- 重置锁的有效期
 * redis.call('expire', key, releaseTime);
 * return 1; -- 返回结果
 * end;
 * return 0; -- 代码走到这，说明获取的锁不是自己的，标识获取锁失败
 * <p>
 * 释放锁的Lua脚本：
 * local key = KEY[1] -- 锁的key
 * local threadId = ARGV[1] -- 线程标识
 * local releaseTime = ARGV[2] -- 锁的自动释放时间
 * -- 判断锁是不是自己的
 * if (redis.call('hexists', key, threadId) == 0) then
 * -- 锁不是自己的，直接返回
 * return nil;
 * end;
 * -- 锁是自己的，重入次数减一
 * local count = redis.call('hincrby', key, threadId, -1);
 * -- 重入次数后判断次数是否为0，为0表示锁释放到最后，可删除
 * if (count > 0) then
 * -- 大于0，说明还不能释放锁，重置有效期返回
 * redis.call('exipire', key, releaseTime);
 * return nil;
 * else -- 等于0，说明可以释放锁，直接删除锁即可
 * redis.call('del', key);
 * return 0;
 * end;
 */

@Slf4j
@SpringBootTest
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;

    private RLock lock;

    @BeforeEach
    void setUp() {
        lock = redissonClient.getLock("order");
    }

    @Test
    void method1() {
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败。。。。。1");
            return;
        }
        try {
            log.info("获取锁成功。。。。。1");
            method2();
            log.info("开始执行业务。。。1");
        } finally {
            log.warn("准备释放锁。。。1");
            lock.unlock();
        }
    }

    void method2() {
        // 尝试获取锁
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("获取锁失败。。。。。2");
            return;
        }
        try {
            log.info("获取锁成功。。。。。2");
            log.info("开始执行业务。。。2");
        } finally {
            log.warn("准备释放锁。。。2");
            lock.unlock();
        }
    }
}
