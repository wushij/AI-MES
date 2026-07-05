package com.aimes.service.coze;

public enum CozeChatPromptMode {
    /** 原样发送用户问题，由 Bot 检索知识库 */
    KNOWLEDGE("knowledge"),
    /** 注入 MySQL 实时数据后作答 */
    REALTIME("realtime");

    private final String wireValue;

    CozeChatPromptMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
