package com.liu.hmdp.utils;

/**
 * 常量工具类（redis）
 */
public class RedisConstants {
    // 登录保存验证码的业务名：做什么的：key
    public static final String LOGIN_CODE_KEY = "login:code:";

    // 登录保存验证码的有效时间
    public static final Long LOGIN_CODE_TTL = 2L;

    // 保存登录用于存储用户信息的Redis的key的业务名
    public static final String LOGIN_USER_KEY = "login:token:";

    // 登录用于存储用户信息的Redis的有效期（类似于Session的有效期，防止一直存储导致内存溢出）
    public static final Long LOGIN_USER_TTL = 30L;

    // 店铺缓存时间，超时后自动查询数据库更新，尽量保证数据一致性
    public static final Long CACHE_SHOP_TTL = 30L;

    // 设置存储到redis中空值的有效时间，用于防止缓存穿透
    public static final Long CACHE_NULL_TTL = 2L;


    // 店铺缓存的业务名（为店铺增加缓存，缓解数据库压力）
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    // 店铺类型缓存业务名（页面上展示的店铺类型，数据不常更改，最适合使用Redis缓存存储）
    public static final String CACHE_SHOP_TYPE_KEY = "cache:shopType:";

    // 用于互斥锁，防止缓存击穿，同一个key访问太多，导致突然失效
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    // 互斥锁的过期时间
    public static final Long LOCK_SHOP_TTL = 10L;

    // 秒杀资格判断
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    // 博客点赞键的头
    public static final String BLOG_LIKED_KEY = "blog:liked:";

    // 关注和取关
    public static final String FOLLOW_KEY = "follows:";

    // 推送笔记给粉丝
    public static final String FEED_KEY = "feed:";

    // 添加店铺信息到redis中（在测试方法中使用）
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
