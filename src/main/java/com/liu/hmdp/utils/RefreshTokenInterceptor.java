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
 * 此拦截器只用于刷新token有效期
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    /**
     * TODO 不能使用注解，只能使用构造函数注入。
     * 因为LoginInterceptor类对象本身是我们自己创建new出来的，不是通过注解new出来的。
     * 即不是Spring创建的，于是需要我们手动通过构造函数注入（谁用它谁注入 -> MVCConfig拦截器中使用了）
     */
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

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
        /*// 1、根据前端携带的Cookie获取用户的session
        HttpSession session = request.getSession();*/

        // 1、根据前端携带的请求头获取token（前端代码中声明了请求头名称）
        String token = request.getHeader("authorization");

        // 2、判断token是否为空。使用hutool提供的工具类StrUtil中的方法isBlank
        if (StrUtil.isBlank(token)) {
            // 用户不存在，放行
            return true;
        }

        // 3、基于token获取Redis中的用户
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        // entries会自动判断是否为null，为null返回空map
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        // 4、判断用户是否存在
        if (userMap.isEmpty()) {
            // 用户不存在，放行
            return true;
        }

        // 5、将查询到的HashMap数据转为UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 6、用户存在，保存到ThreadLocal中
        UserHolder.saveUser(userDTO);

        // 7、刷新token
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8、放行
        return true;
    }

    /**
     * 渲染之后，返回给用户之前执行。
     * 用户执行完毕，销毁对应的线程中用户信息，避免内存泄露
     *
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 用户使用完毕后移除用户
        UserHolder.removeUser();
    }
}
