package com.liu.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.Shop;

public interface ShopService extends IService<Shop> {

    //根据id查询商铺信息
    Result queryById(Long id);

    // 更新店铺并删除缓存
    Result updateShopAndRemoveCache(Shop shop);

    // 根据商铺类型分页查询商铺信息
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
