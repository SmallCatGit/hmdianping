package com.liu.hmdp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
/*Spring中@RestController的作用等同于@Controller + @ResponseBody。
    一个类上添加@Controller注解，表明了这个类是一个控制器类
    @ResponseBody表示方法的返回值直接以指定的格式写入Http response body中，而不是解析为跳转路径。*/
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {
}
