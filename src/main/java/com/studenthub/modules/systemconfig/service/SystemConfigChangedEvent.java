package com.studenthub.modules.systemconfig.service;

/**
 * 管理端保存配置后发布；监听器在事务提交后把新值应用到运行时组件。
 */
public record SystemConfigChangedEvent(String reason) {
}
