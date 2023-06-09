---
--- Generated by Luanalysis
--- Created by liuqi.
--- DateTime: 2023/6/20 1:16
---
-- 1、参数列表
-- 1.1、优惠券id
local voucherId = ARGV[1];
-- 1.2、用户id
local userId = ARGV[2];
-- 1.3、订单id
local orderId = ARGV[3];


-- 2、数据
-- 2.1、库存key
local stockKey = 'seckill:stock:' .. voucherId;
-- 2.2、订单key
local orderKey = 'seckill:order:' .. voucherId;

-- 3、业务
-- 3.1、判断库存是否充足get stockKey 【Redis的存取都是String类型，所以get操作需要转换成数值类型】
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.1.1、库存不足，返回1
    return 1;
end
-- 3.2、判断用户是否下单sismember orderKey userId
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 3.2.1、成立，说明用户已下单，返回2
    return 2;
end
-- 3.3、扣减库存incrby stockKey -1
redis.call('incrby', stockKey, -1);
-- 3.4、下单（保存用户信息到订单中）sadd orderKey userId
redis.call('sadd', orderKey, userId);
-- 3.5、扣减成功，返回0

-- TODO 发送消息到队列中前，先要使用命令行加入组：XGROUP create stream.orders g1 0 mkstream 【stream.orders加到组g1中，从0开始】
-- 3.6、发送消息到队列中，xadd stream.orders * k1 v1 k2 v2
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId);
return 0;