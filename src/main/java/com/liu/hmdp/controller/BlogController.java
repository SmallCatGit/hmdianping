package com.liu.hmdp.controller;


import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.dto.UserDTO;
import com.liu.hmdp.entity.Blog;
import com.liu.hmdp.service.BlogService;
import com.liu.hmdp.utils.SystemConstants;
import com.liu.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.websocket.server.PathParam;
import java.util.List;

@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private BlogService blogService;

    /**
     * 发布博客
     *
     * @param blog
     * @return
     */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    /**
     * 根据博客id，进行博客点赞、取消点赞（一个用户只能点赞一次，再次点赞则取消点赞）
     *
     * @param id
     * @return
     */
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    /**
     * 查询热门博客
     *
     * @param current
     * @return
     */
    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    /**
     * 根据博客id显示博客内容
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    /**
     * 查询博客点赞前5名
     *
     * @param id
     * @return
     */
    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikes(id);
    }

    /**
     * 进入博客主页，查询指定用户的所有笔记(分页查询)
     *
     * @param current
     * @param id
     * @return
     */
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @PathParam("id") Long id) {

        // 根据用户查询，分页
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取页面数据
        List<Blog> records = page.getRecords();
        // 返回
        return Result.ok(records);
    }

    /**
     * 查询关注的博客的内容（滚动分页查询）
     *
     * @param max
     * @param offset
     * @return
     */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset) {
        return blogService.queryBlogOfFollow(max, offset);
    }
}
