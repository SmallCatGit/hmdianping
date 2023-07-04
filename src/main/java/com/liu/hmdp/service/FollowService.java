package com.liu.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.Follow;

public interface FollowService extends IService<Follow> {

    // 关注和取关
    Result follow(Long followUserId, Boolean isFollow);

    // 进入博客页面后，判断是已关注还是未关注
    Result isFollow(Long followUserId);

    // 共同关注
    Result followCommons(Long id);
}
