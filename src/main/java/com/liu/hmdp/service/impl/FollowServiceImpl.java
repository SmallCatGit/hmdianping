package com.liu.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.dto.UserDTO;
import com.liu.hmdp.mapper.FollowMapper;
import com.liu.hmdp.entity.Follow;
import com.liu.hmdp.service.FollowService;
import com.liu.hmdp.service.UserService;
import com.liu.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.liu.hmdp.utils.RedisConstants.FOLLOW_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    /**
     * 关注和取关
     *
     * @param followUserId 被关注的用户的id
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1、获取登录的用户id
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOW_KEY + userId;
        // 2、判断是关注还是取关
        if (isFollow) {
            // 3、需要关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 将关注用户的id保存到redis中 SADD userId followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 4、需要取关，删除数据 delete from tb_follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                // 将redis中保存的关注用户的信息删除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 进入博客页面后，判断是已关注还是未关注
     *
     * @param followUserId 目标用户id
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 1、查询是否关注 select count(*) from tb_follow where user_id =
        // ? and follow_user_id = ?
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // 2、判断结果，确定是否关注
        return Result.ok(count > 0);
    }

    /**
     * 共同关注
     *
     * @param id 被关注用户的id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        // 1、获取当前登录用户的id
        Long userId = UserHolder.getUser().getId();
        String userKey = FOLLOW_KEY + userId;
        // 2、求交集
        String followKey = FOLLOW_KEY + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, followKey);
        if (intersect == null || intersect.isEmpty()) {
            // 无交集
            return Result.ok(Collections.emptyList());
        }
        // 3、解析集合，转换为long型
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4、根据id查询用户，获得共同关注信息
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.toBean(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
