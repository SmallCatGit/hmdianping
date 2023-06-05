package com.liu.hmdp.utils;

/**
 * 利用Redis实现分布式锁（）
 */
public interface ILock {

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true表示获取锁成功，false表示获取锁失败
     */
    boolean tryLock(long timeoutSec);

    // 释放锁
    void unlock();
}
