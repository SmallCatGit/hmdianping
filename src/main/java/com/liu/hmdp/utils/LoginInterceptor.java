package com.liu.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.liu.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 自定义拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 前置拦截，该方法将在Controller处理之前进行调用
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1、从ThreadLocal中通过key获取用户，并判断是否需要拦截（用户是否存在）
        if (UserHolder.getUser() == null) {
            // 用户不存在，拦截，设置状态码
            response.setStatus(401);
            return false;
        }
        // 用户存在，放行
        return true;
    }
}
