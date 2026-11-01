package org.buzizi.deepseekpet.ai;

/**
 * 单条对话消息（持久记忆的最小单元）。
 */
public final class Message {

    private String role;
    private String content;
    private long timestamp;

    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
