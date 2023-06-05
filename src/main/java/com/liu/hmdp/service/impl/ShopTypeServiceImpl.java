package com.liu.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.mapper.ShopTypeMapper;
import com.liu.hmdp.entity.ShopType;
import com.liu.hmdp.service.ShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.liu.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;


    /**
     * 基于Redis查询店铺类型
     *
     * @return
     */
    @Override
    public Result queryTypeList() {
        // 1、从Redis中查询店铺类型
        List<String> stringList = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY + "list", 0, -1);
        if (stringList != null && !stringList.isEmpty()) {
            // 2、存在，转换为ShopType类型直接返回
            LinkedList<ShopType> shopTypeList = new LinkedList<>();
            for (String type : stringList) {
                shopTypeList.push(JSONUtil.toBean(type, ShopType.class));
            }
            return Result.ok(shopTypeList);
        }

        // 3、不存在，从数据库中查询店铺类型
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        // 4、判断数据库中是否存在数据，没有报业务异常
        if (shopTypes == null || shopTypes.isEmpty()) {
            return Result.fail("店铺类型为空");
        }
        // 数据库中不为空，转化为JSON存入Redis
        String jsonStrShopTypeList;
        for (ShopType shopType : shopTypes) {
            jsonStrShopTypeList = JSONUtil.toJsonStr(shopType);
            // 逆序存储，取的时候从头开始取（队列的形式）
            stringRedisTemplate.opsForList().leftPush(CACHE_SHOP_TYPE_KEY + "list", jsonStrShopTypeList);
        }

        // 4、返回店铺信息
        return Result.ok(shopTypes);
    }
}
