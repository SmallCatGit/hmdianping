package com.liu.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * 滚动分页结果包装类（通用）
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
