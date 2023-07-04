package com.liu.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.LoginFormDTO;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.dto.UserDTO;
import com.liu.hmdp.entity.User;
import com.liu.hmdp.mapper.UserMapper;
import com.liu.hmdp.service.UserService;
import com.liu.hmdp.utils.RegexUtils;
import com.liu.hmdp.utils.UserHolder;
import com.sun.xml.internal.bind.v2.TODO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.liu.hmdp.utils.RedisConstants.*;
import static com.liu.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    // @Resource注入SprinData提供的api中操作Redis的模板
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据注册填写的手机号发送验证码并且保存到session中
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1、校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2、如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        // 3、如果符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        /*// 4、将验证码保存到session中（无法实现集群共享Session）
        session.setAttribute("code", code);*/
        // 4、将验证码保存到Redis中，并且设置有效期（set key value ex 120）
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5、发送验证码
        log.debug("发送短信验证码成功，验证码为：{}", code);

        // 返回
        return Result.ok();
    }

    @Autowired
    private UserService userService;

    /**
     * 根据手机号和验证码完成登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1、检验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2、如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
        /*// 3、从Session中获取验证码检验
        Object cacheCode = session.getAttribute("code");*/
        // TODO 3、从Redis中获取验证码检验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        // 验证码不能为空前端已经校验过了
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 4、不一致，报错
            Result.fail("验证码为空或者验证码错误");
        }
        // 5、一致，根据手机号查询用户信息
        /*LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = userService.getOne(queryWrapper);*/ // TODO 已经测试通过
        User user = query().eq("phone", phone).one();

        // 6、判断用户是否存在
        if (user == null) {
            // 7、不存在，保存该新用户
            user = createUserWithPhone(phone);
        }
        // 拷贝用户信息到UserDto对象中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class); // BeanUtil拷贝有返回值BeanUtils拷贝无返回值
        /*// 8、存在，保存用户信息到session中(敏感信息需要过滤，于是保存的应该是封装的UserDto对象)
        session.setAttribute("user", userDTO);*/
        // TODO 8、存在，保存用户信息到Redis中(敏感信息需要过滤，于是保存的应该是封装的UserDto对象)
        // 8.1、获取随机的token用于存储的key和作为登录令牌（UUID生成）
        String token = UUID.randomUUID().toString(true);// 后面的toString(true)会生成不带横线分隔的

        // TODO 8.2、将UserDTO对象转换为HashMap。并且转换类型
        // StringRedisTemplate要求所有的类型都是String类型，而userDto里面id是long类型。因此需要转换
        // 使用工具自定义类型。CopyOptions数据拷贝后自定义
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)// setIgnoreNullValue:忽略空的值;
                        // TODO setFieldValueEditor:修改字段值（两个参数：字段名，字段值；一个返回值：修改后的字段值）
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 8.3、存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);


        // 8.4、设置token（UserDTO）有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 9、返回token给前端
        return Result.ok(token);
    }

    /**
     * 当前用户当天签到
     * postman测试
     *
     * @return
     */
    @Override
    public Result sign() {
        // 1、获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 2、获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3、拼接key(要从dataTime中获取年月的字符串格式)
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4、确定今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5、将签到信息写入到redis中 SETBIT key offset 0 / 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 当前用户截至当前时间在本月的连续签到天数
     *
     * @return
     */
    @Override
    public Result signCount() {
        // 1、获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 2、获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3、拼接key(要从dataTime中获取年月的字符串格式)
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy/MM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4、确定今天是这个月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5、获取本月截至到今天为止的所有签到记录，返回的是一个十进制的数字  BITFIELD key GET u[第几天] offset（开始位置）
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        // 判断是否有签到记录
        if (result == null || result.isEmpty()) {
            // 没有签到结果，直接返回
            return Result.fail("没有任何签到结果");
        }
        // 获取签到结果
        Long num = result.get(0);
        // 判断签到结果
        if (num == null || num == 0) {
            // 签到数据不存在
            return Result.ok(0);
        }
        // 6、循环遍历
        int count = 0;
        while (true) {
            // 6.1、让获取的数字与1做与运算，得到数字的最后一个bit位，并且判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 6.2、为0，未签到，结束
                break;
            } else {
                // 6.3、不为0，说明已经签到，计数器 + 1
                count++;
            }
            // 6.4、让数字向右移动一位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 创建新用户并保存
     *
     * @param phone
     */
    private User createUserWithPhone(String phone) {
        // 1、创建用户
        User user = new User();

        // 2、设置用户phone
        user.setPhone(phone);

        // 3、设置用户昵称
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));

        // 4、保存用户
        save(user);
        // 5、返回用户
        return user;

    }
}
