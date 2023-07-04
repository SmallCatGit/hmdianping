package com.liu.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
// 作用于类，覆盖默认的 equals 和 hashCode
@EqualsAndHashCode(callSuper = false) // callSuper：是否需要调用父类的方法，默认为 false
// 用来配置lombok如何产生和显示getter和setter的方法
@Accessors(chain = true) // 默认为false（注：但是当fluent为true时，其默认为true），生成的setter方法是void类型；如果设置为true生成的setter方法返回this（当前对象）。
// 实现实体类型和数据库中的表实现映射。
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long seriaVersionUID = 1L;

    // 主键
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    // 商户id
    private Long shopId;

    // 用户id
    private Long userId;

    // 用户图标
    @TableField(exist = false) // 加这个说明字段不属于这个表，需要手动维护这两个字段
    private String icon;

    // 用户姓名
    @TableField(exist = false)
    private String name;

    // 是否点赞过了
    @TableField(exist = false)
    private Boolean isLike;

    // 标题
    private String title;

    // 探店照片，最多9张，以“，隔开”
    private String images;

    // 探店的文字描述
    private String content;

    // 点赞数量
    private Integer liked;

    // 评论数量
    private Integer comments;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;

}
