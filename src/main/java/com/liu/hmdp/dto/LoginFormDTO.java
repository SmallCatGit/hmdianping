package com.liu.hmdp.dto;

import lombok.Data;

/**
 * 封装的注册、登录的对象
 */
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
