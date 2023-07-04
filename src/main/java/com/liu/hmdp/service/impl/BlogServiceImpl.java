package com.liu.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.dto.ScrollResult;
import com.liu.hmdp.dto.UserDTO;
import com.liu.hmdp.entity.Follow;
import com.liu.hmdp.entity.User;
import com.liu.hmdp.mapper.BlogMapper;
import com.liu.hmdp.entity.Blog;
import com.liu.hmdp.service.BlogService;
import com.liu.hmdp.service.FollowService;
import com.liu.hmdp.service.UserService;
import com.liu.hmdp.utils.SystemConstants;
import com.liu.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.liu.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.liu.hmdp.utils.RedisConstants.FEED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FollowService followService;

    /**
     * 查询热门博客
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据博客id显示博客内容
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 1、获取博客
        Blog blog = getById(id);
        // 2、判断博客是否为空
        if (blog == null) {
            // 为空，表示博客不存在，返回错误信息
            return Result.fail("这篇博客不存在");
        }
        // 3、查询blog有关用户，并设置博客中的用户信息
        queryBlogUser(blog);
        // 4、是否点赞了博客
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 是否点赞了博客
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        // 1、获取用户
        UserDTO user = UserHolder.getUser();
        // 1.1、判断用户是否存在
        if (user == null) {
            // 用户未登录，无需查询是都点赞
            return;
        }
        // 1.2、获取用户id
        Long userId = user.getId();
        // 2、获取用户在redis中点赞博客信息
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // 3、设置博客的点赞字段
        blog.setIsLike(score != null);
    }

    /**
     * 博客点赞、取消点赞
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1、获取用户id
        Long userId = UserHolder.getUser().getId();
        // 2、判断用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3、如果未点赞
            // 3.1、数据库该用户点赞数加一
            boolean isIncreaseSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2、redis中存储用户点赞数据（sortedSet集合）
            if (isIncreaseSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4、如果已经点赞
            // 4.1、数据库该用户点赞数减一
            boolean isDecreaseSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2、redis中删除用户点赞数据
            if (isDecreaseSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok("点赞或取消点赞成功");
    }

    /**
     * 查询博客点赞前5名
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        // 1、查询redis中点赞前五名的用户 zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // 2、判断是否有用户点赞
        if (range == null || range.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 3、有用户点赞，则从redis存储的博客点赞集合中解析用户id
        List<Long> userIds = range.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", userIds);
        // 4、根据用户id获得用户，封装成UserDto对象返回（排除用户敏感信息）
        // 【直接用userService.listByIds的结果点赞顺序是反的，需要使用MybatisPlus提供的自定义查询】
        // WHERE id IN (5, 1) ORDER BY FIELD (id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", userIds).last("ORDER BY FIELD (id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 5、返回
        return Result.ok(userDTOS);
    }

    /**
     * 发布博客
     * 以推模式，把博客推送给粉丝
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 1、获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 2、保存探店博文
        boolean isSuccess = save(blog);
        // 判断笔记是否保存成功
        if (!isSuccess) {
            // 保存失败，返回错误信息
            return Result.fail("笔记新增失败");
        }
        // 3、查询笔记作者的所有粉丝(用户粉丝关系表tb_follow 中 follow_user_id = 当前用户id)
        // select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        // 4、推送笔记id给所有粉丝(迭代)
        for (Follow follow : follows) {
            // 4.1、获取粉丝id
            Long userId = follow.getUserId();
            // 4.2、推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // 5、返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注的博客的内容
     *
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1、获取当前用户id
        Long userId = UserHolder.getUser().getId();
        // 2、查询收件箱（获取推送内容） ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3、判断收信箱是否为空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.fail("收信箱无内容");
        }
        // 4、解析数据：blogId、score（minTime时间戳）、offset
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        // 定义最小时间，minTime（最后赋值的一定就是最小时间，按照score的降序排序的）
        long minTime = 0;
        // 定义offset标识，用于计数获取offset。offset至少会和自己相同，所以初始化为1
        int os = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 4.1、获取博客id，并且保存到列表中
            ids.add(Long.valueOf(Objects.requireNonNull(tuple.getValue())));
            // 4.2、获取分数score（时间戳）
            long score = Objects.requireNonNull(tuple.getScore()).longValue();
            if (score == minTime) {
                os++;
            } else {
                minTime = score;
                os = 1;
            }
        }
        // 5、根据id查询博客 SELECT * FROM tb_blog WHERE id IN(?) ORDER BY FIELD(id, ?, ?)
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER By FIELD(id," + idStr + ")").list();
        // 每个博客的相关点赞和关联信息（博客可以点赞和查看关注信息）
        for (Blog blog : blogs) {
            // 5.1、查询与博客相关的用户（博客对应的博主被人关注的信息）
            queryBlogUser(blog);
            // 5.2、查询博客是否被点赞
            isBlogLiked(blog);
        }
        // 6、封装对象
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setOffset(os);
        result.setMinTime(minTime);
        // 7、返回
        return Result.ok(result);
    }

    /**
     * 查询博客中的用户信息，并设置用户其他信息
     *
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        // 获取用户id
        Long userId = blog.getUserId();
        // 根据用户id获取用户
        User user = userService.getById(userId);
        // 设置用户昵称
        blog.setName(user.getNickName());
        // 设置用户头像
        blog.setIcon(user.getIcon());
    }
}