package com.liu.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// 暴露代理对象
@EnableAspectJAutoProxy(exposeProxy = true)
// @MapperScan:指定要变成实现类的接口所在的包，然后包下面的所有接口在编译之后都会生成相应的实现类
@MapperScan("com.liu.hmdp.mapper")
@SpringBootApplication(scanBasePackages = "com.liu.hmdp")
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }
}
