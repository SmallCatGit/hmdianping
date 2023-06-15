package com.liu.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient () {
        // 配置config
        Config config = new Config();
        // 添加redis地址，这里添加单节点地址。添加集群地址：config.useClusterServers();
        config.useSingleServer().setAddress("redis://192.168.31.125:6379").setPassword("123321");
        // 创建Redisson对象(创建客户端)
        return Redisson.create(config);
    }

}
