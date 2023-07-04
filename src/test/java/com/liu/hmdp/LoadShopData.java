package com.liu.hmdp;

import com.liu.hmdp.entity.Shop;
import com.liu.hmdp.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.liu.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 向redis中加入店铺数据
 */
@SpringBootTest
public class LoadShopData {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopService shopService;

    @Test
    void loadShopData() {
        // 1、查询店铺信息
        List<Shop> list = shopService.list();
        // 2、把店铺信息分组，按照typeId分组，typeId一致的放到一个集合（基于stream流实现，比手动添加简单）
        // typeId为key，集合为value
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3、分批写入到redis中
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1、获取店铺类型的id
            Long typeId = entry.getKey();
            // 设置存储到redis中的key
            String key = SHOP_GEO_KEY + typeId;
            // 3.2、获取同类型店铺的集合
            List<Shop> value = entry.getValue();
            // 以集合的方式批量的添加（效率更高）
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            // 3.3、将集合写入到redis中 GEOADD key 经度 纬度 member(存入店铺id即可)
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);


        }

    }
}
