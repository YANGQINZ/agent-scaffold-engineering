package com.ai.agent.types.common;

/**
 * 通用常量信息
 *
 * @author bitSky
 */
public class Constants
{
    /**
     * UTF-8 字符集
     */
    public static final String UTF8 = "UTF-8";

    /**
     * GBK 字符集
     */
    public static final String GBK = "GBK";

    /**
     * www主域
     */
    public static final String WWW = "www.";

    /**
     * http请求
     */
    public static final String HTTP = "http://";

    /**
     * https请求
     */
    public static final String HTTPS = "https://";

    /**
     * 通用成功标识
     */
    public static final String SUCCESS = "0";

    /**
     * 通用失败标识
     */
    public static final String FAIL = "1";

    /**
     * 密钥后缀
     */
    public static final String SECRET_KEY_SUFFIX = "BitSky";

    /**
     * 登录成功
     */
    public static final String LOGIN_SUCCESS = "Success";

    /**
     * 注销
     */
    public static final String LOGOUT = "Logout";

    /**
     * 注册
     */
    public static final String REGISTER = "Register";

    /**
     * 登录失败
     */
    public static final String LOGIN_FAIL = "Error";

    /**
     * 验证码有效期（分钟）
     */
    public static final Integer CAPTCHA_EXPIRATION = 5;

    /**
     * 绑定有效期（分钟）
     */
    public static final Integer BIND_EXPIRATION = 15;


    /**
     * 监控有效期（天）
     */
    public static final Integer MONITOR_EXPIRATION = 999;

    /**
     * 令牌
     */
    public static final String TOKEN = "token";

    /**
     * 令牌前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 令牌前缀
     */
    public static final String LOGIN_USER_KEY = "login_user_key";

    /**
     * 用户ID
     */
    public static final String JWT_USERID = "userid";

    /**
     * 用户头像
     */
    public static final String JWT_AVATAR = "avatar";

    /**
     * 创建时间
     */
    public static final String JWT_CREATED = "created";

    /**
     * 用户权限
     */
    public static final String JWT_AUTHORITIES = "authorities";

    /**
     * 资源映射路径 前缀
     */
    public static final String RESOURCE_PREFIX = "/profile";

    /**
     * RMI 远程方法调用
     */
    public static final String LOOKUP_RMI = "rmi:";

    /**
     * LDAP 远程方法调用
     */
    public static final String LOOKUP_LDAP = "ldap:";

    /**
     * LDAPS 远程方法调用
     */
    public static final String LOOKUP_LDAPS = "ldaps:";
    /**
     * MQ没批拉取的数量
     */
    public final static Long MQ_POP_QUANTITY = 50L;

    /**
     * 流水号未完成标识
     */
    public static final Integer IDEMPOTENT_STATUS_FAIL = 0;
    /**
     * 流水号完成标识
     */
    public static final Integer IDEMPOTENT_STATUS_SUCCESS = 1;
    /**
     * OSS 的 point
     */
    public static String END_POINT = "https://oss-ap-southeast-1.aliyuncs.com";

    /**
     *  应用内浏览器打开,在链接后面添加默认参数：`&inApp=1`
     */
    public static String APP_BROWSER_DEFAULT_PARAM = "inApp=1";

    /**
     * APP应用内跳转前缀
     *
     */
    public static String APP_NAVIGATION_SCHEMA ="saletop://page";

}
