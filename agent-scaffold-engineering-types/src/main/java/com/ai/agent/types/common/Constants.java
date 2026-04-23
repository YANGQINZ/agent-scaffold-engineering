package com.ai.agent.types.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class Constants {

    public final static String SPLIT = ",";

    public static class ErrorCode {
        public static final String CHAT_PREFIX = "CHAT_";
        public static final String AGENT_PREFIX = "AGENT_";
        public static final String KNOW_PREFIX = "KNOW_";

        public static final String CHAT_MODE_UNSUPPORTED = "CHAT_1001";
        public static final String CHAT_SESSION_EXPIRED = "CHAT_1002";
        public static final String CHAT_LLM_ERROR = "CHAT_1003";

        public static final String AGENT_NOT_FOUND = "AGENT_2001";
        public static final String AGENT_ORCHESTRATION_FAILED = "AGENT_2002";
        public static final String AGENT_MODE_UNSUPPORTED = "AGENT_2003";

        public static final String KNOW_UPLOAD_FAILED = "KNOW_3001";
        public static final String KNOW_PROCESSING_FAILED = "KNOW_3002";
        public static final String KNOW_RETRIEVE_FAILED = "KNOW_3003";
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public enum ResponseCode {

        SUCCESS("0000", "成功"),
        UN_ERROR("0001", "未知失败"),
        ILLEGAL_PARAMETER("0002", "非法参数"),
        ;

        private String code;
        private String info;

    }

}
