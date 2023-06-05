package com.liu.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.dto.LoginFormDTO;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface UserService extends IService<User> {

    //根据注册填写的手机号发送验证码并且保存到session中
    Result sendCode(String phone, HttpSession session);

    // 根据手机号和验证码完成登录
    Result login(LoginFormDTO loginForm, HttpSession session);
}
