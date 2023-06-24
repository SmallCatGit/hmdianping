package com.liu.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.VoucherOrder;
import com.liu.hmdp.mapper.VoucherOrderMapper;
import com.liu.hmdp.service.SeckillVoucherService;
import com.liu.hmdp.service.VoucherOrderService;
import com.liu.hmdp.utils.RedisIdWorker;
import com.liu.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {

    @Resource
    private SeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 静态代码块初始化脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        // 设置位置
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        // 设置结果类型
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 创建线程池(一个线程)
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct // 类初始化完毕后执行
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 线程任务(秒杀抢购前执行 --> 类初始化时就执行)
    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    // 1、获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2、判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 2.1、获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 3、获取消息成功，解析消息中的订单信息，转换为VoucherOrder对象
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 4、创建订单
                    handleVoucherOrder(voucherOrder);
                    // 5、进行ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常" + e);
                    handlePendingList();
                }
            }
        }

        /**
         * 处理消息抛了异常，没有进行ACK确认，需要再次处理，直到进行ACK确认
         */
        private void handlePendingList() {
            while (true) {
                try {
                    // 1、获取pengding-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2、判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        // 2.1、获取失败，说明pengding-list中没有消息，结束循环
                        break;
                    }
                    // 3、获取消息成功，解析消息中的订单信息，转换为VoucherOrder对象
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 4、创建订单
                    handleVoucherOrder(voucherOrder);
                    // 5、进行ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pengding-list订单异常" + e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    /*
    // 阻塞队列
    // 特点：线程尝试从队列中获取元素时，没有元素就会被阻塞，直到队列中有元素才会被唤醒获取元素
    private BlockingQueue<VoucherOrder> orderTakes = new ArrayBlockingQueue<>(1024 * 1024); // 使用最简单的利用数组实现阻塞队列

    // 线程任务(秒杀抢购前执行 --> 类初始化时就执行)
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1、获取队列中的订单信息
                    // take:必要时等待，直到元素可用为止。没有元素不会往下执行,不会造成cpu负担
                    VoucherOrder voucherOrder = orderTakes.take();
                    // 2、创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常" + e);
                }
            }
        }
    }*/

    /**
     * 处理订单
     *
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 1、获取用户（此时不能从线程中获取，因为开启了子线程）
        Long userId = voucherOrder.getUserId();
        // 2、创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 3、获取锁（防止redis出错）
        boolean isLock = lock.tryLock();
        // 4、判断锁是否获取成功
        if (!isLock) {
            // 获取锁失败，返回错误信息
            log.error("不符合要求，不能下单");
            return;
        }
        try {
            // 创建订单
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    VoucherOrderService proxy;

    /**
     * 优惠券秒杀下单（基于Lua和Stream消息队列实现）
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 获取订单id
        long orderId = redisIdWorker.nextId("order");
        // 1、执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(), // 传入空集合
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        // 2、判断脚本结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1、结果不为0，表示没有购买资格（库存不足或已经下过单）
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 3、获取代理对象（只能提前获取，开启了新线程，在子线程中无法获取）
        proxy = (VoucherOrderService) AopContext.currentProxy();
        // 4、返回订单id
        return Result.ok(orderId);
    }

    /**
     * 优惠券秒杀下单(Lua代码加上阻塞队列实现)
     * 基于阻塞队列实现异步秒杀（主线程判断秒杀资格完成抢单业务，而耗时久的业务放入阻塞队列，利用独立线程异步执行）
     * 存在问题：
     * 1、使用jdk中的阻塞队列，防止高并发导致内存溢出，限制了阻塞队列长度（内存限制问题）
     * 2、基于内存保存订单信息，如果服务宕机，内存中的所有订单信息都会丢失，用户付款但是后台没数据，导致数据不一致。
     * 还可能有一个线程从队列中取出任务正在执行，此时发生事故，导致任务未执行，任务取消会导致队列消失，以后不再执行任务，从而也会出现数据不一致
     *
     * @param voucherId
     * @return
     */
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 1、执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(), // 传入空集合
                voucherId.toString(), userId.toString()
        );
        // 2、判断脚本结果是否为0
        int r = result.intValue();
        if (r != 0) {
            // 2.1、结果不为0，表示没有购买资格（库存不足或已经下过单）
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2、结果为0，有购买资格，把下单信息存到阻塞队列中，用于异步执行
        // 创建voucherOrder对象
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3、设置订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 2.4、设置用户id
        voucherOrder.setUserId(userId);
        // 2.5、设置代金券id
        voucherOrder.setVoucherId(voucherId);
        // 2.6、创建阻塞队列
        orderTakes.add(voucherOrder);

        // 3、获取代理对象（只能提前获取，开启了新线程，在子线程中无法获取）
        proxy = (VoucherOrderService) AopContext.currentProxy();
        // 4、返回订单id
        return Result.ok(orderId);
    }*/

    /**
     * 优惠券秒杀下单(java代码实现)
     *
     * @param voucherId
     * @return
     */
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1、查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2、根据优惠券信息判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 秒杀未开始
            return Result.fail("秒杀尚未开始！");
        }

        // 3、判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 秒杀结束
            return Result.fail("秒杀已结束！");
        }

        // 库存
        Integer stock = voucher.getStock();

        // 4、判断优惠券库存
        if (stock < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        *//*Long userId = UserHolder.getUser().getId();
        // toString：转换为字符串，但是每个对象转换的是不同字符串对象，哪怕值形同（源代码用new的字符串数组），不符合锁的要求
        // intern方法：去字符串常量池中找一样值的地址返回。从而实现只判断值
        // TODO 把整个函数锁起来，如果只锁对应的修改语句在锁释放时，还没提交事务，其他线程进来修改从而会有线程安全问题
        synchronized (userId.toString().intern()) { // TODO 不能解决集群问题（每个jvm中都有单独的锁的空间，不能共享）
            // TODO 这个方法是this调用的，this是当前这个类对象。而事务生效是Spring当前这个类做动态代理，拿到了代理对象
            // 获取当前对象的代理对象（事务）
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*//*

        Long userId = UserHolder.getUser().getId();
        // 创建锁对象( TODO 锁同一个用户，不同的用户不能被锁 -> 以业务标识 + 用户id为锁)
        // SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        // TODO 使用Redisson的锁，改进自定义锁的功能
        RLock lock = redissonClient.getLock("lock:order" + userId);
        // 尝试获取锁(失败不等待，直接使用无参最简单的方式)
        // boolean isLock = lock.tryLock(1200); // TODO 用于测试，所以时间给的长，真正业务的时候根据业务时间指定时间（自定义锁的测试）
        boolean isLock = lock.tryLock();

        // 判断锁是否获取成功
        if (!isLock) {
            // 获取锁失败。返回错误信息或重试
            return Result.fail("不允许重复下单");
        }

        try {
            // 获取锁成功
            // 获取当前对象的代理对象（事务）
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }*/

    /**
     * 查询库存->判断订单->减库存->创建订单
     *
     * @param voucherOrder
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 创建用户id(在拦截器中获取存储了当前用户，通过自定义的线程类获取用户id)
        Long userId = voucherOrder.getUserId();

        // 一人一单（秒杀优惠券每个用户应该只能下一单）【多线程并发会出安全问题，需要给这个业务加锁】
        // 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();

        // 判断订单是否存在
        if (count > 0) {
            // 订单存在
            log.error("该商品只能购买一次！");
            return;
        }

        // 5、扣减库存(扣减的时候同样判断库存)
        // SQL: update from tb_seckill_voucher set stock = ? where voucher_id = ? and stock > ?
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1") // set stock = stock -1
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0) // CAS（compare and set） 乐观锁法防止高并发造成数据库出现异常数据 where id = ? and stock > ?
                .update();
        if (!success) {
            // 库存不足
            log.error("库存不足！");
            return;
        }

        /*(未使用阻塞队列改变前)
        // 6、生成券订单对象，创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1、创建订单id(用自定义id生成器随机生成)
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);

        // 6.2、设置用户id
        voucherOrder.setUserId(userId);

        // 6，3、代金券id
        voucherOrder.setVoucherId(voucherId);*/

        // 6、保存订单
        save(voucherOrder);

        /*// 7、返回订单id
        return Result.ok(orderId);*/
    }
}

