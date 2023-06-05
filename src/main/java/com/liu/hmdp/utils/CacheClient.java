package com.liu.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.liu.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.liu.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将任意java对象序列化为json并存储在string类型的key中
     * TODO 设置了ttl过期时间（处理缓存穿透问题）
     *
     * @param key
     * @param value
     * @param time  过期时间
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意java对象序列化为json并存储在string类型的key中
     * TODO 设置了逻辑过期时间（处理缓存击穿问题）
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLoginExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间(传过来的时间为知，需要将时间转换成秒)
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis(不设置ttl过期时间，永久有效，只判断逻辑过期时间)
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据key查询缓存，并反序列化为指定类型
     * TODO 利用缓存空值解决缓存穿透
     * 泛型，先定义后使用。定义：<R> -> 使用：R
     * Class<R> type 传入类型，进行泛型推断返回对应类型
     * Function<ID, R> dbFallback  传入数据库查询的逻辑（函数）【后备的逻辑】
     *
     * @param keyPrefix  key的前缀
     * @param id
     * @param type       反序列化的类型
     * @param dbFallback 查询Redis失败，再对数据库查询的逻辑（函数：参数ID，返回值R）
     * @param time       Redis缓存过期时间
     * @param unit       时间单位
     * @param <R>
     * @param <ID>       不确定的id类型，使用泛型
     * @return
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1、从Redis中查询信息
        String shopKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);

        // 2、判断Redis中是否存在信息
        if (StrUtil.isNotBlank(json)) {
            // 3、存在，将信息的JSON字符串装换为对象返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否是空值，isNotBlank会将空值也判断进去，所以无法避免缓存穿透，需要判空设置
        if (json != null) {
            // 为空值，返回错误信息
            return null;
        }

        // 4、不存在（为null），根据id查询数据库
        R r = dbFallback.apply(id);

        // 5、数据库不存在信息，返回错误信息
        if (r == null) {
            // 将空值写入到Redis中，防止缓存穿透
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            // 返回错误信息
            return null;
        }
        // 6、数据库存在信息，写入到Redis中(转换为JSON字符串存储)。
        this.set(shopKey, r, time, unit);

        // 7、写入到Redis后返回信息
        return r;
    }

    // 构建线程池（用于逻辑过期获取锁后开启线程进行缓存重建）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据key查询缓存，并反序列化为指定类型
     * 需要利用逻辑过期，解决缓存击穿问题（热点key）
     *
     * @param keyPrefix  key的前缀
     * @param id
     * @param type       反序列化的类型
     * @param dbFallback 查询Redis失败，再对数据库查询的逻辑（函数：参数ID，返回值R）
     * @param time       Redis缓存过期时间
     * @param unit       时间单位
     * @param <R>
     * @param <ID>       不确定的id类型，使用泛型
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        // 1、从Redis中查询信息
        String shopKey = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(shopKey);

        // 2、判断Redis中是否存在信息
        if (StrUtil.isBlank(json)) {
            // 3、不存在，返回null
            return null;
        }
        // 4、命中，需要把json反序列化为对象
        // 反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        // 从RedisData中获取data中对应的JSONObject对象
        JSONObject data = (JSONObject) redisData.getData();
        // 将JSONObject转化为原本的对象
        R r = JSONUtil.toBean(data, type);
        // 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5、判断对象中的时间是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1、未过期，直接返回
            return r;
        }

        // 5.2、过期，重建缓存
        // 6、重建缓存
        // 6.1、获取互斥锁
        // 设置互斥锁的key
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 6.2、判断锁是否获取成功
        if (isLock) {
            // 6.3、成功，再次检测缓存逻辑时间是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 6.3.1、未过期，直接返回
                return r;
            }

            // 6.3.2、过期
            // 6.4、开启独立线程，建立缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // TODO 重建缓存(用于测试时间只写了20秒，实际开发中应该是30分钟)
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入Redis
                    this.setWithLoginExpire(shopKey, r1, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4、返回旧的信息（成功、失败都要返回）
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // TODO 不要直接返回，直接返回会拆箱操作容易产生空指针。使用工具类
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
