package com.liu.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liu.hmdp.mapper.BlogCommentsMapper;
import com.liu.hmdp.entity.BlogComments;
import com.liu.hmdp.service.BlogCommentsService;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements BlogCommentsService {
}