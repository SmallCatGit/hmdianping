package com.liu.hmdp.controller;


import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.ShopType;
import com.liu.hmdp.service.ShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private ShopTypeService typeService;

    /**
     * 查询店铺类型（添加Redis缓存）
     *
     * @return
     */
    @GetMapping("/list")
    public Result queryTypeList() {
        /*List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);*/
        return typeService.queryTypeList();

    }
}
