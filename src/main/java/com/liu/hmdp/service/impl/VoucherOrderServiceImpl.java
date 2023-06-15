package com.liu.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.SeckillVoucher;
import com.liu.hmdp.entity.VoucherOrder;
import com.liu.hmdp.mapper.VoucherOrderMapper;
import com.liu.hmdp.service.SeckillVoucherService;
import com.liu.hmdp.service.VoucherOrderService;
import com.liu.hmdp.utils.RedisIdWorker;
import com.liu.hmdp.utils.SimpleRedisLock;
import com.liu.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    /**
     * 优惠券秒杀下单
     *
     * @param voucherId
     * @return
     */
    @Override
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

        /*Long userId = UserHolder.getUser().getId();
        // toString：转换为字符串，但是每个对象转换的是不同字符串对象，哪怕值形同（源代码用new的字符串数组），不符合锁的要求
        // intern方法：去字符串常量池中找一样值的地址返回。从而实现只判断值
        // TODO 把整个函数锁起来，如果只锁对应的修改语句在锁释放时，还没提交事务，其他线程进来修改从而会有线程安全问题
        synchronized (userId.toString().intern()) { // TODO 不能解决集群问题（每个jvm中都有单独的锁的空间，不能共享）
            // TODO 这个方法是this调用的，this是当前这个类对象。而事务生效是Spring当前这个类做动态代理，拿到了代理对象
            // 获取当前对象的代理对象（事务）
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*/

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
    }

    /**
     * 查询库存->判断订单->减库存->创建订单
     *
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 创建用户id(在拦截器中获取存储了当前用户，通过自定义的线程类获取用户id)
        Long userId = UserHolder.getUser().getId();

        // 一人一单（秒杀优惠券每个用户应该只能下一单）【多线程并发会出安全问题，需要给这个业务加锁】
        // 查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        // 判断订单是否存在
        if (count > 0) {
            // 订单存在
            return Result.fail("该商品只能购买一次！");
        }

        // 5、扣减库存(扣减的时候同样判断库存)
        // SQL: update from tb_seckill_voucher set stock = ? where voucher_id = ? and stock > ?
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1") // set stock = stock -1
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // CAS（compare and set） 乐观锁法防止高并发造成数据库出现异常数据 where id = ? and stock > ?
                .update();
        if (!success) {
            // 库存不足
            return Result.fail("库存不足！");
        }

        // 6、生成券订单对象，创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1、创建订单id(用自定义id生成器随机生成)
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);

        // 6.2、设置用户id
        voucherOrder.setUserId(userId);

        // 6，3、代金券id
        voucherOrder.setVoucherId(voucherId);

        // 6.4、保存订单
        save(voucherOrder);

        // 7、返回订单id
        return Result.ok(orderId);
    }
}

