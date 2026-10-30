package com.studenthub.modules.community.realtime;

/** 跨节点实时提示；消息本体始终由数据库负责补拉。 */
public interface RealtimeEventBus {
    void publish(Long recipientId, String type, Object data);
    void presence(Long userId, boolean online);
    boolean isOnline(Long userId);
    record Delivery(Long recipientId, String type, Object data) {}
}
