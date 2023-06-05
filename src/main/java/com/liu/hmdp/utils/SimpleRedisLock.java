package com.liu.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;

    // 锁的业务名，每个业务对应不同的锁
    private String name;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    // 锁的前缀名
    private static final String KEY_PREFIX = "lock:";

    // 锁的唯一标识，用UUID区分不同的服务（也就是不同的jvm），再用不同的线程id区分不同的线程，二者结合确保不同线程标识不同，相同线程标识相同
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-"; // 去掉UUID生成的中间的-分隔符

    // 定义锁的脚本（Lua脚本）
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT; //大小写转换快捷键 CTRL + shift + u
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>(); // 不建议用参数传入（直接字符串传入）。相当于硬编码
        // setLocation：设置脚本位置。ClassPathResource：默认就会去ClassPath下找资源（Spring提供的）
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        // 设置返回值
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        // 获取线程id，UUID + 线程id用作锁的值
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁 (要让redis的nx和ex同时执行)
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        // 防止拆箱出现空指针异常(使用工具类或者java自带的去包装都可以)
        return Boolean.TRUE.equals(success);
        // return BooleanUtil.isTrue(success); // 工具类包装
    }

    /**
     * 释放锁
     * TODO 判断锁和释放锁是两个动作（因此需要保证这两个操作的原子性）
     * 1、多线程阻塞时，如果在锁释放的时候阻塞，且触发超时释放，这时新线程获取锁执行自己的业务
     * 2、如果这个时候刚好之前那个线程阻塞结束，就会去删除其他线程的锁。从而出现多线程并发
     * Lua脚本保证锁的误删，同时lua脚本的原子性，不会出现线程安全的漏洞
     */
    @Override
    public void unlock() {
        /*// 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取Redis中存的锁的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        // 判断标识是否一致
        if (threadId.equals(id)) {
            // 一致，释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }*/

        // 基于Lua脚本实现分布式锁，只需要调用RedisTemplate中的api execute方法就可实现
        // 调用脚本:execute(脚本, KEYS, ARGV) keys(集合): redis中锁的key    argv：线程标识
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),// 单元素集合
                ID_PREFIX + Thread.currentThread().getId());
    }
}
