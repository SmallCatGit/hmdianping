package com.liu.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.mapper.SeckillVoucherMapper;
import com.liu.hmdp.entity.SeckillVoucher;
import com.liu.hmdp.service.SeckillVoucherService;
import org.springframework.stereotype.Service;

/**
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 *
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements SeckillVoucherService {

}
