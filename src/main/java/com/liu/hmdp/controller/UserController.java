package com.liu.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liu.hmdp.dto.LoginFormDTO;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.dto.UserDTO;
import com.liu.hmdp.entity.User;
import com.liu.hmdp.entity.UserInfo;
import com.liu.hmdp.service.UserInfoService;
import com.liu.hmdp.service.UserService;
import com.liu.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import javax.websocket.server.PathParam;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private UserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        // TODO 实现登录功能
        log.info("loginForm: {}", loginForm);
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     *
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout() {
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me() {
        // TODO 获取当前登录的用户并返回
        // 获取线程中的用户
        UserDTO user = UserHolder.getUser();
        // 返回用户
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

    /**
     * 根据id查用户，进入博客主页查询用户信息
     *
     * @param userId
     * @return
     */
    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        /*这个只能查看当前登录用户的主页，TODO 测试不通过
        UserDTO dto = UserHolder.getUser();
        return Result.ok(dto);*/
        // 根据id，查询用户
        User user = userService.getById(userId);
        // 判断用户是否存在
        if (user == null) {
            return Result.fail("用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 返回
        return Result.ok(userDTO);
    }
}
