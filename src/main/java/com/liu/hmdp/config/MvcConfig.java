package com.liu.hmdp.config;

import com.liu.hmdp.utils.LoginInterceptor;
import com.liu.hmdp.utils.RefreshTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * 配置拦截路径
 */
@Slf4j
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    // 这个类加了@Configuration注解，由Sping注入。于是我们可以使用注解注入StringRedisTemplate对象
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 添加拦截器,拦部分请求（登录拦截器）
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // log.info("拦截器类。。。");
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns( // 排除拦截路径
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);// order：拦截器执行顺序，默认都是0（也就是按照添加顺序执行），也可以手动设置，值越大，执行运行级越低

        // 添加拦截器,拦所有请求（Token刷新拦截器）
        // addInterceptor 默认拦所有请求，也可以调用addPathPatterns加拦截路径
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
