package com.liu.hmdp;

import cn.hutool.json.JSONUtil;
import com.liu.hmdp.dto.LoginFormDTO;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.User;
import com.liu.hmdp.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.annotation.Resource;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import static com.liu.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
public class getTokenBy1000 {

    @Autowired
    MockMvc mockMvc;

    @Resource
    UserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    void getToken() throws Exception {
        // 电话
        String phone = "";
        // 验证码
        String code = "";
        // 设置文件保存路径(OutputStreamWriter：字符流转为字节流)
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                new FileOutputStream("D:\\Desktop\\token.txt"));
        // 循环得到1000用户(数据库id从10到1009的)
        for (int id = 10; id <= 1009; id++) {
            // 通过id从数据库中获得user对象
            User user = userService.getById(id);
            // 设置电话号码
            phone = user.getPhone();
            // 根据手机号，创建虚拟请求，模拟通过手机号获取验证码（用手机号登录）
            ResultActions performObtainCode = mockMvc.perform(MockMvcRequestBuilders.post("/user/code?phone=" + phone));
            /*// 获得响应体的信息(执行并返回转换为字符串的响应体信息)
            String codeResultAsJson = performObtainCode.andReturn().getResponse().getContentAsString();
            // 将响应体信息转换为自定义返回对象(result)
            Result resultAsCode = JSONUtil.toBean(codeResultAsJson, Result.class);
            // 获得验证码
            code = resultAsCode.getData().toString();*/

            // 从redis中获取code（code没有返回给前端，所以在响应体中没有code这个信息）
            code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

            // 创建登录表单
            LoginFormDTO loginFormDTO = new LoginFormDTO();
            loginFormDTO.setPhone(phone);
            loginFormDTO.setCode(code);
            // 将表单转换为Json字符串
            String loginFormAsJson = JSONUtil.toJsonStr(loginFormDTO);
            // 创建虚拟请求，模拟登录
            ResultActions performObtainAslogin = mockMvc.perform(MockMvcRequestBuilders.post("/user/login")
                    // 设置页面显示内容（contentType）类型为Json
                    .contentType(MediaType.APPLICATION_JSON)
                    // 需要页面显示的内容
                    .content(loginFormAsJson));
            // 获得响应体的信息(执行并返回转换为字符串的响应体信息)【token返回给了前端，能从响应体中获取】
            String LoginResultAsJson = performObtainAslogin.andReturn().getResponse().getContentAsString();
            // 将响应体信息(token)转换为自定义返回对象(result)
            Result resultAsToken = JSONUtil.toBean(LoginResultAsJson, Result.class);
            // 获得token
            String token = resultAsToken.getData().toString();
            // 将token写入到文件
            outputStreamWriter.write(token + "\n");
        }
        // 关闭输出流
        outputStreamWriter.close();
    }

}
