package com.liu.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liu.hmdp.dto.Result;
import com.liu.hmdp.entity.Blog;

public interface BlogService extends IService<Blog> {

    // 查询热门博客
    Result queryHotBlog(Integer current);

    // 根据博客id显示博客内容
    Result queryBlogById(Long id);

    // 博客点赞、取消点赞
    Result likeBlog(Long id);

    // 查询博客点赞前5名
    Result queryBlogLikes(Long id);

    // 发布博客
    Result saveBlog(Blog blog);

    // 查询关注的博客的内容
    Result queryBlogOfFollow(Long max, Integer offset);
}
