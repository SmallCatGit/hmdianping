package com.liu.hmdp;

import com.liu.hmdp.entity.Shop;
import com.liu.hmdp.service.impl.ShopServiceImpl;
import com.liu.hmdp.utils.CacheClient;
import com.liu.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.liu.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianpingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    // 线程池 500线程
    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testRedisIdWorker() throws InterruptedException {
        // 统计线程执行时间用CountDownLatch(等线程执行完计时)[线程是异步执行,没法直接统计结束时间]
        CountDownLatch latch = new CountDownLatch(300);

        // 定义任务
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                // 生成id
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            // 一个线程执行完毕就执行countDown
            latch.countDown();
        };
        // 任务开始时记时
        long begin = System.currentTimeMillis();

        // 将任务提交300次
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        // 等待countDown结束
        latch.await();

        // 任务结束后计时
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    // TODO 设置店铺信息逻辑过期时间，要先加入Redis，不然后面没有这个信息去测试逻辑过期！！
    @Test
    void testsaveShopToRedis() throws InterruptedException {
        shopService.saveShopToRedis(1L, 10L);
    }

    @Test
    void testqueryWithLogicalExpire() {
        // 查询数据库
        Shop shop = shopService.getById(1L);
        // 设置逻辑过期时间
        cacheClient.setWithLoginExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testRedisson() throws InterruptedException {
        // 获取锁（可重入），指定锁的名称
        RLock lock = redissonClient.getLock("anylock");
        // 尝试获取锁，参数分别是：获取锁的最大等待时间（期间会重试），锁自动释放时间，时间单位
        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
        // 判断锁是否获取成功
        if (isLock) {
            try {
                System.out.println("执行业务");
            } finally {
                // 释放锁
                lock.unlock();
            }
        }
    }

}
