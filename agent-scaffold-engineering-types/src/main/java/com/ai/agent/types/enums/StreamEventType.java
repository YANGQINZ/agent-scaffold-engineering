package com.ai.agent.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StreamEventType {

    TEXT_DELTA("文本片段"),
    THINKING("思考过程"),
    NODE_START("节点开始"),
    NODE_END("节点结束"),
    RAG_RETRIEVE("RAG检索"),
    DONE("完成");

    private final String description;

}
