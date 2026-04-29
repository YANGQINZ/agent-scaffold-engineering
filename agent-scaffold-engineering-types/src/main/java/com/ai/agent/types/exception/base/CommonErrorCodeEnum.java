package com.ai.agent.types.exception.base;

/**
 * 系统通用的业务异常错误码枚举
 */
public enum CommonErrorCodeEnum implements BaseErrorCodeEnum {

    // =========== 系统级别未知异常 =========

    /**
     * 系统未知错误
     */
    SYSTEM_UNKNOWN_ERROR("-1", "系统未知错误"),
    /**
     * 正式环境统一提示内容, 网络异常
     */
    NETWORK_ANOMALY("-2", "network anomaly"),

    // =========== 客户端异常 =========

    /**
     * 客户端HTTP请求方法错误
     * org.springframework.web.HttpRequestMethodNotSupportedException
     */
    CLIENT_HTTP_METHOD_ERROR("1001", "客户端HTTP请求方法错误"),

    /**
     * 客户端request body参数错误
     * 主要是未能通过Hibernate Validator校验的异常处理
     * <p>
     * org.springframework.web.bind.MethodArgumentNotValidException
     */
    CLIENT_REQUEST_BODY_CHECK_ERROR("1002", "客户端请求体参数校验不通过"),

    /**
     * 客户端@RequestBody请求体JSON格式错误或字段类型错误
     * org.springframework.http.converter.HttpMessageNotReadableException
     * <p>
     * eg:
     * 1、参数类型不对:{"test":"abc"}，本身类型是Long
     * 2、{"test":}  test属性没有给值
     */
    CLIENT_REQUEST_BODY_FORMAT_ERROR("1003", "客户端请求体JSON格式错误或字段类型不匹配"),

    /**
     * 客户端@PathVariable参数错误
     * 一般是类型不匹配，比如本来是Long类型，客户端却给了一个无法转换成Long字符串
     * org.springframework.validation.BindException
     */
    CLIENT_PATH_VARIABLE_ERROR("1004", "客户端URL中的参数类型错误"),

    /**
     * 客户端@RequestParam参数校验不通过
     * 主要是未能通过Hibernate Validator校验的异常处理
     * jakarta.validation.ConstraintViolationException
     */
    CLIENT_REQUEST_PARAM_CHECK_ERROR("1005", "客户端请求参数校验不通过"),

    /**
     * 客户端@RequestParam参数必填
     * 入参中的@RequestParam注解设置了必填，但是客户端没有给值
     * jakarta.validation.ConstraintViolationException
     */
    CLIENT_REQUEST_PARAM_REQUIRED_ERROR("1006", "客户端请求缺少必填的参数"),
    /**
     * 该功能已关闭
     */
    FEATURE_IS_TURNED_OFF("1010","该功能已关闭"),
    // =========== 服务端异常 =========

    /**
     * 通用的业务方法入参检查错误
     * java.lang.IllegalArgumentException
     */
    SERVER_ILLEGAL_ARGUMENT_ERROR("2001", "业务方法参数检查不通过"),
    /**
     * 用户隐私设置访问权限
     */
    NO_ACCESS("2009", "无访问权限"),


    EXECUTION_OF_SERVICES_RELATED_TO_SERIAL_NUMBERS_FAILED("60051101", "流水线相关业务执行失败"),
    FILE_UPLOAD_ERROR("6001", "file upload error"),
    FILE_UPLOAD_MODULE_NOT_EXIST("6002", "module not exist"),
    REQUEST_TOO_FREQUENTLY("6003", "请求太频繁，请稍后再试！"),
    TOKEN_INVALID("60050100", "令牌无效"),

    APPLE_AUTHORIZATION_EXCEPTION("6004", "苹果授权异常"),
    APPLE_AUTHORIZATION_PARSING_EXCEPTION("6005", "苹果授权解析异常"),
    APPLE_AUTHORIZATION_HAS_EXPIRED("6006", "苹果授权已过期"),
    UNSUPPORTED_CHAIN("6007", "暂不支持此公链"),
    UNSUPPORTED_COIN("6008", "暂不支持此币种"),
    NOT_CREATE_GROUP("7000", "今日已不支持创建群聊,请明日再来"),
    QUERY_EXCHANGE_RATE("7001", "汇率查询失败"),
    SEND_MQ_FAILED("8000", "发送MQ消息失败"),

    GOOGLE_AUTHORIZATION_EXCEPTION("6010", "谷歌授权异常"),
    UNABLE_TO_RECOGNIZE_LANGUAGE_ERROR("60100105", "无法识别的语言"),
    TRANSLATION_FAILED_EXCEPTION("60100106", "翻译失败"),
    FORMAT_TYPE_IS_NOT_NULL("60100107", "FormatType不能为空"),
    TRANSLATE_IN_THE_SAME_LANGUAGE_THE_ORIGINAL("60100108", "翻译语言和原文相同"),
    ALIYUN_GREEN_CLIENT_EXCEPTION("6011", "阿里云内容安全client创建异常"),
    ALIYUN_GREEN_REQUEST_FAIL("6012", "阿里云内容安全api请求失败"),
    ELASTICSEARCH_REQUEST_EXCEPTION("6013", "elasticsearch请求异常"),
    REQUEST_IMAGE_OVERSIZE("6014", "上传图片过大"),
    REQUEST_IMAGE_FORMAT_NOT_SUPPORTED("6016", "图片格式暂不支持"),
    THE_LANGUAG_CANNOT_RECOGNIZED_OR_IS_NOT_SUPPORTED("6017", "无法识别或不支持该语种类型"),
    STATE_MACHINE_TRANSITION_FAILED("6018", "状态机转换失败"),
    CONTENT_IS_NOT_COMPLIANCE("60085114", "内容不合规"),
    IMAGE_IS_NOT_COMPLIANCE("60085115", "图片不合规"),
    USER_IS_SILENCE("60085110", "你已被禁言"),

    VERSION_NOT_SUPPORTED("60070111", "版本不支持"),
    ACCOUNT_DISABLE("60050101", "账号已停用"),
    CURRENT_VERSION_IS_TOO_LOW("60070200", "当前版本过低，为了使用该功能，请升级到最新版"),
    UNSUPPORTED_CHAIN_ERROR("60010133", "此公链暂不支持"),
    WALLET_CONNECT_SIGN_DUPLICATE_REQUEST("60100010", "钱包签名请求重复"),
    WALLET_CONNECT_SIGN_REQUEST_EXPIRED("60100011", "钱包签名请求已过期"),
    EMAIL_FORMAT_OF_VERIFICATION_CODE("60010101", "邮箱格式不正确"),
    SEND_EMAIL_FAIL("60010181","发送邮件失败"),
    VERIFY_BIND_EMAIL_VOUCHER("60013243", "邮箱验证凭证错误/失效"),
    USER_NOT_EXITS("60010105", "用户不存在"),

    UNSUPPORTED_LANGUAGES("60019001","不支持该语种"),
    FIND_LANGUAGE_FAIL("60019002","查找多语言失败"),
    ;

    private String errorCode;

    private String errorMsg;

    CommonErrorCodeEnum(String errorCode, String errorMsg) {
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
