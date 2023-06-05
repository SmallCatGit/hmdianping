package com.liu.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.VoucherOrder;

public interface VoucherOrderService extends IService<VoucherOrder> {

    // 优惠券秒杀下单
    Result seckillVoucher(Long voucherId);

    // 查询库存->判断订单->减库存->创建订单
    Result createVoucherOrder(Long voucherId);
}
