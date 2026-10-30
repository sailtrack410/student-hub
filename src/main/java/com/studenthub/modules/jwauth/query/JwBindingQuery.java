package com.studenthub.modules.jwauth.query;

/** 跨模块只读入口，仅公开课表定位所需字段，不公开教务凭据。 */
public interface JwBindingQuery {
    String lastSemesterCode(Long userId);
}
