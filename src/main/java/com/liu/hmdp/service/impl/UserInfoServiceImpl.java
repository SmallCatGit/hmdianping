package com.liu.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.mapper.UserInfoMapper;
import com.liu.hmdp.entity.UserInfo;
import com.liu.hmdp.service.UserInfoService;
import org.springframework.stereotype.Service;

/**
 * 服务实现类
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

}
