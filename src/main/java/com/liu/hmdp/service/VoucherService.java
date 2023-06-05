package com.liu.hmdp.service;

import com.liu.hmdp.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.entity.Voucher;

public interface VoucherService extends IService<Voucher> {

    // 查询店铺的优惠券列表
    Result queryVoucherOfShop(Long shopId);

    // 新增秒杀券
    void addSeckillVoucher(Voucher voucher);
}
