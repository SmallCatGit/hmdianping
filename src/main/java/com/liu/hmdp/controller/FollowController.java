package com.liu.hmdp.controller;

import com.liu.hmdp.dto.Result;
import com.liu.hmdp.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private FollowService followService;

    /**
     * 关注和取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow (@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 进入博客页面后，判断是已关注还是未关注
     * @param followUserId
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow (@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id) {
        return followService.followCommons(id);
    }
}
