package com.liu.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.ShopType;

import java.util.List;

public interface ShopTypeService extends IService<ShopType> {

    // 基于Redis查询店铺类型
    Result queryTypeList();
}
