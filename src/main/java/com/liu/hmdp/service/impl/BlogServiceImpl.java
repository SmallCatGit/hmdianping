package com.liu.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.mapper.BlogMapper;
import com.liu.hmdp.entity.Blog;
import com.liu.hmdp.service.BlogService;
import org.springframework.stereotype.Service;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
}