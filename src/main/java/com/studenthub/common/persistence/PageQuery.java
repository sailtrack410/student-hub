package com.studenthub.common.persistence;

/**
 * 分页查询统一接口：record 组件访问器自动实现抽象方法，
 * default 方法提供空值兜底，各分页 DTO 无需再各自复制 pageNumOrDefault/pageSizeOrDefault。
 */
public interface PageQuery {

    Integer pageNum();

    Integer pageSize();

    default int pageNumOrDefault() {
        return pageNum() == null ? 1 : pageNum();
    }

    default int pageSizeOrDefault() {
        return pageSize() == null ? 10 : pageSize();
    }
}
