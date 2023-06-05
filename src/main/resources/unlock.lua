--[[ -- 获取锁的key
local key = KEYS[1]

-- 获取当前线程标识
local threadId = ARGV[1] ]]

-- 获取锁中线程标识 get key
--local id = redis.call('get', KEYS[1])

-- 判断线程标识与锁中标识是否一致
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 一致，释放锁 del key
    return redis.call('del', KEYS[1])
end
return 0

