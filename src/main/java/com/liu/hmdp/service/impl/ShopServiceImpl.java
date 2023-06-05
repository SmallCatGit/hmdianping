package com.liu.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.Shop;
import com.liu.hmdp.mapper.ShopMapper;
import com.liu.hmdp.service.ShopService;
import com.liu.hmdp.utils.CacheClient;
import com.liu.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.liu.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    CacheClient cacheClient;

    /**
     * 根据id查询商铺信息（建立在Redis的基础上）
     * TODO 防止缓存穿透（缓存空值）
     * TODO 防止缓存击穿（加互斥锁或逻辑过期）
     * 自定义的互斥锁，不是synchronized或者lock。使用的是redis String操作中的setnx操作：只有在key不存在时才执行）
     * 逻辑过期：手动判断缓存是否过期，根据提前存的一个时间字段去判断，过期的话通过竞争锁去开启线程更新缓存，其他的返回旧数据
     * TODO 缓存雪崩，可以在key上随机加一个范围时间，防止大量的key同时过期
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        /*// 缓存穿透（自定义的方法）
        Shop shop = queryWithPassThrough(id);*/

        // 缓存穿透（自定义的工具类）
        // Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        /*// 缓存击穿。互斥锁解决
        Shop shop = queryWithMutex(id);*/

        // 缓存击穿。逻辑过期解决 （TODO 需要提前加载（在单元测试中加载），才能查询到逻辑过期时间！！！）
        // Shop shop = queryWithLogicalExpire(id);
        /*Shop shop = cacheClient
                // .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES); // 实际
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS); // 测试
*/
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        // 7、写入到Redis后返回信息给前端
        return Result.ok(shop);
    }

    // 构建线程池（用于逻辑过期获取锁后开启线程进行缓存重建）
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     *
     * @param id
     * @return
     */
    /*public Shop queryWithLogicalExpire(Long id) {
        // 1、从Redis中查询店铺信息
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2、判断Redis中是否存在店铺信息
        if (StrUtil.isBlank(shopJson)) {
            // 3、不存在，返回null
            return null;
        }
        // 4、命中，需要把json反序列化为对象
        // 反序列化为RedisData对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        // 从RedisData中获取data中对应的JSONObject对象
        JSONObject data = (JSONObject) redisData.getData();
        // 将JSONObject转化为原本的Shop对象
        Shop shop = JSONUtil.toBean(data, Shop.class);
        // 获取逻辑过期时间
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5、判断对象中的时间是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1、未过期，直接返回店铺信息
            return shop;
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
                // 6.3.1、未过期，直接返回店铺信息
                return shop;
            }

            // 6.3.2、过期
            // 6.4、开启独立线程，建立缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // TODO 重建缓存(用于测试时间只写了20秒，实际开发中应该是30分钟)
                    this.saveShopToRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 6.4、返回旧的店铺信息（成功、失败都要返回）
        return shop;
    }
*/

    /**
     * 缓存击穿(热点key问题) 互斥锁
     * 被高并发访问、缓存重建业务复杂导致key突然失效
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        // 1、从Redis中查询店铺信息
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2、判断Redis中是否存在店铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            // 3、存在，将店铺信息的JSON字符串装换为对象返回
            /*Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);*/
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值，isNotBlank会将空值也判断进去，所以无法避免缓存穿透，需要判空设置
        if (shopJson != null) {
            // 前面判断了有值，所以这里不为null，就一定是空字符串
            // 空字符串，返回错误信息
            // return Result.fail("没有该店铺信息");
            return null;
        }

        // 4、实现缓存重建
        // 4.1、尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2、判断锁是否获得
            if (!isLock) {
                // 4.3、失败，休眠并重新获取锁
                Thread.sleep(50);
                // 递归重新获取锁
                return queryWithMutex(id);
            }

            // 4.4、成功，再次确定Redis中的缓存是否存在，存在直接返回
            String shopJsonToo = stringRedisTemplate.opsForValue().get(shopKey);
            if (StrUtil.isNotBlank(shopJsonToo)) {
                return JSONUtil.toBean(shopJsonToo, Shop.class);
            }
            // 4.5、再次确定缓存不存在，且获取锁成功，根据id查询数据库
            shop = getById(id);

            // 模拟重建延迟
            Thread.sleep(200);

            // 5、数据库不存在信息，返回错误信息
            if (shop == null) {
                // 将空值写入到Redis中，防止缓存穿透（缓存空值或者采用布隆过滤算法，这里采用缓存空值）
                // 布隆过滤（概率上的统计，并不是100%正确）：【客户端和Redis之间加入一个布隆过滤器，判断是否拦截请求。过滤器相当于byte数组，存储数据库计算后的hash值的二进制位】
                //（缓存穿透：当前端提交一个redis和数据库都没有的id这种去进行数据查询时候，就会一直请求数据库，可能使数据库崩坏）
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

                // 返回错误信息
                // return Result.fail("店铺不存在");
                return null;
            }
            // 6、数据库存在信息，写入到Redis中(转换为JSON字符串存储)。
            // TODO 缓存删除：设置超时时间（用于更新缓存兜底，缓存失效后会自动调用数据库存储，尽可能保证数据一致性）
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7、释放互斥锁
            unlock(lockKey);
        }

        // 8、写入到Redis后返回信息给前端
        // return Result.ok(shop);
        return shop;
    }

    /**
     * 解决缓存穿透的方法（从service上拿下来的）
     *
     * @param id
     * @return
     */
    /*public Shop queryWithPassThrough(Long id) {
        // 1、从Redis中查询店铺信息
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);

        // 2、判断Redis中是否存在店铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            // 3、存在，将店铺信息的JSON字符串装换为对象返回
            // Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            // return Result.ok(shop);
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中的是否是空值，isNotBlank会将空值也判断进去，所以无法避免缓存穿透，需要判空设置
        if (shopJson != null) {
            // 前面判断了有值，所以这里不为null，就一定是空字符串
            // 空字符串，返回错误信息
            // return Result.fail("没有该店铺信息");
            return null;
        }

        // 4、不存在，根据id查询数据库
        Shop shop = getById(id);

        // 5、数据库不存在信息，返回错误信息
        if (shop == null) {
            // 将空值写入到Redis中，防止缓存穿透（缓存空值或者采用布隆过滤算法，这里采用缓存空值）
            // 布隆过滤（概率上的统计，并不是100%正确）：【客户端和Redis之间加入一个布隆过滤器，判断是否拦截请求。过滤器相当于byte数组，存储数据库计算后的hash值的二进制位】
            //（缓存穿透：当前端提交一个redis和数据库都没有的id这种去进行数据查询时候，就会一直请求数据库，可能使数据库崩坏）
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            // 返回错误信息
            // return Result.fail("店铺不存在");
            return null;
        }
        // 6、数据库存在信息，写入到Redis中(转换为JSON字符串存储)。
        // TODO 缓存删除：设置超时时间（用于更新缓存兜底，缓存失效后会自动调用数据库存储，尽可能保证数据一致性）
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7、写入到Redis后返回信息给前端
        // return Result.ok(shop);
        return shop;
    }
*/

    /**
     * 获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // TODO 不要直接返回，直接返回会拆箱操作容易产生空指针。使用工具类
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 设置店铺信息逻辑过期时间(缓存重建)
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShopToRedis(Long id, Long expireSeconds) throws InterruptedException {
        // 1、查询店铺数据
        Shop shop = getById(id);

        // TODO 设置休眠时间用于测试
        Thread.sleep(200);

        // 2、封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3、写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 更新店铺并删除缓存（单体项目，可以通过事务去控制原子性）
     *
     * @param shop
     * @return
     */
    @Override
    @Transactional // 更新、删除过程中出现异常，应该进行事务回滚
    public Result updateShopAndRemoveCache(Shop shop) {
        // 1、判断店铺id是否为空
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        // 2、更新数据库
        updateById(shop);

        // 3、删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        // 4、返回
        return Result.ok();
    }
}
