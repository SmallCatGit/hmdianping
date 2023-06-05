package com.liu.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.liu.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VoucherMapper extends BaseMapper<Voucher> {

    // 根据店铺id查询店铺凭证
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
