package com.liu.hmdp.utils;

import com.liu.hmdp.dto.UserDTO;

/**
 * ThreadLocal静态线程类，里面还有一些向线程中保存、获取、删除用户的方法
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user) {
        tl.set(user);
    }

    public static UserDTO getUser() {
        return tl.get();
    }

    public static void removeUser() {
        tl.remove();
    }
}
