package com.studenthub.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "统一分页响应结构")
public class PageResult<T> {

    @Schema(description = "当前页码，从 1 开始", example = "1")
    private final long pageNum;
    @Schema(description = "每页条数", example = "10")
    private final long pageSize;
    @Schema(description = "总记录数", example = "42")
    private final long total;
    @Schema(description = "当前页数据")
    private final List<T> records;

    private PageResult(long pageNum, long pageSize, long total, List<T> records) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }

    public static <T> PageResult<T> of(long pageNum, long pageSize, long total, List<T> records) {
        return new PageResult<>(pageNum, pageSize, total, records);
    }
}
